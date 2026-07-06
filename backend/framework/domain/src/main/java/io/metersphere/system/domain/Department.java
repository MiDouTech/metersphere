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
public class Department implements Serializable {
    @Schema(description = "部门ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{department.id.not_blank}", groups = {Updated.class})
    @Size(min = 1, max = 50, message = "{department.id.length_range}", groups = {Created.class, Updated.class})
    private String id;

    @Schema(description = "所属MeterSphere组织ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{department.organization_id.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 50, message = "{department.organization_id.length_range}", groups = {Created.class, Updated.class})
    private String organizationId;

    @Schema(description = "部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{department.name.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 255, message = "{department.name.length_range}", groups = {Created.class, Updated.class})
    private String name;

    @Schema(description = "父部门本地ID")
    private String parentId;

    @Schema(description = "企微部门ID")
    private Long wecomDeptId;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "1启用 0停用")
    private Integer deptStatus;

    @Schema(description = "0未同步 1已同步 2同步失败")
    private Integer syncStatus;

    @Schema(description = "最近同步时间戳")
    private Long syncTime;

    @Schema(description = "部门负责人企微UserID")
    private String leaderWecomUserid;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "{department.create_time.not_blank}", groups = {Created.class})
    private Long createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "{department.update_time.not_blank}", groups = {Created.class})
    private Long updateTime;

    @Schema(description = "创建人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{department.create_user.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 50, message = "{department.create_user.length_range}", groups = {Created.class, Updated.class})
    private String createUser;

    @Schema(description = "修改人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{department.update_user.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 50, message = "{department.update_user.length_range}", groups = {Created.class, Updated.class})
    private String updateUser;

    private static final long serialVersionUID = 1L;

    public enum Column {
        id("id", "id", "VARCHAR", false),
        organizationId("organization_id", "organizationId", "VARCHAR", false),
        name("name", "name", "VARCHAR", true),
        parentId("parent_id", "parentId", "VARCHAR", false),
        wecomDeptId("wecom_dept_id", "wecomDeptId", "BIGINT", false),
        sortOrder("sort_order", "sortOrder", "INTEGER", false),
        deptStatus("dept_status", "deptStatus", "TINYINT", false),
        syncStatus("sync_status", "syncStatus", "TINYINT", false),
        syncTime("sync_time", "syncTime", "BIGINT", false),
        leaderWecomUserid("leader_wecom_userid", "leaderWecomUserid", "VARCHAR", false),
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
