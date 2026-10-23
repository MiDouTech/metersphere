<template>
  <a-button v-permission="['FUNCTIONAL_CASE:READ+EXPORT']" class="ml-[12px]" @click="open">导出记录</a-button>
  <a-drawer v-model:visible="visible" title="我的导出记录" width="min(960px, 100vw)" unmount-on-close @close="close">
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <span class="text-[var(--color-text-3)]"
        >当前项目本人发起的导出。重试沿用原筛选与字段，重新读取当前用例内容。</span
      >
      <a-button :loading="loading" :disabled="!!busy" @click="load">刷新记录</a-button>
    </div>
    <a-alert v-if="errorMessage" type="error" class="mb-3">
      {{ errorMessage }}{{ rows.length ? '；当前显示上次读取的记录。' : '' }}
    </a-alert>
    <a-table :data="rows" :loading="loading" :pagination="false" :scroll="{ x: 900 }" row-key="taskId">
      <template #empty><a-empty description="当前项目暂无本人的导出记录" /></template>
      <template #columns>
        <a-table-column title="任务编号" :width="220">
          <template #cell="{ record }">
            <span class="break-all">{{ record.taskId }}</span>
            <a-tooltip v-if="record.sourceTaskId" :content="`原任务：${record.sourceTaskId}`">
              <div class="text-[var(--color-text-3)]">关联重试任务</div>
            </a-tooltip>
          </template>
        </a-table-column>
        <a-table-column title="创建时间" :width="170">
          <template #cell="{ record }">{{
            record.createTime ? new Date(record.createTime).toLocaleString() : '—'
          }}</template>
        </a-table-column>
        <a-table-column title="格式" :width="70"
          ><template #cell="{ record }">{{ record.fileType?.toUpperCase() || '—' }}</template></a-table-column
        >
        <a-table-column title="条数" data-index="count" :width="60" />
        <a-table-column title="状态" :width="130">
          <template #cell="{ record }">
            <a-tooltip :content="record.message || stateText(record.state)"
              ><span>{{ stateText(record.state) }}</span></a-tooltip
            >
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="220">
          <template #cell="{ record }">
            <a-space>
              <a-button
                type="text"
                :loading="busy === record.taskId && busyAction === 'download'"
                :disabled="record.state !== 'SUCCEEDED' || !!busy"
                @click="download(record)"
                >下载</a-button
              >
              <a-button
                type="text"
                :loading="busy === record.taskId && busyAction === 'retry'"
                :disabled="!['FAILED', 'CANCELLED', 'EXPIRED'].includes(record.state) || !!busy"
                @click="retry(record)"
                >重试</a-button
              >
              <a-button
                type="text"
                :loading="busy === record.taskId && busyAction === 'stop'"
                :disabled="record.state !== 'RUNNING' || !!busy"
                @click="stop(record)"
                >停止</a-button
              >
            </a-space>
          </template>
        </a-table-column>
      </template>
    </a-table>
    <template #footer>
      <div class="flex flex-wrap justify-end">
        <a-pagination
          :total="total"
          :current="current"
          :page-size="pageSize"
          :page-size-options="[10, 20, 50, 100]"
          show-total
          show-page-size
          :hide-on-single-page="false"
          :disabled="loading || !!busy"
          @change="changePage"
          @page-size-change="changeSize"
        />
      </div>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import type { CaseExportTask } from '@/api/modules/case-management/featureCase';
  import {
    getCaseDownloadFile,
    getCaseExportHistory,
    retryCaseExport,
    stopCaseExport,
  } from '@/api/modules/case-management/featureCase';
  import useListRefresh from '@/hooks/useListRefresh';
  import { downloadByteFile } from '@/utils';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';

  const props = defineProps<{ projectId: string }>();
  const visible = ref(false);
  const rows = ref<CaseExportTask[]>([]);
  const total = ref(0);
  const current = ref(1);
  const pageSize = ref(10);
  const loading = ref(false);
  const busy = ref('');
  const busyAction = ref('');
  const denied = ref(false);
  const errorMessage = ref('');
  let sequence = 0;

  function stateText(state: CaseExportTask['state']) {
    return (
      { RUNNING: '导出中', SUCCEEDED: '可下载', FAILED: '失败', CANCELLED: '已停止', EXPIRED: '已过期' }[state] ||
      '状态未知'
    );
  }
  async function load() {
    if (!visible.value || !props.projectId) return;
    const id = ++sequence;
    const { projectId } = props;
    loading.value = true;
    denied.value = false;
    errorMessage.value = '';
    try {
      const result = await getCaseExportHistory(projectId, current.value, pageSize.value);
      if (id !== sequence || projectId !== props.projectId || !visible.value) return;
      rows.value = result.list;
      total.value = result.total;
      current.value = result.current;
    } catch (error) {
      if (id !== sequence || projectId !== props.projectId || !visible.value) return;
      const detail = ensureAppError(error, '读取导出记录失败，请重试');
      errorMessage.value = formatAppErrorMessage(detail);
      denied.value = detail.category === 'permission' || [401, 403, 404].includes(detail.status || 0);
      if (denied.value) {
        rows.value = [];
        total.value = 0;
      }
    } finally {
      if (id === sequence) loading.value = false;
    }
  }
  function open() {
    visible.value = true;
    current.value = 1;
    load();
  }
  function close() {
    sequence += 1;
    loading.value = false;
  }
  function changePage(page: number) {
    current.value = page;
    load();
  }
  function changeSize(size: number) {
    pageSize.value = size;
    current.value = 1;
    load();
  }

  async function download(task: CaseExportTask) {
    if (busy.value || task.state !== 'SUCCEEDED') return;
    busy.value = task.taskId;
    busyAction.value = 'download';
    try {
      const response = await getCaseDownloadFile(task.projectId, task.fileId);
      if (task.projectId !== props.projectId || !visible.value) return;
      if (!(response.data instanceof Blob) || /json|html/i.test(response.data.type))
        throw new Error('文件暂时不可下载，请刷新记录后重试');
      const disposition = String(response.headers.get('content-disposition') || '');
      const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
      const plain = /filename="?([^";]+)/i.exec(disposition)?.[1];
      let fileName = encoded || plain || `export.${task.fileType || 'xlsx'}`;
      try {
        fileName = decodeURIComponent(fileName);
      } catch {
        /* Keep a non-encoded filename. */
      }
      downloadByteFile(response.data, fileName);
    } catch (error) {
      if (task.projectId === props.projectId && visible.value)
        Message.error(formatAppErrorMessage(ensureAppError(error, '下载失败，请刷新记录后重试')));
    } finally {
      if (busy.value === task.taskId) {
        busy.value = '';
        busyAction.value = '';
      }
    }
  }
  async function stop(task: CaseExportTask) {
    if (busy.value || task.state !== 'RUNNING') return;
    busy.value = task.taskId;
    busyAction.value = 'stop';
    try {
      const result = await stopCaseExport(task.taskId);
      if (task.projectId !== props.projectId || !visible.value) return;
      if (result.state === 'CANCELLED') Message.success('导出已停止');
      else Message.info(`任务状态已更新：${stateText(result.state)}`);
      await load();
    } catch (error) {
      if (task.projectId === props.projectId && visible.value)
        Message.error(formatAppErrorMessage(ensureAppError(error, '停止失败，请重试')));
    } finally {
      if (busy.value === task.taskId) {
        busy.value = '';
        busyAction.value = '';
      }
    }
  }
  async function retry(task: CaseExportTask) {
    if (busy.value || !['FAILED', 'CANCELLED', 'EXPIRED'].includes(task.state)) return;
    busy.value = task.taskId;
    busyAction.value = 'retry';
    const key = `ms:export-retry:${task.projectId}:${task.taskId}`;
    try {
      let requestId = sessionStorage.getItem(key);
      if (!requestId) {
        requestId = Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) =>
          byte.toString(16).padStart(2, '0')
        ).join('');
        sessionStorage.setItem(key, requestId);
      }
      const result = await retryCaseExport(task.taskId, requestId);
      sessionStorage.removeItem(key);
      if (task.projectId !== props.projectId || !visible.value) return;
      Message.success(`已创建重试任务：${result.taskId}`);
      current.value = 1;
      await load();
    } catch (error) {
      if (task.projectId === props.projectId && visible.value)
        Message.error(
          formatAppErrorMessage(ensureAppError(error, '重试未确认，请再次点击重试；系统会沿用同一请求标识'))
        );
    } finally {
      if (busy.value === task.taskId) {
        busy.value = '';
        busyAction.value = '';
      }
    }
  }
  watch(
    () => props.projectId,
    () => {
      visible.value = false;
      close();
      rows.value = [];
      total.value = 0;
      current.value = 1;
      errorMessage.value = '';
      denied.value = false;
    }
  );
  onBeforeUnmount(() => {
    sequence += 1;
  });
  useListRefresh(load, () => visible.value && !loading.value && !busy.value && !denied.value, 5000, {
    allowReadOnlyOverlay: true,
  });
</script>
