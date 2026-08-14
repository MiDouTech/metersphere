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
  const tabs = computed(() => [
    ...(hasAnyPermission(['FUNCTIONAL_CASE_AI:READ']) ? [{ key: 'documents', title: '业务文档' }] : []),
    ...(hasAnyPermission(['CASE_ASSET:READ']) ? [{ key: 'cases', title: '用例资产' }] : []),
    ...(hasAnyPermission(['PROJECT_FILE_MANAGEMENT:READ']) ? [{ key: 'datasets', title: '测试数据' }] : []),
    ...(hasAnyPermission(['PROJECT_ENVIRONMENT:READ']) ? [{ key: 'environments', title: '测试环境' }] : []),
    { key: 'versions', title: '资产版本' },
    { key: 'relations', title: '关联追溯' },
    ...(hasAnyPermission(['PROJECT_API_SCENARIO:READ']) ? [{ key: 'common-steps', title: '公共步骤' }] : []),
    ...(hasAnyPermission(['PROJECT_API_DEFINITION:READ']) ? [{ key: 'apis', title: '接口资产' }] : []),
    ...(hasAnyPermission(['AI_EXECUTION:READ']) ? [{ key: 'evidence', title: '执行证据' }] : []),
    ...(hasAnyPermission(['PROJECT_BUG:READ']) ? [{ key: 'bugs', title: '缺陷资产' }] : []),
  ]);
  const activeKey = computed(() => {
    const segment = route.path.split('/').filter(Boolean).at(-1) || 'versions';
    return ['project', 'system'].includes(segment) ? 'cases' : segment;
  });

  function handleChange(key: string | number) {
    const target = String(key);
    if (target !== activeKey.value) router.push({ path: `/test-assets/${target}` });
  }
</script>
