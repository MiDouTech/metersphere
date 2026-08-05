package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AiSourceDocumentDTO {
    @Schema(description = "Source document ID")
    private String id;
    @Schema(description = "Project ID")
    private String projectId;
    @Schema(description = "AI conversation ID")
    private String conversationId;
    @Schema(description = "File service ID")
    private String fileId;
    @Schema(description = "Original file name")
    private String originalName;
    @Schema(description = "MIME type")
    private String mimeType;
    @Schema(description = "File size")
    private Long fileSize;
    @Schema(description = "SHA-256 digest")
    private String sha256;
    @Schema(description = "Duplicate file flag")
    private Boolean duplicate;
    @Schema(description = "Duplicate source document ID")
    private String duplicateSourceDocumentId;
    @Schema(description = "Parse status")
    private String parseStatus;
    @Schema(description = "Parsed result object path")
    private String parsedResultPath;
    @Schema(description = "Parser type")
    private String parserType;
    @Schema(description = "Parsed summary")
    private String summary;
    @Schema(description = "Section index JSON")
    private String sectionIndex;
    @Schema(description = "Error message")
    private String errorMessage;
    @Schema(description = "Creator")
    private String createUser;
    @Schema(description = "Created at")
    private Long createTime;
    @Schema(description = "Updated at")
    private Long updateTime;
    @Schema(description = "Deleted flag")
    private Boolean deleted;
}
