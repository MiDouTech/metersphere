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

export function getMemberPage(params: MemberPageParams) {
  return MSR.get<CommonList<OrgStructureMemberItem>>({ url: GetMemberPageUrl, params });
}

export function getMemberDetail(id: string, organizationId: string) {
  return MSR.get<OrgStructureMemberDetail>({ url: `${GetMemberDetailUrl}/${id}`, params: { organizationId } });
}

export function manualSync(organizationId: string) {
  return MSR.post<OrgWecomSyncManualResponse>({ url: ManualSyncUrl, params: { organizationId } });
}

export function getSyncStatus(organizationId: string) {
  return MSR.get<OrgWecomSyncStatus>({ url: GetSyncStatusUrl, params: { organizationId } });
}

export function getSyncLogPage(params: SyncLogPageParams) {
  return MSR.get<CommonList<OrgSyncLogItem>>({ url: GetSyncLogPageUrl, params });
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
