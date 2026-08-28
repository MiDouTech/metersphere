package io.metersphere.agent.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data public class AgentCheckpointResumeRequest {
    @NotBlank @Size(min = 40, max = 128) private String resumeToken;
    @NotBlank private String preflightId;
}
