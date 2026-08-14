package io.metersphere.functional.hub.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DefaultHubJobResponse {
    private String jobId;
    private String status;
    private Integer progress;
    private Integer successCount;
    private Integer failCount;
    private String errorMessage;
    private List<Map<String, Object>> items;
}
