import MSR from '@/api/http/index';

export interface DataAsset {
  id: string;
  name: string;
  format: string;
  categoryId?: string;
  revision: number;
  description?: string;
  currentVersionId?: string;
  sources?: { sourceFileId: string; sourceProjectId: string; sourceProjectName: string; versionId: string }[];
  projectReferences?: { projectId: string; projectName: string; referenceCount: number }[];
  owner?: string;
  createTime?: number;
  updateTime?: number;
  hasFile?: boolean;
  jobId?: string;
  parseStatus?: string;
  parseJobId?: string;
  parseError?: string;
}
export interface DataEdit {
  requestId?: string;
  description?: string;
  id?: string;
  name?: string;
  format?: string;
  categoryId?: string;
  content?: unknown;
  expectedRevision?: number;
  delete?: boolean;
}
export interface DataVersionMetadata {
  metadata?: { name?: string | null; description?: string | null; categoryId?: string | null; format?: string | null };
  metadataAvailable?: boolean;
  metadataSource?: 'ASSET_VERSION' | 'LEGACY_SOURCE_VERSION';
}
export interface DataVersion extends DataVersionMetadata {
  id: string;
  versionNo?: number;
  hasFile?: boolean;
  createTime?: number;
}
const base = '/test-data-assets';
export const listDataAssets = (params: object) => MSR.get<{ items: DataAsset[]; total: number }>({ url: base, params });
export const getDataAsset = (id: string) => MSR.get<DataAsset>({ url: `${base}/${encodeURIComponent(id)}` });
export const getDataContent = (id: string, versionId?: string) =>
  MSR.get<DataVersionMetadata & { content: unknown; complete: boolean }>({
    url: `${base}/${encodeURIComponent(id)}/content`,
    params: { versionId },
  });
export const saveDataAsset = (data: DataEdit) =>
  data.id
    ? MSR.put<DataAsset>({ url: `${base}/${encodeURIComponent(data.id)}`, data })
    : MSR.post<DataAsset>({ url: base, data });
export const deleteDataAsset = (row: DataAsset) =>
  MSR.delete({ url: `${base}/${encodeURIComponent(row.id)}?expectedRevision=${row.revision}` });
export const batchDataAssets = (items: DataEdit[]) =>
  MSR.post<{ id?: string; items: { id: string; status: string; error?: string }[] }>({
    url: `${base}/batches`,
    data: { items },
  });
export const uploadDataAsset = (file: File, categoryId?: string, requestId: string = crypto.randomUUID()) =>
  MSR.uploadFile<DataAsset>({ url: `${base}/uploads`, params: { categoryId, requestId } }, { fileList: [file] });
export const dataVersions = (id: string) =>
  MSR.get<DataVersion[]>({ url: `${base}/${encodeURIComponent(id)}/versions` });

export const importLegacyData = (projectId: string, fileId: string) =>
  MSR.post<DataAsset>({
    url: `${base}/legacy/${encodeURIComponent(fileId)}/import?projectId=${encodeURIComponent(projectId)}`,
  });
export const downloadDataFile = (id: string, versionId?: string) =>
  MSR.get<Blob>(
    { url: `${base}/${encodeURIComponent(id)}/file`, params: { versionId }, responseType: 'blob' },
    { isTransformResponse: false }
  );

export interface DataCategory {
  id: string;
  parentId?: string;
  name: string;
  assetCount: number;
  children?: DataCategory[];
}
export const listDataCategories = () => MSR.get<DataCategory[]>({ url: `${base}/categories` });
export interface DataJob {
  id: string;
  status: string;
  assetId?: string;
  versionId?: string;
  error?: string;
  items?: { id: string; status: string; error?: string }[];
}
export const getDataJob = (id: string) => MSR.get<DataJob>({ url: `${base}/jobs/${encodeURIComponent(id)}` });
export const retryDataJob = (id: string) => MSR.post<DataJob>({ url: `${base}/jobs/${encodeURIComponent(id)}/retry` });

export const getDataLimits = () =>
  MSR.get<{ maxBytes: number; maxRows: number; formats: string[] }>({ url: `${base}/limits` });

export const publishDataAsset = (id: string, projectId: string, versionId: string) =>
  MSR.post<{ id: string; sourceVersion: string }>({
    url: `${base}/${encodeURIComponent(id)}/publish?projectId=${encodeURIComponent(
      projectId
    )}&versionId=${encodeURIComponent(versionId)}`,
  });

export const replaceDataFile = (id: string, file: File, expectedRevision: number, requestId: string) =>
  MSR.uploadFile<DataAsset>(
    { url: `${base}/${encodeURIComponent(id)}/uploads`, method: 'PUT', params: { expectedRevision, requestId } },
    { fileList: [file] }
  );

export interface DataRecord {
  recordKey: string;
  recordIndex: number;
  content: unknown;
  used: boolean;
  usedTime?: number;
}
export interface DataRecordPage {
  versionId: string;
  items: DataRecord[];
  total: number;
  page: number;
  pageSize: number;
}
export const getDataRecords = (id: string, versionId: string, page: number) =>
  MSR.get<DataRecordPage>({
    url: `${base}/${encodeURIComponent(id)}/records`,
    params: { versionId, page, pageSize: 100 },
  });
export const copyDataRecords = (id: string, versionId: string, recordKeys: string[]) =>
  MSR.post<{ content: string }>({
    url: `${base}/${encodeURIComponent(id)}/records/copy`,
    data: { versionId, recordKeys },
  });
export const markDataRecordsUsed = (id: string, versionId: string, recordKeys: string[], requestId: string) =>
  MSR.post<{ used: boolean }>({
    url: `${base}/${encodeURIComponent(id)}/records/used`,
    data: { versionId, recordKeys, requestId },
  });

export interface LegacyMigrationStatus {
  counts: { pending: number; running: number; failed: number; done: number };
  canRun: boolean;
  hasMore: boolean;
  items: { projectId: string; familyId: string; status: string; errorCode?: string }[];
}
export const getLegacyMigrationStatus = () => MSR.get<LegacyMigrationStatus>({ url: `${base}/legacy/auto/status` });
const migrationRuns = new Map<string, Promise<LegacyMigrationStatus>>();
export function runLegacyMigration(organization: string, retryFailed = false) {
  const existing = migrationRuns.get(organization);
  if (existing) return existing;
  const request = MSR.post<LegacyMigrationStatus>({ url: `${base}/legacy/auto/run`, data: { retryFailed } }).finally(
    () => {
      migrationRuns.delete(organization);
    }
  );
  migrationRuns.set(organization, request);
  return request;
}
