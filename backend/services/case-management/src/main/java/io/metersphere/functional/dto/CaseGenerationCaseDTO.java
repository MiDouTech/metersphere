package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CaseGenerationCaseDTO {
    @Schema(description = "Case name")
    private String name;

    @Schema(description = "Case level")
    private String level;

    @Schema(description = "Module ID")
    private String moduleId;

    @Schema(description = "Template ID")
    private String templateId;

    @Schema(description = "Case edit type: STEP/TEXT")
    private String editType;

    @Schema(description = "Prerequisite")
    private String prerequisite;

    @Schema(description = "Text description when editType is TEXT")
    private String textDescription;

    @Schema(description = "Expected result")
    private String expectedResult;

    @Schema(description = "Step list")
    private List<FunctionalCaseStepDTO> steps = new ArrayList<>();

    @Schema(description = "Tags")
    private List<String> tags = new ArrayList<>();
}
