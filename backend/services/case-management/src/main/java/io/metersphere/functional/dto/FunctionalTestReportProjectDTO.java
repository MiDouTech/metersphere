package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FunctionalTestReportProjectDTO {

    @Schema(description = "项目ID")
    private String projectId;

    @Schema(description = "当前用户是否拥有报告所属项目权限")
    private boolean hasProjectPermission;
}
