package io.metersphere.functional.dto;

import lombok.Data;

@Data
public class AiResourceCapabilities {
    private boolean stream;
    private boolean tools;
    private boolean files;
    private boolean cancel;
    private boolean vision;
    private Long contextWindow;
    private Long maxOutputTokens;
}
