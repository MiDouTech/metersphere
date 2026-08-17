import { hasAnyPermission } from '@/utils/permission';

import { TestAssetRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const readRoles = [
  'CASE_ASSET:READ',
  'FUNCTIONAL_CASE:READ',
  'FUNCTIONAL_CASE_AI:READ',
  'PROJECT_FILE_MANAGEMENT:READ',
  'PROJECT_ENVIRONMENT:READ',
  'PROJECT_API_SCENARIO:READ',
  'PROJECT_API_DEFINITION:READ',
  'AI_EXECUTION:READ',
  'PROJECT_BUG:READ',
];

function resolveDefaultTestAssetPath() {
  if (hasAnyPermission(['FUNCTIONAL_CASE_AI:READ'])) return '/test-assets/documents';
  if (readRoles.some((permission) => hasAnyPermission([permission]))) return '/test-assets/versions';
  return '/no-resource';
}

const TestAsset: AppRouteRecordRaw = {
  path: '/test-assets',
  name: TestAssetRouteEnum.TEST_ASSET,
  redirect: resolveDefaultTestAssetPath,
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.testAsset',
    collapsedLocale: 'menu.testAssetShort',
    icon: 'icon-icon_functional_testing1',
    order: 2,
    hideChildrenInMenu: true,
    roles: readRoles,
  },
  children: [
    {
      path: 'documents',
      name: TestAssetRouteEnum.TEST_ASSET_DOCUMENTS,
      component: () => import('@/views/test-asset/documents.vue'),
      meta: {
        locale: 'menu.testAsset.documents',
        roles: ['FUNCTIONAL_CASE_AI:READ'],
        resourceCode: 'TEST_ASSET_DOCUMENTS_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'cases',
      name: TestAssetRouteEnum.TEST_ASSET_CASES,
      redirect: '/test-assets/cases/project',
      component: () => import('@/views/test-asset/cases.vue'),
      meta: {
        locale: 'menu.testAsset.cases',
        roles: ['CASE_ASSET:READ'],
        resourceCode: 'TEST_ASSET_CASES_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'datasets',
      name: TestAssetRouteEnum.TEST_ASSET_DATASETS,
      component: () => import('@/views/test-asset/datasets.vue'),
      meta: {
        locale: 'menu.testAsset.datasets',
        roles: ['PROJECT_FILE_MANAGEMENT:READ'],
        resourceCode: 'TEST_ASSET_DATASETS_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'environments',
      name: TestAssetRouteEnum.TEST_ASSET_ENVIRONMENTS,
      component: () => import('@/views/test-asset/environments.vue'),
      meta: {
        locale: 'menu.testAsset.environments',
        roles: ['PROJECT_ENVIRONMENT:READ'],
        resourceCode: 'TEST_ASSET_ENVIRONMENTS_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'versions',
      name: TestAssetRouteEnum.TEST_ASSET_VERSIONS,
      component: () => import('@/views/test-asset/versions.vue'),
      meta: {
        locale: 'menu.testAsset.versions',
        roles: readRoles,
        resourceCode: 'TEST_ASSET_VERSIONS_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'relations',
      name: TestAssetRouteEnum.TEST_ASSET_RELATIONS,
      component: () => import('@/views/test-asset/relations.vue'),
      meta: {
        locale: 'menu.testAsset.relations',
        roles: readRoles,
        resourceCode: 'TEST_ASSET_RELATIONS_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'common-steps',
      name: TestAssetRouteEnum.TEST_ASSET_COMMON_STEPS,
      component: () => import('@/views/test-asset/common-steps.vue'),
      meta: {
        locale: 'menu.testAsset.commonSteps',
        roles: ['PROJECT_API_SCENARIO:READ'],
        resourceCode: 'TEST_ASSET_COMMON_STEPS_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'apis',
      name: TestAssetRouteEnum.TEST_ASSET_APIS,
      component: () => import('@/views/test-asset/apis.vue'),
      meta: {
        locale: 'menu.testAsset.apis',
        roles: ['PROJECT_API_DEFINITION:READ'],
        resourceCode: 'TEST_ASSET_APIS_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'evidence',
      name: TestAssetRouteEnum.TEST_ASSET_EVIDENCE,
      component: () => import('@/views/test-asset/evidence.vue'),
      meta: {
        locale: 'menu.testAsset.evidence',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'TEST_ASSET_EVIDENCE_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'bugs',
      name: TestAssetRouteEnum.TEST_ASSET_BUGS,
      component: () => import('@/views/test-asset/bugs.vue'),
      meta: {
        locale: 'menu.testAsset.bugs',
        roles: ['PROJECT_BUG:READ'],
        resourceCode: 'TEST_ASSET_BUGS_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'cases/project',
      name: TestAssetRouteEnum.TEST_ASSET_CASES_PROJECT,
      component: () => import('@/views/test-asset/cases.vue'),
      meta: {
        locale: 'menu.testAsset.cases',
        roles: ['CASE_ASSET:READ'],
        resourceCode: 'TEST_ASSET_CASE_PROJECT_TAB',
        hideInMenu: true,
        activeMenu: TestAssetRouteEnum.TEST_ASSET_CASES,
      },
    },
    {
      path: 'cases/system',
      name: TestAssetRouteEnum.TEST_ASSET_CASES_SYSTEM,
      component: () => import('@/views/test-asset/cases.vue'),
      meta: {
        locale: 'menu.testAsset.cases',
        roles: ['CASE_ASSET:READ'],
        resourceCode: 'TEST_ASSET_CASE_SYSTEM_TAB',
        hideInMenu: true,
        activeMenu: TestAssetRouteEnum.TEST_ASSET_CASES,
      },
    },
    {
      path: 'cases/:catalogId/:caseId',
      name: TestAssetRouteEnum.TEST_ASSET_CASE_DETAIL,
      component: () => import('@/views/test-asset/asset-case-detail.vue'),
      meta: {
        locale: 'menu.testAsset.cases',
        roles: ['CASE_ASSET:READ'],
        hideInMenu: true,
        activeMenu: TestAssetRouteEnum.TEST_ASSET_CASES,
      },
    },
  ],
};

export default TestAsset;
