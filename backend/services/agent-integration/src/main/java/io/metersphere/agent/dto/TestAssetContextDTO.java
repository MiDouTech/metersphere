package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class TestAssetContextDTO {
    private String assetType;
    private String assetId;
    private String assetName;
    private String versionId;
    private Integer versionNo;
    private String contentHash;
    private String contentSnapshot;
}
