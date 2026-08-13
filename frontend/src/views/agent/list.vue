<template>
  <AgentPage>
    <div class="mb-4 grid grid-cols-2 gap-4 lg:grid-cols-5">
      <MsCard v-for="item in metrics" :key="item.label" simple>
        <div class="text-sm text-[var(--color-text-3)]">{{ item.label }}</div>
        <div class="mt-2 text-2xl font-semibold">{{ item.value }}</div>
      </MsCard>
    </div>
    <MsCard simple>
      <div class="mb-4 flex items-center justify-between">
        <div>
          <div class="text-base font-medium">Agent / Runner 实例</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]">在线状态按 90 秒心跳窗口实时计算。</div>
        </div>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
      <a-table :data="runners" :loading="loading" :pagination="false" row-key="id">
        <template #empty><a-empty description="尚未注册 Runner，请前往接入配置完成注册。" /></template>
        <a-table-column title="名称" data-index="name" />
        <a-table-column title="状态" :width="110">
          <template #cell="{ record }">
            <a-tag :color="record.status === 'ONLINE' ? 'green' : record.status === 'STALE' ? 'orange' : 'gray'">
              {{ record.status }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="版本" :width="150">
          <template #cell="{ record }">{{ record.runnerVersion || '-' }} / {{ record.contractVersion || '-' }}</template>
        </a-table-column>
        <a-table-column title="系统" data-index="operatingSystem" :width="140" />
        <a-table-column title="能力">
          <template #cell="{ record }">{{ record.browserCapabilities || '-' }}</template>
        </a-table-column>
        <a-table-column title="并发" :width="110">
          <template #cell="{ record }">{{ record.activeCount || 0 }} / {{ record.maxConcurrency || 0 }}</template>
        </a-table-column>
        <a-table-column title="最后心跳" :width="180">
          <template #cell="{ record }">{{ formatTime(record.lastHeartbeatTime) }}</template>
        </a-table-column>
      </a-table>
    </MsCard>
  </AgentPage>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';
  import dayjs from 'dayjs';

  import AgentPage from './components/AgentPage.vue';
  import { getAiExecutionOperations, getAiRunners } from '@/api/modules/ai-execution';
  import type { AiExecutionOperations, AiRunner } from '@/api/modules/ai-execution';

  const loading = ref(false);
  const runners = ref<AiRunner[]>([]);
  const operations = ref<AiExecutionOperations>();
  const metrics = computed(() => [
    { label: '在线 Agent', value: operations.value?.onlineRunnerCount || 0 },
    { label: '活动租约', value: operations.value?.activeLeaseCount || 0 },
    { label: '排队任务', value: operations.value?.queuedTaskCount || 0 },
    { label: '疑似卡住', value: operations.value?.stuckTaskCount || 0 },
    { label: '回写积压', value: operations.value?.writebackBacklogCount || 0 },
  ]);
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');

  async function load() {
    loading.value = true;
    try {
      const [runnerResult, operationResult] = await Promise.all([getAiRunners(), getAiExecutionOperations()]);
      runners.value = runnerResult || [];
      operations.value = operationResult;
    } finally {
      loading.value = false;
    }
  }
  onMounted(load);
</script>
