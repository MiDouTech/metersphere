package io.metersphere.functional.dto;

import lombok.Data;

@Data
public class AiCaseExecutionEventDTO {
    private String id;
    private String requestId;
    private Long sequence;
    private String eventType;
    private String payload;
    private Long createTime;
    private Long timestamp;
}
