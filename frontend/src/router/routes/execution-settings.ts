import { AgentRouteEnum } from '@/enums/routeEnum';

import type { AppRouteRecordRaw } from './types';

// Compatibility routes only. Retired groups must not appear in any navigation.
const executionSettings: AppRouteRecordRaw[] = [
  {
    path: 'personal-access/access',
    name: AgentRouteEnum.AGENT_ACCESS,
    component: () => import('@/views/agent/access.vue'),
    meta: { hideInMenu: true, roles: ['SYSTEM_PERSONAL_AI_AGENT:READ'], resourceCode: 'AGENT_INTEGRATION_PAGE' },
  },
  { path: 'personal-access', redirect: '/setting/personal-access/access', component: null, meta: { hideInMenu: true } },
  ...[
    ['environment-profile', 'AgentEnvironmentProfile', 'platform'],
    ['login-profile', 'AgentLoginProfile', 'login'],
    ['credential-reference', 'AgentCredentialReference', 'credentials'],
  ].map(([path, name, tab]) => ({
    path: `execution-settings/${path}`,
    name,
    component: null,
    redirect: { path: '/execution/platforms', query: { tab } },
    meta: { hideInMenu: true },
  })),
  {
    path: 'execution-settings/quality-policy',
    name: 'ExecutionQualityPolicy',
    component: null,
    redirect: '/execution/quality-policy',
    meta: { hideInMenu: true },
  },
  {
    path: 'runtime/queue',
    name: AgentRouteEnum.AGENT_QUEUE,
    component: null,
    redirect: { path: '/execution/tasks', query: { view: 'schedule' } },
    meta: { hideInMenu: true },
  },
  ...[
    ['execution-settings/governance', 'ExecutionGovernance'],
    ['runtime/list', AgentRouteEnum.AGENT_LIST],
    ['runtime/capability', AgentRouteEnum.AGENT_CAPABILITY],
    ['extensions/model-profile', 'AgentModelProfile'],
    ['extensions/prompt-template', 'AgentPromptTemplate'],
    ['extensions/page-object', 'AgentPageObject'],
    ['extensions/business-flow', 'AgentBusinessFlow'],
    ['extensions/status', 'ExecutionCapabilities'],
  ].map(([path, name]) => ({
    path,
    name,
    component: () => import('@/views/execution/retired.vue'),
    meta: { hideInMenu: true },
  })),
  ...['execution-settings', 'runtime', 'extensions'].map((path) => ({
    path,
    component: () => import('@/views/execution/retired.vue'),
    meta: { hideInMenu: true },
  })),
];
export default executionSettings;
