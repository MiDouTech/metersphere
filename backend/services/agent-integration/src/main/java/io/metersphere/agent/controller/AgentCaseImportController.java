package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentCaseImportService;
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
    @PostMapping("/precheck")
    public Map<String,Object> precheck(@RequestPart("request") FunctionalCaseImportRequest request,@RequestPart("file") MultipartFile file,@RequestParam String requestId) {
        return service.precheck(request,file,requestId,SessionUtils.getUserId(),"WEB");
    }
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable String id,@RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="10") int pageSize) {
        return service.get(id,SessionUtils.getUserId(),"WEB",current,pageSize);
    }
    @PostMapping("/{id}/{action}") public Map<String,Object> action(@PathVariable String id,@PathVariable String action) {
        return service.action(id,action,SessionUtils.getUserId(),"WEB");
    }
}
