package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentWebActionDTO;
import io.metersphere.agent.dto.AgentWebAssertionDTO;
import io.metersphere.agent.dto.AgentWebLocatorDTO;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

@Component
public class AgentWebExecutionContractValidator {
    public static final Set<String> ACTION_TYPES = Set.of(
            "NAVIGATE", "CLICK", "FILL", "SELECT", "CHECK", "UPLOAD", "KEYBOARD", "WAIT", "SCROLL");
    public static final Set<String> LOCATOR_STRATEGIES = Set.of(
            "TEST_ID", "ROLE", "LABEL", "PLACEHOLDER", "TEXT", "CSS");
    public static final Set<String> ASSERTION_TYPES = Set.of(
            "TEXT", "VISIBLE", "ENABLED", "CHECKED", "ATTRIBUTE", "COUNT", "URL", "TITLE");
    private static final Set<String> HIGH_RISK_WORDS = Set.of(
            "删除", "支付", "退款", "发布", "权限", "批量修改", "转账", "清空",
            "delete", "payment", "refund", "publish", "permission", "transfer");
    private static final int MAX_TIMEOUT_MS = 60000;

    public void validateAction(AgentWebActionDTO action) {
        validateSchemaVersion(action == null ? null : action.getContractVersion(), "action.contractVersion");
        String type = normalize(action.getType());
        if (!ACTION_TYPES.contains(type)) {
            throw new MSException("UNSUPPORTED_CONTRACT_VALUE: action.type");
        }
        action.setType(type);
        if (StringUtils.isBlank(action.getId()) || StringUtils.length(action.getId()) > 128) {
            throw new MSException("INVALID_ACTION: action.id is required");
        }
        if (StringUtils.isBlank(action.getIdempotencyKey()) || StringUtils.length(action.getIdempotencyKey()) > 256) {
            throw new MSException("INVALID_ACTION: action.idempotencyKey is required");
        }
        validateTimeout(action.getTimeoutMs(), "action.timeoutMs");
        if (requiresTarget(type)) {
            validateLocator(action.getTarget());
        }
        if ("FILL".equals(type) && StringUtils.isAllBlank(action.getValue(), action.getValueRef())) {
            throw new MSException("INVALID_ACTION: FILL requires value or valueRef");
        }
        validateValueRefs(action);
        validateUploads(action);
        validateNoInlineSecret(action);
        boolean highRisk = "HIGH".equalsIgnoreCase(action.getRiskLevel()) || containsHighRisk(action);
        if (highRisk) {
            action.setRiskLevel("HIGH");
            action.setRetryable(false);
        }
    }

    public void validateSchemaVersion(String version, String field) {
        if (!"v1".equals(version)) throw new MSException("UNSUPPORTED_CONTRACT_VALUE: " + field);
    }

    public void validateActions(List<AgentWebActionDTO> actions) {
        if (actions == null || actions.isEmpty()) throw new MSException("INVALID_ACTION: actions are required");
        actions.forEach(this::validateAction);
    }

    public void validateOrigins(String targetOrigin, Set<String> allowedOrigins) {
        if (StringUtils.isBlank(targetOrigin) || allowedOrigins == null
                || allowedOrigins.stream().noneMatch(item -> StringUtils.equalsIgnoreCase(StringUtils.removeEnd(item, "/"), StringUtils.removeEnd(targetOrigin, "/")))) {
            throw new MSException("TARGET_ORIGIN_NOT_ALLOWED");
        }
    }

    public void validateLocators(List<AgentWebLocatorDTO> locators) {
        if (locators != null) locators.forEach(this::validateLocator);
    }

    public void validateValueRefs(AgentWebActionDTO action) {
        if (StringUtils.isNotBlank(action.getValueRef()) && !action.getValueRef().matches("^(credential|dataset|runtime):[A-Za-z0-9._:-]+$")) {
            throw new MSException("INVALID_VALUE_REF");
        }
        if (StringUtils.isNotBlank(action.getValue()) && StringUtils.isNotBlank(action.getValueRef())) {
            throw new MSException("INVALID_ACTION: value and valueRef are mutually exclusive");
        }
    }

    public void validateUploads(AgentWebActionDTO action) {
        if ("UPLOAD".equals(action.getType()) && (StringUtils.isBlank(action.getFileRef())
                || !action.getFileRef().matches("^(artifact|dataset):[A-Za-z0-9._:-]+$"))) {
            throw new MSException("INVALID_UPLOAD_REF");
        }
        if (!"UPLOAD".equals(action.getType()) && StringUtils.isNotBlank(action.getFileRef())) {
            throw new MSException("INVALID_UPLOAD_REF");
        }
    }

    public void validateTimeouts(AgentWebActionDTO action, List<AgentWebAssertionDTO> assertions) {
        validateTimeout(action.getTimeoutMs(), "action.timeoutMs");
        if (assertions != null) assertions.forEach(assertion -> validateTimeout(assertion.getTimeoutMs(), "assertion.timeoutMs"));
    }

    public void validateScopeExpansion(List<String> originalCaseIds, List<String> addedCaseIds) {
        int original = originalCaseIds == null ? 0 : new java.util.HashSet<>(originalCaseIds).size();
        int added = addedCaseIds == null ? 0 : new java.util.HashSet<>(addedCaseIds).size();
        if (original < 1 || added > (int) Math.ceil(original * 0.15d)) throw new MSException("SCOPE_EXPANSION_LIMIT_EXCEEDED");
        if (addedCaseIds != null && originalCaseIds != null && addedCaseIds.stream().anyMatch(originalCaseIds::contains)) {
            throw new MSException("SCOPE_EXPANSION_DUPLICATE");
        }
    }

    public void validateRisk(AgentWebActionDTO action) {
        if ("HIGH".equalsIgnoreCase(action.getRiskLevel()) && Boolean.TRUE.equals(action.getRetryable())) {
            throw new MSException("HIGH_RISK_ACTION_MUST_NOT_RETRY");
        }
    }

    public void validateNoInlineSecret(AgentWebActionDTO action) {
        if (StringUtils.isNotBlank(action.getValue()) && looksSensitive(action.getValue())) {
            throw new MSException("SECURITY_SENSITIVE_VALUE: use valueRef instead of an inline secret");
        }
    }

    @SuppressWarnings("unchecked")
    public void validateContract(Map<String,Object> contract) {
        validateSchemaVersion((String)contract.get("contractVersion"), "contractVersion");
        if (StringUtils.isAnyBlank((String)contract.get("taskId"), (String)contract.get("snapshotHash"), (String)contract.get("credentialRole"))) {
            throw new MSException("EXECUTION_CONTRACT_REQUIRED_FIELD_MISSING");
        }
        if (!String.valueOf(contract.get("snapshotHash")).matches("^[a-f0-9]{64}$") || !(contract.get("generatedAt") instanceof Number)) {
            throw new MSException("EXECUTION_CONTRACT_INVALID_METADATA");
        }
        Map<String,Object> scope=(Map<String,Object>)contract.get("scope");
        if(scope==null)throw new MSException("EXECUTION_CONTRACT_SCOPE_MISSING");
        validateScopeExpansion((List<String>)scope.get("caseIds"),(List<String>)scope.get("addedCaseIds"));
        List<Map<String,Object>> cases=(List<Map<String,Object>>)contract.get("cases");
        if(cases==null||cases.isEmpty())throw new MSException("EXECUTION_CONTRACT_CASES_MISSING");
        for(Map<String,Object> item:cases){
            if(StringUtils.isAnyBlank((String)item.get("caseId"),(String)item.get("assetVersionId"),(String)item.get("name")))throw new MSException("EXECUTION_CONTRACT_CASE_INVALID");
            List<Map<String,Object>> steps=(List<Map<String,Object>>)item.get("steps");
            if(steps==null||steps.isEmpty())throw new MSException("EXECUTION_CONTRACT_STEPS_MISSING");
            for(Map<String,Object> step:steps){
                AgentWebActionDTO action=io.metersphere.sdk.util.JSON.parseObject(io.metersphere.sdk.util.JSON.toJSONString(step.get("action")),AgentWebActionDTO.class);
                List<AgentWebAssertionDTO> assertions=io.metersphere.sdk.util.JSON.parseArray(io.metersphere.sdk.util.JSON.toJSONString(step.get("assertions")),AgentWebAssertionDTO.class);
                validateAction(action);validateAssertions(assertions);validateRisk(action);
                if(!Set.of("STOP_CASE","CONTINUE_CASE","STOP_TASK","NEEDS_REVIEW").contains(step.get("onFailure")))throw new MSException("EXECUTION_CONTRACT_ON_FAILURE_INVALID");
                if(!Set.of("ON_FAILURE","ALWAYS","NONE").contains(step.get("evidencePolicy")))throw new MSException("EXECUTION_CONTRACT_EVIDENCE_POLICY_INVALID");
            }
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
            case "ROLE" -> StringUtils.isNotBlank(locator.getRole()) && StringUtils.isNotBlank(locator.getName());
            case "LABEL" -> StringUtils.isNotBlank(locator.getLabel());
            case "PLACEHOLDER" -> StringUtils.isNotBlank(locator.getPlaceholder());
            case "TEXT" -> StringUtils.isNotBlank(locator.getText());
            case "CSS" -> StringUtils.isNotBlank(locator.getSelector()) && !locator.getSelector().matches("(?is).*(javascript:|expression\\s*\\(|url\\s*\\().*");
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
