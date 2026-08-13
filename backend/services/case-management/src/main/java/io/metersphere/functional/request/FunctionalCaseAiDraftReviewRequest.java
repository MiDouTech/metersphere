package io.metersphere.functional.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class FunctionalCaseAiDraftReviewRequest {
    @NotBlank
    private String projectId;
    @NotEmpty
    private List<String> draftIds;
    @NotBlank
    private String action;
    private String comment;
}
