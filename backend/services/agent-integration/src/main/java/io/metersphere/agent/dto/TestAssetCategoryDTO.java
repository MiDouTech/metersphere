package io.metersphere.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TestAssetCategoryDTO {
    private String id;
    private String parentId;
    private String name;
    private String path;
    private Integer level;
    private Long sort;
    private Long assetCount;
    private List<TestAssetCategoryDTO> children = new ArrayList<>();
}
