package io.metersphere.system.mapper;

import io.metersphere.system.dto.department.OrgStructureMemberDetailDTO;
import io.metersphere.system.dto.department.OrgStructureMemberItemDTO;
import io.metersphere.system.dto.department.OrgStructureMemberPageRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtOrgStructureMemberMapper {

    List<OrgStructureMemberItemDTO> pageMembers(@Param("request") OrgStructureMemberPageRequest request);

    OrgStructureMemberDetailDTO getMemberDetail(@Param("userId") String userId,
                                                @Param("organizationId") String organizationId);
}
