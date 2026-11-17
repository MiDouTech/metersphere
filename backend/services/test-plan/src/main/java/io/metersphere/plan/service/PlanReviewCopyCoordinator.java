package io.metersphere.plan.service;

import io.metersphere.functional.planreview.*;
import io.metersphere.plan.domain.*;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.plan.dto.response.PlanReviewCopyOutcome;
import io.metersphere.sdk.util.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Orchestration has no outer transaction: every actual source plan commits independently. */
@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class PlanReviewCopyCoordinator {
    @jakarta.annotation.Resource private TestPlanMapper mapper;
    @jakarta.annotation.Resource private JdbcTemplate jdbc;
    @jakarta.annotation.Resource private PlanReviewCreationJournal journal;
    @jakarta.annotation.Resource private PlanReviewCopyRequestJournal requests;
    @jakarta.annotation.Resource private TestPlanBatchOperationService copies;
    @jakarta.annotation.Resource private PlanReviewAuthorization authorization;
    private record Source(TestPlan plan,String parent,Long position,List<String> children) { }
    private record Prepared(Source source,PlanReviewCreationJournal.Reservation reservation,String target,String targetType,String failure) { }
    public Map<String,Object> scope(String project,List<TestPlan> roots,List<String> requested,String actor){
        authorization.requireRead(actor,project,true);
        if(roots.size()!=new HashSet<>(requested).size())throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"复制来源不可访问");
        long real=0,groups=0;
        for(var source:roots){
            requireSource(source,project);
            if("GROUP".equals(source.getType())){
                groups++;var query=new TestPlanExample();query.createCriteria().andGroupIdEqualTo(source.getId()).andStatusNotEqualTo("ARCHIVED");
                var children=mapper.selectByExample(query);for(var child:children){requireSource(child,project);if(!"TEST_PLAN".equals(child.getType()))throw fail("INVALID_SCOPE","子计划类型待核实");}
                real+=children.size();
            }else real++;
        }
        return Map.of("realPlanCount",real,"groupCount",groups,"settingsRequired",real>0);
    }

    public PlanReviewCopyOutcome execute(String project,List<TestPlan> roots,String target,String targetType,String key,PlanReviewSettings settings,String actor,boolean single){
        authorization.requireRead(actor,project,true);
        if(!single){
            if("MODULE".equals(targetType)&&!"root".equals(target)&&jdbc.queryForList("SELECT id FROM test_plan_module WHERE id=? AND project_id=?",String.class,target,project).size()!=1)throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"复制目标不可访问");
            if("GROUP".equals(targetType)&&!"NONE".equals(target)&&jdbc.queryForList("SELECT id FROM test_plan WHERE id=? AND project_id=? AND type='GROUP'",String.class,target,project).size()!=1)throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"复制目标不可访问");
        }
        var rootIds=roots.stream().map(TestPlan::getId).sorted().toList();
        if(new HashSet<>(rootIds).size()!=rootIds.size())throw fail("INVALID_SCOPE","复制来源不能重复");
        var sources=new ArrayList<Source>();
        for(var root:roots.stream().sorted(Comparator.comparing(TestPlan::getId)).toList()){
            requireSource(root,project);
            if("GROUP".equals(root.getType())){
                var previous=stored(project,actor,"COPY_GROUP",PlanReviewCreationJournal.childKey(key,root.getId()));
                List<String> children;
                if(previous!=null){
                    Object frozen=previous.get("childScope");
                    if(!(frozen instanceof List<?> list))throw fail("COPY_SCOPE_UNAVAILABLE","原复制范围不可读取，不能静默重新扫描替代");
                    children=list.stream().map(Object::toString).toList();
                }else{
                    var query=new TestPlanExample();query.createCriteria().andGroupIdEqualTo(root.getId()).andStatusNotEqualTo("ARCHIVED");query.setOrderByClause("pos asc,id asc");
                    var found=mapper.selectByExample(query);for(var child:found)requireSource(child,project);
                    children=found.stream().map(TestPlan::getId).toList();
                }
                sources.add(new Source(root,null,null,children));
                long position=0;
                for(String id:children){
                    var child=mapper.selectByPrimaryKey(id);requireSource(child,project);
                    if(!"TEST_PLAN".equals(child.getType()))throw fail("INVALID_SCOPE","计划组不能作为真实子计划创建评审");
                    sources.add(new Source(child,root.getId(),++position*5000,List.of()));
                }
            }else sources.add(new Source(root,null,null,List.of()));
        }
        if(sources.stream().map(source->source.plan.getId()).distinct().count()!=sources.size())throw fail("INVALID_SCOPE","复制范围不能重复包含同一真实计划");
        boolean real=sources.stream().anyMatch(source->"TEST_PLAN".equals(source.plan.getType()));
        if(real){
            int initial=sources.stream().filter(source->"TEST_PLAN".equals(source.plan.getType())).anyMatch(source->journal.effectiveMembers(source.plan.getId(),project)>0)?1:0;
            journal.validate(project,actor,settings,initial);
        }
        var input=new LinkedHashMap<String,Object>();input.put("roots",rootIds);input.put("single",single);input.put("target",target);input.put("targetType",targetType);input.put("reviewSettings",settings);
        String requestId=requests.prepare(project,actor,key,JSON.toJSONString(input));
        var reservations=new LinkedHashMap<String,PlanReviewCreationJournal.Reservation>();
        var prepared=new ArrayList<Prepared>();
        for(var source:sources){
            String id=source.plan.getId(),destination=target,kind=targetType;
            if(source.parent!=null){
                var parent=reservations.get(source.parent);
                if(parent==null){prepared.add(new Prepared(source,null,null,null,"父计划组尚未成功预留；原成功结果保留"));continue;}
                destination=parent.planId();kind="GROUP";
            }else if(single){destination="GROUP".equals(source.plan.getType())?source.plan.getModuleId():source.plan.getGroupId();kind="GROUP".equals(source.plan.getType())?"MODULE":"GROUP";}
            String operationType="GROUP".equals(source.plan.getType())?"COPY_GROUP":"COPY_PAIR";
            var prior=stored(project,actor,operationType,PlanReviewCreationJournal.childKey(key,id));
            if(prior!=null){String original=(String)prior.get("destination");int separator=original==null?-1:original.indexOf(':');if(separator<1)throw fail("COPY_SCOPE_UNAVAILABLE","原复制目标不可读取");kind=original.substring(0,separator);destination=original.substring(separator+1);}
            var payload=new LinkedHashMap<String,Object>();payload.put("sourceId",id);payload.put("destination",kind+":"+destination);payload.put("reviewSettings",settings);
            if("GROUP".equals(source.plan.getType()))payload.put("childScope",source.children);
            if(source.position!=null)payload.put("copyPosition",source.position);
            try{
                int count="GROUP".equals(source.plan.getType())?0:journal.effectiveMembers(id,project);
                var reservation=journal.prepare(project,actor,"PLAN_SERVICE","GROUP".equals(source.plan.getType())?"COPY_GROUP":"COPY_PAIR",PlanReviewCreationJournal.childKey(key,id),JSON.toJSONString(payload),settings,count);
                reservations.put(id,reservation);prepared.add(new Prepared(source,reservation,destination,kind,null));
            }catch(RuntimeException failure){prepared.add(new Prepared(source,null,destination,kind,safe(failure)));}
        }
        var results=new ArrayList<PlanReviewCopyOutcome.Item>();var created=new HashSet<String>();
        try(var context=journal.copyContext(reservations,settings)){
            for(var item:prepared){
                var source=item.source;var reservation=item.reservation;
                if(item.failure!=null){results.add(result(source,reservation,false,null,item.failure));continue;}
                if(source.parent!=null&&!created.contains(source.parent)){results.add(result(source,reservation,false,null,"父计划组未创建成功；本项保留原预留等待恢复"));continue;}
                try{
                    copies.copyReservedItem(project,source.plan.getId(),item.target,item.targetType,actor,"GROUP".equals(source.plan.getType()),source.position);
                    created.add(source.plan.getId());
                    var operation=jdbc.queryForMap("SELECT o.*,r.sync_state relation_sync_state,r.state relation_state FROM plan_review_operation o LEFT JOIN plan_review_relation r ON r.id=o.relation_id AND r.generation=o.generation AND r.project_id=o.project_id WHERE o.id=? AND o.project_id=?",reservation.id(),project);
                    results.add(result(source,reservation,true,operation,null));
                }catch(RuntimeException failure){results.add(result(source,reservation,false,null,safe(failure)));}
            }
        }
        var access=authorization.current(actor,project,false);
        boolean planRead=access.permissions().contains(PlanReviewPolicy.Permission.PLAN_READ),reviewRead=access.permissions().contains(PlanReviewPolicy.Permission.REVIEW_READ);
        var visible=results.stream().map(item->new PlanReviewCopyOutcome.Item(item.sourceId(),item.sourceType(),planRead?item.sourceName():null,planRead?item.sourceNum():null,item.operationId(),planRead?item.planId():null,planRead&&reviewRead?item.reviewId():null,item.stage(),item.state(),planRead&&reviewRead?item.syncState():null,item.created(),item.safeReason())).toList();
        String root=single&&!roots.isEmpty()&&created.contains(roots.getFirst().getId())&&planRead?reservations.get(roots.getFirst().getId()).planId():null;
        long count=rootIds.stream().filter(created::contains).count();
        return new PlanReviewCopyOutcome(requestId,root,count,created.size(),results.size()-created.size(),visible);
    }
    private PlanReviewCopyOutcome.Item result(Source source,PlanReviewCreationJournal.Reservation reservation,boolean created,Map<String,Object> operation,String reason){
        return new PlanReviewCopyOutcome.Item(source.plan.getId(),source.plan.getType(),source.plan.getName(),source.plan.getNum(),reservation==null?null:reservation.id(),created?reservation.planId():null,created?reservation.reviewId():null,operation==null?"NOT_COMMITTED":(String)operation.get("stage"),operation==null?"FAILED":(String)operation.get("state"),operation==null?null:"ENDED".equals(operation.get("relation_state"))?"ENDED":(String)operation.get("relation_sync_state"),created,reason);
    }
    private Map<String,Object> stored(String project,String actor,String type,String key){
        var rows=jdbc.queryForList("SELECT original_actor,executing_actor,request_json FROM plan_review_operation WHERE project_id=? AND source_kind='PLAN_SERVICE' AND operation_type=? AND request_key=?",project,type,key);
        if(rows.isEmpty())return null;var row=rows.getFirst();
        if(!actor.equals(row.get("original_actor"))&&!actor.equals(row.get("executing_actor")))throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"原复制请求不可访问");
        return JSON.parseObject((String)row.get("request_json"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
    }
    private static void requireSource(TestPlan source,String project){
        if(source==null||!project.equals(source.getProjectId()))throw new PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"复制来源不可访问");
        if(!Set.of("TEST_PLAN","GROUP").contains(source.getType()))throw fail("INVALID_SCOPE","复制来源类型待核实");
    }
    private static String safe(RuntimeException failure){return failure instanceof PlanReviewFailure denied?denied.getMessage():"本项未确认完成，请保留原请求重试或查询原操作";}
    private static PlanReviewFailure fail(String code,String message){return new PlanReviewFailure(code,409,message);}
}
