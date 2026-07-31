package io.metersphere.system.mapper;

import io.metersphere.system.domain.AgentIdempotencyRecord;
import org.apache.ibatis.annotations.Param;

public interface AgentIdempotencyRecordMapper {
    int insert(AgentIdempotencyRecord record);

    AgentIdempotencyRecord selectByTokenToolRequest(@Param("tokenId") String tokenId,
                                                    @Param("toolName") String toolName,
                                                    @Param("requestId") String requestId);

    int deleteExpired(@Param("expiresBefore") long expiresBefore);
}
