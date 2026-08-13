<template>
  <AgentPage>
    <div v-if="summaries.length" class="mb-4 grid gap-4 lg:grid-cols-3">
      <MsCard v-for="item in summaries" :key="`${item.executorType}-${item.executorId}`" simple>
        <div class="flex items-center justify-between">
          <div class="font-medium">{{ item.executorType }} / {{ item.executorId || '-' }}</div>
          <a-tag>{{ item.sampleCount || 0 }} 次</a-tag>
        </div>
        <div class="mt-4 grid grid-cols-2 gap-3 text-sm">
          <div>完成率 <strong>{{ percent(item.averageCompletionRate) }}</strong></div>
          <div>证据率 <strong>{{ percent(item.averageEvidenceRate) }}</strong></div>
          <div>通过 <strong>{{ item.successfulRuns || 0 }}</strong></div>
          <div>Agent 失败 <strong>{{ item.agentFailures || 0 }}</strong></div>
        </div>
      </MsCard>
    </div>
    <MsCard simple>
      <div class="mb-4 flex items-center justify-between">
        <div>
          <div class="text-base font-medium">执行评价明细</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]">运行状态与业务结论分开统计，避免产品缺陷被误判为 Agent 故障。</div>
        </div>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
      <a-table :data="evaluations" :loading="loading" row-key="id" :pagination="pagination" @page-change="changePage">
        <a-table-column title="任务 ID" data-index="taskId" :width="220" />
        <a-table-column title="执行器" :width="180">
          <template #cell="{ record }">{{ record.executorType }} / {{ record.executorId || '-' }}</template>
        </a-table-column>
        <a-table-column title="运行状态" data-index="operationalStatus" :width="150" />
        <a-table-column title="业务结论" data-index="businessVerdict" :width="160" />
        <a-table-column title="完成率" :width="110"><template #cell="{ record }">{{ percent(record.completionRate) }}</template></a-table-column>
        <a-table-column title="证据率" :width="110"><template #cell="{ record }">{{ percent(record.evidenceRate) }}</template></a-table-column>
        <a-table-column title="自愈 / 重试" :width="120"><template #cell="{ record }">{{ record.healingCount || 0 }} / {{ record.retryCount || 0 }}</template></a-table-column>
        <a-table-column title="耗时" :width="120"><template #cell="{ record }">{{ duration(record.durationMs) }}</template></a-table-column>
        <a-table-column title="人工评分" :width="100"><template #cell="{ record }">{{ record.manualScore ?? '-' }}</template></a-table-column>
      </a-table>
    </MsCard>
  </AgentPage>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue';

  import AgentPage from './components/AgentPage.vue';
  import { getAiEvaluationSummary, getAiExecutionEvaluations } from '@/api/modules/ai-execution';
  import type { AiEvaluationSummary, AiExecutionEvaluation } from '@/api/modules/ai-execution';
  import { useAppStore } from '@/store';

  const appStore = useAppStore();
  const loading = ref(false);
  const evaluations = ref<AiExecutionEvaluation[]>([]);
  const summaries = ref<AiEvaluationSummary[]>([]);
  const current = ref(1);
  const pageSize = ref(20);
  const total = ref(0);
  const pagination = computed(() => ({ current: current.value, pageSize: pageSize.value, total: total.value, showTotal: true }));
  const percent = (value?: number) => (value === undefined || value === null ? '-' : `${Number(value).toFixed(2)}%`);
  const duration = (value?: number) => (value === undefined || value === null ? '-' : value < 60_000 ? `${Math.round(value / 1000)} 秒` : `${Math.round(value / 60_000)} 分钟`);

  async function load() {
    if (!appStore.currentProjectId) return;
    loading.value = true;
    try {
      const [page, summary] = await Promise.all([
        getAiExecutionEvaluations(appStore.currentProjectId, current.value, pageSize.value),
        getAiEvaluationSummary(appStore.currentProjectId),
      ]);
      evaluations.value = page.list || [];
      total.value = page.total || 0;
      summaries.value = summary || [];
    } finally {
      loading.value = false;
    }
  }
  function changePage(value: number) { current.value = value; load(); }
  watch(() => appStore.currentProjectId, () => { current.value = 1; load(); });
  onMounted(load);
</script>
