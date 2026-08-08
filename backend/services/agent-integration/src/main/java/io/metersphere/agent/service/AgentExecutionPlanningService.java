package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentWebStepPlanDTO;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.request.ai.AiProviderChatRequest;
import io.metersphere.system.dto.request.ai.AiProviderInvocationResult;
import io.metersphere.system.service.ai.provider.AiProviderAdapter;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AgentExecutionPlanningService {
    private static final int MAX_MODEL_RESPONSE = 50_000;
    private static final String SYSTEM_PROMPT = """
            你是 Web UI 测试步骤编译器。只把给定的单个人工测试步骤转换为 Execution Contract v1 JSON，
            不改变预期结果，不臆造凭据，不输出解释、Markdown 或代码围栏。输出必须严格为：
            {"action":{...},"assertions":[...]}
            动作仅允许 NAVIGATE/CLICK/FILL/SELECT/CHECK/UPLOAD/KEYBOARD/WAIT/SCROLL；
            定位仅允许 TEST_ID/ROLE_NAME/LABEL/PLACEHOLDER/TEXT/SEMANTIC/CSS/XPATH，优先语义定位；
            断言仅允许 TEXT/VISIBLE/ENABLED/CHECKED/ATTRIBUTE/COUNT/URL/TITLE；
            timeoutMs 为 1..60000。密码、Token 等只能用 valueRef，禁止内联；禁止 JavaScript；
            删除、支付、退款、发布、权限、转账、清空等高风险动作必须 riskLevel=HIGH 且 retryable=false。
            如果步骤无法可靠转换，返回 {"error":"NEEDS_REVIEW","reason":"..."}。
            """;

    @Resource
    private AiProviderAdapter aiProviderAdapter;
    @Resource
    private AgentWebExecutionContractValidator contractValidator;

    public void plan(String projectId, String organizationId, String modelSourceId, String taskId,
                     String targetUrl, List<AgentExecutionStepDTO> steps, String userId) {
        for (AgentExecutionStepDTO step : steps) {
            if (!"PENDING".equals(step.getStatus())) {
                continue;
            }
            AiProviderChatRequest request = new AiProviderChatRequest();
            request.setProjectId(projectId);
            request.setOrganizationId(organizationId);
            request.setChatModelId(modelSourceId);
            request.setConversationId(taskId);
            request.setRequestId(taskId + ":" + step.getId());
            request.setSystem(SYSTEM_PROMPT);
            request.setPrompt(JSON.toJSONString(Map.of(
                    "targetUrl", StringUtils.defaultString(targetUrl),
                    "instruction", StringUtils.defaultString(step.getInstruction()),
                    "expected", StringUtils.defaultString(step.getExpected()),
                    "riskLevel", StringUtils.defaultIfBlank(step.getRiskLevel(), "LOW")
            )));
            AiProviderInvocationResult result = aiProviderAdapter.invoke(request, userId);
            AgentWebStepPlanDTO plan = parsePlan(result == null ? null : result.getContent());
            contractValidator.validateAction(plan.getAction());
            contractValidator.validateAssertions(plan.getAssertions());
            if ("HIGH".equalsIgnoreCase(step.getRiskLevel())) {
                plan.getAction().setRiskLevel("HIGH");
                plan.getAction().setRetryable(false);
            }
            step.setActionJson(JSON.toJSONString(plan.getAction()));
            step.setAssertionJson(JSON.toJSONString(plan.getAssertions()));
            step.setRetryable(plan.getAction().getRetryable());
            step.setRiskLevel(plan.getAction().getRiskLevel());
        }
    }

    AgentWebStepPlanDTO parsePlan(String content) {
        String json = extractJson(content);
        Map<?, ?> raw;
        try {
            raw = JSON.parseObject(json, Map.class);
        } catch (Exception ex) {
            throw new MSException("SCOPE_AI_PLAN_INVALID: 模型未返回合法 JSON", ex);
        }
        if (raw.get("error") != null) {
            throw new MSException("SCOPE_AI_PLAN_NEEDS_REVIEW: " + StringUtils.abbreviate(
                    StringUtils.defaultString(String.valueOf(raw.get("reason"))), 500));
        }
        AgentWebStepPlanDTO plan;
        try {
            plan = JSON.parseObject(json, AgentWebStepPlanDTO.class);
        } catch (Exception ex) {
            throw new MSException("SCOPE_AI_PLAN_INVALID: Schema 不匹配", ex);
        }
        if (plan.getAction() == null || plan.getAssertions() == null || plan.getAssertions().isEmpty()) {
            throw new MSException("SCOPE_AI_PLAN_INVALID: action/assertions 不能为空");
        }
        return plan;
    }

    private String extractJson(String content) {
        String value = StringUtils.trimToEmpty(content);
        if (StringUtils.isBlank(value) || value.length() > MAX_MODEL_RESPONSE) {
            throw new MSException("SCOPE_AI_PLAN_INVALID: 模型响应为空或过大");
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new MSException("SCOPE_AI_PLAN_INVALID: 未找到 JSON 对象");
        }
        return value.substring(start, end + 1);
    }
}
