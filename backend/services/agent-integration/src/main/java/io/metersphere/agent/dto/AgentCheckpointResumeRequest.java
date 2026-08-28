package io.metersphere.agent.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data public class AgentCheckpointResumeRequest { @NotBlank private String resumeToken; @NotBlank private String preflightId; }
