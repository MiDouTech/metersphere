package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TestAssetSourceGovernanceRequest {
    @NotBlank private String projectId;
    @NotBlank private String assetType;
    @NotBlank private String assetId;
    @NotBlank private String creationSource;
    @NotBlank private String evidence;
    private String sourceReferenceType;
    private String sourceReferenceId;
}
