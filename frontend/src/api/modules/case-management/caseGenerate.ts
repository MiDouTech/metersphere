import MSR from '@/api/http/index';
import { getToken } from '@/utils/auth';

import type {
  AiCaseDraft,
  AiCaseGenerateRequest,
  AiCaseGenerateResponse,
  AiDraftBatchSaveRequest,
  AiDraftBatchSaveResponse,
  AiDraftDeleteRequest,
  AiDraftPageRequest,
  AiDraftPageResponse,
  AiDraftRegenerateRequest,
  AiDraftReviewRequest,
  AiSourceDocument,
  AiSourceDocumentPageResponse,
} from '@/models/caseManagement/caseGenerate';

const BaseUrl = '/functional/case/ai/draft';
const AgentBaseUrl = '/functional/case/ai/agent';

export interface AiCaseAgentModel {
  id: string;
  name: string;
  provider: string;
  baseName: string;
  supportsStream: boolean;
  supportsTools: boolean;
}

export type AiResourceType = 'MODEL_API' | 'USER_AGENT';

export interface AiSelectableResource {
  id: string;
  resourceType: AiResourceType;
  provider: string;
  displayName: string;
  personal: boolean;
  online: boolean;
  experimental: boolean;
  connectionStatus: string;
  unavailableReason?: string;
  capabilities: {
    stream: boolean;
    tools: boolean;
    files: boolean;
    cancel: boolean;
    vision: boolean;
  };
}

export interface AiCaseAgentConversation {
  id: string;
  projectId: string;
  organizationId: string;
  title: string;
  modelSourceId?: string;
  resourceType: AiResourceType;
  resourceId: string;
  agentConnectionId?: string;
  status: 'ACTIVE' | 'ARCHIVED';
}

export interface AiCaseAgentMessage {
  id: string;
  role: 'USER' | 'ASSISTANT' | 'TOOL' | 'SYSTEM';
  content: string;
  status: 'STREAMING' | 'COMPLETED' | 'FAILED' | 'CANCELED';
  requestId?: string;
  resourceType?: AiResourceType;
  resourceId?: string;
  agentConnectionId?: string;
}

export interface AiCaseAgentEvent {
  requestId: string;
  sequence: number;
  eventType: string;
  payload: string;
  timestamp: number;
}

export interface AiCaseAgentExecution {
  requestId: string;
  conversationId: string;
  status: 'CREATED' | 'RUNNING' | 'WAITING_CONFIRMATION' | 'COMPLETED' | 'FAILED' | 'CANCELED';
  errorCode?: string;
  errorMessage?: string;
}

export function listAiCaseAgentModels(projectId: string) {
  return MSR.get<AiCaseAgentModel[]>({ url: `${AgentBaseUrl}/models`, params: { projectId } });
}

export function listAiCaseAgentResources(projectId: string) {
  return MSR.get<AiSelectableResource[]>({ url: `${AgentBaseUrl}/resources`, params: { projectId } });
}

export function createAiCaseAgentConversation(data: {
  projectId: string;
  organizationId: string;
  modelSourceId?: string;
  resourceType?: AiResourceType;
  resourceId?: string;
  title?: string;
}) {
  return MSR.post<AiCaseAgentConversation>({ url: `${AgentBaseUrl}/conversation/create`, data });
}

export function getAiCaseAgentConversation(id: string, projectId: string) {
  return MSR.get<AiCaseAgentConversation>({ url: `${AgentBaseUrl}/conversation/${id}`, params: { projectId } });
}

export function switchAiCaseAgentModel(data: { projectId: string; conversationId: string; modelSourceId: string }) {
  return MSR.post<AiCaseAgentConversation>({ url: `${AgentBaseUrl}/conversation/model`, data });
}

export function switchAiCaseAgentResource(data: {
  projectId: string;
  conversationId: string;
  resourceType: AiResourceType;
  resourceId: string;
}) {
  return MSR.post<AiCaseAgentConversation>({ url: `${AgentBaseUrl}/conversation/resource`, data });
}

export function pageAiCaseAgentMessages(data: {
  projectId: string;
  conversationId: string;
  beforeTime?: number;
  beforeId?: string;
  pageSize?: number;
}) {
  return MSR.post<{
    records: AiCaseAgentMessage[];
    hasMore: boolean;
    nextBeforeTime?: number;
    nextBeforeId?: string;
  }>({ url: `${AgentBaseUrl}/conversation/messages`, data });
}

export function cancelAiCaseAgentChat(data: { projectId: string; requestId: string }) {
  return MSR.post({ url: `${AgentBaseUrl}/chat/cancel`, data });
}

export function getAiCaseAgentExecution(projectId: string, requestId: string) {
  return MSR.get<AiCaseAgentExecution>({ url: `${AgentBaseUrl}/execution/${requestId}`, params: { projectId } });
}

export function listAiCaseAgentEvents(projectId: string, requestId: string, afterSequence = 0) {
  return MSR.get<AiCaseAgentEvent[]>({
    url: `${AgentBaseUrl}/execution/${requestId}/events`,
    params: { projectId, afterSequence },
  });
}

export function streamAiCaseAgentChat(
  data: {
    projectId: string;
    conversationId: string;
    requestId: string;
    message: string;
    resourceType?: AiResourceType;
    resourceId?: string;
    modelSourceId?: string;
  },
  onEvent: (event: AiCaseAgentEvent) => void
) {
  const controller = new AbortController();
  const apiBase = String(import.meta.env.VITE_API_BASE_URL || 'api').replace(/^\/+|\/+$/g, '');
  const token = getToken();
  const promise = (async () => {
    const response = await fetch(`${window.location.origin}/${apiBase}${AgentBaseUrl}/chat`, {
      method: 'POST',
      credentials: 'include',
      signal: controller.signal,
      headers: {
        'Accept': 'text/event-stream',
        'Content-Type': 'application/json',
        ...(token?.sessionId ? { 'X-AUTH-TOKEN': token.sessionId } : {}),
        ...(token?.csrfToken ? { 'CSRF-TOKEN': token.csrfToken } : {}),
        'PROJECT': data.projectId,
      },
      body: JSON.stringify(data),
    });
    if (!response.ok || !response.body) throw new Error(`Agent SSE HTTP ${response.status}`);
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (!controller.signal.aborted) {
      // SSE frames must be consumed sequentially from the stream reader.
      // eslint-disable-next-line no-await-in-loop
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        const frame = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const serialized = frame
          .split('\n')
          .filter((line) => line.startsWith('data:'))
          .map((line) => line.slice(5).trimStart())
          .join('\n');
        if (serialized) onEvent(JSON.parse(serialized));
        boundary = buffer.indexOf('\n\n');
      }
    }
  })();
  return { abort: () => controller.abort(), promise };
}

export function generateAiCaseDraft(data: AiCaseGenerateRequest) {
  return MSR.post<AiCaseGenerateResponse>({ url: `${BaseUrl}/generation/structured`, data });
}

export function cancelAiCaseGeneration(data: { projectId: string; generationId: string }) {
  return MSR.post({ url: `${BaseUrl}/generation/cancel`, data });
}

export function pageAiCaseDraft(data: AiDraftPageRequest) {
  return MSR.post<AiDraftPageResponse>({ url: `${BaseUrl}/page`, data });
}

export function pageAiCaseDraftReviewQueue(data: AiDraftPageRequest) {
  return MSR.post<AiDraftPageResponse>({ url: `${BaseUrl}/review-page`, data });
}

export function updateAiCaseDraft(data: AiCaseDraft) {
  return MSR.post<AiCaseDraft>({ url: `${BaseUrl}/update`, data });
}

export function deleteAiCaseDraft(data: AiDraftDeleteRequest) {
  return MSR.post({ url: `${BaseUrl}/delete`, data });
}

export function regenerateAiCaseDraft(data: AiDraftRegenerateRequest) {
  return MSR.post<AiCaseGenerateResponse>({ url: `${BaseUrl}/regenerate`, data });
}

export function batchSaveAiCaseDraft(data: AiDraftBatchSaveRequest) {
  return MSR.post<AiDraftBatchSaveResponse>({ url: `${BaseUrl}/batch-save`, data });
}

export function reviewAiCaseDraft(data: AiDraftReviewRequest) {
  return MSR.post<AiCaseDraft[]>({ url: `${BaseUrl}/review`, data });
}

export function uploadAiSourceDocument(data: { request: { projectId: string; conversationId?: string }; file: File }) {
  return MSR.uploadFile<AiSourceDocument>(
    { url: '/functional/case/ai/document/upload' },
    { request: data.request, fileList: [data.file] }
  );
}

export function pageAiSourceDocument(data: {
  projectId: string;
  parseStatus?: string;
  current?: number;
  pageSize?: number;
}) {
  return MSR.post<AiSourceDocumentPageResponse>({ url: '/functional/case/ai/document/page', data });
}

export function retryAiSourceDocument(data: { projectId: string; id: string }) {
  return MSR.post({ url: '/functional/case/ai/document/retry', data });
}

export function deleteAiSourceDocument(data: { projectId: string; id: string }) {
  return MSR.post({ url: '/functional/case/ai/document/delete', data });
}

export function subscribeAiSourceDocumentEvents(
  projectId: string,
  onStatus: (event: { documentId: string; status: string; message?: string; timestamp: number }) => void,
  onError?: (error: unknown) => void,
  onConnected?: () => void
) {
  const controller = new AbortController();
  const apiBase = String(import.meta.env.VITE_API_BASE_URL || 'api').replace(/^\/+|\/+$/g, '');
  const token = getToken();
  let lastEventId = '';
  const wait = (milliseconds: number) =>
    new Promise<void>((resolve) => {
      const timer = window.setTimeout(resolve, milliseconds);
      controller.signal.addEventListener(
        'abort',
        () => {
          window.clearTimeout(timer);
          resolve();
        },
        { once: true }
      );
    });
  const subscriptionPromise = (async () => {
    let reconnectDelay = 1000;
    while (!controller.signal.aborted) {
      try {
        // Reconnect attempts are intentionally serialized with backoff.
        // eslint-disable-next-line no-await-in-loop
        const response = await fetch(
          `${window.location.origin}/${apiBase}/functional/case/ai/document/events?projectId=${encodeURIComponent(
            projectId
          )}`,
          {
            method: 'GET',
            credentials: 'include',
            signal: controller.signal,
            headers: {
              Accept: 'text/event-stream',
              ...(lastEventId ? { 'Last-Event-ID': lastEventId } : {}),
              ...(token?.sessionId ? { 'X-AUTH-TOKEN': token.sessionId } : {}),
              ...(token?.csrfToken ? { 'CSRF-TOKEN': token.csrfToken } : {}),
              PROJECT: projectId,
            },
          }
        );
        if (!response.ok || !response.body) throw new Error(`SSE HTTP ${response.status}`);
        reconnectDelay = 1000;
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (!controller.signal.aborted) {
          // SSE frames must be consumed sequentially from the stream reader.
          // eslint-disable-next-line no-await-in-loop
          const { done, value } = await reader.read();
          if (done) throw new Error('SSE connection closed');
          buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
          let boundary = buffer.indexOf('\n\n');
          while (boundary >= 0) {
            const frame = buffer.slice(0, boundary);
            buffer = buffer.slice(boundary + 2);
            const eventName = frame.match(/^event:\s*(.+)$/m)?.[1];
            lastEventId = frame.match(/^id:\s*(.+)$/m)?.[1] || lastEventId;
            const data = frame
              .split('\n')
              .filter((line) => line.startsWith('data:'))
              .map((line) => line.slice(5).trimStart())
              .join('\n');
            if (eventName === 'connected') onConnected?.();
            if (eventName === 'document-status' && data) onStatus(JSON.parse(data));
            boundary = buffer.indexOf('\n\n');
          }
        }
      } catch (error) {
        if (controller.signal.aborted) break;
        onError?.(error);
        // Backoff must complete before the next reconnect attempt.
        // eslint-disable-next-line no-await-in-loop
        await wait(reconnectDelay);
        reconnectDelay = Math.min(30_000, reconnectDelay * 2);
      }
    }
  })();
  subscriptionPromise.catch(onError);
  return () => controller.abort();
}
