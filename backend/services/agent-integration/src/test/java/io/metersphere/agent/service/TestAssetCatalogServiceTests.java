package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetDocumentDTO;
import io.metersphere.agent.dto.TestAssetCatalogItemDTO;
import io.metersphere.agent.dto.TestAssetContextDTO;
import io.metersphere.agent.dto.TestAssetRefDTO;
import io.metersphere.agent.dto.TestAssetRelationDTO;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.dto.TestAssetContextDocumentDTO;
import io.metersphere.agent.dto.TestAssetExecutableSnapshotDTO;
import io.metersphere.agent.mapper.TestAssetMapper;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.CompressUtils;
import io.metersphere.system.utils.Pager;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAssetCatalogServiceTests {
    @Mock
    private TestAssetMapper mapper;
    @Mock
    private AgentProjectService agentProjectService;
    @Mock
    private TestAssetVersionService versionService;
    @Mock
    private Subject subject;
    @Mock
    private Session session;
    @InjectMocks
    private TestAssetCatalogService service;

    @BeforeEach
    void bindSubject() {
        ThreadContext.bind(subject);
    }

    @AfterEach
    void clearSubject() {
        ThreadContext.unbindSubject();
        ThreadContext.remove();
    }

    @Test
    void catalogShouldCheckSourcePermissionAndPublishImmutableVersion() {
        when(subject.getSession()).thenReturn(session);
        when(subject.isPermitted(PermissionConstants.PROJECT_FILE_MANAGEMENT_READ)).thenReturn(true);
        when(agentProjectService.resolveProjectId("project-number")).thenReturn("project-1");
        when(mapper.countCatalog("project-1", "DATASET", "orders", "ACTIVE")).thenReturn(1L);
        TestAssetCatalogItemDTO item = new TestAssetCatalogItemDTO();
        item.setId("dataset-1");
        item.setProjectId("project-1");
        item.setAssetType("DATASET");
        item.setName("orders.csv");
        item.setSourceVersion("3");
        TestAssetExecutableSnapshotDTO executable = new TestAssetExecutableSnapshotDTO();
        executable.setFileId("file-1");
        executable.setFileName("orders.csv");
        executable.setFileType("csv");
        when(mapper.selectExecutableSnapshot("project-1", "DATASET", "dataset-1")).thenReturn(executable);
        when(mapper.selectCatalog("project-1", "DATASET", "orders", "ACTIVE", 0L, 20))
                .thenReturn(List.of(item));
        TestAssetVersionDTO version = new TestAssetVersionDTO();
        version.setId("version-3");
        version.setVersionNo(3);
        version.setContentHash("sha256");
        when(versionService.publish(eq("project-1"), eq("DATASET"), eq("dataset-1"), eq("3"),
                anyString(), eq("system:test-asset-reconcile"))).thenReturn(version);

        Pager<List<TestAssetCatalogItemDTO>> result = service.catalog(
                "project-number", "dataset", " orders ", "active", 1, 20);

        Assertions.assertEquals("version-3", result.getList().getFirst().getAssetVersionId());
        Assertions.assertEquals(3, result.getList().getFirst().getAssetVersionNo());
        verify(subject).isPermitted(PermissionConstants.PROJECT_FILE_MANAGEMENT_READ);
    }

    @Test
    void catalogShouldPublishExecutableSnapshotsForEverySupportedAssetTypeAndRedactSecrets() {
        when(subject.getSession()).thenReturn(session);
        when(subject.isPermitted(anyString())).thenReturn(true);
        when(agentProjectService.resolveProjectId("project-1")).thenReturn("project-1");
        TestAssetVersionDTO version = new TestAssetVersionDTO();
        version.setId("version-1");
        version.setVersionNo(1);
        version.setContentHash("hash-1");
        when(versionService.publish(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(version);

        Map<String, TestAssetExecutableSnapshotDTO> sources = Map.of(
                "DATASET", datasetSource(),
                "ENVIRONMENT", environmentSource(),
                "COMMON_STEP", commonStepSource(),
                "API_DEFINITION", apiSource(),
                "EVIDENCE", evidenceSource(),
                "BUG", bugSource());
        for (Map.Entry<String, TestAssetExecutableSnapshotDTO> entry : sources.entrySet()) {
            String type = entry.getKey();
            String id = type.toLowerCase() + "-1";
            TestAssetCatalogItemDTO item = new TestAssetCatalogItemDTO();
            item.setId(id);
            item.setProjectId("project-1");
            item.setAssetType(type);
            item.setName(type + " asset");
            item.setSourceVersion("1");
            when(mapper.countCatalog("project-1", type, null, null)).thenReturn(1L);
            when(mapper.selectCatalog("project-1", type, null, null, 0L, 20)).thenReturn(List.of(item));
            when(mapper.selectExecutableSnapshot("project-1", type, id)).thenReturn(entry.getValue());

            service.catalog("project-1", type, null, null, 1, 20);
        }

        ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(versionService, org.mockito.Mockito.times(6)).publish(
                eq("project-1"), anyString(), anyString(), eq("1"), snapshotCaptor.capture(),
                eq("system:test-asset-reconcile"));
        List<String> snapshots = snapshotCaptor.getAllValues();
        Assertions.assertTrue(snapshots.stream().anyMatch(value -> value.contains("FIXED_FILE_REFERENCE")));
        Assertions.assertTrue(snapshots.stream().anyMatch(value -> value.contains("AUTHORIZED_DOWNLOAD")));
        Assertions.assertTrue(snapshots.stream().anyMatch(value -> value.contains("/orders")));
        Assertions.assertTrue(snapshots.stream().anyMatch(value -> value.contains("resultSchema")));
        Assertions.assertTrue(snapshots.stream().anyMatch(value -> value.contains("bug description")));
        Assertions.assertTrue(snapshots.stream().noneMatch(value -> value.contains("raw-secret")));
        Assertions.assertTrue(snapshots.stream().anyMatch(value -> value.contains("valueRef")));
    }

    private TestAssetExecutableSnapshotDTO datasetSource() {
        TestAssetExecutableSnapshotDTO source = new TestAssetExecutableSnapshotDTO();
        source.setFileId("file-1");
        source.setFileName("orders.csv");
        source.setFileType("csv");
        source.setFileSize(128L);
        source.setTagsJson("[\"smoke\"]");
        return source;
    }

    private TestAssetExecutableSnapshotDTO environmentSource() {
        TestAssetExecutableSnapshotDTO source = new TestAssetExecutableSnapshotDTO();
        source.setEnvironmentConfig(blob("{\"baseUrl\":\"https://internal\",\"password\":\"raw-secret\"}"));
        return source;
    }

    private TestAssetExecutableSnapshotDTO commonStepSource() {
        TestAssetExecutableSnapshotDTO source = new TestAssetExecutableSnapshotDTO();
        source.setCommonParams(blob("{\"token\":\"raw-secret\",\"name\":\"id\"}"));
        source.setCommonScript(blob("return input;"));
        source.setCommonResult(blob("{\"type\":\"object\"}"));
        return source;
    }

    private TestAssetExecutableSnapshotDTO apiSource() {
        TestAssetExecutableSnapshotDTO source = new TestAssetExecutableSnapshotDTO();
        source.setProtocol("HTTP");
        source.setHttpMethod("POST");
        source.setApiPath("/orders");
        source.setApiRequest(blob("{\"Authorization\":\"raw-secret\",\"body\":{\"id\":1}}"));
        source.setApiResponse(blob("{\"status\":200}"));
        return source;
    }

    private TestAssetExecutableSnapshotDTO evidenceSource() {
        TestAssetExecutableSnapshotDTO source = new TestAssetExecutableSnapshotDTO();
        source.setTaskId("task-1");
        source.setContentType("image/png");
        source.setSizeBytes(256L);
        source.setSha256("sha256");
        source.setRedacted(true);
        return source;
    }

    private TestAssetExecutableSnapshotDTO bugSource() {
        TestAssetExecutableSnapshotDTO source = new TestAssetExecutableSnapshotDTO();
        source.setBugNumber(1001);
        source.setBugDescription("bug description password=raw-secret");
        source.setTagsJson("[\"regression\"]");
        return source;
    }

    private byte[] blob(String value) {
        return (byte[]) CompressUtils.zip(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void catalogShouldRejectUserWithoutSourcePermission() {
        when(agentProjectService.resolveProjectId("project-1")).thenReturn("project-1");
        when(subject.isPermitted(PermissionConstants.PROJECT_BUG_READ)).thenReturn(false);

        Assertions.assertThrows(MSException.class,
                () -> service.catalog("project-1", "BUG", null, null, 1, 20));
        verify(subject).isPermitted(PermissionConstants.PROJECT_BUG_READ);
    }

    @Test
    void resolveContextShouldPinRequestedVersionAndRejectCrossAssetVersion() {
        when(subject.getSession()).thenReturn(session);
        when(subject.isPermitted(PermissionConstants.PROJECT_API_DEFINITION_READ)).thenReturn(true);
        when(agentProjectService.resolveProjectId("project-1")).thenReturn("project-1");
        TestAssetCatalogItemDTO item = new TestAssetCatalogItemDTO();
        item.setId("api-1");
        item.setProjectId("project-1");
        item.setAssetType("API_DEFINITION");
        item.setName("query orders");
        when(mapper.selectCatalogItem("project-1", "API_DEFINITION", "api-1")).thenReturn(item);
        TestAssetVersionDTO version = new TestAssetVersionDTO();
        version.setId("version-other");
        version.setProjectId("project-1");
        version.setAssetType("API_DEFINITION");
        version.setAssetId("api-2");
        when(mapper.selectVersionById("version-other")).thenReturn(version);
        TestAssetRefDTO ref = new TestAssetRefDTO();
        ref.setAssetType("API_DEFINITION");
        ref.setAssetId("api-1");
        ref.setVersionId("version-other");

        Assertions.assertThrows(MSException.class,
                () -> service.resolveContext("project-1", List.of(ref)));
    }

    @Test
    void resolveContextShouldRejectCrossProjectPinnedVersion() {
        when(subject.getSession()).thenReturn(session);
        when(subject.isPermitted(PermissionConstants.PROJECT_ENVIRONMENT_READ)).thenReturn(true);
        when(agentProjectService.resolveProjectId("project-1")).thenReturn("project-1");
        TestAssetCatalogItemDTO item = new TestAssetCatalogItemDTO();
        item.setId("env-1");
        item.setProjectId("project-1");
        item.setAssetType("ENVIRONMENT");
        when(mapper.selectCatalogItem("project-1", "ENVIRONMENT", "env-1")).thenReturn(item);
        TestAssetVersionDTO foreignVersion = new TestAssetVersionDTO();
        foreignVersion.setId("foreign-version");
        foreignVersion.setProjectId("project-2");
        foreignVersion.setAssetType("ENVIRONMENT");
        foreignVersion.setAssetId("env-1");
        when(mapper.selectVersionById("foreign-version")).thenReturn(foreignVersion);
        TestAssetRefDTO ref = new TestAssetRefDTO();
        ref.setAssetType("ENVIRONMENT");
        ref.setAssetId("env-1");
        ref.setVersionId("foreign-version");

        Assertions.assertThrows(MSException.class,
                () -> service.resolveContext("project-1", List.of(ref)));
    }

    @Test
    void resolveContextShouldRejectMoreThanFiftyReferencesBeforeQueryingAssets() {
        List<TestAssetRefDTO> refs = IntStream.range(0, 51).mapToObj(index -> {
            TestAssetRefDTO ref = new TestAssetRefDTO();
            ref.setAssetType("DATASET");
            ref.setAssetId("dataset-" + index);
            return ref;
        }).toList();

        Assertions.assertThrows(MSException.class, () -> service.resolveContext("project-1", refs));
    }

    @Test
    void resolveContextShouldReturnPinnedSnapshotWithoutRepublishing() {
        when(subject.getSession()).thenReturn(session);
        when(subject.isPermitted(PermissionConstants.PROJECT_ENVIRONMENT_READ)).thenReturn(true);
        when(agentProjectService.resolveProjectId("project-1")).thenReturn("project-1");
        TestAssetCatalogItemDTO item = new TestAssetCatalogItemDTO();
        item.setId("env-1");
        item.setProjectId("project-1");
        item.setAssetType("ENVIRONMENT");
        item.setName("staging");
        when(mapper.selectCatalogItem("project-1", "ENVIRONMENT", "env-1")).thenReturn(item);
        TestAssetVersionDTO version = new TestAssetVersionDTO();
        version.setId("version-1");
        version.setProjectId("project-1");
        version.setAssetType("ENVIRONMENT");
        version.setAssetId("env-1");
        version.setVersionNo(1);
        version.setContentHash("hash-1");
        version.setContentSnapshot("{\"name\":\"staging\"}");
        when(mapper.selectVersionById("version-1")).thenReturn(version);
        TestAssetRefDTO ref = new TestAssetRefDTO();
        ref.setAssetType("ENVIRONMENT");
        ref.setAssetId("env-1");
        ref.setVersionId("version-1");

        List<TestAssetContextDTO> result = service.resolveContext("project-1", List.of(ref));

        Assertions.assertEquals("version-1", result.getFirst().getVersionId());
        Assertions.assertEquals("hash-1", result.getFirst().getContentHash());
        Assertions.assertEquals("{\"name\":\"staging\"}", result.getFirst().getContentSnapshot());
    }

    @Test
    void documentsShouldResolveAccessibleProjectAndCapPageSize() {
        when(agentProjectService.resolveProjectId("project-number")).thenReturn("project-1");
        when(mapper.countDocuments("project-1", "PARSED", "login")).thenReturn(1L);
        TestAssetDocumentDTO document = new TestAssetDocumentDTO();
        document.setId("document-1");
        when(mapper.selectDocuments("project-1", "PARSED", "login", 100L, 100))
                .thenReturn(List.of(document));

        Pager<List<TestAssetDocumentDTO>> result = service.documents(
                "project-number", "PARSED", " login ", 2, 500);

        Assertions.assertEquals(1, result.getTotal());
        Assertions.assertEquals(100, result.getPageSize());
        Assertions.assertEquals("document-1", result.getList().getFirst().getId());
        verify(agentProjectService).resolveProjectId("project-number");
    }

    @Test
    void versionsShouldNormalizeAssetTypeAndEmptyFilters() {
        when(subject.isPermitted(PermissionConstants.FUNCTIONAL_CASE_READ)).thenReturn(true);
        when(agentProjectService.resolveProjectId("project-1")).thenReturn("project-1");
        when(mapper.countVersions("project-1", List.of("CASE"), "CASE", null, null)).thenReturn(0L);

        Pager<List<TestAssetVersionDTO>> result = service.versions(
                "project-1", " case ", " ", " ", null, null);

        Assertions.assertTrue(result.getList().isEmpty());
        Assertions.assertEquals(20, result.getPageSize());
        verify(mapper).countVersions("project-1", List.of("CASE"), "CASE", null, null);
    }

    @Test
    void unfilteredVersionsShouldBeRestrictedToTypesVisibleToCurrentUser() {
        when(subject.isPermitted(anyString())).thenAnswer(invocation ->
                PermissionConstants.PROJECT_ENVIRONMENT_READ.equals(invocation.getArgument(0)));
        when(agentProjectService.resolveProjectId("project-1")).thenReturn("project-1");
        when(mapper.countVersions("project-1", List.of("ENVIRONMENT"), null, null, null)).thenReturn(0L);

        Pager<List<TestAssetVersionDTO>> result = service.versions(
                "project-1", null, null, null, 1, 20);

        Assertions.assertTrue(result.getList().isEmpty());
        verify(mapper).countVersions("project-1", List.of("ENVIRONMENT"), null, null, null);
    }

    @Test
    void relationsShouldApplyProjectIsolationBeforeQuery() {
        when(subject.isPermitted(PermissionConstants.FUNCTIONAL_CASE_AI_READ)).thenReturn(true);
        when(agentProjectService.resolveProjectId("project-alias")).thenReturn("project-2");
        when(mapper.countRelations("project-2", List.of("DOCUMENT"), "DOCUMENT", "doc-1", "DERIVED_FROM", null))
                .thenReturn(1L);
        TestAssetRelationDTO relation = new TestAssetRelationDTO();
        relation.setId("relation-1");
        when(mapper.selectRelations("project-2", List.of("DOCUMENT"), "DOCUMENT", "doc-1",
                "DERIVED_FROM", null, 0L, 20))
                .thenReturn(List.of(relation));

        Pager<List<TestAssetRelationDTO>> result = service.relations(
                "project-alias", "document", "doc-1", "derived_from", null, 1, 20);

        Assertions.assertEquals("relation-1", result.getList().getFirst().getId());
        verify(agentProjectService).resolveProjectId("project-alias");
    }

    @Test
    void documentContextShouldDeduplicateStableCaseIds() {
        TestAssetContextDocumentDTO document = new TestAssetContextDocumentDTO();
        document.setDocumentId("document-1");
        when(mapper.selectDocumentContextForCases("project-1", List.of("case-1", "case-2")))
                .thenReturn(List.of(document));

        List<TestAssetContextDocumentDTO> result = service.documentContextForCases(
                "project-1", List.of("case-1", "case-1", "case-2"));

        Assertions.assertEquals("document-1", result.getFirst().getDocumentId());
        verify(mapper).selectDocumentContextForCases("project-1", List.of("case-1", "case-2"));
    }
}
