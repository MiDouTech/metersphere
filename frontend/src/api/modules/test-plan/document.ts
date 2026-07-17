import MSR from '@/api/http/index';
import { GetTestPlanDocumentUrl, SaveTestPlanDocumentUrl } from '@/api/requrls/test-plan/document';

import type { TestPlanDocumentResponse, TestPlanDocumentSaveRequest } from '@/models/testPlan/document';

/** 获取测试计划文档 */
export function getTestPlanDocument(testPlanId: string) {
  return MSR.get<TestPlanDocumentResponse>({ url: `${GetTestPlanDocumentUrl}/${testPlanId}/document` });
}

/** 保存测试计划文档 */
export function saveTestPlanDocument(testPlanId: string, data: TestPlanDocumentSaveRequest) {
  return MSR.post<TestPlanDocumentResponse>({ url: `${SaveTestPlanDocumentUrl}/${testPlanId}/document`, data });
}
