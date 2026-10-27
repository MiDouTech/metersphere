package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.service.AgentBusinessAccess;
import io.metersphere.agent.tool.*;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** State lookup deliberately exposes references only, not stored arguments or credentials. */
@Component
public class PersonalOperationGetTool implements AgentMcpToolHandler {
    private static final Map<String,String> OPERATIONS=Map.of(
        "metersphere.functional.execution.evidence.upload","functional.execution.evidence.upload",
        "metersphere.functional.execution.submit","functional.execution.submit",
        "metersphere.functional.execution.evidence.import_url","external.image.execution");
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentBusinessAccess access;
    public String name(){return "metersphere.operation.get";}
    public String description(){return "Find this Token's personal execution upload/submit result after a lost response. NOT_FOUND is not proof of non-execution: retry only with the original requestId. Requires current case read permission.";}
    public String requiredScope(){return AgentTokenScope.FUNCTIONAL_READ;}
    public Map<String,Object> inputSchema(){return AgentMcpToolSchemas.object(Map.of("projectId",AgentMcpToolSchemas.string(1,128),"caseId",AgentMcpToolSchemas.string(1,128),"toolName",Map.of("type","string","enum",new ArrayList<>(OPERATIONS.keySet())),"requestId",AgentMcpToolSchemas.requestId()),List.of("projectId","caseId","toolName","requestId"));}
    public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),true,false,true);}
    private String text(Map<String,Object> args,String key){if(!(args.get(key) instanceof String s)||s.isBlank()||s.length()>128)throw new MSException("AGENT_VALIDATION_ERROR");return s;}
    public Object execute(Map<String,Object> args){
        String project=text(args,"projectId"),id=text(args,"caseId"),key=text(args,"requestId"),tool=text(args,"toolName");
        if(!OPERATIONS.containsKey(tool)||!key.matches("[A-Za-z0-9_.:-]{1,128}"))throw new MSException("AGENT_VALIDATION_ERROR");
        access.caseProject(id,project,"FUNCTIONAL_CASE:READ",false,requiredScope());
        var rows=jdbc.queryForList("SELECT status,response_json FROM agent_idempotency_record WHERE token_id=? AND tool_name IN (?,?) AND (request_id=? OR (identity_version='legacy-ci-v1' AND request_id COLLATE utf8mb4_general_ci=? COLLATE utf8mb4_general_ci))",AgentTokenContext.get().getId(),OPERATIONS.get(tool),tool,key,key);
        if(rows.isEmpty())return Map.of("state","NOT_FOUND","outcome","UNKNOWN","retryWithOriginalRequestId",true);
        if(rows.size()!=1)return Map.of("state","UNKNOWN","retryWithOriginalRequestId",false);
        var row=rows.getFirst();
        if(!"SUCCEEDED".equals(row.get("status"))||row.get("response_json")==null)return Map.of("state",Objects.equals(row.get("status"),"RUNNING")?"IN_PROGRESS":"UNKNOWN","retryWithOriginalRequestId",false);
        Map<?,?> result=JSON.parseObject((String)row.get("response_json"),Map.class);
        String evidence=Objects.toString(result.get("evidenceId"),null),submission=Objects.toString(result.get("submissionId"),null);
        if(evidence!=null){
            var owner=jdbc.queryForList("SELECT expires_at,submission_id FROM agent_case_evidence WHERE id=? AND project_id=? AND case_id=? AND token_id=?",evidence,project,id,AgentTokenContext.get().getId());
            if(owner.size()!=1) return id.equals(result.get("caseId")) && project.equals(result.get("projectId")) ? Map.of("state","SUCCEEDED","evidenceId",evidence,"resourceState","EXPIRED") : Map.of("state","UNKNOWN","retryWithOriginalRequestId",false);
            var file=owner.getFirst();
            return Map.of("state","SUCCEEDED","evidenceId",evidence,"resourceState",file.get("submission_id")!=null?"CONSUMED":((Number)file.get("expires_at")).longValue()<=System.currentTimeMillis()?"EXPIRED":"STAGED","expiresAt",file.get("expires_at"));
        }
        if(submission!=null){
            Long owned=jdbc.queryForObject("SELECT COUNT(*) FROM agent_case_submission WHERE id=? AND project_id=? AND case_id=? AND token_id=?",Long.class,submission,project,id,AgentTokenContext.get().getId());
            if(owned==null||owned!=1)throw AgentBusinessAccess.denied();
            return Map.of("state","SUCCEEDED","submissionId",submission);
        }
        return Map.of("state","UNKNOWN","retryWithOriginalRequestId",false);
    }
}
