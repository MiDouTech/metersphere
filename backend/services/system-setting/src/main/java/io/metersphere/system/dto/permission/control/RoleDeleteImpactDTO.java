package io.metersphere.system.dto.permission.control;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoleDeleteImpactDTO {
    private long memberCount;
    private long usersWithoutOtherBusinessRoleCount;
}
