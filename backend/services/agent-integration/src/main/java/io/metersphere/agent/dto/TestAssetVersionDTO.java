package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class TestAssetVersionDTO {
    private String id;
    private String projectId;
    private String assetType;
    private String assetId;
    private String assetName;
    private Integer versionNo;
    private String sourceVersion;
    private String contentHash;
    private String contentSnapshot;
    private String status;
    private String createdBy;
    private Long createdAt;
    private String publishedBy;
    private Long publishedAt;
    private String creationSource;
    private String categoryId;
    private String categoryName;
    private String categoryPath;
}
