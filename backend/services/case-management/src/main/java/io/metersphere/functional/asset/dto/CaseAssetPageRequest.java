package io.metersphere.functional.asset.dto;

import io.metersphere.functional.request.FunctionalCasePageRequest;
import lombok.Data;

@Data
public class CaseAssetPageRequest extends FunctionalCasePageRequest {
    private String catalogId;
    private String targetProjectId;
    private String scene;
}
