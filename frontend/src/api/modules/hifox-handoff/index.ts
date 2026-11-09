import MSR from '@/api/http/index';

export interface Configuration {
  url: string;
  authMode: 'NONE' | 'BEARER';
  token: string;
  clearToken: boolean;
}
export interface ConfigView {
  revision: number;
  enabled: boolean;
  external_grant: boolean;
  configured: boolean;
  urlRevision: number;
  authMode: 'NONE' | 'BEARER';
  tokenConfigured: boolean;
  environment: string;
  productVersion: string;
}
export interface Inspection {
  timeEvidence: { reference: string; revision: string; occurredAt?: string };
  sourceVersion: { revision: string; memberVersion: string; ruleRevision: string; snapshotHash: string };
  functionalCount: number;
  frozenEventId?: string | null;
  pauseReasons: string[];
}
export interface Audit {
  id: string;
  actor: string;
  action: string;
  revision: number;
  time: number;
  scope: 'PROJECT' | 'DELIVERY';
}
export interface Delivery {
  business_key: string;
  event_id: string;
  state: string;
  reason?: string;
  attempts_used: number;
  http_status?: number;
  next_at: number;
  had_unknown: boolean;
  source_changed: boolean;
  create_time: number;
  update_time: number;
  payload_hash?: string;
  url_revision: number;
  source: { planId: string; planName: string; reviewId: string; reviewName: string };
  attempts: {
    sequence_no: number;
    started_at: number;
    ended_at?: number;
    http_status?: number;
    error_code?: string;
    result_unknown: boolean;
  }[];
  message?: Record<string, unknown>;
  inspection: Inspection;
  audits: Audit[];
}
export interface Summary {
  linked?: boolean;
  eligibility?: { eligible: boolean; reason: string; functionalCount: number };
  enabled?: boolean;
  canManage?: boolean;
  timeState?: string;
  delivery?: Partial<Delivery>;
  legacy?: boolean;
  legacyCanMigrate?: boolean;
  risk?: string;
}
export interface PreviewView {
  previewId: string;
  expiresAt: number;
  message: string;
  payload: {
    scope: string;
    configuration: ConfigView;
    visibilityMessage?: string;
    visibleCount?: number;
    visible?: { planId: string; planName: string; reviewId: string; functionalCount: number }[];
    occurredAt?: string;
    timeRevision?: string;
    inspection?: Inspection;
    interval?: string;
    attemptsUsed?: number;
    remaining?: number;
  };
}
export const states: Record<string, string> = {
  NONE: '尚无投递记录',
  READY: '待推送',
  RETRY: '等待重试',
  IN_FLIGHT: '发送中',
  PAUSED: '已暂停',
  FAILED: '失败待处理',
  SUCCESS: 'HTTP投递成功',
  UNKNOWN_STOPPED: 'HTTP结果不明·已停止',
  SOURCE_CHANGED: '来源已变化·已停止',
  URL_CHANGED: '配置地址已变更·已停止',
  CANCELLED: '来源不合格·未发送',
};
export const risk = 'HTTP 2xx不代表建单或通知成功；超时等最多重试3次，接收平台可能重复处理。不新增总预算。';
export const getConfiguration = (projectId: string) =>
  MSR.get<ConfigView>({ url: '/hifox-handoff/config', params: { projectId } });
export const saveConfiguration = (projectId: string, revision: number, configuration: Configuration) =>
  MSR.post<ConfigView>({ url: '/hifox-handoff/config', data: { projectId, revision, configuration } });
export const disableConfiguration = (projectId: string, revision: number) =>
  MSR.post<ConfigView>({ url: '/hifox-handoff/disable', data: { projectId, revision } });
export const grantConfiguration = (projectId: string, revision: number, externalGrant: boolean) =>
  MSR.post<ConfigView>({ url: '/hifox-handoff/grant', data: { projectId, revision, externalGrant } });
export const getSummary = (projectId: string, side: string, objectId: string) =>
  MSR.get<Summary>({ url: '/hifox-handoff/object', params: { projectId, side, objectId } });
export const previewAction = (projectId: string, planId: string, action: string) =>
  MSR.post<PreviewView>({ url: '/hifox-handoff/preview', data: { projectId, planId, action } });
export const confirmAction = (projectId: string, previewId: string) =>
  MSR.post({ url: '/hifox-handoff/confirm', data: { projectId, previewId, acknowledged: true } });
export const getRecords = (
  projectId: string,
  page: number,
  keyword: string,
  state?: string,
  sourceChanged?: boolean,
  from?: number,
  to?: number
) =>
  MSR.get<{ total: number; items: Delivery[] }>({
    url: '/hifox-handoff/records',
    params: { projectId, page, size: 20, keyword, state, sourceChanged, from, to },
  });
