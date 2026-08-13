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
    ...(hasAnyPermission(['FUNCTIONAL_CASE:READ', 'FUNCTIONAL_CASE_AI:READ']) ? [{ key: 'documents', title: '业务文档' }] : []),
    { key: 'versions', title: '资产版本' },
    { key: 'relations', title: '关联追溯' },
  ]);
  const activeKey = computed(() => route.path.split('/').filter(Boolean).at(-1) || 'versions');

  function handleChange(key: string | number) {
    const target = String(key);
    if (target !== activeKey.value) router.push({ path: `/test-assets/${target}` });
  }
</script>
