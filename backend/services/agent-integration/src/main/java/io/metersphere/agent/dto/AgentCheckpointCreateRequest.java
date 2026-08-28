package io.metersphere.agent.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data public class AgentCheckpointCreateRequest {
    @NotBlank private String executionId;
    @NotBlank @Size(max = 255) private String reason;
    @NotBlank @Size(max = 2_000_000) private String stateSnapshot;
    @Size(max = 128) private String requestId;
}
