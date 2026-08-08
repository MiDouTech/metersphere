package io.metersphere.system.dto.permission;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class RoleUiPermissionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank
    private String resourceCode;
    private Boolean visible = false;
    private Boolean operable = false;
}
