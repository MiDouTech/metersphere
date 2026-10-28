<template>
  <div v-permission="['FUNCTIONAL_CASE:READ+IMPORT']" class="flex min-w-0 max-w-full flex-wrap items-center gap-[12px]">
    <a-button type="outline" @click="openImport">{{ t('common.import') }}</a-button>
    <a-button @click="legacyVisible = true">旧版导入记录</a-button>
    <a-button :disabled="!jobId" @click="jobVisible = true">最近导入任务</a-button>
    <a-radio-group v-model="importMode" type="button" size="small">
      <a-radio value="excel">{{ t('caseManagement.featureCase.importExcelTab') }}</a-radio>
      <a-radio value="asset">
        {{ t('caseManagement.featureCase.importHubTab') }}
      </a-radio>
    </a-radio-group>
  </div>
  <!-- Excel 导入 -->
  <ExportExcelModal
    v-model:visible="showExcelModal"
    v-model:validate-type="validateType"
    :confirm-loading="validateLoading"
    @save="validateTemplate"
    @close="showExcelModal = false"
  />
  <LegacyImportHistory v-model:visible="legacyVisible" />
  <ImportJob v-model:visible="jobVisible" :job-id="jobId" @changed="onHubImportSuccess" />
  <ImportFromDefaultModal
    v-model:visible="showHubModal"
    :target-module-id="importTargetModuleId"
    @success="onHubImportSuccess"
  />
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';

  import ExportExcelModal from './exportCaseModal.vue';
  import ImportFromDefaultModal from './importFromDefaultModal.vue';
  import ImportJob from './importJob.vue';
  import LegacyImportHistory from './LegacyImportHistory.vue';

  import MSR from '@/api/http';
  import { useI18n } from '@/hooks/useI18n';
  import { useUserStore } from '@/store';
  import useAppStore from '@/store/modules/app';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';
  import { showHttpErrorMessage } from '@/utils/httpMessage';

  import type { FileItem } from '@arco-design/web-vue';

  const props = withDefaults(
    defineProps<{
      /** 左树当前选中文件夹，导入目标 */
      activeFolder?: string;
    }>(),
    { activeFolder: 'all' }
  );

  const emit = defineEmits<{
    (e: 'confirmImport'): void;
    (e: 'initModules'): void;
  }>();

  const appStore = useAppStore();
  const { t } = useI18n();

  const importMode = ref<'excel' | 'asset'>('excel');
  const showExcelModal = ref<boolean>(false);
  const showHubModal = ref(false);

  const importTargetModuleId = computed(() => {
    const id = props.activeFolder;
    if (!id || id === 'all' || id === 'recycle' || id === 'root') {
      return '';
    }
    return id;
  });

  function openImport() {
    if (importMode.value === 'asset') {
      showHubModal.value = true;
      return;
    }
    showExcelModal.value = true;
  }

  /** 兼容外部仍调用 importCase 打开 Excel */
  function importCase() {
    importMode.value = 'excel';
    showExcelModal.value = true;
  }

  function onHubImportSuccess() {
    emit('confirmImport');
    emit('initModules');
  }

  const validateType = ref<'Excel' | 'Xmind'>('Excel');

  const validateLoading = ref(false);
  const jobId = ref('');
  const jobVisible = ref(false);
  const legacyVisible = ref(false);
  const userStore = useUserStore();
  const storageKey = computed(() => `ms:case-import:${userStore.id}:${appStore.currentProjectId}`);
  let pendingFile: File | undefined;
  let pendingOptions = '';
  let requestId = '';
  watch(
    storageKey,
    () => {
      jobVisible.value = false;
      try {
        jobId.value = localStorage.getItem(storageKey.value) || '';
      } catch {
        jobId.value = '';
      }
    },
    { immediate: true }
  );
  async function validateTemplate(files: FileItem[], cover: boolean) {
    const file = files[0]?.file;
    if (!file || validateLoading.value) return;
    const key = storageKey.value;
    const request = {
      projectId: appStore.currentProjectId,
      versionId: '',
      cover,
      moduleId: importTargetModuleId.value || undefined,
    };
    const options = JSON.stringify(request);
    if (pendingFile !== file || pendingOptions !== options) {
      pendingFile = file;
      pendingOptions = options;
      requestId = crypto.randomUUID();
    }
    validateLoading.value = true;
    try {
      const response = await MSR.uploadFile<{ data: { jobId: string } }>(
        { url: '/ai/case-imports/precheck', params: { requestId } },
        { request, fileList: [file] }
      );
      if (key !== storageKey.value) return;
      jobId.value = response.data.jobId;
      try {
        localStorage.setItem(key, jobId.value);
      } catch {
        /* The server retains the job even when browser storage is unavailable. */
      }
      jobVisible.value = true;
      showExcelModal.value = false;
      pendingFile = undefined;
    } catch (failure) {
      if (key === storageKey.value)
        showHttpErrorMessage(formatAppErrorMessage(ensureAppError(failure, '导入预检失败，请检查文件后重试')));
    } finally {
      validateLoading.value = false;
    }
  }

  defineExpose({
    importCase,
  });
</script>
