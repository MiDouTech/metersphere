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
public class OrgSyncLog implements Serializable {
    @Schema(description = "ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_sync_log.id.not_blank}", groups = {Updated.class})
    @Size(min = 1, max = 50, message = "{org_sync_log.id.length_range}", groups = {Created.class, Updated.class})
    private String id;

    @Schema(description = "MS组织ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_sync_log.organization_id.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 50, message = "{org_sync_log.organization_id.length_range}", groups = {Created.class, Updated.class})
    private String organizationId;

    @Schema(description = "MANUAL/SCHEDULE/LOGIN", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_sync_log.sync_mode.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 20, message = "{org_sync_log.sync_mode.length_range}", groups = {Created.class, Updated.class})
    private String syncMode;

    @Schema(description = "SUCCESS/PARTIAL/FAILED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_sync_log.sync_status.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 20, message = "{org_sync_log.sync_status.length_range}", groups = {Created.class, Updated.class})
    private String syncStatus;

    @Schema(description = "部门总数")
    private Integer deptTotal;

    @Schema(description = "部门同步成功数")
    private Integer deptSuccess;

    @Schema(description = "部门同步失败数")
    private Integer deptFailed;

    @Schema(description = "用户总数")
    private Integer userTotal;

    @Schema(description = "用户同步成功数")
    private Integer userSuccess;

    @Schema(description = "用户同步失败数")
    private Integer userFailed;

    @Schema(description = "耗时毫秒")
    private Long durationMs;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "{org_sync_log.create_time.not_blank}", groups = {Created.class})
    private Long createTime;

    @Schema(description = "创建人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{org_sync_log.create_user.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 50, message = "{org_sync_log.create_user.length_range}", groups = {Created.class, Updated.class})
    private String createUser;

    private static final long serialVersionUID = 1L;

    public enum Column {
        id("id", "id", "VARCHAR", false),
        organizationId("organization_id", "organizationId", "VARCHAR", false),
        syncMode("sync_mode", "syncMode", "VARCHAR", false),
        syncStatus("sync_status", "syncStatus", "VARCHAR", false),
        deptTotal("dept_total", "deptTotal", "INTEGER", false),
        deptSuccess("dept_success", "deptSuccess", "INTEGER", false),
        deptFailed("dept_failed", "deptFailed", "INTEGER", false),
        userTotal("user_total", "userTotal", "INTEGER", false),
        userSuccess("user_success", "userSuccess", "INTEGER", false),
        userFailed("user_failed", "userFailed", "INTEGER", false),
        durationMs("duration_ms", "durationMs", "BIGINT", false),
        errorMessage("error_message", "errorMessage", "LONGVARCHAR", false),
        createTime("create_time", "createTime", "BIGINT", false),
        createUser("create_user", "createUser", "VARCHAR", false);

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
