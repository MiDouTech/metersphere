package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentFactCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

@Service
public class AgentProtocolImageService {
    private final JdbcTemplate jdbc;private final AgentProtocolStore store;private final AgentProtocolAccess access;
    private final AgentGovernedExecutionService execution;private final AgentProtocolEvidenceService evidence;private final AgentFactCipher cipher;
    @jakarta.annotation.Resource private AgentEvidenceRedactionService redaction;
    public AgentProtocolImageService(JdbcTemplate jdbc,AgentProtocolStore store,AgentProtocolAccess access,AgentGovernedExecutionService execution,AgentProtocolEvidenceService evidence,AgentFactCipher cipher){this.jdbc=jdbc;this.store=store;this.access=access;this.execution=execution;this.evidence=evidence;this.cipher=cipher;}
    public Map<String,Object> upload(Map<String,Object>args) {
        require("1.0".equals(args.get("protocolVersion")),"PROTOCOL_VERSION_UNSUPPORTED");
        fields(args,"sessionId","caseExecutionId","fileName","contentType","base64","stepId","purpose","redacted");String sid=id(args,"sessionId"),iid=id(args,"caseExecutionId");
        return store.execute("evidence.upload",sid+":"+iid,args,lock->execution.authorize(sid,lock),()->{},ctx->{
            var s=execution.session(sid,true);var i=execution.instance(sid,iid,true);var data=store.data(i);
            require(!"ENDED".equals(i.get("phase")),"PROTOCOL_INSTANCE_ENDED");access.project(ctx.actor(),(String)s.get("project_id"),true);access.capability(ctx.actor(),(String)s.get("environment_id"),"EVIDENCE_WRITE",false);
            String type=text(args,"contentType");byte[] bytes=AgentDirectExecutionService.decodeImage(text(args,"base64"),type);
            require(Boolean.TRUE.equals(args.get("redacted")),"ARTIFACT_REDACTION_REQUIRED");redaction.scanBeforePersist(bytes,type,true);
            var policy=object(store.data(s).get("policy"));long used=jdbc.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM agent_case_evidence WHERE protocol_instance_id=?",Long.class,iid);
            // Bounded by the explicit per-instance allocation recorded at activation, never an invented TTL.
            require(bytes.length<=number(policy,"limits.import-bytes")&&used+bytes.length<=number(policy,"limits.evidence-total-bytes"),"PROTOCOL_SIZE_LIMIT");
            String purpose=Objects.toString(args.get("purpose"),"AFTER_STEP");require(Set.of("BEFORE_STEP","AFTER_STEP","FAILURE","HEALING_BEFORE","HEALING_AFTER").contains(purpose),"AGENT_VALIDATION_ERROR");
            Integer position=null;if(args.containsKey("stepId")){var steps=objects(object(data.get("contract")).get("steps"));var step=steps.stream().filter(item->args.get("stepId").equals(item.get("id"))).findFirst().orElseThrow(AgentBusinessAccess::denied);position=((Number)step.getOrDefault("sourcePosition",steps.indexOf(step)+1)).intValue();}
            if("TASK".equals(s.get("source_type")))require(position!=null,"PROTOCOL_STEP_UNKNOWN");
            String id=uuid(),name="evidence-"+id+("image/png".equals(type)?".png":".jpg"),sha=org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
            jdbc.update("INSERT INTO agent_case_evidence(id,token_id,user_id,project_id,case_id,content_hash,step_num,purpose,file_name,content_type,size_bytes,sha256,payload,create_time,expires_at,evidence_scope,definition_hash_version,definition_snapshot,protocol_instance_id,uploader_person) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,AgentTokenContext.get()==null?null:AgentTokenContext.get().getId(),ctx.actor().user(),s.get("project_id"),i.get("case_id"),object(data.get("legacy")).get("definitionHash"),position,purpose,name,type,bytes.length,sha,bytes,System.currentTimeMillis(),System.currentTimeMillis(),"CASE","definition-v2",cipher.protect(encode(object(data.get("legacy")).get("definition"))),iid,ctx.actor().person());
            evidence.hold((String)s.get("project_id"),(String)i.get("case_id"),null,iid,List.of(Map.of("kind","PERSONAL","id",id)),"RESULT");
            store.event(sid,iid,ctx.operationId(),"EVIDENCE_UPLOADED",ctx.actor().user(),Map.of("evidenceId",id,"sha256",sha,"sizeBytes",bytes.length));return Map.of("kind","PERSONAL","evidenceId",id,"sha256",sha,"sizeBytes",bytes.length);
        });
    }
    public ResponseEntity<byte[]> download(String sid,String iid,String id) {
        var actor=execution.authorize(sid,false);var s=execution.session(sid,false);execution.instance(sid,iid,false);
        access.capability(actor,(String)s.get("environment_id"),"EVIDENCE_READ",false);
        require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_evidence_hold WHERE owner_id=? AND kind='PERSONAL' AND evidence_id=?",Long.class,iid,id)==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
        var rows=jdbc.queryForList("SELECT e.payload,e.content_type FROM agent_case_evidence e JOIN msp_protocol_instance i ON i.id=? AND e.case_id=i.case_id JOIN msp_protocol_session s ON s.id=i.session_id AND e.project_id=s.project_id WHERE e.id=?",iid,id);
        require(rows.size()==1,"PROTOCOL_EVIDENCE_UNAVAILABLE");return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType((String)rows.getFirst().get("content_type"))).body((byte[])rows.getFirst().get("payload"));
    }
}
