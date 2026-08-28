package io.metersphere.agent.dto;
import jakarta.validation.constraints.NotBlank;import lombok.Data;
@Data public class AgentPromptTemplateVersionRequest { @NotBlank private String projectId; private String promptTemplateId; @NotBlank private String name; @NotBlank private String systemTemplate; @NotBlank private String businessTemplate; @NotBlank private String variableSchema; @NotBlank private String outputSchemaVersion; }
