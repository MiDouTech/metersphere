package io.metersphere.agent.controller;

import io.metersphere.agent.dto.TestAssetCatalogItemDTO;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.service.GlobalAssetCatalogService;
import io.metersphere.system.utils.Pager;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/test-assets/global", "/api/test-assets/global"})
public class GlobalAssetCatalogController {
  private final GlobalAssetCatalogService service;

  public GlobalAssetCatalogController(GlobalAssetCatalogService service) {
    this.service = service;
  }

  @GetMapping("/catalog")
  public Pager<List<TestAssetCatalogItemDTO>> catalog(
      @RequestParam(required = false) String projectId,
      @RequestParam String assetType,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long updatedAfter,
      @RequestParam(required = false) List<String> creationSources,
      @RequestParam(required = false) String categoryId,
      @RequestParam(defaultValue = "false") boolean includeDescendants,
      @RequestParam(defaultValue = "1") int current,
      @RequestParam(defaultValue = "20") int pageSize) {
    return service.catalog(
        projectId,
        assetType,
        keyword,
        status,
        updatedAfter,
        creationSources,
        categoryId,
        includeDescendants,
        current,
        pageSize);
  }

  @GetMapping("/versions")
  public Pager<List<TestAssetVersionDTO>> versions(
      @RequestParam(required = false) String projectId,
      @RequestParam(required = false) String assetType,
      @RequestParam(required = false) String assetId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) List<String> creationSources,
      @RequestParam(required = false) String categoryId,
      @RequestParam(defaultValue = "false") boolean includeDescendants,
      @RequestParam(defaultValue = "1") int current,
      @RequestParam(defaultValue = "20") int pageSize) {
    return service.versions(
        projectId,
        assetType,
        assetId,
        keyword,
        creationSources,
        categoryId,
        includeDescendants,
        current,
        pageSize);
  }
}
