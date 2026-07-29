package io.metersphere.system.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrgSyncEmailConflictResolveRequest {
    @NotBlank
    @Schema(description = "冲突记录ID")
    private String id;

    @NotBlank
    @Schema(description = "SKIP|OVERWRITE|CREATE")
    private String action;
}
