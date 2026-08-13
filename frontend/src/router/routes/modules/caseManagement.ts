import { CaseManagementRouteEnum, TestPlanRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';
import type { RouteLocationGeneric } from 'vue-router';

const CaseManagement: AppRouteRecordRaw = {
  path: '/case-management',
  name: CaseManagementRouteEnum.CASE_MANAGEMENT,
  redirect: '/case-management/featureCase',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.caseManagement',
    collapsedLocale: 'menu.caseManagementShort',
    icon: 'icon-icon_functional_testing1',
    order: 3,
    hideChildrenInMenu: true,
    roles: ['FUNCTIONAL_CASE:READ', 'CASE_REVIEW:READ', 'FUNCTIONAL_CASE_AI:READ', 'AI_EXECUTION:READ'],
  },
  children: [
    // 功能用例
    {
      path: 'featureCase',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE,
      component: () => import('@/views/case-management/caseManagementFeature/index.vue'),
      meta: {
        locale: 'menu.caseManagementShort',
        roles: ['FUNCTIONAL_CASE:READ'],
        resourceCode: 'FUNCTIONAL_CASE_CASE_TAB',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    // AI 生成用例
    {
      path: 'caseGenerate',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE_GENERATE,
      component: () => import('@/views/case-management/caseGenerate/index.vue'),
      meta: {
        locale: 'menu.caseManagement.caseGenerate',
        roles: ['FUNCTIONAL_CASE_AI:READ'],
        resourceCode: 'FUNCTIONAL_CASE_AI_GENERATE_TAB',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    // 创建用例&编辑用例
    {
      path: 'automation-execution',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_AUTOMATION_EXECUTION,
      component: () => import('@/views/bug-management/automationExecution/index.vue'),
      meta: {
        locale: 'menu.bugManagement.automationExecution',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'FUNCTIONAL_CASE_AUTOMATION_EXECUTION_TAB',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    // 创建用例&编辑用例
    {
      path: 'featureCaseDetail/:mode?',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE_DETAIL,
      component: () => import('@/views/case-management/caseManagementFeature/components/caseDetail.vue'),
      meta: {
        locale: 'menu.caseManagement.featureCaseDetail',
        roles: ['FUNCTIONAL_CASE:READ+ADD', 'FUNCTIONAL_CASE:READ+UPDATE'],
        breadcrumbs: [
          {
            name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE,
            locale: 'menu.caseManagement.featureCase',
          },
          {
            name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE_DETAIL,
            locale: 'menu.caseManagement.featureCaseDetail',
            editTag: 'id',
            editLocale: 'menu.caseManagement.featureCaseEdit',
          },
        ],
      },
    },
    // 创建用例成功
    {
      path: 'featureCaseCreateSuccess',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE_CREATE_SUCCESS,
      component: () => import('@/views/case-management/caseManagementFeature/components/createSuccess.vue'),
      meta: {
        locale: 'menu.caseManagement.featureCaseCreateSuccess',
        roles: ['FUNCTIONAL_CASE:READ+ADD'],
      },
    },
    // 功能用例回收站
    {
      path: 'featureCaseRecycle',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE_RECYCLE,
      component: () => import('@/views/case-management/caseManagementFeature/components/recycleCaseTable.vue'),
      meta: {
        locale: 'menu.caseManagement.featureCaseRecycle',
        roles: ['FUNCTIONAL_CASE:READ'],
        breadcrumbs: [
          {
            name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE,
            locale: 'menu.caseManagement.featureCaseList',
          },
          {
            name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE_RECYCLE,
            locale: 'menu.caseManagement.featureCaseRecycle',
          },
        ],
      },
    },
    // 兼容旧路由 caseReview
    {
      path: 'caseReview',
      redirect: '/case-management/caseManagementReview',
      component: null,
    },
    // 用例评审
    {
      path: 'caseManagementReview',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_REVIEW,
      redirect: (to: RouteLocationGeneric) => ({ name: TestPlanRouteEnum.TEST_PLAN_REVIEW, query: to.query }),
      component: null,
      meta: {
        hideInMenu: true,
      },
    },
    // 创建评审
    {
      path: 'caseManagementReviewCreate',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_REVIEW_CREATE,
      redirect: (to: RouteLocationGeneric) => ({ name: TestPlanRouteEnum.TEST_PLAN_REVIEW_CREATE, query: to.query }),
      component: null,
      meta: {
        hideInMenu: true,
      },
    },
    // 评审详情
    {
      path: 'caseManagementReviewDetail',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_REVIEW_DETAIL,
      redirect: (to: RouteLocationGeneric) => ({ name: TestPlanRouteEnum.TEST_PLAN_REVIEW_DETAIL, query: to.query }),
      component: null,
      meta: {
        hideInMenu: true,
      },
    },
    // 评审详情-用例详情
    {
      path: 'caseManagementReviewDetailCaseDetail',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_REVIEW_DETAIL_CASE_DETAIL,
      redirect: (to: RouteLocationGeneric) => ({
        name: TestPlanRouteEnum.TEST_PLAN_REVIEW_DETAIL_CASE_DETAIL,
        query: to.query,
      }),
      component: null,
      meta: {
        hideInMenu: true,
      },
    },
    // 测试报告
    {
      path: 'testReport',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_TEST_REPORT,
      redirect: (to: RouteLocationGeneric) => ({ name: TestPlanRouteEnum.TEST_PLAN_TEST_REPORT, query: to.query }),
      component: null,
      meta: {
        hideInMenu: true,
      },
    },
    // 测试报告详情/编辑
    {
      path: 'testReportDetail',
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_TEST_REPORT_DETAIL,
      redirect: (to: RouteLocationGeneric) => ({
        name: TestPlanRouteEnum.TEST_PLAN_TEST_REPORT_DETAIL,
        query: to.query,
      }),
      component: null,
      meta: {
        hideInMenu: true,
      },
    },
  ],
};

export default CaseManagement;
