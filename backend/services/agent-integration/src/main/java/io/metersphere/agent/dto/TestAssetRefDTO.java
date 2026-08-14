package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TestAssetRefDTO {
    @NotBlank
    private String assetType;
    @NotBlank
    private String assetId;
    private String versionId;
}
