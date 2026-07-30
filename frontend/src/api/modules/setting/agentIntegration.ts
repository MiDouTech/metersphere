import MSR from '@/api/http/index';
import {
  AgentMcpDownloadUrl,
  AgentMcpManifestUrl,
  AgentTokenAddUrl,
  AgentTokenDeleteUrl,
  AgentTokenPageUrl,
  AgentTokenUpdateUrl,
} from '@/api/requrls/setting/agentIntegration';

import type { CommonList, TableQueryParams } from '@/models/common';

export interface AgentTokenListItem {
  id: string;
  name: string;
  userId: string;
  projectId?: string;
  scopes: string;
  expireTime?: number;
  enable: boolean;
  createTime: number;
  createUser: string;
}

export interface AgentTokenCreateParams {
  name: string;
  userId: string;
  projectId?: string;
  scopes: string;
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
  projectId?: string;
  scopes?: string;
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

export function getAgentTokenPage(data: TableQueryParams) {
  return MSR.post<CommonList<AgentTokenListItem>>({ url: AgentTokenPageUrl, data });
}

export function createAgentToken(data: AgentTokenCreateParams) {
  return MSR.post<AgentTokenCreateResult>({ url: AgentTokenAddUrl, data });
}

export function updateAgentToken(data: AgentTokenUpdateParams) {
  return MSR.post({ url: AgentTokenUpdateUrl, data });
}

export function deleteAgentToken(id: string) {
  return MSR.get({ url: AgentTokenDeleteUrl, params: id });
}

export function getAgentMcpManifest() {
  return MSR.get<AgentMcpManifest>({ url: AgentMcpManifestUrl });
}

export function downloadAgentMcpBundle() {
  return MSR.get<BlobPart>({ url: AgentMcpDownloadUrl, responseType: 'blob' }, { isTransformResponse: false });
}
