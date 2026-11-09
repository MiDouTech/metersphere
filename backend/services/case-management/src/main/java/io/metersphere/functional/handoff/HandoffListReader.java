package io.metersphere.functional.handoff;

import io.metersphere.functional.planreview.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Batched list summaries contain no external task IDs, person IDs or snapshots. */
@Service
public class HandoffListReader {
    private final JdbcTemplate jdbc;private final PlanReviewAuthorization auth;
    public HandoffListReader(JdbcTemplate jdbc,PlanReviewAuthorization auth){this.jdbc=jdbc;this.auth=auth;}
    public void decorate(boolean planSide,String project,List<String> ids,Map<String,Map<String,Object>> result,String actor){
        boolean dual=auth.current(actor,project,false).permissions().containsAll(Set.of(PlanReviewPolicy.Permission.PLAN_READ,PlanReviewPolicy.Permission.REVIEW_READ));
        for(String id:ids){var view=new LinkedHashMap<>(result.getOrDefault(id,Map.of()));view.put("handoff",Map.of("restricted",!dual,"state","NONE","sourceChanged",false));result.put(id,view);}
        if(!dual)return;
        for(int offset=0;offset<ids.size();offset+=100){
            var batch=ids.subList(offset,Math.min(offset+100,ids.size()));String markers=String.join(",",Collections.nCopies(batch.size(),"?"));
            String owner=planSide?"d.plan_id":"r.review_id";
            String relation=planSide?"":" JOIN plan_review_relation r ON r.plan_id=d.plan_id AND r.project_id=d.project_id AND r.state='ACTIVE' AND r.active_review_id=r.review_id";
            var args=new ArrayList<Object>();args.add(project);args.addAll(batch);
            var rows=jdbc.queryForList("SELECT "+owner+" object_id,d.state,d.source_changed FROM hifox_http_delivery d"+relation+" WHERE d.project_id=? AND "+owner+" IN ("+markers+")",args.toArray());
            for(var row:rows){String id=Objects.toString(row.get("object_id"));if(result.containsKey(id))result.get(id).put("handoff",Map.of("restricted",false,"state",row.get("state"),"sourceChanged",HandoffSourceReader.truth(row.get("source_changed"))));}
        }
    }
}
