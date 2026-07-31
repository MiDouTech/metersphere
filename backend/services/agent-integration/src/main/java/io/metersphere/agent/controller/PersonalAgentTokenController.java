package io.metersphere.agent.controller;

import io.metersphere.agent.dto.AgentTokenCreateRequest;
import io.metersphere.agent.dto.AgentTokenCreateResponse;
import io.metersphere.agent.dto.AgentTokenListItemDTO;
import io.metersphere.agent.dto.AgentTokenPageRequest;
import io.metersphere.agent.dto.AgentTokenUpdateRequest;
import io.metersphere.agent.service.AgentTokenManagementService;
import io.metersphere.system.utils.Pager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Personal Agent Token")
@RestController
@RequestMapping({"/personal/agent-tokens", "/api/personal/agent-tokens"})
public class PersonalAgentTokenController {
    @Resource
    private AgentTokenManagementService agentTokenManagementService;

    @PostMapping
    @Operation(summary = "Create current user's Agent Token")
    public AgentTokenCreateResponse create(@RequestBody @Valid AgentTokenCreateRequest request) {
        return agentTokenManagementService.createPersonal(request);
    }

    @GetMapping
    @Operation(summary = "List current user's Agent Tokens")
    public Pager<List<AgentTokenListItemDTO>> page(AgentTokenPageRequest request) {
        return agentTokenManagementService.pagePersonal(request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update current user's Agent Token")
    public void update(@PathVariable String id, @RequestBody AgentTokenUpdateRequest request) {
        request.setId(id);
        agentTokenManagementService.updatePersonal(request);
    }

    @PostMapping("/{id}")
    @Operation(summary = "Update current user's Agent Token, POST compatibility")
    public void updatePost(@PathVariable String id, @RequestBody AgentTokenUpdateRequest request) {
        update(id, request);
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "Disable current user's Agent Token")
    public void disable(@PathVariable String id) {
        agentTokenManagementService.disablePersonal(id);
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "Enable current user's Agent Token")
    public void enable(@PathVariable String id) {
        agentTokenManagementService.enablePersonal(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete current user's Agent Token")
    public void delete(@PathVariable String id) {
        agentTokenManagementService.deletePersonal(id);
    }

    @GetMapping("/delete/{id}")
    @Operation(summary = "Delete current user's Agent Token, legacy GET compatibility")
    public void deleteGet(@PathVariable String id) {
        agentTokenManagementService.deletePersonal(id);
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Test current user's Agent Token configuration")
    public AgentTokenListItemDTO test(@PathVariable String id) {
        return agentTokenManagementService.testPersonal(id);
    }
}
