import MSR from '@/api/http/index';

export interface BusinessBranch {
  branchKey: string;
  title: string;
  understandingStatus: 'OBSERVED' | 'INFERRED' | 'UNCONFIRMED';
  evidenceRefs: string[];
}
export interface BusinessSubmission {
  source?: { taskId: string; executorId: string };
  projectId: string;
  schemaVersion: '1.0';
  operation: 'CREATE' | 'UPDATE';
  assetId?: string;
  baseVersion: number;
  idempotencyKey: string;
  scope: { systemKey: string; moduleKey: string; pageKeys: string[]; flowKey: string };
  title: string;
  document: { format: 'markdown'; content: string };
  branches: BusinessBranch[];
  changeSummary: string;
}
export interface BusinessRow {
  restricted: boolean;
  pageKeys: string[];
  id: string;
  title: string;
  systemKey: string;
  moduleKey: string;
  flowKey: string;
  branchCount: number;
  versionNo: number;
  revision: number;
  status: string;
  archived: boolean;
  currentVersionId: string | null;
}
export interface BusinessVersion {
  id: string;
  versionNo: number;
  baseVersion: number;
  status: string;
  createdAt: number;
  snapshot: BusinessSubmission;
}
export interface BusinessDetail {
  restricted: boolean;
  relations: { sourceId: string; targetId: string; relationType: string }[];
  id: string;
  revision: number;
  currentVersionId: string | null;
  versions: BusinessVersion[];
  audit: { eventType: string; actorId: string; versionId: string; reason: string; createTime: number }[];
}
export const listBusinessAssets = (projectId: string, keyword = '', archived = false) =>
  // Active and archived directories are independent requests to the same path.
  // Callers guard stale responses; URL-only cancellation must not cancel either directory.
  MSR.get<BusinessRow[]>(
    { url: '/business-assets', params: { projectId, keyword, archived } },
    { ignoreCancelToken: true }
  );
export const getBusinessAsset = (projectId: string, id: string) =>
  MSR.get<BusinessDetail>({ url: `/business-assets/${encodeURIComponent(id)}`, params: { projectId } });
export const submitBusinessAsset = (data: BusinessSubmission) =>
  MSR.post<{ assetId: string; versionId: string; status: string; currentVersion: number }>({
    url: '/business-assets',
    data,
  });
export const selectBusinessVersion = (
  id: string,
  projectId: string,
  versionId: string,
  revision: number,
  reason: string
) =>
  MSR.post<void>({
    url: `/business-assets/${encodeURIComponent(id)}/current-version`,
    data: { projectId, versionId, revision, reason },
  });
export const archiveBusinessAsset = (
  id: string,
  projectId: string,
  archived: boolean,
  revision: number,
  reason: string
) =>
  MSR.post<void>({
    url: `/business-assets/${encodeURIComponent(id)}/archive`,
    data: { projectId, archived, revision, reason },
  });
