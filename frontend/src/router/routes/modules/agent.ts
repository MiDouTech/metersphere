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
    icon: 'icon-icon_robot',
    order: 7,
    hideChildrenInMenu: true,
    roles: ['AI_EXECUTION:READ', 'SYSTEM_PERSONAL_AI_AGENT:READ'],
  },
  children: [
    {
      path: 'list',
      name: AgentRouteEnum.AGENT_LIST,
      component: () => import('@/views/agent/list.vue'),
      meta: {
        locale: 'Agent 列表',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'AGENT_LIST_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'capability',
      name: AgentRouteEnum.AGENT_CAPABILITY,
      component: () => import('@/views/agent/capability.vue'),
      meta: {
        locale: '能力与授权',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'AGENT_CAPABILITY_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'environment-profile',
      name: 'AgentEnvironmentProfile',
      component: () => import('@/views/agent/environment-profile.vue'),
      meta: {
        locale: '环境执行配置',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'AGENT_ENVIRONMENT_PROFILE_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'credential-reference',
      name: 'AgentCredentialReference',
      component: () => import('@/views/agent/credential-reference.vue'),
      meta: {
        locale: '凭据引用',
        roles: ['AI_CREDENTIAL:READ_METADATA'],
        resourceCode: 'AGENT_CREDENTIAL_REFERENCE_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'model-profile',
      name: 'AgentModelProfile',
      component: () => import('@/views/agent/model-profile.vue'),
      meta: {
        locale: '模型执行配置',
        roles: ['AI_MODEL:READ'],
        resourceCode: 'AGENT_MODEL_PROFILE_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'prompt-template',
      name: 'AgentPromptTemplate',
      component: () => import('@/views/agent/prompt-template.vue'),
      meta: {
        locale: 'Prompt 模板',
        roles: ['AI_MODEL:READ'],
        resourceCode: 'AGENT_PROMPT_TEMPLATE_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'login-profile',
      name: 'AgentLoginProfile',
      component: () => import('@/views/agent/login-profile.vue'),
      meta: {
        locale: '自动登录配置',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'AGENT_LOGIN_PROFILE_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'page-object',
      name: 'AgentPageObject',
      component: () => import('@/views/agent/page-object.vue'),
      meta: {
        locale: 'Page Object',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'AGENT_PAGE_OBJECT_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'business-flow',
      name: 'AgentBusinessFlow',
      component: () => import('@/views/agent/business-flow.vue'),
      meta: {
        locale: '业务流',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'AGENT_BUSINESS_FLOW_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'queue',
      name: AgentRouteEnum.AGENT_QUEUE,
      component: () => import('@/views/agent/queue.vue'),
      meta: {
        locale: '调度队列',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'AGENT_QUEUE_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'evaluation',
      name: AgentRouteEnum.AGENT_EVALUATION,
      component: () => import('@/views/agent/evaluation.vue'),
      meta: {
        locale: '执行评价',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'AGENT_EVALUATION_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
    {
      path: 'execution/detail',
      name: 'AgentExecutionDetail',
      component: () => import('@/views/agent/execution/detail.vue'),
      meta: {
        locale: 'AI 执行详情',
        roles: ['AI_EXECUTION:READ'],
        resourceCode: 'AGENT_EXECUTION_DETAIL_PAGE',
        hideInMenu: true,
      },
    },
    {
      path: 'access',
      name: AgentRouteEnum.AGENT_ACCESS,
      component: () => import('@/views/agent/access.vue'),
      meta: {
        locale: 'Agent 集成',
        roles: ['SYSTEM_PERSONAL_AI_AGENT:READ'],
        resourceCode: 'AGENT_INTEGRATION_PAGE',
        isTopMenu: true,
        keepModuleAlive: true,
      },
    },
  ],
};

export default Agent;
