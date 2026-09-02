package io.metersphere.agent.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TestAssetCategoryReorderRequest {
    @NotEmpty
    private List<String> ids;
}
