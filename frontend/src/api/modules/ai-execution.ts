import MSR from '@/api/http/index';
import {
  AiExecutionAgentsUrl,
  AiExecutionResolveUrl,
  AiExecutionTaskDetailUrl,
  AiExecutionTaskUrl,
} from '@/api/requrls/ai-execution';

export type AiExecutionMode = 'RUNNER' | 'AGENT';
export type AiExecutionAgentType = 'WORKBUDDY' | 'CURSOR' | 'CODEX';

export interface AiExecutionAgentOption {
  gatewayId?: string;
  name: string;
  protocol?: string;
  configured: boolean;
  features?: string[];
  message?: string;
}

export interface AiExecutionStep {
  id: string;
  pos?: number;
  instruction?: string;
  expected?: string;
  status?: string;
  actualResult?: string;
  errorMessage?: string;
  failureCategory?: string;
  healed?: boolean;
}

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
  writebackStatus?: string;
  steps?: AiExecutionStep[];
}

export interface AiExecutionTask {
  id: string;
  projectId: string;
  testPlanId?: string;
  name?: string;
  objective?: string;
  source?: string;
  status: string;
  verdict?: 'PASSED' | 'PRODUCT_FAILED' | 'ENV_FAILED' | 'DATA_FAILED' | 'AGENT_FAILED' | 'BLOCKED' | 'INCONCLUSIVE';
  verdictReason?: string;
  providerId?: string;
  executionMode?: AiExecutionMode;
  dispatchMode?: 'PUSH' | 'PULL';
  agentType?: AiExecutionAgentType;
  agentGatewayId?: string;
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
  selectionMode?: string;
  resolvedFilter?: string;
  caseSnapshotHash?: string;
  writebackStatus?: string;
  artifactStatus?: string;
  requiredCapabilities?: string;
  contextSnapshotHash?: string;
  timeoutAt?: number;
  maxAttempts?: number;
  attemptCount?: number;
  finishedAt?: number;
  cases?: AiExecutionCase[];
}

export interface AiExecutionTaskSearchRequest {
  projectId: string;
  keyword?: string;
  status?: string;
  verdict?: string;
  executionMode?: AiExecutionMode;
  current?: number;
  pageSize?: number;
}

export interface AiExecutionTaskSearchResponse {
  total: number;
  current: number;
  pageSize: number;
  items: AiExecutionTask[];
}

export interface AiRunner {
  id: string;
  name: string;
  runnerVersion?: string;
  contractVersion?: string;
  status: 'ONLINE' | 'OFFLINE' | 'STALE';
  operatingSystem?: string;
  browserCapabilities?: string;
  environmentLabels?: string;
  maxConcurrency?: number;
  activeCount?: number;
  lastHeartbeatTime?: number;
}

export interface AiExecutionOperations {
  health: 'HEALTHY' | 'DEGRADED';
  onlineRunnerCount: number;
  staleRunnerCount: number;
  activeLeaseCount: number;
  queuedTaskCount: number;
  stuckTaskCount: number;
  writebackBacklogCount: number;
  artifactBacklogCount: number;
  expiredArtifactCount: number;
  generatedAt: number;
}

export interface AiExecutionEvaluation {
  id: string;
  taskId: string;
  projectId: string;
  executorType?: string;
  executorId?: string;
  operationalStatus?: string;
  businessVerdict?: string;
  completionRate?: number;
  evidenceRate?: number;
  healingCount?: number;
  retryCount?: number;
  durationMs?: number;
  manualScore?: number;
  manualComment?: string;
  updatedAt?: number;
}

export interface AiEvaluationSummary {
  executorType?: string;
  executorId?: string;
  sampleCount?: number;
  successfulRuns?: number;
  productFailures?: number;
  environmentFailures?: number;
  dataFailures?: number;
  agentFailures?: number;
  blockedRuns?: number;
  averageCompletionRate?: number;
  averageEvidenceRate?: number;
  averageDurationMs?: number;
  averageManualScore?: number;
}

export interface AiEvaluationPage {
  list: AiExecutionEvaluation[];
  total: number;
  current: number;
  pageSize: number;
}

export interface AiTaskTrigger {
  id: string;
  projectId: string;
  name: string;
  triggerType: 'CRON' | 'EVENT' | 'MANUAL';
  cronExpression?: string;
  timezone?: string;
  eventType?: string;
  eventFilter?: string;
  concurrencyPolicy?: 'FORBID' | 'ALLOW';
  missedPolicy?: 'SKIP' | 'FIRE_ONCE';
  taskTemplate: string;
  enabled: boolean;
  nextFireAt?: number;
  lastFireAt?: number;
  lastFireStatus?: string;
  lastError?: string;
  webhookSecret?: string;
  version?: number;
}

export interface AiTaskTriggerHistory {
  id: string;
  triggerId: string;
  taskId?: string;
  eventId?: string;
  scheduledAt?: number;
  fireTime?: number;
  status: string;
  message?: string;
}

export interface AiTaskTriggerRequest {
  projectId: string;
  name: string;
  triggerType: 'CRON' | 'EVENT' | 'MANUAL';
  cronExpression?: string;
  timezone?: string;
  eventType?: string;
  eventFilter?: string;
  concurrencyPolicy?: 'FORBID' | 'ALLOW';
  missedPolicy?: 'SKIP' | 'FIRE_ONCE';
  enabled?: boolean;
  taskTemplate: AiExecutionCreateParams & { name?: string; objective?: string; projectWide?: boolean; confirmed?: boolean };
}

export interface TestAssetPage<T> {
  list: T[];
  total: number;
  current: number;
  pageSize: number;
}

export interface TestAssetDocument {
  id: string;
  projectId: string;
  originalName: string;
  mimeType?: string;
  fileSize?: number;
  sha256?: string;
  duplicate?: boolean;
  parseStatus: string;
  parserType?: string;
  summary?: string;
  errorMessage?: string;
  createUser?: string;
  createTime?: number;
  updateTime?: number;
  assetVersionId?: string;
  assetVersionNo?: number;
}

export interface TestAssetVersion {
  id: string;
  projectId: string;
  assetType: string;
  assetId: string;
  assetName?: string;
  versionNo: number;
  sourceVersion?: string;
  contentHash: string;
  status: string;
  createdBy?: string;
  createdAt?: number;
  publishedBy?: string;
  publishedAt?: number;
}

export interface TestAssetRelation {
  id: string;
  projectId: string;
  relationType: string;
  sourceAssetType: string;
  sourceAssetId: string;
  sourceAssetName?: string;
  sourceVersionId?: string;
  sourceVersionNo?: number;
  targetAssetType: string;
  targetAssetId: string;
  targetAssetName?: string;
  targetVersionId?: string;
  targetVersionNo?: number;
  metadata?: string;
  createdBy?: string;
  createdAt?: number;
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
  artifactIds?: string[];
}

export interface AiExecutionArtifact {
  id: string;
  taskId: string;
  caseId?: string;
  stepId?: string;
  purpose: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  redacted: boolean;
  downloadPath: string;
  createTime?: number;
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
  selectionMode?: string;
  prompt?: string;
  resolvedFilter?: string;
  policySnapshot?: string;
  executionMode?: AiExecutionMode;
  agentType?: AiExecutionAgentType;
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
  selectionMode?: string;
  resolvedFilter?: Record<string, any>;
  caseSnapshotHash?: string;
  parseConfidence?: number;
  matchedReasons?: string[];
}

export function resolveAiExecutionScope(data: Record<string, any>) {
  return MSR.post<AiExecutionResolveResult>({ url: AiExecutionResolveUrl, data });
}

export function getAiExecutionAgents(projectId: string) {
  return MSR.get<AiExecutionAgentOption[]>({ url: AiExecutionAgentsUrl, params: { projectId } });
}

export function createAiExecutionTask(data: AiExecutionCreateParams) {
  return MSR.post<AiExecutionTask>({ url: AiExecutionTaskUrl, data });
}

export function getAiExecutionTask(id: string) {
  return MSR.get<AiExecutionTask>({ url: `${AiExecutionTaskDetailUrl}/${id}` });
}

export function searchAiExecutionTasks(data: AiExecutionTaskSearchRequest) {
  return MSR.post<AiExecutionTaskSearchResponse>({ url: `${AiExecutionTaskUrl}/search`, data });
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

export function getAiExecutionArtifacts(id: string) {
  return MSR.get<AiExecutionArtifact[]>({ url: `${AiExecutionTaskDetailUrl}/${id}/artifacts` });
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

export function getAiRunners() {
  return MSR.get<AiRunner[]>({ url: '/ai/runner' });
}

export function getAiExecutionOperations() {
  return MSR.get<AiExecutionOperations>({ url: '/ai/execution/operations/summary' });
}

export function getAiExecutionEvaluations(projectId: string, current = 1, pageSize = 20) {
  return MSR.get<AiEvaluationPage>({
    url: '/ai/execution/evaluations',
    params: { projectId, current, pageSize },
  });
}

export function getAiEvaluationSummary(projectId: string) {
  return MSR.get<AiEvaluationSummary[]>({ url: '/ai/execution/evaluations/summary', params: { projectId } });
}

export function listAiTaskTriggers(projectId: string) {
  return MSR.get<AiTaskTrigger[]>({ url: '/ai/execution/triggers', params: { projectId } });
}

export function createAiTaskTrigger(data: AiTaskTriggerRequest) {
  return MSR.post<AiTaskTrigger>({ url: '/ai/execution/triggers', data });
}

export function updateAiTaskTrigger(id: string, data: AiTaskTriggerRequest) {
  return MSR.put<AiTaskTrigger>({ url: `/ai/execution/triggers/${id}`, data });
}

export function fireAiTaskTrigger(id: string) {
  return MSR.post<AiTaskTriggerHistory>({ url: `/ai/execution/triggers/${id}/fire` });
}

export function rotateAiTaskTriggerSecret(id: string) {
  return MSR.post<AiTaskTrigger>({ url: `/ai/execution/triggers/${id}/rotate-secret` });
}

export function listAiTaskTriggerHistory(id: string, limit = 50) {
  return MSR.get<AiTaskTriggerHistory[]>({ url: `/ai/execution/triggers/${id}/history`, params: { limit } });
}

export function pageTestAssetDocuments(params: {
  projectId: string;
  parseStatus?: string;
  keyword?: string;
  current?: number;
  pageSize?: number;
}) {
  return MSR.get<TestAssetPage<TestAssetDocument>>({ url: '/test-assets/documents', params });
}

export function pageTestAssetVersions(params: {
  projectId: string;
  assetType?: string;
  assetId?: string;
  keyword?: string;
  current?: number;
  pageSize?: number;
}) {
  return MSR.get<TestAssetPage<TestAssetVersion>>({ url: '/test-assets/versions', params });
}

export function pageTestAssetRelations(params: {
  projectId: string;
  assetType?: string;
  assetId?: string;
  relationType?: string;
  keyword?: string;
  current?: number;
  pageSize?: number;
}) {
  return MSR.get<TestAssetPage<TestAssetRelation>>({ url: '/test-assets/relations', params });
}
