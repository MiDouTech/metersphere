<template>
  <a-modal
    v-model:visible="dialogVisible"
    title-align="start"
    class="ms-modal-form ms-modal-medium"
    :ok-text="t('common.import')"
    :title="t('caseManagement.featureCase.importFromDefaultProject')"
    :cancel-text="t('common.cancel')"
    :ok-loading="submitting"
    :ok-button-props="{ disabled: !canImport }"
    @before-ok="handleImport"
    @cancel="handleCancel"
  >
    <a-alert type="info" class="mb-4">{{ t('caseManagement.featureCase.importHubTip') }}</a-alert>
    <a-form :model="form" layout="vertical">
      <a-form-item :label="t('caseManagement.featureCase.importHubConflict')">
        <a-radio-group v-model="form.conflictStrategy">
          <a-radio value="SKIP">{{ t('caseManagement.featureCase.importHubSkip') }}</a-radio>
          <a-radio value="OVERWRITE">{{ t('caseManagement.featureCase.importHubOverwrite') }}</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item :label="t('common.select')">
        <a-radio-group v-model="form.selectMode" @change="onModeChange">
          <a-radio value="ALL">{{ t('caseManagement.featureCase.importHubSelectAll') }}</a-radio>
          <a-radio value="UNPLANNED">{{ t('caseManagement.featureCase.importHubUnplanned') }}</a-radio>
          <a-radio value="MODULE_IDS">{{ t('caseManagement.featureCase.tableColumnModule') }}</a-radio>
        </a-radio-group>
      </a-form-item>
      <div
        v-if="form.selectMode === 'MODULE_IDS'"
        class="max-h-[320px] overflow-auto rounded border border-[var(--color-text-n8)] p-2"
      >
        <a-spin :loading="treeLoading" class="w-full">
          <a-tree
            v-model:checked-keys="checkedKeys"
            checkable
            :check-strictly="false"
            :data="moduleTree"
            :field-names="{ key: 'id', title: 'name', children: 'children' }"
          />
        </a-spin>
      </div>
      <div class="mt-2 text-[12px] text-[var(--color-text-4)]">{{
        t('caseManagement.featureCase.importHubLimit')
      }}</div>
      <div v-if="jobProgress !== null" class="mt-3">
        <a-progress :percent="jobProgress" />
        <div class="mt-1 text-[12px] text-[var(--color-text-3)]">{{ jobStatus }}</div>
      </div>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';

  import { getDefaultHubProjectId } from '@/api/modules/case-management/defaultHub';
  import {
    getCaseModuleTree,
    getDefaultHubJob,
    importCaseFromDefaultProject,
  } from '@/api/modules/case-management/featureCase';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';
  import { mapTree } from '@/utils';

  import { ModuleTreeNode } from '@/models/common';

  import Message from '@arco-design/web-vue/es/message';

  const props = defineProps<{ visible: boolean }>();
  const emit = defineEmits<{
    (e: 'update:visible', val: boolean): void;
    (e: 'success'): void;
  }>();

  const { t } = useI18n();
  const appStore = useAppStore();

  const dialogVisible = computed({
    get: () => props.visible,
    set: (val) => emit('update:visible', val),
  });

  const form = ref({
    selectMode: 'ALL',
    conflictStrategy: 'SKIP',
  });
  const checkedKeys = ref<string[]>([]);
  const moduleTree = ref<ModuleTreeNode[]>([]);
  const treeLoading = ref(false);
  const submitting = ref(false);
  const jobProgress = ref<number | null>(null);
  const jobStatus = ref('');
  let pollTimer: ReturnType<typeof setInterval> | null = null;

  const canImport = computed(() => {
    if (form.value.selectMode === 'MODULE_IDS') {
      return checkedKeys.value.length > 0;
    }
    return true;
  });

  function stopPoll() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }

  async function loadHubTree() {
    try {
      treeLoading.value = true;
      const hubProjectId = await getDefaultHubProjectId();
      if (!hubProjectId) {
        Message.warning('未配置默认项目');
        return;
      }
      const res = await getCaseModuleTree({ projectId: hubProjectId });
      moduleTree.value = mapTree(res, (e) => ({ ...e }));
    } catch {
      Message.error('加载默认项目模块树失败');
    } finally {
      treeLoading.value = false;
    }
  }

  function onModeChange() {
    checkedKeys.value = [];
    if (form.value.selectMode === 'MODULE_IDS') {
      loadHubTree();
    }
  }

  watch(
    () => props.visible,
    (val) => {
      if (val) {
        form.value = { selectMode: 'ALL', conflictStrategy: 'SKIP' };
        checkedKeys.value = [];
        jobProgress.value = null;
        jobStatus.value = '';
      } else {
        stopPoll();
      }
    }
  );

  function pollJob(jobId: string): Promise<boolean> {
    return new Promise((resolve) => {
      stopPoll();
      pollTimer = setInterval(async () => {
        try {
          const job = await getDefaultHubJob(jobId);
          jobProgress.value = (job.progress || 0) / 100;
          jobStatus.value = job.status;
          if (job.status === 'SUCCESS') {
            stopPoll();
            resolve(true);
          } else if (job.status === 'FAILED') {
            stopPoll();
            Message.error(job.errorMessage || '导入失败');
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
    if (!canImport.value) return false;
    try {
      submitting.value = true;
      const result = await importCaseFromDefaultProject({
        targetProjectId: appStore.currentProjectId,
        selectMode: form.value.selectMode,
        ids: form.value.selectMode === 'MODULE_IDS' ? checkedKeys.value : [],
        conflictStrategy: form.value.conflictStrategy,
      });
      Message.success(t('caseManagement.featureCase.importHubSuccess'));
      jobProgress.value = 0;
      const ok = await pollJob(result.jobId);
      if (ok) {
        emit('success');
        dialogVisible.value = false;
      }
      return ok;
    } catch {
      Message.error('导入失败');
      return false;
    } finally {
      submitting.value = false;
    }
  }

  function handleCancel() {
    stopPoll();
    dialogVisible.value = false;
  }
</script>
