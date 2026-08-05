package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FunctionalCaseAiGenerationDTO {
    @Schema(description = "Generation task ID")
    private String id;
    @Schema(description = "Project ID")
    private String projectId;
    @Schema(description = "AI conversation ID")
    private String conversationId;
    @Schema(description = "AI model source ID")
    private String modelSourceId;
    @Schema(description = "Prompt or input summary")
    private String prompt;
    @Schema(description = "Generation status")
    private String status;
    @Schema(description = "Token usage")
    private Long tokenUsage;
    @Schema(description = "Duration in milliseconds")
    private Long durationMs;
    @Schema(description = "Error message")
    private String errorMessage;
    @Schema(description = "Creator")
    private String createUser;
    @Schema(description = "Created at")
    private Long createTime;
    @Schema(description = "Updated at")
    private Long updateTime;
}
