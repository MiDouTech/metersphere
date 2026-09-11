package io.metersphere.agent.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AgentExecutionHistoryRequest {
    @NotBlank private String projectId;
    @Size(max = 255) private String keyword;
    @Size(max = 40) private String status;
    @Min(0) private Long createdAfter;
    @Min(0) private Long createdBefore;
    @NotNull @Min(1) @Max(1000000) private Integer current = 1;
    @NotNull @Min(1) @Max(100) private Integer pageSize = 20;
}
