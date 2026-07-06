package io.metersphere.system.dto.department;

import io.metersphere.system.dto.sdk.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrgWecomSyncLogPageRequest extends BasePageRequest {

    @Schema(description = "组织ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{organization.id.not_blank}")
    private String organizationId;

    @Schema(description = "同步状态 SUCCESS/PARTIAL/FAILED")
    private String syncStatus;
}
