package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentGovernedExecutionService;
import io.metersphere.agent.service.AgentProtocolPublicationService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping({"/governed-execution","/api/governed-execution","/agent/v1/execution-sessions","/api/agent/v1/execution-sessions"})
public class AgentGovernedExecutionController {
    private final AgentGovernedExecutionService service;
    private final AgentProtocolPublicationService publication;
    public AgentGovernedExecutionController(AgentGovernedExecutionService service,AgentProtocolPublicationService publication){this.service=service;this.publication=publication;}
    @PostMapping public Map<String,Object> start(@RequestBody Map<String,Object> request){return service.start(request);}
    @GetMapping public java.util.List<Map<String,Object>> list(@RequestParam String organizationId,@RequestParam(required=false)String projectId,@RequestParam(required=false)String taskId){return service.list(organizationId,projectId,taskId);}
    @PostMapping("/operations/{operation}") public Map<String,Object> operate(@PathVariable String operation,@RequestBody Map<String,Object> request){
        if("result.sync".equals(operation))return publication.syncRequest(request);
        var result=java.util.Set.of("checkpoint.get","action.status","result.status.get","result.history.get").contains(operation)?service.read(operation,request):service.write(operation,request);
        if("result.verify".equals(operation)&&Boolean.TRUE.equals(result.get("verificationAccepted"))&&Boolean.TRUE.equals(result.get("replayed"))){
            var current=service.read("result.status.get",Map.of("sessionId",request.get("sessionId"),"caseExecutionId",request.get("caseExecutionId")));var instance=(Map<?,?>)((java.util.List<?>)current.get("instances")).getFirst();var response=new java.util.LinkedHashMap<>(result);response.put("syncStatus",instance.get("syncStatus"));response.put("disputed",Boolean.TRUE.equals(instance.get("disputed")));response.put("formalResultAccepted","SYNCED".equals(instance.get("syncStatus"))&&!Boolean.TRUE.equals(instance.get("disputed")));return response;
        }
        if("result.verify".equals(operation)&&Boolean.TRUE.equals(result.get("verificationAccepted"))) {
            var response=new java.util.LinkedHashMap<>(result);var sync=publication.sync((String)request.get("sessionId"),(String)request.get("caseExecutionId"));response.putAll(sync);response.put("formalResultAccepted","SYNCED".equals(sync.get("syncStatus")));return response;
        }
        return result;
    }
}
