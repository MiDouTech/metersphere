package io.metersphere.project.controller;

import io.metersphere.project.domain.Project;
import io.metersphere.project.dto.ProjectRequest;
import io.metersphere.project.request.ProjectSwitchRequest;
import io.metersphere.project.request.ProjectPageRequest;
import io.metersphere.project.service.ProjectLogService;
import io.metersphere.project.service.ProjectService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.ProjectDTO;
import io.metersphere.system.dto.sdk.OptionDTO;
import io.metersphere.system.dto.user.UserDTO;
import io.metersphere.system.dto.user.UserExtendDTO;
import io.metersphere.system.log.annotation.Log;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.security.CheckOwner;
import io.metersphere.system.utils.PageUtils;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.validation.groups.Updated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;

@RestController
@Tag(name = "项目管理")
@RequestMapping("/project")
public class ProjectController {
    @Resource
    private ProjectService projectService;

    @GetMapping("/get/{id}")
    @Operation(summary = "项目管理-基本信息")
    @RequiresPermissions(PermissionConstants.PROJECT_BASE_INFO_READ)
    @CheckOwner(resourceId = "#id", resourceType = "project")
    public ProjectDTO getProject(@PathVariable String id) {
        return projectService.getProjectById(id);
    }

    @GetMapping("/list/options/{organizationId}")
    @Operation(summary = "根据组织ID获取所有有权限的项目")
    @CheckOwner(resourceId = "#organizationId", resourceType = "organization")
    public List<Project> getUserProject(@PathVariable String organizationId) {
        return projectService.getUserProject(organizationId, SessionUtils.getUserId());
    }

    @PostMapping("/page")
    @Operation(summary = "项目管理-当前用户可访问项目分页列表")
    public Pager<List<ProjectDTO>> pageUserProject(@Validated @RequestBody ProjectPageRequest request) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        return PageUtils.setPageInfo(page, projectService.pageUserProject(request, SessionUtils.getUserId()));
    }

    @PostMapping("/case-asset/page")
    @Operation(summary = "测试资产-具备用例读取权限的可访问项目分页列表")
    public Pager<List<ProjectDTO>> pageCaseAssetProject(@Validated @RequestBody ProjectPageRequest request) {
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize());
        return PageUtils.setPageInfo(page, projectService.pageCaseAssetProject(request, SessionUtils.getUserId()));
    }

    @GetMapping("/list/options/{organizationId}/{module}")
    @Operation(summary = "根据组织ID获取所有开启某个模块的所有有权限的项目")
    @CheckOwner(resourceId = "#organizationId", resourceType = "organization")
    public List<Project> getUserProjectWidthModule(@PathVariable String organizationId, @PathVariable String module) {
        return projectService.getUserProjectWidthModule(organizationId, module, SessionUtils.getUserId());
    }

    @PostMapping("/switch")
    @Operation(summary = "切换项目")
    // 切换目标项目权限由 CheckOwner 校验；勿用当前 PROJECT 请求头上的 PROJECT_BASE_INFO:READ，
    // 否则从系统设置新建项目后进入新项目会因「当前上下文无项目权限」失败。
    public UserDTO switchProject(@RequestBody ProjectSwitchRequest request) {
        if (!projectService.canAccessProject(request.getProjectId(), SessionUtils.getUserId())) {
            throw new io.metersphere.sdk.exception.MSException("无权进入该项目");
        }
        return projectService.switchProject(request, SessionUtils.getUserId());
    }

    @PostMapping("/update")
    @Operation(summary = "项目管理-更新项目")
    @RequiresPermissions(PermissionConstants.PROJECT_BASE_INFO_READ_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.updateLog(#request)", msClass = ProjectLogService.class)
    @CheckOwner(resourceId = "#request.getId()", resourceType = "project")
    public ProjectDTO updateProject(@RequestBody @Validated({Updated.class}) ProjectRequest request) {
        return projectService.update(request, SessionUtils.getUserId());
    }

    @GetMapping("/{projectId}/case-asset-catalog")
    @Operation(summary = "查询项目关联的用例资产目录")
    @RequiresPermissions(PermissionConstants.PROJECT_BASE_INFO_READ)
    @CheckOwner(resourceId = "#projectId", resourceType = "project")
    public Map<String, Object> relatedCaseAssetCatalog(@PathVariable String projectId) {
        return projectService.getRelatedCaseAssetCatalog(projectId);
    }

    @GetMapping("/pool-options/{type}/{projectId}")
    @Operation(summary = "项目管理-获取项目下的资源池")
    @RequiresPermissions(PermissionConstants.PROJECT_BASE_INFO_READ)
    @CheckOwner(resourceId = "#projectId", resourceType = "project")
    public List<OptionDTO> getPoolOptions(@PathVariable String type, @PathVariable String projectId) {
        return projectService.getPoolOptions(projectId);
    }

    @GetMapping("/has-permission/{id}")
    @Operation(summary = "项目管理-获取当前用户是否有当前项目的权限")
    @CheckOwner(resourceId = "#id", resourceType = "project")
    public boolean hasPermission(@PathVariable String id) {
        return projectService.hasPermission(id, SessionUtils.getUserId());
    }

    @GetMapping("/get-member/option/{projectId}")
    @Operation(summary = "项目管理-获取成员下拉选项")
    @RequiresPermissions(PermissionConstants.PROJECT_BASE_INFO_READ)
    @CheckOwner(resourceId = "#projectId", resourceType = "project")
    public List<UserExtendDTO> getMemberOption(@PathVariable String projectId,
                                               @Schema(description = "查询关键字，根据邮箱和用户名查询")
                                               @RequestParam(value = "keyword", required = false) String keyword) {
        return projectService.getMemberOption(projectId, keyword);
    }

}
