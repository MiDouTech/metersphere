<template>
  <div class="p-4">
    <a-alert class="mb-4">高级执行诊断。个人 Agent 直接提交用例结果无需配置这些项目。</a-alert>
    <a-select v-model="section" class="mb-4 w-[240px]">
      <a-option v-for="item in sections" :key="item.key" :value="item.key">{{ item.label }}</a-option>
    </a-select>
    <component :is="selected" v-if="selected" />
  </div>
</template>

<script setup lang="ts">
  import { computed, defineAsyncComponent } from 'vue';
  import { useRoute, useRouter } from 'vue-router';

  const route = useRoute();
  const router = useRouter();
  const sections = [
    {
      key: 'recovery',
      label: '回写恢复状态',
      component: defineAsyncComponent(() => import('@/views/agent/recovery-health.vue')),
    },
    { key: 'runners', label: '执行器', component: defineAsyncComponent(() => import('@/views/agent/list.vue')) },
    {
      key: 'models',
      label: '模型配置',
      component: defineAsyncComponent(() => import('@/views/agent/model-profile.vue')),
    },
    {
      key: 'prompts',
      label: 'Prompt 模板',
      component: defineAsyncComponent(() => import('@/views/agent/prompt-template.vue')),
    },
    {
      key: 'page-objects',
      label: 'Page Object',
      component: defineAsyncComponent(() => import('@/views/agent/page-object.vue')),
    },
    { key: 'flows', label: '业务流', component: defineAsyncComponent(() => import('@/views/agent/business-flow.vue')) },
    {
      key: 'runtime',
      label: '运行诊断',
      component: defineAsyncComponent(() => import('@/views/agent/capability.vue')),
    },
  ];
  const section = computed({
    get: () => (sections.some((item) => item.key === route.params.section) ? String(route.params.section) : 'runners'),
    set: (value: string) => {
      router.replace({ name: 'ExecutionDiagnostics', params: { section: value } });
    },
  });
  const selected = computed(() => sections.find((item) => item.key === section.value)?.component);
</script>
