package io.metersphere.system.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class OrgStructureMemberItemDTO {
    @Schema(description = "用户ID")
    private String id;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "主部门ID")
    private String departmentId;

    @Schema(description = "主部门名称")
    private String departmentName;

    @Schema(description = "职位")
    private String position;

    @Schema(description = "是否启用")
    private Boolean enable;

    @Schema(description = "同步状态")
    private Integer syncStatus;

    @Schema(description = "最近同步时间")
    private Long syncTime;
}
