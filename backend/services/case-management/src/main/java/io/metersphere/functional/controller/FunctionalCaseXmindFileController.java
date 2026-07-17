package io.metersphere.functional.controller;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.functional.dto.FunctionalCaseXmindFileDTO;
import io.metersphere.functional.request.FunctionalCaseXmindFilePageRequest;
import io.metersphere.functional.request.FunctionalCaseXmindFileRenameRequest;
import io.metersphere.functional.request.FunctionalCaseXmindFileUploadRequest;
import io.metersphere.functional.service.FunctionalCaseXmindFileService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.security.CheckOwner;
import io.metersphere.system.utils.PageUtils;
import io.metersphere.system.utils.Pager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "用例管理-Xmind用例文件库")
@RestController
@RequestMapping("/functional/case/xmind-file")
public class FunctionalCaseXmindFileController {

    @Resource
    private FunctionalCaseXmindFileService functionalCaseXmindFileService;

    @PostMapping("/page")
    @Operation(summary = "Xmind文件库-分页列表")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public Pager<List<FunctionalCaseXmindFileDTO>> page(@Validated @RequestBody FunctionalCaseXmindFilePageRequest request) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize(),
                StringUtils.isNotBlank(request.getSortString()) ? request.getSortString() : "update_time desc");
        return PageUtils.setPageInfo(page, functionalCaseXmindFileService.list(request));
    }

    @PostMapping("/upload")
    @Operation(summary = "Xmind文件库-上传（仅存文件，不解析为功能用例）")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ_ADD)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public FunctionalCaseXmindFileDTO upload(@Validated @RequestPart("request") FunctionalCaseXmindFileUploadRequest request,
                                             @RequestPart("file") MultipartFile file) {
        return functionalCaseXmindFileService.upload(request, file);
    }

    @PostMapping("/rename")
    @Operation(summary = "Xmind文件库-重命名")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ_UPDATE)
    public FunctionalCaseXmindFileDTO rename(@Validated @RequestBody FunctionalCaseXmindFileRenameRequest request) {
        return functionalCaseXmindFileService.rename(request);
    }

    @GetMapping("/download/{id}")
    @Operation(summary = "Xmind文件库-下载")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ)
    public ResponseEntity<byte[]> download(@NotBlank @PathVariable String id) {
        return functionalCaseXmindFileService.download(id);
    }

    @GetMapping("/preview/{id}")
    @Operation(summary = "Xmind文件库-在线浏览(MinderJson)")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ)
    public Map<String, Object> preview(@NotBlank @PathVariable String id) {
        return functionalCaseXmindFileService.preview(id);
    }

    @GetMapping("/delete/{id}")
    @Operation(summary = "Xmind文件库-删除")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ_DELETE)
    public void delete(@NotBlank @PathVariable String id) {
        functionalCaseXmindFileService.delete(id);
    }
}
