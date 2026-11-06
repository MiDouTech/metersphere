import MSR from '@/api/http/index';

// A denied relation operation stays in its current dialog; authentication and server authorization remain unchanged.
const relationRequestOptions = { handleForbiddenLocally: true };

export type RelationSide = 'plan' | 'review';
export interface RelationSummary {
  canPair?: boolean;
  legacyBlocked?: boolean;
  relationStatus: 'NONE' | 'ACTIVE' | 'RESTRICTED';
  restricted: boolean;
  relationId?: string;
  generation?: string;
  target?: { id: string; num: number; name: string; status: string };
  sync?: {
    state: string;
    sourceVersion: number;
    appliedVersion: number;
    blockers: string[];
    safeError?: string;
    delayed?: boolean;
    attempts: number;
  };
  canUnlink?: boolean;
  canReconfirm?: boolean;
  scopeMessage?: string;
}
export interface RelationListSummary {
  legacyBlocked?: boolean;
  relationStatus: 'NONE' | 'ACTIVE' | 'RESTRICTED' | 'INVALID';
  restricted: boolean;
  target?: { id: string; num: number; name: string; status: string };
  syncState?: string;
  sourceVersion?: number;
  appliedVersion?: number;
}
export interface RelationFrozenFrame {
  available: boolean;
  frozenAt?: number;
  objectState?: string;
  statistics?: Record<string, unknown>;
  members?: { case_id: string; case_name?: string; case_num?: number; epoch?: number; status?: string }[];
  total?: number;
  sourceVersion?: number;
  appliedVersion?: number;
}
export const getPlanReviewFrozen = (side: RelationSide, id: string, page: number) =>
  MSR.get<RelationFrozenFrame>(
    {
      url: `/plan-review/frozen/${side}/${encodeURIComponent(id)}`,
      params: { page, size: 20 },
    },
    relationRequestOptions
  );
export interface RelationPreview {
  sourceVersion?: number;
  previewId: string;
  generation: string;
  expiresAt: number;
  message: string;
  difference: { added: string[]; removed: string[]; retained: string[]; reentered: string[] };
}
export interface RelationCandidate {
  id: string;
  num: number;
  name: string;
  status: string;
}
export interface LinkedReviewRecoveryPreview extends RelationPreview {
  operationId: string;
  requestKey: string;
  projectId: string;
  planId: string;
  settingsSummary: { name: string; reviewPassRule: string; reviewerCount: number };
}
export interface RelationOperation {
  newReviewPreview?: LinkedReviewRecoveryPreview;
  revision: number;
  canResume?: boolean;
  canTerminate?: boolean;
  canRequestTakeover?: boolean;
  reviewId?: string;
  planId?: string;
  operationId: string;
  relationId?: string;
  generation?: string;
  stage: string;
  state?: string;
}
export interface PlanReviewCopyOutcome {
  requestId?: string;
  operationId?: string;
  operationCount: number;
  createdCount: number;
  failedCount: number;
  items: {
    sourceId: string;
    sourceType: string;
    sourceName?: string;
    sourceNum?: number;
    operationId?: string;
    planId?: string;
    reviewId?: string;
    stage: string;
    state: string;
    syncState?: string;
    created: boolean;
    safeReason?: string;
  }[];
}
export interface OperationTakeover {
  credentialToWeb?: boolean;
  previewId: string;
  operationId: string;
  revision: number;
  oldActor: string;
  newActor: string;
  expiresAt: number;
  approved: boolean;
  consumed: boolean;
  stale: boolean;
  canApprove: boolean;
  canTakeover: boolean;
  blockedReason?: string;
}
export const getOperationTakeovers = (projectId: string) =>
  MSR.get<{ items: OperationTakeover[] }>(
    { url: '/plan-review/operations/takeovers', params: { projectId } },
    relationRequestOptions
  );
export const previewOperationTakeover = (
  id: string,
  data: { projectId: string; expectedRevision: number; newActor: string }
) =>
  MSR.post({ url: `/plan-review/operations/${encodeURIComponent(id)}/takeover-preview`, data }, relationRequestOptions);
export const approveOperationTakeover = (data: { projectId: string; previewId: string; acknowledged: boolean }) =>
  MSR.post({ url: '/plan-review/operations/takeover-approve', data }, relationRequestOptions);
export const confirmOperationTakeover = (data: {
  projectId: string;
  approvalId: string;
  expectedRevision: number;
  requestKey: string;
}) => MSR.post({ url: '/plan-review/operations/takeover', data }, relationRequestOptions);
export const getNewReviewPlanCandidates = (projectId: string, keyword: string, page: number) =>
  MSR.get<{ items: RelationCandidate[]; total: number }>(
    {
      url: '/plan-review/new-review/candidates',
      params: { projectId, keyword, page, size: 20 },
    },
    relationRequestOptions
  );
export const previewNewLinkedReview = (data: { planId: string; requestKey: string; draft: Record<string, unknown> }) =>
  MSR.post<RelationPreview & { operationId: string }>(
    { url: '/plan-review/new-review/preview', data },
    relationRequestOptions
  );
export const recoverNewLinkedReviewPreview = (projectId: string, operationId: string) =>
  MSR.post<LinkedReviewRecoveryPreview>(
    {
      url: '/plan-review/new-review/recover-preview',
      data: { projectId, operationId },
    },
    relationRequestOptions
  );
export const confirmNewLinkedReview = (data: { previewId: string; requestKey: string; acknowledged: boolean }) =>
  MSR.post<RelationOperation>({ url: '/plan-review/new-review/confirm', data }, relationRequestOptions);
export const getPlanReview = (side: RelationSide, id: string) =>
  MSR.get<RelationSummary>({ url: `/plan-review/${side}/${encodeURIComponent(id)}` }, relationRequestOptions);
export const findPlanReviewConfirmation = (projectId: string, requestKey: string) =>
  MSR.get<{ items: RelationOperation[] }>(
    {
      url: '/plan-review/operations',
      params: {
        projectId,
        sourceKind: 'WEB',
        operationType: 'CONFIRM',
        requestKey,
        page: 1,
        size: 1,
      },
    },
    relationRequestOptions
  );
export const getPlanReviewCandidates = (data: {
  side: RelationSide;
  id: string;
  keyword: string;
  page: number;
  size: number;
}) =>
  MSR.post<{ items: RelationCandidate[]; total: number }>(
    { url: '/plan-review/candidates', data },
    relationRequestOptions
  );
export const previewPlanReview = (data: { planId: string; reviewId: string; purpose: string }) =>
  MSR.post<RelationPreview>({ url: '/plan-review/preview', data }, relationRequestOptions);
export const confirmPlanReview = (data: { previewId: string; requestKey: string; acknowledged: boolean }) =>
  MSR.post<RelationOperation>({ url: '/plan-review/confirm', data }, relationRequestOptions);
export const unlinkPlanReview = (data: { relationId: string; expectedGeneration: string; requestKey: string }) =>
  MSR.post({ url: '/plan-review/unlink', data }, relationRequestOptions);
export const getReviewOpinionEpochs = (id: string) =>
  MSR.get<Record<string, number>>(
    { url: `/plan-review/opinion-epochs/${encodeURIComponent(id)}` },
    relationRequestOptions
  );
export const getPlanReviewGrant = (projectId: string) =>
  MSR.get<{ revision: number; enabled: boolean }>(
    { url: '/plan-review/grant', params: { projectId } },
    relationRequestOptions
  );
export const setPlanReviewGrant = (data: { projectId: string; expectedRevision: number; enabled: boolean }) =>
  MSR.post<{ revision: number; enabled: boolean }>({ url: '/plan-review/grant', data }, relationRequestOptions);
export const getPlanReviewOperations = (
  projectId: string,
  allProject?: boolean,
  relation?: { relationId?: string; generation?: string }
) =>
  MSR.get<{ items: RelationOperation[] }>(
    {
      url: '/plan-review/operations',
      params: { projectId, page: 1, size: 20, allProject, ...relation },
    },
    relationRequestOptions
  );
export const resumePlanReviewOperation = (
  id: string,
  data: { projectId: string; expectedRevision: number; requestKey: string }
) =>
  MSR.post<RelationOperation>(
    { url: `/plan-review/operations/${encodeURIComponent(id)}/resume`, data },
    relationRequestOptions
  );
export const terminatePlanReviewOperation = (
  id: string,
  data: { projectId: string; expectedRevision: number; requestKey: string }
) =>
  MSR.post<RelationOperation>(
    { url: `/plan-review/operations/${encodeURIComponent(id)}/terminate`, data },
    relationRequestOptions
  );
export interface RelationDifferenceItem {
  id: string;
  num?: number;
  name: string;
  reentered: boolean;
}
export const getPlanReviewPreviewItems = (id: string, kind: string, page: number) =>
  MSR.get<{ items: RelationDifferenceItem[]; total: number }>(
    {
      url: `/plan-review/preview/${encodeURIComponent(id)}/items`,
      params: { kind, page, size: 20 },
    },
    relationRequestOptions
  );
export interface RelationHistoryItem {
  caseId: string;
  epoch: number;
  exitedAt: number;
  reason: string;
  case_snapshot: unknown;
  review_snapshot: unknown;
  history_snapshot: unknown;
}
export const getPlanReviewHistory = (id: string, page: number) =>
  MSR.get<{ items: RelationHistoryItem[]; total: number }>(
    {
      url: `/plan-review/history/${encodeURIComponent(id)}`,
      params: { page, size: 20 },
    },
    relationRequestOptions
  );
export interface RelationLifecyclePreview {
  previewId: string;
  action: string;
  objectState: string;
  sourceVersion: number;
  appliedVersion: number;
  expiresAt: number;
  message: string;
}
export const previewPlanReviewLifecycle = (data: { side: RelationSide; id: string; action: string }) =>
  MSR.post<RelationLifecyclePreview>({ url: '/plan-review/lifecycle/preview', data }, relationRequestOptions);
export const confirmPlanReviewLifecycle = (data: { previewId: string; acknowledged: boolean }) =>
  MSR.post({ url: '/plan-review/lifecycle/confirm', data }, relationRequestOptions);
export interface PlanReviewSettings {
  name: string;
  moduleId: string;
  reviewers: string[];
  reviewPassRule: 'SINGLE' | 'MULTIPLE';
  followPlanName: boolean;
}
export interface PlanReviewNoticeDelivery {
  type: string;
  state: string;
  attempts: number;
  retry_round: number;
  next_retry_at?: number;
  readable_at?: number;
  eligibility: string;
  safe_error?: string;
}
export interface PlanReviewFault {
  id: string;
  reason: string;
  fault_round: number;
  detected_at: number;
  recovered_at?: number;
  ended_at?: number;
  noEligibleRecipients: boolean;
  deliveries: PlanReviewNoticeDelivery[];
}
export const getPlanReviewNotices = (projectId: string, side: RelationSide, objectId: string) =>
  MSR.get<{ items: PlanReviewFault[]; canRecover: boolean }>(
    {
      url: '/plan-review/notices/object',
      params: { projectId, side, objectId },
    },
    relationRequestOptions
  );
export const previewPlanReviewNoticeRetry = (projectId: string, faultId: string) =>
  MSR.post<{ previewId: string; eligibleCount: number; ineligibleCount: number; expiresAt: number }>(
    {
      url: '/plan-review/notices/preview',
      data: { projectId, faultId },
    },
    relationRequestOptions
  );
export const confirmPlanReviewNoticeRetry = (projectId: string, previewId: string) =>
  MSR.post(
    { url: '/plan-review/notices/confirm', data: { projectId, previewId, acknowledged: true } },
    relationRequestOptions
  );

export const getPlanReviewReadOnlyDifference = (
  side: RelationSide,
  id: string,
  generation: string,
  kind: string,
  page: number
) =>
  MSR.get<{ readonly: boolean; generation: string; items: RelationDifferenceItem[]; total: number }>(
    {
      url: `/plan-review/difference/${side}/${encodeURIComponent(id)}`,
      params: { generation, kind, page, size: 20 },
    },
    relationRequestOptions
  );
