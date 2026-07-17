import { TableQueryParams } from '@/models/common';

/** 报告正文 JSON 分节 */
export interface TestReportContent {
  versionOverview?: string | Record<string, any>;
  testScope?: {
    content?: string;
  };
  conclusion?: {
    result?: string;
    suggestion?: string;
  };
  riskNote?: string;
  /** 页脚：撰写人、日期等 */
  footer?: {
    author?: string;
    date?: string;
  };
}

export interface TestReportExecStats {
  total: number;
  pass: number;
  fail: number;
  block: number;
  execRate: string;
  passRate: string;
}

export interface TestReportRiskCase {
  caseId: string;
  num: number;
  name: string;
  lastExecResult: string;
}

/** 统计快照 */
export interface TestReportStats {
  exec: TestReportExecStats;
  bugHandlerStatus: Record<string, any>[];
  bugType: Record<string, any>[];
  riskCases: TestReportRiskCase[];
  passRateFormulaNote?: string;
  bugTypeMessage?: string;
}

export interface TestReportItem {
  id: string;
  projectId: string;
  name: string;
  planId?: string | null;
  content?: string;
  statsSnapshot?: string;
  createTime: number;
  updateTime: number;
  createUser: string;
  updateUser: string;
  /** 列表展示用：计划名称 */
  planName?: string;
}

export interface TestReportPageRequest extends TableQueryParams {
  projectId: string;
  planId?: string;
}

export interface TestReportGenerateRequest {
  projectId: string;
  name?: string;
  planId?: string;
  startTime?: number;
  endTime?: number;
}

export interface TestReportUpdateRequest {
  id: string;
  name?: string;
  content?: string;
}
