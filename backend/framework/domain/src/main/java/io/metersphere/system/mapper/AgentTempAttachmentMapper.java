package io.metersphere.system.mapper;

import io.metersphere.system.domain.AgentTempAttachment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentTempAttachmentMapper {
    int insert(AgentTempAttachment record);

    int updateByPrimaryKeySelective(AgentTempAttachment record);

    AgentTempAttachment selectByPrimaryKey(@Param("id") String id);

    List<AgentTempAttachment> selectExpiredUnlinked(@Param("expiresBefore") long expiresBefore, @Param("limit") int limit);

    int deleteByPrimaryKey(@Param("id") String id);
}
