package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentCaseDeletionService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/ai/case-deletions","/api/ai/case-deletions"})
@RequiresPermissions("FUNCTIONAL_CASE:READ+DELETE")
public class AgentCaseDeletionController {
    @Resource private AgentCaseDeletionService service;
    @PostMapping("/preview") public AgentCaseDeletionService.Preview preview(@RequestBody io.metersphere.functional.request.FunctionalCaseBatchRequest request){return service.previewWeb(request,SessionUtils.getUserId());}
    @PostMapping("/{id}/execute") public Object execute(@PathVariable String id){return service.executeWeb(id,SessionUtils.getUserId());}
    public record Decision(String decision) { }
    @GetMapping("/{id}") public AgentCaseDeletionService.Preview get(@PathVariable String id){return service.webGet(id,SessionUtils.getUserId());}
    @PostMapping("/{id}/decision") public AgentCaseDeletionService.Preview decide(@PathVariable String id,@RequestBody Decision request){return service.decide(id,SessionUtils.getUserId(),request.decision());}
}
