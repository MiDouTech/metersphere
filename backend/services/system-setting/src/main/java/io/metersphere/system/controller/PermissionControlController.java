package io.metersphere.system.controller;

import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.domain.RoleAssignmentRule;
import io.metersphere.system.domain.StatusFlowRolePermission;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.domain.WorkflowDefinition;
import io.metersphere.system.domain.WorkflowRole;
import io.metersphere.system.domain.UserRoleUiPermission;
import io.metersphere.system.dto.permission.PermissionDefinitionItem;
import io.metersphere.system.dto.permission.PermissionResourceDTO;
import io.metersphere.system.dto.permission.control.PermissionControlFlowMatrixDTO;
import io.metersphere.system.dto.permission.control.RoleAssignmentRuleRequest;
import io.metersphere.system.dto.permission.control.RoleDeleteImpactDTO;
import io.metersphere.system.dto.permission.control.RoleEnableRequest;
import io.metersphere.system.dto.permission.control.RoleMemberUpdateRequest;
import io.metersphere.system.dto.permission.control.RoleSaveRequest;
import io.metersphere.system.dto.permission.control.WorkflowRolePermissionRequest;
import io.metersphere.system.dto.permission.control.WorkflowDesignerDTO;
import io.metersphere.system.dto.permission.control.WorkflowValidationDTO;
import io.metersphere.system.dto.permission.control.WorkflowMigrationPreviewDTO;
import io.metersphere.system.dto.permission.control.WorkflowMigrationRequest;
import io.metersphere.system.dto.permission.control.WorkflowMigrationCandidateRequest;
import io.metersphere.system.dto.permission.control.UnknownPermissionDiagnosticRequest;
import io.metersphere.system.dto.request.GlobalUserRoleRelationQueryRequest;
import io.metersphere.system.dto.sdk.request.PermissionSettingUpdateRequest;
import io.metersphere.system.dto.sdk.OptionDTO;
import io.metersphere.system.dto.sdk.request.UserRoleUpdateRequest;
import io.metersphere.system.dto.user.UserExcludeOptionDTO;
import io.metersphere.system.dto.user.UserRoleRelationUserDTO;
import io.metersphere.system.service.PermissionControlService;
import io.metersphere.system.service.GlobalUserRoleLogService;
import io.metersphere.system.service.PermissionControlFlowLogService;
import io.metersphere.system.log.annotation.Log;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.validation.groups.Created;
import io.metersphere.validation.groups.Updated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.Logical;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/role/get/{roleId}")
    @Operation(summary = "权限控制-角色设置-角色详情")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public UserRole getRole(@PathVariable String roleId) {
        return permissionControlService.getRole(roleId);
    }

    @GetMapping("/role/permission/{roleId}")
    @Operation(summary = "权限控制-角色设置-接口权限")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<PermissionDefinitionItem> getRolePermission(@PathVariable String roleId) {
        return permissionControlService.getRolePermission(roleId);
    }

    @GetMapping("/role/permission-definition")
    @Operation(summary = "权限控制-角色设置-指定范围完整权限定义")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<PermissionDefinitionItem> getPermissionDefinition(@RequestParam String roleType) {
        return permissionControlService.getPermissionDefinition(roleType);
    }

    @PostMapping("/role/save")
    @Operation(summary = "权限控制-角色设置-原子保存角色及权限")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_PERMISSION_CONTROL_ADD,
            PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE}, logical = Logical.OR)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.saveLog(#request)", msClass = GlobalUserRoleLogService.class)
    public UserRole saveRole(@Validated @RequestBody RoleSaveRequest request) {
        return permissionControlService.saveRole(request);
    }

    @GetMapping("/role/ui-permission/{roleId}")
    @Operation(summary = "权限控制-角色设置-UI 资源权限")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<UserRoleUiPermission> getRoleUiPermission(@PathVariable String roleId) {
        return permissionControlService.getRoleUiPermission(roleId);
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
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.updateLog(#request)", msClass = GlobalUserRoleLogService.class)
    public UserRole updateRole(@Validated({Updated.class}) @RequestBody UserRoleUpdateRequest request) {
        return permissionControlService.updateRole(request);
    }

    @PostMapping("/role/enable")
    @Operation(summary = "权限控制-角色设置-启用禁用角色")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.enableLog(#request)", msClass = GlobalUserRoleLogService.class)
    public UserRole enableRole(@Validated @RequestBody RoleEnableRequest request) {
        return permissionControlService.enableRole(request.getRoleId(), request.getEnabled());
    }

    @PostMapping("/role/delete")
    @Operation(summary = "权限控制-角色设置-删除角色")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_DELETE)
    @Log(type = OperationLogType.DELETE, expression = "#msClass.deleteLog(#roleId)", msClass = GlobalUserRoleLogService.class)
    public void deleteRole(@RequestParam String roleId) {
        permissionControlService.deleteRole(roleId);
    }

    @GetMapping("/role/delete-impact/{roleId}")
    @Operation(summary = "权限控制-角色设置-删除影响")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public RoleDeleteImpactDTO getRoleDeleteImpact(@PathVariable String roleId) {
        return permissionControlService.getRoleDeleteImpact(roleId);
    }

    @PostMapping("/role/member/list")
    @Operation(summary = "权限控制-角色设置-成员列表")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public Pager<List<UserRoleRelationUserDTO>> listRoleMembers(@Validated @RequestBody GlobalUserRoleRelationQueryRequest request) {
        return permissionControlService.listRoleMembers(request);
    }

    @GetMapping("/role/member/options/{roleId}")
    @Operation(summary = "权限控制-角色设置-可添加成员选项")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<UserExcludeOptionDTO> getRoleMemberOptions(@PathVariable String roleId,
                                                            @RequestParam(required = false) String sourceId,
                                                            @RequestParam(required = false) String keyword) {
        return permissionControlService.getRoleMemberOptions(roleId, sourceId, keyword);
    }

    @GetMapping("/role/member/scope/options/{roleId}")
    @Operation(summary = "权限控制-角色设置-可管理成员作用域选项")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<OptionDTO> getRoleMemberScopeOptions(@PathVariable String roleId,
                                                      @RequestParam(required = false) String keyword) {
        return permissionControlService.getRoleMemberScopeOptions(roleId, keyword);
    }

    @PostMapping("/role/member/add")
    @Operation(summary = "权限控制-角色设置-添加成员")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    @Log(type = OperationLogType.ADD, expression = "#msClass.memberLog(#request)", msClass = GlobalUserRoleLogService.class)
    public void addRoleMembers(@Validated @RequestBody RoleMemberUpdateRequest request) {
        permissionControlService.addRoleMembers(request);
    }

    @PostMapping("/role/member/remove")
    @Operation(summary = "权限控制-角色设置-移除成员")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    @Log(type = OperationLogType.DELETE, expression = "#msClass.memberLog(#request)", msClass = GlobalUserRoleLogService.class)
    public void removeRoleMembers(@Validated @RequestBody RoleMemberUpdateRequest request) {
        permissionControlService.removeRoleMembers(request);
    }

    @PostMapping("/diagnostic/unknown-permission")
    @Operation(summary = "权限控制-上报未知权限中文映射")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public void reportUnknownPermission(@Validated @RequestBody UnknownPermissionDiagnosticRequest request) {
        LogUtils.warn("权限中文化映射缺失: kind=" + request.getKind()
                + ", code=" + request.getCode() + ", context=" + request.getContext()
                + ", reporter=" + SessionUtils.getUserId());
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
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.updateLog(#request)", msClass = GlobalUserRoleLogService.class)
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
    @Log(type = OperationLogType.ADD, expression = "#msClass.add(#request)", msClass = PermissionControlFlowLogService.class)
    public WorkflowDefinition addFlow(@RequestBody WorkflowDefinition request) {
        return permissionControlService.addFlow(request);
    }

    @PostMapping("/flow/update")
    @Operation(summary = "权限控制-流程控制-修改流程")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.update(#request)", msClass = PermissionControlFlowLogService.class)
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
    @Log(type = OperationLogType.DELETE, expression = "#msClass.delete(#flowId)", msClass = PermissionControlFlowLogService.class)
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

    @GetMapping("/flow/{flowId}/designer")
    @Operation(summary = "权限控制-流程控制-读取流程设计")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public WorkflowDesignerDTO getFlowDesigner(@PathVariable String flowId) {
        return permissionControlService.getWorkflowDesigner(flowId);
    }

    @PostMapping("/flow/{flowId}/designer")
    @Operation(summary = "权限控制-流程控制-保存流程设计草稿")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.designer(#flowId,#request)", msClass = PermissionControlFlowLogService.class)
    public WorkflowDesignerDTO saveFlowDesigner(@PathVariable String flowId, @RequestBody WorkflowDesignerDTO request) {
        return permissionControlService.saveWorkflowDesigner(flowId, request);
    }

    @PostMapping("/flow/{flowId}/validate")
    @Operation(summary = "权限控制-流程控制-发布前校验")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public WorkflowValidationDTO validateFlow(@PathVariable String flowId) {
        return permissionControlService.validateWorkflow(flowId);
    }

    @PostMapping("/flow/{flowId}/publish")
    @Operation(summary = "权限控制-流程控制-发布全局流程")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.publish(#flowId)", msClass = PermissionControlFlowLogService.class)
    public WorkflowDefinition publishFlow(@PathVariable String flowId, @RequestParam Integer expectedVersion) {
        return permissionControlService.publishWorkflow(flowId, expectedVersion);
    }

    @PostMapping("/flow/{flowId}/activate")
    @Operation(summary = "权限控制-流程控制-开启流程用于新建缺陷")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.activate(#flowId)", msClass = PermissionControlFlowLogService.class)
    public WorkflowDefinition activateFlow(@PathVariable String flowId) {
        return permissionControlService.activateWorkflow(flowId);
    }

    @GetMapping("/flow/{flowId}/wecom-positions/preview")
    @Operation(summary = "权限控制-流程控制-预览企微岗位")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<Map<String, Object>> previewWecomPositions(@PathVariable String flowId) {
        return permissionControlService.previewWecomPositions(flowId);
    }

    @PostMapping("/flow/{flowId}/wecom-positions/sync")
    @Operation(summary = "权限控制-流程控制-同步企微岗位")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.syncPositions(#flowId)", msClass = PermissionControlFlowLogService.class)
    public Map<String, Object> syncWecomPositions(@PathVariable String flowId) {
        return permissionControlService.syncWecomPositions(flowId);
    }

    @GetMapping("/flow/{flowId}/wecom-positions/sync-results")
    @Operation(summary = "权限控制-流程控制-查询企微岗位同步结果")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<Map<String, Object>> wecomPositionSyncResults(@PathVariable String flowId) {
        return permissionControlService.listWecomPositionSyncResults(flowId);
    }

    @GetMapping("/flow/{flowId}/impact")
    @Operation(summary = "权限控制-流程控制-流程引用影响")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public Map<String, Object> flowImpact(@PathVariable String flowId) {
        return permissionControlService.getFlowImpact(flowId);
    }

    @PostMapping("/flow/{flowId}/archive")
    @Operation(summary = "权限控制-流程控制-归档流程")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.archive(#flowId)", msClass = PermissionControlFlowLogService.class)
    public WorkflowDefinition archiveFlow(@PathVariable String flowId) {
        return permissionControlService.archiveWorkflow(flowId);
    }

    @PostMapping("/flow/{flowId}/copy")
    @Operation(summary = "权限控制-流程控制-复制为新版本草稿")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_ADD)
    @Log(type = OperationLogType.ADD, expression = "#msClass.copy(#flowId)", msClass = PermissionControlFlowLogService.class)
    public WorkflowDefinition copyFlow(@PathVariable String flowId) {
        return permissionControlService.copyWorkflow(flowId);
    }

    @GetMapping("/flow/{flowId}/migration/preview")
    @Operation(summary = "权限控制-流程控制-历史缺陷迁移预检")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public WorkflowMigrationPreviewDTO previewMigration(@PathVariable String flowId) {
        return permissionControlService.previewWorkflowMigration(flowId);
    }

    @PostMapping("/flow/{flowId}/migration/candidates")
    @Operation(summary = "权限控制-流程控制-分页查询历史缺陷候选项")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public Map<String, Object> migrationCandidates(@PathVariable String flowId,
                                                   @RequestBody WorkflowMigrationCandidateRequest request) {
        return permissionControlService.pageWorkflowMigrationCandidates(flowId, request, false);
    }

    @PostMapping("/flow/{flowId}/migration/candidate-ids")
    @Operation(summary = "权限控制-流程控制-查询筛选范围内历史缺陷 ID")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public Map<String, Object> migrationCandidateIds(@PathVariable String flowId,
                                                     @RequestBody WorkflowMigrationCandidateRequest request) {
        return permissionControlService.pageWorkflowMigrationCandidates(flowId, request, true);
    }

    @PostMapping("/flow/migration")
    @Operation(summary = "权限控制-流程控制-显式迁移历史缺陷")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.migration(#request)", msClass = PermissionControlFlowLogService.class)
    public Map<String, Object> migrate(@Validated @RequestBody WorkflowMigrationRequest request) {
        return permissionControlService.migrateWorkflow(request);
    }

    @GetMapping("/flow/migration/{batchId}")
    @Operation(summary = "权限控制-流程控制-查询迁移批次")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public Map<String, Object> migrationBatch(@PathVariable String batchId) {
        return permissionControlService.getWorkflowMigrationBatch(batchId);
    }

    @GetMapping("/flow/{flowId}/migration/batches")
    @Operation(summary = "权限控制-流程控制-查询最近迁移批次")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ)
    public List<Map<String, Object>> migrationBatches(@PathVariable String flowId) {
        return permissionControlService.listWorkflowMigrationBatches(flowId);
    }

    @PostMapping("/flow/migration/{batchId}/resume")
    @Operation(summary = "权限控制-流程控制-断点续跑迁移批次")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.batch(#batchId)", msClass = PermissionControlFlowLogService.class)
    public Map<String, Object> resumeMigration(@PathVariable String batchId) {
        return permissionControlService.resumeWorkflowMigration(batchId);
    }

    @PostMapping("/flow/migration/{batchId}/rollback")
    @Operation(summary = "权限控制-流程控制-回滚迁移批次")
    @RequiresPermissions(PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.batch(#batchId)", msClass = PermissionControlFlowLogService.class)
    public Map<String, Object> rollbackMigration(@PathVariable String batchId) {
        return permissionControlService.rollbackWorkflowMigration(batchId);
    }
}
