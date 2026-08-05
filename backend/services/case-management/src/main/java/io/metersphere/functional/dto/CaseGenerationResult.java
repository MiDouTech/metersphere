package io.metersphere.functional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CaseGenerationResult {
    @Schema(description = "Generated functional cases")
    private List<CaseGenerationCaseDTO> cases = new ArrayList<>();

    @Schema(description = "Generation warnings")
    private List<String> warnings = new ArrayList<>();
}
