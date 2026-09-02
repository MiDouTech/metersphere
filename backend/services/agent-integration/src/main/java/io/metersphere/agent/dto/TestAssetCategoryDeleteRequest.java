package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class TestAssetCategoryDeleteRequest {
    private String strategy;
    private String targetCategoryId;
}
