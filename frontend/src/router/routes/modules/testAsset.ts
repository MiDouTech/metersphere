import { TestAssetRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const readRoles = ['FUNCTIONAL_CASE:READ', 'FUNCTIONAL_CASE_AI:READ'];

const TestAsset: AppRouteRecordRaw = {
  path: '/test-assets',
  name: TestAssetRouteEnum.TEST_ASSET,
  redirect: '/test-assets/versions',
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
      meta: { locale: 'menu.testAsset.documents', roles: ['FUNCTIONAL_CASE:READ', 'FUNCTIONAL_CASE_AI:READ'], isTopMenu: true, keepModuleAlive: true },
    },
    {
      path: 'versions',
      name: TestAssetRouteEnum.TEST_ASSET_VERSIONS,
      component: () => import('@/views/test-asset/versions.vue'),
      meta: { locale: 'menu.testAsset.versions', roles: readRoles, isTopMenu: true, keepModuleAlive: true },
    },
    {
      path: 'relations',
      name: TestAssetRouteEnum.TEST_ASSET_RELATIONS,
      component: () => import('@/views/test-asset/relations.vue'),
      meta: { locale: 'menu.testAsset.relations', roles: readRoles, isTopMenu: true, keepModuleAlive: true },
    },
  ],
};

export default TestAsset;
