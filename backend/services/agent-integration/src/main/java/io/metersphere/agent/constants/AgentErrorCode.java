package io.metersphere.agent.constants;

import io.metersphere.sdk.exception.IResultCode;

/**
 * Agent MCP 业务错误码。JSON-RPC error.data.code 使用 name()。
 */
public enum AgentErrorCode implements IResultCode {
    PROJECT_NOT_FOUND(180001),
    PROJECT_NOT_ALLOWED(180002),
    RESOURCE_NOT_FOUND(180003),
    RESOURCE_PROJECT_MISMATCH(180004),
    SCOPE_DENIED(180005),
    RBAC_DENIED(180006),
    VERSION_CONFLICT(180007),
    ATTACHMENT_EXPIRED(180008),
    ATTACHMENT_PURPOSE_MISMATCH(180009),
    ATTACHMENT_LIMIT_EXCEEDED(180010),
    ATTACHMENT_TYPE_NOT_ALLOWED(180011),
    IDEMPOTENCY_CONFLICT(180012),
    CONFIRMATION_REQUIRED(180013);

    private final int code;

    AgentErrorCode(int code) {
        this.code = code;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return name();
    }
}
