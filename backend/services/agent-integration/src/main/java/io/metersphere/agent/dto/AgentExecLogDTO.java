package io.metersphere.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class AgentExecLogDTO {
    private String id;
    private String caseId;
    private String testPlanId;
    private String testPlanCaseId;
    private String lastExecResult;
    private String executedBy;
    private String stepsSnapshot;
    private String content;
    private Long createTime;
    private String createUser;
    @Schema(description = "关联附件")
    private List<AgentAttachmentDTO> attachments;
}
