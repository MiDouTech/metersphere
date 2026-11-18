package io.metersphere.agent.controller;
import io.metersphere.agent.service.AgentKnowledgeMigrationService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping({"/test-knowledge/migration","/api/test-knowledge/migration"})
public class AgentKnowledgeMigrationController {
    private final AgentKnowledgeMigrationService service;
    public AgentKnowledgeMigrationController(AgentKnowledgeMigrationService service){this.service=service;}
    @GetMapping("/preview")public Map<String,Object> preview(@RequestParam String projectId,@RequestParam String assetId,@RequestParam String versionId){return service.preview(projectId,assetId,versionId);}
    @PostMapping public Map<String,Object> migrate(@RequestParam String organizationId,@RequestBody Map<String,Object> request){return service.migrate(organizationId,request);}
    @PostMapping("/withdraw")public Map<String,Object> withdraw(@RequestParam String organizationId,@RequestBody Map<String,Object> request){return service.withdraw(organizationId,request);}
}
