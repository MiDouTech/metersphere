import MSR from '@/api/http/index';

export type ResourceEditType = 'FUNCTIONAL_CASE' | 'BUG' | 'TEST_PLAN_DOCUMENT' | string;

export interface ResourceEditLockRequest {
  resourceType: ResourceEditType;
  resourceId: string;
  projectId: string;
}

export interface ResourceEditLockResponse {
  acquired: boolean;
  readOnly: boolean;
  holderUserId?: string;
  holderUserName?: string;
  expireTime?: number;
  message?: string;
}

export interface ResourceEditMetaResponse {
  resourceType: string;
  resourceId: string;
  undoAvailable: number;
  redoAvailable: number;
  activeSeq?: number;
}

export interface ResourceEditUndoResponse {
  success: boolean;
  payload?: string;
  undoAvailable: number;
  redoAvailable: number;
  message?: string;
}

export function getResourceEditAutosaveEnabled() {
  return MSR.get<boolean>({ url: '/resource-edit/autosave-enabled' });
}

export function acquireResourceEditLock(data: ResourceEditLockRequest) {
  return MSR.post<ResourceEditLockResponse>({ url: '/resource-edit/lock/acquire', data });
}

export function heartbeatResourceEditLock(data: ResourceEditLockRequest) {
  return MSR.post<ResourceEditLockResponse>({ url: '/resource-edit/lock/heartbeat', data });
}

export function releaseResourceEditLock(data: ResourceEditLockRequest) {
  return MSR.post({ url: '/resource-edit/lock/release', data });
}

export function recordResourceEditSnapshot(data: ResourceEditLockRequest & { payload: string }) {
  return MSR.post({ url: '/resource-edit/snapshot', data });
}

export function getResourceEditMeta(resourceType: string, resourceId: string) {
  return MSR.get<ResourceEditMetaResponse>({ url: `/resource-edit/meta/${resourceType}/${resourceId}` });
}

export function undoResourceEdit(data: ResourceEditLockRequest) {
  return MSR.post<ResourceEditUndoResponse>({ url: '/resource-edit/undo', data });
}

export function redoResourceEdit(data: ResourceEditLockRequest) {
  return MSR.post<ResourceEditUndoResponse>({ url: '/resource-edit/redo', data });
}
