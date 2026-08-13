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

  const route = useRoute();
  const router = useRouter();
  const tabs = [
    { key: 'list', title: 'Agent 列表' },
    { key: 'capability', title: '能力与授权' },
    { key: 'queue', title: '调度队列' },
    { key: 'evaluation', title: '执行评价' },
    { key: 'access', title: '接入配置' },
  ];
  const activeKey = computed(() => route.path.split('/').filter(Boolean).at(-1) || 'list');

  function handleChange(key: string | number) {
    const target = String(key);
    if (target !== activeKey.value) {
      router.push({ path: `/agent/${target}` });
    }
  }
</script>
