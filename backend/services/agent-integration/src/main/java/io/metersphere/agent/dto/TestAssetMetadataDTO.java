package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class TestAssetMetadataDTO {
    private String assetType;
    private String assetId;
    private String creationSource;
    private String categoryId;
    private String categoryName;
    private String categoryPath;
    private String sourceReferenceType;
    private String sourceReferenceId;
    private String createdByActorType;
    private String createdByActorId;
    private Long createTime;
    private String aiGenerationId;
    private String aiProvider;
    private String aiModelId;
    private String aiModelName;
    private String promptTemplateVersion;
    private String sourceDocumentId;
    private Long generationTime;
    private String generationInitiator;
    private String reviewStatus;
    private String reviewedBy;
    private Long reviewedAt;
    private Long publishedAt;
}
