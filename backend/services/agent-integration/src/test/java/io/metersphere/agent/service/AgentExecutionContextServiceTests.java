package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.TestAssetContextDocumentDTO;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class AgentExecutionContextServiceTests {
    private final AgentExecutionContextService service = new AgentExecutionContextService();

    @Test
    void shouldFreezeStructuredBusinessDocumentVersionIntoContext() {
        AgentExecutionTaskDTO task = new AgentExecutionTaskDTO();
        task.setId("task-1");
        task.setProjectId("project-1");
        AgentExecutionCaseDTO executionCase = new AgentExecutionCaseDTO();
        executionCase.setId("execution-case-1");
        executionCase.setCaseId("case-row-1");
        executionCase.setCaseVersion("version-1");
        executionCase.setCaseSnapshot("{\"name\":\"登录\"}");
        AgentExecutionStepDTO step = new AgentExecutionStepDTO();
        step.setExecutionCaseId("execution-case-1");
        TestAssetContextDocumentDTO document = new TestAssetContextDocumentDTO();
        document.setVersionId("document-version-1");
        document.setDocumentId("document-1");
        document.setVersionNo(2);
        document.setContentHash("sha256");
        document.setContentSnapshot("{\"summary\":\"登录规则\"}");

        AgentExecutionContextService.ContextSnapshot result = service.build(
                task, List.of(executionCase), List.of(step), List.of(document));

        Assertions.assertTrue(result.content().contains("\"businessDocuments\""));
        Assertions.assertTrue(result.content().contains("\"documentId\":\"document-1\""));
        Assertions.assertTrue(result.content().contains("\"summary\":\"登录规则\""));
        Assertions.assertFalse(result.content().contains("contentSnapshot"));
    }

    @Test
    void shouldRejectContextThatCannotFitMediumTextSafely() {
        AgentExecutionTaskDTO task = new AgentExecutionTaskDTO();
        task.setId("task-large");
        task.setProjectId("project-1");
        TestAssetContextDocumentDTO document = new TestAssetContextDocumentDTO();
        document.setDocumentId("document-large");
        document.setContentSnapshot("中".repeat(5_100_000));

        MSException error = Assertions.assertThrows(MSException.class,
                () -> service.build(task, List.of(), List.of(), List.of(document)));

        Assertions.assertTrue(error.getMessage().contains("15 MB"));
    }
}
