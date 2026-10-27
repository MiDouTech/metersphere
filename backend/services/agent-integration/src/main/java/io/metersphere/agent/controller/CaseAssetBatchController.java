package io.metersphere.agent.controller;
import io.metersphere.agent.dto.CaseAssetBatchRequest;
import io.metersphere.agent.service.CaseAssetBatchService;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/case-asset-batches")
@RequiresPermissions("CASE_ASSET:READ")
public class CaseAssetBatchController {
 private final CaseAssetBatchService service;
 public CaseAssetBatchController(CaseAssetBatchService service){this.service=service;}
 @PostMapping public Map<String,Object> create(@Valid @RequestBody CaseAssetBatchRequest request){return service.create(request);}
 @GetMapping("/latest") public Map<String,Object> latest(){return service.latest();}
 @GetMapping("/{id}") public Map<String,Object> get(@PathVariable String id){return service.get(id);}
 @PostMapping("/{id}/retry") public Map<String,Object> retry(@PathVariable String id){return service.retry(id);}
 @PostMapping("/{id}/advance") public Map<String,Object> advance(@PathVariable String id){return service.advance(id);}
}
