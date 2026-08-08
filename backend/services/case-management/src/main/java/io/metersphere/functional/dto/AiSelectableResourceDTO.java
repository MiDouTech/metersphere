package io.metersphere.functional.dto;

import lombok.Data;

@Data
public class AiSelectableResourceDTO {
    private String id;
    private String resourceType;
    private String provider;
    private String displayName;
    private boolean personal;
    private boolean online;
    private boolean experimental;
    private String connectionStatus;
    private String unavailableReason;
    private AiResourceCapabilities capabilities;
}
