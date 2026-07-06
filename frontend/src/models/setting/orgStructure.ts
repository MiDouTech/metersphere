export interface DepartmentTreeNode {
  id: string;
  name: string;
  parentId?: string;
  sortOrder?: number;
  directUserCount?: number;
  totalUserCount?: number;
  deptStatus?: number;
  syncStatus?: number;
  syncTime?: number;
  children?: DepartmentTreeNode[];
}

export interface OrgStructureMemberItem {
  id: string;
  name: string;
  departmentId?: string;
  departmentName?: string;
  position?: string;
  enable?: boolean;
  syncStatus?: number;
  syncTime?: number;
}

export interface OrgStructureMemberDetail extends OrgStructureMemberItem {
  email?: string;
  phone?: string;
  wecomUserid?: string;
}

export interface MemberPageParams {
  organizationId: string;
  departmentId?: string;
  keyword?: string;
  enable?: boolean;
  syncStatus?: number;
  current?: number;
  pageSize?: number;
}

export interface OrgWecomSyncStatus {
  organizationId?: string;
  lastSyncTime?: number;
  syncLogId?: string;
  syncMode?: string;
  syncStatus?: string;
  deptTotal?: number;
  deptSuccess?: number;
  deptFailed?: number;
  userTotal?: number;
  userSuccess?: number;
  userFailed?: number;
  durationMs?: number;
  errorMessage?: string;
  logCreateTime?: number;
}

export interface OrgWecomSyncManualResponse {
  syncLogId?: string;
  syncStatus?: string;
  deptSuccess?: number;
  deptFailed?: number;
  userSuccess?: number;
  userFailed?: number;
  durationMs?: number;
  errorMessage?: string;
}

export interface OrgSyncLogItem {
  id: string;
  organizationId: string;
  syncMode: string;
  syncStatus: string;
  deptTotal?: number;
  deptSuccess?: number;
  deptFailed?: number;
  userTotal?: number;
  userSuccess?: number;
  userFailed?: number;
  durationMs?: number;
  errorMessage?: string;
  createTime: number;
  createUser: string;
}

export interface SyncLogPageParams {
  organizationId: string;
  syncStatus?: string;
  current?: number;
  pageSize?: number;
}

export interface OrgWecomSyncConfig {
  organizationId: string;
  corpId?: string;
  contactSecret?: string;
  agentId?: string;
  scheduleEnabled?: boolean;
  scheduleCron?: string;
  retryTimes?: number;
  lastSyncTime?: number;
  configured?: boolean;
}

export interface OrgWecomSyncConfigSaveParams {
  organizationId: string;
  corpId: string;
  contactSecret?: string;
  agentId?: string;
  scheduleEnabled?: boolean;
  scheduleCron?: string;
  retryTimes?: number;
}

export interface OrgWecomSyncConfigTestParams {
  organizationId: string;
  corpId?: string;
  contactSecret?: string;
}

export interface OrgWecomSyncConfigTestResponse {
  success: boolean;
  deptCount?: number;
  message?: string;
}

export const SECRET_MASK_PREFIX = '******';
