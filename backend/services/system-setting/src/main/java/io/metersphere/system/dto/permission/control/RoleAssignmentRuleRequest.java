package io.metersphere.system.dto.permission.control;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoleAssignmentRuleRequest {
    @NotBlank
    private String roleId;
    @NotBlank
    private String organizationId;
    private String departmentId;
    private String positionId;
    private Boolean enabled = true;
    private String syncMode = "MANUAL";
}
