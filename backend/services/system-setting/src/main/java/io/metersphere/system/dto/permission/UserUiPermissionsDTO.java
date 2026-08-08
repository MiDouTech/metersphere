package io.metersphere.system.dto.permission;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserUiPermissionsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private UiPermissionSetDTO system = new UiPermissionSetDTO();
    private UiPermissionSetDTO organization = new UiPermissionSetDTO();
    private UiPermissionSetDTO project = new UiPermissionSetDTO();
}
