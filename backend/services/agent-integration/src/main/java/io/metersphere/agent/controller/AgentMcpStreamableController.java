package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentMcpStreamableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Agent Remote MCP")
@RestController
@RequestMapping({"/mcp", "/api/mcp"})
public class AgentMcpStreamableController {
    @Resource
    private AgentMcpStreamableService agentMcpStreamableService;

    @PostMapping
    @Operation(summary = "Streamable HTTP MCP JSON-RPC endpoint")
    public Map<String, Object> post(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        return agentMcpStreamableService.handle(request, httpRequest.getHeader("Idempotency-Key"));
    }

    /**
     * 无状态 Streamable HTTP 不提供 GET SSE。
     * 按 MCP 规范返回 405，避免 Cursor/WorkBuddy 把 401 误判为 Token 失效。
     */
    @GetMapping
    @Operation(summary = "MCP endpoint does not offer SSE; use POST")
    public ResponseEntity<Map<String, Object>> get() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, "POST")
                .body(Map.of(
                        "jsonrpc", "2.0",
                        "error", Map.of("code", -32000, "message", "Method Not Allowed")
                ));
    }

    @DeleteMapping
    @Operation(summary = "MCP session close endpoint")
    public Map<String, Object> delete() {
        return Map.of("ok", true);
    }
}
