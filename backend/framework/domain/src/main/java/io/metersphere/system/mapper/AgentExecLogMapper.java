package io.metersphere.system.mapper;

import io.metersphere.system.domain.AgentExecLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentExecLogMapper {
    int insert(AgentExecLog record);

    AgentExecLog selectByPrimaryKey(@Param("id") String id);

    List<AgentExecLog> selectPage(@Param("caseId") String caseId,
                                  @Param("executedBy") String executedBy,
                                  @Param("offset") long offset,
                                  @Param("pageSize") long pageSize);

    long countPage(@Param("caseId") String caseId, @Param("executedBy") String executedBy);
}
