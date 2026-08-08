package io.metersphere.functional.dto;

import lombok.Data;

@Data
public class AiCaseAvailableModelDTO {
    private String id;
    private String name;
    private String type;
    private String provider;
    private String baseName;
    private boolean personal;
    private boolean supportsStream;
    private boolean supportsTools;
    private boolean supportsVision;
    private Long contextWindow;
    private Long maxOutputTokens;
    private String connectionStatus;
    private String disabledReason;
}
