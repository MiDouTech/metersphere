import MSR from '@/api/http/index';
import {
  AgentMcpDownloadUrl,
  AgentMcpManifestUrl,
  AgentTokenUrl,
  PersonalAgentProjectUrl,
} from '@/api/requrls/setting/agentIntegration';

import type { CommonList, TableQueryParams } from '@/models/common';

export interface AgentTokenListItem {
  id: string;
  name: string;
  userId: string;
  projectId?: string;
  projectIds?: string[];
  projectScopeLabel?: string;
  scopes: string;
  clientType?: string;
  expireTime?: number;
  enable: boolean;
  status?: string;
  displayPrefix?: string;
  lastUsedAt?: number;
  invocationCount?: number;
  createTime: number;
  createUser: string;
}

export interface AgentTokenCreateParams {
  name: string;
  projectIds?: string[];
  projectId?: string;
  scopes: string;
  clientType?: string;
  expireTime?: number;
}

export interface AgentTokenCreateResult {
  id: string;
  name: string;
  token: string;
  scopes: string;
  expireTime?: number;
  warning: string;
}

export interface AgentTokenUpdateParams {
  id: string;
  name?: string;
  projectIds?: string[];
  projectId?: string;
  scopes?: string;
  clientType?: string;
  expireTime?: number;
  enable?: boolean;
}

export interface AgentMcpManifest {
  name?: string;
  version?: string;
  fileName?: string;
  description?: string;
  nodeEngine?: string;
  installHint?: string;
  available?: boolean;
}

export interface PersonalAgentProject {
  id: string;
  num?: number;
  name: string;
  organizationId?: string;
  organizationName?: string;
}

export function getAgentTokenPage(data: TableQueryParams) {
  return MSR.get<CommonList<AgentTokenListItem>>({ url: AgentTokenUrl, params: data });
}

export function createAgentToken(data: AgentTokenCreateParams) {
  return MSR.post<AgentTokenCreateResult>({ url: AgentTokenUrl, data });
}

export function updateAgentToken(data: AgentTokenUpdateParams) {
  const { id, ...payload } = data;
  return MSR.request({ url: `${AgentTokenUrl}/${id}`, method: 'PATCH', data: payload });
}

export function enableAgentToken(id: string) {
  return MSR.post({ url: `${AgentTokenUrl}/${id}/enable` });
}

export function disableAgentToken(id: string) {
  return MSR.post({ url: `${AgentTokenUrl}/${id}/disable` });
}

export function deleteAgentToken(id: string) {
  return MSR.delete({ url: `${AgentTokenUrl}/${id}` });
}

export function getAgentMcpManifest() {
  return MSR.get<AgentMcpManifest>({ url: AgentMcpManifestUrl });
}

export function downloadAgentMcpBundle() {
  return MSR.get<BlobPart>({ url: AgentMcpDownloadUrl, responseType: 'blob' }, { isTransformResponse: false });
}

export function getPersonalAgentProjectList(keyword: string) {
  return MSR.get<PersonalAgentProject[]>({ url: PersonalAgentProjectUrl, params: { keyword, limit: 50 } });
}

export function getAdminAgentTokenPage(data: TableQueryParams) {
  return MSR.get<CommonList<AgentTokenListItem>>({ url: '/admin/agent-tokens', params: data });
}

export function revokeAdminAgentToken(id: string) {
  return MSR.post({ url: `/admin/agent-tokens/${id}/revoke` });
}

export function exportAdminAgentTokenAudit(params: Record<string, unknown>) {
  return MSR.get<BlobPart>(
    { url: '/admin/agent-tokens/audit/export', params, responseType: 'blob' },
    { isTransformResponse: false }
  );
}
