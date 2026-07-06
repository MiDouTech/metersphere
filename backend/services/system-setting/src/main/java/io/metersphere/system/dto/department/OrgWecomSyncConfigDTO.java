package io.metersphere.system.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class OrgWecomSyncConfigDTO {

    @Schema(description = "组织ID")
    private String organizationId;

    @Schema(description = "企微 CorpID")
    private String corpId;

    @Schema(description = "通讯录 Secret（掩码）")
    private String contactSecret;

    @Schema(description = "应用 AgentId（可选）")
    private String agentId;

    @Schema(description = "是否启用定时同步")
    private Boolean scheduleEnabled;

    @Schema(description = "Cron 表达式")
    private String scheduleCron;

    @Schema(description = "失败重试次数")
    private Integer retryTimes;

    @Schema(description = "最近同步时间")
    private Long lastSyncTime;

    @Schema(description = "是否已配置")
    private Boolean configured;
}
