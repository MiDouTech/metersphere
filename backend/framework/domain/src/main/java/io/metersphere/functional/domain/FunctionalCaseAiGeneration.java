package io.metersphere.functional.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class FunctionalCaseAiGeneration implements Serializable {
    private String id;
    private String projectId;
    private String conversationId;
    private String modelSourceId;
    private String prompt;
    private String status;
    private Long tokenUsage;
    private Long durationMs;
    private String errorMessage;
    private String createUser;
    private Long createTime;
    private Long updateTime;

    private static final long serialVersionUID = 1L;
}
