package io.metersphere.system.edit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResourceEditSnapshotRequest {
    @NotBlank
    private String resourceType;
    @NotBlank
    private String resourceId;
    @NotBlank
    private String projectId;
    @NotBlank
    @Schema(description = "当前正式数据整单 JSON")
    private String payload;
}
