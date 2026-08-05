package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CaseGenerationSourceRefDTO {
    @Schema(description = "Source document ID")
    private String documentId;

    @Schema(description = "Section title or index")
    private String section;

    @Schema(description = "Excerpt from the source")
    private String excerpt;
}
