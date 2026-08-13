import { TestPlanRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const TestPlan: AppRouteRecordRaw = {
  path: '/test-plan',
  name: TestPlanRouteEnum.TEST_PLAN,
  redirect: '/test-plan/testPlanIndex',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.testPlan',
    collapsedLocale: 'menu.testPlanShort',
    icon: 'icon-a-icon_test-tracking_filled1',
    order: 2,
    hideChildrenInMenu: true,
    roles: ['PROJECT_TEST_PLAN:READ', 'PROJECT_TEST_PLAN_REPORT:READ'],
  },
  children: [
    // 测试计划
    {
      path: 'testPlanIndex',
      name: TestPlanRouteEnum.TEST_PLAN_INDEX,
      component: () => import('@/views/test-plan/testPlan/index.vue'),
      meta: {
        locale: 'menu.testPlanShort',
        roles: ['PROJECT_TEST_PLAN:READ'],
        resourceCode: 'TEST_PLAN_LIST_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'testPlanReport',
      name: TestPlanRouteEnum.TEST_PLAN_REPORT,
      component: () => import('@/views/test-plan/report/index.vue'),
      meta: {
        locale: 'menu.apiTest.report',
        roles: ['PROJECT_TEST_PLAN_REPORT:READ'],
        resourceCode: 'TEST_PLAN_DETAIL_REPORT_TAB',
        hideInMenu: true,
        isTopMenu: false,
        keepModuleAlive: true,
      },
    },
    // 用例评审：按重构方案迁移到测试计划模块入口
    {
      path: 'caseReview',
      name: TestPlanRouteEnum.TEST_PLAN_REVIEW,
      component: () => import('@/views/case-management/caseReview/index.vue'),
      meta: {
        locale: 'menu.caseManagement.caseManagementReviewShort',
        roles: ['CASE_REVIEW:READ'],
        resourceCode: 'TEST_PLAN_REVIEW_TAB',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'caseReviewCreate',
      name: TestPlanRouteEnum.TEST_PLAN_REVIEW_CREATE,
      component: () => import('@/views/case-management/caseReview/create.vue'),
      meta: {
        locale: 'menu.caseManagement.caseManagementReviewCreate',
        roles: ['CASE_REVIEW:READ+ADD', 'CASE_REVIEW:READ+UPDATE'],
        breadcrumbs: [
          {
            name: TestPlanRouteEnum.TEST_PLAN_REVIEW,
            locale: 'menu.caseManagement.caseManagementReview',
          },
          {
            name: TestPlanRouteEnum.TEST_PLAN_REVIEW_CREATE,
            locale: 'menu.caseManagement.caseManagementReviewCreate',
            editTag: 'id',
            editLocale: 'menu.caseManagement.caseManagementCaseReviewEdit',
          },
        ],
      },
    },
    {
      path: 'caseReviewDetail',
      name: TestPlanRouteEnum.TEST_PLAN_REVIEW_DETAIL,
      component: () => import('@/views/case-management/caseReview/detail.vue'),
      meta: {
        locale: 'menu.caseManagement.caseManagementReviewDetail',
        roles: ['CASE_REVIEW:READ'],
        breadcrumbs: [
          {
            name: TestPlanRouteEnum.TEST_PLAN_REVIEW,
            locale: 'menu.caseManagement.caseManagementReview',
          },
          {
            name: TestPlanRouteEnum.TEST_PLAN_REVIEW_DETAIL,
            locale: 'menu.caseManagement.caseManagementReviewDetail',
          },
        ],
      },
    },
    {
      path: 'caseReviewDetailCaseDetail',
      name: TestPlanRouteEnum.TEST_PLAN_REVIEW_DETAIL_CASE_DETAIL,
      component: () => import('@/views/case-management/caseReview/caseDetail.vue'),
      meta: {
        locale: 'menu.caseManagement.caseManagementCaseDetail',
        roles: ['CASE_REVIEW:READ'],
        breadcrumbs: [
          {
            name: TestPlanRouteEnum.TEST_PLAN_REVIEW,
            locale: 'menu.caseManagement.caseManagementReview',
          },
          {
            name: TestPlanRouteEnum.TEST_PLAN_REVIEW_DETAIL,
            locale: 'menu.caseManagement.caseManagementReviewDetail',
            isBack: true,
            query: ['id'],
          },
          {
            name: TestPlanRouteEnum.TEST_PLAN_REVIEW_DETAIL_CASE_DETAIL,
            locale: 'menu.caseManagement.caseManagementCaseDetail',
          },
        ],
      },
    },
    // 功能测试报告：按重构方案迁移到测试计划模块入口
    {
      path: 'functionalTestReport',
      name: TestPlanRouteEnum.TEST_PLAN_TEST_REPORT,
      component: () => import('@/views/case-management/testReport/index.vue'),
      meta: {
        locale: 'menu.caseManagement.testReport',
        roles: ['PROJECT_TEST_PLAN_REPORT:READ'],
        resourceCode: 'TEST_PLAN_TEST_REPORT_TAB',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'functionalTestReportDetail',
      name: TestPlanRouteEnum.TEST_PLAN_TEST_REPORT_DETAIL,
      component: () => import('@/views/case-management/testReport/detail.vue'),
      meta: {
        locale: 'menu.caseManagement.testReportDetail',
        roles: ['PROJECT_TEST_PLAN_REPORT:READ'],
        breadcrumbs: [
          {
            name: TestPlanRouteEnum.TEST_PLAN_TEST_REPORT,
            locale: 'menu.caseManagement.testReport',
          },
          {
            name: TestPlanRouteEnum.TEST_PLAN_TEST_REPORT_DETAIL,
            locale: 'menu.caseManagement.testReportDetail',
            editTag: 'id',
            editLocale: 'menu.caseManagement.testReportEdit',
          },
        ],
      },
    },
    {
      path: 'testPlanReportDetail',
      name: TestPlanRouteEnum.TEST_PLAN_REPORT_DETAIL,
      component: () => import('@/views/test-plan/report/detail/detail.vue'),
      meta: {
        locale: 'menu.apiTest.reportDetail',
        roles: ['PROJECT_TEST_PLAN_REPORT:READ'],
        breadcrumbs: [
          {
            name: TestPlanRouteEnum.TEST_PLAN_REPORT,
            locale: 'menu.apiTest.report',
          },
          {
            name: TestPlanRouteEnum.TEST_PLAN_REPORT_DETAIL,
            locale: 'menu.apiTest.reportDetail',
          },
        ],
      },
    },
    // 测试计划详情
    {
      path: 'testPlanIndexDetail',
      name: TestPlanRouteEnum.TEST_PLAN_INDEX_DETAIL,
      component: () => import('@/views/test-plan/testPlan/detail/index.vue'),
      meta: {
        locale: 'menu.testPlan.testPlanDetail',
        roles: ['PROJECT_TEST_PLAN:READ'],
        resourceCode: 'TEST_PLAN_DETAIL_PAGE',
        keepModuleAlive: true,
        breadcrumbs: [
          {
            name: TestPlanRouteEnum.TEST_PLAN_INDEX,
            locale: 'menu.testPlan',
          },
          {
            name: TestPlanRouteEnum.TEST_PLAN_INDEX_DETAIL,
            locale: 'menu.testPlan.testPlanDetail',
          },
        ],
      },
    },
    // 自定义配置报告
    {
      path: 'testPlanIndexConfig',
      name: TestPlanRouteEnum.TEST_PLAN_INDEX_CONFIG,
      component: () => import('@/views/test-plan/report/detail/configReport.vue'),
      meta: {
        locale: 'testPlan.planConfigReport',
        roles: ['PROJECT_TEST_PLAN_REPORT:READ'],
        isTopMenu: false,
      },
    },
    // 测试计划-测试计划详情-功能用例详情
    {
      path: 'testPlanIndexDetailFeatureCaseDetail',
      name: TestPlanRouteEnum.TEST_PLAN_INDEX_DETAIL_FEATURE_CASE_DETAIL,
      component: () => import('@/views/test-plan/testPlan/detail/featureCase/detail/index.vue'),
      meta: {
        locale: 'menu.testPlan.testPlanDetail',
        roles: ['PROJECT_TEST_PLAN:READ'],
        breadcrumbs: [
          {
            name: TestPlanRouteEnum.TEST_PLAN_INDEX,
            locale: 'menu.testPlan',
          },
          {
            name: TestPlanRouteEnum.TEST_PLAN_INDEX_DETAIL,
            locale: 'menu.testPlan.testPlanDetail',
            isBack: true,
            query: ['id'],
          },
          {
            name: TestPlanRouteEnum.TEST_PLAN_INDEX_DETAIL_FEATURE_CASE_DETAIL,
            locale: 'menu.caseManagement.caseManagementCaseDetail',
          },
        ],
      },
    },
  ],
};

export default TestPlan;
