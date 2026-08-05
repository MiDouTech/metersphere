package io.metersphere.functional.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FunctionalCaseAiBatchSaveResponse {
    @Schema(description = "Success count")
    private int successCount;

    @Schema(description = "Failure count")
    private int failureCount;

    @Schema(description = "Item save results")
    private List<ItemResult> results = new ArrayList<>();

    @Data
    public static class ItemResult {
        @Schema(description = "Draft ID")
        private String draftId;

        @Schema(description = "Case name")
        private String name;

        @Schema(description = "Formal case ID")
        private String formalCaseId;

        @Schema(description = "Whether save succeeded")
        private boolean success;

        @Schema(description = "Failure message")
        private String message;
    }
}
