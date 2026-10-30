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
    <slot name="modes"></slot>
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
    <ImportFollowupChoice v-if="visible && !resultJobId" ref="followupChoice" :project-id="appStore.currentProjectId" />
    <CaseAssetSelector
      :selected-ids="selectedIds"
      :target-project-id="appStore.currentProjectId"
      scene="PROJECT_IMPORT"
      @change="onSelectionChange"
    />
    <div v-if="jobProgress !== null" class="mt-3"
      ><a-progress :percent="jobProgress" /><div class="mt-1 text-xs">{{ jobStatus }}</div></div
    >
    <a-alert v-if="followupResult" class="mt-3" :type="followupResult.state === 'FAILED' ? 'warning' : 'info'">
      计划与评审关联：{{ followupResult.state }} {{ followupResult.message || '' }}
      <div v-if="followupResult.planId">测试计划：{{ followupResult.planName || followupResult.planId }}</div>
      <div v-if="followupResult.reviewId">评审计划：{{ followupResult.reviewName || followupResult.reviewId }}</div>
      <a-button v-if="followupResult.state === 'FAILED'" size="mini" @click="retryAssociation">重试关联</a-button>
    </a-alert>
    <div class="mt-4 flex justify-end gap-2"
      ><a-button @click="handleCancel">{{ t('common.cancel') }}</a-button
      ><a-button
        type="primary"
        :disabled="!selectedIds.length || !!resultJobId"
        :loading="submitting"
        @click="handleImport"
        >导入选中用例</a-button
      ></div
    >
  </a-modal>
</template>

<script setup lang="ts">
  import { computed, onUnmounted, ref, watch } from 'vue';

  import CaseAssetSelector from '@/components/business/case-asset-selector/index.vue';
  import ImportFollowupChoice from './ImportFollowupChoice.vue';

  import MSR from '@/api/http';
  import {
    getCaseAssetImportResult,
    getDefaultHubJob,
    importCasesFromAssets,
  } from '@/api/modules/case-management/featureCase';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';

  import Message from '@arco-design/web-vue/es/message';

  const props = defineProps<{ visible: boolean; targetModuleId?: string }>();
  const emit = defineEmits<{
    (e: 'update:visible', val: boolean): void;
    (e: 'success'): void;
    (e: 'busy', value: boolean): void;
  }>();
  const { t } = useI18n();
  const appStore = useAppStore();
  const dialogVisible = computed({ get: () => props.visible, set: (val) => emit('update:visible', val) });
  const selectedIds = ref<string[]>([]);
  const conflictStrategy = ref<'SKIP' | 'OVERWRITE'>('SKIP');
  const copyAttachments = ref(true);
  const idempotencyKey = ref(crypto.randomUUID());
  const submitting = ref(false);
  watch(submitting, (value) => emit('busy', value));
  const followupChoice = ref<InstanceType<typeof ImportFollowupChoice>>();
  const resultJobId = ref('');
  const followupResult = ref<{
    state: string;
    message?: string;
    planId?: string;
    planName?: string;
    reviewId?: string;
    reviewName?: string;
  }>();
  let associationTimer: ReturnType<typeof setTimeout> | undefined;
  const jobProgress = ref<number | null>(null);
  const jobStatus = ref('');
  let pollTimer: ReturnType<typeof setTimeout> | undefined;
  let resolvePoll: ((success: boolean) => void) | undefined;
  let pollSequence = 0;
  function onSelectionChange(ids: string[]) {
    selectedIds.value = ids;
  }
  function stopPoll(success = false) {
    ++pollSequence;
    clearTimeout(pollTimer);
    const finish = resolvePoll;
    resolvePoll = undefined;
    finish?.(success);
  }
  function pollJob(jobId: string): Promise<boolean> {
    stopPoll();
    const sequence = pollSequence;
    return new Promise((resolve) => {
      resolvePoll = resolve;
      const deadline = Date.now() + 5 * 60 * 1000;
      const check = async () => {
        try {
          const job = await getDefaultHubJob(jobId);
          if (sequence !== pollSequence) return;
          jobProgress.value = (job.progress || 0) / 100;
          jobStatus.value = job.status;
          if (job.status === 'SUCCESS' || job.status === 'PARTIAL_SUCCESS') {
            if (job.status === 'PARTIAL_SUCCESS') Message.warning(job.errorMessage || '部分用例导入失败，请查看结果');
            stopPoll(true);
          } else if (job.status === 'FAILED') {
            Message.error(job.errorMessage || '导入失败');
            stopPoll();
          } else if (Date.now() >= deadline) {
            Message.warning('导入仍在后台执行，请稍后查看任务结果');
            stopPoll();
          } else pollTimer = setTimeout(check, 1000);
        } catch {
          if (sequence === pollSequence) stopPoll();
        }
      };
      check();
    });
  }
  async function refreshAssociation() {
    if (!resultJobId.value || !props.visible) return;
    const id = resultJobId.value;
    const result = await MSR.get<typeof followupResult.value>({ url: `/ai/case-imports/${id}/followup` });
    if (!props.visible || resultJobId.value !== id) return;
    followupResult.value = result;
    if (followupResult.value?.state === 'PENDING') associationTimer = setTimeout(refreshAssociation, 2500);
  }
  async function retryAssociation() {
    await MSR.post({ url: `/ai/case-imports/${resultJobId.value}/followup/retry` });
    await refreshAssociation();
  }
  async function handleImport() {
    if (!selectedIds.value.length || resultJobId.value) return;
    const followup = followupChoice.value?.decision();
    if (!followup) return;
    submitting.value = true;
    try {
      const result = await importCasesFromAssets({
        followup,
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
        resultJobId.value = result.jobId;
        emit('success');
        await refreshAssociation();
      }
    } finally {
      submitting.value = false;
    }
  }
  function handleCancel() {
    stopPoll();
    clearTimeout(associationTimer);
    dialogVisible.value = false;
  }
  onUnmounted(() => {
    stopPoll();
    clearTimeout(associationTimer);
  });
  watch(
    () => props.visible,
    (visible) => {
      if (visible) {
        resultJobId.value = '';
        followupResult.value = undefined;
        selectedIds.value = [];
        conflictStrategy.value = 'SKIP';
        copyAttachments.value = true;
        idempotencyKey.value = crypto.randomUUID();
        jobProgress.value = null;
        jobStatus.value = '';
      } else {
        stopPoll();
        clearTimeout(associationTimer);
      }
    }
  );
</script>
