package io.metersphere.system.mapper;

import io.metersphere.system.domain.UserRoleUiPermission;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserRoleUiPermissionMapper {
    List<UserRoleUiPermission> selectByRoleId(@Param("roleId") String roleId);

    List<UserRoleUiPermission> selectByRoleIds(@Param("roleIds") List<String> roleIds);

    int deleteByRoleId(@Param("roleId") String roleId);

    int insert(UserRoleUiPermission record);

    int batchInsert(@Param("list") List<UserRoleUiPermission> list);
}
