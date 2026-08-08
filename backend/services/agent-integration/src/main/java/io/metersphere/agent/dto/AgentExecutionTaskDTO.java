package io.metersphere.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentExecutionTaskDTO {
    private String id;
    private String organizationId;
    private String projectId;
    private String testPlanId;
    private String source;
    private String selectionMode;
    private String prompt;
    private String resolvedFilter;
    private String caseSnapshotHash;
    private String policySnapshot;
    private String status;
    private String executionMode;
    private String agentType;
    private String agentGatewayId;
    private String runnerId;
    private String runnerLeaseId;
    private String providerId;
    private String environmentId;
    private String targetUrl;
    private String browserType;
    private String loginMode;
    private String idempotencyKey;
    private Boolean confirmRequired;
    private String confirmationReason;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer blockedCount;
    private Integer skippedCount;
    private Integer unexecutedCount;
    private String writebackStatus;
    private String artifactStatus;
    private String executedBy;
    private Long createTime;
    private Long updateTime;
    private String createUser;
    private String updateUser;
    private Integer version;
    private List<AgentExecutionCaseDTO> cases = new ArrayList<>();
}
