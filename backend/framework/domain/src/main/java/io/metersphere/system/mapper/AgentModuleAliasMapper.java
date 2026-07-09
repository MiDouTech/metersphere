package io.metersphere.system.mapper;

import io.metersphere.system.domain.AgentModuleAlias;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentModuleAliasMapper {
    int insert(AgentModuleAlias record);

    int deleteByPrimaryKey(@Param("id") String id);

    List<AgentModuleAlias> selectByProjectId(@Param("projectId") String projectId);

    AgentModuleAlias selectByProjectAndAlias(@Param("projectId") String projectId, @Param("alias") String alias);
}
