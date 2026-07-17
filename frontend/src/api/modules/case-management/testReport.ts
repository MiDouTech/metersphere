import MSR from '@/api/http/index';
import {
  DeleteTestReportUrl,
  GenerateTestReportUrl,
  GetTestReportDetailUrl,
  GetTestReportPageUrl,
  RefreshTestReportStatsUrl,
  UpdateTestReportUrl,
} from '@/api/requrls/case-management/testReport';

import {
  TestReportGenerateRequest,
  TestReportItem,
  TestReportPageRequest,
  TestReportUpdateRequest,
} from '@/models/caseManagement/testReport';
import { CommonList } from '@/models/common';

export const getTestReportPage = (data: TestReportPageRequest) => {
  return MSR.post<CommonList<TestReportItem>>({ url: GetTestReportPageUrl, data });
};

export const getTestReportDetail = (id: string) => {
  return MSR.get<TestReportItem>({ url: `${GetTestReportDetailUrl}/${id}` });
};

export const generateTestReport = (data: TestReportGenerateRequest) => {
  return MSR.post<TestReportItem>({ url: GenerateTestReportUrl, data });
};

export const updateTestReport = (data: TestReportUpdateRequest) => {
  return MSR.post<TestReportItem>({ url: UpdateTestReportUrl, data });
};

export const refreshTestReportStats = (id: string) => {
  return MSR.post<TestReportItem>({ url: `${RefreshTestReportStatsUrl}/${id}` });
};

export const deleteTestReport = (id: string) => {
  return MSR.get({ url: `${DeleteTestReportUrl}/${id}` });
};
