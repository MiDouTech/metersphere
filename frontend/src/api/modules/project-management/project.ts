import MSR from '@/api/http/index';
import { ProjectListUrl, ProjectSwitchUrl } from '@/api/requrls/project-management/project';

import type { CommonList, TableQueryParams } from '@/models/common';
import type { ProjectListItem } from '@/models/setting/project';
import type { OrgProjectTableItem } from '@/models/setting/system/orgAndProject';
import type { LoginRes } from '@/models/user';

export function getProjectList(organizationId: string) {
  return MSR.get<ProjectListItem[]>({ url: ProjectListUrl, params: organizationId }, { ignoreCancelToken: true });
}

export function pageAccessibleProjects(data: TableQueryParams & { enable?: boolean }) {
  return MSR.post<CommonList<OrgProjectTableItem>>({ url: '/project/page', data });
}

export function pageCaseAssetProjects(data: TableQueryParams) {
  return MSR.post<CommonList<OrgProjectTableItem>>({ url: '/project/case-asset/page', data });
}

export function switchProject(data: { projectId: string; userId: string }) {
  return MSR.post<LoginRes>({ url: ProjectSwitchUrl, data });
}

export function getProjectInfo(projectId: string) {
  return MSR.get<ProjectListItem>({ url: `/project/get/${projectId}` });
}

export function getProjectListByOrgAndModule(orgId: string, module: string) {
  return MSR.get<ProjectListItem[]>({ url: `${ProjectListUrl}/${orgId}/${module}` });
}
