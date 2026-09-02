package io.metersphere.agent.controller;

import io.metersphere.agent.dto.*;
import io.metersphere.agent.service.TestAssetCatalogService;
import io.metersphere.agent.service.TestAssetGovernanceService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.utils.Pager;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping({"/test-assets", "/api/test-assets"})
public class TestAssetController {
    @Resource
    private TestAssetCatalogService service;
    @Resource
    private TestAssetGovernanceService governanceService;

    @GetMapping("/categories/tree")
    public List<TestAssetCategoryDTO> categoryTree(@RequestParam(required = false) String keyword) {
        return governanceService.tree(keyword);
    }

    @PostMapping("/categories")
    @RequiresPermissions(PermissionConstants.TEST_ASSET_CATEGORY_MANAGE)
    public TestAssetCategoryDTO createCategory(@Valid @RequestBody TestAssetCategorySaveRequest request) {
        return governanceService.create(request);
    }

    @PutMapping("/categories/{id}")
    @RequiresPermissions(PermissionConstants.TEST_ASSET_CATEGORY_MANAGE)
    public TestAssetCategoryDTO updateCategory(@PathVariable String id,
                                                @Valid @RequestBody TestAssetCategorySaveRequest request) {
        return governanceService.update(id, request);
    }

    @PutMapping("/categories/reorder")
    @RequiresPermissions(PermissionConstants.TEST_ASSET_CATEGORY_MANAGE)
    public void reorderCategories(@Valid @RequestBody TestAssetCategoryReorderRequest request) {
        governanceService.reorder(request);
    }

    @DeleteMapping("/categories/{id}")
    @RequiresPermissions(PermissionConstants.TEST_ASSET_CATEGORY_MANAGE)
    public void deleteCategory(@PathVariable String id, @RequestBody TestAssetCategoryDeleteRequest request) {
        governanceService.delete(id, request);
    }

    @GetMapping("/{assetType}/{assetId}/metadata")
    public TestAssetMetadataDTO metadata(@PathVariable String assetType, @PathVariable String assetId,
                                         @RequestParam String projectId) {
        return governanceService.metadata(projectId, assetType, assetId);
    }

    @PutMapping("/{assetType}/{assetId}/category")
    @RequiresPermissions(PermissionConstants.TEST_ASSET_CATEGORY_ASSIGN)
    public TestAssetMetadataDTO assignCategory(@PathVariable String assetType, @PathVariable String assetId,
                                                @Valid @RequestBody TestAssetCategoryAssignRequest request) {
        return governanceService.assign(assetType, assetId, request);
    }

    @PostMapping("/category-assignments/batch")
    @RequiresPermissions(PermissionConstants.TEST_ASSET_CATEGORY_ASSIGN)
    public List<TestAssetBatchAssignResult> batchAssign(@Valid @RequestBody TestAssetBatchAssignRequest request) {
        return governanceService.batchAssign(request);
    }

    @PostMapping("/source-governance")
    @RequiresPermissions(PermissionConstants.TEST_ASSET_SOURCE_GOVERN)
    public TestAssetMetadataDTO governSource(@Valid @RequestBody TestAssetSourceGovernanceRequest request) {
        return governanceService.governSource(request);
    }

    @GetMapping("/catalog")
    @RequiresPermissions(value = {
            PermissionConstants.PROJECT_FILE_MANAGEMENT_READ,
            PermissionConstants.PROJECT_ENVIRONMENT_READ,
            PermissionConstants.PROJECT_API_SCENARIO_READ,
            PermissionConstants.PROJECT_API_DEFINITION_READ,
            PermissionConstants.AI_EXECUTION_READ,
            PermissionConstants.PROJECT_BUG_READ
    }, logical = Logical.OR)
    public Pager<List<TestAssetCatalogItemDTO>> catalog(@RequestParam String projectId,
                                                        @RequestParam String assetType,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) Long updatedAfter,
                                                        @RequestParam(required = false) List<String> creationSources,
                                                        @RequestParam(required = false) String categoryId,
                                                        @RequestParam(defaultValue = "false") boolean includeDescendants,
                                                        @RequestParam(required = false) Integer current,
                                                        @RequestParam(required = false) Integer pageSize) {
        return service.catalog(projectId, assetType, keyword, status, updatedAfter, creationSources,
                categoryId, includeDescendants, current, pageSize);
    }

    @GetMapping("/catalog/{assetType}/{assetId}")
    @RequiresPermissions(value = {
            PermissionConstants.PROJECT_FILE_MANAGEMENT_READ,
            PermissionConstants.PROJECT_ENVIRONMENT_READ,
            PermissionConstants.PROJECT_API_SCENARIO_READ,
            PermissionConstants.PROJECT_API_DEFINITION_READ,
            PermissionConstants.AI_EXECUTION_READ,
            PermissionConstants.PROJECT_BUG_READ
    }, logical = Logical.OR)
    public TestAssetCatalogItemDTO catalogDetail(@RequestParam String projectId,
                                                  @PathVariable String assetType,
                                                  @PathVariable String assetId) {
        return service.detail(projectId, assetType, assetId);
    }

    @PostMapping("/catalog/{assetType}/{assetId}/publish")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public TestAssetCatalogItemDTO publish(@RequestParam String projectId,@PathVariable String assetType,@PathVariable String assetId){
        return service.publishAsset(projectId,assetType,assetId);
    }

    @PostMapping("/versions/{versionId}/deprecate")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_RUN)
    public TestAssetVersionDTO deprecate(@RequestParam String projectId,@RequestParam String assetType,
                                         @RequestParam String assetId,@PathVariable String versionId){
        return service.deprecateVersion(projectId,versionId,assetType,assetId);
    }

    @GetMapping("/documents")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_AI_READ)
    public Pager<List<TestAssetDocumentDTO>> documents(@RequestParam String projectId,
                                                        @RequestParam(required = false) String parseStatus,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) List<String> creationSources,
                                                        @RequestParam(required = false) String categoryId,
                                                        @RequestParam(defaultValue = "false") boolean includeDescendants,
                                                        @RequestParam(required = false) Integer current,
                                                        @RequestParam(required = false) Integer pageSize) {
        return service.documents(projectId, parseStatus, keyword, creationSources, categoryId, includeDescendants, current, pageSize);
    }

    @GetMapping("/versions")
    @RequiresPermissions(value = {
            PermissionConstants.FUNCTIONAL_CASE_READ,
            PermissionConstants.FUNCTIONAL_CASE_AI_READ,
            PermissionConstants.PROJECT_FILE_MANAGEMENT_READ,
            PermissionConstants.PROJECT_ENVIRONMENT_READ,
            PermissionConstants.PROJECT_API_SCENARIO_READ,
            PermissionConstants.PROJECT_API_DEFINITION_READ,
            PermissionConstants.AI_EXECUTION_READ,
            PermissionConstants.PROJECT_BUG_READ
    }, logical = Logical.OR)
    public Pager<List<TestAssetVersionDTO>> versions(@RequestParam String projectId,
                                                      @RequestParam(required = false) String assetType,
                                                      @RequestParam(required = false) String assetId,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) List<String> creationSources,
                                                      @RequestParam(required = false) String categoryId,
                                                      @RequestParam(defaultValue = "false") boolean includeDescendants,
                                                      @RequestParam(required = false) Integer current,
                                                      @RequestParam(required = false) Integer pageSize) {
        return service.versions(projectId, assetType, assetId, keyword, creationSources, categoryId, includeDescendants, current, pageSize);
    }

    @GetMapping("/relations")
    @RequiresPermissions(value = {
            PermissionConstants.FUNCTIONAL_CASE_READ,
            PermissionConstants.FUNCTIONAL_CASE_AI_READ,
            PermissionConstants.PROJECT_FILE_MANAGEMENT_READ,
            PermissionConstants.PROJECT_ENVIRONMENT_READ,
            PermissionConstants.PROJECT_API_SCENARIO_READ,
            PermissionConstants.PROJECT_API_DEFINITION_READ,
            PermissionConstants.AI_EXECUTION_READ,
            PermissionConstants.PROJECT_BUG_READ
    }, logical = Logical.OR)
    public Pager<List<TestAssetRelationDTO>> relations(@RequestParam String projectId,
                                                        @RequestParam(required = false) String assetType,
                                                        @RequestParam(required = false) String assetId,
                                                        @RequestParam(required = false) String relationType,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) List<String> creationSources,
                                                        @RequestParam(required = false) String categoryId,
                                                        @RequestParam(defaultValue = "false") boolean includeDescendants,
                                                        @RequestParam(required = false) Integer current,
                                                        @RequestParam(required = false) Integer pageSize) {
        return service.relations(projectId, assetType, assetId, relationType, keyword, creationSources,
                categoryId, includeDescendants, current, pageSize);
    }
}
