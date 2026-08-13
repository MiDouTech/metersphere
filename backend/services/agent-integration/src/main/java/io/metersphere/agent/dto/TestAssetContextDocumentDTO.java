package io.metersphere.agent.dto;

import lombok.Data;

@Data
public class TestAssetContextDocumentDTO {
    private String versionId;
    private String documentId;
    private String documentName;
    private Integer versionNo;
    private String sourceVersion;
    private String contentHash;
    private String contentSnapshot;
}
