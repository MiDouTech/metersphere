package io.metersphere.system.dto.department;

import io.metersphere.system.dto.sdk.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class OrgStructureMemberPageRequest extends BasePageRequest {

    @Schema(description = "组织ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String organizationId;

    @Schema(description = "部门ID")
    private String departmentId;

    @Schema(description = "姓名/邮箱/手机模糊搜索")
    private String keyword;

    @Schema(description = "账号启用状态")
    private Boolean enable;

    @Schema(description = "同步状态")
    private Integer syncStatus;
}
