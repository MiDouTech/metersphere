<template>
  <a-drawer
    :visible="visible"
    title="导入预检与任务"
    :width="760"
    :footer="false"
    unmount-on-close
    @cancel="emit('update:visible', false)"
  >
    <a-alert class="mb-3"
      >预检已固定文件、模板和覆盖目标。提交后在后台逐条导入；关闭窗口不会停止任务，停止会保留已成功写入的用例。</a-alert
    >
    <a-alert v-if="error" type="error" class="mb-3">{{ error }}</a-alert>
    <template v-if="job">
      <p>{{ job.fileName }} · {{ labels[job.state] || job.state }} · 共 {{ job.total }} 条</p>
      <a-space class="my-3" wrap>
        <span v-for="count in job.counts" :key="count.state"
          >{{ labels[count.state] || count.state }}：{{ count.total }}</span
        >
      </a-space>
      <a-space class="mb-3" wrap>
        <a-button v-if="job.state === 'PRECHECKED'" type="primary" :loading="saving" @click="action('commit')"
          >提交有效用例</a-button
        >
        <a-button
          v-if="['PRECHECKED', 'QUEUED', 'RUNNING'].includes(job.state)"
          status="danger"
          :loading="saving"
          @click="action('stop')"
          >停止导入</a-button
        >
        <a-button v-if="['STOPPED', 'PARTIAL', 'FAILED'].includes(job.state)" :loading="saving" @click="action('retry')"
          >重试未成功条目</a-button
        >
        <a-button :loading="loading" :disabled="saving" @click="load">刷新</a-button>
      </a-space>
      <a-table :data="job.items" row-key="position" :pagination="false">
        <template #columns>
          <a-table-column title="条目" data-index="position" :width="70" />
          <a-table-column title="处理"
            ><template #cell="{ record }">{{ labels[record.action] || record.action }}</template></a-table-column
          >
          <a-table-column title="状态"
            ><template #cell="{ record }">{{ labels[record.state] || record.state }}</template></a-table-column
          >
          <a-table-column title="说明" data-index="message" />
        </template>
      </a-table>
      <a-pagination
        class="mt-4"
        v-bind="paging.pagination"
        @change="paging.onPageChange"
        @page-size-change="paging.onPageSizeChange"
      />
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';

  import MSR from '@/api/http';
  import useClientTablePaging from '@/hooks/useClientTablePaging';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';

  const props = defineProps<{ jobId: string; visible: boolean }>();
  const emit = defineEmits<{ (event: 'update:visible', value: boolean): void; (event: 'changed'): void }>();
  interface Job {
    jobId: string;
    state: string;
    fileName: string;
    total: number;
    counts: { state: string; total: number }[];
    items: { position: number; action: string; state: string; message?: string }[];
  }
  const job = ref<Job>();
  const paging = useClientTablePaging(
    'case-import-items',
    () => job.value?.total || 0,
    () => props.jobId
  );
  const loading = ref(false);
  const saving = ref(false);
  const error = ref('');
  const labels: Record<string, string> = {
    PRECHECKED: '等待提交',
    QUEUED: '排队中',
    RUNNING: '导入中',
    COMPLETED: '全部处理完成',
    PARTIAL: '部分未成功',
    STOPPED: '已停止',
    EXPIRED: '已过期',
    FAILED: '失败',
    PENDING: '待处理',
    SUCCESS: '成功',
    INVALID: '校验不通过',
    SKIPPED: '未覆盖，已跳过',
    CREATE: '新增',
    UPDATE: '覆盖',
  };
  let sequence = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let previous = '';
  function clearTimer() {
    if (timer) clearTimeout(timer);
    timer = undefined;
  }
  async function load() {
    if (!props.visible || !props.jobId) return;
    clearTimer();
    const request = ++sequence;
    loading.value = true;
    error.value = '';
    try {
      const result = await MSR.get<Job>({
        url: `/ai/case-imports/${encodeURIComponent(props.jobId)}`,
        params: { current: paging.value.pagination.current, pageSize: paging.value.pagination.pageSize },
      });
      if (request !== sequence) return;
      job.value = result;
      if (previous && previous !== result.state && ['COMPLETED', 'PARTIAL', 'STOPPED'].includes(result.state))
        emit('changed');
      previous = result.state;
      if (['QUEUED', 'RUNNING'].includes(result.state)) timer = setTimeout(load, 3000);
    } catch (failure) {
      if (request === sequence)
        error.value = formatAppErrorMessage(ensureAppError(failure, '无法查询导入状态，请重试'));
    } finally {
      if (request === sequence) loading.value = false;
    }
  }
  async function action(value: string) {
    if (saving.value) return;
    const id = props.jobId;
    const captured = sequence;
    saving.value = true;
    clearTimer();
    try {
      await MSR.post({ url: `/ai/case-imports/${encodeURIComponent(id)}/${value}` });
      if (captured === sequence) await load();
    } catch (failure) {
      if (captured === sequence)
        error.value = formatAppErrorMessage(ensureAppError(failure, '导入操作失败，请刷新重试'));
    } finally {
      saving.value = false;
    }
  }
  watch(
    () => [paging.value.pagination.current, paging.value.pagination.pageSize],
    () => {
      if (props.visible) load();
    }
  );
  watch(
    () => [props.jobId, props.visible],
    () => {
      ++sequence;
      clearTimer();
      job.value = undefined;
      previous = '';
      paging.value.onPageChange(1);
      if (props.visible) load();
    },
    { immediate: true }
  );
  onBeforeUnmount(() => {
    ++sequence;
    clearTimer();
  });
</script>
