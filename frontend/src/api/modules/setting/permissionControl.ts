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
  StatusFlowRolePermission,
  WorkflowDefinition,
  WorkflowRole,
  WorkflowValidationResult,
} from '@/models/setting/permissionControl';

export function getPermissionControlRoles() {
  return MSR.get<PermissionControlRole[]>({ url: urls.roleListUrl });
}
export function getPermissionControlRole(roleId: string) {
  return MSR.get<PermissionControlRole>({ url: `/permission-control/role/get/${roleId}` });
}

export function deletePermissionControlRole(roleId: string) {
  return MSR.post<void>({ url: urls.roleDeleteUrl, params: { roleId } }, { joinParamsToUrl: true });
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
  return MSR.post<void>({ url: urls.flowDeleteUrl, params: { flowId } }, { joinParamsToUrl: true });
}

export function getPermissionControlFlowRoles(flowId: string) {
  return MSR.get<WorkflowRole[]>({ url: urls.flowRoleListUrl, params: { flowId } });
}

export function getPermissionControlFlowMatrix(params: { scene?: string; scopeId?: string }) {
  return MSR.get<PermissionControlFlowMatrix>({ url: urls.flowMatrixUrl, params });
}

export function getPermissionControlFlowDesigner(flowId: string) {
  return MSR.get<PermissionControlFlowMatrix>({ url: `/permission-control/flow/${flowId}/designer` });
}

export function savePermissionControlFlowDesigner(flowId: string, data: PermissionControlFlowMatrix) {
  return MSR.post<PermissionControlFlowMatrix>({ url: `/permission-control/flow/${flowId}/designer`, data });
}

export function validatePermissionControlFlow(flowId: string) {
  return MSR.post<WorkflowValidationResult>({ url: `/permission-control/flow/${flowId}/validate` });
}

export function publishPermissionControlFlow(flowId: string, expectedVersion: number) {
  return MSR.post<WorkflowDefinition>(
    {
      url: `/permission-control/flow/${flowId}/publish`,
      params: { expectedVersion },
    },
    { joinParamsToUrl: true }
  );
}

export function archivePermissionControlFlow(flowId: string) {
  return MSR.post<WorkflowDefinition>({ url: `/permission-control/flow/${flowId}/archive` });
}

export function activatePermissionControlFlow(flowId: string) {
  return MSR.post<WorkflowDefinition>({ url: `/permission-control/flow/${flowId}/activate` });
}

export interface WorkflowImpact {
  associatedBugCount: number;
  transitionHistoryCount: number;
  activeForNew: boolean;
  deleteStateAllowed?: boolean;
  deletable: boolean;
  archiveRequired: boolean;
  reason?: string;
}
export function getPermissionControlFlowImpact(flowId: string) {
  return MSR.get<WorkflowImpact>({ url: `/permission-control/flow/${flowId}/impact` });
}

export function previewPermissionControlWecomPositions(flowId: string) {
  return MSR.get<Array<{ position: string; sourceKey: string; memberCount: number; existing: boolean }>>({
    url: `/permission-control/flow/${flowId}/wecom-positions/preview`,
  });
}

export function syncPermissionControlWecomPositions(flowId: string) {
  return MSR.post<{ batchId: string; total: number; created: number; updated: number; disabled: number }>({
    url: `/permission-control/flow/${flowId}/wecom-positions/sync`,
  });
}

export function getPermissionControlWecomPositionSyncResults(flowId: string) {
  return MSR.get<
    Array<{
      batchId: string;
      total: number;
      created: number;
      updated: number;
      disabled: number;
      createUser: string;
      createTime: number;
    }>
  >({ url: `/permission-control/flow/${flowId}/wecom-positions/sync-results` });
}

export function copyPermissionControlFlow(flowId: string) {
  return MSR.post<WorkflowDefinition>({ url: `/permission-control/flow/${flowId}/copy` });
}

export interface WorkflowMigrationPreview {
  targetFlowId: string;
  targetVersion: number;
  affectedBugCount: number;
  suggestedMappings: Record<string, string>;
  sourceStatuses?: Array<{
    id: string;
    code: string;
    name: string;
    bugCount: number;
    projectCount: number;
    nameMissing: boolean;
    suggestedTargetStatusId?: string;
    autoMapped: boolean;
  }>;
  /** @deprecated Kept for compatibility with older backends. */
  unresolvedStatusIds: string[];
  targetStatuses: Array<{ id: string; code: string; name: string }>;
  projects: Array<{
    projectId: string;
    projectName: string;
    bugCount: number;
    sourceStatusIds: string[];
    unresolvedStatusIds: string[];
  }>;
}

export function previewPermissionControlFlowMigration(flowId: string) {
  return MSR.get<WorkflowMigrationPreview>({ url: `/permission-control/flow/${flowId}/migration/preview` });
}

export interface WorkflowMigrationCandidateQuery {
  current: number;
  pageSize: number;
  keyword?: string;
  projectIds?: string[];
  sourceStatusIds?: string[];
  createTimeStart?: number;
  createTimeEnd?: number;
}

export interface WorkflowMigrationCandidate {
  id: string;
  num: number | string;
  title: string;
  projectId: string;
  projectName: string;
  sourceStatusId: string;
  sourceStatusName: string;
  createTime: number;
}

export function pagePermissionControlFlowMigrationCandidates(flowId: string, data: WorkflowMigrationCandidateQuery) {
  return MSR.post<CommonList<WorkflowMigrationCandidate>>({
    url: `/permission-control/flow/${flowId}/migration/candidates`,
    data,
  });
}

export function getPermissionControlFlowMigrationCandidateIds(flowId: string, data: WorkflowMigrationCandidateQuery) {
  return MSR.post<{ ids: string[]; total: number }>({
    url: `/permission-control/flow/${flowId}/migration/candidate-ids`,
    data,
  });
}

export function migratePermissionControlFlow(data: {
  targetFlowId: string;
  dryRun: boolean;
  statusMappings: Record<string, string>;
  bugIds?: string[];
  projectIds?: string[];
}) {
  return MSR.post<{ batchId: string; total: number; status?: string; migrated: number; skipped?: number }>({
    url: '/permission-control/flow/migration',
    data,
  });
}

export interface WorkflowMigrationBatch {
  id: string;
  targetFlowId?: string;
  dryRun?: boolean;
  status: string;
  totalCount: number;
  successCount: number;
  skippedCount: number;
  failedCount: number;
  createTime?: number;
  updateTime?: number;
  finishTime?: number;
  failures?: Array<{ bugId: string; sourceStatus: string; failureCode: string; failureReason: string }>;
}
export function listPermissionControlFlowMigrationBatches(flowId: string) {
  return MSR.get<WorkflowMigrationBatch[]>({ url: `/permission-control/flow/${flowId}/migration/batches` });
}
export function getPermissionControlFlowMigrationBatch(batchId: string) {
  return MSR.get<WorkflowMigrationBatch>({ url: `/permission-control/flow/migration/${batchId}` });
}
export function resumePermissionControlFlowMigration(batchId: string) {
  return MSR.post({ url: `/permission-control/flow/migration/${batchId}/resume` });
}
export function rollbackPermissionControlFlowMigration(batchId: string) {
  return MSR.post<WorkflowMigrationBatch>({ url: `/permission-control/flow/migration/${batchId}/rollback` });
}

export function addPermissionControlFlowRole(data: WorkflowRole) {
  return MSR.post<WorkflowRole>({ url: urls.flowRoleAddUrl, data });
}

export function updatePermissionControlFlowRole(data: WorkflowRole) {
  return MSR.post<WorkflowRole>({ url: urls.flowRoleUpdateUrl, data });
}

export function deletePermissionControlFlowRole(roleId: string) {
  return MSR.post<void>({ url: urls.flowRoleDeleteUrl, params: { roleId } }, { joinParamsToUrl: true });
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
