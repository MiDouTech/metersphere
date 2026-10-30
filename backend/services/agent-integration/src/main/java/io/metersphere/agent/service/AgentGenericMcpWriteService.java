package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenProjectAccess;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Supplier;

/** Business MCP writes commit with their result; resource authorization also runs on replay. */
@Service
public class AgentGenericMcpWriteService {
    private static final Set<String> TOOLS=Set.of(
        "metersphere.functional.module.create","metersphere.functional.case.create","metersphere.functional.case.batch_create",
        "metersphere.functional.attachment.attach","metersphere.functional.attachment.delete",
        "metersphere.bug.update","metersphere.bug.transition","metersphere.bug.comment.create",
        "metersphere.bug.case.relate","metersphere.bug.relate_case","metersphere.bug.case.unrelate",
        "metersphere.bug.attachment.attach","metersphere.bug.attachment.delete",
        "metersphere.test_plan.update","metersphere.test_plan.disassociate_cases",
        "metersphere.project.create","metersphere.project.members.add");
    @org.springframework.beans.factory.annotation.Value("${agent.mcp.business-writes-enabled:true}")
    private boolean writesEnabled=true;
    private final AgentOperationExecutor operations;
    private final AgentBusinessAccess access;
    private final AgentProjectService projects;
    private final JdbcTemplate jdbc;
    public AgentGenericMcpWriteService(AgentOperationExecutor operations,AgentBusinessAccess access,AgentProjectService projects,JdbcTemplate jdbc){this.operations=operations;this.access=access;this.projects=projects;this.jdbc=jdbc;}
    public static boolean handles(String name){return TOOLS.contains(name);}
    public Map<String,Object> execute(String name,Map<String,Object> raw,Supplier<Map<String,Object>> action){
        if(!handles(name))throw new MSException("MCP_TOOL_NOT_FOUND");
        var args=new LinkedHashMap<>(raw);
        if(!name.equals("metersphere.project.create")) {
            String project;
            if(name.equals("metersphere.bug.comment.create") && !args.containsKey("projectId")) {
                var roots=jdbc.queryForList("SELECT project_id FROM bug WHERE id=? AND deleted=0",String.class,text(args,"bugId"));
                if(roots.size()!=1)throw AgentBusinessAccess.denied();project=roots.getFirst();
            } else project=projects.resolveProjectId((String)args.get("projectId"));
            args.put("projectId",project);
        }
        String canonical=name.equals("metersphere.bug.relate_case")?"metersphere.bug.case.relate":name;
        if(canonical.equals("metersphere.bug.case.relate") && (!(args.get("caseType") instanceof String type)||type.isBlank()))args.put("caseType",io.metersphere.sdk.constants.CaseType.FUNCTIONAL_CASE.getKey());
        return operations.executeWithLegacyArguments("generic."+canonical.substring("metersphere.".length()),text(args,"requestId"),args,Map.class,
                ()->authorize(canonical,args),()->{
                    if(!writesEnabled)throw new MSException("MCP_BUSINESS_WRITE_DISABLED");
                    if(canonical.equals("metersphere.bug.case.unrelate")) {
                        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM bug_relation_case WHERE id=?",Long.class,text(args,"relationId"));
                        if(count==null||count!=1)throw new MSException("RESOURCE_NOT_FOUND");
                    }
                    return action.get();
                },raw);
    }
    private void authorize(String name,Map<String,Object> args){
        if(name.startsWith("metersphere.project.")){authorizeOrganization(name,args);return;}
        String project=text(args,"projectId");
        if(name.startsWith("metersphere.test_plan.")) {
            String permission=name.endsWith(".update")?PermissionConstants.TEST_PLAN_READ_UPDATE:PermissionConstants.TEST_PLAN_READ_ASSOCIATION;
            access.project(project,permission,true,AgentTokenScope.PLAN_WRITE);
            Long count=jdbc.queryForObject("SELECT COUNT(*) FROM test_plan WHERE id=? AND project_id=?",Long.class,text(args,"testPlanId"),project);
            if(count==null||count!=1)throw AgentBusinessAccess.denied();return;
        }
        if(name.startsWith("metersphere.functional.attachment.")) {
            String caseId=name.endsWith(".delete")?resourceId(args,"caseId"):text(args,"caseId");
            access.caseProject(caseId,project,PermissionConstants.FUNCTIONAL_CASE_READ_UPDATE,true,AgentTokenScope.CASE_ATTACHMENT);return;
        }
        if(name.startsWith("metersphere.functional.")) {
            access.project(project,PermissionConstants.FUNCTIONAL_CASE_READ_ADD,true,AgentTokenScope.CASE_WRITE);return;
        }
        String scope=name.contains(".attachment.")?AgentTokenScope.BUG_ATTACHMENT:name.contains(".comment.")?AgentTokenScope.BUG_COMMENT:name.contains(".case.")?AgentTokenScope.BUG_RELATE:AgentTokenScope.BUG_WRITE;
        access.project(project,name.contains(".comment.")?PermissionConstants.PROJECT_BUG_COMMENT:PermissionConstants.PROJECT_BUG_UPDATE,true,scope);
        if(name.endsWith(".unrelate")) {
            var roots=jdbc.queryForList("SELECT b.project_id FROM bug_relation_case r LEFT JOIN bug b ON b.id=r.bug_id WHERE r.id=?",String.class,text(args,"relationId"));
            if(!roots.isEmpty() && (roots.size()!=1||!project.equals(roots.getFirst())))throw AgentBusinessAccess.denied();
        } else {
            String bugId=name.equals("metersphere.bug.attachment.delete")?resourceId(args,"bugId"):text(args,"bugId");
            Long count=jdbc.queryForObject("SELECT COUNT(*) FROM bug WHERE id=? AND project_id=? AND deleted=0",Long.class,bugId,project);
            if(count==null||count!=1)throw AgentBusinessAccess.denied();
        }
    }
    // Keep the public payload/hash unchanged so old successful requests still replay.
    static String resourceId(Map<String,Object> args,String legacyKey){
        String resource=text(args,"resourceId");
        if(args.containsKey(legacyKey) && !resource.equals(text(args,legacyKey)))throw new MSException("AGENT_VALIDATION_ERROR");
        return resource;
    }
    private void authorizeOrganization(String name,Map<String,Object> args){
        var token=access.activeToken(AgentTokenScope.PROJECT_WRITE);
        String org;
        boolean create=name.endsWith(".create");
        if(create)org=text(args,"organizationId");
        else {
            String project=text(args,"projectId");
            if(!AgentTokenProjectAccess.allows(token,project))throw AgentBusinessAccess.denied();
            var roots=jdbc.queryForList("SELECT organization_id FROM project WHERE id=? AND deleted=0 AND enable=1",String.class,project);
            if(roots.size()!=1)throw AgentBusinessAccess.denied();org=roots.getFirst();
        }
        access.organization(org,create?PermissionConstants.ORGANIZATION_PROJECT_READ_ADD:PermissionConstants.ORGANIZATION_PROJECT_MEMBER_ADD,
            create?PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ_ADD:PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_MEMBER_ADD,AgentTokenScope.PROJECT_WRITE);
    }
    private static String text(Map<String,Object> args,String key){if(!(args.get(key) instanceof String value)||value.isBlank()||value.length()>128)throw new MSException("AGENT_VALIDATION_ERROR");return value;}
}
