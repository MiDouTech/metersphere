package io.metersphere.system.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class RoleAssignmentRule implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String roleId;
    private String organizationId;
    private String departmentId;
    private String positionId;
    private Boolean enabled;
    private String syncMode;
    private Long createTime;
    private Long updateTime;
}
