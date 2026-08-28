package io.metersphere.agent.mapper;

import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.dto.AgentExecutionEventDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.dto.AgentExecutionAttemptDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionStepResultDTO;
import io.metersphere.agent.dto.AgentExecutionHealingDTO;
import io.metersphere.agent.dto.AgentExecutionOperationsDTO;
import io.metersphere.agent.dto.AgentRunnerDTO;
import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.dto.AgentTestPlanDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentExecutionMapper {
    AgentExecutionTaskDTO selectTaskById(@Param("id") String id);

    AgentExecutionTaskDTO selectTaskByIdempotency(@Param("projectId") String projectId,
                                                  @Param("createUser") String createUser,
                                                  @Param("idempotencyKey") String idempotencyKey);

    List<AgentExecutionCaseDTO> selectCasesByTaskId(@Param("taskId") String taskId);

    List<AgentExecutionStepDTO> selectStepsByTaskId(@Param("taskId") String taskId);

    List<AgentExecutionHealingDTO> selectHealingByTaskId(@Param("taskId") String taskId);

    AgentRunnerDTO selectRunnerById(@Param("id") String id);

    List<AgentRunnerDTO> selectRunnersByOrganization(@Param("organizationId") String organizationId,
                                                      @Param("now") long now,
                                                      @Param("staleBefore") long staleBefore);

    AgentRunnerLeaseDTO selectLeaseById(@Param("id") String id);

    List<AgentRunnerLeaseDTO> selectLeasesByOrganization(@Param("organizationId") String organizationId,
                                                         @Param("status") String status,
                                                         @Param("limit") int limit);

    AgentExecutionArtifactDTO selectArtifactById(@Param("id") String id);

    AgentExecutionArtifactDTO selectArtifactByIdentity(@Param("taskId") String taskId,
                                                       @Param("sha256") String sha256,
                                                       @Param("purpose") String purpose,
                                                       @Param("stepId") String stepId);

    AgentExecutionArtifactDTO selectArtifactByPrepareKey(@Param("taskId") String taskId,
                                                         @Param("idempotencyKey") String idempotencyKey);

    List<AgentExecutionArtifactDTO> selectArtifactsByTaskId(@Param("taskId") String taskId);

    List<AgentExecutionArtifactDTO> selectExpiredArtifacts(@Param("now") long now,
                                                           @Param("limit") int limit);

    AgentExecutionOperationsDTO selectOperationsSummary(@Param("organizationId") String organizationId,
                                                         @Param("now") long now,
                                                         @Param("runnerStaleBefore") long runnerStaleBefore,
                                                         @Param("taskStuckBefore") long taskStuckBefore);

    List<AgentRunnerLeaseDTO> selectExpiredActiveLeases(@Param("now") long now,
                                                        @Param("limit") int limit);

    int countActiveRunnerLeases(@Param("runnerId") String runnerId,
                                @Param("now") long now);

    AgentExecutionTaskDTO selectQueuedTaskForRunner(@Param("organizationId") String organizationId,
                                                    @Param("runnerId") String runnerId);

    List<AgentExecutionTaskDTO> selectQueuedTasksForAgent(@Param("projectId") String projectId,
                                                          @Param("agentType") String agentType,
                                                          @Param("limit") int limit);

    long countTasks(@Param("projectId") String projectId,
                    @Param("keyword") String keyword,
                    @Param("likeKeyword") String likeKeyword,
                    @Param("status") String status,
                    @Param("verdict") String verdict,
                    @Param("taskOrigin") String taskOrigin,
                    @Param("executorChannel") String executorChannel,
                    @Param("executionMode") String executionMode);

    List<AgentExecutionTaskDTO> searchTasks(@Param("projectId") String projectId,
                                            @Param("keyword") String keyword,
                                            @Param("likeKeyword") String likeKeyword,
                                            @Param("status") String status,
                                            @Param("verdict") String verdict,
                                            @Param("taskOrigin") String taskOrigin,
                                            @Param("executorChannel") String executorChannel,
                                            @Param("executionMode") String executionMode,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    List<AgentExecutionCaseDTO> selectCasesByTaskIdAndStatuses(@Param("taskId") String taskId,
                                                               @Param("statuses") List<String> statuses);

    List<AgentExecutionEventDTO> selectEvents(@Param("taskId") String taskId,
                                              @Param("cursor") long cursor,
                                              @Param("limit") int limit);

    Long selectMaxEventSequence(@Param("taskId") String taskId);

    void insertTask(AgentExecutionTaskDTO task);

    void insertCase(AgentExecutionCaseDTO executionCase);

    void insertStep(AgentExecutionStepDTO executionStep);

    void insertHealing(AgentExecutionHealingDTO healing);

    void insertRunner(AgentRunnerDTO runner);

    void insertRunnerLease(AgentRunnerLeaseDTO lease);

    void insertExecutionAttempt(AgentExecutionAttemptDTO attempt);

    AgentExecutionStepResultDTO selectStepResultByRequest(@Param("executionId") String executionId,
                                                          @Param("stepId") String stepId,
                                                          @Param("requestId") String requestId);

    void insertStepResult(AgentExecutionStepResultDTO result);

    int bindExecutionLease(@Param("executionId") String executionId,
                           @Param("leaseId") String leaseId,
                           @Param("updateTime") long updateTime);

    int finishExecutionAttempt(@Param("executionId") String executionId,
                               @Param("status") String status,
                               @Param("errorCode") String errorCode,
                               @Param("errorMessage") String errorMessage,
                               @Param("finishTime") long finishTime);

    void insertArtifact(AgentExecutionArtifactDTO artifact);

    int storePreparedArtifact(@Param("id") String id,
                              @Param("leaseId") String leaseId,
                              @Param("fileId") String fileId,
                              @Param("fileName") String fileName,
                              @Param("storageFolder") String storageFolder,
                              @Param("contentType") String contentType,
                              @Param("sizeBytes") long sizeBytes,
                              @Param("sha256") String sha256);

    int commitPreparedArtifact(@Param("id") String id,
                               @Param("leaseId") String leaseId,
                               @Param("committedAt") long committedAt,
                               @Param("retentionUntil") long retentionUntil,
                               @Param("traceId") String traceId);

    int markArtifactDeleted(@Param("id") String id, @Param("status") String status);

    int completeHealing(@Param("taskId") String taskId,
                        @Param("stepId") String stepId,
                        @Param("attempt") int attempt,
                        @Param("result") String result,
                        @Param("afterArtifactId") String afterArtifactId,
                        @Param("eventTime") long eventTime);

    int updateRunnerHeartbeat(@Param("id") String id,
                              @Param("status") String status,
                              @Param("activeCount") int activeCount,
                              @Param("heartbeatTime") long heartbeatTime);

    int renewRunnerLease(@Param("id") String id,
                         @Param("runnerId") String runnerId,
                         @Param("version") int version,
                         @Param("expireTime") long expireTime,
                         @Param("heartbeatTime") long heartbeatTime);

    int assignRunnerLease(@Param("taskId") String taskId,
                          @Param("fromStatus") String fromStatus,
                          @Param("version") int version,
                          @Param("runnerId") String runnerId,
                          @Param("leaseId") String leaseId,
                          @Param("toStatus") String toStatus,
                          @Param("updateTime") long updateTime);

    int assignExecutionLease(@Param("taskId") String taskId,
                             @Param("fromStatus") String fromStatus,
                             @Param("version") int version,
                             @Param("runnerId") String runnerId,
                             @Param("leaseId") String leaseId,
                             @Param("executionId") String executionId,
                             @Param("toStatus") String toStatus,
                             @Param("updateTime") long updateTime);

    int updateTaskContext(@Param("id") String id,
                          @Param("contextSnapshot") String contextSnapshot,
                          @Param("contextSnapshotHash") String contextSnapshotHash,
                          @Param("executionContract") String executionContract,
                          @Param("executionContractHash") String executionContractHash,
                          @Param("updateTime") long updateTime);

    int updateLeaseEventSequence(@Param("id") String id,
                                 @Param("runnerId") String runnerId,
                                 @Param("version") int version,
                                 @Param("previousEventSequence") long previousEventSequence,
                                 @Param("lastEventSequence") long lastEventSequence,
                                 @Param("updateTime") long updateTime);

    int closeRunnerLease(@Param("id") String id,
                         @Param("runnerId") String runnerId,
                         @Param("version") int version,
                         @Param("status") String status,
                         @Param("updateTime") long updateTime);

    int recoverExpiredTaskLease(@Param("taskId") String taskId,
                                @Param("leaseId") String leaseId,
                                @Param("updateTime") long updateTime);

    int releaseTaskLease(@Param("taskId") String taskId,
                         @Param("leaseId") String leaseId,
                         @Param("reason") String reason,
                         @Param("updateTime") long updateTime);

    int finalizeHumanBlockedTask(@Param("taskId") String taskId,
                                 @Param("fromStatus") String fromStatus,
                                 @Param("version") int version,
                                 @Param("reason") String reason,
                                 @Param("updateUser") String updateUser,
                                 @Param("updateTime") long updateTime);

    int markStepStarted(@Param("taskId") String taskId, @Param("stepId") String stepId,
                        @Param("attempt") int attempt, @Param("updateTime") long updateTime);

    int markStepHealing(@Param("taskId") String taskId, @Param("stepId") String stepId,
                        @Param("updateTime") long updateTime);

    int markStepHealingCompleted(@Param("taskId") String taskId, @Param("stepId") String stepId,
                                 @Param("updateTime") long updateTime);

    int markStepCompleted(@Param("taskId") String taskId, @Param("stepId") String stepId,
                          @Param("status") String status, @Param("actualResult") String actualResult,
                          @Param("errorMessage") String errorMessage,
                          @Param("failureCategory") String failureCategory,
                          @Param("healed") boolean healed, @Param("updateTime") long updateTime);

    int markCaseStarted(@Param("taskId") String taskId, @Param("caseId") String caseId,
                        @Param("updateTime") long updateTime);

    int markCaseCompleted(@Param("taskId") String taskId, @Param("caseId") String caseId,
                          @Param("status") String status, @Param("result") String result,
                          @Param("errorMessage") String errorMessage, @Param("updateTime") long updateTime);

    int updateCaseWritebackStatus(@Param("taskId") String taskId, @Param("caseId") String caseId,
                                  @Param("writebackStatus") String writebackStatus,
                                  @Param("errorMessage") String errorMessage,
                                  @Param("updateTime") long updateTime);

    int finalizeExecutionTask(@Param("taskId") String taskId, @Param("status") String status,
                              @Param("verdict") String verdict, @Param("verdictReason") String verdictReason,
                              @Param("successCount") int successCount, @Param("failedCount") int failedCount,
                              @Param("blockedCount") int blockedCount, @Param("skippedCount") int skippedCount,
                              @Param("unexecutedCount") int unexecutedCount,
                              @Param("writebackStatus") String writebackStatus,
                              @Param("artifactStatus") String artifactStatus,
                              @Param("updateUser") String updateUser, @Param("updateTime") long updateTime);

    int countAvailableArtifacts(@Param("taskId") String taskId);
    int countAvailableArtifactsByExecutionCase(@Param("taskId") String taskId, @Param("executionCaseId") String executionCaseId);
    int countOutstandingDataCleanup(@Param("taskId") String taskId);
    int countTerminalSteps(@Param("taskId") String taskId);
    int countTerminalStepsWithArtifacts(@Param("taskId") String taskId);
    int sumHealingCount(@Param("taskId") String taskId);
    int sumRetryCount(@Param("taskId") String taskId);

    void insertEvent(AgentExecutionEventDTO event);

    int updateTaskStatus(@Param("id") String id,
                         @Param("status") String status,
                         @Param("updateUser") String updateUser,
                         @Param("updateTime") long updateTime);

    int blockTask(@Param("id") String id, @Param("blockedReason") String blockedReason,
                  @Param("blockedDetail") String blockedDetail, @Param("verdict") String verdict,
                  @Param("updateUser") String updateUser, @Param("updateTime") long updateTime);

    int transitionTaskStatus(@Param("id") String id,
                             @Param("fromStatus") String fromStatus,
                             @Param("version") int version,
                             @Param("toStatus") String toStatus,
                             @Param("updateUser") String updateUser,
                             @Param("updateTime") long updateTime);

    int confirmTask(@Param("id") String id,
                    @Param("fromStatus") String fromStatus,
                    @Param("version") int version,
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

    int retryFailedSteps(@Param("taskId") String taskId,
                         @Param("updateTime") long updateTime);

    int requeueTaskForRetry(@Param("id") String id, @Param("fromStatus") String fromStatus,
                            @Param("version") int version, @Param("updateUser") String updateUser,
                            @Param("successCount") int successCount, @Param("failedCount") int failedCount,
                            @Param("blockedCount") int blockedCount, @Param("skippedCount") int skippedCount,
                            @Param("unexecutedCount") int unexecutedCount,
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

    List<String> selectProjectCaseIds(@Param("projectId") String projectId);

    int countWritebackIdempotency(@Param("taskId") String taskId,
                                  @Param("caseId") String caseId,
                                  @Param("idempotencyKey") String idempotencyKey);

    void insertWritebackIdempotency(@Param("id") String id,
                                    @Param("taskId") String taskId,
                                    @Param("caseId") String caseId,
                                    @Param("idempotencyKey") String idempotencyKey,
                                    @Param("projectId") String projectId,
                                    @Param("lastExecResult") String lastExecResult,
                                    @Param("createUser") String createUser,
                                    @Param("createTime") long createTime);

    int countActiveRunnerSessions(@Param("userId") String userId,
                                  @Param("domain") String domain,
                                  @Param("now") long now);

    int countCredentialReferences(@Param("projectId") String projectId,
                                  @Param("environmentId") String environmentId,
                                  @Param("domain") String domain);

    int countEventsByType(@Param("taskId") String taskId, @Param("eventType") String eventType);

    int countEvidenceEvents(@Param("taskId") String taskId);
}
