package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentKnowledgeService;
import io.metersphere.agent.service.AgentStructuredContractService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping({"/test-knowledge","/api/test-knowledge","/agent/v1/test-knowledge","/api/agent/v1/test-knowledge"})
public class AgentKnowledgeController {
    private final AgentKnowledgeService service;private final AgentStructuredContractService contracts;
    public AgentKnowledgeController(AgentKnowledgeService service,AgentStructuredContractService contracts){this.service=service;this.contracts=contracts;}
    @GetMapping public Map<String,Object> search(@RequestParam String organizationId,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String type,@RequestParam(defaultValue="")String scope,@RequestParam(defaultValue="")String system,@RequestParam(defaultValue="")String module){return service.search(organizationId,keyword,page,size,Map.of("type",type,"scope",scope,"system",system,"module",module));}
    @GetMapping("/capabilities")public java.util.List<String> capabilities(@RequestParam String organizationId){return service.capabilities(organizationId);}
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable String id,@RequestParam String organizationId,@RequestParam(required=false)Long version){return service.get(organizationId,id,version);}
    @GetMapping("/{id}/history") public Map<String,Object> history(@PathVariable String id,@RequestParam String organizationId){return service.history(organizationId,id);}
    @PostMapping public Map<String,Object> save(@RequestParam String organizationId,@RequestBody Map<String,Object> request){return service.save(organizationId,request);}
    @PostMapping("/{action:publish|disable|delete|revoke|restore}") public Map<String,Object> govern(@PathVariable String action,@RequestParam String organizationId,@RequestBody Map<String,Object> request){return service.govern(organizationId,action,request);}
    @GetMapping("/contracts/{caseId}")public Map<String,Object> contract(@PathVariable String caseId,@RequestParam String projectId,@RequestParam(required=false)Long version){return contracts.get(projectId,caseId,version);}
    @GetMapping("/contracts/{caseId}/conversion")public Map<String,Object> conversion(@PathVariable String caseId,@RequestParam String projectId){return contracts.conversion(projectId,caseId);}
    @PostMapping("/contracts/{caseId}")public Map<String,Object> saveContract(@PathVariable String caseId,@RequestParam String projectId,@RequestBody Map<String,Object> request){return contracts.save(projectId,caseId,request);}
}
