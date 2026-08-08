package io.metersphere.system.mapper;

import io.metersphere.system.domain.PermissionResource;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PermissionResourceMapper {
    List<PermissionResource> selectEnabledByScopeType(@Param("scopeType") String scopeType);

    List<PermissionResource> selectEnabled();

    List<PermissionResource> selectEnabledByCodes(@Param("codes") List<String> codes);

    long countEnabledByCodes(@Param("codes") List<String> codes);
}
