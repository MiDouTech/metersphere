import MSR from '@/api/http/index';
import { AiExecutionResolveUrl, AiExecutionTaskDetailUrl, AiExecutionTaskUrl } from '@/api/requrls/ai-execution';

export interface AiExecutionCase {
  id?: string;
  taskId?: string;
  projectId?: string;
  caseId: string;
  caseNum?: number;
  caseName?: string;
  testPlanId?: string;
  testPlanCaseId?: string;
  status?: string;
  result?: string;
  pos?: number;
  retryCount?: number;
  errorMessage?: string;
}

export interface AiExecutionTask {
  id: string;
  projectId: string;
  testPlanId?: string;
  source?: string;
  status: string;
  providerId?: string;
  environmentId?: string;
  targetUrl?: string;
  browserType?: string;
  loginMode?: string;
  totalCount: number;
  successCount: number;
  failedCount: number;
  blockedCount?: number;
  skippedCount?: number;
  unexecutedCount: number;
  confirmRequired?: boolean;
  confirmationReason?: string;
  createTime?: number;
  updateTime?: number;
  cases?: AiExecutionCase[];
}

export interface AiExecutionEvent {
  id?: string;
  taskId?: string;
  caseId?: string;
  stepId?: string;
  sequence: number;
  eventTime?: number;
  level: string;
  eventType: string;
  message?: string;
}

export interface AiExecutionEventsResponse {
  events: AiExecutionEvent[];
  cursor: number;
  hasMore?: boolean;
}

export interface AiExecutionCreateParams {
  projectId: string;
  testPlanId?: string;
  caseIds: string[];
  source?: string;
  confirmed?: boolean;
  projectWide?: boolean;
  idempotencyKey?: string;
  providerId?: string;
  environmentId?: string;
  targetUrl?: string;
  browserType?: string;
  loginMode?: string;
}

export interface AiExecutionResolveResult {
  status?: string;
  executable?: boolean;
  confirmationRequired?: boolean;
  confirmationReason?: string;
  projectId?: string;
  testPlanId?: string;
  message?: string;
  total?: number;
  estimatedMinutes?: number;
  highRisk?: boolean;
  highRiskSignals?: string[];
  candidatePlans?: Array<{ id: string; name?: string; status?: string; associatedCaseCount?: number }>;
  cases?: Array<{ caseId: string; num?: number; name?: string; testPlanCaseId?: string }>;
  warnings?: string[];
}

export function resolveAiExecutionScope(data: Record<string, any>) {
  return MSR.post<AiExecutionResolveResult>({ url: AiExecutionResolveUrl, data });
}

export function createAiExecutionTask(data: AiExecutionCreateParams) {
  return MSR.post<AiExecutionTask>({ url: AiExecutionTaskUrl, data });
}

export function getAiExecutionTask(id: string) {
  return MSR.get<AiExecutionTask>({ url: `${AiExecutionTaskDetailUrl}/${id}` });
}

export function getAiExecutionEvents(id: string, params?: { cursor?: number; limit?: number }) {
  return MSR.get<AiExecutionEventsResponse>({
    url: `${AiExecutionTaskDetailUrl}/${id}/events`,
    params: {
      cursor: params?.cursor ?? 0,
      limit: params?.limit ?? 100,
    },
  });
}

export function confirmAiExecutionTask(id: string, reason?: string) {
  return MSR.post<AiExecutionTask>({ url: `${AiExecutionTaskDetailUrl}/${id}/confirm`, data: { reason } });
}

export function cancelAiExecutionTask(id: string, reason?: string) {
  return MSR.post<AiExecutionTask>({ url: `${AiExecutionTaskDetailUrl}/${id}/cancel`, data: { reason } });
}

export function loginReadyAiExecutionTask(id: string, reason?: string) {
  return MSR.post<AiExecutionTask>({ url: `${AiExecutionTaskDetailUrl}/${id}/login-ready`, data: { reason } });
}

export function pauseAiExecutionTask(id: string, reason?: string) {
  return MSR.post<AiExecutionTask>({ url: `${AiExecutionTaskDetailUrl}/${id}/pause`, data: { reason } });
}

export function retryAiExecutionTask(id: string, reason?: string) {
  return MSR.post<AiExecutionTask>({ url: `${AiExecutionTaskDetailUrl}/${id}/retry`, data: { reason } });
}
