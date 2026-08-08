package io.metersphere.system.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class PermissionResource implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String code;
    private String name;
    private String type;
    private String scopeType;
    private String parentCode;
    private String routeName;
    private String permissionId;
    private Boolean visibleDefault;
    private Boolean operableDefault;
    private Integer sort;
    private Boolean enabled;
    private String description;
}
