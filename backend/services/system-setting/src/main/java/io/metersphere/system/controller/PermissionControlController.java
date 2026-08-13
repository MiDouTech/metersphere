package io.metersphere.system.controller;

import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.domain.RoleAssignmentRule;
import io.metersphere.system.domain.StatusFlowRolePermission;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.domain.WorkflowDefinition;
import io.metersphere.system.domain.WorkflowRole;
import io.metersphere.system.dto.permission.PermissionResourceDTO;
import io.metersphere.system.dto.permission.control.PermissionControlFlowMatrixDTO;
import io.metersphere.system.dto.permission.control.RoleAssignmentRuleRequest;
import io.metersphere.system.dto.permission.control.RoleEnableRequest;
import io.metersphere.system.dto.permission.control.RoleMemberUpdateRequest;
import io.metersphere.system.dto.permission.control.WorkflowRolePermissionRequest;
import io.metersphere.system.dto.request.GlobalUserRoleRelationQueryRequest;
import io.metersphere.system.dto.sdk.request.PermissionSettingUpdateRequest;
import io.metersphere.system.dto.sdk.request.UserRoleUpdateRequest;
import io.metersphere.system.dto.user.UserRoleRelationUserDTO;
import io.metersphere.system.service.PermissionControlService;
import io.metersphere.validation.groups.Created;
import io.metersphere.validation.groups.Updated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "系统设置-系统-权限控制")
@RestController
@RequestMapping("/permission-control")
public class PermissionControlController {

    @Resource
    private PermissionControlService permissionControlService;

    @GetMapping("/role/list")
    @Operation(summary = "权限控制-角色设置-角色列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<UserRole> listRoles() {
        return permissionControlService.listRoles();
    }

    @PostMapping("/role/add")
    @Operation(summary = "权限控制-角色设置-新增角色")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_ADD)
    public UserRole addRole(@Validated({Created.class}) @RequestBody UserRoleUpdateRequest request) {
        return permissionControlService.addRole(request);
    }

    @PostMapping("/role/update")
    @Operation(summary = "权限控制-角色设置-修改角色")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    public UserRole updateRole(@Validated({Updated.class}) @RequestBody UserRoleUpdateRequest request) {
        return permissionControlService.updateRole(request);
    }

    @PostMapping("/role/enable")
    @Operation(summary = "权限控制-角色设置-启用禁用角色")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    public UserRole enableRole(@Validated @RequestBody RoleEnableRequest request) {
        return permissionControlService.enableRole(request.getRoleId(), request.getEnabled());
    }

    @PostMapping("/role/delete")
    @Operation(summary = "权限控制-角色设置-删除角色")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_DELETE)
    public void deleteRole(@RequestParam String roleId) {
        permissionControlService.deleteRole(roleId);
    }

    @PostMapping("/role/member/list")
    @Operation(summary = "权限控制-角色设置-成员列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<UserRoleRelationUserDTO> listRoleMembers(@Validated @RequestBody GlobalUserRoleRelationQueryRequest request) {
        return permissionControlService.listRoleMembers(request);
    }

    @PostMapping("/role/member/add")
    @Operation(summary = "权限控制-角色设置-添加成员")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    public void addRoleMembers(@Validated @RequestBody RoleMemberUpdateRequest request) {
        permissionControlService.addRoleMembers(request);
    }

    @PostMapping("/role/member/remove")
    @Operation(summary = "权限控制-角色设置-移除成员")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    public void removeRoleMembers(@Validated @RequestBody RoleMemberUpdateRequest request) {
        permissionControlService.removeRoleMembers(request);
    }

    @PostMapping("/role/member/assign-by-position")
    @Operation(summary = "权限控制-角色设置-按组织岗位分配成员")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    public RoleAssignmentRule assignByPosition(@Validated @RequestBody RoleAssignmentRuleRequest request) {
        return permissionControlService.assignByPosition(request);
    }

    @GetMapping("/role/member/assignment-rule/{roleId}")
    @Operation(summary = "权限控制-角色设置-岗位分配规则")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<RoleAssignmentRule> listAssignmentRules(@PathVariable String roleId) {
        return permissionControlService.listAssignmentRules(roleId);
    }

    @GetMapping("/resource/tree")
    @Operation(summary = "权限控制-角色设置-资源树")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<PermissionResourceDTO> resourceTree(@RequestParam String scopeType) {
        return permissionControlService.getResourceTree(scopeType);
    }

    @PostMapping("/role/permission/save")
    @Operation(summary = "权限控制-角色设置-保存角色权限")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    public void saveRolePermission(@Validated @RequestBody PermissionSettingUpdateRequest request) {
        permissionControlService.saveRolePermission(request);
    }

    @GetMapping("/flow/list")
    @Operation(summary = "权限控制-流程控制-流程列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<WorkflowDefinition> listFlows(@RequestParam(defaultValue = "BUG") String scene,
                                              @RequestParam(defaultValue = "SYSTEM") String scopeType,
                                              @RequestParam(defaultValue = "system") String scopeId) {
        return permissionControlService.listFlows(scene, scopeType, scopeId);
    }

    @PostMapping("/flow/add")
    @Operation(summary = "权限控制-流程控制-新建流程")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_ADD)
    public WorkflowDefinition addFlow(@RequestBody WorkflowDefinition request) {
        return permissionControlService.addFlow(request);
    }

    @PostMapping("/flow/update")
    @Operation(summary = "权限控制-流程控制-修改流程")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    public WorkflowDefinition updateFlow(@RequestBody WorkflowDefinition request) {
        return permissionControlService.updateFlow(request);
    }

    @PostMapping("/flow/enable")
    @Operation(summary = "权限控制-流程控制-启用禁用流程")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    public WorkflowDefinition enableFlow(@RequestBody WorkflowDefinition request) {
        WorkflowDefinition update = new WorkflowDefinition();
        update.setId(request.getId());
        update.setEnabled(request.getEnabled());
        return permissionControlService.updateFlow(update);
    }

    @PostMapping("/flow/delete")
    @Operation(summary = "权限控制-流程控制-删除流程")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_DELETE)
    public void deleteFlow(@RequestParam String flowId) {
        permissionControlService.deleteFlow(flowId);
    }

    @GetMapping("/flow/status/list")
    @Operation(summary = "权限控制-流程控制-状态列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<SelectOption> listFlowStatus(@RequestParam(defaultValue = "BUG") String scene,
                                             @RequestParam(defaultValue = "system") String scopeId) {
        return permissionControlService.listFlowStatus(scene, scopeId);
    }

    @GetMapping("/flow/matrix")
    @Operation(summary = "权限控制-流程控制-流转矩阵")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public PermissionControlFlowMatrixDTO flowMatrix(@RequestParam(defaultValue = "BUG") String scene,
                                                    @RequestParam(defaultValue = "system") String scopeId) {
        return permissionControlService.flowMatrix(scene, scopeId);
    }

    @GetMapping("/flow/role/list")
    @Operation(summary = "权限控制-流程控制-流程角色列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<WorkflowRole> listFlowRoles(@RequestParam String flowId) {
        return permissionControlService.listFlowRoles(flowId);
    }

    @PostMapping("/flow/role/add")
    @Operation(summary = "权限控制-流程控制-新增流程角色")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_ADD)
    public WorkflowRole addFlowRole(@RequestBody WorkflowRole request) {
        return permissionControlService.addFlowRole(request);
    }

    @PostMapping("/flow/role/update")
    @Operation(summary = "权限控制-流程控制-修改流程角色")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    public WorkflowRole updateFlowRole(@RequestBody WorkflowRole request) {
        return permissionControlService.updateFlowRole(request);
    }

    @PostMapping("/flow/role/delete")
    @Operation(summary = "权限控制-流程控制-删除流程角色")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_DELETE)
    public void deleteFlowRole(@RequestParam String roleId) {
        permissionControlService.deleteFlowRole(roleId);
    }

    @GetMapping("/flow/role-permission")
    @Operation(summary = "权限控制-流程控制-流转角色授权")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<StatusFlowRolePermission> listFlowRolePermissions(@RequestParam String flowId) {
        return permissionControlService.listFlowRolePermissions(flowId);
    }

    @PostMapping("/flow/role-permission/save")
    @Operation(summary = "权限控制-流程控制-保存流转角色授权")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    public void saveFlowRolePermissions(@Validated @RequestBody WorkflowRolePermissionRequest request) {
        permissionControlService.saveFlowRolePermissions(request);
    }
}
