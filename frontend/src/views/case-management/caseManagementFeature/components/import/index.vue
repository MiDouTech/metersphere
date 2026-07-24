<template>
  <div v-permission="['FUNCTIONAL_CASE:READ+IMPORT']" class="flex items-center gap-[12px]">
    <a-button type="outline" @click="openImport">{{ t('common.import') }}</a-button>
    <a-radio-group v-model="importMode" type="button" size="small">
      <a-radio value="excel">{{ t('caseManagement.featureCase.importExcelTab') }}</a-radio>
      <a-radio value="defaultHub" :disabled="hubModeDisabled">
        {{ t('caseManagement.featureCase.importHubTab') }}
      </a-radio>
    </a-radio-group>
    <a-tooltip v-if="hubModeDisabled" :content="hubDisabledTip">
      <icon-info-circle class="text-[var(--color-text-4)]" />
    </a-tooltip>
  </div>
  <!-- Excel 导入 -->
  <ExportExcelModal
    v-model:visible="showExcelModal"
    v-model:validate-type="validateType"
    :confirm-loading="validateLoading"
    @save="validateTemplate"
    @close="showExcelModal = false"
  />
  <ValidateModal
    v-model:visible="validateModal"
    :validate-type="validateType"
    :percent="progress"
    @cancel="cancelValidate"
    @check-finished="checkFinished"
  />
  <ValidateResult
    v-model:visible="validateResultModal"
    :validate-type="validateType"
    :validate-info="validateInfo"
    :import-loading="importLoading"
    @close="closeHandler"
    @save="confirmImport"
  />
  <ImportFromDefaultModal v-model:visible="showHubModal" @success="onHubImportSuccess" />
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue';

  import ExportExcelModal from './exportCaseModal.vue';
  import ImportFromDefaultModal from './importFromDefaultModal.vue';
  import ValidateModal from './validateModal.vue';
  import ValidateResult from './validateResult.vue';

  import { getDefaultHubProjectId } from '@/api/modules/case-management/defaultHub';
  import { importExcelOrXMindCase, importExcelOrXMindChecked } from '@/api/modules/case-management/featureCase';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';

  import type { ValidateInfo } from '@/models/caseManagement/featureCase';

  import type { FileItem } from '@arco-design/web-vue';
  import Message from '@arco-design/web-vue/es/message';

  const emit = defineEmits<{
    (e: 'confirmImport'): void;
    (e: 'initModules'): void;
  }>();

  const appStore = useAppStore();
  const { t } = useI18n();

  const importMode = ref<'excel' | 'defaultHub'>('excel');
  const hubProjectId = ref('');
  const showExcelModal = ref<boolean>(false);
  const showHubModal = ref(false);

  const hubModeDisabled = computed(() => !!hubProjectId.value && hubProjectId.value === appStore.currentProjectId);
  const hubDisabledTip = computed(() => t('caseManagement.featureCase.importHubDisabledCurrent'));

  async function loadHubProjectId() {
    try {
      hubProjectId.value = (await getDefaultHubProjectId()) || '';
    } catch {
      hubProjectId.value = '';
    }
  }

  function openImport() {
    if (importMode.value === 'defaultHub') {
      if (hubModeDisabled.value) {
        Message.warning(hubDisabledTip.value);
        return;
      }
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

  const fileList = ref<FileItem[]>([]);
  const isCover = ref<boolean>(false);
  const validateLoading = ref<boolean>(false);
  const validateModal = ref<boolean>(false);

  // 校验结果弹窗
  const validateResultModal = ref<boolean>(false);
  const intervalId = ref<any>(null);
  const progress = ref<number>(0);
  const increment = ref<number>(0.1);

  function updateProgress() {
    progress.value = Math.floor(progress.value + increment.value);
    if (progress.value >= 1) {
      progress.value = 1;
    }
  }

  function finish() {
    clearInterval(intervalId.value);
    progress.value = 1;
    updateProgress();
  }

  function start() {
    progress.value = 0;
    increment.value = 0.1;
    intervalId.value = setInterval(() => {
      if (progress.value >= 1) {
        finish();
      } else {
        updateProgress();
      }
    }, 100);
  }
  const validateInfo = ref<ValidateInfo>({
    failCount: 0,
    successCount: 0,
    errorMessages: [],
  });

  // 校验导入模板
  async function validateTemplate(files: FileItem[], cover: boolean) {
    fileList.value = files;
    isCover.value = cover;
    validateLoading.value = true;
    try {
      validateModal.value = true;
      start();
      const params = {
        projectId: appStore.currentProjectId,
        versionId: '',
        cover,
      };
      const result = await importExcelOrXMindChecked(
        { request: params, fileList: files.map((item: any) => item.file) },
        validateType.value
      );
      finish();
      validateInfo.value = result.data;
    } catch (error) {
      validateModal.value = false;
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      validateLoading.value = false;
    }
  }

  function checkFinished() {
    validateResultModal.value = true;
  }

  function cancelValidate() {
    validateModal.value = false;
  }

  function closeHandler() {
    showExcelModal.value = false;
    validateResultModal.value = false;
    emit('initModules');
  }
  const importLoading = ref<boolean>(false);
  // 确定导入
  async function confirmImport() {
    importLoading.value = true;
    try {
      const params = {
        projectId: appStore.currentProjectId,
        versionId: '',
        cover: isCover.value,
        count: validateInfo.value.successCount,
      };
      await importExcelOrXMindCase(
        { request: params, fileList: fileList.value.map((item: any) => item.file) },
        validateType.value
      );
      Message.success(t('caseManagement.featureCase.importSuccess'));
      validateResultModal.value = false;
      showExcelModal.value = false;
      emit('confirmImport');
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      importLoading.value = false;
    }
  }

  watch(
    () => appStore.currentProjectId,
    () => {
      if (hubModeDisabled.value && importMode.value === 'defaultHub') {
        importMode.value = 'excel';
      }
    }
  );

  onMounted(() => {
    loadHubProjectId();
  });

  defineExpose({
    importCase,
  });
</script>
