package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentCaseImportService;
import io.metersphere.agent.service.ImportFollowupCoordinator;
import io.metersphere.agent.dto.ImportFollowupRequest;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping({"/ai/case-imports","/api/ai/case-imports"})
@RequiresPermissions("FUNCTIONAL_CASE:READ+IMPORT")
public class AgentCaseImportController {
    @Resource private AgentCaseImportService service;
    @Resource private ImportFollowupCoordinator followups;
    @PostMapping("/precheck")
    public Map<String,Object> precheck(@RequestPart("request") FunctionalCaseImportRequest request,@RequestPart("file") MultipartFile file,@RequestParam String requestId) {
        return service.precheck(request,file,requestId,SessionUtils.getUserId(),"WEB");
    }
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable String id,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int pageSize) {
        Map<String,Object> result = new java.util.LinkedHashMap<>(service.get(id,SessionUtils.getUserId(),"WEB",current,pageSize));
        result.put("followup",followups.status(id,SessionUtils.getUserId()));
        return result;
    }
    @GetMapping("/followup-options")
    public Map<String,Object> projectOptions(@RequestParam String projectId){return followups.optionsForProject(projectId,SessionUtils.getUserId());}
    @GetMapping("/{id}/followup")
    public Map<String,Object> followupStatus(@PathVariable String id){return followups.status(id,SessionUtils.getUserId());}
    @GetMapping("/{id}/followup-options")
    public Map<String,Object> followupOptions(@PathVariable String id) { return followups.options(id,SessionUtils.getUserId()); }

    @PostMapping("/{id}/followup/retry")
    public void retryFollowup(@PathVariable String id) { followups.retry(id,SessionUtils.getUserId()); }

    @PostMapping("/{id}/commit")
    public Map<String,Object> commit(@PathVariable String id,
            @jakarta.validation.Valid @RequestBody(required=false) ImportFollowupRequest followup) {
        followups.prepare(id,SessionUtils.getUserId(),followup);
        return service.action(id,"commit",SessionUtils.getUserId(),"WEB");
    }

    @PostMapping("/{id}/{action:stop|retry}")
    public Map<String,Object> action(@PathVariable String id,@PathVariable String action) {
        var result=service.action(id,action,SessionUtils.getUserId(),"WEB");
        if("retry".equals(action))followups.retry(id,SessionUtils.getUserId());
        return result;
    }
}
