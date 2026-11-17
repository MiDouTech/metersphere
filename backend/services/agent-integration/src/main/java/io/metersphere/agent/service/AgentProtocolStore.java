package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentFactCipher;
import io.metersphere.sdk.exception.MSException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Function;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** New stable-subject ledger; explicitly isolated from the old token-scoped idempotency table. */
@Service
public class AgentProtocolStore {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AgentProtocolStore.class);
    final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final TransactionTemplate nested;
    private final TransactionTemplate isolated;
    private final AgentFactCipher cipher;
    public AgentProtocolStore(JdbcTemplate jdbc,PlatformTransactionManager manager,AgentFactCipher cipher) {
        this.jdbc=jdbc;this.cipher=cipher;transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);transaction.setTimeout(30);
        isolated=new TransactionTemplate(manager);isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);isolated.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);isolated.setTimeout(30);
        nested=new TransactionTemplate(manager);nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }
    public <T> T isolated(java.util.function.Supplier<T> action){return isolated.execute(status->action.get());}
    public interface Authorization { AgentProtocolAccess.Actor check(boolean lock); }
    public record WriteContext(AgentProtocolAccess.Actor actor,String operationId) {}
    private record Outcome(Map<String,Object> response,String error) {}

    public Map<String,Object> execute(String operation,String resource,Map<String,Object> args,Authorization authorize,
                                      Runnable meter,Function<WriteContext,Map<String,Object>> action) {
        var first=authorize.check(false);
        String key=id(args,"idempotencyKey"),fingerprint=requestHash(args);
        String scope=hash(List.of(first.organization(),first.user(),first.agent(),operation,resource));
        try {
            Outcome outcome=transaction.execute(status -> {
                var actor=authorize.check(true);
                require(actor.equals(first),"AGENT_RESOURCE_NOT_ACCESSIBLE");
                String operationId=uuid();
                jdbc.update("INSERT INTO msp_protocol_operation(scope_key,request_key,operation_id,request_hash,status,metered,create_time) VALUES (?,?,?,?,'RUNNING',false,?) ON DUPLICATE KEY UPDATE scope_key=scope_key",
                        scope,key,operationId,fingerprint,System.currentTimeMillis());
                var row=jdbc.queryForMap("SELECT * FROM msp_protocol_operation WHERE scope_key=? AND request_key=? FOR UPDATE",scope,key);
                require(fingerprint.equals(row.get("request_hash")),"IDEMPOTENCY_CONFLICT");
                if(!operationId.equals(row.get("operation_id")) && "RUNNING".equals(row.get("status")))
                    return new Outcome(null,"REQUEST_IN_PROGRESS");
                operationId=(String)row.get("operation_id");
                if("SUCCEEDED".equals(row.get("status"))) {
                    authorize.check(true);
                    var response=new LinkedHashMap<>(decodePayload((String)row.get("response")));
                    if(response.containsKey("permissionToAct"))response.put("permissionToAct",false);
                    if(response.containsKey("recoveryPermitted"))response.put("recoveryPermitted",false);
                    response.put("replayed",true);return new Outcome(response,null);
                }
                if("UNKNOWN".equals(row.get("status")))return new Outcome(null,"PROTOCOL_OPERATION_UNKNOWN");
                if(!Boolean.TRUE.equals(row.get("metered"))) {
                    meter.run(); // New logical calls are accounted once, including later semantic rejection.
                    jdbc.update("UPDATE msp_protocol_operation SET metered=true WHERE scope_key=? AND request_key=?",scope,key);
                }
                Object savepoint=status.createSavepoint();
                try {
                    var response=new LinkedHashMap<>(action.apply(new WriteContext(actor,operationId)));
                    authorize.check(true); // Held identity/permission rows serialize ordinary revocation writes.
                    response.put("operationId",operationId);response.put("replayed",false);
                    jdbc.update("UPDATE msp_protocol_operation SET status='SUCCEEDED',response=?,error_code=NULL WHERE scope_key=? AND request_key=?",payload(response),scope,key);
                    status.releaseSavepoint(savepoint);return new Outcome(response,null);
                } catch(MSException rejected) {
                    status.rollbackToSavepoint(savepoint);status.releaseSavepoint(savepoint);
                    String code=safeCode(rejected.getMessage());
                    jdbc.update("UPDATE msp_protocol_operation SET status='REJECTED',error_code=? WHERE scope_key=? AND request_key=?",code,scope,key);
                    if(operation.startsWith("session.")&&resource.contains(":")){int separator=resource.indexOf(':');String sid=resource.substring(0,separator),iid=resource.substring(separator+1);if(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_instance WHERE id=? AND session_id=?",Long.class,iid,sid)==1)event(sid,iid,operationId,"REQUEST_REJECTED",actor.user(),Map.of("operation",operation,"code",code));}
                    LOG.info("Protocol request traceId={} stage={} code={} operationId={}",org.slf4j.MDC.get("traceId"),operation,code,operationId);
                    return new Outcome(null,code);
                }
            });
            if(outcome.error()!=null)fail(outcome.error());return outcome.response();
        } catch(org.springframework.dao.DataAccessException failure) {
            String state="UNKNOWN";int vendor=0;
            for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLException sql){String candidate=sql.getSQLState();if(candidate!=null&&candidate.matches("[A-Za-z0-9]{5}"))state=candidate;vendor=sql.getErrorCode();break;}
            LOG.warn("Protocol storage failure traceId={} stage={} code=PROTOCOL_STORAGE_UNAVAILABLE sqlState={} vendorCode={}",org.slf4j.MDC.get("traceId"),operation,state,vendor);
            throw new MSException("PROTOCOL_STORAGE_UNAVAILABLE");
        }
    }
    public <T> T transaction(java.util.function.Supplier<T> work){return transaction.execute(status -> work.get());}
    public <T> T nested(java.util.function.Supplier<T> work){return nested.execute(status -> work.get());}
    public <T> T preview(java.util.function.Supplier<T> action){return nested.execute(status->{try{return action.get();}finally{status.setRollbackOnly();}});}
    public Map<String,Object> data(Map<String,Object> row){return decodePayload((String)row.get("payload"));}
    private Map<String,Object> decodePayload(String value){return decode((String)decode(cipher.reveal(value)).get("secret"));}
    public String payload(Object value){return cipher.protectAll(encode(Map.of("secret",encode(value))));}
    public String event(String session,String instance,String operation,String kind,String actor,Object value) {
        String id=uuid();jdbc.update("INSERT INTO msp_protocol_event(id,session_id,instance_id,operation_id,kind,actor_id,payload,create_time) VALUES (?,?,?,?,?,?,?,?)",id,session,instance,operation,kind,actor,payload(auditValue(value)),System.currentTimeMillis());return id;
    }
    private Object auditValue(Object value){
        if(value instanceof Map<?,?> map){var safe=new LinkedHashMap<String,Object>();for(var entry:map.entrySet()){String key=String.valueOf(entry.getKey());if(!Set.of("resumeToken","base64","authorization","password","token","secret").contains(key))safe.put(key,auditValue(entry.getValue()));}return safe;}
        if(value instanceof List<?> list)return list.stream().map(this::auditValue).toList();return value;
    }
    private String safeCode(String code){return code!=null&&code.matches("[A-Z][A-Z0-9_]{2,63}")?code:"PROTOCOL_REQUEST_REJECTED";}
}
