package io.metersphere.agent.service;

import io.metersphere.agent.service.gateway.GatewayPlanningResponse;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class AgentModelInvocationService {
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private AgentExecutionMetrics metrics;
    public String start(String taskId,String traceId,String profileId,String logicalModel,String promptVersion,String requestHash){
        String id=IDGenerator.nextStr();jdbcTemplate.update("""
          INSERT INTO ai_model_invocation(id,task_id,trace_id,model_profile_id,logical_model_public_id,prompt_version_id,
          request_hash,status,create_time) VALUES (?,?,?,?,?,?,?,'STARTED',?)
          """,id,taskId,traceId,profileId,logicalModel,StringUtils.defaultIfBlank(promptVersion,"gateway-managed"),requestHash,System.currentTimeMillis());return id;
    }
    public void recordGatewayAccepted(String id,GatewayPlanningResponse response){
        if(response==null)return;
        jdbcTemplate.update("""
          UPDATE ai_model_invocation SET gateway_request_id=COALESCE(?,gateway_request_id),
          resolved_offering_snapshot=COALESCE(?,resolved_offering_snapshot),status=CASE WHEN status='STARTED' THEN 'ACCEPTED' ELSE status END,
          finish_reason=COALESCE(?,finish_reason),retry_count=?,ttft_ms=COALESCE(?,ttft_ms),duration_ms=COALESCE(?,duration_ms)
          WHERE id=? AND status IN ('STARTED','ACCEPTED')
          """,StringUtils.trimToNull(response.getGatewayRequestId()),io.metersphere.sdk.util.JSON.toJSONString(response.getResolvedOffering()),
                response.getFinishReason(),response.getRetries()==null?0:response.getRetries(),response.getTtftMs(),response.getDurationMs(),id);
    }
    public void recordUsage(String id,GatewayPlanningResponse response){
        if(response==null)return;
        GatewayPlanningResponse.Usage u=response.getUsage()==null?new GatewayPlanningResponse.Usage():response.getUsage();
        GatewayPlanningResponse.Cost c=response.getCost()==null?new GatewayPlanningResponse.Cost():response.getCost();
        jdbcTemplate.update("""
          UPDATE ai_model_invocation SET input_tokens=?,output_tokens=?,reasoning_tokens=?,cached_tokens=?,cost_amount=?,currency=?
          WHERE id=? AND status IN ('STARTED','ACCEPTED')
          """,value(u.getInputTokens()),value(u.getOutputTokens()),value(u.getReasoningTokens()),value(u.getCachedTokens()),c.getAmount(),c.getCurrency(),id);
    }
    public void recordFailure(String id,String code,GatewayPlanningResponse response){
        recordGatewayAccepted(id,response);recordUsage(id,response);
        int changed=jdbcTemplate.update("UPDATE ai_model_invocation SET status='FAILED',error_code=?,error_message='MAP Gateway planning failed',finish_time=? WHERE id=? AND status IN ('STARTED','ACCEPTED')",code,System.currentTimeMillis(),id);
        if(changed==1)recordMetric(id,"FAILED",response==null?null:response.getDurationMs(),response==null||response.getCost()==null?null:response.getCost().getAmount());
    }
    public void finish(String id, GatewayPlanningResponse r){
        recordGatewayAccepted(id,r);recordUsage(id,r);
        int changed=jdbcTemplate.update("UPDATE ai_model_invocation SET status='SUCCESS',error_code=NULL,error_message=NULL,finish_time=? WHERE id=? AND status IN ('STARTED','ACCEPTED')",System.currentTimeMillis(),id);
        if(changed==1)recordMetric(id,"SUCCESS",r.getDurationMs(),r.getCost()==null?null:r.getCost().getAmount());
    }
    public void fail(String id,String code){recordFailure(id,code,null);}
    public BigDecimal taskCost(String taskId){BigDecimal v=jdbcTemplate.queryForObject("SELECT COALESCE(SUM(cost_amount),0) FROM ai_model_invocation WHERE task_id=?",BigDecimal.class,taskId);return v==null?BigDecimal.ZERO:v;}
    public Map<String,Object> get(String id,String projectId){List<Map<String,Object>> rows=jdbcTemplate.queryForList("SELECT i.* FROM ai_model_invocation i JOIN ai_execution_task t ON t.id=i.task_id WHERE i.id=? AND t.project_id=?",id,projectId);if(rows.isEmpty())throw new io.metersphere.sdk.exception.MSException("MODEL_INVOCATION_NOT_FOUND");return safeRow(rows.getFirst());}
    public Map<String,Object> usage(String projectId,Long from,Long to){long start=from==null?0:from;long end=to==null?System.currentTimeMillis():to;return jdbcTemplate.queryForMap("SELECT COUNT(1) invocationCount,COALESCE(SUM(input_tokens),0) inputTokens,COALESCE(SUM(output_tokens),0) outputTokens,COALESCE(SUM(reasoning_tokens),0) reasoningTokens,COALESCE(SUM(cached_tokens),0) cachedTokens,COALESCE(SUM(cost_amount),0) costAmount,MAX(currency) currency FROM ai_model_invocation i JOIN ai_execution_task t ON t.id=i.task_id WHERE t.project_id=? AND i.create_time BETWEEN ? AND ?",projectId,start,end);}
    private Map<String,Object> safeRow(Map<String,Object> row){java.util.LinkedHashMap<String,Object> safe=new java.util.LinkedHashMap<>(row);safe.remove("request_hash");return safe;}
    private void recordMetric(String id,String status,Long duration,BigDecimal cost){Map<String,Object> identity=jdbcTemplate.queryForMap("SELECT task_id,trace_id FROM ai_model_invocation WHERE id=?",id);metrics.recordModel((String)identity.get("task_id"),status,duration,cost,(String)identity.get("trace_id"));}
    private long value(Long v){return v==null?0:v;}
}
