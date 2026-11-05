package io.metersphere.functional.planreview;
import java.util.List;
import java.util.Map;

/** Explicit selections, never an automatic choice between conflicting historical targets. */
public record PlanReviewLegacyDecision(String projectId,String requestKey,String manifestHash,
        Map<String,String> groups,Map<String,Job> jobs) {
    public record Job(String action,String executingActor,PlanReviewSettings reviewSettings,String executionMode) {
        public Job(String action,String actor,PlanReviewSettings settings){this(action,actor,settings,"ORIGINAL");}
        public Job { executionMode=executionMode==null?"ORIGINAL":executionMode;if(!List.of("ORIGINAL","WEB").contains(executionMode))throw new IllegalArgumentException("请选择保留原执行上下文或经负责人批准的网页登录接手"); }
    }
    public PlanReviewLegacyDecision {
        if(projectId==null||projectId.isBlank()||requestKey==null||requestKey.isBlank()||requestKey.length()>128||manifestHash==null||!manifestHash.matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException("明确项目、原请求标识和迁移清单哈希后才能预览");
        groups=groups==null?Map.of():java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(groups));
        jobs=jobs==null?Map.of():java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(jobs));
        if(groups.isEmpty()&&jobs.isEmpty()||groups.size()+jobs.size()>1000)throw new IllegalArgumentException("请选择一至一千项明确的历史关系或任务");
        for(var entry:groups.entrySet())if(entry.getKey()==null||entry.getKey().isBlank()||entry.getValue()==null||!List.of("KEEP","ISOLATE","RELEASE").contains(entry.getValue()))throw new IllegalArgumentException("历史关系必须明确选择保留原对、隔离或解除");
        for(var entry:jobs.entrySet()){
            var job=entry.getValue();if(entry.getKey()==null||entry.getKey().isBlank()||job==null||job.action()==null||!List.of("ADOPT","ISOLATE","TERMINATE").contains(job.action()))throw new IllegalArgumentException("历史任务必须明确选择受控移交、隔离或终止");
            if("ADOPT".equals(job.action())&&(job.executingActor()==null||job.executingActor().isBlank()))throw new IllegalArgumentException("移交原任务必须明确当前执行人，不会自动替换账号");
        }
    }
}
