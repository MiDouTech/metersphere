package io.metersphere.functional.hub.dto;

import lombok.Data;

@Data
public class DefaultHubJobResponse {
    private String jobId;
    private String status;
    private Integer progress;
    private Integer successCount;
    private Integer failCount;
    private String errorMessage;
}
