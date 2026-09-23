import { hasAnyPermission, hasPageVisible } from '@/utils/permission';

import { AgentRouteEnum } from '@/enums/routeEnum';

import type { AppRouteRecordRaw } from './types';

const executionSettings: AppRouteRecordRaw[] = [
  {
    path: 'personal-access',
    name: 'SettingPersonalAccess',
    component: null,
    redirect: '/setting/personal-access/access',
    meta: { locale: '个人接入', roles: ['SYSTEM_PERSONAL_AI_AGENT:READ'], hideChildrenInMenu: true },
    children: [
      {
        path: 'access',
        name: AgentRouteEnum.AGENT_ACCESS,
        component: () => import('@/views/agent/access.vue'),
        meta: {
          locale: '个人接入',
          roles: ['SYSTEM_PERSONAL_AI_AGENT:READ'],
          resourceCode: 'AGENT_INTEGRATION_PAGE',
          isTopMenu: true,
          keepModuleAlive: true,
        },
      },
    ],
  },
  {
    path: 'execution-settings',
    name: 'SettingExecutionSettings',
    component: null,
    redirect: () =>
      hasAnyPermission(['QUALITY:READ']) && hasPageVisible('EXECUTION_QUALITY_POLICY_PAGE')
        ? '/setting/execution-settings/quality-policy'
        : '/setting/execution-settings/environment-profile',
    meta: {
      locale: '项目执行设置',
      roles: [
        'AI_EXECUTION:READ',
        'QUALITY:READ',
        'FUNCTIONAL_CASE_AI:CONFIG',
        'AI_MODEL:READ',
        'AI_CREDENTIAL:READ_METADATA',
      ],
      hideChildrenInMenu: true,
    },
    children: [
      {
        path: 'environment-profile',
        name: 'AgentEnvironmentProfile',
        component: () => import('@/views/agent/environment-profile.vue'),
        meta: {
          adminOnly: true,
          locale: '环境执行配置',
          roles: ['AI_EXECUTION:READ'],
          resourceCode: 'AGENT_ENVIRONMENT_PROFILE_PAGE',
          isTopMenu: true,
          keepModuleAlive: true,
        },
      },
      {
        path: 'login-profile',
        name: 'AgentLoginProfile',
        component: () => import('@/views/agent/login-profile.vue'),
        meta: {
          adminOnly: true,
          locale: '自动登录配置',
          roles: ['AI_EXECUTION:READ'],
          resourceCode: 'AGENT_LOGIN_PROFILE_PAGE',
          isTopMenu: true,
          keepModuleAlive: true,
        },
      },
      {
        path: 'credential-reference',
        name: 'AgentCredentialReference',
        component: () => import('@/views/agent/credential-reference.vue'),
        meta: {
          adminOnly: true,
          locale: '凭据引用',
          roles: ['AI_CREDENTIAL:READ_METADATA'],
          resourceCode: 'AGENT_CREDENTIAL_REFERENCE_PAGE',
          isTopMenu: true,
          keepModuleAlive: true,
        },
      },
      {
        path: 'quality-policy',
        name: 'ExecutionQualityPolicy',
        component: () => import('@/views/execution/quality-policy.vue'),
        meta: {
          locale: 'menu.executionQuality.policy',
          roles: ['QUALITY:READ'],
          resourceCode: 'EXECUTION_QUALITY_POLICY_PAGE',
          isTopMenu: true,
        },
      },
      {
        path: 'governance',
        name: 'ExecutionGovernance',
        component: () => import('@/views/agent/capability.vue'),
        props: { section: 'policy' },
        meta: {
          locale: '项目执行策略',
          adminOnly: true,
          roles: ['FUNCTIONAL_CASE_AI:CONFIG'],
          resourceCode: 'AGENT_CAPABILITY_PAGE',
          isTopMenu: true,
        },
      },
    ],
  },
  {
    path: 'runtime',
    name: 'SettingRuntime',
    component: null,
    redirect: '/setting/runtime/list',
    meta: {
      locale: '系统运行维护',
      roles: [
        'AI_EXECUTION:READ',
        'QUALITY:READ',
        'FUNCTIONAL_CASE_AI:CONFIG',
        'AI_MODEL:READ',
        'AI_CREDENTIAL:READ_METADATA',
      ],
      hideChildrenInMenu: true,
      adminOnly: true,
    },
    children: [
      {
        path: 'list',
        name: AgentRouteEnum.AGENT_LIST,
        component: () => import('@/views/agent/list.vue'),
        meta: {
          adminOnly: true,
          locale: '执行器管理',
          roles: ['AI_EXECUTION:READ'],
          resourceCode: 'AGENT_LIST_PAGE',
          isTopMenu: true,
          keepModuleAlive: true,
        },
      },
      {
        path: 'queue',
        name: AgentRouteEnum.AGENT_QUEUE,
        component: () => import('@/views/agent/queue.vue'),
        meta: {
          adminOnly: true,
          locale: '租约与调度',
          roles: ['AI_EXECUTION:READ'],
          resourceCode: 'AGENT_QUEUE_PAGE',
          isTopMenu: true,
          keepModuleAlive: true,
        },
      },
      {
        path: 'capability',
        name: AgentRouteEnum.AGENT_CAPABILITY,
        component: () => import('@/views/agent/capability.vue'),
        props: { section: 'alerts' },
        meta: {
          adminOnly: true,
          locale: '运行告警',
          roles: ['AI_EXECUTION:READ'],
          resourceCode: 'AGENT_CAPABILITY_PAGE',
          isTopMenu: true,
          keepModuleAlive: true,
        },
      },
    ],
  },
  {
    path: 'extensions',
    name: 'SettingExtensions',
    component: null,
    redirect: '/setting/extensions/model-profile',
    meta: {
      locale: '扩展能力',
      roles: [
        'AI_EXECUTION:READ',
        'QUALITY:READ',
        'FUNCTIONAL_CASE_AI:CONFIG',
        'AI_MODEL:READ',
        'AI_CREDENTIAL:READ_METADATA',
      ],
      hideChildrenInMenu: true,
      adminOnly: true,
    },
    children: [
      {
        path: 'model-profile',
        name: 'AgentModelProfile',
        component: () => import('@/views/agent/model-profile.vue'),
        meta: {
          adminOnly: true,
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
          adminOnly: true,
          locale: 'Prompt 模板',
          roles: ['AI_MODEL:READ'],
          resourceCode: 'AGENT_PROMPT_TEMPLATE_PAGE',
          isTopMenu: true,
          keepModuleAlive: true,
        },
      },
      {
        path: 'page-object',
        name: 'AgentPageObject',
        component: () => import('@/views/agent/page-object.vue'),
        meta: {
          adminOnly: true,
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
          adminOnly: true,
          locale: '业务流',
          roles: ['AI_EXECUTION:READ'],
          resourceCode: 'AGENT_BUSINESS_FLOW_PAGE',
          isTopMenu: true,
          keepModuleAlive: true,
        },
      },
      {
        path: 'status',
        name: 'ExecutionCapabilities',
        component: () => import('@/views/agent/capability.vue'),
        props: { section: 'capabilities' },
        meta: {
          locale: '接入状态',
          adminOnly: true,
          roles: ['AI_EXECUTION:READ'],
          resourceCode: 'AGENT_CAPABILITY_PAGE',
          isTopMenu: true,
        },
      },
    ],
  },
];

export default executionSettings;
