import MSR from '@/api/http/index';
import * as urls from '@/api/requrls/setting/permissionControl';

import {
  PermissionControlFlowMatrix,
  StatusFlowRolePermission,
  WorkflowDefinition,
  WorkflowRole,
} from '@/models/setting/permissionControl';
import { UserGroupItem } from '@/models/setting/usergroup';

export function getPermissionControlRoles() {
  return MSR.get<UserGroupItem[]>({ url: urls.roleListUrl });
}

export function enablePermissionControlRole(data: { roleId: string; enabled: boolean }) {
  return MSR.post<UserGroupItem>({ url: urls.roleEnableUrl, data });
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

export function getPermissionControlFlowRoles(flowId: string) {
  return MSR.get<WorkflowRole[]>({ url: urls.flowRoleListUrl, params: { flowId } });
}

export function getPermissionControlFlowMatrix(params: { scene?: string; scopeId?: string }) {
  return MSR.get<PermissionControlFlowMatrix>({ url: urls.flowMatrixUrl, params });
}

export function addPermissionControlFlowRole(data: WorkflowRole) {
  return MSR.post<WorkflowRole>({ url: urls.flowRoleAddUrl, data });
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
