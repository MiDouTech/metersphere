package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;

/** Only server-defined codes and field paths, never submitted file content. */
public class ProjectFileException extends MSException {
    private final String fieldPath;
    private final java.util.Map<String, Object> details;
    private String traceId;
    private java.util.Map<String, Object> auditContext = java.util.Map.of();
    public ProjectFileException(String codeAndField) {
        this(codeAndField, java.util.Map.of());
    }
    public ProjectFileException(String codeAndField, java.util.Map<String, Object> details) {
        super(codeAndField.split(":", 2)[0]);
        fieldPath = codeAndField.contains(":") ? codeAndField.split(":", 2)[1] : "";
        this.details = java.util.Map.copyOf(details);
    }
    public ProjectFileException(MSException cause) {
        this(java.util.Objects.toString(cause.getMessage(), "VALIDATION_ERROR"));
        this.errorCode = cause.getErrorCode();
        initCause(cause);
    }
    public String fieldPath() { return fieldPath; }
    public java.util.Map<String, Object> details() { return details; }
    public String traceId() { return traceId; }
    public ProjectFileException withTraceId(String value) { traceId = value; return this; }
    public java.util.Map<String, Object> auditContext() { return auditContext; }
    public ProjectFileException withAuditContext(java.util.Map<String, Object> value) { auditContext = java.util.Map.copyOf(value); return this; }
}
