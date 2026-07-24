package io.metersphere.system.edit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResourceEditLockRequest {
    @NotBlank
    @Schema(description = "资源类型")
    private String resourceType;

    @NotBlank
    @Schema(description = "资源ID")
    private String resourceId;

    @NotBlank
    @Schema(description = "项目ID")
    private String projectId;
}
