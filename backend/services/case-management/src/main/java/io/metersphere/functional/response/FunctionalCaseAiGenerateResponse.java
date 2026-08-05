package io.metersphere.functional.response;

import io.metersphere.functional.dto.FunctionalCaseAiDraftDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FunctionalCaseAiGenerateResponse {
    @Schema(description = "Generation task ID")
    private String generationId;

    @Schema(description = "Created draft count")
    private int createdCount;

    @Schema(description = "Generation warnings")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "Created drafts")
    private List<FunctionalCaseAiDraftDTO> drafts = new ArrayList<>();
}
