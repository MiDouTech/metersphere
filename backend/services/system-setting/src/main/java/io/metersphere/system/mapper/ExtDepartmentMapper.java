package io.metersphere.system.mapper;

import io.metersphere.system.domain.Department;
import io.metersphere.system.dto.department.DepartmentUserCountDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtDepartmentMapper {

    /**
     * 按组织查询全部部门（树构建在 Service 层完成）
     */
    List<Department> listByOrganizationId(@Param("organizationId") String organizationId);

    /**
     * 按组织与企微部门ID查询
     */
    Department selectByWecomDeptId(@Param("organizationId") String organizationId,
                                   @Param("wecomDeptId") Long wecomDeptId);

    /**
     * 按组织统计启用部门数量
     */
    long countEnabledByOrganizationId(@Param("organizationId") String organizationId);

    /**
     * 统计组织内各部门直属成员数
     */
    List<DepartmentUserCountDTO> countDirectUsersByOrganizationId(@Param("organizationId") String organizationId);
}
