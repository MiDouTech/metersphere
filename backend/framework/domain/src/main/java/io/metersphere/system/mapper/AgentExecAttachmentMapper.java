package io.metersphere.system.mapper;

import io.metersphere.system.domain.AgentExecAttachment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentExecAttachmentMapper {
    int insert(AgentExecAttachment record);

    int updateByPrimaryKeySelective(AgentExecAttachment record);

    List<AgentExecAttachment> selectByExecHistoryId(@Param("execHistoryId") String execHistoryId);

    List<AgentExecAttachment> selectByExecLogId(@Param("execLogId") String execLogId);

    AgentExecAttachment selectByPrimaryKey(@Param("id") String id);
}
