package io.metersphere.system.dto.permission.control;

import io.metersphere.system.domain.StatusFlowRolePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowRolePermissionRequest {
    @NotBlank
    private String flowId;
    @Valid
    private List<StatusFlowRolePermission> permissions;
}
