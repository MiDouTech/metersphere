package io.metersphere.agent.controller;
import java.util.Map;
import io.metersphere.agent.service.DataAssetLegacyAutoService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping({"/test-data-assets/legacy/auto","/api/test-data-assets/legacy/auto"})
public class DataAssetLegacyAutoController {
 private final DataAssetLegacyAutoService service;
 public DataAssetLegacyAutoController(DataAssetLegacyAutoService service){this.service=service;}
 @GetMapping("/status") public Map<String,Object> status(@RequestParam(required=false) String projectId){return service.status(projectId);}
 @PostMapping("/run") public Map<String,Object> run(@RequestParam(required=false) String projectId,@RequestBody(required=false) Map<String,Object> body){return service.run(projectId,body!=null && Boolean.TRUE.equals(body.get("retryFailed")));}
}
