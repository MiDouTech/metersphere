import MSR from '@/api/http/index';

export interface LegacyJob {
  jobId: string;
  policy: string;
  state: string;
  stage?: string;
}
export interface LegacyGroup {
  legacyGroupId: string;
  classification: string;
  planId: string;
  reviewId: string;
  sourceCount?: number;
  targetCount?: number;
  addCount?: number;
  removeCount?: number;
  jobs: LegacyJob[];
}
export interface LegacyInventory {
  projectId: string;
  manifestHash: string;
  groups: LegacyGroup[];
  orphanJobs: { job_id: string; policy: string; state: string }[];
}
export interface LegacyJobDecision {
  action: string;
  executingActor?: string;
  executionMode?: 'ORIGINAL' | 'WEB';
  reviewSettings?: { name: string; moduleId: string; reviewers: string[]; reviewPassRule: string };
}
export interface LegacyDecision {
  projectId: string;
  requestKey: string;
  manifestHash: string;
  groups: Record<string, string>;
  jobs: Record<string, LegacyJobDecision>;
}
export interface LegacyChange {
  id: string;
  project_id: string;
  state: string;
  snapshot_hash: string;
  revision: number;
  expires_at: number;
  backup_at?: number;
  rehearsal_id?: string;
  rollback_mode?: string;
  selection?: LegacyDecision;
  canExecute?: boolean;
  canApprove?: boolean;
  effects?: string[];
}
export interface LegacyStep {
  projectId: string;
  changeId: string;
  snapshotHash?: string;
  acknowledged?: boolean;
  stopEvidenceHash?: string;
  releaseSha?: string;
  previewHash?: string;
}
export type LegacyAction =
  | 'backup'
  | 'rehearse'
  | 'approve'
  | 'execute'
  | 'rollback-preview'
  | 'rollback-approve'
  | 'rollback';
export const scanLegacyRelations = (projectId: string) =>
  MSR.get<LegacyInventory>({ url: '/plan-review/legacy/scan', params: { projectId } });
export const previewLegacyMigration = (data: LegacyDecision) =>
  MSR.post<LegacyChange>({ url: '/plan-review/legacy/preview', data });
export const getLegacyChange = (projectId: string, changeId: string) =>
  MSR.get<LegacyChange>({ url: '/plan-review/legacy/change', params: { projectId, changeId } });
export const legacyMigrationStep = (action: LegacyAction, data: LegacyStep) =>
  MSR.post<{ projectId?: string; state?: string; previewHash?: string; mode?: string }>({
    url: `/plan-review/legacy/${action}`,
    data,
  });
export const getLegacyChanges = (projectId: string) =>
  MSR.get<{ projectId: string; items: LegacyChange[] }>({ url: '/plan-review/legacy/changes', params: { projectId } });
