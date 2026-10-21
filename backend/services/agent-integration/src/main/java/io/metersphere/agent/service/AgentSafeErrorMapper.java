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
        if (error instanceof MSException business && business.getErrorCode() instanceof io.metersphere.agent.constants.AgentErrorCode code) {
            return new AgentApiErrorDTO(code.name(), safeMessage(code.name()), Map.of(), traceId);
        }
        if (error instanceof jakarta.validation.ValidationException || error instanceof IllegalArgumentException) {
            return new AgentApiErrorDTO("AGENT_VALIDATION_ERROR", safeMessage("AGENT_VALIDATION_ERROR"), Map.of(), traceId);
        }
        if (error instanceof MSException) {
            String commentCode = switch (StringUtils.defaultString(error.getMessage())) {
                case "评论已被更新，请刷新后重试" -> "VERSION_CONFLICT";
                case "只能编辑当前用例中本人创建的评论" -> "AGENT_PERMISSION_DENIED";
                case "用例已删除，不能编辑评论" -> "RESOURCE_NOT_FOUND";
                case "请刷新评论后重试，评论内容不能为空或超过限制" -> "AGENT_VALIDATION_ERROR";
                default -> null;
            };
            if (commentCode != null) return new AgentApiErrorDTO(commentCode, error.getMessage(), Map.of(), traceId);
        }
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
            case "SCOPE_DENIED", "RBAC_DENIED", "PROJECT_NOT_ALLOWED" -> "当前令牌或账号没有执行此项目操作的权限";
            case "PROJECT_NOT_FOUND" -> "项目不存在或当前账号无权访问";
            case "IDEMPOTENCY_CONFLICT" -> "相同请求标识的参数已变化，请使用新的请求标识";
            case "PROJECT_IDENTITY_REQUIRED" -> "请选择项目或提供项目 ID、编号或名称";
            case "PROJECT_IDENTITY_AMBIGUOUS" -> "项目标识存在歧义，请搜索授权项目后使用内部项目 ID";
            case "QUALITY_CLAIM_ACTOR_REVOKED" -> "任务发起人的项目权限已失效，请重新授权后再执行";
            case "QUALITY_CLAIM_PROJECT_UNAVAILABLE", "QUALITY_CLAIM_CASE_UNAVAILABLE", "QUALITY_CLAIM_ENVIRONMENT_UNAVAILABLE", "QUALITY_CLAIM_CREDENTIAL_UNAVAILABLE" -> "执行所需的项目、用例、环境或凭据已失效，请重新预检后发起任务";
            case "AGENT_RESOURCE_NOT_ACCESSIBLE" -> "资源不存在或当前账号和令牌无权访问，请检查项目授权";
            case "IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED" -> "历史请求结果需要核对，请先查询资源，不要直接重新执行";
            case "REQUEST_IN_PROGRESS" -> "请求正在处理，请稍后使用相同请求标识查询或重试";
            case "AGENT_RESULT_LIMIT_EXCEEDED", "AGENT_SERIALIZATION_ERROR" -> "请求结果暂时无法保存，请提供 traceId 联系管理员";
            case "AGENT_VALIDATION_ERROR" -> "参数不符合要求，请检查必填项、数量与字段格式";
            case "AGENT_REVIEW_STATE_CONFLICT" -> "评审状态已变化，请刷新后重试";
            case "QUALITY_FACTS_KEY_NOT_CONFIGURED" -> "敏感执行事实需要管理员配置加密密钥后再提交";
            case "QUALITY_FACTS_ENCRYPTION_FAILED", "QUALITY_FACTS_DECRYPTION_FAILED" -> "受保护的执行事实暂时不可用，请提供 traceId 联系管理员";
            case "QUALITY_FINAL_FACTS_INCOMPLETE", "QUALITY_FAILURE_FACTS_MISMATCH" -> "执行事实不完整或与申报结果不一致，不能同步正式结果";
            case "QUALITY_FINAL_FACTS_HASH_INVALID" -> "最终事实校验失败，请联系管理员";
            case "EXECUTION_FACTS_FINALIZED_CONFLICT" -> "执行事实已冻结，不能继续提交或修改本次结果";
            case "STEP_RESULT_REQUEST_CONFLICT" -> "同一请求标识对应的提交内容已变化，请使用新的请求标识";
            case "STEP_RESULT_LEGACY_REQUEST_CONFLICT" -> "历史结果没有可验证摘要，请查询原结果，不要重放提交";
            case "STEP_FACTS_LIMIT_EXCEEDED" -> "步骤事实超过允许容量，请减少非必要信息后重新提交";
            case "CASE_CONTENT_MISSING" -> "用例缺少有效操作或预期，请核对并重新发布";
            case "CASE_CONTENT_FORMAT_INVALID", "CASE_CONTENT_DECODE_FAILED", "CASE_CONTENT_HASH_INVALID" -> "发布版本内容无法读取，请提供 traceId 联系管理员";
            case "CASE_CONTENT_LIMIT_EXCEEDED" -> "用例内容超过安全读取限制，请联系管理员";
            case "CASE_VERSION_NOT_PUBLISHED" -> "请先发布用例版本，再执行检查";
            case "PREFLIGHT_CHECKER_VERSION_EXPIRED" -> "预检版本已失效，请重新执行预检";
            case "AUTHENTICATION_REQUIRED" -> "请登录后重试";
            case "QUALITY_POLICY_FORBIDDEN" -> "执行令牌不能管理质量策略，请使用具有策略权限的登录账号";
            case "QUALITY_LEGACY_SUBMIT_FORBIDDEN" -> "旧结果直写接口已停用，请通过执行任务提交步骤和证据";
            case "QUALITY_POLICY_NOT_CONFIGURED" -> "尚未发布平台统一门禁策略，无法开始执行";
            case "QUALITY_POLICY_BINDING_NOT_FOUND" -> "执行未绑定平台策略，请重新发起执行";
            case "QUALITY_POLICY_HASH_INVALID", "QUALITY_CONTRACT_INVALID" -> "执行策略或合同校验失败，请联系管理员";
            case "QUALITY_EVIDENCE_REQUIRED", "QUALITY_EVIDENCE_INVALID" -> "执行证据不符合平台门禁要求，请补充本步骤的有效证据";
            case "QUALITY_ASSERTION_INVALID", "QUALITY_ASSERTION_FAILED", "QUALITY_RULE_UNSUPPORTED" -> "断言未通过平台门禁，请核对冻结预期和实际结果";
            case "QUALITY_ATTEMPT_INVALID" -> "提交的执行轮次与当前租约不一致";
            case "QUALITY_POLICY_LEGACY_WRITE_FORBIDDEN" -> "项目策略已停止维护，请进入系统设置中的平台质量门禁";
            case "QUALITY_POLICY_PROJECT_FORBIDDEN" -> "无权访问该项目的质量策略，请确认当前项目";
            case "QUALITY_POLICY_PROJECT_DISABLED" -> "项目已停用，质量策略仅可查看，不能修改或发布";
            case "QUALITY_POLICY_NOT_FOUND" -> "质量策略不存在或不属于当前项目";
            case "QUALITY_POLICY_VERSION_CONFLICT" -> "策略或当前发布版本已变化，请刷新后比较并重试";
            case "MCP_TOOL_NOT_FOUND" -> "MCP 工具不存在，请刷新工具清单";
            case "BUG_REQUIRED_FIELD_MISSING" -> "请补齐缺陷必填字段和处理人";
            case "BUG_FIELD_VALUE_INVALID" -> "缺陷字段值或处理人不在当前项目允许范围内";
            case "BUG_INITIAL_STATUS_SERVER_OWNED" -> "新建缺陷状态由已发布工作流决定，请勿指定状态";
            case "BUG_IMPORT_PREVIEW_CHANGED" -> "导入内容或模板已变化，请重新预检";
            case "BUG_IMPORT_FILE_INVALID" -> "请提供符合模板的 XLSX 文件，不能包含公式或重复表头";
            case "BUG_TRANSFER_LIMIT_EXCEEDED" -> "单次最多处理100条缺陷，文件不超过5MiB";
            case "BUG_IMPORT_LOCAL_ONLY" -> "当前批量导入仅支持本地缺陷项目";
            case "BUG_EXPORT_EMPTY" -> "没有可导出的缺陷";
            case "BUG_WORKFLOW_NOT_PUBLISHED", "BUG_WORKFLOW_INITIAL_STATUS_INVALID" -> "请管理员发布具有唯一初始状态的缺陷工作流";
            case "MCP_TOOL_FORBIDDEN" -> "当前身份无权调用该 MCP 工具";
            case "TASK_ORIGIN_CHANNEL_MISMATCH" -> "任务来源与执行通道不匹配";
            case "PERMISSION_DENIED" -> "没有执行此操作的权限";
            case "VALIDATION_ERROR" -> "请求参数校验失败";
            case "RESOURCE_NOT_FOUND", "RESOURCE_PROJECT_MISMATCH" -> "资源不存在或不属于指定项目";
            case "VERSION_CONFLICT" -> "数据已被更新，请重新查询后重试";
            case "TEST_PLAN_ARCHIVED" -> "测试计划已归档，不能修改或回写";
            case "TEST_PLAN_EXECUTING" -> "测试计划正在执行，请结束执行后再修改";
            case "CHECKPOINT_RESUME_TOKEN_INVALID" -> "断点恢复凭据无效或已使用";
            case "CHECKPOINT_HASH_MISMATCH" -> "断点数据校验失败，不能恢复";
            case "PREFLIGHT_REQUIRED_FOR_RESUME" -> "请完成新的执行预检后再恢复";
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
