package io.metersphere.system.edit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ResourceEditMetaResponse {
    private String resourceType;
    private String resourceId;
    @Schema(description = "可 Undo 步数")
    private int undoAvailable;
    @Schema(description = "可 Redo 步数")
    private int redoAvailable;
    private Long activeSeq;
}
