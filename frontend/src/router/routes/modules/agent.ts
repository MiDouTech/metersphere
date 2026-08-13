import { AgentRouteEnum } from '@/enums/routeEnum';

import { DEFAULT_LAYOUT } from '../base';
import type { AppRouteRecordRaw } from '../types';

const Agent: AppRouteRecordRaw = {
  path: '/agent',
  name: AgentRouteEnum.AGENT,
  redirect: '/agent/list',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'Agent',
    collapsedLocale: 'Agent',
    icon: 'icon-icon_ai_assistant',
    order: 7,
    hideChildrenInMenu: true,
    roles: ['AI_EXECUTION:READ'],
  },
  children: [
    {
      path: 'list',
      name: AgentRouteEnum.AGENT_LIST,
      component: () => import('@/views/agent/list.vue'),
      meta: { locale: 'Agent 列表', roles: ['AI_EXECUTION:READ'], isTopMenu: true, keepModuleAlive: true },
    },
    {
      path: 'capability',
      name: AgentRouteEnum.AGENT_CAPABILITY,
      component: () => import('@/views/agent/capability.vue'),
      meta: { locale: '能力与授权', roles: ['AI_EXECUTION:READ'], isTopMenu: true, keepModuleAlive: true },
    },
    {
      path: 'queue',
      name: AgentRouteEnum.AGENT_QUEUE,
      component: () => import('@/views/agent/queue.vue'),
      meta: { locale: '调度队列', roles: ['AI_EXECUTION:READ'], isTopMenu: true, keepModuleAlive: true },
    },
    {
      path: 'evaluation',
      name: AgentRouteEnum.AGENT_EVALUATION,
      component: () => import('@/views/agent/evaluation.vue'),
      meta: { locale: '执行评价', roles: ['AI_EXECUTION:READ'], isTopMenu: true, keepModuleAlive: true },
    },
    {
      path: 'access',
      name: AgentRouteEnum.AGENT_ACCESS,
      component: () => import('@/views/agent/access.vue'),
      meta: { locale: '接入配置', roles: ['AI_EXECUTION:READ'], isTopMenu: true, keepModuleAlive: true },
    },
  ],
};

export default Agent;
