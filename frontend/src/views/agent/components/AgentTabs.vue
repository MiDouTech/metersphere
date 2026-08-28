<template>
  <div class="mb-4">
    <a-tabs :active-key="activeKey" class="no-content" @change="handleChange">
      <a-tab-pane v-for="tab in tabs" :key="tab.key" :title="tab.title" />
    </a-tabs>
    <a-divider :margin="0" />
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { useRoute, useRouter } from 'vue-router';

  import { hasAnyPermission } from '@/utils/permission';

  const route = useRoute();
  const router = useRouter();
  const tabs = computed(() =>
    [
      { key: 'list', title: 'Agent 列表', permissions: ['AI_EXECUTION:READ'] },
      { key: 'capability', title: '能力与授权', permissions: ['AI_EXECUTION:READ'] },
      { key: 'environment-profile', title: '环境执行配置', permissions: ['AI_EXECUTION:READ'] },
      { key: 'credential-reference', title: '凭据引用', permissions: ['AI_CREDENTIAL:READ_METADATA'] },
      { key: 'model-profile', title: '模型执行配置', permissions: ['AI_MODEL:READ'] },
      { key: 'prompt-template', title: 'Prompt 模板', permissions: ['AI_MODEL:READ'] },
      { key: 'login-profile', title: '自动登录配置', permissions: ['AI_EXECUTION:READ'] },
      { key: 'page-object', title: 'Page Object', permissions: ['AI_EXECUTION:READ'] },
      { key: 'business-flow', title: '业务流', permissions: ['AI_EXECUTION:READ'] },
      { key: 'queue', title: '调度队列', permissions: ['AI_EXECUTION:READ'] },
      { key: 'evaluation', title: '执行评价', permissions: ['AI_EXECUTION:READ'] },
      { key: 'access', title: 'Agent 集成', permissions: ['SYSTEM_PERSONAL_AI_AGENT:READ'] },
    ].filter((tab) => hasAnyPermission(tab.permissions))
  );
  const activeKey = computed(() => route.path.split('/').filter(Boolean).at(-1) || 'list');

  function handleChange(key: string | number) {
    const target = String(key);
    if (target !== activeKey.value) {
      router.push({ path: `/agent/${target}` });
    }
  }
</script>
