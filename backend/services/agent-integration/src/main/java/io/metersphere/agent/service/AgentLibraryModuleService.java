package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.functional.domain.FunctionalCaseModule;
import io.metersphere.functional.request.FunctionalCaseModuleUpdateRequest;
import io.metersphere.functional.service.FunctionalCaseModuleService;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class AgentLibraryModuleService {
    private final AgentBusinessAccess access;
    private final AgentOperationExecutor operations;
    private final FunctionalCaseModuleService modules;
    private final JdbcTemplate jdbc;
    public AgentLibraryModuleService(AgentBusinessAccess access, AgentOperationExecutor operations,
                                     FunctionalCaseModuleService modules, JdbcTemplate jdbc) {
        this.access=access; this.operations=operations; this.modules=modules; this.jdbc=jdbc;
    }
    public FunctionalCaseModule get(String projectId, String moduleId) {
        access.project(projectId,"FUNCTIONAL_CASE:READ",false,AgentTokenScope.FUNCTIONAL_READ);
        return read(projectId,moduleId,false);
    }
    private FunctionalCaseModule read(String projectId,String moduleId,boolean lock) {
        var rows=jdbc.query("SELECT * FROM functional_case_module WHERE id=? AND project_id=?"+(lock?" FOR UPDATE":""),
                BeanPropertyRowMapper.newInstance(FunctionalCaseModule.class),moduleId,projectId);
        if(rows.size()!=1)throw AgentBusinessAccess.denied();
        return rows.getFirst();
    }
    public FunctionalCaseModule write(Map<String,Object> args, boolean move) {
        String projectId=(String)args.get("projectId"), moduleId=(String)args.get("moduleId");
        if(StringUtils.isBlank(projectId)||StringUtils.isBlank(moduleId)||!(args.get("expectedUpdateTime") instanceof Number))throw new MSException("AGENT_VALIDATION_ERROR");
        String value=(String)args.get(move?"parentId":"name");
        if(StringUtils.isBlank(value)||value.length()>255)throw new MSException("AGENT_VALIDATION_ERROR");
        return operations.execute(move?"functional.module.move":"functional.module.update",(String)args.get("requestId"),args,FunctionalCaseModule.class,
                ()->{access.project(projectId,"FUNCTIONAL_CASE:READ+UPDATE",true,AgentTokenScope.CASE_MODULE_WRITE_V2);read(projectId,moduleId,false);},
                ()->{
                    modules.lockProjectTree(projectId);
                    var current=read(projectId,moduleId,true);
                    if(current.getUpdateTime()==null || current.getUpdateTime()!=((Number)args.get("expectedUpdateTime")).longValue())throw new MSException("VERSION_CONFLICT");
                    String userId=AgentTokenContext.get().getUserId();
                    if(move)modules.moveToParent(moduleId,value,userId);
                    else {var request=new FunctionalCaseModuleUpdateRequest();request.setId(moduleId);request.setName(value);modules.update(request,userId);}
                    return read(projectId,moduleId,true);
                });
    }
}
