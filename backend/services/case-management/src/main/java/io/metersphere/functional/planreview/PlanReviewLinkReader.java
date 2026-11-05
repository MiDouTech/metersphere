package io.metersphere.functional.planreview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Batched list decoration; it does not create links or fetch opposite details without read permission. */
@Service
@Transactional(readOnly=true)
public class PlanReviewLinkReader {
    private final JdbcTemplate jdbc;
    private final PlanReviewAuthorization authorization;
    private final PlanReviewPlanStateResolver states;
    public PlanReviewLinkReader(JdbcTemplate jdbc,PlanReviewAuthorization authorization,PlanReviewPlanStateResolver states){this.jdbc=jdbc;this.authorization=authorization;this.states=states;}

    public Map<String,Map<String,Object>> read(boolean planSide,String project,Collection<String> ids,String actor){
        if(ids.isEmpty())return Map.of();
        if(ids.stream().anyMatch(id->id==null||id.isBlank()))throw inaccessible();
        authorization.requireRead(actor,project,planSide);
        boolean opposite=authorization.current(actor,project,false).permissions().contains(planSide?PlanReviewPolicy.Permission.REVIEW_READ:PlanReviewPolicy.Permission.PLAN_READ);
        var ordered=ids.stream().distinct().sorted().toList();
        var result=new LinkedHashMap<String,Map<String,Object>>();
        for(int offset=0;offset<ordered.size();offset+=100){
            var batch=ordered.subList(offset,Math.min(offset+100,ordered.size()));
            String placeholders=String.join(",",Collections.nCopies(batch.size(),"?"));
            var arguments=new ArrayList<Object>();arguments.add(project);arguments.addAll(batch);
            Long ownCount=jdbc.queryForObject("SELECT COUNT(*) FROM "+(planSide?"test_plan":"case_review")+" WHERE project_id=? AND id IN ("+placeholders+")",Long.class,arguments.toArray());
            if(ownCount==null||ownCount!=batch.size())throw inaccessible();
            for(String id:batch)result.put(id,Map.of("relationStatus","NONE","restricted",false));
            String owner=planSide?"active_plan_id":"active_review_id";
            var rows=jdbc.queryForList("SELECT * FROM plan_review_relation WHERE project_id=? AND "+owner+" IN ("+placeholders+") AND state='ACTIVE'",arguments.toArray());
            if(!opposite){for(var row:rows)result.put((String)row.get(owner),Map.of("relationStatus","RESTRICTED","restricted",true));continue;}
            for(var row:rows){
                String target=(String)row.get(planSide?"review_id":"plan_id");
                var objects=jdbc.queryForList("SELECT * FROM "+(planSide?"case_review":"test_plan")+" WHERE id=? AND project_id=?",target,project);
                if(objects.size()!=1){result.put((String)row.get(owner),Map.of("relationStatus","INVALID","restricted",false));continue;}
                var object=planSide?objects.getFirst():states.resolve(objects.getFirst());
                var view=new LinkedHashMap<String,Object>();view.put("relationStatus","ACTIVE");view.put("restricted",false);
                var description=new LinkedHashMap<String,Object>();for(String field:List.of("id","num","name","status"))if(object.get(field)!=null)description.put(field,object.get(field));
                view.put("target",description);view.put("syncState",row.get("sync_state"));
                view.put("sourceVersion",row.get("source_version"));view.put("appliedVersion",row.get("applied_version"));
                result.put((String)row.get(owner),view);
            }
        }
        return result;
    }
    private static PlanReviewFailure inaccessible(){return new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"对象不可访问");}
}
