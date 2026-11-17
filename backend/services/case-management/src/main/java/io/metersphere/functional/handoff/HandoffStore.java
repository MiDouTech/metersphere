package io.metersphere.functional.handoff;

import io.metersphere.sdk.util.JSON;
import io.metersphere.functional.planreview.PlanReviewPersistence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static io.metersphere.functional.handoff.HandoffSourceReader.text;

@Service
public class HandoffStore {
    final JdbcTemplate jdbc;
    private final PlanReviewPersistence source;
    public HandoffStore(JdbcTemplate jdbc,PlanReviewPersistence source){this.jdbc=jdbc;this.source=source;}
    Map<String,Object> lock(String project){
        if (!io.metersphere.system.service.PlatformNotificationScope.ID.equals(project)) source.lockProject(project);
        jdbc.update("INSERT INTO hifox_http_config(project_id,update_time) VALUES(?,?) ON DUPLICATE KEY UPDATE project_id=project_id",project,System.currentTimeMillis());
        return jdbc.queryForMap("SELECT * FROM hifox_http_config WHERE project_id=? FOR UPDATE",project);
    }
    String configurationScope(Map<String,Object> delivery) {
        return delivery.get("config_scope") == null ? text(delivery,"project_id") : text(delivery,"config_scope");
    }
    Map<String,Object> deliveryConfig(Map<String,Object> delivery) { return lock(configurationScope(delivery)); }
    long next(String project){jdbc.update("UPDATE hifox_http_config SET event_sequence=event_sequence+1 WHERE project_id=?",project);return jdbc.queryForObject("SELECT event_sequence FROM hifox_http_config WHERE project_id=?",Long.class,project);}
    Map<String,Object> delivery(String project,String key){
        lock(io.metersphere.system.service.PlatformNotificationScope.ID);
        return jdbc.queryForList("SELECT * FROM hifox_http_delivery WHERE project_id=? AND business_key=? FOR UPDATE",project,key).stream().findFirst().orElse(Map.of());
    }
    boolean legacy(String project,String plan){return jdbc.queryForObject("SELECT COUNT(*) FROM hifox_handoff_delivery WHERE project_id=? AND plan_id=? AND NOT EXISTS(SELECT 1 FROM hifox_http_legacy_migration m WHERE m.legacy_key=hifox_handoff_delivery.business_key)",Long.class,project,plan)>0;}
    List<Map<String,Object>> legacyRows(String project,String plan){return jdbc.queryForList("SELECT business_key,state,attempt_json,task_id,revision FROM hifox_handoff_delivery WHERE project_id=? AND plan_id=? FOR UPDATE",project,plan);}
    boolean provenLegacyUnsent(String project,String plan){
        var rows=legacyRows(project,plan);if(rows.isEmpty())return false;
        for(var row:rows){
            Map<?,?> a;try{a=decode(row.get("attempt_json"),Map.class);}catch(RuntimeException invalid){return false;}
            if(a==null || !(a.get("attempts") instanceof Number n) || n.longValue()!=0 || a.get("requestId")!=null || row.get("task_id")!=null
                    || !Set.of("READY","PAUSED","CANCELLED").contains(Objects.toString(row.get("state"))))return false;
            for(String table:List.of("hifox_handoff_webhook","hifox_handoff_attempt_history","hifox_handoff_notice"))
                if(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE business_key=?",Long.class,row.get("business_key"))>0)return false;
        }
        return true;
    }
    void audit(String project,String key,String actor,String action,long revision){jdbc.update("INSERT INTO hifox_handoff_audit(id,project_id,business_key,actor,action,revision,create_time) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),project,key,actor,action,revision,System.currentTimeMillis());}
    static String json(Object value){return JSON.toJSONString(value);}
    static <T>T decode(Object value,Class<T> type){return JSON.parseObject(Objects.toString(value),type);}
}
