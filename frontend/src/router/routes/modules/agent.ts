import { executionLocation, safeExecutionQuery } from '@/utils/execution-navigation';

import { AgentRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const Agent: AppRouteRecordRaw = {
  path: '/agent',
  name: AgentRouteEnum.AGENT,
  component: DEFAULT_LAYOUT,
  redirect: '/execution/tasks',
  meta: { hideInMenu: true },
  children: [
    {
      path: 'list',
      component: null,
      redirect: (to) => ({ path: '/setting/runtime/list', query: safeExecutionQuery(to.query) }),
      meta: { hideInMenu: true },
    },
    {
      path: 'capability',
      component: null,
      redirect: (to) => ({ path: '/setting/extensions/status', query: safeExecutionQuery(to.query) }),
      meta: { hideInMenu: true },
    },
    {
      path: 'environment-profile',
      component: null,
      redirect: (to) => ({
        path: '/setting/execution-settings/environment-profile',
        query: safeExecutionQuery(to.query),
      }),
      meta: { hideInMenu: true },
    },
    {
      path: 'credential-reference',
      component: null,
      redirect: (to) => ({
        path: '/setting/execution-settings/credential-reference',
        query: safeExecutionQuery(to.query),
      }),
      meta: { hideInMenu: true },
    },
    {
      path: 'model-profile',
      component: null,
      redirect: (to) => ({ path: '/setting/extensions/model-profile', query: safeExecutionQuery(to.query) }),
      meta: { hideInMenu: true },
    },
    {
      path: 'prompt-template',
      component: null,
      redirect: (to) => ({ path: '/setting/extensions/prompt-template', query: safeExecutionQuery(to.query) }),
      meta: { hideInMenu: true },
    },
    {
      path: 'login-profile',
      component: null,
      redirect: (to) => ({ path: '/setting/execution-settings/login-profile', query: safeExecutionQuery(to.query) }),
      meta: { hideInMenu: true },
    },
    {
      path: 'page-object',
      component: null,
      redirect: (to) => ({ path: '/setting/extensions/page-object', query: safeExecutionQuery(to.query) }),
      meta: { hideInMenu: true },
    },
    {
      path: 'business-flow',
      component: null,
      redirect: (to) => ({ path: '/setting/extensions/business-flow', query: safeExecutionQuery(to.query) }),
      meta: { hideInMenu: true },
    },
    {
      path: 'queue',
      component: null,
      redirect: (to) =>
        ['leases', 'triggers'].includes(String(to.query.tab || to.query.queueTab))
          ? {
              path: '/setting/runtime/queue',
              query: { ...safeExecutionQuery(to.query), tab: String(to.query.tab || to.query.queueTab) },
            }
          : executionLocation(to.query),
      meta: { hideInMenu: true },
    },
    {
      path: 'evaluation',
      name: AgentRouteEnum.AGENT_EVALUATION,
      component: null,
      redirect: (to) => ({ path: '/execution/tasks', query: { ...safeExecutionQuery(to.query), view: 'evaluation' } }),
      meta: { hideInMenu: true },
    },
    {
      path: 'execution/detail',
      component: null,
      redirect: (to) => executionLocation(to.query),
      meta: { hideInMenu: true },
    },
    {
      path: 'access',
      component: null,
      redirect: (to) => ({ path: '/setting/personal-access/access', query: safeExecutionQuery(to.query) }),
      meta: { hideInMenu: true },
    },
  ],
};
export default Agent;
