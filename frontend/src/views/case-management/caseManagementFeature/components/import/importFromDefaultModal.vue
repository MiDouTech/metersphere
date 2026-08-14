<template>
  <a-modal
    v-model:visible="dialogVisible"
    title="从用例资产导入"
    class="ms-modal-form"
    :width="980"
    :footer="false"
    unmount-on-close
    @cancel="handleCancel"
  >
    <a-alert type="info" class="mb-3"
      >选中的资产用例将复制到当前项目，并保留资产来源血缘；后续修改项目副本不会回写源资产。</a-alert
    >
    <a-form :model="{ conflictStrategy }" layout="vertical">
      <a-form-item label="重名用例处理">
        <a-radio-group v-model="conflictStrategy"
          ><a-radio value="SKIP">跳过</a-radio><a-radio value="OVERWRITE">覆盖当前项目副本</a-radio></a-radio-group
        >
      </a-form-item>
      <a-form-item label="附件"><a-checkbox v-model="copyAttachments">同时复制附件</a-checkbox></a-form-item>
    </a-form>
    <CaseAssetSelector
      :selected-ids="selectedIds"
      :target-project-id="appStore.currentProjectId"
      scene="PROJECT_IMPORT"
      @change="onSelectionChange"
    />
    <div v-if="jobProgress !== null" class="mt-3"
      ><a-progress :percent="jobProgress" /><div class="mt-1 text-xs">{{ jobStatus }}</div></div
    >
    <div class="mt-4 flex justify-end gap-2"
      ><a-button @click="handleCancel">{{ t('common.cancel') }}</a-button
      ><a-button type="primary" :disabled="!selectedIds.length" :loading="submitting" @click="handleImport"
        >导入选中用例</a-button
      ></div
    >
  </a-modal>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';

  import CaseAssetSelector from '@/components/business/case-asset-selector/index.vue';

  import {
    getCaseAssetImportResult,
    getDefaultHubJob,
    importCasesFromAssets,
  } from '@/api/modules/case-management/featureCase';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';

  import Message from '@arco-design/web-vue/es/message';

  const props = defineProps<{ visible: boolean; targetModuleId?: string }>();
  const emit = defineEmits<{ (e: 'update:visible', val: boolean): void; (e: 'success'): void }>();
  const { t } = useI18n();
  const appStore = useAppStore();
  const dialogVisible = computed({ get: () => props.visible, set: (val) => emit('update:visible', val) });
  const selectedIds = ref<string[]>([]);
  const conflictStrategy = ref<'SKIP' | 'OVERWRITE'>('SKIP');
  const copyAttachments = ref(true);
  const idempotencyKey = ref(crypto.randomUUID());
  const submitting = ref(false);
  const jobProgress = ref<number | null>(null);
  const jobStatus = ref('');
  let pollTimer: ReturnType<typeof setInterval> | null = null;
  function onSelectionChange(ids: string[]) {
    selectedIds.value = ids;
  }
  function stopPoll() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }
  function pollJob(jobId: string): Promise<boolean> {
    return new Promise((resolve) => {
      stopPoll();
      const deadline = Date.now() + 5 * 60 * 1000;
      pollTimer = setInterval(async () => {
        try {
          const job = await getDefaultHubJob(jobId);
          jobProgress.value = (job.progress || 0) / 100;
          jobStatus.value = job.status;
          if (job.status === 'SUCCESS') {
            stopPoll();
            resolve(true);
          } else if (job.status === 'PARTIAL_SUCCESS') {
            stopPoll();
            Message.warning(job.errorMessage || '部分用例导入失败，请查看结果');
            resolve(true);
          } else if (job.status === 'FAILED') {
            stopPoll();
            Message.error(job.errorMessage || '导入失败');
            resolve(false);
          } else if (Date.now() >= deadline) {
            stopPoll();
            Message.warning('导入仍在后台执行，可稍后重新打开并使用相同选择重试');
            resolve(false);
          }
        } catch {
          stopPoll();
          resolve(false);
        }
      }, 1000);
    });
  }
  async function handleImport() {
    if (!selectedIds.value.length) return;
    submitting.value = true;
    try {
      const result = await importCasesFromAssets({
        targetProjectId: appStore.currentProjectId,
        selectMode: 'CASE_IDS',
        ids: selectedIds.value,
        conflictStrategy: conflictStrategy.value,
        targetModuleId: props.targetModuleId || undefined,
        copyAttachments: copyAttachments.value,
        idempotencyKey: idempotencyKey.value,
      });
      jobProgress.value = 0;
      if (await pollJob(result.jobId)) {
        const items = await getCaseAssetImportResult(result.jobId);
        const failed = items.filter((item) => item.status === 'FAILED');
        if (failed.length)
          Message.warning(`导入完成：成功 ${items.length - failed.length} 条，失败 ${failed.length} 条`);
        else Message.success('用例资产导入成功');
        emit('success');
        dialogVisible.value = false;
      }
    } finally {
      submitting.value = false;
    }
  }
  function handleCancel() {
    stopPoll();
    dialogVisible.value = false;
  }
  watch(
    () => props.visible,
    (visible) => {
      if (visible) {
        selectedIds.value = [];
        conflictStrategy.value = 'SKIP';
        copyAttachments.value = true;
        idempotencyKey.value = crypto.randomUUID();
        jobProgress.value = null;
        jobStatus.value = '';
      } else stopPoll();
    }
  );
</script>
