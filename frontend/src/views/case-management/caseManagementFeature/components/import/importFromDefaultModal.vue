<template>
  <a-modal
    v-model:visible="dialogVisible"
    title-align="start"
    class="ms-modal-form ms-modal-medium"
    :title="t('caseManagement.featureCase.importFromDefaultProject')"
    :footer="false"
    unmount-on-close
    @cancel="handleCancel"
  >
    <a-alert type="info" class="mb-4">{{ t('caseManagement.featureCase.importHubTip') }}</a-alert>
    <a-alert type="warning" class="mb-4">{{ t('caseManagement.featureCase.importHubPlannedOnly') }}</a-alert>
    <a-form :model="form" layout="vertical">
      <a-form-item :label="t('caseManagement.featureCase.importHubConflict')">
        <a-radio-group v-model="form.conflictStrategy">
          <a-radio value="SKIP">{{ t('caseManagement.featureCase.importHubSkip') }}</a-radio>
          <a-radio value="OVERWRITE">{{ t('caseManagement.featureCase.importHubOverwrite') }}</a-radio>
        </a-radio-group>
      </a-form-item>
      <div class="max-h-[320px] overflow-auto rounded border border-[var(--color-text-n8)] p-2">
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
      <div class="mt-2 text-[12px] text-[var(--color-text-4)]">
        {{ t('caseManagement.featureCase.importHubLimit') }}
        <span v-if="selectedCaseIds.length > 0" class="ml-2">
          {{ t('caseManagement.featureCase.importHubSelectedCount', { count: selectedCaseIds.length }) }}
        </span>
      </div>
      <div v-if="jobProgress !== null" class="mt-3">
        <a-progress :percent="jobProgress" />
        <div class="mt-1 text-[12px] text-[var(--color-text-3)]">{{ jobStatus }}</div>
      </div>
    </a-form>
    <div class="mt-4 flex justify-end gap-[8px]">
      <a-button @click="handleCancel">{{ t('common.cancel') }}</a-button>
      <a-button v-if="canImport" type="primary" :loading="submitting" @click="handleImport">
        {{ t('common.import') }}
      </a-button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';

  import {
    getDefaultHubImportTree,
    getDefaultHubJob,
    importCaseFromDefaultProject,
  } from '@/api/modules/case-management/featureCase';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';
  import { mapTree } from '@/utils';

  import { ModuleTreeNode } from '@/models/common';

  import Message from '@arco-design/web-vue/es/message';

  const MAX_IMPORT = 500;

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
    conflictStrategy: 'SKIP',
  });
  const checkedKeys = ref<string[]>([]);
  const moduleTree = ref<ModuleTreeNode[]>([]);
  const treeLoading = ref(false);
  const submitting = ref(false);
  const jobProgress = ref<number | null>(null);
  const jobStatus = ref('');
  let pollTimer: ReturnType<typeof setInterval> | null = null;

  function collectCaseIds(nodes: ModuleTreeNode[], checked: Set<string>, out: string[]) {
    nodes.forEach((node) => {
      if ((node as any).type === 'CASE' && checked.has(node.id)) {
        out.push(node.id);
      }
      if (node.children?.length) {
        collectCaseIds(node.children as ModuleTreeNode[], checked, out);
      }
    });
  }

  const selectedCaseIds = computed(() => {
    const ids: string[] = [];
    collectCaseIds(moduleTree.value, new Set(checkedKeys.value), ids);
    return ids;
  });

  const canImport = computed(() => {
    const count = selectedCaseIds.value.length;
    return count >= 1 && count <= MAX_IMPORT;
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
      const res = await getDefaultHubImportTree();
      moduleTree.value = mapTree(res || [], (e) => ({ ...e }));
    } catch {
      Message.error(t('caseManagement.featureCase.importHubTreeFail'));
    } finally {
      treeLoading.value = false;
    }
  }

  watch(
    () => props.visible,
    (val) => {
      if (val) {
        form.value = { conflictStrategy: 'SKIP' };
        checkedKeys.value = [];
        jobProgress.value = null;
        jobStatus.value = '';
        loadHubTree();
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
            Message.error(job.errorMessage || t('caseManagement.featureCase.importHubFail'));
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
    if (!canImport.value) return;
    if (selectedCaseIds.value.length > MAX_IMPORT) {
      Message.warning(t('caseManagement.featureCase.importHubLimit'));
      return;
    }
    try {
      submitting.value = true;
      const result = await importCaseFromDefaultProject({
        targetProjectId: appStore.currentProjectId,
        selectMode: 'CASE_IDS',
        ids: selectedCaseIds.value,
        conflictStrategy: form.value.conflictStrategy,
      });
      Message.success(t('caseManagement.featureCase.importHubSuccess'));
      jobProgress.value = 0;
      const ok = await pollJob(result.jobId);
      if (ok) {
        emit('success');
        dialogVisible.value = false;
      }
    } catch {
      Message.error(t('caseManagement.featureCase.importHubFail'));
    } finally {
      submitting.value = false;
    }
  }

  function handleCancel() {
    stopPoll();
    dialogVisible.value = false;
  }
</script>
