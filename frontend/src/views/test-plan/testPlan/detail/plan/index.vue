<template>
  <div v-loading="loading" class="flex h-full min-h-[480px] flex-col p-[16px]">
    <div class="mb-[12px] flex flex-shrink-0 items-center gap-[12px]">
      <template v-if="canEdit">
        <a-button type="primary" :loading="saving" @click="handleSave">
          {{ t('common.save') }}
        </a-button>
        <a-button type="secondary" @click="previewVisible = true">
          {{ t('testPlan.document.preview') }}
        </a-button>
        <a-button type="secondary" :disabled="saving" @click="handleResetTemplate">
          {{ t('testPlan.document.resetTemplate') }}
        </a-button>
      </template>
      <a-button v-permission="['PROJECT_TEST_PLAN:READ']" type="secondary" :loading="exporting" @click="handleExport">
        {{ t('testPlan.document.export') }}
      </a-button>
      <span v-if="canEdit" class="text-[12px] text-[var(--color-text-4)]">{{ t('testPlan.document.resetTip') }}</span>
    </div>
    <div
      class="document-editor flex-1 overflow-auto rounded-[var(--border-radius-small)] border border-[var(--color-text-n8)] p-[12px]"
    >
      <MsRichText
        v-model:raw="content"
        v-model:filedIds="fileIds"
        :upload-image="handleUploadImage"
        :preview-url="`${PreviewEditorImageUrl}/${appStore.currentProjectId}`"
        :editable="canEdit"
        :auto-height="false"
        class="min-h-[400px] w-full"
      />
    </div>
    <a-modal
      v-model:visible="previewVisible"
      :title="t('testPlan.document.preview')"
      :width="900"
      :footer="false"
      unmount-on-close
    >
      <MsRichText :raw="content" :editable="false" class="w-full" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import MsRichText from '@/components/pure/ms-rich-text/MsRichText.vue';

  import { editorUploadFile } from '@/api/modules/case-management/featureCase';
  import { exportTestPlanDocument, getTestPlanDocument, saveTestPlanDocument } from '@/api/modules/test-plan/document';
  import { PreviewEditorImageUrl } from '@/api/requrls/case-management/featureCase';
  import { useI18n } from '@/hooks/useI18n';
  import useModal from '@/hooks/useModal';
  import useAppStore from '@/store/modules/app';
  import { hasAnyPermission } from '@/utils/permission';

  import type { TestPlanDocumentTemplateMeta } from '@/models/testPlan/document';

  import buildTestPlanDocumentTemplate from './documentTemplate';

  const props = defineProps<{
    planId: string;
    status: string;
  }>();

  const emit = defineEmits<{
    (e: 'refresh'): void;
  }>();

  const { t } = useI18n();
  const { openModal } = useModal();
  const appStore = useAppStore();

  const loading = ref(false);
  const saving = ref(false);
  const exporting = ref(false);
  const content = ref('');
  const fileIds = ref<string[]>([]);
  const templateMeta = ref<TestPlanDocumentTemplateMeta | undefined>();
  const previewVisible = ref(false);

  const canEdit = computed(() => {
    return hasAnyPermission(['PROJECT_TEST_PLAN:READ+UPDATE']) && props.status !== 'ARCHIVED';
  });

  async function handleUploadImage(file: File) {
    const { data } = await editorUploadFile({
      fileList: [file],
    });
    return data;
  }

  async function loadDocument() {
    if (!props.planId) {
      return;
    }
    try {
      loading.value = true;
      const res = await getTestPlanDocument(props.planId);
      templateMeta.value = res.templateMeta;
      if (!res.exists || !res.content) {
        content.value = buildTestPlanDocumentTemplate(res.templateMeta);
      } else {
        content.value = res.content;
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      loading.value = false;
    }
  }

  async function handleSave() {
    if (!canEdit.value) {
      return;
    }
    try {
      saving.value = true;
      await saveTestPlanDocument(props.planId, {
        content: content.value,
        contentType: 'RICH_TEXT',
      });
      Message.success(t('common.saveSuccess'));
      emit('refresh');
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      saving.value = false;
    }
  }

  async function handleExport() {
    if (!props.planId) {
      return;
    }
    try {
      exporting.value = true;
      const res = (await exportTestPlanDocument(props.planId)) as any;
      const blob = res?.data instanceof Blob ? res.data : new Blob([res], { type: 'text/html;charset=utf-8' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `test-plan-${props.planId}.html`;
      link.click();
      window.URL.revokeObjectURL(url);
      Message.success(t('testPlan.document.exportSuccess'));
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      exporting.value = false;
    }
  }

  /**
   * 重置为模板：二次确认后仅覆盖本地编辑器内容，需再点「保存」才落库
   */
  function handleResetTemplate() {
    openModal({
      type: 'warning',
      title: t('common.tip'),
      content: t('testPlan.document.resetConfirm'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      okButtonProps: {
        status: 'normal',
      },
      onBeforeOk: async () => {
        content.value = buildTestPlanDocumentTemplate(templateMeta.value);
        Message.info(t('testPlan.document.resetDone'));
      },
      hideCancel: false,
    });
  }

  watch(
    () => props.planId,
    () => {
      loadDocument();
    }
  );

  onMounted(() => {
    loadDocument();
  });
</script>

<style lang="less" scoped>
  .document-editor {
    :deep(.halo-rich-text-editor) {
      min-height: 400px;
    }
  }
</style>
