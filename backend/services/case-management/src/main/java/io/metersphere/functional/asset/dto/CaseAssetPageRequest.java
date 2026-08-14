package io.metersphere.functional.asset.dto;

import io.metersphere.functional.request.FunctionalCasePageRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CaseAssetPageRequest extends FunctionalCasePageRequest {
    @NotBlank
    private String catalogId;
    private String targetProjectId;
    private String scene;
}
