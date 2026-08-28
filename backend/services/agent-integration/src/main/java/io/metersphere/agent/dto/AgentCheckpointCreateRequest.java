package io.metersphere.agent.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data public class AgentCheckpointCreateRequest { @NotBlank private String executionId; @NotBlank private String reason; @NotBlank private String stateSnapshot; }
