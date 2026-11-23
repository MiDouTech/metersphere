package io.metersphere.agent.service;

import io.metersphere.agent.tool.AgentMcpToolHandler;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

/** Fresh web-equivalent RBAC for legacy execution tools, including idempotent response replay. */
@Service
public class AgentRuntimeRoleAccess {
    private final JdbcTemplate jdbc;
    private final AgentBusinessAccess access;
    private final AgentProjectService projects;

    public AgentRuntimeRoleAccess(JdbcTemplate jdbc, AgentBusinessAccess access, AgentProjectService projects) {
        this.jdbc=jdbc; this.access=access; this.projects=projects;
    }

    public void authorize(AgentMcpToolHandler handler, Map<String,Object> args) {
        String name=handler.name();
        String metadataPermission=switch(name) {
            case "metersphere.environment.profile.list", "metersphere.environment.profile.get",
                 "metersphere.page_object.get", "metersphere.business_flow.get" -> "AI_EXECUTION:READ";
            case "metersphere.credential.metadata.list" -> "AI_CREDENTIAL:READ_METADATA";
            default -> null;
        };
        if(metadataPermission!=null) {
            access.project(projects.resolveProjectId(text(args,"projectId")),metadataPermission,false);
            return;
        }
        if (!(name.startsWith("metersphere.execution.") || name.startsWith("metersphere.task.")
                || name.startsWith("metersphere.human_request.") || name.startsWith("metersphere.artifact."))) return;
        String task=consistent(text(args,"taskId"),text(args,"executionTaskId"));
        for(String key:new String[]{"leaseId","checkpointId"}) {
            String id=text(args,key);
            if(id!=null) task=consistent(task,lookup("leaseId".equals(key)?"ai_runner_lease":"ai_execution_checkpoint","task_id",id));
        }
        String project=task==null?null:lookup("ai_execution_task","project_id",task);
        String preflight=text(args,"preflightId");
        if(preflight!=null) project=consistent(project,lookup("ai_execution_preflight","project_id",preflight));
        project=consistent(project,text(args,"projectId"));
        project=projects.resolveProjectId(project);
        if(StringUtils.isBlank(project)) throw AgentBusinessAccess.denied();
        boolean write=!handler.readOnlyHint();
        String permission="AI_EXECUTION:"+(write?"RUN":"READ");
        if(name.equals("metersphere.execution.cancel"))permission="AI_EXECUTION:CANCEL";
        if(name.equals("metersphere.execution.resume"))permission="AI_EXECUTION:LOGIN";
        access.project(project,permission,write);
    }

    private String lookup(String table,String column,String id) {
        var values=jdbc.queryForList("SELECT "+column+" FROM "+table+" WHERE id=?",String.class,id);
        if(values.size()!=1 || StringUtils.isBlank(values.getFirst()))throw AgentBusinessAccess.denied();
        return values.getFirst();
    }
    private static String consistent(String first,String second) {
        if(first!=null && second!=null && !first.equals(second))throw AgentBusinessAccess.denied();
        return first==null?second:first;
    }
    private static String text(Map<String,Object> args,String key) {
        Object value=args.get(key);
        if(value==null)return null;
        if(!(value instanceof String text) || StringUtils.isBlank(text))throw AgentBusinessAccess.denied();
        return text;
    }
}
