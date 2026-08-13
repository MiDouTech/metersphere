export interface WorkflowDefinition {
  id?: string;
  code: string;
  name: string;
  scene: string;
  scopeType: 'SYSTEM' | 'ORGANIZATION' | 'PROJECT';
  scopeId: string;
  defaultFlow?: boolean;
  enabled?: boolean;
  description?: string;
}

export interface WorkflowRole {
  id?: string;
  flowId: string;
  code: string;
  name: string;
  roleType: 'SYSTEM_ROLE' | 'FIELD_USER';
  roleId?: string;
  fieldKey?: string;
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
  name: string;
  statusFlowTargets?: string[];
}

export interface PermissionControlStatusFlow {
  id: string;
  fromId: string;
  toId: string;
}

export interface PermissionControlFlowMatrix {
  statuses: PermissionControlStatus[];
  transitions: PermissionControlStatusFlow[];
}
