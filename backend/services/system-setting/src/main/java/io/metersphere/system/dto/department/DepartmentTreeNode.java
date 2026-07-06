package io.metersphere.system.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DepartmentTreeNode {
    @Schema(description = "部门ID")
    private String id;

    @Schema(description = "部门名称")
    private String name;

    @Schema(description = "父部门本地ID")
    private String parentId;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "直属成员数")
    private Long directUserCount;

    @Schema(description = "含子部门成员数")
    private Long totalUserCount;

    @Schema(description = "1启用 0停用")
    private Integer deptStatus;

    @Schema(description = "同步状态")
    private Integer syncStatus;

    @Schema(description = "最近同步时间")
    private Long syncTime;

    @Schema(description = "子部门")
    private List<DepartmentTreeNode> children = new ArrayList<>();
}
