export type AiDraftStatus = 'DRAFT' | 'VALIDATING' | 'INVALID' | 'READY' | 'SAVING' | 'SAVED' | 'FAILED';

export interface AiCaseDraft {
  id: string;
  generationId: string;
  sourceDocumentId?: string;
  projectId: string;
  moduleId?: string;
  templateId?: string;
  name: string;
  caseLevel?: string;
  editType?: 'STEP' | 'TEXT' | string;
  prerequisite?: string;
  steps?: string;
  expectedResult?: string;
  tags?: string;
  customFields?: string;
  sourceReferences?: string;
  validationMessage?: string;
  fingerprint?: string;
  duplicate?: boolean;
  validationStatus?: string;
  draftStatus: AiDraftStatus;
  formalCaseId?: string;
  deleted?: boolean;
  version?: number;
  createUser?: string;
  createTime?: number;
  updateTime?: number;
}

export interface AiCaseGenerateRequest {
  projectId: string;
  moduleId?: string;
  templateId?: string;
  prompt: string;
  chatModelId: string;
  conversationId: string;
  organizationId: string;
  maxCases?: number;
  sourceDocumentIds?: string[];
  generationId?: string;
}

export interface AiCaseGenerateResponse {
  generationId: string;
  createdCount: number;
  warnings: string[];
  drafts: AiCaseDraft[];
}

export interface AiDraftPageRequest {
  projectId: string;
  draftStatus?: string;
  current?: number;
  pageSize?: number;
}

export interface AiDraftPageResponse {
  total: number;
  records: AiCaseDraft[];
}

export interface AiDraftDeleteRequest {
  projectId: string;
  draftIds: string[];
}

export interface AiDraftRegenerateRequest {
  projectId: string;
  draftId: string;
  prompt?: string;
  chatModelId: string;
  conversationId: string;
  organizationId: string;
}

export interface AiDraftBatchSaveRequest {
  projectId: string;
  moduleId?: string;
  templateId?: string;
  draftIds: string[];
}

export interface AiDraftBatchSaveResponse {
  successCount: number;
  failureCount: number;
  results: Array<{
    draftId: string;
    name?: string;
    formalCaseId?: string;
    success: boolean;
    message?: string;
  }>;
}

export interface AiSourceDocument {
  id: string;
  projectId: string;
  conversationId?: string;
  fileId: string;
  originalName: string;
  mimeType?: string;
  fileSize?: number;
  sha256?: string;
  duplicate?: boolean;
  duplicateSourceDocumentId?: string;
  parseStatus: 'UPLOADED' | 'PARSING' | 'PARSED' | 'GENERATING' | 'GENERATED' | 'FAILED';
  parsedResultPath?: string;
  parserType?: string;
  summary?: string;
  sectionIndex?: string;
  errorMessage?: string;
  createTime?: number;
  updateTime?: number;
}

export interface AiSourceDocumentPageResponse {
  total: number;
  records: AiSourceDocument[];
}
