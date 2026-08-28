<template>
  <AgentPage>
    <a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert>
    <div v-if="summaries.length" class="mb-4 grid gap-4 lg:grid-cols-3">
      <MsCard v-for="item in summaries" :key="`${item.executorType}-${item.executorId}`" simple>
        <div class="flex items-center justify-between">
          <div class="font-medium">{{ item.executorType }} / {{ item.executorId || '-' }}</div>
          <a-tag>{{ item.sampleCount || 0 }} 次</a-tag>
        </div>
        <div class="mt-4 grid grid-cols-2 gap-3 text-sm">
          <div
            >完成率 <strong>{{ percent(item.averageCompletionRate) }}</strong></div
          >
          <div
            >证据率 <strong>{{ percent(item.averageEvidenceRate) }}</strong></div
          >
          <div
            >通过 <strong>{{ item.successfulRuns || 0 }}</strong></div
          >
          <div
            >Agent 失败 <strong>{{ item.agentFailures || 0 }}</strong></div
          >
        </div>
      </MsCard>
    </div>
    <MsCard simple>
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <a-select
          v-model="filters.operationalStatus"
          class="w-[180px]"
          allow-clear
          placeholder="运行状态"
          @change="search"
        >
          <a-option v-for="status in operationalStatuses" :key="status" :value="status">{{ status }}</a-option>
        </a-select>
        <a-select
          v-model="filters.businessVerdict"
          class="w-[180px]"
          allow-clear
          placeholder="业务结论"
          @change="search"
        >
          <a-option v-for="verdict in businessVerdicts" :key="verdict" :value="verdict">{{ verdict }}</a-option>
        </a-select>
        <a-select
          v-model="filters.executorType"
          class="w-[150px]"
          allow-clear
          placeholder="执行器类型"
          @change="search"
        >
          <a-option value="RUNNER">RUNNER</a-option><a-option value="AGENT">AGENT</a-option>
        </a-select>
      </div>
      <div class="mb-4 flex items-center justify-between">
        <div>
          <div class="text-base font-medium">执行评价明细</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]"
            >运行状态与业务结论分开统计，避免产品缺陷被误判为 Agent 故障。</div
          >
        </div>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
      <a-table :data="evaluations" :loading="loading" row-key="id" :pagination="pagination" @page-change="changePage">
        <template #columns>
          <a-table-column title="任务 ID" data-index="taskId" :width="220" />
          <a-table-column title="执行器" :width="180">
            <template #cell="{ record }">{{ record.executorType }} / {{ record.executorId || '-' }}</template>
          </a-table-column>
          <a-table-column title="运行状态" data-index="operationalStatus" :width="150" />
          <a-table-column title="业务结论" data-index="businessVerdict" :width="160" />
          <a-table-column title="完成率" :width="110"
            ><template #cell="{ record }">{{ percent(record.completionRate) }}</template></a-table-column
          >
          <a-table-column title="证据率" :width="110"
            ><template #cell="{ record }">{{ percent(record.evidenceRate) }}</template></a-table-column
          >
          <a-table-column title="自愈 / 重试" :width="120"
            ><template #cell="{ record }"
              >{{ record.healingCount || 0 }} / {{ record.retryCount || 0 }}</template
            ></a-table-column
          >
          <a-table-column title="耗时" :width="120"
            ><template #cell="{ record }">{{ duration(record.durationMs) }}</template></a-table-column
          >
          <a-table-column title="人工评分" :width="100"
            ><template #cell="{ record }">{{ record.manualScore ?? '-' }}</template></a-table-column
          >
          <a-table-column title="操作" :width="150"
            ><template #cell="{ record }"
              ><a-space
                ><a-link v-permission="['AI_EXECUTION:RUN']" @click="openEvaluation(record)">评价</a-link
                ><a-link @click="openHistory(record)">历史</a-link></a-space
              ></template
            ></a-table-column
          >
        </template>
      </a-table>
    </MsCard>
    <a-modal v-model:visible="evaluationVisible" title="人工评价" :ok-loading="saving" @before-ok="saveEvaluation">
      <a-form :model="evaluationForm" layout="vertical">
        <a-form-item label="评分（0-100）" required
          ><a-input-number v-model="evaluationForm.score" :min="0" :max="100" class="w-full"
        /></a-form-item>
        <a-form-item label="评价说明"
          ><a-textarea v-model="evaluationForm.comment" :max-length="2000" show-word-limit
        /></a-form-item>
      </a-form>
    </a-modal>
    <a-drawer v-model:visible="historyVisible" title="人工评价历史" :width="640">
      <a-table :data="history" :loading="historyLoading" :pagination="false" row-key="id">
        <template #columns>
          <a-table-column title="评分" data-index="score" :width="90" />
          <a-table-column title="评价说明" data-index="comment" />
          <a-table-column title="评价人" data-index="evaluatedBy" :width="150" />
          <a-table-column title="评价时间" :width="180"
            ><template #cell="{ record }">{{ formatTime(record.evaluatedAt) }}</template></a-table-column
          >
        </template>
      </a-table>
    </a-drawer>
  </AgentPage>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import dayjs from 'dayjs';

  import AgentPage from './components/AgentPage.vue';

  import type { AiEvaluationHistory, AiEvaluationSummary, AiExecutionEvaluation } from '@/api/modules/ai-execution';
  import {
    getAiEvaluationHistory,
    getAiEvaluationSummary,
    getAiExecutionEvaluations,
    manualEvaluateAiExecution,
  } from '@/api/modules/ai-execution';
  import { useAppStore } from '@/store';

  const appStore = useAppStore();
  const loading = ref(false);
  const error = ref('');
  const evaluations = ref<AiExecutionEvaluation[]>([]);
  const summaries = ref<AiEvaluationSummary[]>([]);
  const current = ref(1);
  const pageSize = ref(20);
  const total = ref(0);
  const evaluationVisible = ref(false);
  const saving = ref(false);
  const historyVisible = ref(false);
  const historyLoading = ref(false);
  const history = ref<AiEvaluationHistory[]>([]);
  const filters = reactive({
    operationalStatus: undefined as string | undefined,
    businessVerdict: undefined as string | undefined,
    executorType: undefined as string | undefined,
  });
  const operationalStatuses = ['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELED', 'EXPIRED'];
  const businessVerdicts = [
    'PASSED',
    'PRODUCT_FAILED',
    'ENV_FAILED',
    'DATA_FAILED',
    'AGENT_FAILED',
    'BLOCKED',
    'CANCELED',
  ];
  const evaluationForm = ref({ taskId: '', score: 100, comment: '' });
  const pagination = computed(() => ({
    current: current.value,
    pageSize: pageSize.value,
    total: total.value,
    showTotal: true,
  }));
  const percent = (value?: number) =>
    value === undefined || value === null ? '-' : `${(Number(value) * 100).toFixed(2)}%`;
  function duration(value?: number) {
    if (value === undefined || value === null) return '-';
    if (value < 60_000) return `${Math.round(value / 1000)} 秒`;
    return `${Math.round(value / 60_000)} 分钟`;
  }

  async function load() {
    if (!appStore.currentProjectId) return;
    loading.value = true;
    error.value = '';
    try {
      const [page, summary] = await Promise.all([
        getAiExecutionEvaluations(appStore.currentProjectId, current.value, pageSize.value, { ...filters }),
        getAiEvaluationSummary(appStore.currentProjectId),
      ]);
      evaluations.value = page.list || [];
      total.value = page.total || 0;
      summaries.value = summary || [];
    } catch (reason: any) {
      error.value = reason?.message || '执行评价加载失败，请稍后重试';
    } finally {
      loading.value = false;
    }
  }
  function changePage(value: number) {
    current.value = value;
    load();
  }
  function search() {
    current.value = 1;
    load();
  }
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  async function openHistory(record: AiExecutionEvaluation) {
    historyVisible.value = true;
    historyLoading.value = true;
    try {
      history.value = await getAiEvaluationHistory(record.taskId);
    } finally {
      historyLoading.value = false;
    }
  }
  function openEvaluation(record: AiExecutionEvaluation) {
    evaluationForm.value = {
      taskId: record.taskId,
      score: Number(record.manualScore ?? 100),
      comment: record.manualComment || '',
    };
    evaluationVisible.value = true;
  }
  async function saveEvaluation(done: (closed: boolean) => void) {
    saving.value = true;
    try {
      await manualEvaluateAiExecution(
        evaluationForm.value.taskId,
        evaluationForm.value.score,
        evaluationForm.value.comment.trim() || undefined
      );
      await load();
      done(true);
    } catch {
      done(false);
    } finally {
      saving.value = false;
    }
  }
  watch(
    () => appStore.currentProjectId,
    () => {
      current.value = 1;
      load();
    }
  );
  onMounted(load);
</script>
