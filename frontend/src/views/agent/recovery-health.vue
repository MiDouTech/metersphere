<template>
  <MsCard simple>
    <div class="mb-4 flex justify-between"
      ><span>回写恢复扫描状态</span><a-button :loading="loading" @click="load">刷新</a-button></div
    >
    <a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert>
    <a-alert v-if="runtime" class="mb-4"
      >堆内存 {{ Math.round(Number(runtime.heapUsedBytes) / 1048576) }} MiB /
      {{ Math.round(Number(runtime.heapMaxBytes) / 1048576) }} MiB；线程 {{ runtime.threads }}</a-alert
    >
    <a-table :data="pools" :pagination="false" class="mb-4">
      <template #columns>
        <a-table-column title="连接池" data-index="name" />
        <a-table-column title="使用中" data-index="active" />
        <a-table-column title="空闲" data-index="idle" />
        <a-table-column title="等待连接" data-index="pending" />
      </template>
    </a-table>
    <a-table :data="rows" :loading="loading" :pagination="false">
      <template #empty><a-empty description="暂无恢复扫描状态" /></template>
      <template #columns>
        <a-table-column title="扫描通道" data-index="scanName" />
        <a-table-column title="连续失败" data-index="consecutiveFailures" />
        <a-table-column title="错误分类" data-index="errorCategory" />
        <a-table-column title="最近成功"
          ><template #cell="{ record }">{{ time(record.lastSuccessAt) }}</template></a-table-column
        >
        <a-table-column title="下次扫描"
          ><template #cell="{ record }">{{ time(record.nextScanAt) }}</template></a-table-column
        >
      </template>
    </a-table>
  </MsCard>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, onMounted, ref } from 'vue';
  import dayjs from 'dayjs';

  import MsCard from '@/components/pure/ms-card/index.vue';

  import MSR from '@/api/http';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';

  const rows = ref<Record<string, unknown>[]>([]);
  const runtime = ref<Record<string, unknown>>();
  const pools = ref<Record<string, unknown>[]>([]);
  const loading = ref(false);
  const error = ref('');
  let active = true;
  const time = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  async function load() {
    loading.value = true;
    error.value = '';
    try {
      const [result, snapshot] = await Promise.all([
        MSR.get<Record<string, unknown>[]>({ url: '/ai/recovery/status' }, { errorMessageMode: 'none' }),
        MSR.get<Record<string, unknown>>({ url: '/ai/recovery/status/runtime' }, { errorMessageMode: 'none' }),
      ]);
      if (active) {
        rows.value = result;
        runtime.value = snapshot;
        pools.value = (snapshot.pools || []) as Record<string, unknown>[];
      }
    } catch (failure) {
      if (active) error.value = formatAppErrorMessage(ensureAppError(failure, '扫描状态加载失败，请重试'));
    } finally {
      if (active) loading.value = false;
    }
  }
  onMounted(load);
  onBeforeUnmount(() => {
    active = false;
  });
</script>
