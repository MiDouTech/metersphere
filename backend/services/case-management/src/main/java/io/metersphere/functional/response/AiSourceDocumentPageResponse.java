package io.metersphere.functional.response;

import io.metersphere.functional.dto.AiSourceDocumentDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiSourceDocumentPageResponse {
    @Schema(description = "Total document count")
    private long total;

    @Schema(description = "Document records")
    private List<AiSourceDocumentDTO> records = new ArrayList<>();
}
