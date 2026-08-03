import { ShareEnum } from '@/enums/routeEnum';

import { SHARE_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const ShareRoute: AppRouteRecordRaw = {
  path: '/share',
  name: ShareEnum.SHARE,
  component: SHARE_LAYOUT,
  meta: {
    hideInMenu: true,
    roles: ['*'],
    requiresAuth: true,
  },
  children: [
    {
      path: 'shareReportScenario',
      name: ShareEnum.SHARE_REPORT_SCENARIO,
      component: () => import('@/views/api-test/report/shareSceneIndex.vue'),
      meta: {
        locale: '',
        roles: ['*'],
        isTopMenu: false,
      },
    },
    {
      path: 'shareReportCase',
      name: ShareEnum.SHARE_REPORT_CASE,
      component: () => import('@/views/api-test/report/shareCaseIndex.vue'),
      meta: {
        locale: '',
        roles: ['*'],
        isTopMenu: false,
      },
    },
    {
      path: 'shareReportTestPlan',
      name: ShareEnum.SHARE_REPORT_TEST_PLAN,
      component: () => import('@/views/test-plan/report/detail/sharePlanReportIndex.vue'),
      meta: {
        locale: '',
        roles: ['*'],
        isTopMenu: false,
      },
    },
    {
      path: 'shareFunctionalTestReport',
      name: ShareEnum.SHARE_FUNCTIONAL_TEST_REPORT,
      component: () => import('@/views/case-management/testReport/detail.vue'),
      meta: {
        locale: '',
        roles: ['*'],
        isTopMenu: false,
      },
    },
    {
      path: 'shareDefinitionApi',
      name: ShareEnum.SHARE_DEFINITION_API,
      component: () => import('@/views/api-test/management/components/management/api/shareApiDocIndex.vue'),
      meta: {
        locale: '',
        roles: ['*'],
        isTopMenu: false,
      },
    },
  ],
};

export default ShareRoute;
