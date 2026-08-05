import MSR from '@/api/http/index';

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
  AiSourceDocument,
  AiSourceDocumentPageResponse,
} from '@/models/caseManagement/caseGenerate';

const BaseUrl = '/functional/case/ai/draft';

export function generateAiCaseDraft(data: AiCaseGenerateRequest) {
  return MSR.post<AiCaseGenerateResponse>({ url: `${BaseUrl}/generation/structured`, data });
}

export function cancelAiCaseGeneration(data: { projectId: string; generationId: string }) {
  return MSR.post({ url: `${BaseUrl}/generation/cancel`, data });
}

export function pageAiCaseDraft(data: AiDraftPageRequest) {
  return MSR.post<AiDraftPageResponse>({ url: `${BaseUrl}/page`, data });
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
