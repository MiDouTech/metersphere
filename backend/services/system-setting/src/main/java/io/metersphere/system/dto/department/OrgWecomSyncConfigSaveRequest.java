package io.metersphere.system.dto.department;

import io.metersphere.validation.groups.Created;
import io.metersphere.validation.groups.Updated;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrgWecomSyncConfigSaveRequest {

    @Schema(description = "组织ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{organization.id.not_blank}", groups = {Created.class, Updated.class})
    private String organizationId;

    @Schema(description = "企微 CorpID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_wecom_sync_config.corp_id.not_blank}", groups = {Created.class, Updated.class})
    @Size(max = 100, groups = {Created.class, Updated.class})
    private String corpId;

    @Schema(description = "通讯录 Secret", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_wecom_sync_config.contact_secret.not_blank}", groups = {Created.class})
    @Size(max = 255, groups = {Created.class, Updated.class})
    private String contactSecret;

    @Schema(description = "应用 AgentId（可选）")
    private String agentId;

    @Schema(description = "是否启用定时同步")
    private Boolean scheduleEnabled;

    @Schema(description = "Cron 表达式")
    private String scheduleCron;

    @Schema(description = "失败重试次数")
    private Integer retryTimes;
}
