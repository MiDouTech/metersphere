package io.metersphere.functional.response;

import io.metersphere.functional.dto.FunctionalCaseAiDraftDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FunctionalCaseAiDraftPageResponse {
    @Schema(description = "Total draft count")
    private long total;

    @Schema(description = "Draft rows")
    private List<FunctionalCaseAiDraftDTO> records = new ArrayList<>();
}
