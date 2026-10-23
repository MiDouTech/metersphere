package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentExecutionArchiveService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping({"/ai/execution-archives","/api/ai/execution-archives"})
public class AgentExecutionArchiveController {
    @Resource private AgentExecutionArchiveService service;
    public record Source(String sourceType,String sourceId) { }
    @PostMapping public Map<String,Object> prepare(@RequestBody Source source){return service.prepare(source.sourceType(),source.sourceId(),SessionUtils.getUserId());}
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable String id){return service.status(id,SessionUtils.getUserId());}
    @PostMapping("/{id}/retry") public Map<String,Object> retry(@PathVariable String id){return service.retry(id,SessionUtils.getUserId());}
    @PostMapping("/{id}/data") public Map<String,Object> data(@PathVariable String id,@RequestBody List<Map<String,Object>> records){return service.data(id,SessionUtils.getUserId(),records);}
}
