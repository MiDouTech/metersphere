import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const Execution: AppRouteRecordRaw = {
  path: '/execution',
  name: 'ExecutionQuality',
  redirect: '/execution/quality-policy',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.executionQuality',
    icon: 'icon-icon_robot',
    order: 6,
    roles: ['QUALITY:READ'],
    resourceCode: 'EXECUTION_QUALITY_MENU',
  },
  children: [
    {
      path: 'quality-policy',
      name: 'ExecutionQualityPolicy',
      component: () => import('@/views/execution/quality-policy.vue'),
      meta: {
        locale: 'menu.executionQuality.policy',
        roles: ['QUALITY:READ'],
        resourceCode: 'EXECUTION_QUALITY_POLICY_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
  ],
};
export default Execution;
