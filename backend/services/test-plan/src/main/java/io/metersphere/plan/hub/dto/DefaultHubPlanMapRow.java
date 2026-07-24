package io.metersphere.plan.hub.dto;

import lombok.Data;

@Data
public class DefaultHubPlanMapRow {
    private String id;
    private String bizProjectId;
    private String bizPlanId;
    private String hubPlanId;
    private String contentHash;
    private Long createTime;
    private Long updateTime;
}
