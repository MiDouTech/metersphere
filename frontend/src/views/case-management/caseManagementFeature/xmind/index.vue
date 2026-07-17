<template>
  <div class="xmind-case-page p-[16px]">
    <div class="mb-[16px] flex items-center justify-between">
      <div class="text-[14px] text-[var(--color-text-2)]">
        {{ t('caseManagement.featureCase.xmindCaseTip') }}
      </div>
      <a-button v-permission="['FUNCTIONAL_CASE:READ+ADD']" type="primary" @click="uploadVisible = true">
        {{ t('caseManagement.featureCase.uploadXmind') }}
      </a-button>
    </div>
    <a-table :data="fileList" :pagination="false" :bordered="false" row-key="id">
      <template #columns>
        <a-table-column :title="t('caseManagement.featureCase.xmindFileName')" data-index="name" />
        <a-table-column :title="t('caseManagement.featureCase.tableColumnUpdateTime')" data-index="updateTime" />
        <a-table-column :title="t('common.operation')" :width="160">
          <template #cell>
            <span class="text-[var(--color-text-4)]">—</span>
          </template>
        </a-table-column>
      </template>
      <template #empty>
        <div class="py-[48px]">
          <a-empty :description="t('caseManagement.featureCase.xmindCaseEmpty')" />
        </div>
      </template>
    </a-table>

    <a-modal
      v-model:visible="uploadVisible"
      title-align="start"
      class="ms-modal-form ms-modal-medium"
      :title="t('caseManagement.featureCase.uploadXmind')"
      :ok-text="t('common.confirm')"
      :cancel-text="t('common.cancel')"
      :ok-button-props="{ disabled: uploadFileList.length < 1 }"
      @ok="handleUpload"
      @cancel="handleUploadCancel"
    >
      <MsUpload
        v-model:file-list="uploadFileList"
        class="w-full"
        accept="xmind"
        size-unit="MB"
        main-text="caseManagement.featureCase.dragOrClick"
        :sub-text="t('caseManagement.featureCase.onlyXmindTip', { size: appStore.getFileMaxSize })"
        :show-file-list="false"
        :auto-upload="false"
        :allow-repeat="true"
        :file-type-tip="t('caseManagement.featureCase.xmindImportTip')"
      />
      <!-- TODO: 对接后端 POST /functional/case/xmind-file/upload，上传后仅存文件资产，不解析为功能用例 -->
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import { FileItem } from '@arco-design/web-vue';

  import MsUpload from '@/components/pure/ms-upload/index.vue';

  import { useI18n } from '@/hooks/useI18n';
  import { useAppStore } from '@/store';

  import Message from '@arco-design/web-vue/es/message';

  const { t } = useI18n();
  const appStore = useAppStore();

  // MVP 占位：后端 xmind-file API 就绪后替换为真实列表
  const fileList = ref<{ id: string; name: string; updateTime: string }[]>([]);

  const uploadVisible = ref(false);
  const uploadFileList = ref<FileItem[]>([]);

  function handleUploadCancel() {
    uploadFileList.value = [];
    uploadVisible.value = false;
  }

  function handleUpload() {
    // TODO: 调用 xmind-file/upload，成功后刷新列表
    Message.info(t('caseManagement.featureCase.xmindUploadTodo'));
    handleUploadCancel();
  }
</script>
