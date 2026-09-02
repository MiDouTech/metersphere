package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TestAssetCategorySaveRequest {
    @NotBlank
    @Size(max = 100)
    private String name;
    private String parentId;
}
