package io.metersphere.system.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class OrgWecomSyncConfigTestResponse {

    @Schema(description = "是否成功")
    private boolean success;

    @Schema(description = "部门数量")
    private Integer deptCount;

    @Schema(description = "提示信息")
    private String message;
}
