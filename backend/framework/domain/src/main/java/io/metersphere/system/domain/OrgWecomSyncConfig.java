package io.metersphere.system.domain;

import io.metersphere.validation.groups.Created;
import io.metersphere.validation.groups.Updated;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import lombok.Data;

@Data
public class OrgWecomSyncConfig implements Serializable {
    @Schema(description = "ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_wecom_sync_config.id.not_blank}", groups = {Updated.class})
    @Size(min = 1, max = 50, message = "{org_wecom_sync_config.id.length_range}", groups = {Created.class, Updated.class})
    private String id;

    @Schema(description = "MS组织ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_wecom_sync_config.organization_id.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 50, message = "{org_wecom_sync_config.organization_id.length_range}", groups = {Created.class, Updated.class})
    private String organizationId;

    @Schema(description = "企微CorpID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_wecom_sync_config.corp_id.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 100, message = "{org_wecom_sync_config.corp_id.length_range}", groups = {Created.class, Updated.class})
    private String corpId;

    @Schema(description = "通讯录Secret", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_wecom_sync_config.contact_secret.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 255, message = "{org_wecom_sync_config.contact_secret.length_range}", groups = {Created.class, Updated.class})
    private String contactSecret;

    @Schema(description = "应用AgentId（可选）")
    private String agentId;

    @Schema(description = "是否启用定时同步")
    private Integer scheduleEnabled;

    @Schema(description = "Cron表达式")
    private String scheduleCron;

    @Schema(description = "失败重试次数")
    private Integer retryTimes;

    @Schema(description = "最近同步时间")
    private Long lastSyncTime;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "{org_wecom_sync_config.create_time.not_blank}", groups = {Created.class})
    private Long createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "{org_wecom_sync_config.update_time.not_blank}", groups = {Created.class})
    private Long updateTime;

    @Schema(description = "创建人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_wecom_sync_config.create_user.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 50, message = "{org_wecom_sync_config.create_user.length_range}", groups = {Created.class, Updated.class})
    private String createUser;

    @Schema(description = "修改人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_wecom_sync_config.update_user.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 50, message = "{org_wecom_sync_config.update_user.length_range}", groups = {Created.class, Updated.class})
    private String updateUser;

    private static final long serialVersionUID = 1L;

    public enum Column {
        id("id", "id", "VARCHAR", false),
        organizationId("organization_id", "organizationId", "VARCHAR", false),
        corpId("corp_id", "corpId", "VARCHAR", false),
        contactSecret("contact_secret", "contactSecret", "VARCHAR", false),
        agentId("agent_id", "agentId", "VARCHAR", false),
        scheduleEnabled("schedule_enabled", "scheduleEnabled", "TINYINT", false),
        scheduleCron("schedule_cron", "scheduleCron", "VARCHAR", false),
        retryTimes("retry_times", "retryTimes", "INTEGER", false),
        lastSyncTime("last_sync_time", "lastSyncTime", "BIGINT", false),
        createTime("create_time", "createTime", "BIGINT", false),
        updateTime("update_time", "updateTime", "BIGINT", false),
        createUser("create_user", "createUser", "VARCHAR", false),
        updateUser("update_user", "updateUser", "VARCHAR", false);

        private static final String BEGINNING_DELIMITER = "`";

        private static final String ENDING_DELIMITER = "`";

        private final String column;

        private final boolean isColumnNameDelimited;

        private final String javaProperty;

        private final String jdbcType;

        public String value() {
            return this.column;
        }

        public String getValue() {
            return this.column;
        }

        public String getJavaProperty() {
            return this.javaProperty;
        }

        public String getJdbcType() {
            return this.jdbcType;
        }

        Column(String column, String javaProperty, String jdbcType, boolean isColumnNameDelimited) {
            this.column = column;
            this.javaProperty = javaProperty;
            this.jdbcType = jdbcType;
            this.isColumnNameDelimited = isColumnNameDelimited;
        }

        public String desc() {
            return this.getEscapedColumnName() + " DESC";
        }

        public String asc() {
            return this.getEscapedColumnName() + " ASC";
        }

        public static Column[] excludes(Column ... excludes) {
            ArrayList<Column> columns = new ArrayList<>(Arrays.asList(Column.values()));
            if (excludes != null && excludes.length > 0) {
                columns.removeAll(new ArrayList<>(Arrays.asList(excludes)));
            }
            return columns.toArray(new Column[]{});
        }

        public static Column[] all() {
            return Column.values();
        }

        public String getEscapedColumnName() {
            if (this.isColumnNameDelimited) {
                return new StringBuilder().append(BEGINNING_DELIMITER).append(this.column).append(ENDING_DELIMITER).toString();
            } else {
                return this.column;
            }
        }

        public String getAliasedEscapedColumnName() {
            return this.getEscapedColumnName();
        }
    }
}
