package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentWebActionDTO;
import io.metersphere.agent.dto.AgentWebAssertionDTO;
import io.metersphere.agent.dto.AgentWebLocatorDTO;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AgentWebExecutionContractValidator {
    public static final Set<String> ACTION_TYPES = Set.of(
            "NAVIGATE", "CLICK", "FILL", "SELECT", "CHECK", "UPLOAD", "KEYBOARD", "WAIT", "SCROLL");
    public static final Set<String> LOCATOR_STRATEGIES = Set.of(
            "TEST_ID", "ROLE_NAME", "LABEL", "PLACEHOLDER", "TEXT", "SEMANTIC", "CSS", "XPATH");
    public static final Set<String> ASSERTION_TYPES = Set.of(
            "TEXT", "VISIBLE", "ENABLED", "CHECKED", "ATTRIBUTE", "COUNT", "URL", "TITLE");
    private static final Set<String> HIGH_RISK_WORDS = Set.of(
            "删除", "支付", "退款", "发布", "权限", "批量修改", "转账", "清空",
            "delete", "payment", "refund", "publish", "permission", "transfer");
    private static final int MAX_TIMEOUT_MS = 60000;

    public void validateAction(AgentWebActionDTO action) {
        if (action == null || !"v1".equals(action.getContractVersion())) {
            throw new MSException("UNSUPPORTED_CONTRACT_VALUE: action.contractVersion");
        }
        String type = normalize(action.getType());
        if (!ACTION_TYPES.contains(type)) {
            throw new MSException("UNSUPPORTED_CONTRACT_VALUE: action.type");
        }
        action.setType(type);
        validateTimeout(action.getTimeoutMs(), "action.timeoutMs");
        if (requiresTarget(type)) {
            validateLocator(action.getTarget());
        }
        if ("FILL".equals(type) && StringUtils.isAllBlank(action.getValue(), action.getValueRef())) {
            throw new MSException("INVALID_ACTION: FILL requires value or valueRef");
        }
        if (StringUtils.isNotBlank(action.getValue()) && looksSensitive(action.getValue())) {
            throw new MSException("SECURITY_SENSITIVE_VALUE: use valueRef instead of an inline secret");
        }
        boolean highRisk = "HIGH".equalsIgnoreCase(action.getRiskLevel()) || containsHighRisk(action);
        if (highRisk) {
            action.setRiskLevel("HIGH");
            action.setRetryable(false);
        }
    }

    public void validateAssertions(List<AgentWebAssertionDTO> assertions) {
        if (assertions == null || assertions.isEmpty()) {
            throw new MSException("INVALID_ASSERTION: at least one assertion is required");
        }
        for (AgentWebAssertionDTO assertion : assertions) {
            if (assertion == null || !"v1".equals(assertion.getContractVersion())) {
                throw new MSException("UNSUPPORTED_CONTRACT_VALUE: assertion.contractVersion");
            }
            String type = normalize(assertion.getType());
            if (!ASSERTION_TYPES.contains(type)) {
                throw new MSException("UNSUPPORTED_CONTRACT_VALUE: assertion.type");
            }
            assertion.setType(type);
            validateTimeout(assertion.getTimeoutMs(), "assertion.timeoutMs");
            if (!Set.of("URL", "TITLE").contains(type)) {
                validateLocator(assertion.getTarget());
            }
            if (Set.of("TEXT", "ATTRIBUTE", "COUNT", "URL", "TITLE").contains(type)
                    && assertion.getExpected() == null) {
                throw new MSException("INVALID_ASSERTION: " + type + " requires expected");
            }
            if ("ATTRIBUTE".equals(type) && StringUtils.isBlank(assertion.getAttribute())) {
                throw new MSException("INVALID_ASSERTION: ATTRIBUTE requires attribute");
            }
        }
    }

    public void validateLocator(AgentWebLocatorDTO locator) {
        if (locator == null || !LOCATOR_STRATEGIES.contains(normalize(locator.getStrategy()))) {
            throw new MSException("UNSUPPORTED_CONTRACT_VALUE: locator.strategy");
        }
        locator.setStrategy(normalize(locator.getStrategy()));
        boolean present = switch (locator.getStrategy()) {
            case "TEST_ID" -> StringUtils.isNotBlank(locator.getTestId());
            case "ROLE_NAME" -> StringUtils.isNotBlank(locator.getRole()) && StringUtils.isNotBlank(locator.getName());
            case "LABEL" -> StringUtils.isNotBlank(locator.getLabel());
            case "PLACEHOLDER" -> StringUtils.isNotBlank(locator.getPlaceholder());
            case "TEXT", "SEMANTIC" -> StringUtils.isNotBlank(locator.getText());
            case "CSS", "XPATH" -> StringUtils.isNotBlank(locator.getSelector());
            default -> false;
        };
        if (!present) {
            throw new MSException("INVALID_LOCATOR: value required for strategy " + locator.getStrategy());
        }
    }

    private boolean requiresTarget(String type) {
        return !Set.of("NAVIGATE", "WAIT", "SCROLL").contains(type);
    }

    private void validateTimeout(Integer timeoutMs, String field) {
        if (timeoutMs == null || timeoutMs < 1 || timeoutMs > MAX_TIMEOUT_MS) {
            throw new MSException("INVALID_TIMEOUT: " + field + " must be between 1 and " + MAX_TIMEOUT_MS);
        }
    }

    private boolean containsHighRisk(AgentWebActionDTO action) {
        String candidate = String.join(" ",
                StringUtils.defaultString(action.getType()),
                action.getTarget() == null ? "" : StringUtils.defaultString(action.getTarget().getName()),
                action.getTarget() == null ? "" : StringUtils.defaultString(action.getTarget().getText()),
                action.getTarget() == null ? "" : StringUtils.defaultString(action.getTarget().getLabel()));
        String lower = candidate.toLowerCase(Locale.ROOT);
        return HIGH_RISK_WORDS.stream().anyMatch(word -> lower.contains(word.toLowerCase(Locale.ROOT)));
    }

    private boolean looksSensitive(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("bearer ") || lower.contains("private key")
                || lower.matches(".*(?:password|passwd|token|secret)\\s*[:=].+");
    }

    private String normalize(String value) {
        return StringUtils.upperCase(StringUtils.trimToEmpty(value), Locale.ROOT);
    }
}
