package io.metersphere.agent.dto;

import lombok.Data;

/** Raw source fields used to build a secret-free, immutable asset snapshot. */
@Data
public class TestAssetExecutableSnapshotDTO {
    private String rawContentJson;
    private String fileId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String storage;
    private String filePath;
    private String moduleId;
    private String tagsJson;

    private byte[] environmentConfig;

    private byte[] commonParams;
    private byte[] commonScript;
    private byte[] commonResult;

    private String protocol;
    private String httpMethod;
    private String apiPath;
    private byte[] apiRequest;
    private byte[] apiResponse;

    private String taskId;
    private String executionCaseId;
    private String caseId;
    private String stepId;
    private String purpose;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private Boolean redacted;
    private Long retentionUntil;

    private Integer bugNumber;
    private String bugDescription;
    private String handleUser;
}
