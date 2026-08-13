package io.metersphere.system.dto.permission.control;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoleEnableRequest {
    @NotBlank
    private String roleId;
    @NotNull
    private Boolean enabled;
}
