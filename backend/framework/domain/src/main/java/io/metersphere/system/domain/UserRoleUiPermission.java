package io.metersphere.system.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserRoleUiPermission implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String roleId;
    private String resourceCode;
    private Boolean visible;
    private Boolean operable;
}
