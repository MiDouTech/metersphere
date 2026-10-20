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
        var rows=jdbc.queryForList("SELECT request_hash,response_json,status,hash_version FROM agent_idempotency_record WHERE token_id=? AND tool_name=? AND request_id=?",token.getId(),operation,requestId);
        if(rows.isEmpty())return Optional.empty();
        var row=rows.getFirst();
        if(!hash(arguments).equals(row.get("request_hash")))throw new MSException("IDEMPOTENCY_CONFLICT");
        if(!"SUCCEEDED".equals(row.get("status")) || !"canonical-v1".equals(row.get("hash_version")))throw new MSException("REQUEST_IN_PROGRESS");
        authorize.run();
        return Optional.of(read((String)row.get("response_json"),type));
    }
    public <T> T execute(String operation,String requestId,Object arguments,Class<T> resultType,Runnable authorize,Supplier<T> action) {
        if(requestId==null || !requestId.matches("[A-Za-z0-9_.:-]{1,128}"))throw new MSException("IDEMPOTENCY_KEY_REQUIRED");
        var token=AgentTokenContext.get();if(token==null)throw AgentBusinessAccess.denied();
        authorize.run();
        String hash=hash(arguments);
        try {
            return transaction.execute(status->{
                authorize.run();
                // Older dispatchers used the public tool name; do not let renaming repeat those writes.
                var legacy=jdbc.queryForList("SELECT id FROM agent_idempotency_record WHERE token_id=? AND tool_name=? AND request_id=?",token.getId(),"metersphere."+operation,requestId);
                if(!legacy.isEmpty())throw new MSException("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");
                String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();
                jdbc.update("INSERT INTO agent_idempotency_record(id,token_id,tool_name,request_id,request_hash,create_time,status,hash_version) VALUES (?,?,?,?,?,?,'RUNNING','canonical-v1') ON DUPLICATE KEY UPDATE id=id",id,token.getId(),operation,requestId,hash,now);
                var row=jdbc.queryForMap("SELECT id,request_hash,response_json,status,hash_version FROM agent_idempotency_record WHERE token_id=? AND tool_name=? AND request_id=? FOR UPDATE",token.getId(),operation,requestId);
                if(!hash.equals(row.get("request_hash")))throw new MSException("IDEMPOTENCY_CONFLICT");
                if(!id.equals(row.get("id"))) {
                    if(!"SUCCEEDED".equals(row.get("status")) || !"canonical-v1".equals(row.get("hash_version")))throw new MSException("REQUEST_IN_PROGRESS");
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
    public String hash(Object arguments) {
        JsonNode node=json.valueToTree(arguments);
        if(node.isObject())((ObjectNode)node).remove("requestId");
        return DigestUtils.sha256Hex(write(sorted(node)));
    }
    private JsonNode sorted(JsonNode node) {
        if(node.isObject()) {ObjectNode result=json.createObjectNode();var names=new TreeSet<String>();node.fieldNames().forEachRemaining(names::add);for(String name:names)result.set(name,sorted(node.get(name)));return result;}
        if(node.isArray()){var result=json.createArrayNode();node.forEach(item->result.add(sorted(item)));return result;}
        return node;
    }
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception error){throw new MSException("AGENT_SERIALIZATION_ERROR");}}
    private <T> T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception error){throw new MSException("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED");}}
}
