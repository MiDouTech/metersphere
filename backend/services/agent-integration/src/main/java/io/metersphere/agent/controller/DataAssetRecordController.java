package io.metersphere.agent.controller;
import io.metersphere.agent.service.DataAssetRecordService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping({"/test-data-assets", "/api/test-data-assets"})
public class DataAssetRecordController {
 private final DataAssetRecordService service;
 public DataAssetRecordController(DataAssetRecordService service){this.service=service;}
 public record Selection(String versionId,List<String> recordKeys,String requestId) {}
 @GetMapping("/{id}/records")
 public Map<String,Object> page(@PathVariable String id,@RequestParam(required=false) String versionId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="100") int pageSize){return service.page(null,id,versionId,page,pageSize);}
 @PostMapping("/{id}/records/copy")
 public Map<String,Object> copy(@PathVariable String id,@RequestBody Selection request){return service.copy(null,id,request.versionId(),request.recordKeys());}
 @PostMapping("/{id}/records/used")
 public Map<String,Object> used(@PathVariable String id,@RequestBody Selection request){return service.used(null,id,request.versionId(),request.recordKeys(),request.requestId());}
}
