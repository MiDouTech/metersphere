package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentExecutionArtifactUploadResponse {
    private String artifactId;
    private String purpose;
    private String sha256;
    private Long sizeBytes;
    private String downloadPath;
}
