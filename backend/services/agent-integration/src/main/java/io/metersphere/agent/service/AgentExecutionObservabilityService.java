package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AgentExecutionObservabilityService {
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentExecutionService executionService;

    public Map<String,Object> detail(String taskId){
        AgentExecutionTaskDTO task=executionService.get(taskId);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("traceId",task.getTraceId());
        result.put("assertionContracts",assertionContracts(task));
        result.put("resultSync",safeRows(jdbc.queryForList("SELECT execution_id,execution_case_id,business_status,quality_status,status,error_code,attempt_count,next_retry_at,updated_at FROM ai_execution_case_sync WHERE task_id=? AND project_id=? ORDER BY created_at DESC,execution_case_id LIMIT 100",taskId,task.getProjectId())));
        result.put("qualityEvaluations",safeRows(jdbc.queryForList("SELECT id,execution_id,step_id,stage,outcome,rule_code,request_id,trace_id,created_at,contract_hash,policy_hash,payload_hash FROM ai_quality_evaluation WHERE task_id=? AND project_id=? ORDER BY created_at DESC,id DESC LIMIT 100",taskId,task.getProjectId())));
        result.put("preflight",preflight(task.getPreflightId()));
        result.put("modelInvocations",safeRows(jdbc.queryForList("SELECT id,gateway_request_id,resolved_offering_snapshot,status,finish_reason,input_tokens,output_tokens,reasoning_tokens,cached_tokens,cost_amount,currency,retry_count,ttft_ms,duration_ms,error_code,error_message,create_time,finish_time FROM ai_model_invocation WHERE task_id=? ORDER BY create_time",taskId)));
        result.put("runnerLeases",safeRows(jdbc.queryForList("SELECT id,execution_id,runner_id,executor_channel,executor_type,lease_owner_type,attempt,status,accepted_time,expire_time,last_heartbeat_time,closed_at AS close_time,released_reason FROM ai_runner_lease WHERE task_id=? ORDER BY create_time",taskId)));
        result.put("dataLeases",safeRows(jdbc.queryForList("SELECT id,execution_id,dataset_id,data_key,namespace,status,expires_at,released_at,version,create_time,update_time FROM ai_test_data_lease WHERE task_id=? ORDER BY create_time",taskId)));
        result.put("cleanupJobs",safeRows(jdbc.queryForList("SELECT c.id,c.lease_id,c.cleanup_type,c.status,c.attempt_count,c.next_retry_at,c.error_code,c.error_message,c.created_at,c.finished_at FROM ai_test_data_cleanup c JOIN ai_test_data_lease l ON l.id=c.lease_id WHERE l.task_id=? ORDER BY c.created_at",taskId)));
        result.put("audits",safeRows(jdbc.queryForList("SELECT actor_type,actor_id,action,target_type,target_id,before_json,after_json,trace_id,create_time FROM ai_execution_audit WHERE (target_type='TASK' AND target_id=?) OR trace_id=? ORDER BY create_time",taskId,task.getTraceId())));
        return result;
    }
    private List<Map<String,Object>> assertionContracts(AgentExecutionTaskDTO task){
        if(task.getExecutionContract()==null)return List.of();
        try {
            var root=new com.fasterxml.jackson.databind.ObjectMapper().readTree(task.getExecutionContract());
            List<Map<String,Object>> rows=new ArrayList<>();
            for(var c:root.path("cases"))for(var step:c.path("steps"))for(var assertion:step.path("assertions")){
                rows.add(Map.of("caseId",c.path("caseId").asText(),"stepId",step.path("stepId").asText(),
                        "assertionId",assertion.path("assertionId").asText("v1-position"),"valueType",assertion.path("valueType").asText("v1"),
                        "operator",assertion.path("operator").asText(),"expected",io.metersphere.agent.security.AgentSensitiveDataSanitizer.sanitize(assertion.path("expected").asText()),
                        "unit",assertion.path("unit").asText(),"scale",assertion.path("scale").asText()));
            }
            return rows;
        }catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new io.metersphere.sdk.exception.MSException("QUALITY_CONTRACT_INVALID");}
    }
    private Map<String,Object> preflight(String id){if(id==null)return Map.of();List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,status,checks_json,resolved_scope_json,snapshot_json,original_scope_count,expanded_scope_count,scope_expansion_rate,asset_snapshot_hash,environment_profile_version,model_profile_version,CASE WHEN JSON_VALID(snapshot_json) THEN JSON_UNQUOTE(JSON_EXTRACT(snapshot_json,'$.promptTemplate.id')) ELSE NULL END AS prompt_template_version_id,blocked_reason,blocked_detail,trace_id,started_at,finished_at,expires_at FROM ai_execution_preflight WHERE id=?",id);return rows.isEmpty()?Map.of():safeRow(rows.getFirst());}
    private List<Map<String,Object>> safeRows(List<Map<String,Object>> rows){return rows.stream().map(this::safeRow).toList();}
    private Map<String,Object> safeRow(Map<String,Object> row){Map<String,Object> safe=new LinkedHashMap<>();row.forEach((key,value)->safe.put(camel(key),parseJson(value)));return safe;}
    private Object parseJson(Object value){if(!(value instanceof String s))return value;if((s.startsWith("{")&&s.endsWith("}"))||(s.startsWith("[")&&s.endsWith("]"))){try{return JSON.parseObject(s,Object.class);}catch(Exception ignored){return s;}}return s;}
    private String camel(String key){StringBuilder b=new StringBuilder();boolean upper=false;for(char c:key.toCharArray()){if(c=='_'){upper=true;}else{b.append(upper?Character.toUpperCase(c):c);upper=false;}}return b.toString();}
}
