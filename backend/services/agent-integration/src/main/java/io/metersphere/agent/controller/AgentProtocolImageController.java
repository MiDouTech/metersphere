package io.metersphere.agent.controller;
import io.metersphere.agent.service.AgentProtocolImageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;
@RestController
@RequestMapping({"/governed-execution/evidence","/api/governed-execution/evidence","/agent/v1/execution-sessions/evidence","/api/agent/v1/execution-sessions/evidence"})
public class AgentProtocolImageController {
    private final AgentProtocolImageService service;
    public AgentProtocolImageController(AgentProtocolImageService service){this.service=service;}
    @PostMapping public Map<String,Object> upload(@RequestBody Map<String,Object> args){return service.upload(args);}
    @GetMapping("/{id}")public ResponseEntity<byte[]> download(@PathVariable String id,@RequestParam String sessionId,@RequestParam String caseExecutionId){return service.download(sessionId,caseExecutionId,id);}
}
