package io.metersphere.functional.asset.controller;
import io.metersphere.functional.asset.dto.CaseAssetSystemRequest;
import io.metersphere.functional.asset.service.CaseAssetSystemService;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/case-asset")
public class CaseAssetSystemController {
    @Resource private CaseAssetSystemService service;
    @GetMapping("/systems") @RequiresPermissions("CASE_ASSET:READ")
    public Map<String,Object> list(){return service.list();}
    @PostMapping("/systems") @RequiresPermissions("CASE_ASSET:READ+ADD")
    public Map<String,Object> add(@RequestBody CaseAssetSystemRequest r){return service.saveSystem(null,r);}
    @PutMapping("/systems/{id}") @RequiresPermissions("CASE_ASSET:READ+UPDATE")
    public Map<String,Object> update(@PathVariable String id,@RequestBody CaseAssetSystemRequest r){return service.saveSystem(id,r);}
    @DeleteMapping("/systems/{id}") @RequiresPermissions("CASE_ASSET:READ+DELETE")
    public void delete(@PathVariable String id){service.deleteSystem(id);}
    @PostMapping("/systems/{id}/modules") @RequiresPermissions("CASE_ASSET:READ+ADD")
    public Map<String,Object> addModule(@PathVariable String id,@RequestBody CaseAssetSystemRequest r){return service.saveModule(id,null,r);}
    @PutMapping("/systems/{id}/modules/{moduleId}") @RequiresPermissions("CASE_ASSET:READ+UPDATE")
    public Map<String,Object> updateModule(@PathVariable String id,@PathVariable String moduleId,@RequestBody CaseAssetSystemRequest r){return service.saveModule(id,moduleId,r);}
    @DeleteMapping("/systems/{id}/modules/{moduleId}") @RequiresPermissions("CASE_ASSET:READ+DELETE")
    public void deleteModule(@PathVariable String id,@PathVariable String moduleId){service.deleteModule(id,moduleId);}
    @PostMapping("/system-classification") @RequiresPermissions("CASE_ASSET:READ+UPDATE")
    public Map<String,Object> classify(@RequestBody CaseAssetSystemRequest r){return service.classify(r);}
}
