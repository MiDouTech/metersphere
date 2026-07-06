package io.metersphere.system.mapper;

import io.metersphere.system.domain.OrgSyncLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtOrgSyncLogMapper {

    OrgSyncLog selectLatestByOrganizationId(@Param("organizationId") String organizationId);

    List<OrgSyncLog> listByOrganizationId(@Param("organizationId") String organizationId,
                                            @Param("syncStatus") String syncStatus);
}
