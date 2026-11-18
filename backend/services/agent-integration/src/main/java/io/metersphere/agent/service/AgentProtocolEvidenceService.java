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
    private final ThreadLocal<ReferenceCapture> referencedAuthors=new ThreadLocal<>();
    public AgentProtocolEvidenceService(JdbcTemplate jdbc,AgentProtocolStore store){this.jdbc=jdbc;this.store=store;}
    /** Captures every successfully used proof, including proofs not retained in the current checkpoint. */
    ReferenceCapture captureReferences(String instance){return new ReferenceCapture(instance);}
    final class ReferenceCapture implements AutoCloseable {
        private final ReferenceCapture previous=referencedAuthors.get();
        private final String instance;
        private final List<Map<String,Object>> current=new ArrayList<>();
        ReferenceCapture(String instance){this.instance=instance;referencedAuthors.set(this);}
        void recordInto(Map<String,Object> data){var identities=object(data.getOrDefault("evidenceIdentities",Map.of()));var people=new HashSet<>(strings(data.getOrDefault("evidencePersons",List.of())));for(var ref:current){String user=(String)ref.get("contributorUserId"),person=(String)ref.get("contributorPerson");identities.putIfAbsent(user,person);people.add(person);}data.put("evidenceIdentities",identities);data.put("evidencePersons",new ArrayList<>(people));}
        public void close(){if(previous==null)referencedAuthors.remove();else referencedAuthors.set(previous);}
    }
    /** Only evidence actually referenced by server state contributes; an unused upload cannot veto review. */
    static void refreshContributors(JdbcTemplate jdbc,Map<String,Object> session,Map<String,Object> data){
        refreshContributors(jdbc,session,data,Map.of());
    }
    private static void refreshContributors(JdbcTemplate jdbc,Map<String,Object> session,Map<String,Object> data,Object additional){
        var refs=new TreeMap<String,Map<String,Object>>();collectReferences(data,refs);collectReferences(additional,refs);
        var identities=object(data.getOrDefault("evidenceIdentities",Map.of()));var people=new HashSet<>(strings(data.getOrDefault("evidencePersons",List.of())));
        for(var ref:refs.values()){
            boolean personal="PERSONAL".equals(ref.get("kind"));
            var rows=jdbc.queryForList(personal?"SELECT user_id AS author,uploader_person FROM agent_case_evidence WHERE id=? FOR UPDATE":"SELECT create_user AS author FROM ai_execution_artifact WHERE id=? FOR UPDATE",ref.get("id"));
            require(rows.size()==1&&rows.getFirst().get("author") instanceof String,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");String user=(String)rows.getFirst().get("author");
            var persons=jdbc.queryForList("SELECT person_ref FROM msp_protocol_identity WHERE organization_id=? AND user_id=? FOR UPDATE",String.class,session.get("organization_id"),user);
            require(persons.size()==1&&persons.getFirst()!=null,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");
            Object recorded=rows.getFirst().get("uploader_person");require(recorded==null||recorded.equals(persons.getFirst()),"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");
            require(!ref.containsKey("contributorUserId")||user.equals(ref.get("contributorUserId")),"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");require(!ref.containsKey("contributorPerson")||persons.getFirst().equals(ref.get("contributorPerson")),"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");
            require(!identities.containsKey(user)||identities.get(user).equals(persons.getFirst()),"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");identities.put(user,persons.getFirst());people.add(persons.getFirst());
        }
        data.put("evidenceIdentities",identities);data.put("evidencePersons",new ArrayList<>(people));
    }
    /** Reconstructs pre-fix proofs from successful operation audits; rejected requests/uploads are excluded. */
    static void refreshHistoricalContributors(JdbcTemplate jdbc,AgentProtocolStore store,Map<String,Object> session,Map<String,Object> data,String instance){
        var records=new ArrayList<Map<String,Object>>();var audits=new ArrayList<Map<String,Object>>();
        for(var row:jdbc.queryForList("SELECT kind,payload FROM msp_protocol_event WHERE session_id=? AND instance_id=? AND kind IN ('data.record','case.prepare','result.revalidate','recovery.record','cleanup.recovery.record')",session.get("id"),instance)){var audit=store.data(row);audit.put("auditKind",row.get("kind"));audits.add(audit);if("data.record".equals(row.get("kind")))records.add(audit);}
        records.sort(Comparator.comparingLong(record->number(record,"expectedContextVersion")));var proofs=new ArrayList<Map<String,Object>>();
        for(var audit:audits){String kind=(String)audit.get("auditKind");if(Set.of("data.record","recovery.record","cleanup.recovery.record").contains(kind)){proofs.add(Map.of("evidence",audit.getOrDefault("evidence",List.of())));continue;}
            var preflight=object(audit.get("preflight"));proofs.add(Map.of("evidence",preflight.getOrDefault("evidence",List.of())));var usedBindings=new LinkedHashMap<String,Object>();
            if("case.prepare".equals(kind)){long before=number(audit,"expectedContextVersion");for(var record:records)if(number(record,"expectedContextVersion")<before)usedBindings.put(id(record,"key"),Map.of("objectId",id(record,"objectId")));}
            else usedBindings.putAll(object(data.getOrDefault("bindings",Map.of())));
            var incoming=object(preflight.getOrDefault("bindings",Map.of()));for(var entry:usedBindings.entrySet()){var checked=object(incoming.getOrDefault(entry.getKey(),Map.of()));require(Objects.equals(object(entry.getValue()).get("objectId"),checked.get("objectId")),"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");proofs.add(Map.of("evidence",checked.getOrDefault("evidence",List.of())));}
        }
        refreshContributors(jdbc,session,data,proofs);
    }
    private static void collectReferences(Object value,Map<String,Map<String,Object>> refs){
        if(value instanceof Map<?,?> map)for(var entry:map.entrySet()){
            String key=String.valueOf(entry.getKey());
            if(Set.of("contract","legacy","outputs","variables","knowledge","knowledgeSegments","originalAccepted").contains(key))continue;
            if("substitution".equals(key)&&entry.getValue() instanceof Map<?,?> proposal&&!"CONFIRMED".equals(proposal.get("state")))continue;
            if(Set.of("evidence","preflightEvidence","ownershipEvidence").contains(key)&&entry.getValue() instanceof List<?> list)
                for(Object item:list)if(item instanceof Map<?,?> ref&&("PERSONAL".equals(ref.get("kind"))||"TASK".equals(ref.get("kind")))&&ref.get("id") instanceof String){var copy=object(item);refs.merge(copy.get("kind")+":"+copy.get("id"),copy,(old,next)->old.containsKey("contributorPerson")?old:next);}
            collectReferences(entry.getValue(),refs);
        }else if(value instanceof List<?> list)for(Object item:list)collectReferences(item,refs);
    }
    public List<Map<String,Object>> holdOwned(String project,String caseId,String taskId,String owner,Object references,String reason) {
        var contexts=jdbc.queryForList("SELECT s.owner_id,s.agent_ref,s.organization_id FROM msp_protocol_instance i JOIN msp_protocol_session s ON s.id=i.session_id WHERE i.id=?",owner);
        require(contexts.size()==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");var context=contexts.getFirst();
        for(var ref:objects(references))if("PERSONAL".equals(ref.get("kind"))) {
            var rows=jdbc.queryForList("SELECT token_id,user_id,protocol_instance_id FROM agent_case_evidence WHERE id=? FOR UPDATE",id(ref,"id"));require(rows.size()==1,"PROTOCOL_EVIDENCE_UNAVAILABLE");var row=rows.getFirst();
            if(row.get("protocol_instance_id")!=null)require(owner.equals(row.get("protocol_instance_id")),"AGENT_RESOURCE_NOT_ACCESSIBLE");
            else require(context.get("owner_id").equals(row.get("user_id"))&&jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_subject WHERE token_id=? AND user_id=? AND agent_ref=? AND organization_id=?",Long.class,row.get("token_id"),context.get("owner_id"),context.get("agent_ref"),context.get("organization_id"))==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
        }
        List<Map<String,Object>> result=new ArrayList<>();for(var ref:hold(project,caseId,taskId,owner,references,reason))result.add(new LinkedHashMap<>(ref));
        for(var ref:result){boolean personal="PERSONAL".equals(ref.get("kind"));var authors=jdbc.queryForList(personal?"SELECT user_id AS author,uploader_person FROM agent_case_evidence WHERE id=? FOR UPDATE":"SELECT create_user AS author FROM ai_execution_artifact WHERE id=? FOR UPDATE",ref.get("id"));require(authors.size()==1&&authors.getFirst().get("author") instanceof String,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");String user=(String)authors.getFirst().get("author");var people=jdbc.queryForList("SELECT person_ref FROM msp_protocol_identity WHERE organization_id=? AND user_id=? FOR UPDATE",String.class,context.get("organization_id"),user);require(people.size()==1&&people.getFirst()!=null,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");Object recorded=authors.getFirst().get("uploader_person");require(recorded==null||recorded.equals(people.getFirst()),"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");ref.put("contributorUserId",user);ref.put("contributorPerson",people.getFirst());}
        if(referencedAuthors.get()!=null&&owner.equals(referencedAuthors.get().instance))referencedAuthors.get().current.addAll(result);
        return result;
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
