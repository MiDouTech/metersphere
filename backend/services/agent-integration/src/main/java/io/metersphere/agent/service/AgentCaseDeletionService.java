package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentCaseDeletionService {
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentBusinessAccess access;
    @Resource private FunctionalCaseService cases;
    @Resource private AgentExecutionService executions;
    @Resource private io.metersphere.functional.service.FunctionalCaseTrashService trash;
    public record Target(String id, String name, long updateTime, String moduleId) { }
    public record Preview(String previewId, String projectId, List<Target> targets, List<String> activeTaskIds,
                          String state, long expiresAt, String confirmationPath, String message, boolean webRequest) { }
    private static final String DELETE_PERMISSION="FUNCTIONAL_CASE:READ+DELETE";

    public Object trash(String project,int current,int size) {
        access.project(project,"FUNCTIONAL_CASE:READ",false,AgentTokenScope.FUNCTIONAL_READ);
        if(current<1||size<1||size>100)throw new MSException("分页参数无效");
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM functional_case WHERE project_id=? AND deleted=1",Long.class,project);
        var items=jdbc.queryForList("SELECT id AS caseId,name,module_id AS moduleId,version_id AS versionId,update_time AS updateTime,delete_time AS deleteTime FROM functional_case WHERE project_id=? AND deleted=1 ORDER BY delete_time DESC,id LIMIT ? OFFSET ?",project,size,(current-1L)*size);
        return Map.of("total",Objects.requireNonNull(total),"current",current,"pageSize",size,"items",items);
    }

    public Map<String,Object> restore(String project,String caseId,long expectedVersion,String destination,String scope) {
        access.project(project,DELETE_PERMISSION,true,AgentTokenScope.CASE_RESTORE_V2);
        var rows=jdbc.queryForList("SELECT * FROM functional_case WHERE id=? AND project_id=? AND deleted=1 FOR UPDATE",caseId,project);
        if(rows.size()!=1)throw new MSException("回收站用例不存在或已恢复");
        var source=rows.getFirst();
        if(((Number)source.get("update_time")).longValue()!=expectedVersion)throw new MSException("回收站用例已变更，请刷新后重试");
        if(!Set.of("CURRENT","ALL").contains(scope))throw new MSException("恢复范围必须为CURRENT或ALL");
        String family=Objects.toString(source.get("ref_id"),"");
        if(family.isBlank())family=caseId;
        var targets="ALL".equals(scope)?jdbc.queryForList("SELECT id,module_id FROM functional_case WHERE project_id=? AND COALESCE(NULLIF(ref_id,''),id)=? AND deleted=1 ORDER BY id FOR UPDATE",project,family)
                :List.of(Map.of("id",caseId,"module_id",Objects.toString(source.get("module_id"),"")));
        if(targets.size()>100)throw new MSException("一次最多恢复100个版本，请分批恢复");
        for(var target:targets) {
            String module=destination==null||destination.isBlank()?Objects.toString(target.get("module_id"),""):destination;
            if(!"NONE".equals(module)) {
                Long count=jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_module WHERE id=? AND project_id=?",Long.class,module,project);
                if(count==null||count!=1)throw new MSException("原目录已不存在，请明确选择当前项目的恢复目录");
            }
            jdbc.update("UPDATE functional_case SET module_id=? WHERE id=?",module,target.get("id"));
        }
        var ids=targets.stream().map(item->(String)item.get("id")).toList();
        trash.recoverVersions(ids,AgentTokenContext.get().getUserId());
        return Map.of("items",ids.stream().map(id->Map.of("caseId",id,"restored",true)).toList(),"message","用例已恢复，原执行任务不会重新启动");
    }

    public Preview preview(String project, List<String> ids, String scope) {
        access.project(project, DELETE_PERMISSION, true, AgentTokenScope.CASE_DELETE_V2);
        var token=AgentTokenContext.get();
        return prepare(project,ids,scope,token.getId(),token.getUserId());
    }

    public Preview previewWeb(io.metersphere.functional.request.FunctionalCaseBatchRequest request,String user) {
        if(!access.userHasPermission(user,request.getProjectId(),DELETE_PERMISSION))throw AgentBusinessAccess.denied();
        return prepare(request.getProjectId(),cases.doSelectIds(request,request.getProjectId()),
                Boolean.TRUE.equals(request.getDeleteAll())?"ALL":"CURRENT","WEB",user);
    }

    private Preview prepare(String project,List<String> ids,String scope,String tokenId,String user) {
        if (ids == null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size()!=ids.size()
                || !Set.of("CURRENT","ALL").contains(scope)) throw new MSException("删除范围无效，最多选择100条用例");
        List<Target> targets=new ArrayList<>();
        for(String id:ids) {
            Long count=jdbc.queryForObject("SELECT COUNT(*) FROM functional_case WHERE id=? AND project_id=? AND deleted=0",Long.class,id,project);
            if(count==null||count!=1)throw new MSException("选择的用例不存在或已被删除，请刷新列表");
            String condition="id=?";
            if("ALL".equals(scope)) condition="COALESCE(NULLIF(ref_id,''),id)=(SELECT family FROM (SELECT COALESCE(NULLIF(ref_id,''),id) AS family FROM functional_case WHERE id=?) selected_version)";
            var selected=jdbc.query("SELECT id,name,update_time,module_id FROM functional_case WHERE "+condition+" AND project_id=? AND deleted=0 ORDER BY id",
                    (rs,index)->new Target(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getString(4)),id,project);
            for(var item:selected)if(targets.stream().noneMatch(existing->existing.id().equals(item.id())))targets.add(item);
        }
        if(targets.isEmpty()||targets.size()>100)throw new MSException("展开后的版本范围超过100条或已不存在，请缩小范围");
        targets.sort(Comparator.comparing(Target::id));
        var tasks=activeTasks(targets);
        String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();
        jdbc.update("INSERT INTO agent_case_deletion_preview(id,token_id,user_id,project_id,targets_json,tasks_json,create_time,expires_at) VALUES (?,?,?,?,?,?,?,?)",
                id,tokenId,user,project,JSON.toJSONString(targets),JSON.toJSONString(tasks),now,now+900000);
        return webGet(id,user);
    }

    public Preview webGet(String id,String user) {
        var row=owned(id,user,false);
        return view(row);
    }

    /** Called only through the authenticated web controller, never exposed as an MCP decision tool. */
    public Preview decide(String id,String user,String decision) {
        var row=owned(id,user,true);
        if(!"PENDING".equals(row.get("state")))throw new MSException("该请求已处理，请刷新查看结果");
        if(!Set.of("CANCEL","WAIT","STOP","DELETE").contains(Objects.toString(decision,"")))throw new MSException("请选择有效的删除处理方式");
        var targets=targets(row);
        if(!"CANCEL".equals(decision)) validateTargets(targets,(String)row.get("project_id"));
        var active=activeTasks(targets);
        var originally=JSON.parseArray((String)row.get("tasks_json"),String.class);
        if(!originally.containsAll(active))throw new MSException("预检后出现了新任务，请让 Agent 重新发起删除预检");
        if("DELETE".equals(decision)&&!active.isEmpty())throw new MSException("存在未结束任务，请选择等待或停止后删除");
        if("STOP".equals(decision)) {
            if(!access.userHasPermission(user,(String)row.get("project_id"),"AI_EXECUTION:RUN"))throw AgentBusinessAccess.denied();
            for(String task:active)executions.cancel(task,"用户确认停止后删除关联用例");
        }
        jdbc.update("UPDATE agent_case_deletion_preview SET state=?,decision=?,decision_time=? WHERE id=?",
                "CANCEL".equals(decision)?"CANCELLED":"WAIT".equals(decision)?"WAITING":"APPROVED",decision,System.currentTimeMillis(),id);
        return webGet(id,user);
    }

    public Object execute(String id) {
        var token=AgentTokenContext.get();var row=owned(id,token.getUserId(),true);
        if(!token.getId().equals(row.get("token_id")))throw AgentBusinessAccess.denied();
        String project=(String)row.get("project_id");
        access.project(project,DELETE_PERMISSION,true,AgentTokenScope.CASE_DELETE_V2);
        return executeConfirmed(id,row,token.getUserId(),project);
    }

    public Object executeWeb(String id,String user) {
        var row=owned(id,user,true);
        if(!"WEB".equals(row.get("token_id")))throw AgentBusinessAccess.denied();
        return executeConfirmed(id,row,user,(String)row.get("project_id"));
    }

    private Object executeConfirmed(String id,Map<String,Object> row,String user,String project) {
        if("COMPLETED".equals(row.get("state")))return JSON.parseObject((String)row.get("result_json"),Map.class);
        if(!Set.of("APPROVED","WAITING").contains(row.get("state")))return view(row);
        var targets=targets(row);validateTargets(targets,project);
        var active=activeTasks(targets);
        if(!JSON.parseArray((String)row.get("tasks_json"),String.class).containsAll(active))throw new MSException("出现新的执行任务，请重新发起删除预检");
        if(!active.isEmpty())return view(row);
        List<String> ids=targets.stream().map(Target::id).toList();
        cases.handDeleteFunctionalCase(ids,false,user,project);
        var result=Map.of("previewId",id,"state","COMPLETED","items",ids.stream().map(caseId->Map.of("caseId",caseId,"deleted",true)).toList(),
                "message","用例已移入回收站；取消任务只阻止结果发布，不代表外部浏览器已退出");
        jdbc.update("UPDATE agent_case_deletion_preview SET state='COMPLETED',result_json=? WHERE id=?",JSON.toJSONString(result),id);
        return result;
    }

    private Map<String,Object> owned(String id,String user,boolean lock) {
        var rows=jdbc.queryForList("SELECT * FROM agent_case_deletion_preview WHERE id=? AND user_id=?"+(lock?" FOR UPDATE":""),id,user);
        if(rows.size()!=1)throw AgentBusinessAccess.denied();var row=rows.getFirst();
        if(!access.userHasPermission(user,(String)row.get("project_id"),DELETE_PERMISSION))throw AgentBusinessAccess.denied();
        if(((Number)row.get("expires_at")).longValue()<=System.currentTimeMillis()&&!"COMPLETED".equals(row.get("state")))throw new MSException("删除确认已过期，请重新发起预检");
        return row;
    }
    private List<Target> targets(Map<String,Object> row){return JSON.parseArray((String)row.get("targets_json"),Target.class);}
    private void validateTargets(List<Target> targets,String project) {
        for(var target:targets) {
            var rows=jdbc.queryForList("SELECT update_time FROM functional_case WHERE id=? AND project_id=? AND deleted=0 FOR UPDATE",target.id(),project);
            if(rows.size()!=1||((Number)rows.getFirst().get("update_time")).longValue()!=target.updateTime())throw new MSException("用例已变更，请重新发起删除预检");
        }
    }
    private List<String> activeTasks(List<Target> targets) {
        String markers=String.join(",",Collections.nCopies(targets.size(),"?"));
        return jdbc.queryForList("SELECT DISTINCT t.id FROM ai_execution_task t JOIN ai_execution_case c ON c.task_id=t.id WHERE c.case_id IN ("+markers+") AND t.status NOT IN ('SUCCESS','PARTIAL_SUCCESS','FAILED','CANCELED','EXPIRED') ORDER BY t.id",String.class,targets.stream().map(Target::id).toArray());
    }
    private Preview view(Map<String,Object> row) {
        String id=(String)row.get("id");var targets=targets(row);
        return new Preview(id,(String)row.get("project_id"),targets,activeTasks(targets),(String)row.get("state"),((Number)row.get("expires_at")).longValue(),
                "/#/case-deletion/confirm/"+id,"请核对目标版本。只有当前用户在确认页面作出选择后，才能继续删除。","WEB".equals(row.get("token_id")));
    }
}
