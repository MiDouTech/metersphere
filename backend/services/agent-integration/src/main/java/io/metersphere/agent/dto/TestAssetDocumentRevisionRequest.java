package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestAssetDocumentRevisionRequest(
        @NotBlank String projectId,
        @NotBlank String expectedVersionId,
        @NotBlank @Size(max = 200_000) String markdown) {
}
