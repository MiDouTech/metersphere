package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentApiErrorDTO;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class AgentSafeErrorMapper {
    public AgentApiErrorDTO toApiError(Throwable error, String suppliedTraceId) {
        String traceId = StringUtils.defaultIfBlank(suppliedTraceId, UUID.randomUUID().toString());
        if (error instanceof MSException && isStableCode(error.getMessage())) {
            return new AgentApiErrorDTO(error.getMessage(), safeMessage(error.getMessage()), Map.of(), traceId);
        }
        return new AgentApiErrorDTO("AI_EXECUTION_INTERNAL_ERROR", "服务暂时不可用，请使用 traceId 联系管理员", Map.of(), traceId);
    }

    private boolean isStableCode(String value) {
        return StringUtils.isNotBlank(value) && value.matches("^[A-Z][A-Z0-9_]{2,63}$");
    }

    private String safeMessage(String code) {
        return switch (code) {
            case "MCP_TOOL_FORBIDDEN" -> "当前身份无权调用该 MCP 工具";
            case "TASK_ORIGIN_CHANNEL_MISMATCH" -> "任务来源与执行通道不匹配";
            case "PERMISSION_DENIED" -> "没有执行此操作的权限";
            case "VALIDATION_ERROR" -> "请求参数校验失败";
            case "ENVIRONMENT_PROFILE_DISABLED" -> "所选环境执行配置已停用";
            case "MODEL_SERVICE_KEY_REF_INVALID" -> "Gateway Service Key 引用必须使用 env:// 或 vault:// 格式";
            case "CREDENTIAL_SECRET_REF_INVALID" -> "Secret 引用格式无效，请使用 env://变量名 或 vault://mount/path#field";
            case "CREDENTIAL_SECRET_REF_UNAVAILABLE" -> "Secret 引用不存在或当前不可用";
            case "CREDENTIAL_ENVIRONMENT_NOT_FOUND" -> "所选环境不存在对应的环境执行配置";
            case "CREDENTIAL_TYPE_INVALID" -> "不支持当前凭据类型";
            case "CREDENTIAL_EXPIRY_INVALID" -> "凭据过期时间必须晚于当前时间";
            case "CREDENTIAL_NAME_CONFLICT" -> "同一环境下凭据引用名称不能重复";
            case "CREDENTIAL_VERSION_CONFLICT" -> "凭据引用已被修改，请刷新后重试";
            case "ENV_SECRET_PROVIDER_DISABLED" -> "当前部署未启用 ENV Secret Provider";
            case "VAULT_SECRET_PROVIDER_DISABLED" -> "当前部署未启用 Vault Secret Provider";
            case "VAULT_AUTHENTICATION_UNAVAILABLE" -> "Vault 服务身份不可用";
            case "VAULT_ADDRESS_INVALID" -> "Vault 地址必须配置为 HTTPS 地址";
            case "VAULT_REQUEST_INTERRUPTED" -> "Vault 请求被中断，请稍后重试";
            case "ALERT_NOT_OPEN_OR_NOT_FOUND" -> "告警不存在、无权访问或已被确认";
            case "MODEL_BUDGET_EXCEEDED" -> "任务已超过模型调用预算并被阻塞";
            case "CHECKPOINT_CREATE_CONFLICT", "CHECKPOINT_RESUME_CONFLICT", "CHECKPOINT_RESUME_STATE_CONFLICT" -> "任务状态已变化，请刷新后重试";
            case "CHECKPOINT_LEASE_INVALID_OR_EXPIRED" -> "执行租约已失效，不能创建检查点";
            case "PUBLISHED_ASSET_VERSION_NOT_FOUND" -> "资产尚无可供执行的已发布版本";
            case "FROZEN_ASSET_VERSION_NOT_FOUND" -> "任务冻结的资产版本不存在或已失效";
            case "ASSET_VERSION_DEPRECATE_CONFLICT" -> "资产版本状态已变化，请刷新后重试";
            case "TEST_DATASET_NOT_IN_FROZEN_SCOPE" -> "数据集不在本次任务冻结范围内";
            case "TEST_DATA_SNAPSHOT_INTEGRITY_FAILED" -> "测试数据快照完整性校验失败";
            case "PREFLIGHT_REQUEST_MISMATCH" -> "任务参数与执行前检查结果不一致";
            default -> "请求未能完成，请根据错误码和 traceId 排查";
        };
    }
}
