package io.metersphere.plan.controller;

import io.metersphere.plan.constants.TestPlanResourceConfig;
import io.metersphere.plan.dto.request.TestPlanDocumentSaveRequest;
import io.metersphere.plan.dto.response.TestPlanDocumentResponse;
import io.metersphere.plan.service.TestPlanDocumentService;
import io.metersphere.plan.service.TestPlanManagementService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.security.CheckOwner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/test-plan")
@Tag(name = "测试计划-文档")
public class TestPlanDocumentController {

    @Resource
    private TestPlanDocumentService testPlanDocumentService;
    @Resource
    private TestPlanManagementService testPlanManagementService;

    @GetMapping("/{id}/document")
    @Operation(summary = "测试计划-获取文档")
    @RequiresPermissions(PermissionConstants.TEST_PLAN_READ)
    @CheckOwner(resourceId = "#id", resourceType = "test_plan")
    public TestPlanDocumentResponse getDocument(@NotBlank @PathVariable String id) {
        testPlanManagementService.checkModuleIsOpen(id, TestPlanResourceConfig.CHECK_TYPE_TEST_PLAN,
                Collections.singletonList(TestPlanResourceConfig.CONFIG_TEST_PLAN));
        return testPlanDocumentService.getDocument(id);
    }

    @GetMapping("/{id}/document/export")
    @Operation(summary = "测试计划-导出文档(HTML)")
    @RequiresPermissions(PermissionConstants.TEST_PLAN_READ)
    @CheckOwner(resourceId = "#id", resourceType = "test_plan")
    public ResponseEntity<byte[]> exportDocument(@NotBlank @PathVariable String id) {
        testPlanManagementService.checkModuleIsOpen(id, TestPlanResourceConfig.CHECK_TYPE_TEST_PLAN,
                Collections.singletonList(TestPlanResourceConfig.CONFIG_TEST_PLAN));
        return testPlanDocumentService.exportDocumentHtml(id);
    }

    @PostMapping("/{id}/document")
    @Operation(summary = "测试计划-保存文档")
    @RequiresPermissions(PermissionConstants.TEST_PLAN_READ_UPDATE)
    @CheckOwner(resourceId = "#id", resourceType = "test_plan")
    public TestPlanDocumentResponse saveDocument(@NotBlank @PathVariable String id,
                                                 @Validated @RequestBody TestPlanDocumentSaveRequest request) {
        testPlanManagementService.checkModuleIsOpen(id, TestPlanResourceConfig.CHECK_TYPE_TEST_PLAN,
                Collections.singletonList(TestPlanResourceConfig.CONFIG_TEST_PLAN));
        return testPlanDocumentService.saveDocument(id, request);
    }
}
