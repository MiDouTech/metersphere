package io.metersphere.agent.controller;
import io.metersphere.agent.service.AgentProtocolAdministrationService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping({"/governed-execution/administration","/api/governed-execution/administration"})
public class AgentProtocolAdministrationController {
    private final AgentProtocolAdministrationService service;
    private final io.metersphere.agent.service.AgentProtocolPolicyService policies;
    public AgentProtocolAdministrationController(AgentProtocolAdministrationService service,io.metersphere.agent.service.AgentProtocolPolicyService policies){this.service=service;this.policies=policies;}
    @GetMapping public Map<String,Object> inspect(@RequestParam String organizationId){return service.inspect(organizationId);}
    @PostMapping("/{type:identity|subject|grant}")public Map<String,Object> save(@PathVariable String type,@RequestParam String organizationId,@RequestBody Map<String,Object> request){return service.save(organizationId,type,request);}
    @PostMapping("/policy")public Map<String,Object> policy(@RequestParam String organizationId,@RequestBody Map<String,Object> request){return policies.configure(organizationId,request);}
}
