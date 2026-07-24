package io.metersphere.functional.hub.dto;

import lombok.Data;

@Data
public class DefaultHubCaseMapRow {
    private String id;
    private String bizProjectId;
    private String bizCaseId;
    private String hubCaseId;
    private String contentHash;
    private Long createTime;
    private Long updateTime;
}
