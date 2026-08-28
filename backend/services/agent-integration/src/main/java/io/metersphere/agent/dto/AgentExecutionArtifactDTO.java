package io.metersphere.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class AgentExecutionArtifactDTO {
    private String id;
    private String taskId;
    private String executionId;
    private String leaseId;
    private String executionCaseId;
    private String caseId;
    private String stepId;
    private String purpose;
    @JsonIgnore
    private String fileId;
    private String fileName;
    @JsonIgnore
    private String storageFolder;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private Boolean redacted;
    private String status;
    private String uploadStatus;
    private Long expectedSize;
    private String expectedSha256;
    private String expectedContentType;
    @JsonIgnore
    private String uploadTokenHash;
    private String idempotencyKey;
    private Long preparedAt;
    private Long committedAt;
    private String traceId;
    private Long retentionUntil;
    private Long createTime;
    private String createUser;
    private String downloadPath;
}
