/** 测试计划文档模板元数据 */
export interface TestPlanDocumentTemplateMeta {
  projectName?: string;
  planName?: string;
  author?: string;
  date?: string;
  docNo?: string;
}

/** 测试计划文档响应 */
export interface TestPlanDocumentResponse {
  testPlanId: string;
  content: string;
  contentType: string;
  exists: boolean;
  updateTime?: number;
  updateUser?: string;
  templateMeta?: TestPlanDocumentTemplateMeta;
}

/** 保存测试计划文档请求 */
export interface TestPlanDocumentSaveRequest {
  content: string;
  contentType: string;
}
