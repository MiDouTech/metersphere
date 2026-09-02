package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import io.metersphere.sdk.exception.MSException;

import java.nio.charset.StandardCharsets;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExecutionArtifactRelationTests {

    @Test
    void artifactTypeShouldAllowUtf8TextButRejectHtmlAndBinaryPayloads() {
        AgentExecutionArtifactService service = new AgentExecutionArtifactService();
        byte[] text = "redacted execution log".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile textFile = new MockMultipartFile("file", "run.log", "text/plain", text);

        AgentExecutionArtifactService.ArtifactType type = service.detectArtifact(text, textFile);

        org.junit.jupiter.api.Assertions.assertEquals("text/plain", type.contentType());
        byte[] html = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile htmlFile = new MockMultipartFile("file", "page.html", "text/html", html);
        org.junit.jupiter.api.Assertions.assertThrows(MSException.class,
                () -> service.detectArtifact(html, htmlFile));
        byte[] binary = new byte[]{0, 1, 2, 3};
        MockMultipartFile binaryFile = new MockMultipartFile("file", "data.txt", "text/plain", binary);
        org.junit.jupiter.api.Assertions.assertThrows(MSException.class,
                () -> service.detectArtifact(binary, binaryFile));
    }

    @Test
    void artifactShouldPublishTaskStepAndCaseRelations() {
        AgentExecutionMapper executionMapper = mock(AgentExecutionMapper.class);
        TestAssetVersionService versionService = mock(TestAssetVersionService.class);
        TestAssetGovernanceService governanceService = mock(TestAssetGovernanceService.class);
        FunctionalCaseMapper functionalCaseMapper = mock(FunctionalCaseMapper.class);
        AgentExecutionArtifactService service = new AgentExecutionArtifactService();
        ReflectionTestUtils.setField(service, "executionMapper", executionMapper);
        ReflectionTestUtils.setField(service, "testAssetVersionService", versionService);
        ReflectionTestUtils.setField(service, "functionalCaseMapper", functionalCaseMapper);
        ReflectionTestUtils.setField(service, "testAssetGovernanceService", governanceService);

        TestAssetVersionDTO evidenceVersion = version("evidence-version");
        TestAssetVersionDTO stepVersion = version("step-version");
        when(versionService.publish(anyString(), eq("EVIDENCE"), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(evidenceVersion);
        when(versionService.publish(anyString(), eq("STEP"), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(stepVersion);

        AgentExecutionTaskDTO task = new AgentExecutionTaskDTO();
        task.setId("task-1");
        task.setProjectId("project-1");
        AgentExecutionArtifactDTO artifact = new AgentExecutionArtifactDTO();
        artifact.setId("artifact-1");
        artifact.setTaskId("task-1");
        artifact.setExecutionCaseId("execution-case-1");
        artifact.setCaseId("case-row-1");
        artifact.setStepId("step-1");
        artifact.setPurpose("FAILURE");
        artifact.setSha256("sha256");
        artifact.setCreateUser("runner:1");

        AgentExecutionStepDTO step = new AgentExecutionStepDTO();
        step.setId("step-1");
        step.setTaskId("task-1");
        step.setExecutionCaseId("execution-case-1");
        step.setCaseId("case-row-1");
        step.setPos(1);
        step.setInstruction("Click submit");
        step.setVersion(2);
        when(executionMapper.selectStepsByTaskId("task-1")).thenReturn(List.of(step));

        AgentExecutionCaseDTO executionCase = new AgentExecutionCaseDTO();
        executionCase.setId("execution-case-1");
        executionCase.setCaseId("case-row-1");
        executionCase.setAssetVersionId("case-version");
        when(executionMapper.selectCasesByTaskId("task-1")).thenReturn(List.of(executionCase));
        FunctionalCase functionalCase = new FunctionalCase();
        functionalCase.setId("case-row-1");
        functionalCase.setRefId("case-stable-1");
        functionalCase.setProjectId("project-1");
        when(functionalCaseMapper.selectByPrimaryKey("case-row-1")).thenReturn(functionalCase);

        service.publishAssetRelations(task, artifact);

        verify(versionService).relate(eq("project-1"), eq("PRODUCES"), eq("TASK"), eq("task-1"), isNull(),
                eq("EVIDENCE"), eq("artifact-1"), eq("evidence-version"), anyString(), eq("runner:1"));
        verify(versionService).relate(eq("project-1"), eq("PRODUCES"), eq("STEP"), eq("step-1"), eq("step-version"),
                eq("EVIDENCE"), eq("artifact-1"), eq("evidence-version"), anyString(), eq("runner:1"));
        verify(versionService).relate(eq("project-1"), eq("PRODUCES"), eq("CASE"), eq("case-stable-1"), eq("case-version"),
                eq("EVIDENCE"), eq("artifact-1"), eq("evidence-version"), anyString(), eq("runner:1"));
        verify(governanceService).recordTrustedSource("project-1", "EVIDENCE", "artifact-1", "AUTOMATION",
                "AI_EXECUTION_TASK", "task-1", "AGENT", "runner:1");
    }

    private TestAssetVersionDTO version(String id) {
        TestAssetVersionDTO value = new TestAssetVersionDTO();
        value.setId(id);
        return value;
    }
}
