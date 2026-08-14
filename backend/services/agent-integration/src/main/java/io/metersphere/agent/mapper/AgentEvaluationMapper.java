package io.metersphere.agent.mapper;

import io.metersphere.agent.dto.AgentEvaluationSummaryDTO;
import io.metersphere.agent.dto.AgentEvaluationHistoryDTO;
import io.metersphere.agent.dto.AgentExecutionEvaluationDTO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface AgentEvaluationMapper {
    void upsert(AgentExecutionEvaluationDTO evaluation);
    AgentExecutionEvaluationDTO selectByTaskId(@Param("taskId") String taskId);
    List<AgentExecutionEvaluationDTO> selectByProject(@Param("projectId") String projectId,
                                                      @Param("operationalStatus") String operationalStatus,
                                                      @Param("businessVerdict") String businessVerdict,
                                                      @Param("executorType") String executorType,
                                                      @Param("offset") int offset,
                                                      @Param("limit") int limit);
    long countByProject(@Param("projectId") String projectId,
                        @Param("operationalStatus") String operationalStatus,
                        @Param("businessVerdict") String businessVerdict,
                        @Param("executorType") String executorType);
    List<AgentEvaluationSummaryDTO> summarize(@Param("projectId") String projectId,
                                              @Param("fromTime") Long fromTime,
                                              @Param("toTime") Long toTime);
    int updateManual(@Param("taskId") String taskId, @Param("score") BigDecimal score,
                     @Param("comment") String comment, @Param("evaluatedBy") String evaluatedBy,
                     @Param("evaluatedAt") long evaluatedAt);
    void insertHistory(AgentEvaluationHistoryDTO history);
    List<AgentEvaluationHistoryDTO> selectHistory(@Param("taskId") String taskId, @Param("limit") int limit);
    List<String> selectTerminalTasksWithoutEvaluation(@Param("limit") int limit);
}
