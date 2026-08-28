package io.metersphere.agent.service;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentExecutionMetrics {
    @Resource private JdbcTemplate jdbc;
    public void record(String name,Number value,Map<String,?> tags,String traceId){
        if(StringUtils.isBlank(name)||value==null)return;
        jdbc.update("INSERT INTO ai_execution_metric(id,metric_name,metric_value,tags_json,trace_id,create_time) VALUES (?,?,?,?,?,?)",IDGenerator.nextStr(),StringUtils.abbreviate(name,128),new BigDecimal(value.toString()),JSON.toJSONString(tags==null?Map.of():tags),StringUtils.trimToNull(traceId),System.currentTimeMillis());
    }
    public void recordModel(String taskId,String status,Long durationMs,BigDecimal cost,String traceId){Map<String,Object> tags=Map.of("taskId",taskId,"status",status);record("ai.model.invocation.count",1,tags,traceId);if(durationMs!=null)record("ai.model.duration.ms",durationMs,tags,traceId);if(cost!=null)record("ai.model.cost",cost,tags,traceId);}
    public void recordRunner(String taskId,String outcome,Long durationMs,String traceId){Map<String,Object> tags=Map.of("taskId",taskId,"outcome",outcome);record("ai.runner.execution.count",1,tags,traceId);if(durationMs!=null)record("ai.runner.duration.ms",durationMs,tags,traceId);}
    public void recordBusiness(String taskId,String verdict,boolean evidenceComplete,String traceId){record("ai.execution.business.count",1,Map.of("taskId",taskId,"verdict",verdict,"evidenceComplete",evidenceComplete),traceId);}
    public List<Map<String,Object>> summary(Long from,Long to){long start=from==null?System.currentTimeMillis()-86_400_000L:from;long end=to==null?System.currentTimeMillis():to;return jdbc.queryForList("SELECT metric_name metricName,COUNT(1) samples,SUM(metric_value) total,AVG(metric_value) average,MIN(metric_value) minimum,MAX(metric_value) maximum FROM ai_execution_metric WHERE create_time BETWEEN ? AND ? GROUP BY metric_name ORDER BY metric_name",start,end);}
}
