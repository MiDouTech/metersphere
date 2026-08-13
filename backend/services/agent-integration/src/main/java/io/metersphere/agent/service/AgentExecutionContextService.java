package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.TestAssetContextDocumentDTO;
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

/** Builds the immutable, secret-free package consumed by a Runner or Agent. */
@Service
public class AgentExecutionContextService {
    private static final int MAX_CONTEXT_BYTES = 15_000_000;

    public ContextSnapshot build(AgentExecutionTaskDTO task,
                                 List<AgentExecutionCaseDTO> cases,
                                 List<AgentExecutionStepDTO> steps,
                                 List<TestAssetContextDocumentDTO> documents) {
        Map<String, List<AgentExecutionStepDTO>> stepsByCase = steps.stream()
                .collect(Collectors.groupingBy(AgentExecutionStepDTO::getExecutionCaseId));
        List<Map<String, Object>> caseContexts = cases.stream().map(executionCase -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("executionCaseId", executionCase.getId());
            value.put("caseId", executionCase.getCaseId());
            value.put("caseVersion", executionCase.getCaseVersion());
            value.put("caseSnapshot", parseJsonOrText(executionCase.getCaseSnapshot()));
            value.put("steps", stepsByCase.getOrDefault(executionCase.getId(), List.of()));
            return value;
        }).toList();

        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("environmentId", task.getEnvironmentId());
        environment.put("targetUrl", task.getTargetUrl());
        environment.put("browserType", task.getBrowserType());
        environment.put("loginMode", task.getLoginMode());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("contractVersion", "v1");
        context.put("taskId", task.getId());
        context.put("projectId", task.getProjectId());
        context.put("testPlanId", task.getTestPlanId());
        context.put("name", task.getName());
        context.put("objective", task.getObjective());
        context.put("caseSnapshotHash", task.getCaseSnapshotHash());
        context.put("requiredCapabilities", parseJsonOrText(task.getRequiredCapabilities()));
        context.put("policy", parseJsonOrText(task.getPolicySnapshot()));
        context.put("approvalPolicy", parseJsonOrText(task.getApprovalPolicy()));
        context.put("environment", environment);
        context.put("businessDocuments", documents.stream().map(document -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("versionId", document.getVersionId());
            value.put("documentId", document.getDocumentId());
            value.put("documentName", document.getDocumentName());
            value.put("versionNo", document.getVersionNo());
            value.put("sourceVersion", document.getSourceVersion());
            value.put("contentHash", document.getContentHash());
            value.put("snapshot", parseJsonOrText(document.getContentSnapshot()));
            return value;
        }).toList());
        context.put("cases", caseContexts);
        // Deliberately exclude provider credentials, browser storage state and token values.
        String snapshot = JSON.toJSONString(context);
        if (snapshot.getBytes(StandardCharsets.UTF_8).length > MAX_CONTEXT_BYTES) {
            throw new MSException("任务上下文超过 15 MB，请减少用例或来源文档范围后重试");
        }
        return new ContextSnapshot(snapshot, DigestUtils.sha256Hex(snapshot));
    }

    private Object parseJsonOrText(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return JSON.parseObject(value, Object.class);
        } catch (Exception ignored) {
            return value;
        }
    }

    public record ContextSnapshot(String content, String sha256) {
    }
}
