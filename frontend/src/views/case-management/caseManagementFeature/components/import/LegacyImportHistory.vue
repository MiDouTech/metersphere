<template>
  <a-drawer
    :visible="visible"
    title="旧版 Excel 导入记录"
    :width="900"
    :footer="false"
    @cancel="emit('update:visible', false)"
  >
    <a-alert class="mb-3"
      >仅展示当前账号在当前项目的旧版同步接口记录。中断任务的提交结果需核对，不要直接重复导入。</a-alert
    >
    <a-alert v-if="error" type="error" class="mb-3">{{ error }}</a-alert>
    <a-button class="mb-3" :loading="loading" @click="load">刷新</a-button>
    <a-table :data="rows" :loading="loading" :pagination="{ pageSize: 10 }">
      <template #empty><a-empty description="暂无旧版导入记录" /></template>
      <template #columns>
        <a-table-column title="文件" data-index="fileName" />
        <a-table-column title="状态"
          ><template #cell="{ record }">{{ labels[record.state] || record.state }}</template></a-table-column
        >
        <a-table-column title="阶段" data-index="stage" />
        <a-table-column title="已解析" data-index="processedRows" />
        <a-table-column title="成功" data-index="successCount" />
        <a-table-column title="失败" data-index="failCount" />
        <a-table-column title="traceId" data-index="traceId" />
      </template>
    </a-table>
  </a-drawer>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';

  import MSR from '@/api/http';
  import useAppStore from '@/store/modules/app';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';

  const props = defineProps<{ visible: boolean }>();
  const emit = defineEmits<{ (event: 'update:visible', visible: boolean): void }>();
  const app = useAppStore();
  const rows = ref<Record<string, any>[]>([]);
  const error = ref('');
  const loading = ref(false);
  let sequence = 0;
  const labels: Record<string, string> = {
    RUNNING: '处理中',
    SUCCEEDED: '成功',
    PARTIAL_FAILED: '部分条目失败',
    FAILED: '失败',
    INTERRUPTED: '中断，需核对',
  };
  async function load() {
    const current = ++sequence;
    if (!props.visible || !app.currentProjectId) return;
    loading.value = true;
    error.value = '';
    try {
      const result = await MSR.get<Record<string, any>[]>(
        { url: '/functional/case/import/jobs', params: { projectId: app.currentProjectId } },
        { errorMessageMode: 'none' }
      );
      if (current === sequence) rows.value = result;
    } catch (failure) {
      if (current === sequence)
        error.value = formatAppErrorMessage(ensureAppError(failure, '导入记录加载失败，请重试'));
    } finally {
      if (current === sequence) loading.value = false;
    }
  }
  watch([() => props.visible, () => app.currentProjectId], () => {
    rows.value = [];
    load();
  });
  onBeforeUnmount(() => {
    sequence += 1;
  });
</script>
