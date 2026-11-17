package io.metersphere.plan.service;

import io.metersphere.functional.planreview.PlanReviewPlanStateProvider;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlanReviewPlanStateAdapter implements PlanReviewPlanStateProvider {
    @Resource @Lazy
    private TestPlanStatisticsService statistics;

    @Resource
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override public String executionRevision(String planId){
        var rows=new java.util.ArrayList<Object>();
        for(String table:List.of("test_plan_functional_case","test_plan_api_case","test_plan_api_scenario")){
            rows.add(jdbc.queryForList("SELECT id,last_exec_result,last_exec_time FROM "+table+" WHERE test_plan_id=? ORDER BY id",planId));
        }
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(io.metersphere.sdk.util.JSON.toJSONString(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }

    @Override
    public String businessState(String planId) {
        var rows = statistics.calculateRate(List.of(planId));
        return rows.size() == 1 ? rows.getFirst().getStatus() : "UNKNOWN";
    }
    @Override public String completionState(String planId){var rows=statistics.calculateRateForCompletionSnapshot(List.of(planId));return rows.size()==1?rows.getFirst().getStatus():"UNKNOWN";}
    @Override
    public java.util.Map<String,Object> statisticsSnapshot(String planId){
        var rows=statistics.calculateRate(List.of(planId));
        if(rows.size()!=1)return java.util.Map.of("status","UNKNOWN");
        var all=io.metersphere.sdk.util.JSON.parseObject(io.metersphere.sdk.util.JSON.toJSONString(rows.getFirst()),new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>(){});
        var result=new java.util.LinkedHashMap<String,Object>();
        for(String field:io.metersphere.functional.planreview.PlanReviewFrozenReader.STATISTIC_FIELDS)if(all.get(field)!=null)result.put(field,all.get(field));
        return result;
    }
}
