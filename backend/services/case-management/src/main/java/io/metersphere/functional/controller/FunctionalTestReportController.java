package io.metersphere.functional.controller;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.functional.dto.FunctionalTestReportDTO;
import io.metersphere.functional.request.FunctionalTestReportGenerateRequest;
import io.metersphere.functional.request.FunctionalTestReportPageRequest;
import io.metersphere.functional.request.FunctionalTestReportUpdateRequest;
import io.metersphere.functional.service.FunctionalTestReportService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用例管理-测试报告")
@RestController
@RequestMapping("/functional/test-report")
public class FunctionalTestReportController {

    @Resource
    private FunctionalTestReportService functionalTestReportService;

    @PostMapping("/page")
    @Operation(summary = "用例管理-测试报告-分页列表")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public Pager<List<FunctionalTestReportDTO>> page(@Validated @RequestBody FunctionalTestReportPageRequest request) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize(),
                StringUtils.isNotBlank(request.getSortString()) ? request.getSortString() : "create_time desc");
        return PageUtils.setPageInfo(page, functionalTestReportService.list(request));
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "用例管理-测试报告-详情")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ)
    public FunctionalTestReportDTO get(@NotBlank @PathVariable String id) {
        return functionalTestReportService.get(id);
    }

    @PostMapping("/generate")
    @Operation(summary = "用例管理-测试报告-一键生成草稿")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ_ADD)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public FunctionalTestReportDTO generate(@Validated @RequestBody FunctionalTestReportGenerateRequest request) {
        return functionalTestReportService.generate(request);
    }

    @PostMapping("/update")
    @Operation(summary = "用例管理-测试报告-更新名称/正文")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ_UPDATE)
    public FunctionalTestReportDTO update(@Validated @RequestBody FunctionalTestReportUpdateRequest request) {
        return functionalTestReportService.update(request);
    }

    @PostMapping("/refresh-stats/{id}")
    @Operation(summary = "用例管理-测试报告-刷新统计快照")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ_UPDATE)
    public FunctionalTestReportDTO refreshStats(@NotBlank @PathVariable String id) {
        return functionalTestReportService.refreshStats(id);
    }

    @GetMapping("/delete/{id}")
    @Operation(summary = "用例管理-测试报告-删除")
    @RequiresPermissions(PermissionConstants.FUNCTIONAL_CASE_READ_DELETE)
    public void delete(@NotBlank @PathVariable String id) {
        functionalTestReportService.delete(id);
    }
}
