package io.metersphere.functional.hub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class DefaultHubSyncRequest {
    @Schema(description = "业务项目ID；空=全量对账")
    private String projectId;
}
