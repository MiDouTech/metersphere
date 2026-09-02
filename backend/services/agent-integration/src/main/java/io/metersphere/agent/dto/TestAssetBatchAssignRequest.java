package io.metersphere.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TestAssetBatchAssignRequest {
    @Valid
    @NotEmpty
    private List<Item> items;
    private String categoryId;

    @Data
    public static class Item {
        private String projectId;
        private String assetType;
        private String assetId;
    }
}
