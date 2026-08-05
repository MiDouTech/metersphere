import MSR from '@/api/http/index';
import { AiExecutionResolveUrl, AiExecutionTaskDetailUrl, AiExecutionTaskUrl } from '@/api/requrls/ai-execution';

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
}

export interface AiExecutionTask {
  id: string;
  projectId: string;
  testPlanId?: string;
  source?: string;
  status: string;
  totalCount: number;
  successCount: number;
  failedCount: number;
  blockedCount: number;
  skippedCount: number;
  unexecutedCount: number;
  confirmRequired?: boolean;
  confirmationReason?: string;
  cases?: AiExecutionCase[];
}

export interface AiExecutionCreateParams {
  projectId: string;
  testPlanId?: string;
  caseIds: string[];
  source?: string;
  confirmed?: boolean;
  idempotencyKey?: string;
}

export function resolveAiExecutionScope(data: Record<string, any>) {
  return MSR.post({ url: AiExecutionResolveUrl, data });
}

export function createAiExecutionTask(data: AiExecutionCreateParams) {
  return MSR.post<AiExecutionTask>({ url: AiExecutionTaskUrl, data });
}

export function getAiExecutionTask(id: string) {
  return MSR.get<AiExecutionTask>({ url: `${AiExecutionTaskDetailUrl}/${id}` });
}
