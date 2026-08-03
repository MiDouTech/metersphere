package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentProjectDTO;
import io.metersphere.agent.dto.AgentProjectSearchRequest;
import io.metersphere.agent.service.AgentProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Personal Agent Projects")
@RestController
@RequestMapping({"/personal/agent-projects", "/api/personal/agent-projects"})
public class PersonalAgentProjectController {
    @Resource
    private AgentProjectService agentProjectService;

    @GetMapping
    @Operation(summary = "List current user's accessible projects for personal Agent Token")
    public List<AgentProjectDTO> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer limit) {
        AgentProjectSearchRequest request = new AgentProjectSearchRequest();
        request.setKeyword(keyword);
        request.setLimit(limit);
        return agentProjectService.search(request).getItems();
    }
}
