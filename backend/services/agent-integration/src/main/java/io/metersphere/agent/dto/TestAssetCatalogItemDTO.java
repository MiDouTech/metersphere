package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class TestAssetCatalogItemDTO {
    private String id;
    private String projectId;
    private String assetType;
    private String name;
    private String category;
    private String status;
    private String summary;
    private String owner;
    private Long updateTime;
    private String sourceVersion;
    private String relatedId;
    private String assetVersionId;
    private Integer assetVersionNo;
    private String contentHash;
    private String creationSource;
    private String categoryId;
    private String categoryName;
    private String categoryPath;
    private String sourceReferenceType;
    private String sourceReferenceId;
}
