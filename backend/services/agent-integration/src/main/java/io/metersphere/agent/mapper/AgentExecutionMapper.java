package io.metersphere.agent.mapper;

import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionEventDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentTestPlanDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentExecutionMapper {
    AgentExecutionTaskDTO selectTaskById(@Param("id") String id);

    AgentExecutionTaskDTO selectTaskByIdempotency(@Param("projectId") String projectId,
                                                  @Param("createUser") String createUser,
                                                  @Param("idempotencyKey") String idempotencyKey);

    List<AgentExecutionCaseDTO> selectCasesByTaskId(@Param("taskId") String taskId);

    List<AgentExecutionCaseDTO> selectCasesByTaskIdAndStatuses(@Param("taskId") String taskId,
                                                               @Param("statuses") List<String> statuses);

    List<AgentExecutionEventDTO> selectEvents(@Param("taskId") String taskId,
                                              @Param("cursor") long cursor,
                                              @Param("limit") int limit);

    Long selectMaxEventSequence(@Param("taskId") String taskId);

    void insertTask(AgentExecutionTaskDTO task);

    void insertCase(AgentExecutionCaseDTO executionCase);

    void insertEvent(AgentExecutionEventDTO event);

    int updateTaskStatus(@Param("id") String id,
                         @Param("status") String status,
                         @Param("updateUser") String updateUser,
                         @Param("updateTime") long updateTime);

    int confirmTask(@Param("id") String id,
                    @Param("status") String status,
                    @Param("updateUser") String updateUser,
                    @Param("updateTime") long updateTime);

    int updateTaskCounts(@Param("id") String id,
                         @Param("status") String status,
                         @Param("successCount") int successCount,
                         @Param("failedCount") int failedCount,
                         @Param("blockedCount") int blockedCount,
                         @Param("skippedCount") int skippedCount,
                         @Param("unexecutedCount") int unexecutedCount,
                         @Param("updateUser") String updateUser,
                         @Param("updateTime") long updateTime);

    int updateCaseStatus(@Param("taskId") String taskId,
                         @Param("caseId") String caseId,
                         @Param("status") String status,
                         @Param("result") String result,
                         @Param("errorMessage") String errorMessage,
                         @Param("updateTime") long updateTime);

    int retryFailedCases(@Param("taskId") String taskId,
                         @Param("updateTime") long updateTime);

    long countPlans(@Param("projectId") String projectId,
                    @Param("keyword") String keyword,
                    @Param("likeKeyword") String likeKeyword,
                    @Param("status") String status,
                    @Param("includeArchived") boolean includeArchived);

    List<AgentTestPlanDTO> searchPlans(@Param("projectId") String projectId,
                                       @Param("keyword") String keyword,
                                       @Param("likeKeyword") String likeKeyword,
                                       @Param("status") String status,
                                       @Param("includeArchived") boolean includeArchived,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    long countProjectCases(@Param("projectId") String projectId,
                           @Param("keyword") String keyword,
                           @Param("likeKeyword") String likeKeyword);

    List<AgentExecutionCaseDTO> selectProjectCases(@Param("projectId") String projectId,
                                                   @Param("keyword") String keyword,
                                                   @Param("likeKeyword") String likeKeyword,
                                                   @Param("limit") int limit);
}
