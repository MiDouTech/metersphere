package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AgentExecutionCaseDTO {
    @Schema(description = "执行任务用例快照 ID")
    private String id;
    private String taskId;
    private String projectId;
    private String caseId;
    private Long caseNum;
    private String caseName;
    private String testPlanId;
    private String testPlanCaseId;
    private String status;
    private String result;
    private Integer pos;
    private Integer retryCount;
    private String errorMessage;
    private Long createTime;
    private Long updateTime;
}
