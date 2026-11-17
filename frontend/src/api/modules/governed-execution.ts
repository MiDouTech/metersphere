import http from '@/api/http/index';
import { clearProtocolMemory, protocolMemoryGeneration, protocolRequests } from '@/utils/protocol-memory';

// Late responses from a previous identity must never refill a cleared in-memory view.
const MSR = new Proxy(http, {
  get(target, key, receiver) {
    const method = Reflect.get(target, key, receiver);
    if (!['get', 'post'].includes(String(key))) return method;
    return async (...args: unknown[]) => {
      const generation = protocolMemoryGeneration();
      try {
        const result = await Reflect.apply(method, target, args);
        if (generation !== protocolMemoryGeneration()) throw new Error('PROTOCOL_CONTEXT_CHANGED');
        return result;
      } catch (error) {
        const failure = error as { status?: number; code?: string };
        if (
          generation === protocolMemoryGeneration() &&
          ([401, 403].includes(failure.status || 0) ||
            ['AGENT_RESOURCE_NOT_ACCESSIBLE', 'PROTOCOL_KNOWLEDGE_UNAVAILABLE'].includes(failure.code || ''))
        )
          clearProtocolMemory();
        throw error;
      }
    };
  },
});

export type ProtocolRecord = Record<string, unknown>;
export interface SourceReference {
  projectId: string;
  assetType: string;
  assetId: string;
  versionId: string;
  snapshotHash: string;
  contributes: boolean;
}
export interface KnowledgeDocument {
  id?: string;
  version?: number;
  expectedKnowledgeVersion?: number;
  state?: string;
  schemaVersion: string;
  title: string;
  type: string;
  body: string;
  scope: string;
  preconditions: string;
  successCriteria: string;
  sources: SourceReference[];
  readers?: string[];
  relations: ProtocolRecord[];
  origin?: ProtocolRecord;
  inputs?: ProtocolRecord;
  outputs?: ProtocolRecord;
  system?: string;
  module?: string;
  preparation?: ProtocolRecord;
}
export interface ContractStep {
  id: string;
  sourcePosition: number;
  action: string;
  expectedObservation: string;
  inputBindings: Record<string, string>;
}
export interface ContractAssertion {
  id: string;
  required: boolean;
  entryPoint: string;
  expected: string;
  evidenceTypes: string[];
  objectFields: string[];
  verificationMethod: string;
  failureRule: string;
  stepId: string;
}
export interface StructuredContract {
  schemaVersion: string;
  contractVersion?: number;
  state?: string;
  goal: string;
  preconditions: string;
  environmentId: string;
  riskBoundary: string;
  steps: ContractStep[];
  assertions: ContractAssertion[];
  allowedActions: string[];
  dataRequirements: ProtocolRecord[];
  outputs: ProtocolRecord;
  dependencies: ProtocolRecord[];
  knowledgeRefs: ProtocolRecord[];
  variables?: ProtocolRecord;
}
export interface ExecutionInstance extends ProtocolRecord {
  caseExecutionId: string;
  caseId: string;
  contextVersion: number;
  resultVersion: number;
  submissionRevision: number;
  leaseGeneration: number;
  phase: string;
  verification: string;
  result?: string;
  syncStatus?: string;
  contract: StructuredContract;
  actions: ProtocolRecord[];
  steps: ProtocolRecord[];
  humanRequests: ProtocolRecord[];
  submittedAssertions?: ProtocolRecord[];
  reviewedAssertions?: ProtocolRecord[];
}
export interface ExecutionSession {
  sessionId: string;
  state: string;
  sessionVersion: number;
  executionContextVersion: number;
  instances: ExecutionInstance[];
  events: Array<{ id: string; instance_id?: string; kind: string; create_time: number }>;
  context: ProtocolRecord;
  capabilities: string[];
  actorType: string;
}
export const requestKey = () =>
  Array.from(crypto.getRandomValues(new Uint8Array(16)), (value) => value.toString(16).padStart(2, '0')).join('');
export const newRequest = (value: ProtocolRecord) => ({
  protocolVersion: '1.0',
  idempotencyKey: requestKey(),
  ...value,
});
const pendingRequests = protocolRequests;
export function stableRequest(scope: string, value: ProtocolRecord): ProtocolRecord {
  const fingerprint = JSON.stringify(value);
  const previous = pendingRequests.get(scope);
  if (previous?.fingerprint === fingerprint) return previous.request;
  const request = newRequest(JSON.parse(JSON.stringify(value)) as ProtocolRecord);
  pendingRequests.set(scope, { fingerprint, request });
  return request;
}
export const searchKnowledge = (organizationId: string, keyword = '', page = 1, filters: Record<string, string> = {}) =>
  MSR.get<{ total: number; records: KnowledgeDocument[] }>({
    url: '/test-knowledge',
    params: { organizationId, keyword, page, size: 20, ...filters },
  });
export const knowledgeCapabilities = (organizationId: string) =>
  MSR.get<string[]>({ url: '/test-knowledge/capabilities', params: { organizationId } });
export const getKnowledge = (organizationId: string, id: string, version?: number) =>
  MSR.get<KnowledgeDocument>({ url: `/test-knowledge/${encodeURIComponent(id)}`, params: { organizationId, version } });
export const getKnowledgeHistory = (organizationId: string, id: string) =>
  MSR.get<{ versions: ProtocolRecord[]; usage: ProtocolRecord[] }>({
    url: `/test-knowledge/${encodeURIComponent(id)}/history`,
    params: { organizationId },
  });
export const saveKnowledge = (organizationId: string, request: ProtocolRecord) =>
  MSR.post<{ knowledgeId: string; version: number; validationIssues: string[] }>({
    url: '/test-knowledge',
    params: { organizationId },
    data: request,
  });
export const governKnowledge = (organizationId: string, action: string, request: ProtocolRecord) =>
  MSR.post<ProtocolRecord>({ url: `/test-knowledge/${action}`, params: { organizationId }, data: request });
export const getStructuredContract = (projectId: string, caseId: string) =>
  MSR.get<StructuredContract>({
    url: `/test-knowledge/contracts/${encodeURIComponent(caseId)}`,
    params: { projectId },
  });
export const convertStructuredContract = (projectId: string, caseId: string) =>
  MSR.get<StructuredContract>({
    url: `/test-knowledge/contracts/${encodeURIComponent(caseId)}/conversion`,
    params: { projectId },
  });
export const saveStructuredContract = (projectId: string, caseId: string, request: ProtocolRecord) =>
  MSR.post<{ version: number; issues: string[] }>({
    url: `/test-knowledge/contracts/${encodeURIComponent(caseId)}`,
    params: { projectId },
    data: request,
  });
export const readSession = (sessionId: string) =>
  MSR.post<ExecutionSession>({ url: '/governed-execution/operations/checkpoint.get', data: { sessionId } });
export const listSessions = (organizationId: string, projectId?: string, taskId?: string) =>
  MSR.get<Array<{ id: string; source_type: string; source_id: string; state: string; create_time: number }>>({
    url: '/governed-execution',
    params: { organizationId, projectId, taskId },
  });
export const sessionOperation = (operation: string, request: ProtocolRecord) =>
  MSR.post<ProtocolRecord>({ url: `/governed-execution/operations/${operation}`, data: request });
export const startSession = (request: ProtocolRecord) =>
  MSR.post<{ sessionId: string }>({ url: '/governed-execution', data: request });
export const uploadProtocolEvidence = (request: ProtocolRecord) =>
  MSR.post<{ evidenceId: string }>({ url: '/governed-execution/evidence', data: request });
export const previewSource = (projectId: string, assetId: string, versionId: string) =>
  MSR.get<{ name: string; content: unknown; snapshotHash: string; parseStatus: string }>({
    url: '/test-knowledge/migration/preview',
    params: { projectId, assetId, versionId },
  });
export const migrateSources = (organizationId: string, request: ProtocolRecord) =>
  MSR.post<ProtocolRecord>({ url: '/test-knowledge/migration', params: { organizationId }, data: request });
export const inspectProtocolAdministration = (organizationId: string) =>
  MSR.get<{ identities: ProtocolRecord[]; subjects: ProtocolRecord[]; grants: ProtocolRecord[] }>({
    url: '/governed-execution/administration',
    params: { organizationId },
  });
export const configureProtocolAdministration = (organizationId: string, type: string, request: ProtocolRecord) =>
  MSR.post<ProtocolRecord>({
    url: `/governed-execution/administration/${type}`,
    params: { organizationId },
    data: request,
  });
export const authorizeProtocolSource = (projectId: string, type: string, id: string, request: ProtocolRecord) =>
  MSR.post<ProtocolRecord>({
    url: `/governed-execution/authorizations/${type}/${encodeURIComponent(id)}`,
    params: { projectId },
    data: request,
  });

export const withdrawMigration = (organizationId: string, request: ProtocolRecord) =>
  MSR.post<ProtocolRecord>({ url: '/test-knowledge/migration/withdraw', params: { organizationId }, data: request });

export const downloadProtocolEvidence = (sessionId: string, caseExecutionId: string, id: string) =>
  MSR.get<Blob>(
    {
      url: `/governed-execution/evidence/${encodeURIComponent(id)}`,
      params: { sessionId, caseExecutionId },
      responseType: 'blob',
    },
    { isTransformResponse: false }
  );

export const readProtocolSourceAuthorization = (projectId: string, type: string, id: string, environmentId: string) =>
  MSR.get<ProtocolRecord>({
    url: `/governed-execution/authorizations/${type}/${encodeURIComponent(id)}`,
    params: { projectId, environmentId },
  });
