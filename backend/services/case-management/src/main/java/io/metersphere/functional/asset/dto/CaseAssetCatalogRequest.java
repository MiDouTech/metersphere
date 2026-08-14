package io.metersphere.functional.asset.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CaseAssetCatalogRequest {
    private String id;
    @NotBlank
    private String name;
}
