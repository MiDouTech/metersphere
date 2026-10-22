package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentEnvironmentAssetBindingTests {
    private AgentEnvironmentProfileService service;
    private TestAssetCatalogService catalog;
    private JdbcTemplate jdbc;
    private AgentEnvironmentProfileDTO existing;

    @BeforeEach void setup() {
        service = spy(new AgentEnvironmentProfileService());
        catalog = mock(TestAssetCatalogService.class);
        jdbc = mock(JdbcTemplate.class);
        var projects = mock(AgentProjectService.class);
        when(projects.resolveProjectId("p1")).thenReturn("p1");
        var projectMapper = mock(ProjectMapper.class);
        var project = new Project(); project.setOrganizationId("o1");
        when(projectMapper.selectByPrimaryKey("p1")).thenReturn(project);
        ReflectionTestUtils.setField(service, "projectService", projects);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "testAssetCatalogService", catalog);
        existing = new AgentEnvironmentProfileDTO();
        existing.setId("profile"); existing.setProjectId("p1");
        existing.setEnvironmentId("credential-scope"); existing.setEnabled(true);
        existing.setEnvironmentType("TEST"); existing.setVersion(2);
        existing.setBaseUrl("https://test.example.com");
        existing.setAllowedOrigins(List.of("https://test.example.com"));
        existing.setRunnerType("BROWSER"); existing.setRequiredCapabilities(List.of());
        doReturn(existing).when(service).get(anyString());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    }

    private AgentEnvironmentProfileRequest request() {
        var request = new AgentEnvironmentProfileRequest();
        request.setProjectId("p1"); request.setName("platform");
        request.setBaseUrl("https://test.example.com");
        request.setAllowedOrigins(List.of("https://test.example.com"));
        request.setEnvironmentType("TEST"); request.setRunnerType("BROWSER");
        request.setEnabled(true); request.setVersion(2);
        return request;
    }

    @Test void personalConfigurationCreatesWithoutRequiringPublishedAssets() {
        var request = request();
        try (var session = mockStatic(SessionUtils.class);
             var ids = mockStatic(io.metersphere.system.uid.IDGenerator.class)) {
            session.when(SessionUtils::getUserId).thenReturn("u1");
            ids.when(io.metersphere.system.uid.IDGenerator::nextStr).thenReturn("new-scope", "new-profile");
            service.create(request);
        }
        assertNotNull(request.getEnvironmentId());
        assertNull(request.getEnvironmentAssetId());
        verifyNoInteractions(catalog);
    }

    @Test void bindingValidatesSameProjectPublishedAssetAndKeepsCredentialScope() {
        var request = request(); request.setEnvironmentAssetId("published-environment");
        try (var session = mockStatic(SessionUtils.class)) { service.update("profile", request); }
        ArgumentCaptor<List<TestAssetRefDTO>> refs = ArgumentCaptor.forClass(List.class);
        verify(catalog).resolveContext(eq("p1"), refs.capture());
        assertEquals("ENVIRONMENT", refs.getValue().getFirst().getAssetType());
        assertEquals("published-environment", refs.getValue().getFirst().getAssetId());
        assertEquals("credential-scope", request.getEnvironmentId());
    }

    @Test void unauthorizedCrossProjectOrUnpublishedBindingDoesNotWrite() {
        when(catalog.resolveContext(eq("p1"), anyList())).thenThrow(new MSException("PUBLISHED_ASSET_VERSION_NOT_FOUND"));
        var request = request(); request.setEnvironmentAssetId("unavailable");
        assertEquals("ENVIRONMENT_ASSET_UNAVAILABLE", assertThrows(MSException.class,
                () -> service.update("profile", request)).getMessage());
        verifyNoInteractions(jdbc);
    }

    @Test void changingCredentialScopeIsRejectedBeforeWriting() {
        var request = request(); request.setEnvironmentId("other-scope");
        assertEquals("ENVIRONMENT_CREDENTIAL_SCOPE_IMMUTABLE", assertThrows(MSException.class,
                () -> service.update("profile", request)).getMessage());
        verifyNoInteractions(jdbc, catalog);
    }

    @Test void staleVersionReturnsSafeConflictInsteadOfGenericBusinessError() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        MSException error;
        try (var session = mockStatic(SessionUtils.class)) {
            error = assertThrows(MSException.class, () -> service.update("profile", request()));
        }
        var response = new io.metersphere.agent.controller.AgentExecutionExceptionHandler(new AgentSafeErrorMapper())
                .business(error, new org.springframework.mock.web.MockHttpServletRequest());
        assertEquals(409, response.getStatusCode().value());
        assertEquals("ENVIRONMENT_PROFILE_VERSION_CONFLICT", response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("刷新"));
    }

    @Test void legacyUpdatePreservesBindingButEmptyStringExplicitlyUnbinds() {
        existing.setEnvironmentAssetId("published");
        var legacy = request();
        var unbind = request(); unbind.setEnvironmentAssetId("");
        try (var session = mockStatic(SessionUtils.class)) {
            service.update("profile", legacy);
            service.update("profile", unbind);
        }
        assertEquals("published", legacy.getEnvironmentAssetId());
        assertEquals("", unbind.getEnvironmentAssetId());
        verifyNoInteractions(catalog);
    }

    @Test void managedTaskRejectsMissingAssetInsteadOfResolvingCredentialScopeAsAsset() {
        assertEquals("ENVIRONMENT_ASSET_BINDING_REQUIRED", assertThrows(MSException.class,
                () -> service.resolveForTask("profile", "p1")).getMessage());
        existing.setEnvironmentAssetId("published");
        assertSame(existing, service.resolveForTask("profile", "p1"));
        Map<?, ?> snapshot = JSON.parseObject(service.freezeSnapshot(existing), Map.class);
        assertEquals("published", snapshot.get("environmentAssetId"));
        assertEquals("credential-scope", snapshot.get("environmentId"));
    }

    @Test void changedConfigurationAfterPreflightCannotMixNewProfileWithOldFrozenAssets() {
        var preflight = spy(new AgentExecutionPreflightService());
        existing.setEnvironmentAssetId("published");
        doReturn(service.freezeSnapshot(existing)).when(preflight).frozenSnapshotSection("pf", "environment");
        assertDoesNotThrow(() -> preflight.assertEnvironmentUnchanged("pf", existing));
        existing.setVersion(3);
        assertEquals("PREFLIGHT_ENVIRONMENT_CHANGED", assertThrows(MSException.class,
                () -> preflight.assertEnvironmentUnchanged("pf", existing)).getMessage());
        existing.setVersion(2); existing.setEnvironmentAssetId("another");
        assertThrows(MSException.class, () -> preflight.assertEnvironmentUnchanged("pf", existing));
    }
}
