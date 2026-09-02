package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TestAssetCategoryAssignRequest {
    @NotBlank
    private String projectId;
    private String categoryId;
}
