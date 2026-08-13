package io.metersphere.agent.mapper;

import io.metersphere.agent.dto.AgentTaskTriggerDTO;
import io.metersphere.agent.dto.AgentTaskTriggerHistoryDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentTaskTriggerMapper {
    void insert(AgentTaskTriggerDTO trigger);
    AgentTaskTriggerDTO selectById(@Param("id") String id);
    List<AgentTaskTriggerDTO> selectByProject(@Param("projectId") String projectId);
    List<AgentTaskTriggerDTO> selectDue(@Param("now") long now, @Param("limit") int limit);
    int update(AgentTaskTriggerDTO trigger);
    int claimScheduledFire(@Param("id") String id, @Param("version") int version,
                           @Param("scheduledAt") long scheduledAt, @Param("nextFireAt") Long nextFireAt,
                           @Param("updatedAt") long updatedAt);
    int updateFireResult(@Param("id") String id, @Param("status") String status,
                         @Param("error") String error, @Param("fireAt") long fireAt);
    int countActiveTasks(@Param("triggerId") String triggerId);
    void insertHistory(AgentTaskTriggerHistoryDTO history);
    List<AgentTaskTriggerHistoryDTO> selectHistory(@Param("triggerId") String triggerId,
                                                   @Param("limit") int limit);
    AgentTaskTriggerHistoryDTO selectHistoryByEvent(@Param("triggerId") String triggerId,
                                                    @Param("eventId") String eventId);
}
