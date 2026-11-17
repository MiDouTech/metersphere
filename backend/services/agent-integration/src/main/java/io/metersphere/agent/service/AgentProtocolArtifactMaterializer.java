package io.metersphere.agent.service;

import io.metersphere.sdk.file.FileCenter;
import io.metersphere.sdk.file.FileRequest;
import io.metersphere.sdk.exception.MSException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** Materializes governed images through the established file store, preserving real task/attempt/step IDs. */
@Service
public class AgentProtocolArtifactMaterializer {
    private final JdbcTemplate jdbc;private final AgentProtocolEvidenceService evidence;
    public AgentProtocolArtifactMaterializer(JdbcTemplate jdbc,AgentProtocolEvidenceService evidence){this.jdbc=jdbc;this.evidence=evidence;}
    public Map<String,String> materialize(Map<String,Object>s,Map<String,Object>i,Map<String,Object>data) {
        var references=new TreeSet<String>();
        for(String collection:List.of("submittedAssertions","reviewedAssertions","steps","correctedSteps"))for(var record:objects(data.getOrDefault(collection,List.of())))for(var ref:objects(record.getOrDefault("evidence",List.of())))if("PERSONAL".equals(ref.get("kind")))references.add(id(ref,"id"));
        var mapping=new LinkedHashMap<String,String>();
        for(String id:references) {
            var rows=jdbc.queryForList("SELECT * FROM agent_case_evidence WHERE id=? FOR UPDATE",id);require(rows.size()==1,"PROTOCOL_EVIDENCE_UNAVAILABLE");var file=rows.getFirst();
            require(Objects.equals(object(data.get("legacy")).get("definitionHash"),file.get("content_hash")),"PROTOCOL_EVIDENCE_UNAVAILABLE");
            require(s.get("project_id").equals(file.get("project_id"))&&i.get("case_id").equals(file.get("case_id"))&&file.get("step_num")!=null&&file.get("purpose")!=null,"PROTOCOL_TASK_STEP_EVIDENCE_REQUIRED");
            var steps=jdbc.queryForList("SELECT id FROM ai_execution_step WHERE task_id=? AND execution_case_id=? AND pos=?",String.class,s.get("source_id"),i.get("source_case_id"),file.get("step_num"));require(steps.size()==1,"PROTOCOL_TASK_STEP_EVIDENCE_REQUIRED");String step=steps.getFirst();
            var existing=jdbc.queryForList("SELECT id,status FROM ai_execution_artifact WHERE task_id=? AND execution_id=? AND sha256=? AND purpose=? AND step_id=? FOR UPDATE",s.get("source_id"),s.get("source_execution_id"),file.get("sha256"),file.get("purpose"),step);
            String artifact=id;
            if(!existing.isEmpty()){require("AVAILABLE".equals(existing.getFirst().get("status")),"PROTOCOL_EVIDENCE_UNAVAILABLE");artifact=(String)existing.getFirst().get("id");}
            else {
                String folder="ai/execution/governed/"+s.get("source_id")+"/"+s.get("source_execution_id"),name=(String)file.get("file_name");
                var request=new FileRequest();request.setFolder(folder+"/"+id);request.setFileName(name);
                try {
                    byte[] bytes=(byte[])file.get("payload");FileCenter.getDefaultRepository().saveFile(bytes,request);
                    byte[] restored=FileCenter.getDefaultRepository().getFile(request);
                    require(org.apache.commons.codec.digest.DigestUtils.sha256Hex(restored).equals(file.get("sha256")),"PROTOCOL_ARTIFACT_STORAGE_FAILED");
                }catch(MSException failure){AgentProtocolDiagnostics.warn(org.slf4j.LoggerFactory.getLogger(getClass()),"MATERIALIZE","PROTOCOL_ARTIFACT_STORAGE_FAILED",id,failure);throw failure;}catch(Exception failure){AgentProtocolDiagnostics.warn(org.slf4j.LoggerFactory.getLogger(getClass()),"MATERIALIZE","PROTOCOL_ARTIFACT_STORAGE_FAILED",id,failure);throw new MSException("PROTOCOL_ARTIFACT_STORAGE_FAILED");}
                jdbc.update("INSERT INTO ai_execution_artifact(id,task_id,execution_case_id,case_id,step_id,purpose,file_id,file_name,storage_folder,content_type,size_bytes,sha256,redacted,status,retention_until,create_time,create_user,execution_id,upload_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,1,'AVAILABLE',?,?,?,?,'AVAILABLE')",id,s.get("source_id"),i.get("source_case_id"),i.get("case_id"),step,file.get("purpose"),id,name,folder,file.get("content_type"),file.get("size_bytes"),file.get("sha256"),System.currentTimeMillis(),System.currentTimeMillis(),file.get("user_id"),s.get("source_execution_id"));
            }
            evidence.hold((String)s.get("project_id"),(String)i.get("case_id"),(String)s.get("source_id"),(String)i.get("id"),List.of(Map.of("kind","TASK","id",artifact)),"RESULT");mapping.put(id,artifact);
        }
        return mapping;
    }
}
