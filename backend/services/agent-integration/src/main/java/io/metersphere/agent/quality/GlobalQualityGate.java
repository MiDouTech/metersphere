package io.metersphere.agent.quality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.agent.dto.*;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Server-owned policy binding and result verification; no project-specific policy fallback. */
@Service
public class GlobalQualityGate {
    private final JdbcTemplate jdbc;
    private final AgentExecutionMapper mapper;
    private final QualityPolicyValidator validator;
    public GlobalQualityGate(JdbcTemplate jdbc, AgentExecutionMapper mapper, QualityPolicyValidator validator) {
        this.jdbc=jdbc; this.mapper=mapper; this.validator=validator;
    }
    public record Binding(String policyId, int versionNo, String contentHash, String rulesJson) {}

    @Transactional(rollbackFor=Exception.class)
    public Binding bind(String executionId, String taskId) {
        // Same singleton lock as publication: an attempt sees one complete published version.
        var current=jdbc.queryForList("SELECT current_policy_id FROM execution_quality_policy_global_state WHERE scope='GLOBAL' FOR UPDATE",String.class);
        if(current.isEmpty() || current.getFirst()==null) throw new MSException("QUALITY_POLICY_NOT_CONFIGURED");
        var policies=jdbc.query("SELECT id,version_no,content_hash,rules_json FROM execution_quality_policy_global WHERE id=? AND status='PUBLISHED'",
                (rs,n)->new Binding(rs.getString(1),rs.getInt(2),rs.getString(3),rs.getString(4)),current.getFirst());
        if(policies.size()!=1) throw new MSException("QUALITY_POLICY_NOT_CONFIGURED");
        Binding policy=policies.getFirst(); checkHash(policy);
        jdbc.update("INSERT INTO execution_quality_attempt_policy(execution_id,task_id,policy_id,version_no,content_hash,rules_json,bound_at) VALUES (?,?,?,?,?,?,?)",
                executionId,taskId,policy.policyId(),policy.versionNo(),policy.contentHash(),policy.rulesJson(),System.currentTimeMillis());
        return policy;
    }
    public Binding findBinding(String executionId, String taskId) {
        if(executionId==null) return null;
        var rows=jdbc.query("SELECT policy_id,version_no,content_hash,rules_json FROM execution_quality_attempt_policy WHERE execution_id=? AND task_id=?",
                (rs,n)->new Binding(rs.getString(1),rs.getInt(2),rs.getString(3),rs.getString(4)),executionId,taskId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public Binding binding(String executionId, String taskId) {
        var rows=jdbc.query("SELECT policy_id,version_no,content_hash,rules_json FROM execution_quality_attempt_policy WHERE execution_id=? AND task_id=?",
                (rs,n)->new Binding(rs.getString(1),rs.getInt(2),rs.getString(3),rs.getString(4)),executionId,taskId);
        if(rows.size()!=1) throw new MSException("QUALITY_POLICY_BINDING_NOT_FOUND");
        checkHash(rows.getFirst()); return rows.getFirst();
    }
    private void checkHash(Binding policy) {
        var validated=validator.validate(policy.rulesJson());
        if(!validated.valid() || !Objects.equals(validated.contentHash(),policy.contentHash()))
            throw new MSException("QUALITY_POLICY_HASH_INVALID");
    }
    public void verifySuccess(AgentExecutionTaskDTO task, String executionId, AgentExecutionStepDTO step,
                              String assertionResult, List<String> artifactIds) {
        Binding policy=binding(executionId,task.getId());
        List<AgentExecutionArtifactDTO> artifacts=new ArrayList<>();
        for(String id:artifactIds) {
            var artifact=mapper.selectArtifactById(id);
            if(artifact==null || !Objects.equals(task.getId(),artifact.getTaskId())
                    || !Objects.equals(executionId,artifact.getExecutionId()) || !Objects.equals(step.getId(),artifact.getStepId())
                    || !"AVAILABLE".equals(artifact.getStatus()) || artifact.getSha256()==null || artifact.getSha256().length()!=64)
                throw new MSException("QUALITY_EVIDENCE_INVALID");
            artifacts.add(artifact);
        }
        if(task.getExecutionContract()==null || !Objects.equals(DigestUtils.sha256Hex(task.getExecutionContract()),task.getExecutionContractHash()))
            throw new MSException("QUALITY_CONTRACT_INVALID");
        JsonNode contract=read(task.getExecutionContract());
        JsonNode frozen=null;
        for(JsonNode c:contract.path("cases")) for(JsonNode s:c.path("steps")) {
            if(step.getId().equals(s.path("stepId").asText())) frozen=s.path("assertions");
        }
        evaluate(read(policy.rulesJson()),frozen,read(assertionResult),artifacts);
    }
    static void evaluate(JsonNode policy, JsonNode frozen, JsonNode observed, List<AgentExecutionArtifactDTO> artifacts) {
        if(frozen==null || !frozen.isArray() || frozen.isEmpty() || !observed.isArray() || observed.size()!=frozen.size())
            throw new MSException("QUALITY_ASSERTION_INVALID");
        for(JsonNode rule:policy.path("rules")) {
            JsonNode parameters=rule.path("parameters");
            if("Q-EVIDENCE-01".equals(rule.path("ruleId").asText())) {
                for(JsonNode role:parameters.path("requiredRoles")) {
                    String purpose="BEFORE_ACTION".equals(role.asText())?"BEFORE_STEP":"AFTER_STEP";
                    boolean found=artifacts.stream().anyMatch(a->purpose.equals(a.getPurpose())
                            && a.getSizeBytes()!=null && a.getSizeBytes()>0 && a.getSizeBytes()<=parameters.path("maxArtifactBytes").asLong()
                            && contains(parameters.path("allowedMimeTypes"),a.getContentType()));
                    if(!found) throw new MSException("QUALITY_EVIDENCE_REQUIRED");
                }
            } else if("Q-ASSERT-01".equals(rule.path("ruleId").asText())) {
                for(int i=0;i<frozen.size();i++) {
                    JsonNode expected=frozen.get(i); JsonNode actual=observed.get(i).get("actual");
                    String operator=expected.path("operator").asText();
                    if(actual==null || actual.isNull() || !contains(parameters.path("allowedOperators"),operator))
                        throw new MSException("QUALITY_ASSERTION_INVALID");
                    JsonNode value=expected.get("expected"); boolean pass=false;
                    if(value!=null && "EQUALS".equals(operator)) pass=value.equals(actual);
                    else if(value!=null && "IN_RANGE".equals(operator)) {
                        JsonNode range=value.isTextual()?read(value.asText()):value;
                        if(range.isArray() && range.size()==2 && range.get(0).isNumber() && range.get(1).isNumber() && actual.isNumber())
                            pass=actual.decimalValue().compareTo(range.get(0).decimalValue())>=0
                                    && actual.decimalValue().compareTo(range.get(1).decimalValue())<=0;
                    }
                    if(!pass) throw new MSException("QUALITY_ASSERTION_FAILED");
                }
            } else throw new MSException("QUALITY_RULE_UNSUPPORTED");
        }
    }
    private static boolean contains(JsonNode values,String value) {
        for(JsonNode item:values) if(item.asText().equals(value)) return true;
        return false;
    }
    private static JsonNode read(String value) {
        try { if(value!=null) { JsonNode node=new ObjectMapper().readTree(value); if(node!=null) return node; } }
        catch(Exception ignored) { /* converted to safe contract error */ }
        throw new MSException("QUALITY_ASSERTION_INVALID");
    }
    public void requireCase(AgentExecutionTaskDTO task, AgentExecutionCaseDTO executionCase, List<AgentExecutionStepDTO> steps) {
        binding(task.getCurrentExecutionId(),task.getId());
        if(!"SUCCESS".equals(executionCase.getStatus())) return;
        if(steps.isEmpty()) throw new MSException("QUALITY_ASSERTION_INVALID");
        for(var step:steps) {
            var results=jdbc.queryForList("SELECT status,assertion_result,artifact_ids FROM ai_execution_step_result WHERE execution_id=? AND step_id=? ORDER BY create_time DESC,id DESC LIMIT 1",
                    task.getCurrentExecutionId(),step.getId());
            if(results.isEmpty() || !"SUCCESS".equals(step.getStatus())) throw new MSException("QUALITY_ASSERTION_INVALID");
            var result=results.getFirst();
            if(!"SUCCESS".equals(result.get("status"))) throw new MSException("QUALITY_ASSERTION_INVALID");
            var artifactIds=new ArrayList<String>();
            JsonNode stored=read((String)result.get("artifact_ids"));
            if(!stored.isArray()) throw new MSException("QUALITY_EVIDENCE_INVALID");
            stored.forEach(id->artifactIds.add(id.asText()));
            verifySuccess(task,task.getCurrentExecutionId(),step,(String)result.get("assertion_result"),artifactIds);
        }
    }
}
