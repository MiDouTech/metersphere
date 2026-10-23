<template>
  <a-button class="mb-4" @click="open">执行产物归档</a-button>
  <a-drawer v-model:visible="visible" title="执行理解与数据归档" :width="650" :footer="false" unmount-on-close>
    <a-alert class="mb-3"
      >归档与测试结果分别处理。记录来源执行、事实、推断和待确认项；没有业务数据时不生成空数据集。</a-alert
    >
    <a-alert v-if="error" type="error" class="mb-3">{{ error }}</a-alert>
    <a-spin :loading="loading" class="w-full">
      <template v-if="archive">
        <a-descriptions :column="1" bordered>
          <a-descriptions-item label="状态">{{ labels[archive.state] || archive.state }}</a-descriptions-item>
          <a-descriptions-item label="来源运行">{{ archive.runId }}</a-descriptions-item>
          <a-descriptions-item label="说明">{{ archive.message }}</a-descriptions-item>
          <a-descriptions-item label="文档">{{ archive.documentId || '尚未归档' }}</a-descriptions-item>
          <a-descriptions-item label="业务数据">{{ archive.datasetId || '未提交独立业务数据' }}</a-descriptions-item>
        </a-descriptions>
        <a-space class="mt-4" wrap>
          <a-button :loading="loading" @click="refresh">刷新</a-button>
          <a-button v-if="archive.state === 'FAILED'" :loading="loading" @click="retry">重试归档</a-button>
          <a-button
            v-if="archive.documentId"
            v-permission="['FUNCTIONAL_CASE_AI:READ']"
            @click="router.push('/test-assets/documents')"
            >查看项目文档</a-button
          >
          <a-button
            v-if="archive.datasetId"
            v-permission="['PROJECT_FILE_MANAGEMENT:READ']"
            @click="router.push('/test-assets/datasets')"
            >查看数据资产</a-button
          >
        </a-space>
      </template>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';

  import MSR from '@/api/http';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';

  const props = defineProps<{ taskId: string }>();
  const router = useRouter();
  const visible = ref(false);
  const loading = ref(false);
  const error = ref('');
  interface Archive {
    id: string;
    runId: string;
    state: string;
    message: string;
    documentId?: string;
    datasetId?: string;
  }
  const archive = ref<Archive>();
  let sequence = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const labels: Record<string, string> = { PENDING: '等待归档', DONE: '已归档', FAILED: '归档失败，可单独重试' };
  function clearTimer() {
    if (timer) clearTimeout(timer);
    timer = undefined;
  }
  async function request(action: 'prepare' | 'get' | 'retry') {
    clearTimer();
    const captured = ++sequence;
    loading.value = true;
    error.value = '';
    try {
      let result: Archive;
      if (action === 'prepare')
        result = await MSR.post<Archive>({
          url: '/ai/execution-archives',
          data: { sourceType: 'TASK', sourceId: props.taskId },
        });
      else if (action === 'retry')
        result = await MSR.post<Archive>({ url: `/ai/execution-archives/${archive.value?.id}/retry` });
      else result = await MSR.get<Archive>({ url: `/ai/execution-archives/${archive.value?.id}` });
      if (captured !== sequence) return;
      archive.value = result;
      if (result.state === 'PENDING') timer = setTimeout(() => request('get'), 5000);
    } catch (failure) {
      if (captured === sequence) error.value = formatAppErrorMessage(ensureAppError(failure, '归档处理失败，请重试'));
    } finally {
      if (captured === sequence) loading.value = false;
    }
  }
  function open() {
    visible.value = true;
    request('prepare');
  }
  function refresh() {
    request(archive.value ? 'get' : 'prepare');
  }
  function retry() {
    request('retry');
  }
  watch(
    () => props.taskId,
    () => {
      ++sequence;
      clearTimer();
      visible.value = false;
      archive.value = undefined;
    }
  );
  watch(visible, (value) => {
    if (!value) {
      ++sequence;
      clearTimer();
    }
  });
  onBeforeUnmount(() => {
    ++sequence;
    clearTimer();
  });
</script>
