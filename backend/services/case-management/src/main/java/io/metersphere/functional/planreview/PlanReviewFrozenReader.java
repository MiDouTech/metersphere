package io.metersphere.functional.planreview;

import io.metersphere.sdk.util.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class PlanReviewFrozenReader {
    public static final Set<String> STATISTIC_FIELDS=Set.of("id","status","passThreshold","passRate","executeRate","pass","successCount","errorCount","fakeErrorCount","blockCount","pendingCount","caseTotal","functionalCaseCount","apiCaseCount","apiScenarioCount","completionBlockedByReview");
    private final JdbcTemplate jdbc;
    public PlanReviewFrozenReader(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Map<String,Object> frame(String side,String id){
        var rows=jdbc.queryForList("SELECT id,object_state,relation_id,generation,target_fingerprint,source_version,applied_version,create_time,snapshot FROM plan_review_frozen_snapshot WHERE object_side=? AND object_id=? ORDER BY create_time DESC,id DESC LIMIT 1",side,id);
        if(rows.isEmpty())return Map.of();
        var row=new LinkedHashMap<>(rows.getFirst()); Object bytes=row.remove("snapshot");
        if(bytes instanceof java.sql.Blob blob){try{bytes=blob.getBytes(1,Math.toIntExact(blob.length()));}catch(java.sql.SQLException error){throw new PlanReviewFailure("SNAPSHOT_READ_FAILED",503,"冻结快照暂不可读取");}}
        if(!(bytes instanceof byte[] value))throw new PlanReviewFailure("SNAPSHOT_READ_FAILED",503,"冻结快照暂不可读取");
        row.put("data",JSON.parseObject(new String(value,StandardCharsets.UTF_8),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));
        return row;
    }
    public Map<String,Object> statistics(String plan){
        var frame=frame("plan",plan); if(frame.isEmpty())return Map.of();
        Object data=frame.get("data");
        if(data instanceof Map<?,?> map && map.get("sourceStatistics") instanceof Map<?,?> stats){
            var result=new LinkedHashMap<String,Object>();
            for(String key:STATISTIC_FIELDS)if(stats.get(key)!=null)result.put(key,stats.get(key));return result;
        }
        return Map.of();
    }
}
