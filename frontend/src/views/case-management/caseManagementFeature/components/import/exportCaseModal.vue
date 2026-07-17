<template>
  <a-modal
    v-model:visible="dialogVisible"
    title-align="start"
    class="ms-modal-form ms-modal-medium"
    :ok-text="t('common.confirm')"
    :title="t('common.import')"
    :cancel-text="t('common.cancel')"
    @close="handleCancel"
  >
    <div>
      <!-- Xmind 导入已迁移至「Xmind用例」Tab，执行用例仅保留 Excel -->
      <a-alert class="my-4">
        <div class="flex items-center">
          {{ t('caseManagement.featureCase.beforeUploadTip', { type: validateType }) }}
          <MsIcon
            type="icon-icon_file-excel_colorful1"
            class="mx-1 cursor-pointer text-[rgb(var(--primary-6))]"
          ></MsIcon>
          <MsButton @click="downloadExcelTemplate">
            {{ t('caseManagement.featureCase.downloadTemplate', { type: validateType }) }}
          </MsButton>
        </div>
      </a-alert>
      <MsUpload
        v-model:file-list="fileList"
        class="mb-6 w-full"
        accept="excel"
        size-unit="MB"
        main-text="caseManagement.featureCase.dragOrClick"
        :sub-text="t('caseManagement.featureCase.onlyEXcelTip', { size: appStore.getFileMaxSize })"
        :show-file-list="false"
        :auto-upload="false"
        :allow-repeat="true"
        :disabled="confirmLoading"
        :file-type-tip="fileTypeTip"
      ></MsUpload>
      <!-- 版本暂时不上 -->
      <!-- <a-form-item field="post" :label="t('caseManagement.featureCase.selectVersion')">
        <a-select class="max-w-[240px]" :placeholder="t('caseManagement.featureCase.defaultSelectNewVersion')">
          <a-option v-for="item of versionOptions" :key="item.id" :value="item.id">{{ item.name }}</a-option>
        </a-select>
      </a-form-item> -->
    </div>
    <template #footer>
      <div class="flex items-center justify-between">
        <a-checkbox v-model="isRecover"
          ><span class="flex items-center">
            {{ t('caseManagement.featureCase.isRecoverOriginCase') }}
            <a-tooltip>
              <template #content>
                <div>
                  {{ t('caseManagement.featureCase.selectedRecoverCase') }}
                </div>
                <div>
                  {{ t('caseManagement.featureCase.notSelectedRecoverCase') }}
                </div>
              </template>
              <icon-question-circle
                class="ml-1 inline-block text-[var(--color-text-4)] hover:text-[rgb(var(--primary-5))]"
              /> </a-tooltip></span
        ></a-checkbox>
        <div>
          <!--          <a-button type="secondary" @click="handleCancel">{{ t('system.plugin.pluginCancel') }}</a-button>-->
          <a-button
            class="ml-3"
            type="primary"
            :loading="props.confirmLoading"
            :disabled="fileList.length < 1"
            @click="saveConfirm"
          >
            {{ t('caseManagement.featureCase.checkImportFile') }}
          </a-button>
        </div>
      </div>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import { FileItem } from '@arco-design/web-vue';

  import MsButton from '@/components/pure/ms-button/index.vue';
  import MsUpload from '@/components/pure/ms-upload/index.vue';

  import { downloadTemplate } from '@/api/modules/case-management/featureCase';
  import { useI18n } from '@/hooks/useI18n';
  import { useAppStore } from '@/store';
  import { downloadByteFile } from '@/utils';

  const { t } = useI18n();

  const props = defineProps<{
    visible: boolean;
    confirmLoading: boolean;
  }>();

  const emit = defineEmits<{
    (e: 'update:visible', val: boolean): void;
    (e: 'save', files: FileItem[], isRecover: boolean): void;
    (e: 'close'): void;
  }>();

  const validateType = defineModel<'Excel' | 'Xmind'>('validateType', { required: true });
  const fileList = ref<FileItem[]>([]);
  watch(
    () => validateType.value,
    () => {
      fileList.value = [];
    }
  );

  const appStore = useAppStore();
  const currentProjectId = computed(() => appStore.currentProjectId);

  const dialogVisible = computed({
    get: () => props.visible,
    set: (val) => emit('update:visible', val),
  });

  const handleCancel = () => {
    validateType.value = 'Excel';
    fileList.value = [];
    emit('close');
  };

  const fileTypeTip = computed(() => t('caseManagement.featureCase.excelImportTip'));

  const isRecover = ref<boolean>(false);

  // 下载 Excel 模板
  async function downloadExcelTemplate() {
    try {
      const res = await downloadTemplate(currentProjectId.value, 'Excel');
      downloadByteFile(res, 'excel_case.xlsx');
    } catch (error) {
      console.log(error);
    }
  }

  function saveConfirm() {
    emit('save', fileList.value, isRecover.value);
  }
</script>
