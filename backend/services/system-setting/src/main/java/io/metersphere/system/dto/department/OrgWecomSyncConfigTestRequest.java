package io.metersphere.system.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrgWecomSyncConfigTestRequest {

    @Schema(description = "组织ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{organization.id.not_blank}")
    private String organizationId;

    @Schema(description = "企微 CorpID")
    private String corpId;

    @Schema(description = "通讯录 Secret（掩码占位时不传真实值）")
    private String contactSecret;
}
