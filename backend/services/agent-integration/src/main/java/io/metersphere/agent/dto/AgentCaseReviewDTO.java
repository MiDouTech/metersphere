package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class AgentCaseReviewDTO {
    private String id;
    private String name;
    private String projectId;
    private String status;
    private String reviewPassRule;
    private Integer associatedCaseCount;
}
