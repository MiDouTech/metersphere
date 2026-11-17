package io.metersphere.agent.controller;
import io.metersphere.agent.service.AgentProtocolPolicyService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping({"/governed-execution/authorizations","/api/governed-execution/authorizations"})
public class AgentProtocolAuthorizationController {
    private final AgentProtocolPolicyService service;
    public AgentProtocolAuthorizationController(AgentProtocolPolicyService service){this.service=service;}
    @GetMapping("/{type}/{id}")public Map<String,Object> current(@PathVariable String type,@PathVariable String id,@RequestParam String projectId,@RequestParam String environmentId){return service.currentAuthorization(projectId,type,id,environmentId);}
    @PostMapping("/{type}/{id}")public Map<String,Object> save(@PathVariable String type,@PathVariable String id,@RequestParam String projectId,@RequestBody Map<String,Object> request){return service.authorize(projectId,type,id,request);}
}
