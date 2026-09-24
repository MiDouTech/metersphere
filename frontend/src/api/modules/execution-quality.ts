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
  scope: 'SYSTEM';
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
  traceId: string;
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
const base = '/system/quality/policies';
const options = { errorMessageMode: 'none' as const, handleForbiddenLocally: true };
export function listQualityPolicies(page = 1, pageSize = 20) {
  return MSR.get<{
    items: QualityPolicy[];
    currentPolicyId: string | null;
    currentPolicy: QualityPolicy | null;
    total: number;
  }>({ url: base, params: { page, pageSize } }, options);
}
export function getQualityPolicy(id: string) {
  return MSR.get<{ policy: QualityPolicy; publication: PolicyPublication | null }>({ url: `${base}/${id}` }, options);
}
export function getQualityPolicySchema() {
  return MSR.get<PolicySchema>({ url: '/system/quality/policy-schema' }, options);
}
export function validateQualityPolicy(rulesJson: string) {
  return MSR.post<PolicyValidation>({ url: `${base}/validate`, data: { rulesJson } }, options);
}
export function saveQualityPolicy(rulesJson: string, policy?: QualityPolicy) {
  const data = { rulesJson, expectedVersion: policy?.rowVersion };
  return policy
    ? MSR.put<QualityPolicy>({ url: `${base}/${policy.id}/draft`, data }, options)
    : MSR.post<QualityPolicy>({ url: base, data }, options);
}
export function publishQualityPolicy(policy: QualityPolicy, currentPolicyId: string | null, changeReason: string) {
  return MSR.post<QualityPolicy>(
    {
      url: `${base}/${policy.id}/publish`,
      data: {
        expectedVersion: policy.rowVersion,
        expectedCurrentPolicyId: currentPolicyId,
        changeReason,
      },
    },
    options
  );
}

export interface LegacyQualityPolicy {
  id: string;
  projectId: string;
  versionNo: number;
  status: string;
  contentHash: string;
  rulesJson: string;
}
export function listLegacyQualityPolicies(page = 1) {
  return MSR.get<{ items: LegacyQualityPolicy[]; total: number }>(
    { url: '/system/quality/legacy-policies', params: { page, pageSize: 20 } },
    options
  );
}
export function importLegacyQualityPolicy(id: string) {
  return MSR.post<QualityPolicy>({ url: `/system/quality/legacy-policies/${id}/import` }, options);
}
