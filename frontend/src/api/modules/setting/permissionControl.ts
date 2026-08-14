import MSR from '@/api/http/index';
import * as urls from '@/api/requrls/setting/permissionControl';
import type { UnknownPermissionDiagnostic } from '@/config/permissionLocale';

import type { CommonList, TableQueryParams } from '@/models/common';
import {
  PermissionControlFlowMatrix,
  PermissionControlRole,
  PermissionControlRoleMember,
  PermissionControlRoleMemberOption,
  PermissionControlRoleMemberScopeOption,
  PermissionResourceNode,
  RoleAssignmentRule,
  RoleDeleteImpact,
  RolePermissionItem,
  RoleSavePayload,
  RoleUiPermissionValue,
  StatusFlowRolePermission,
  WorkflowDefinition,
  WorkflowRole,
} from '@/models/setting/permissionControl';

export function getPermissionControlRoles() {
  return MSR.get<PermissionControlRole[]>({ url: urls.roleListUrl });
}

export function deletePermissionControlRole(roleId: string) {
  return MSR.post<void>({ url: urls.roleDeleteUrl, params: { roleId } });
}

export function getPermissionControlResourceTree(scopeType: string) {
  return MSR.get<PermissionResourceNode[]>({ url: urls.resourceTreeUrl, params: { scopeType } });
}

export function getPermissionControlRolePermissions(roleId: string) {
  return MSR.get<RolePermissionItem[]>({ url: `${urls.rolePermissionUrl}${roleId}` });
}

export function getPermissionControlPermissionDefinition(roleType: string) {
  return MSR.get<RolePermissionItem[]>({ url: urls.rolePermissionDefinitionUrl, params: { roleType } });
}

export function savePermissionControlRole(data: RoleSavePayload) {
  return MSR.post<PermissionControlRole>({ url: urls.roleSaveUrl, data });
}

export function getPermissionControlRoleDeleteImpact(roleId: string) {
  return MSR.get<RoleDeleteImpact>({ url: `${urls.roleDeleteImpactUrl}${roleId}` });
}

export function getPermissionControlRoleUiPermissions(roleId: string) {
  return MSR.get<RoleUiPermissionValue[]>({ url: `${urls.roleUiPermissionUrl}${roleId}` });
}

export function enablePermissionControlRole(data: { roleId: string; enabled: boolean }) {
  return MSR.post<PermissionControlRole>({ url: urls.roleEnableUrl, data });
}

export function pagePermissionControlRoleMembers(data: TableQueryParams & { roleId: string; sourceId?: string }) {
  return MSR.post<CommonList<PermissionControlRoleMember>>({ url: urls.roleMemberListUrl, data });
}

export function getPermissionControlRoleMemberOptions(roleId: string, sourceId?: string, keyword?: string) {
  return MSR.get<PermissionControlRoleMemberOption[]>({
    url: `${urls.roleMemberOptionsUrl}${roleId}`,
    params: { sourceId, keyword },
  });
}

export function getPermissionControlRoleMemberScopeOptions(roleId: string, keyword?: string) {
  return MSR.get<PermissionControlRoleMemberScopeOption[]>({
    url: `${urls.roleMemberScopeOptionsUrl}${roleId}`,
    params: { keyword },
  });
}

export function addPermissionControlRoleMembers(data: { roleId: string; sourceId?: string; userIds: string[] }) {
  return MSR.post<void>({ url: urls.roleMemberAddUrl, data });
}

export function removePermissionControlRoleMembers(data: { roleId: string; sourceId?: string; userIds: string[] }) {
  return MSR.post<void>({ url: urls.roleMemberRemoveUrl, data });
}

export function assignPermissionControlRoleByPosition(
  data: Omit<RoleAssignmentRule, 'id' | 'createTime' | 'updateTime'>
) {
  return MSR.post<RoleAssignmentRule>({ url: urls.roleMemberAssignByPositionUrl, data });
}

export function getPermissionControlRoleAssignmentRules(roleId: string) {
  return MSR.get<RoleAssignmentRule[]>({ url: `${urls.roleMemberAssignmentRuleUrl}${roleId}` });
}

export function reportUnknownPermissionDiagnostic(data: UnknownPermissionDiagnostic) {
  return MSR.post<void>({ url: urls.unknownPermissionReportUrl, data });
}

export function getPermissionControlFlows(params: { scene?: string; scopeType?: string; scopeId?: string }) {
  return MSR.get<WorkflowDefinition[]>({ url: urls.flowListUrl, params });
}

export function addPermissionControlFlow(data: WorkflowDefinition) {
  return MSR.post<WorkflowDefinition>({ url: urls.flowAddUrl, data });
}

export function updatePermissionControlFlow(data: Partial<WorkflowDefinition>) {
  return MSR.post<WorkflowDefinition>({ url: urls.flowUpdateUrl, data });
}

export function enablePermissionControlFlow(data: Pick<WorkflowDefinition, 'id' | 'enabled'>) {
  return MSR.post<WorkflowDefinition>({ url: urls.flowEnableUrl, data });
}

export function deletePermissionControlFlow(flowId: string) {
  return MSR.post<void>({ url: urls.flowDeleteUrl, params: { flowId } });
}

export function getPermissionControlFlowRoles(flowId: string) {
  return MSR.get<WorkflowRole[]>({ url: urls.flowRoleListUrl, params: { flowId } });
}

export function getPermissionControlFlowMatrix(params: { scene?: string; scopeId?: string }) {
  return MSR.get<PermissionControlFlowMatrix>({ url: urls.flowMatrixUrl, params });
}

export function addPermissionControlFlowRole(data: WorkflowRole) {
  return MSR.post<WorkflowRole>({ url: urls.flowRoleAddUrl, data });
}

export function updatePermissionControlFlowRole(data: WorkflowRole) {
  return MSR.post<WorkflowRole>({ url: urls.flowRoleUpdateUrl, data });
}

export function deletePermissionControlFlowRole(roleId: string) {
  return MSR.post<void>({ url: urls.flowRoleDeleteUrl, params: { roleId } });
}

export function getPermissionControlFlowRolePermissions(flowId: string) {
  return MSR.get<StatusFlowRolePermission[]>({ url: urls.flowRolePermissionUrl, params: { flowId } });
}

export function savePermissionControlFlowRolePermissions(data: {
  flowId: string;
  permissions: StatusFlowRolePermission[];
}) {
  return MSR.post<void>({ url: urls.flowRolePermissionSaveUrl, data });
}
