package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentBugDTO {
    private String id;
    private Integer num;
    private String title;
    private String projectId;
    private String status;
    private String caseId;
}
