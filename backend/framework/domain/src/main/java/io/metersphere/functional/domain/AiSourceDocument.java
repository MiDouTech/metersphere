package io.metersphere.functional.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiSourceDocument implements Serializable {
    private String id;
    private String projectId;
    private String conversationId;
    private String fileId;
    private String originalName;
    private String mimeType;
    private Long fileSize;
    private String sha256;
    private Boolean duplicate;
    private String duplicateSourceDocumentId;
    private String parseStatus;
    private String parsedResultPath;
    private String parserType;
    private String summary;
    private String sectionIndex;
    private String errorMessage;
    private String createUser;
    private Long createTime;
    private Long updateTime;
    private Boolean deleted;

    private static final long serialVersionUID = 1L;
}
