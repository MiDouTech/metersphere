import MSR from '@/api/http/index';
import {
  acknowledgeAiExecutionAlertUrl,
  AiBusinessFlowsUrl,
  AiBusinessFlowUrl,
  AiCaseExecutabilityCheckUrl,
  AiCaseExecutabilityConfigUrl,
  AiCredentialReferenceDisableUrl,
  AiCredentialReferenceEnableUrl,
  AiCredentialReferencesUrl,
  AiCredentialReferenceUrl,
  AiCredentialReferenceVerifyUrl,
  AiEnvironmentProfileDisableUrl,
  AiEnvironmentProfileEnableUrl,
  AiEnvironmentProfilesUrl,
  AiEnvironmentProfileUrl,
  AiEnvironmentProfileVerifyUrl,
  AiExecutionAgentsUrl,
  AiExecutionAlertsUrl,
  AiExecutionArtifactDownloadUrl,
  AiExecutionEvaluationHistoryUrl,
  AiExecutionEvaluationManualUrl,
  AiExecutionEvaluationSummaryUrl,
  AiExecutionEvaluationsUrl,
  AiExecutionHumanRequestRespondUrl,
  AiExecutionMetricsUrl,
  AiExecutionOperationsLeasesUrl,
  AiExecutionOperationsSummaryUrl,
  AiExecutionPreflightDetailUrl,
  AiExecutionPreflightUrl,
  AiExecutionResolveUrl,
  AiExecutionTaskArtifactsUrl,
  AiExecutionTaskByIdUrl,
  AiExecutionTaskCancelUrl,
  AiExecutionTaskConfirmUrl,
  AiExecutionTaskEventsUrl,
  AiExecutionTaskHumanRequestsUrl,
  AiExecutionTaskLoginReadyUrl,
  AiExecutionTaskObservabilityUrl,
  AiExecutionTaskPauseUrl,
  AiExecutionTaskRetryUrl,
  AiExecutionTaskSearchUrl,
  AiExecutionTaskUrl,
  AiExecutionTriggerFireUrl,
  AiExecutionTriggerHistoryUrl,
  AiExecutionTriggerRotateSecretUrl,
  AiExecutionTriggersUrl,
  AiExecutionTriggerUrl,
  AiLoginProfileDisableUrl,
  AiLoginProfileEnableUrl,
  AiLoginProfilesUrl,
  AiLoginProfileUrl,
  AiModelInvocationUrl,
  AiModelProfileCapabilitiesUrl,
  AiModelProfileDisableUrl,
  AiModelProfileEnableUrl,
  AiModelProfileHealthUrl,
  AiModelProfilesUrl,
  AiModelProfileUrl,
  AiModelProfileVerifyUrl,
  AiModelUsageUrl,
  AiPageObjectsUrl,
  AiPageObjectUrl,
  AiPromptTemplatePreviewUrl,
  AiPromptTemplatePublishUrl,
  AiPromptTemplateRollbackUrl,
  AiPromptTemplatesUrl,
  AiRunnerRegisterUrl,
  AiRunnersUrl,
  TestAssetBatchCategoryAssignUrl,
  TestAssetCatalogDetailUrl,
  TestAssetCatalogPublishUrl,
  TestAssetCatalogUrl,
  TestAssetCategoriesUrl,
  TestAssetCategoryAssignUrl,
  TestAssetCategoryReorderUrl,
  TestAssetCategoryTreeUrl,
  TestAssetDocumentsUrl,
  TestAssetMetadataUrl,
  TestAssetRelationsUrl,
  TestAssetSourceGovernanceUrl,
  TestAssetVersionDeprecateUrl,
  TestAssetVersionsUrl,
} from '@/api/requrls/ai-execution';

export type AiExecutionMode = 'RUNNER' | 'AGENT';
export type AiExecutionAgentType = 'WORKBUDDY' | 'CURSOR' | 'CODEX';
export type AiTaskOrigin = 'PLATFORM_SCHEDULED' | 'PLATFORM_MANUAL' | 'PERSONAL_MCP';
export type AiExecutorChannel = 'MODEL_API_RUNNER' | 'EXTERNAL_MCP_AGENT';

export interface AiEnvironmentProfile {
  id: string;
  organizationId: string;
  projectId: string;
  environmentId: string;
  name: string;
  baseUrl: string;
  allowedOrigins: string[];
  networkZone?: string;
  environmentType: 'TEST' | 'STAGING';
  loginProfileId?: string;
  defaultCredentialReferenceId?: string;
  runnerType: 'BROWSER' | 'API';
  requiredCapabilities: string[];
  productionAllowed: boolean;
  enabled: boolean;
  version: number;
  createTime: number;
  updateTime: number;
}

export type AiEnvironmentProfileRequest = Omit<
  AiEnvironmentProfile,
  'id' | 'organizationId' | 'productionAllowed' | 'createTime' | 'updateTime'
>;

export interface AiCaseExecutability {
  id: string;
  projectId: string;
  caseId: string;
  environmentProfileId: string;
  automationReadiness: 'NOT_READY' | 'PARTIAL' | 'READY';
  credentialRole?: string;
  pageObjectIds: string[];
  datasetIds: string[];
  businessFlowId?: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  missingItems: string[];
  lastCheckedAt?: number;
  checkerVersion: string;
  version: number;
}

export interface AiCaseExecutabilityConfigRequest {
  projectId: string;
  caseId: string;
  environmentProfileId: string;
  credentialRole?: string;
  pageObjectIds?: string[];
  datasetIds?: string[];
  businessFlowId?: string;
  riskLevel?: 'LOW' | 'MEDIUM' | 'HIGH';
  version?: number;
}

export interface AiExecutionObservability {
  traceId?: string;
  preflight: Record<string, any>;
  modelInvocations: Array<Record<string, any>>;
  runnerLeases: Array<Record<string, any>>;
  dataLeases: Array<Record<string, any>>;
  cleanupJobs: Array<Record<string, any>>;
  audits: Array<Record<string, any>>;
}

export interface AiEnvironmentVerifyResult {
  valid: boolean;
  reachable: boolean;
  originAllowed: boolean;
  dnsResolved: boolean;
  tlsValid: boolean;
  runnerMatched: boolean;
  checks: string[];
  traceId: string;
}

export interface AiCredentialReference {
  id: string;
  projectId: string;
  environmentId: string;
  name: string;
  credentialType: 'USERNAME_PASSWORD' | 'TOKEN' | 'API_KEY' | 'OAUTH_CLIENT';
  businessRole: string;
  providerType: 'ENV' | 'VAULT';
  secretVersion?: string;
  usernameHint?: string;
  status: string;
  expiresAt?: number;
  lastVerifiedAt?: number;
  lastVerifyStatus?: string;
  lastVerifyMessage?: string;
  enabled: boolean;
  version: number;
  createTime: number;
  updateTime: number;
}
export interface AiCredentialReferenceRequest {
  projectId: string;
  environmentId: string;
  name: string;
  credentialType: AiCredentialReference['credentialType'];
  businessRole: string;
  providerType: AiCredentialReference['providerType'];
  secretRef: string;
  usernameHint?: string;
  expiresAt?: number;
  enabled: boolean;
  version?: number;
}

export interface AiModelProfile {
  id: string;
  organizationId: string;
  projectId: string;
  name: string;
  gatewayAppCaller: string;
  logicalModelPublicId: string;
  promptPolicyId: string;
  gatewayPromptPolicyId?: string;
  requiredCapabilities: string[];
  requestTimeoutMs: number;
  maxOutputTokens: number;
  maxCostAmount?: number;
  currency: string;
  enabled: boolean;
  version: number;
  lastVerifiedAt?: number;
  lastVerifyStatus?: string;
  lastVerifyMessage?: string;
  createTime?: number;
  updateTime?: number;
}
export interface AiModelProfileRequest {
  projectId: string;
  name: string;
  gatewayAppCaller: string;
  gatewayServiceKeyRef: string;
  logicalModelPublicId: string;
  promptPolicyId: string;
  gatewayPromptPolicyId?: string;
  requiredCapabilities: string[];
  requestTimeoutMs: number;
  maxOutputTokens: number;
  maxCostAmount?: number;
  currency: string;
  enabled: boolean;
  version?: number;
}

export type TestAssetCatalogType =
  | 'CASE'
  | 'DOCUMENT'
  | 'PLAN'
  | 'DATASET'
  | 'ENVIRONMENT'
  | 'PAGE_OBJECT'
  | 'BUSINESS_FLOW'
  | 'COMMON_STEP'
  | 'API_DEFINITION'
  | 'EVIDENCE'
  | 'BUG';

export interface AiExecutionPreflightRequest {
  projectId: string;
  testPlanId?: string;
  caseIds?: string[];
  expandedCaseIds?: string[];
  expansionReasons?: Record<string, string>;
  environmentProfileId: string;
  credentialReferenceId?: string;
  modelProfileId?: string;
  promptTemplateId?: string;
  runnerType: string;
  requiredCapabilities?: string[];
  browserType?: string;
  assetRefs?: Array<{ assetType: TestAssetCatalogType; assetId: string; versionId?: string }>;
  policy?: Record<string, unknown>;
  taskOrigin: AiTaskOrigin;
  responsibleUserIds?: string[];
}
export interface AiExecutionPreflight {
  id: string;
  projectId: string;
  taskOrigin: AiTaskOrigin;
  status: 'PASSED' | 'BLOCKED';
  resolvedCaseIds: string[];
  originalScopeCount: number;
  expandedScopeCount: number;
  scopeExpansionRate: number;
  snapshotHash: string;
  blockedReason?: string;
  blockedDetail?: string;
  traceId: string;
  expiresAt: number;
  promptTemplateVersionId?: string;
}
export interface AiPromptTemplateVersion {
  id: string;
  promptTemplateId: string;
  organizationId: string;
  name: string;
  versionNo: number;
  systemTemplate: string;
  businessTemplate: string;
  variableSchema: string;
  outputSchemaVersion: string;
  contentHash: string;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  publishedAt?: number;
  createTime: number;
}
export interface AiPromptTemplateVersionRequest {
  projectId: string;
  promptTemplateId?: string;
  name: string;
  systemTemplate: string;
  businessTemplate: string;
  variableSchema: string;
  outputSchemaVersion: string;
}
export interface AiLoginProfile {
  id: string;
  projectId: string;
  environmentProfileId: string;
  name: string;
  loginType: 'FORM' | 'TOKEN';
  loginUrl: string;
  usernameLocator: string;
  passwordLocator: string;
  submitLocator: string;
  successAssertion: string;
  sessionValidation?: string;
  mfaPolicy: 'BLOCK' | 'CHECKPOINT';
  timeoutMs: number;
  enabled: boolean;
  version: number;
  createTime: number;
  updateTime: number;
}
export type AiLoginProfileRequest = Omit<AiLoginProfile, 'id' | 'createTime' | 'updateTime'>;
export interface AiPageElement {
  id?: string;
  name: string;
  strategy: 'TEST_ID' | 'ROLE' | 'LABEL' | 'PLACEHOLDER' | 'TEXT' | 'CSS';
  selectorValue: string;
  fallbackLocators?: string;
  sensitive: boolean;
  riskLevel: 'LOW' | 'HIGH';
  version?: number;
}
export interface AiPageObject {
  id: string;
  projectId: string;
  name: string;
  routePattern?: string;
  allowedOrigins: string[];
  status: 'DRAFT' | 'PUBLISHED' | 'DISABLED';
  version: number;
  elements: AiPageElement[];
  createTime: number;
  updateTime: number;
}
export interface AiPageObjectRequest {
  projectId: string;
  name: string;
  routePattern?: string;
  allowedOrigins: string[];
  status: AiPageObject['status'];
  version?: number;
  elements: AiPageElement[];
}
export interface AiBusinessFlow {
  id: string;
  projectId: string;
  name: string;
  nodes: Record<string, unknown>[];
  edges: Record<string, unknown>[];
  entryNodeId: string;
  exitConditions: Record<string, unknown>[];
  allowedActions: string[];
  status: 'DRAFT' | 'PUBLISHED' | 'DISABLED';
  version: number;
  assetVersionId?: string;
  createTime: number;
  updateTime: number;
}
export type AiBusinessFlowRequest = Omit<AiBusinessFlow, 'id' | 'assetVersionId' | 'createTime' | 'updateTime'>;

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
  taskOrigin: AiTaskOrigin;
  executorChannel: AiExecutorChannel;
  currentExecutionId?: string;
  traceId?: string;
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
  preflightId?: string;
  environmentProfileId?: string;
  environmentProfileVersion?: number;
  credentialReferenceId?: string;
  modelProfileId?: string;
  promptTemplateVersionId?: string;
  runnerId?: string;
  runnerLeaseId?: string;
  executionContract?: string;
  executionContractHash?: string;
  originalScopeCount?: number;
  expandedScopeCount?: number;
  scopeExpansionRate?: number;
  blockedReason?: string;
  blockedDetail?: string;
}

export interface AiExecutionTaskSearchRequest {
  projectId: string;
  keyword?: string;
  status?: string;
  verdict?: string;
  taskOrigin?: AiTaskOrigin;
  executorChannel?: AiExecutorChannel;
  /** @deprecated compatibility filter */
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
  isolationMode?: 'UNDECLARED' | 'PROCESS' | 'CONTAINER' | 'VM';
  maxConcurrency?: number;
  activeCount?: number;
  lastHeartbeatTime?: number;
}

export interface AiRunnerRegisterResult {
  runnerId: string;
  runnerToken: string;
  contractVersion: string;
  createTime: number;
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

export interface AiRunnerLease {
  id: string;
  taskId: string;
  executionId?: string;
  executorChannel?: AiExecutorChannel;
  runnerId?: string;
  executorType?: string;
  executorId?: string;
  leaseOwnerType?: string;
  leaseOwnerId?: string;
  attempt?: number;
  status: string;
  acceptedTime?: number;
  expireTime?: number;
  lastHeartbeatTime?: number;
  lastEventSequence?: number;
  releasedReason?: string;
  closedAt?: number;
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

export interface AiEvaluationHistory {
  id: string;
  taskId: string;
  projectId: string;
  score: number;
  comment?: string;
  evaluatedBy: string;
  evaluatedAt: number;
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
  triggerVersion?: number;
  modelProfileId: string;
  promptTemplateId?: string;
  environmentProfileId: string;
  credentialReferenceId?: string;
  runnerType: string;
  requiredCapabilities?: string;
  responsibleUserIds?: string;
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

export interface AiHumanRequest {
  id: string;
  requestKey?: string;
  taskId: string;
  projectId: string;
  requestType: string;
  title: string;
  content?: string;
  riskLevel?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'ANSWERED' | 'EXPIRED' | 'CANCELED';
  requestedBy?: string;
  response?: string;
  respondedBy?: string;
  respondedAt?: number;
  expiresAt?: number;
  createdAt?: number;
}

export interface TestAssetPage<T> {
  list: T[];
  total: number;
  current: number;
  pageSize: number;
}

export type TestAssetCreationSource = 'MANUAL' | 'AI' | 'IMPORT' | 'SYNC' | 'AUTOMATION' | 'UNKNOWN';

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
  creationSource?: TestAssetCreationSource;
  categoryId?: string;
  categoryName?: string;
  categoryPath?: string;
}

export interface TestAssetCatalogItem {
  id: string;
  projectId: string;
  assetType: TestAssetCatalogType;
  name: string;
  category?: string;
  status?: string;
  summary?: string;
  owner?: string;
  updateTime?: number;
  sourceVersion?: string;
  relatedId?: string;
  assetVersionId?: string;
  assetVersionNo?: number;
  contentHash?: string;
  creationSource?: TestAssetCreationSource;
  categoryId?: string;
  categoryName?: string;
  categoryPath?: string;
  sourceReferenceType?: string;
  sourceReferenceId?: string;
}

export interface TestAssetCategory {
  id: string;
  parentId?: string;
  name: string;
  path: string;
  level: number;
  sort?: number;
  assetCount?: number;
  children?: TestAssetCategory[];
}

export interface TestAssetMetadata {
  assetType: string;
  assetId: string;
  creationSource: TestAssetCreationSource;
  categoryId?: string;
  categoryName?: string;
  categoryPath?: string;
  sourceReferenceType?: string;
  sourceReferenceId?: string;
  createdByActorType?: string;
  createdByActorId?: string;
  createTime?: number;
  aiGenerationId?: string;
  aiProvider?: string;
  aiModelId?: string;
  aiModelName?: string;
  promptTemplateVersion?: string;
  sourceDocumentId?: string;
  generationTime?: number;
  generationInitiator?: string;
  reviewStatus?: string;
  reviewedBy?: string;
  reviewedAt?: number;
  publishedAt?: number;
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
  creationSource?: TestAssetCreationSource;
  categoryId?: string;
  categoryName?: string;
  categoryPath?: string;
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
  sourceCreationSource?: TestAssetCreationSource;
  sourceCategoryPath?: string;
  targetCreationSource?: TestAssetCreationSource;
  targetCategoryPath?: string;
}

export interface AiExecutionEvent {
  id?: string;
  taskId?: string;
  executionId?: string;
  leaseId?: string;
  caseId?: string;
  stepId?: string;
  sequence: number;
  eventTime?: number;
  level: string;
  eventType: string;
  actorType?: string;
  actorId?: string;
  toolName?: string;
  requestId?: string;
  traceId?: string;
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
  assetRefs?: Array<{ assetType: TestAssetCatalogType; assetId: string; versionId?: string }>;
  preflightId: string;
  environmentProfileId: string;
  credentialReferenceId?: string;
  modelProfileId?: string;
  promptTemplateVersionId?: string;
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
  modelProfileId: string;
  promptTemplateId?: string;
  environmentProfileId: string;
  credentialReferenceId?: string;
  runnerType: string;
  requiredCapabilities?: string[];
  policy?: Record<string, unknown>;
  evidencePolicy?: Record<string, unknown>;
  notificationPolicy?: Record<string, unknown>;
  responsibleUserIds: string[];
  taskTemplate: AiExecutionCreateParams & {
    name?: string;
    objective?: string;
    projectWide?: boolean;
    confirmed?: boolean;
  };
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

export function listAiEnvironmentProfiles(projectId: string) {
  return MSR.get<AiEnvironmentProfile[]>({ url: AiEnvironmentProfilesUrl, params: { projectId } });
}

export function checkAiCaseExecutability(data: { projectId: string; caseIds: string[]; environmentProfileId: string }) {
  return MSR.post<AiCaseExecutability[]>({ url: AiCaseExecutabilityCheckUrl, data });
}

export function saveAiCaseExecutabilityConfig(data: AiCaseExecutabilityConfigRequest) {
  return MSR.post<AiCaseExecutability>({ url: AiCaseExecutabilityConfigUrl, data });
}

export function createAiEnvironmentProfile(data: AiEnvironmentProfileRequest) {
  return MSR.post<AiEnvironmentProfile>({ url: AiEnvironmentProfilesUrl, data });
}

export function updateAiEnvironmentProfile(id: string, data: AiEnvironmentProfileRequest) {
  return MSR.put<AiEnvironmentProfile>({ url: AiEnvironmentProfileUrl(id), data });
}

export function verifyAiEnvironmentProfile(id: string, data?: { targetUrl?: string; runnerId?: string }) {
  return MSR.post<AiEnvironmentVerifyResult>({ url: AiEnvironmentProfileVerifyUrl(id), data: data ?? {} });
}
export function enableAiEnvironmentProfile(id: string) {
  return MSR.post<AiEnvironmentProfile>({ url: AiEnvironmentProfileEnableUrl(id) });
}
export function disableAiEnvironmentProfile(id: string) {
  return MSR.post<AiEnvironmentProfile>({ url: AiEnvironmentProfileDisableUrl(id) });
}

export function listAiCredentialReferences(projectId: string, environmentId?: string) {
  return MSR.get<AiCredentialReference[]>({ url: AiCredentialReferencesUrl, params: { projectId, environmentId } });
}
export function createAiCredentialReference(data: AiCredentialReferenceRequest) {
  return MSR.post<AiCredentialReference>({ url: AiCredentialReferencesUrl, data });
}
export function updateAiCredentialReference(id: string, data: AiCredentialReferenceRequest) {
  return MSR.put<AiCredentialReference>({ url: AiCredentialReferenceUrl(id), data });
}
export function verifyAiCredentialReference(id: string) {
  return MSR.post<{ valid: boolean; status: string; message: string; traceId: string }>({
    url: AiCredentialReferenceVerifyUrl(id),
  });
}
export function enableAiCredentialReference(id: string) {
  return MSR.post<AiCredentialReference>({ url: AiCredentialReferenceEnableUrl(id) });
}
export function disableAiCredentialReference(id: string) {
  return MSR.post<AiCredentialReference>({ url: AiCredentialReferenceDisableUrl(id) });
}

export function listAiModelProfiles(projectId: string) {
  return MSR.get<AiModelProfile[]>({ url: AiModelProfilesUrl, params: { projectId } });
}
export function createAiModelProfile(data: AiModelProfileRequest) {
  return MSR.post<AiModelProfile>({ url: AiModelProfilesUrl, data });
}
export function updateAiModelProfile(id: string, data: AiModelProfileRequest) {
  return MSR.put<AiModelProfile>({ url: AiModelProfileUrl(id), data });
}
export function verifyAiModelProfile(id: string) {
  return MSR.post<Record<string, unknown>>({ url: AiModelProfileVerifyUrl(id) });
}
export function getAiModelProfileHealth(id: string) {
  return MSR.get<Record<string, unknown>>({ url: AiModelProfileHealthUrl(id) });
}
export function getAiModelProfileCapabilities(id: string) {
  return MSR.get<Record<string, unknown>>({ url: AiModelProfileCapabilitiesUrl(id) });
}
export function enableAiModelProfile(id: string) {
  return MSR.post<AiModelProfile>({ url: AiModelProfileEnableUrl(id) });
}
export function disableAiModelProfile(id: string) {
  return MSR.post<AiModelProfile>({ url: AiModelProfileDisableUrl(id) });
}
export function preflightAiExecution(data: AiExecutionPreflightRequest) {
  return MSR.post<AiExecutionPreflight>({ url: AiExecutionPreflightUrl, data });
}
export function getAiExecutionPreflight(id: string) {
  return MSR.get<AiExecutionPreflight>({ url: AiExecutionPreflightDetailUrl(id) });
}
export function listAiPromptTemplateVersions(projectId: string, promptTemplateId?: string) {
  return MSR.get<AiPromptTemplateVersion[]>({ url: AiPromptTemplatesUrl, params: { projectId, promptTemplateId } });
}
export function createAiPromptTemplateVersion(data: AiPromptTemplateVersionRequest) {
  return MSR.post<AiPromptTemplateVersion>({ url: AiPromptTemplatesUrl, data });
}
export function publishAiPromptTemplateVersion(id: string, projectId: string) {
  return MSR.post<AiPromptTemplateVersion>({ url: AiPromptTemplatePublishUrl(id), params: { projectId } });
}
export function previewAiPromptTemplateVersion(id: string, projectId: string, data: Record<string, unknown>) {
  return MSR.post<Record<string, unknown>>({ url: AiPromptTemplatePreviewUrl(id), params: { projectId }, data });
}
export function rollbackAiPromptTemplateVersion(id: string, projectId: string) {
  return MSR.post<AiPromptTemplateVersion>({ url: AiPromptTemplateRollbackUrl(id), params: { projectId } });
}
export function getAiModelInvocation(id: string, projectId: string) {
  return MSR.get<Record<string, unknown>>({ url: AiModelInvocationUrl(id), params: { projectId } });
}
export function getAiModelUsage(projectId: string, from?: number, to?: number) {
  return MSR.get<Record<string, unknown>>({ url: AiModelUsageUrl, params: { projectId, from, to } });
}
export function listAiLoginProfiles(projectId: string) {
  return MSR.get<AiLoginProfile[]>({ url: AiLoginProfilesUrl, params: { projectId } });
}
export function createAiLoginProfile(data: AiLoginProfileRequest) {
  return MSR.post<AiLoginProfile>({ url: AiLoginProfilesUrl, data });
}
export function updateAiLoginProfile(id: string, data: AiLoginProfileRequest) {
  return MSR.put<AiLoginProfile>({ url: AiLoginProfileUrl(id), data });
}
export function enableAiLoginProfile(id: string) {
  return MSR.post<AiLoginProfile>({ url: AiLoginProfileEnableUrl(id) });
}
export function disableAiLoginProfile(id: string) {
  return MSR.post<AiLoginProfile>({ url: AiLoginProfileDisableUrl(id) });
}
export function listAiPageObjects(projectId: string) {
  return MSR.get<AiPageObject[]>({ url: AiPageObjectsUrl, params: { projectId } });
}
export function createAiPageObject(data: AiPageObjectRequest) {
  return MSR.post<AiPageObject>({ url: AiPageObjectsUrl, data });
}
export function updateAiPageObject(id: string, data: AiPageObjectRequest) {
  return MSR.put<AiPageObject>({ url: AiPageObjectUrl(id), data });
}
export function listAiBusinessFlows(projectId: string) {
  return MSR.get<AiBusinessFlow[]>({ url: AiBusinessFlowsUrl, params: { projectId } });
}
export function createAiBusinessFlow(data: AiBusinessFlowRequest) {
  return MSR.post<AiBusinessFlow>({ url: AiBusinessFlowsUrl, data });
}
export function updateAiBusinessFlow(id: string, data: AiBusinessFlowRequest) {
  return MSR.put<AiBusinessFlow>({ url: AiBusinessFlowUrl(id), data });
}

export function createAiExecutionTask(data: AiExecutionCreateParams) {
  return MSR.post<AiExecutionTask>({ url: AiExecutionTaskUrl, data });
}

export function getAiExecutionTask(id: string) {
  return MSR.get<AiExecutionTask>({ url: AiExecutionTaskByIdUrl(id) });
}

export function searchAiExecutionTasks(data: AiExecutionTaskSearchRequest) {
  return MSR.post<AiExecutionTaskSearchResponse>({ url: AiExecutionTaskSearchUrl, data });
}

export function getAiExecutionEvents(id: string, params?: { cursor?: number; limit?: number }) {
  return MSR.get<AiExecutionEventsResponse>({
    url: AiExecutionTaskEventsUrl(id),
    params: {
      cursor: params?.cursor ?? 0,
      limit: params?.limit ?? 100,
    },
  });
}

export function getAiExecutionArtifacts(id: string) {
  return MSR.get<AiExecutionArtifact[]>({ url: AiExecutionTaskArtifactsUrl(id) });
}

export function getAiHumanRequests(id: string) {
  return MSR.get<AiHumanRequest[]>({ url: AiExecutionTaskHumanRequestsUrl(id) });
}

export function respondAiHumanRequest(
  taskId: string,
  requestId: string,
  action: 'APPROVE' | 'REJECT' | 'ANSWER' | 'CANCEL',
  response?: string
) {
  return MSR.post<AiHumanRequest>({
    url: AiExecutionHumanRequestRespondUrl(taskId, requestId),
    data: { action, response },
  });
}

export function confirmAiExecutionTask(id: string, reason?: string) {
  return MSR.post<AiExecutionTask>({ url: AiExecutionTaskConfirmUrl(id), data: { reason } });
}

export function cancelAiExecutionTask(id: string, reason?: string) {
  return MSR.post<AiExecutionTask>({ url: AiExecutionTaskCancelUrl(id), data: { reason } });
}

export function loginReadyAiExecutionTask(id: string, reason?: string) {
  return MSR.post<AiExecutionTask>({ url: AiExecutionTaskLoginReadyUrl(id), data: { reason } });
}

export function pauseAiExecutionTask(id: string, reason?: string) {
  return MSR.post<AiExecutionTask>({ url: AiExecutionTaskPauseUrl(id), data: { reason } });
}

export function retryAiExecutionTask(id: string, reason?: string) {
  return MSR.post<AiExecutionTask>({ url: AiExecutionTaskRetryUrl(id), data: { reason } });
}

export function getAiRunners() {
  return MSR.get<AiRunner[]>({ url: AiRunnersUrl });
}

export function registerAiRunner(data: {
  name: string;
  runnerVersion: string;
  contractVersion: string;
  operatingSystem?: string;
  browserCapabilities?: string;
  environmentLabels?: string;
  isolationMode: 'UNDECLARED' | 'PROCESS' | 'CONTAINER' | 'VM';
  maxConcurrency: number;
}) {
  return MSR.post<AiRunnerRegisterResult>({ url: AiRunnerRegisterUrl, data });
}

export function getAiExecutionOperations() {
  return MSR.get<AiExecutionOperations>({ url: AiExecutionOperationsSummaryUrl });
}

export function getAiExecutionObservability(taskId: string) {
  return MSR.get<AiExecutionObservability>({ url: AiExecutionTaskObservabilityUrl(taskId) });
}

export function getAiExecutionMetrics(from?: number, to?: number) {
  return MSR.get<Array<Record<string, any>>>({ url: AiExecutionMetricsUrl, params: { from, to } });
}
export interface AiExecutionAlert {
  id: string;
  projectId?: string;
  taskId?: string;
  alertType: string;
  severity: string;
  message: string;
  traceId?: string;
  status: string;
  acknowledgedBy?: string;
  acknowledgedAt?: number;
  createTime: number;
}
export function listAiExecutionAlerts(projectId: string, status?: string) {
  return MSR.get<AiExecutionAlert[]>({ url: AiExecutionAlertsUrl, params: { projectId, status } });
}
export function acknowledgeAiExecutionAlert(projectId: string, id: string) {
  return MSR.post<void>({ url: acknowledgeAiExecutionAlertUrl(id), params: { projectId } });
}

export function getAiExecutionLeases(status?: string, limit = 50) {
  return MSR.get<AiRunnerLease[]>({ url: AiExecutionOperationsLeasesUrl, params: { status, limit } });
}

export function getAiExecutionEvaluations(
  projectId: string,
  current?: number,
  pageSize?: number,
  filters?: { operationalStatus?: string; businessVerdict?: string; executorType?: string }
) {
  return MSR.get<AiEvaluationPage>({
    url: AiExecutionEvaluationsUrl,
    params: { projectId, current: current ?? 1, pageSize: pageSize ?? 20, ...filters },
  });
}

export function getAiEvaluationSummary(projectId: string) {
  return MSR.get<AiEvaluationSummary[]>({ url: AiExecutionEvaluationSummaryUrl, params: { projectId } });
}

export function manualEvaluateAiExecution(taskId: string, score: number, comment?: string) {
  return MSR.post<AiExecutionEvaluation>({
    url: AiExecutionEvaluationManualUrl(taskId),
    data: { score, comment },
  });
}

export function getAiEvaluationHistory(taskId: string, limit = 50) {
  return MSR.get<AiEvaluationHistory[]>({
    url: AiExecutionEvaluationHistoryUrl(taskId),
    params: { limit },
  });
}

export function listAiTaskTriggers(projectId: string) {
  return MSR.get<AiTaskTrigger[]>({ url: AiExecutionTriggersUrl, params: { projectId } });
}

export function createAiTaskTrigger(data: AiTaskTriggerRequest) {
  return MSR.post<AiTaskTrigger>({ url: AiExecutionTriggersUrl, data });
}

export function updateAiTaskTrigger(id: string, data: AiTaskTriggerRequest) {
  return MSR.put<AiTaskTrigger>({ url: AiExecutionTriggerUrl(id), data });
}

export function fireAiTaskTrigger(id: string) {
  return MSR.post<AiTaskTriggerHistory>({ url: AiExecutionTriggerFireUrl(id) });
}

export function rotateAiTaskTriggerSecret(id: string) {
  return MSR.post<AiTaskTrigger>({ url: AiExecutionTriggerRotateSecretUrl(id) });
}

export function listAiTaskTriggerHistory(id: string, limit = 50) {
  return MSR.get<AiTaskTriggerHistory[]>({ url: AiExecutionTriggerHistoryUrl(id), params: { limit } });
}

export function pageTestAssetDocuments(params: {
  projectId: string;
  parseStatus?: string;
  keyword?: string;
  creationSources?: TestAssetCreationSource[];
  categoryId?: string;
  includeDescendants?: boolean;
  current?: number;
  pageSize?: number;
}) {
  return MSR.get<TestAssetPage<TestAssetDocument>>({ url: TestAssetDocumentsUrl, params });
}

export function pageTestAssetCatalog(params: {
  projectId: string;
  assetType: TestAssetCatalogType;
  keyword?: string;
  status?: string;
  updatedAfter?: number;
  creationSources?: TestAssetCreationSource[];
  categoryId?: string;
  includeDescendants?: boolean;
  current?: number;
  pageSize?: number;
}) {
  return MSR.get<TestAssetPage<TestAssetCatalogItem>>({ url: TestAssetCatalogUrl, params });
}

export function listTestAssetCategories(keyword?: string) {
  return MSR.get<TestAssetCategory[]>({ url: TestAssetCategoryTreeUrl, params: { keyword } });
}

export function createTestAssetCategory(data: { name: string; parentId?: string }) {
  return MSR.post<TestAssetCategory>({ url: TestAssetCategoriesUrl, data });
}

export function updateTestAssetCategory(id: string, data: { name: string; parentId?: string }) {
  return MSR.put<TestAssetCategory>({ url: `${TestAssetCategoriesUrl}/${id}`, data });
}

export function reorderTestAssetCategories(ids: string[]) {
  return MSR.put({ url: TestAssetCategoryReorderUrl, data: { ids } });
}

export function deleteTestAssetCategory(id: string, data: { strategy?: string; targetCategoryId?: string }) {
  return MSR.delete({ url: `${TestAssetCategoriesUrl}/${id}`, data });
}

export function getTestAssetMetadata(projectId: string, assetType: string, assetId: string) {
  return MSR.get<TestAssetMetadata>({ url: TestAssetMetadataUrl(assetType, assetId), params: { projectId } });
}

export function assignTestAssetCategory(projectId: string, assetType: string, assetId: string, categoryId?: string) {
  return MSR.put<TestAssetMetadata>({
    url: TestAssetCategoryAssignUrl(assetType, assetId),
    data: { projectId, categoryId },
  });
}

export function batchAssignTestAssetCategory(data: {
  items: Array<{ projectId: string; assetType: string; assetId: string }>;
  categoryId?: string;
}) {
  return MSR.post<Array<{ assetId: string; success: boolean; message: string }>>({
    url: TestAssetBatchCategoryAssignUrl,
    data,
  });
}

export function governTestAssetSource(data: {
  projectId: string;
  assetType: string;
  assetId: string;
  creationSource: TestAssetCreationSource;
  evidence: string;
  sourceReferenceType?: string;
  sourceReferenceId?: string;
}) {
  return MSR.post<TestAssetMetadata>({ url: TestAssetSourceGovernanceUrl, data });
}

export function getTestAssetCatalogDetail(projectId: string, assetType: TestAssetCatalogType, assetId: string) {
  return MSR.get<TestAssetCatalogItem>({
    url: TestAssetCatalogDetailUrl(assetType, assetId),
    params: { projectId },
  });
}

export function publishTestAssetCatalog(projectId: string, assetType: TestAssetCatalogType, assetId: string) {
  return MSR.post<TestAssetCatalogItem>({ url: TestAssetCatalogPublishUrl(assetType, assetId), params: { projectId } });
}

export function downloadAiExecutionArtifact(taskId: string, artifactId: string) {
  return MSR.get<BlobPart>(
    { url: AiExecutionArtifactDownloadUrl(taskId, artifactId), responseType: 'blob' },
    { isTransformResponse: false }
  );
}

export function pageTestAssetVersions(params: {
  projectId: string;
  assetType?: string;
  assetId?: string;
  keyword?: string;
  creationSources?: TestAssetCreationSource[];
  categoryId?: string;
  includeDescendants?: boolean;
  current?: number;
  pageSize?: number;
}) {
  return MSR.get<TestAssetPage<TestAssetVersion>>({ url: TestAssetVersionsUrl, params });
}

export function deprecateTestAssetVersion(projectId: string, version: TestAssetVersion) {
  return MSR.post<TestAssetVersion>({
    url: TestAssetVersionDeprecateUrl(version.id),
    params: { projectId, assetType: version.assetType, assetId: version.assetId },
  });
}

export function pageTestAssetRelations(params: {
  projectId: string;
  assetType?: string;
  assetId?: string;
  relationType?: string;
  keyword?: string;
  creationSources?: TestAssetCreationSource[];
  categoryId?: string;
  includeDescendants?: boolean;
  current?: number;
  pageSize?: number;
}) {
  return MSR.get<TestAssetPage<TestAssetRelation>>({ url: TestAssetRelationsUrl, params });
}
