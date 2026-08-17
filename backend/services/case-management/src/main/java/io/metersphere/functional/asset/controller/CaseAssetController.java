package io.metersphere.functional.asset.controller;

import io.metersphere.functional.asset.dto.CaseAssetCatalogPageRequest;
import io.metersphere.functional.asset.dto.CaseAssetCatalogRequest;
import io.metersphere.functional.asset.dto.CaseAssetPageRequest;
import io.metersphere.functional.asset.dto.CaseAssetSaveRequest;
import io.metersphere.functional.asset.service.CaseAssetService;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.dto.FunctionalCasePageDTO;
import io.metersphere.functional.dto.response.FunctionalCaseImportResponse;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.functional.hub.dto.DefaultHubCaseImportRequest;
import io.metersphere.functional.hub.dto.DefaultHubJobResponse;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.utils.Pager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

@Tag(name = "测试资产-用例资产")
@RestController
@RequestMapping("/case-asset")
public class CaseAssetController {
    @Resource private CaseAssetService caseAssetService;

    @PostMapping("/catalog/page")
    @Operation(summary = "用例资产目录分页")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_READ)
    public Map<String, Object> pageCatalogs(@RequestBody CaseAssetCatalogPageRequest request) {
        return caseAssetService.pageCatalogs(request);
    }

    @PostMapping("/catalog")
    @Operation(summary = "新建用例项目（资产目录）")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_ADD)
    public Map<String, Object> createCatalog(@Validated @RequestBody CaseAssetCatalogRequest request) {
        return caseAssetService.createCatalog(request);
    }

    @PutMapping("/catalog")
    @Operation(summary = "修改用例资产目录")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_UPDATE)
    public Map<String, Object> updateCatalog(@Validated @RequestBody CaseAssetCatalogRequest request) {
        return caseAssetService.updateCatalog(request);
    }

    @DeleteMapping("/catalog/{catalogId}")
    @Operation(summary = "删除用例资产目录")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_DELETE)
    public void deleteCatalog(@PathVariable String catalogId) {
        caseAssetService.deleteCatalog(catalogId);
    }

    @PostMapping("/catalog/backfill")
    @Operation(summary = "为当前组织历史项目幂等补建用例资产目录")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_ADD)
    public Map<String, Object> backfillProjectCatalogs() {
        return caseAssetService.backfillProjectCatalogs();
    }

    @GetMapping("/catalog/backfill/{jobId}")
    @Operation(summary = "查询历史项目与用例同步任务")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_READ)
    public Map<String, Object> historySyncJob(@PathVariable String jobId) {
        return caseAssetService.getHistorySyncJob(jobId);
    }

    @GetMapping("/catalog/backfill/latest")
    @Operation(summary = "查询最近一次历史项目与用例同步任务")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_READ)
    public Map<String, Object> latestHistorySyncJob() {
        return caseAssetService.getLatestHistorySyncJob();
    }

    @PostMapping("/catalog/backfill/{jobId}/retry")
    @Operation(summary = "重试历史项目与用例同步失败项")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_ADD)
    public Map<String, Object> retryHistorySyncJob(@PathVariable String jobId) {
        return caseAssetService.retryHistorySyncJob(jobId);
    }

    @PostMapping("/case/page")
    @Operation(summary = "资产用例分页")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_READ)
    public Pager<List<FunctionalCasePageDTO>> pageCases(@Validated @RequestBody CaseAssetPageRequest request) {
        return caseAssetService.pageCases(request);
    }

    @PostMapping("/case/options")
    @Operation(summary = "按 ID 回显资产用例选项")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_READ)
    public List<Map<String, Object>> caseOptions(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) request.getOrDefault("ids", List.of());
        return caseAssetService.caseOptions(ids, (String) request.get("targetProjectId"), (String) request.get("scene"));
    }

    @PostMapping("/case")
    @Operation(summary = "新建资产用例")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_ADD)
    public FunctionalCase addCase(@Validated @RequestBody CaseAssetSaveRequest request) {
        return caseAssetService.addCase(request);
    }

    @PutMapping("/case")
    @Operation(summary = "更新资产用例")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_UPDATE)
    public FunctionalCase updateCase(@Validated @RequestBody CaseAssetSaveRequest request) {
        return caseAssetService.updateCase(request);
    }

    @GetMapping("/case/{caseId}")
    @Operation(summary = "资产用例详情")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_READ)
    public FunctionalCaseDetailDTO detail(@PathVariable String caseId, @RequestParam String catalogId) {
        return caseAssetService.detail(catalogId, caseId);
    }

    @GetMapping("/case/{caseId}/referenced-projects")
    @Operation(summary = "分页查询资产用例已引用项目")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_READ)
    public Map<String, Object> referencedProjects(@PathVariable String caseId, @RequestParam String catalogId,
                                                   @RequestParam(defaultValue = "1") int current,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        return caseAssetService.referencedProjects(catalogId, caseId, current, pageSize);
    }

    @DeleteMapping("/case/{caseId}")
    @Operation(summary = "软删除资产用例")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_DELETE)
    public void deleteCase(@PathVariable String caseId, @RequestParam String catalogId) {
        caseAssetService.deleteCase(catalogId, caseId);
    }

    @PostMapping("/case/{caseId}/attachment")
    @Operation(summary = "上传资产用例附件")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_UPDATE)
    public void uploadAttachment(@PathVariable String caseId, @RequestParam String catalogId,
                                 @RequestPart("file") MultipartFile file) {
        caseAssetService.uploadAttachment(catalogId, caseId, file);
    }

    @DeleteMapping("/case/{caseId}/attachment/{fileId}")
    @Operation(summary = "删除资产用例附件")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_UPDATE)
    public void deleteAttachment(@PathVariable String caseId, @PathVariable String fileId,
                                 @RequestParam String catalogId, @RequestParam(defaultValue = "true") Boolean local) {
        caseAssetService.deleteAttachment(catalogId, caseId, fileId, local);
    }

    @GetMapping("/case/{caseId}/attachment/{fileId}")
    @Operation(summary = "下载资产用例附件")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_READ)
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable String caseId, @PathVariable String fileId,
                                                     @RequestParam String catalogId,
                                                     @RequestParam(defaultValue = "true") Boolean local) throws Exception {
        return caseAssetService.downloadAttachment(catalogId, caseId, fileId, local);
    }

    @PostMapping("/case/import/excel/{catalogId}")
    @Operation(summary = "导入 Excel 资产用例")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_IMPORT)
    public Map<String, Object> importExcel(@PathVariable String catalogId,
                                                     @RequestPart("request") FunctionalCaseImportRequest request,
                                                     @RequestPart("file") MultipartFile file) {
        return caseAssetService.importExcel(catalogId, request, file);
    }

    @PostMapping("/case/import/xmind/{catalogId}")
    @Operation(summary = "导入 XMind 资产用例")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_IMPORT)
    public Map<String, Object> importXMind(@PathVariable String catalogId,
                                                     @RequestPart("request") FunctionalCaseImportRequest request,
                                                     @RequestPart("file") MultipartFile file) {
        return caseAssetService.importXMind(catalogId, request, file);
    }

    @GetMapping("/case/import/job/{jobId}")
    @Operation(summary = "查询资产文件导入任务")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_READ)
    public Map<String, Object> fileImportJob(@PathVariable String jobId) {
        return caseAssetService.fileImportJob(jobId);
    }

    @GetMapping("/case/import/job/latest")
    @Operation(summary = "查询当前资产目录最近一次文件导入任务")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_READ)
    public Map<String, Object> latestFileImportJob(@RequestParam String catalogId) {
        return caseAssetService.latestFileImportJob(catalogId);
    }

    @GetMapping("/case/import/job/{jobId}/errors/download")
    @Operation(summary = "下载资产用例文件导入错误明细")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_READ)
    public ResponseEntity<byte[]> downloadFileImportErrors(@PathVariable String jobId) {
        return caseAssetService.downloadFileImportErrors(jobId);
    }

    @PostMapping("/import/project")
    @Operation(summary = "从用例资产导入到业务项目")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_IMPORT)
    public DefaultHubJobResponse importToProject(@Validated @RequestBody DefaultHubCaseImportRequest request) {
        return caseAssetService.importToProject(request);
    }

    @GetMapping("/import/job/{jobId}/result")
    @Operation(summary = "查询资产导入后的项目用例 ID")
    @RequiresPermissions(PermissionConstants.CASE_ASSET_READ)
    public List<Map<String, Object>> importResult(@PathVariable String jobId) {
        return caseAssetService.importResult(jobId);
    }
}
