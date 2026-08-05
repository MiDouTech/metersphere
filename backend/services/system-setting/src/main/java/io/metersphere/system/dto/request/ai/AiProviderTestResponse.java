package io.metersphere.system.dto.request.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AiProviderTestResponse {
    @Schema(description = "Whether connection test succeeded")
    private boolean success;

    @Schema(description = "Response content when success")
    private String content;

    @Schema(description = "Failure message")
    private String message;

    @Schema(description = "Elapsed milliseconds")
    private long durationMs;
}
