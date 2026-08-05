package io.metersphere.functional.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class FunctionalCaseAiDraft implements Serializable {
    private String id;
    private String generationId;
    private String sourceDocumentId;
    private String projectId;
    private String moduleId;
    private String templateId;
    private String name;
    private String caseLevel;
    private String editType;
    private String prerequisite;
    private String steps;
    private String expectedResult;
    private String tags;
    private String customFields;
    private String validationMessage;
    private String fingerprint;
    private Boolean duplicate;
    private String validationStatus;
    private String draftStatus;
    private String formalCaseId;
    private Boolean deleted;
    private Integer version;
    private String createUser;
    private Long createTime;
    private Long updateTime;

    private static final long serialVersionUID = 1L;
}
