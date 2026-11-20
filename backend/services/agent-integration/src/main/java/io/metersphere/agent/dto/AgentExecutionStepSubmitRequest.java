package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AgentExecutionStepSubmitRequest {
    @NotBlank private String taskId;
    @NotBlank private String executionId;
    @NotBlank private String leaseId;
    @NotBlank private String leaseToken;
    @NotBlank private String stepId;
    private Integer attempt;
    @NotBlank private String status;
    private String inputSnapshot;
    private String outputSummary;
    @io.swagger.v3.oas.annotations.media.Schema(description="Frozen v1: ordered [{actual}]; v2: [{assertionId,actual}], DECIMAL/INTEGER as strings. Maximum UTF-8 payload 65535 bytes.")
    private String assertionResult;
    private String errorCode;
    private String errorMessage;
    private List<String> artifactIds;
    @NotBlank private String requestId;
    private String traceId;
    private Long startedAt;
    private Long finishedAt;
}
