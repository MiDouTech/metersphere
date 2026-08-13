package io.metersphere.agent.mapper;

import io.metersphere.agent.dto.AgentHumanRequestDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentHumanRequestMapper {
    void insert(AgentHumanRequestDTO request);
    AgentHumanRequestDTO selectById(@Param("id") String id);
    AgentHumanRequestDTO selectByTaskAndKey(@Param("taskId") String taskId, @Param("requestKey") String requestKey);
    List<AgentHumanRequestDTO> selectByTaskId(@Param("taskId") String taskId);
    int countPendingByTaskId(@Param("taskId") String taskId);
    int respond(@Param("id") String id, @Param("status") String status,
                @Param("response") String response, @Param("respondedBy") String respondedBy,
                @Param("respondedAt") long respondedAt);
    int closePendingByTaskAndType(@Param("taskId") String taskId, @Param("requestType") String requestType,
                                  @Param("status") String status, @Param("response") String response,
                                  @Param("respondedBy") String respondedBy, @Param("respondedAt") long respondedAt);
}
