import { safeExecutionQuery } from '@/utils/execution-navigation';

import { CaseManagementRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const Execution: AppRouteRecordRaw = {
  path: '/execution',
  name: 'ExecutionQuality',
  redirect: '/execution/tasks',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.executionCenter',
    icon: 'icon-icon_robot',
    order: 6,
    resourceCode: 'EXECUTION_QUALITY_MENU',
    hideChildrenInMenu: true,
    roles: ['AI_EXECUTION:READ'],
  },
  children: [
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
      component: null,
      redirect: (to) => ({ path: '/setting/execution-settings/quality-policy', query: safeExecutionQuery(to.query) }),
      meta: { hideInMenu: true },
    },
  ],
};
export default Execution;
