<template>
  <div>
    <a-tabs
      v-if="!route.params.id"
      :active-key="route.query.view === 'schedule' ? 'schedule' : 'tasks'"
      @change="changeView"
    >
      <a-tab-pane key="tasks" title="执行记录" />
      <a-tab-pane key="schedule" title="定时任务" />
    </a-tabs>
    <Schedule v-if="route.query.view === 'schedule' && !route.params.id" schedule-only />
    <AutomationExecution v-else />
    <a-collapse
      v-if="!route.params.id && route.query.view !== 'schedule' && route.query.creating !== '1' && canEvaluate"
      v-model:active-key="expanded"
      class="m-4"
    >
      <a-collapse-item key="evaluation" header="执行统计与历史评价（不代表审批）">
        <Evaluation v-if="expanded.includes('evaluation')" />
      </a-collapse-item>
    </a-collapse>
  </div>
</template>

<script setup lang="ts">
  import { computed, defineAsyncComponent, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';

  import Evaluation from '@/views/agent/evaluation.vue';
  import AutomationExecution from '@/views/bug-management/automationExecution/index.vue';

  import { hasAnyPermission, hasPageVisible, hasRouteVisible } from '@/utils/permission';

  const route = useRoute();
  const router = useRouter();
  const Schedule = defineAsyncComponent(() => import('@/views/agent/queue.vue'));
  function changeView(view: string | number) {
    router.replace({ query: { ...route.query, view: view === 'schedule' ? 'schedule' : undefined } });
  }
  const expanded = ref<string[]>([]);
  const canEvaluate = computed(
    () =>
      hasAnyPermission(['AI_EXECUTION:READ']) &&
      hasPageVisible('AGENT_EVALUATION_PAGE') &&
      hasRouteVisible('agentEvaluation')
  );
  watch(
    () => route.query.view,
    (value) => {
      expanded.value = value === 'evaluation' ? ['evaluation'] : [];
    },
    { immediate: true }
  );
</script>
