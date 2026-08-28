export interface WorkflowDefinition {
  id?: string;
  code: string;
  name: string;
  scene: string;
  scopeType: 'SYSTEM' | 'ORGANIZATION' | 'PROJECT';
  scopeId: string;
  defaultFlow?: boolean;
  activeForNew?: boolean;
  enabled?: boolean;
  description?: string;
  version?: number;
  lifecycle?: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  publishedTime?: number;
  publishedBy?: string;
  sourceFlowId?: string;
  copyFromFlowId?: string;
}

export interface WorkflowRole {
  id?: string;
  flowId: string;
  code: string;
  name: string;
  roleType: 'SYSTEM_ROLE' | 'FIELD_USER' | 'POSITION';
  roleId?: string;
  fieldKey?: string;
  sourceType?: 'MANUAL' | 'WECOM_POSITION';
  sourceKey?: string;
  matchMode?: 'CONTAINS' | 'EXACT';
  enabled?: boolean;
}

export interface StatusFlowRolePermission {
  id?: string;
  flowId: string;
  statusFlowId: string;
  workflowRoleId: string;
  visible: boolean;
  operable: boolean;
  enabled?: boolean;
}

export interface PermissionControlStatus {
  id: string;
  code?: string;
  name: string;
  remark?: string;
  initial?: boolean;
  terminal?: boolean;
  enabled?: boolean;
  pos?: number;
  statusFlowTargets?: string[];
}

export interface PermissionControlStatusFlow {
  id: string;
  fromId: string;
  toId: string;
  enabled?: boolean;
}

export interface WorkflowValidationResult {
  valid: boolean;
  errors: string[];
}

export interface PermissionControlFlowMatrix {
  statuses: PermissionControlStatus[];
  transitions: PermissionControlStatusFlow[];
}

export interface PermissionControlRole {
  id: string;
  name: string;
  description?: string;
  internal: boolean;
  type: 'SYSTEM' | 'ORGANIZATION' | 'PROJECT';
  scopeId: string;
  enabled?: boolean;
}

export interface PermissionResourceNode {
  id: string;
  code: string;
  name: string;
  type: 'MENU' | 'PAGE' | 'TAB' | 'BUTTON' | 'API';
  scopeType: 'SYSTEM' | 'ORGANIZATION' | 'PROJECT';
  parentCode?: string;
  permissionId?: string;
  visibleDefault: boolean;
  operableDefault: boolean;
  children?: PermissionResourceNode[];
}

export interface RolePermissionItem {
  id: string;
  name: string;
  enable: boolean;
  permissions?: RolePermissionItem[];
  children?: RolePermissionItem[];
}

export interface RoleSavePayload {
  id?: string;
  name: string;
  description?: string;
  type: 'SYSTEM' | 'ORGANIZATION' | 'PROJECT';
  enabled: boolean;
  permissions: Array<{ id: string; enable: boolean }>;
}

export interface RoleDeleteImpact {
  memberCount: number;
  usersWithoutOtherBusinessRoleCount: number;
}

export interface PermissionControlRoleMember {
  id: string;
  userId: string;
  name: string;
  email?: string;
  phone?: string;
}

export interface PermissionControlRoleMemberOption {
  id: string;
  name: string;
  email?: string;
  exclude?: boolean;
}

export interface PermissionControlRoleMemberScopeOption {
  id: string;
  name: string;
}

export interface RoleAssignmentRule {
  id?: string;
  roleId: string;
  organizationId: string;
  departmentId?: string;
  positionId?: string;
  enabled: boolean;
  syncMode: 'MANUAL' | 'AUTO';
  createTime?: number;
  updateTime?: number;
}
