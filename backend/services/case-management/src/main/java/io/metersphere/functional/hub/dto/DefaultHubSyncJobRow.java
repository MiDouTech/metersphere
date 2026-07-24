package io.metersphere.functional.hub.dto;

import lombok.Data;

@Data
public class DefaultHubSyncJobRow {
    private String id;
    private String jobType;
    private String scopeProjectId;
    private String status;
    private Integer progress;
    private Integer successCount;
    private Integer failCount;
    private String errorMessage;
    private String createUser;
    private Long createTime;
    private Long updateTime;
    private Long finishTime;
}
