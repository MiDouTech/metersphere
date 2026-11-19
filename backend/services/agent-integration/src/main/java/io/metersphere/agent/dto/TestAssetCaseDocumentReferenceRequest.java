package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record TestAssetCaseDocumentReferenceRequest(
        @NotBlank String projectId,
        @NotBlank String documentId,
        @NotBlank String documentVersionId) {
}
