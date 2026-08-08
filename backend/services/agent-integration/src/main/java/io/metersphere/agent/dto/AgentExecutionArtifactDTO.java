package io.metersphere.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class AgentExecutionArtifactDTO {
    private String id;
    private String taskId;
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
    private Long retentionUntil;
    private Long createTime;
    private String createUser;
    private String downloadPath;
}
