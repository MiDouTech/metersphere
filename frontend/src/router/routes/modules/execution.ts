import { hasAnyPermission } from '@/utils/permission';

import { CaseManagementRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const Execution: AppRouteRecordRaw = {
  path: '/execution',
  name: 'ExecutionQuality',
  redirect: () => {
    if (hasAnyPermission(['AI_EXECUTION:READ'])) return '/execution/tasks';
    if (hasAnyPermission(['SYSTEM_QUALITY:READ'], ['SYSTEM'])) return '/execution/quality-policy';
    return '/execution/platforms';
  },
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.executionCenter',
    icon: 'icon-icon_robot',
    order: 6,
    hideChildrenInMenu: true,
    roles: ['AI_EXECUTION:READ', 'AI_CREDENTIAL:READ_METADATA', 'SYSTEM_QUALITY:READ'],
  },
  children: [
    {
      path: 'diagnostics/:section?',
      name: 'ExecutionDiagnostics',
      component: () => import('@/views/execution/diagnostics.vue'),
      meta: { hideInMenu: true, adminOnly: true, roles: ['AI_EXECUTION:READ'] },
    },
    {
      path: 'platforms',
      name: 'ExecutionPlatforms',
      component: () => import('@/views/execution/platforms.vue'),
      meta: {
        locale: '测试平台配置',
        roles: ['AI_EXECUTION:READ', 'AI_CREDENTIAL:READ_METADATA'],
        isTopMenu: true,
      },
    },
    {
      path: 'tasks',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_AUTOMATION_EXECUTION,
      component: () => import('@/views/execution/tasks.vue'),
      meta: {
        locale: 'menu.executionCenter.tasks',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'FUNCTIONAL_CASE_AUTOMATION_EXECUTION_TAB',
        isTopMenu: true,
      },
    },
    {
      path: 'tasks/:id',
      name: 'AgentExecutionDetail',
      component: () => import('@/views/execution/tasks.vue'),
      meta: {
        locale: 'menu.executionCenter.detail',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'AGENT_EXECUTION_DETAIL_PAGE',
        hideInMenu: true,
        activeMenu: CaseManagementRouteEnum.CASE_MANAGEMENT_AUTOMATION_EXECUTION,
      },
    },
    {
      path: 'quality-policy',
      name: 'SystemQualityPolicy',
      component: () => import('@/views/execution/quality-policy.vue'),
      meta: {
        locale: '平台质量门禁',
        roles: ['SYSTEM_QUALITY:READ'],
        resourceCode: 'SYSTEM_QUALITY_POLICY_PAGE',
        isTopMenu: true,
      },
    },
  ],
};
export default Execution;
