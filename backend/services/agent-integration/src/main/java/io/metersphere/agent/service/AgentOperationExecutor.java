package io.metersphere.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Supplier;

/** Database side effects and the replay result commit together. Never use for lease credentials. */
@Service
public class AgentOperationExecutor {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper json=new ObjectMapper();
    public AgentOperationExecutor(JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.transaction=new TransactionTemplate(manager);this.transaction.setTimeout(15);
        this.transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    /** Read only committed replay results before expensive external validation. */
    public <T> Optional<T> completed(String operation,String requestId,Object arguments,Class<T> type,Runnable authorize) {
        if(requestId==null || !requestId.matches("[A-Za-z0-9_.:-]{1,128}"))throw new MSException("IDEMPOTENCY_KEY_REQUIRED");
        var token=AgentTokenContext.get();if(token==null)throw AgentBusinessAccess.denied();
        authorize.run();
        String storedKey=storedKey(token.getId(),operation,requestId);
        var rows=jdbc.queryForList("SELECT request_hash,response_json,status,hash_version FROM agent_idempotency_record WHERE token_id=? AND tool_name=? AND request_id=?",token.getId(),operation,storedKey);
        if(rows.isEmpty()) {
            for(String legacyName:legacyNames(operation))if(!jdbc.queryForList("SELECT id FROM agent_idempotency_record WHERE token_id=? AND tool_name=? AND request_id=?",token.getId(),legacyName,storedKey(token.getId(),legacyName,requestId)).isEmpty())throw new MSException("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");
            return Optional.empty();
        }
        var row=rows.getFirst();
        if(!"canonical-v1".equals(row.get("hash_version")))throw new MSException("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");
        if(!hash(arguments).equals(row.get("request_hash")))throw new MSException("IDEMPOTENCY_CONFLICT");
        if(!"SUCCEEDED".equals(row.get("status")))throw new MSException("RUNNING".equals(row.get("status"))?"REQUEST_IN_PROGRESS":"IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");
        authorize.run();
        return Optional.of(read((String)row.get("response_json"),type));
    }
    public <T> T execute(String operation,String requestId,Object arguments,Class<T> resultType,Runnable authorize,Supplier<T> action) {
        return executeWithLegacyArguments(operation,requestId,arguments,resultType,authorize,action,arguments);
    }
    public <T> T executeWithLegacyArguments(String operation,String requestId,Object arguments,Class<T> resultType,Runnable authorize,Supplier<T> action,Object legacyArguments) {
        if(requestId==null || !requestId.matches("[A-Za-z0-9_.:-]{1,128}"))throw new MSException("IDEMPOTENCY_KEY_REQUIRED");
        var token=AgentTokenContext.get();if(token==null)throw AgentBusinessAccess.denied();
        authorize.run();
        String hash=hash(arguments);
        String storedKey=storedKey(token.getId(),operation,requestId);
        try {
            return transaction.execute(status->{
                authorize.run();
                // Older dispatchers used the public tool name; do not let renaming repeat those writes.
                var legacyRows=new ArrayList<Map<String,Object>>();
                for(String legacyName:legacyNames(operation))legacyRows.addAll(jdbc.queryForList("SELECT request_hash,response_json,status,hash_version FROM agent_idempotency_record WHERE token_id=? AND tool_name=? AND request_id=? FOR UPDATE",token.getId(),legacyName,storedKey(token.getId(),legacyName,requestId)));
                if(!legacyRows.isEmpty()) {
                    if(legacyRows.size()!=1 || !operation.startsWith("generic."))throw new MSException("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");
                    var old=legacyRows.getFirst();String version=Objects.toString(old.get("hash_version"),"legacy-v0");
                    if(!"SUCCEEDED".equals(old.get("status")) || old.get("response_json")==null || !Set.of("legacy-v0","generic-canonical-v1").contains(version))throw new MSException("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");
                    String expected;
                    if("generic-canonical-v1".equals(version))expected=hash(legacyArguments);
                    else {
                        Map<String,Object> original=json.convertValue(legacyArguments,Map.class);var sorted=new TreeMap<>(original);sorted.remove("requestId");
                        expected=DigestUtils.sha256Hex(io.metersphere.sdk.util.JSON.toJSONString(sorted));
                    }
                    if(!expected.equals(old.get("request_hash")))throw new MSException("IDEMPOTENCY_CONFLICT");
                    String saved=(String)old.get("response_json");
                    if(saved.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1048576)throw new MSException("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");
                    var envelope=json.valueToTree(read(saved,Map.class));
                    if(!envelope.path("content").isArray())throw new MSException("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");
                    authorize.run();return read(saved,resultType);
                }
                String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();
                jdbc.update("INSERT INTO agent_idempotency_record(id,token_id,tool_name,request_id,request_hash,create_time,status,hash_version,identity_version) VALUES (?,?,?,?,?,?,'RUNNING','canonical-v1','binary-v2') ON DUPLICATE KEY UPDATE id=id",id,token.getId(),operation,storedKey,hash,now);
                var row=jdbc.queryForMap("SELECT id,request_hash,response_json,status,hash_version FROM agent_idempotency_record WHERE token_id=? AND tool_name=? AND request_id=? FOR UPDATE",token.getId(),operation,storedKey);
                if(!"canonical-v1".equals(row.get("hash_version")))throw new MSException("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");
                if(!hash.equals(row.get("request_hash")))throw new MSException("IDEMPOTENCY_CONFLICT");
                if(!id.equals(row.get("id"))) {
                    if(!"SUCCEEDED".equals(row.get("status")))throw new MSException("RUNNING".equals(row.get("status"))?"REQUEST_IN_PROGRESS":"IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");
                    authorize.run();return read((String)row.get("response_json"),resultType);
                }
                T result=action.get();String encoded=write(result);
                if(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1048576)throw new MSException("AGENT_RESULT_LIMIT_EXCEEDED");
                jdbc.update("UPDATE agent_idempotency_record SET response_json=?,status='SUCCEEDED',completed_time=? WHERE id=?",encoded,System.currentTimeMillis(),id);
                return result;
            });
        } catch(org.springframework.dao.PessimisticLockingFailureException | org.springframework.transaction.TransactionTimedOutException busy) {
            throw new MSException("REQUEST_IN_PROGRESS");
        }
    }
    private String storedKey(String token,String operation,String key) {
        var old=jdbc.queryForList("SELECT request_id FROM agent_idempotency_record WHERE token_id=? AND tool_name=? AND identity_version='legacy-ci-v1' AND request_id COLLATE utf8mb4_general_ci=? COLLATE utf8mb4_general_ci",String.class,token,operation,key);
        if(old.size()>1)throw new MSException("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");
        return old.isEmpty()?key:old.getFirst();
    }
    private List<String> legacyNames(String operation) {
        String external=switch(operation) {
            case "external.image.case" -> "metersphere.functional.attachment.import_url";
            case "external.image.bug" -> "metersphere.bug.attachment.import_url";
            case "external.image.execution" -> "metersphere.functional.execution.evidence.import_url";
            default -> operation.startsWith("generic.")?"metersphere."+operation.substring("generic.".length()):"metersphere."+operation;
        };
        var names=new ArrayList<>(List.of("metersphere."+operation,external));
        if(operation.equals("generic.bug.case.relate"))names.add("metersphere.bug.relate_case");
        return names.stream().distinct().sorted().toList();
    }
    public String hash(Object arguments) {return AgentRequestFingerprint.hash(arguments);}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception error){throw new MSException("AGENT_SERIALIZATION_ERROR");}}
    private <T> T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception error){throw new MSException("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");}}
}
