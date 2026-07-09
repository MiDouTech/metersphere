import MSR from '@/api/http/index';
import {
  GetDepartmentTreeUrl,
  GetMemberDetailUrl,
  GetMemberPageUrl,
  GetSyncConfigUrl,
  GetSyncLogPageUrl,
  GetSyncStatusUrl,
  ManualSyncUrl,
  SaveSyncConfigUrl,
  TestSyncConfigUrl,
} from '@/api/requrls/setting/orgStructure';

import type { CommonList } from '@/models/common';
import type {
  DepartmentTreeNode,
  MemberPageParams,
  OrgStructureMemberDetail,
  OrgStructureMemberItem,
  OrgSyncLogItem,
  OrgWecomSyncConfig,
  OrgWecomSyncConfigSaveParams,
  OrgWecomSyncConfigTestParams,
  OrgWecomSyncConfigTestResponse,
  OrgWecomSyncManualResponse,
  OrgWecomSyncStatus,
  SyncLogPageParams,
} from '@/models/setting/orgStructure';

export function getDepartmentTree(organizationId: string) {
  return MSR.get<DepartmentTreeNode[]>({ url: GetDepartmentTreeUrl, params: { organizationId } });
}

export function getMemberPage(params: MemberPageParams & Record<string, unknown>) {
  // useTable 会附带 combineSearch 等字段，GET 查询参数无法绑定到 BasePageRequest
  const { organizationId, departmentId, keyword, enable, syncStatus, current, pageSize } = params;
  return MSR.get<CommonList<OrgStructureMemberItem>>({
    url: GetMemberPageUrl,
    params: {
      organizationId,
      departmentId,
      keyword,
      enable,
      syncStatus,
      current,
      pageSize,
    },
  });
}

export function getMemberDetail(id: string, organizationId: string) {
  return MSR.get<OrgStructureMemberDetail>({ url: `${GetMemberDetailUrl}/${id}`, params: { organizationId } });
}

export function manualSync(organizationId: string) {
  // 后端使用 @RequestParam，需将 organizationId 拼到 URL 查询参数
  return MSR.post<OrgWecomSyncManualResponse>(
    { url: ManualSyncUrl, params: { organizationId } },
    { joinParamsToUrl: true }
  );
}

export function getSyncStatus(organizationId: string) {
  return MSR.get<OrgWecomSyncStatus>({ url: GetSyncStatusUrl, params: { organizationId } });
}

export function getSyncLogPage(params: SyncLogPageParams & Record<string, unknown>) {
  const { organizationId, syncStatus, current, pageSize } = params;
  return MSR.get<CommonList<OrgSyncLogItem>>({
    url: GetSyncLogPageUrl,
    params: {
      organizationId,
      syncStatus,
      current,
      pageSize,
    },
  });
}

export function getSyncConfig(organizationId: string) {
  return MSR.get<OrgWecomSyncConfig>({ url: GetSyncConfigUrl, params: { organizationId } });
}

export function saveSyncConfig(data: OrgWecomSyncConfigSaveParams) {
  return MSR.post({ url: SaveSyncConfigUrl, data });
}

export function testSyncConfig(data: OrgWecomSyncConfigTestParams) {
  return MSR.post<OrgWecomSyncConfigTestResponse>({ url: TestSyncConfigUrl, data });
}
