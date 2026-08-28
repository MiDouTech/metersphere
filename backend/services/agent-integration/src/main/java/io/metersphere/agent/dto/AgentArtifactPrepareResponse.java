package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentArtifactPrepareResponse {
    private String artifactId;
    private String uploadPath;
    private String uploadToken;
    private Long expiresAt;
    private String status;
}
