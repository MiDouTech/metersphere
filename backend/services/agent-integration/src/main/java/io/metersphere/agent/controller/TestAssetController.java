package io.metersphere.agent.controller;

import io.metersphere.agent.dto.TestAssetDocumentDTO;
import io.metersphere.agent.dto.TestAssetRelationDTO;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.service.TestAssetCatalogService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.utils.Pager;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/test-assets", "/api/test-assets"})
public class TestAssetController {
    @Resource
    private TestAssetCatalogService service;

    @GetMapping("/documents")
    @RequiresPermissions(value = {
            PermissionConstants.FUNCTIONAL_CASE_READ,
            PermissionConstants.FUNCTIONAL_CASE_AI_READ
    }, logical = Logical.OR)
    public Pager<List<TestAssetDocumentDTO>> documents(@RequestParam String projectId,
                                                        @RequestParam(required = false) String parseStatus,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Integer current,
                                                        @RequestParam(required = false) Integer pageSize) {
        return service.documents(projectId, parseStatus, keyword, current, pageSize);
    }

    @GetMapping("/versions")
    @RequiresPermissions(value = {
            PermissionConstants.FUNCTIONAL_CASE_READ,
            PermissionConstants.FUNCTIONAL_CASE_AI_READ
    }, logical = Logical.OR)
    public Pager<List<TestAssetVersionDTO>> versions(@RequestParam String projectId,
                                                      @RequestParam(required = false) String assetType,
                                                      @RequestParam(required = false) String assetId,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Integer current,
                                                      @RequestParam(required = false) Integer pageSize) {
        return service.versions(projectId, assetType, assetId, keyword, current, pageSize);
    }

    @GetMapping("/relations")
    @RequiresPermissions(value = {
            PermissionConstants.FUNCTIONAL_CASE_READ,
            PermissionConstants.FUNCTIONAL_CASE_AI_READ
    }, logical = Logical.OR)
    public Pager<List<TestAssetRelationDTO>> relations(@RequestParam String projectId,
                                                        @RequestParam(required = false) String assetType,
                                                        @RequestParam(required = false) String assetId,
                                                        @RequestParam(required = false) String relationType,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Integer current,
                                                        @RequestParam(required = false) Integer pageSize) {
        return service.relations(projectId, assetType, assetId, relationType, keyword, current, pageSize);
    }
}
