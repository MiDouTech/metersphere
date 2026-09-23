<template>
  <div>
    <AutomationExecution />
    <a-collapse
      v-if="!route.params.id && route.query.creating !== '1' && canEvaluate"
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
  import { computed, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';

  import Evaluation from '@/views/agent/evaluation.vue';
  import AutomationExecution from '@/views/bug-management/automationExecution/index.vue';

  import { hasAnyPermission, hasPageVisible, hasRouteVisible } from '@/utils/permission';

  const route = useRoute();
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
