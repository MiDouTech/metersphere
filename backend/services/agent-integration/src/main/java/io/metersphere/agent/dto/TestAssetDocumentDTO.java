package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class TestAssetDocumentDTO {
    private String id;
    private String projectId;
    private String originalName;
    private String mimeType;
    private Long fileSize;
    private String sha256;
    private Boolean duplicate;
    private String parseStatus;
    private String parserType;
    private String summary;
    private String errorMessage;
    private String createUser;
    private Long createTime;
    private Long updateTime;
    private String assetVersionId;
    private Integer assetVersionNo;
}
