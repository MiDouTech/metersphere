package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
    private String caseVersion;
    private String caseSnapshot;
    private String status;
    private String result;
    private Integer pos;
    private Integer retryCount;
    private Integer healCount;
    private Boolean healed;
    private String errorMessage;
    private String failureCategory;
    private String writebackStatus;
    private Long createTime;
    private Long updateTime;
    private Integer version;
    private List<AgentExecutionStepDTO> steps = new ArrayList<>();
}
