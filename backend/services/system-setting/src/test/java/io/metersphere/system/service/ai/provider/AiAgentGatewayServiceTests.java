package io.metersphere.system.service.ai.provider;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.request.ai.AiAgentGatewayInvokeRequest;
import io.metersphere.system.service.PermissionCheckService;
import io.metersphere.system.service.ai.AiAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import io.metersphere.sdk.exception.MSException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiAgentGatewayServiceTests {

    @Test
    void invokesCurrentStatelessMcpProtocolWithRequiredHeadersAndTaskContext() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiAgentGatewayService service = new AiAgentGatewayService(builder);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "aiAuditService", mock(AiAuditService.class));
        ReflectionTestUtils.setField(service, "permissionCheckService", mock(PermissionCheckService.class));

        Map<String, Object> gateway = Map.of(
                "id", "gateway-1", "protocol", "MCP", "base_url", "https://gateway.example.com/mcp",
                "owner_user_id", "user-1", "create_user", "user-1", "auth_type", "NONE",
                "capabilities", JSON.toJSONString(List.of("metersphere.case.generate")));
        when(jdbc.queryForList("SELECT * FROM ai_agent_gateway WHERE id=? AND enabled=1", "gateway-1"))
                .thenReturn(List.of(gateway));

        server.expect(requestTo("https://gateway.example.com/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("MCP-Protocol-Version", "2026-07-28"))
                .andExpect(header("Mcp-Method", "tools/call"))
                .andExpect(header("Mcp-Name", "metersphere.case.generate"))
                .andExpect(jsonPath("$.method").value("tools/call"))
                .andExpect(jsonPath("$.params.name").value("metersphere.case.generate"))
                .andExpect(jsonPath("$.params._meta['io.metersphere/taskContext'].taskId").value("task-1"))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"accepted\":true}}",
                        MediaType.APPLICATION_JSON));

        AiAgentGatewayInvokeRequest request = new AiAgentGatewayInvokeRequest();
        request.setGatewayId("gateway-1");
        request.setProjectId("project-1");
        request.setTaskId("task-1");
        request.setOperation("metersphere.case.generate");
        request.setContext(Map.of("prompt", "generate"));
        Map<?, ?> response = service.invoke(request, "user-1");

        assertEquals(Boolean.TRUE, ((Map<?, ?>) response.get("result")).get("accepted"));
        server.verify();
    }

    @Test
    void personalGatewayCannotBeInvokedByAnotherUser() {
        RestClient.Builder builder = RestClient.builder();
        AiAgentGatewayService service = new AiAgentGatewayService(builder);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "aiAuditService", mock(AiAuditService.class));
        ReflectionTestUtils.setField(service, "permissionCheckService", mock(PermissionCheckService.class));
        Map<String, Object> gateway = Map.of(
                "id", "gateway-1", "protocol", "MCP", "base_url", "https://gateway.example.com/mcp",
                "owner_user_id", "user-1", "create_user", "user-1", "auth_type", "NONE",
                "capabilities", "[]");
        when(jdbc.queryForList("SELECT * FROM ai_agent_gateway WHERE id=? AND enabled=1", "gateway-1"))
                .thenReturn(List.of(gateway));
        AiAgentGatewayInvokeRequest request = new AiAgentGatewayInvokeRequest();
        request.setGatewayId("gateway-1");
        request.setProjectId("project-1");
        request.setOperation("tool");

        assertThrows(MSException.class, () -> service.invoke(request, "user-2"));
    }
}
