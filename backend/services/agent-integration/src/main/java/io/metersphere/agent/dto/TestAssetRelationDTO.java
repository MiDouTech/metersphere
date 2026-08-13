package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class TestAssetRelationDTO {
    private String id;
    private String projectId;
    private String relationType;
    private String sourceAssetType;
    private String sourceAssetId;
    private String sourceAssetName;
    private String sourceVersionId;
    private Integer sourceVersionNo;
    private String targetAssetType;
    private String targetAssetId;
    private String targetAssetName;
    private String targetVersionId;
    private Integer targetVersionNo;
    private String metadata;
    private String createdBy;
    private Long createdAt;
}
