package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** References and all applicable deletion paths use the actual evidence row as their common lock. */
@Service
public class AgentProtocolEvidenceService {
    private final JdbcTemplate jdbc;
    private final AgentProtocolStore store;
    public AgentProtocolEvidenceService(JdbcTemplate jdbc,AgentProtocolStore store){this.jdbc=jdbc;this.store=store;}
    public List<Map<String,Object>> holdOwned(String project,String caseId,String taskId,String owner,Object references,String reason) {
        var contexts=jdbc.queryForList("SELECT s.owner_id,s.agent_ref,s.organization_id FROM msp_protocol_instance i JOIN msp_protocol_session s ON s.id=i.session_id WHERE i.id=?",owner);
        require(contexts.size()==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");var context=contexts.getFirst();
        for(var ref:objects(references))if("PERSONAL".equals(ref.get("kind"))) {
            var rows=jdbc.queryForList("SELECT token_id,user_id,protocol_instance_id FROM agent_case_evidence WHERE id=? FOR UPDATE",id(ref,"id"));require(rows.size()==1,"PROTOCOL_EVIDENCE_UNAVAILABLE");var row=rows.getFirst();
            if(row.get("protocol_instance_id")!=null)require(owner.equals(row.get("protocol_instance_id")),"AGENT_RESOURCE_NOT_ACCESSIBLE");
            else require(context.get("owner_id").equals(row.get("user_id"))&&jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_subject WHERE token_id=? AND user_id=? AND agent_ref=? AND organization_id=?",Long.class,row.get("token_id"),context.get("owner_id"),context.get("agent_ref"),context.get("organization_id"))==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
        }
        return hold(project,caseId,taskId,owner,references,reason);
    }

    public List<Map<String,Object>> hold(String project,String caseId,String taskId,String owner,Object references,String reason) {
        require(Set.of("UNKNOWN","RESULT","CORRECTION","DISPUTE","KNOWLEDGE").contains(reason),"AGENT_VALIDATION_ERROR");
        List<Map<String,Object>> result=new ArrayList<>();
        var refs=objects(references).stream().sorted(Comparator.comparing(ref -> text(ref,"kind")+":"+text(ref,"id"))).toList();
        for(var ref:refs) {
            fields(ref,"kind","id");String kind=text(ref,"kind"),id=id(ref,"id");
            Map<String,Object> row;
            if("PERSONAL".equals(kind)) {
                var rows=jdbc.queryForList("SELECT id,project_id,case_id,sha256,content_type,size_bytes,payload FROM agent_case_evidence WHERE id=? FOR UPDATE",id);
                if(rows.size()!=1)throw new MSException("PROTOCOL_EVIDENCE_UNAVAILABLE");row=rows.getFirst();
                require(project.equals(row.get("project_id"))&&caseId.equals(row.get("case_id"))&&row.get("payload")!=null,"PROTOCOL_EVIDENCE_UNAVAILABLE");
            } else if("TASK".equals(kind)) {
                var rows=jdbc.queryForList("SELECT a.id,t.project_id,a.task_id,a.status,a.sha256,a.content_type,a.size_bytes,c.case_id FROM ai_execution_artifact a JOIN ai_execution_task t ON t.id=a.task_id JOIN ai_execution_case c ON c.id=a.execution_case_id AND c.task_id=t.id WHERE a.id=? FOR UPDATE",id);
                if(rows.size()!=1)throw new MSException("PROTOCOL_EVIDENCE_UNAVAILABLE");row=rows.getFirst();
                require(project.equals(row.get("project_id"))&&caseId.equals(row.get("case_id"))&&Objects.equals(taskId,row.get("task_id"))&&"AVAILABLE".equals(row.get("status")),"PROTOCOL_EVIDENCE_UNAVAILABLE");
            } else throw new MSException("AGENT_VALIDATION_ERROR");
            require(id.equals(row.get("id")),"PROTOCOL_EVIDENCE_UNAVAILABLE");
            jdbc.update("INSERT INTO msp_protocol_evidence_hold(kind,evidence_id,owner_id,reason,create_time) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE reason=reason",kind,id,owner,reason,System.currentTimeMillis());
            result.add(Map.of("kind",kind,"id",id,"sha256",Objects.toString(row.get("sha256"),""),"contentType",Objects.toString(row.get("content_type"),""),"sizeBytes",row.get("size_bytes")));
        }
        return result;
    }

    /** Physical task deletion is claimed durably before touching external storage. A failed delete stays unavailable. */
    public boolean claimTaskDeletion(String artifact,long now) {
        return store.transaction(() -> {
            var rows=jdbc.queryForList("SELECT id,status,retention_until FROM ai_execution_artifact WHERE id=? FOR UPDATE",artifact);
            if(rows.size()!=1)return false;var row=rows.getFirst();if(!artifact.equals(row.get("id")))return false;
            if(!Set.of("AVAILABLE","PROTOCOL_DELETE_PENDING").contains(row.get("status")))return false;
            if(row.get("retention_until")==null||((Number)row.get("retention_until")).longValue()>now)return false;
            if(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_evidence_hold WHERE kind='TASK' AND evidence_id=?",Long.class,artifact)>0)return false;
            jdbc.update("UPDATE ai_execution_artifact SET status='PROTOCOL_DELETE_PENDING' WHERE id=?",artifact);return true;
        });
    }

    public int cleanupPersonal(long now) {
        return store.transaction(() -> {
            var ids=jdbc.queryForList("SELECT id FROM agent_case_evidence WHERE submission_id IS NULL AND expires_at<? ORDER BY id LIMIT 1000 FOR UPDATE",String.class,now);
            int count=0;
            for(String id:ids)if(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_evidence_hold WHERE kind='PERSONAL' AND evidence_id=?",Long.class,id)==0)
                count+=jdbc.update("DELETE FROM agent_case_evidence WHERE id=? AND submission_id IS NULL AND expires_at<? AND NOT EXISTS (SELECT 1 FROM agent_case_submission_evidence r WHERE r.evidence_id=? AND r.retain_until>?)",id,now,id,now);
            return count;
        });
    }
}
