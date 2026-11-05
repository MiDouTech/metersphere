package io.metersphere.agent.tool;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenRateLimiter;
import io.metersphere.agent.service.*;
import io.metersphere.agent.tool.project.AgentProjectFileTools;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentProjectFileToolTests {
    private AgentProjectFileService files;
    private AgentMcpStreamableService transport;
    private AgentIdempotencyService cache;

    @BeforeEach void setup() {
        files = mock(AgentProjectFileService.class);
        var config = new AgentProjectFileTools();
        var handlers = List.of(config.projectFilePrepare(files), config.projectFileChunk(files), config.projectFileComplete(files),
                config.projectFileCancel(files), config.projectFileUploadGet(files), config.projectFileSearch(files),
                config.projectFileGet(files), config.projectFileVersions(files), config.projectFileDownload(files));
        transport = new AgentMcpStreamableService();
        var rate = mock(AgentTokenRateLimiter.class);
        when(rate.tryAcquireTool(anyString(), anyString())).thenReturn(true);
        cache = mock(AgentIdempotencyService.class);
        ReflectionTestUtils.setField(transport, "agentMcpToolRegistry", new AgentMcpToolRegistry(handlers));
        ReflectionTestUtils.setField(transport, "agentTokenRateLimiter", rate);
        ReflectionTestUtils.setField(transport, "agentIdempotencyService", cache);
        ReflectionTestUtils.setField(transport, "safeErrorMapper", new AgentSafeErrorMapper());
        var token = new AgentToken(); token.setId("token"); token.setUserId("user"); token.setScopes("PROJECT_FILE_READ;PROJECT_FILE_WRITE;PROJECT_FILE_DOWNLOAD"); AgentTokenContext.set(token);
    }
    @AfterEach void cleanup() { AgentTokenContext.clear(); }

    @Test void listsNineToolsOnlyForExplicitGrants() {
        var tools = (List<?>) ((Map<?, ?>) transport.handle(Map.of("id", 1, "method", "tools/list")).get("result")).get("tools");
        assertEquals(9, tools.size());
        AgentTokenContext.get().setScopes("AGENT_ALL");
        assertEquals(0, ((List<?>) ((Map<?, ?>) transport.handle(Map.of("id", 2, "method", "tools/list")).get("result")).get("tools")).size());
    }

    @Test void resourceOwnedUploadProtocolNeverUsesGenericResponseCache() {
        when(files.upload(eq("chunk"), anyMap())).thenReturn(Map.of("state", "UPLOADING"));
        for (int i = 0; i < 2; i++) {
            var result = transport.handle(Map.of("id", i, "method", "tools/call", "params", Map.of("name", "metersphere.project.file.upload.chunk", "arguments", Map.of("projectId", "p", "uploadId", "u", "offset", 0, "base64", "YQ==", "chunkHash", "0".repeat(64)))));
            assertNull(result.get("error"));
        }
        verify(files, times(2)).upload(eq("chunk"), anyMap());
        verifyNoInteractions(cache);
    }

    @Test void errorsKeepSafeCodeFieldAndRecoveryWithoutFileContent() {
        var error = new AgentSafeErrorMapper().toApiError(new ProjectFileException("VALIDATION_ERROR:offset"), "trace");
        assertEquals("VALIDATION_ERROR", error.getCode());
        assertEquals("offset", error.getDetails().get("fieldPath"));
        assertEquals("FIX_INPUT", error.getDetails().get("nextAction"));
        assertEquals("trace", error.getTraceId());
    }

    @Test void writeSchemaRejectsLatestBeforeCallingTheApplicationService() {
        var args = Map.<String, Object>of("projectId", "p", "requestId", "write", "fileName", "原件.xmind", "size", 1,
                "sha256", "0".repeat(64), "existingFileId", "file", "expectedVersion", "latest", "confirmNewVersion", true);
        var response = transport.handle(Map.of("id", 1, "method", "tools/call", "params", Map.of("name", "metersphere.project.file.upload.prepare", "arguments", args)));
        assertEquals("VALIDATION_ERROR", ((Map<?, ?>) ((Map<?, ?>) response.get("error")).get("data")).get("code"));
        verify(files, never()).prepare(anyMap());
    }

    @Test void rejectionAuditAndErrorResponseShareTheSameGeneratedTrace() {
        when(files.upload(eq("complete"), anyMap())).thenThrow(new ProjectFileException("HASH_MISMATCH"));
        var args = Map.<String, Object>of("projectId", "p", "uploadId", "owned-upload");
        var response = transport.handle(Map.of("id", 1, "method", "tools/call", "params", Map.of("name", "metersphere.project.file.upload.complete", "arguments", args)));
        var data = (Map<?, ?>) ((Map<?, ?>) response.get("error")).get("data");
        assertEquals("HASH_MISMATCH", data.get("code"));
        assertNotNull(data.get("traceId"));
        verify(files).recordRejection(argThat(error -> "HASH_MISMATCH".equals(error.getMessage())), eq("upload.complete"), eq(args), eq((String) data.get("traceId")));
        assertNull(org.slf4j.MDC.get("traceId"));
    }
}
