package io.metersphere.agent.quality;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class GlobalQualityAnalysisService {
    @jakarta.annotation.Resource private io.metersphere.agent.security.AgentFactCipher factCipher;
    private final JdbcTemplate jdbc;private final QualityPolicyAccess access;private final QualityPolicyValidator validator;private final AgentExecutionMapper mapper;
    private final ObjectMapper json=new ObjectMapper();
    public GlobalQualityAnalysisService(JdbcTemplate jdbc,QualityPolicyAccess access,QualityPolicyValidator validator,AgentExecutionMapper mapper){this.jdbc=jdbc;this.access=access;this.validator=validator;this.mapper=mapper;}
    private record Candidate(JsonNode rules,String hash) {}
    private Candidate candidate(String id){
        var rows=jdbc.queryForList("SELECT rules_json,content_hash FROM execution_quality_policy_global WHERE id=?",id);
        if(rows.size()!=1)throw new MSException("QUALITY_POLICY_NOT_FOUND");
        var validation=validator.validate((String)rows.getFirst().get("rules_json"));
        if(!validation.valid() || !validation.contentHash().equals(rows.getFirst().get("content_hash")))throw new MSException("QUALITY_POLICY_HASH_INVALID");
        return new Candidate(read(validation.normalizedJson()),validation.contentHash());
    }
    @Transactional(readOnly=true)
    public Map<String,Object> preview(String id){
        access.requireSystem(PermissionConstants.SYSTEM_QUALITY_READ);var proposed=candidate(id);
        var current=jdbc.queryForList("SELECT p.rules_json,p.content_hash FROM execution_quality_policy_global_state s JOIN execution_quality_policy_global p ON p.id=s.current_policy_id WHERE s.scope='GLOBAL'");
        Map<String,JsonNode> oldRules=new HashMap<>();
        if(!current.isEmpty())read((String)current.getFirst().get("rules_json")).path("rules").forEach(rule->oldRules.put(rule.path("ruleId").asText(),rule));
        Set<String> changed=new TreeSet<>();
        proposed.rules().path("rules").forEach(rule->{String ruleId=rule.path("ruleId").asText();if(!rule.equals(oldRules.remove(ruleId)))changed.add(ruleId);});changed.addAll(oldRules.keySet());
        return Map.of("scope","SYSTEM","candidateHash",proposed.hash(),"changedRules",changed,
                "inFlightAttempts",jdbc.queryForObject("SELECT COUNT(*) FROM ai_execution_attempt WHERE status IN ('CLAIMED','RUNNING','FINALIZING')",Long.class),
                "appliesTo","仅影响后续绑定的新执行；在途和历史执行保留原版本");
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> trial(String id){
        String actor=access.requireSystem(PermissionConstants.SYSTEM_QUALITY_MANAGE);var proposed=candidate(id);
        var samples=jdbc.queryForList("SELECT r.id,r.task_id,r.execution_id,r.step_id,r.assertion_result,r.artifact_ids,r.facts_version,t.execution_contract,t.execution_contract_hash FROM ai_execution_step_result r JOIN ai_execution_task t ON t.id=r.task_id ORDER BY r.create_time DESC,r.id DESC LIMIT 100");
        int pass=0,rejected=0,unavailable=0;List<String> sampleIds=new ArrayList<>();
        for(var sample:samples){
            sampleIds.add((String)sample.get("id"));
            if(sample.get("facts_version")==null || sample.get("execution_contract")==null || !DigestUtils.sha256Hex((String)sample.get("execution_contract")).equals(sample.get("execution_contract_hash"))){unavailable++;continue;}
            try {
                JsonNode frozen=null;for(JsonNode c:read((String)sample.get("execution_contract")).path("cases"))for(JsonNode step:c.path("steps"))if(step.path("stepId").asText().equals(sample.get("step_id")))frozen=step.path("assertions");
                if(frozen==null){unavailable++;continue;}
                List<AgentExecutionArtifactDTO> artifacts=new ArrayList<>();boolean available=true;
                for(JsonNode ref:read(sample.get("artifact_ids")==null?"[]":(String)sample.get("artifact_ids"))){
                    var artifact=mapper.selectArtifactById(ref.asText());
                    if(artifact==null || !Objects.equals(artifact.getTaskId(),sample.get("task_id")) || !Objects.equals(artifact.getExecutionId(),sample.get("execution_id")) || !Objects.equals(artifact.getStepId(),sample.get("step_id")) || !"AVAILABLE".equals(artifact.getStatus())){available=false;break;}
                    artifacts.add(artifact);
                }
                if(!available){unavailable++;continue;}
                GlobalQualityGate.evaluate(proposed.rules(),frozen,read(factCipher.reveal((String)sample.get("assertion_result"))),artifacts);pass++;
            }catch(MSException error){if(error.getMessage().startsWith("QUALITY_FACTS_"))unavailable++;else rejected++;}
        }
        String trialId=UUID.randomUUID().toString();long now=System.currentTimeMillis();
        Map<String,Object> result=Map.of("trialId",trialId,"candidateHash",proposed.hash(),"sampleCount",samples.size(),"wouldPass",pass,"wouldReject",rejected,"unavailable",unavailable,"createdAt",now,
                "notice","只读试算最近 100 条步骤事实的成功申请，不修改正式结果；历史摘要不可用或证据已清理的记录不判为通过");
        try {jdbc.update("INSERT INTO ai_quality_policy_trial(id,policy_id,policy_hash,actor,summary_json,sample_ids,created_at) VALUES (?,?,?,?,?,?,?)",trialId,id,proposed.hash(),actor,json.writeValueAsString(result),json.writeValueAsString(sampleIds),now);}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new MSException("QUALITY_TRIAL_FAILED");}
        return result;
    }
    private JsonNode read(String value){try {if(value!=null){var node=json.readTree(value);if(node!=null)return node;}}catch(Exception ignored){}throw new MSException("QUALITY_ASSERTION_INVALID");}
}
