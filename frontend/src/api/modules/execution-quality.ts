import MSR from '@/api/http/index';

export interface QualityPolicyDocument {
  schemaVersion: 'quality-policy.v1';
  name: string;
  rules: Array<{
    ruleId: string;
    parameters: {
      requiredRoles?: string[];
      allowedMimeTypes?: string[];
      maxArtifactBytes?: number;
      allowedOperators?: string[];
      expectedSource?: string;
    };
  }>;
}
export interface QualityPolicy {
  id: string;
  projectId: string;
  versionNo: number;
  status: 'DRAFT' | 'PUBLISHED';
  rulesJson: string;
  contentHash: string;
  rowVersion: number;
  createdBy: string;
  createdAt: number;
  publishedBy: string | null;
  publishedAt: number | null;
  changeReason: string | null;
}
export interface PolicyValidation {
  valid: boolean;
  errors: Array<{ path: string; message: string }>;
  normalizedJson: string | null;
  contentHash: string | null;
}
export interface PolicyPublication {
  previousPolicyId: string | null;
  previousHash: string | null;
  publishedHash: string;
  actor: string;
  publishedAt: number;
  reason: string;
}
export interface PolicySchema {
  properties: {
    rules: {
      items: {
        oneOf: Array<{
          properties: {
            ruleId: { enum: string[] };
            parameters: { properties: Record<string, { maximum?: number; items?: { enum: string[] } }> };
          };
        }>;
      };
    };
  };
}
const base = '/quality/policies';
const options = { errorMessageMode: 'none' as const };
export function listQualityPolicies(projectId: string, page = 1, pageSize = 20) {
  return MSR.get<{
    items: QualityPolicy[];
    currentPolicyId: string | null;
    currentPolicy: QualityPolicy | null;
    total: number;
  }>({ url: base, params: { projectId, page, pageSize } }, options);
}
export function getQualityPolicy(id: string, projectId: string) {
  return MSR.get<{ policy: QualityPolicy; publication: PolicyPublication | null }>(
    { url: `${base}/${id}`, params: { projectId } },
    options
  );
}
export function getQualityPolicySchema(projectId: string) {
  return MSR.get<PolicySchema>({ url: '/quality/policy-schema', params: { projectId } }, options);
}
export function validateQualityPolicy(projectId: string, rulesJson: string) {
  return MSR.post<PolicyValidation>({ url: `${base}/validate`, data: { projectId, rulesJson } }, options);
}
export function saveQualityPolicy(projectId: string, rulesJson: string, policy?: QualityPolicy) {
  const data = { projectId, rulesJson, expectedVersion: policy?.rowVersion };
  return policy
    ? MSR.put<QualityPolicy>({ url: `${base}/${policy.id}/draft`, data }, options)
    : MSR.post<QualityPolicy>({ url: base, data }, options);
}
export function publishQualityPolicy(policy: QualityPolicy, currentPolicyId: string | null, changeReason: string) {
  return MSR.post<QualityPolicy>(
    {
      url: `${base}/${policy.id}/publish`,
      data: {
        projectId: policy.projectId,
        expectedVersion: policy.rowVersion,
        expectedCurrentPolicyId: currentPolicyId,
        changeReason,
      },
    },
    options
  );
}
