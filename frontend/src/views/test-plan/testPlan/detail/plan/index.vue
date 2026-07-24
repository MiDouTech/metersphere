<template>
  <div v-loading="loading" class="plan-document p-[16px]">
    <div class="mb-[16px] flex flex-wrap items-center justify-between gap-[12px]">
      <div class="min-w-0 flex-1">
        <div class="truncate text-[16px] font-medium text-[var(--color-text-1)]">
          {{ t('testPlan.document.title') }}
        </div>
        <div class="mt-[4px] text-[12px] text-[var(--color-text-4)]">
          {{
            autoSaveStatus === 'locked-readonly'
              ? lockMessage || t('common.autoSave.lockedByOther')
              : canEdit
              ? t('testPlan.document.editTip')
              : t('testPlan.document.readonlyTip')
          }}
        </div>
      </div>
      <div class="flex flex-wrap items-center gap-[8px]">
        <MsAutoSaveStatus
          v-if="editorCanEdit"
          :status="autoSaveStatus"
          :last-saved-at="lastSavedAt"
          :lock-message="lockMessage"
          show-retry
          @retry="manualSave"
        />
        <template v-if="editorCanEdit">
          <a-button type="primary" :loading="saving || autoSaveSaving" @click="handleSave">
            {{ t('common.save') }}
          </a-button>
          <a-button type="secondary" @click="previewVisible = true">
            {{ t('testPlan.document.preview') }}
          </a-button>
          <a-button type="secondary" :disabled="saving || autoSaveSaving" @click="handleResetTemplate">
            {{ t('testPlan.document.resetTemplate') }}
          </a-button>
        </template>
        <a-button v-permission="['PROJECT_TEST_PLAN:READ']" type="secondary" :loading="exporting" @click="handleExport">
          {{ t('testPlan.document.export') }}
        </a-button>
      </div>
    </div>

    <section class="document-section">
      <h3 class="section-title">{{ t('testPlan.document.contentSection') }}</h3>
      <div v-if="editorCanEdit" class="mb-[8px] text-[12px] text-[var(--color-text-4)]">
        {{ t('testPlan.document.resetTip') }}
      </div>
      <div class="document-editor rounded-[var(--border-radius-small)] bg-[var(--color-bg-1)]">
        <MsRichText
          v-model:raw="content"
          v-model:filedIds="fileIds"
          :upload-image="handleUploadImage"
          :preview-url="`${PreviewEditorImageUrl}/${appStore.currentProjectId}`"
          :editable="editorCanEdit"
          :auto-height="true"
          class="w-full"
        />
      </div>
    </section>

    <a-modal
      v-model:visible="previewVisible"
      :title="t('testPlan.document.preview')"
      :width="960"
      :footer="false"
      unmount-on-close
      title-align="start"
    >
      <div class="preview-body max-h-[70vh] overflow-auto p-[8px]">
        <MsRichText :raw="content" :editable="false" class="w-full" />
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, nextTick, onMounted, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import MsRichText from '@/components/pure/ms-rich-text/MsRichText.vue';
  import MsAutoSaveStatus from '@/components/business/ms-auto-save-status/index.vue';

  import { editorUploadFile } from '@/api/modules/case-management/featureCase';
  import { exportTestPlanDocument, getTestPlanDocument, saveTestPlanDocument } from '@/api/modules/test-plan/document';
  import { PreviewEditorImageUrl } from '@/api/requrls/case-management/featureCase';
  import useAutoSaveEditor from '@/hooks/useAutoSaveEditor';
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
  const formReady = ref(false);
  let skipAutoSaveDirty = false;

  const canEdit = computed(() => {
    return hasAnyPermission(['PROJECT_TEST_PLAN:READ+UPDATE']) && props.status !== 'ARCHIVED';
  });

  const planResourceId = computed(() => props.planId || '');
  const projectId = computed(() => appStore.currentProjectId);
  const autoSaveCanEdit = computed(() => formReady.value && canEdit.value && !!planResourceId.value);

  function serializeDocument() {
    return JSON.stringify({
      testPlanId: props.planId,
      projectId: projectId.value,
      content: content.value || '',
      contentType: 'RICH_TEXT',
    });
  }

  async function persistDocument(silent = false) {
    if (!canEdit.value) {
      return false;
    }
    try {
      saving.value = true;
      skipAutoSaveDirty = true;
      await saveTestPlanDocument(props.planId, {
        content: content.value,
        contentType: 'RICH_TEXT',
      });
      if (!silent) {
        Message.success(t('common.saveSuccess'));
        emit('refresh');
      }
      return true;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
      return false;
    } finally {
      saving.value = false;
      nextTick(() => {
        skipAutoSaveDirty = false;
      });
    }
  }

  const {
    status: autoSaveStatus,
    saving: autoSaveSaving,
    lastSavedAt,
    readOnly: autoSaveReadOnly,
    lockMessage,
    markDirty,
    manualSave,
  } = useAutoSaveEditor({
    resourceType: 'TEST_PLAN_DOCUMENT',
    resourceId: planResourceId,
    projectId,
    canEdit: autoSaveCanEdit,
    serialize: serializeDocument,
    save: async () => {
      const ok = await persistDocument(true);
      if (!ok) throw new Error('persistDocument failed');
    },
    applyPayload: async (payload: string) => {
      skipAutoSaveDirty = true;
      try {
        const data = JSON.parse(payload);
        content.value = data.content ?? '';
        await nextTick();
      } finally {
        skipAutoSaveDirty = false;
      }
    },
    debounceMs: 1800,
  });

  const editorCanEdit = computed(() => canEdit.value && !autoSaveReadOnly.value);

  watch(content, () => {
    if (!autoSaveCanEdit.value || skipAutoSaveDirty || autoSaveReadOnly.value) return;
    if (autoSaveStatus.value === 'saving') return;
    markDirty();
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
      formReady.value = false;
      skipAutoSaveDirty = true;
      const res = await getTestPlanDocument(props.planId);
      templateMeta.value = res.templateMeta;
      if (!res.exists || !res.content) {
        content.value = buildTestPlanDocumentTemplate(res.templateMeta);
      } else {
        content.value = res.content;
      }
      await nextTick();
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      skipAutoSaveDirty = false;
      loading.value = false;
      formReady.value = true;
    }
  }

  async function handleSave() {
    const ok = await manualSave();
    if (ok) {
      Message.success(t('common.saveSuccess'));
      emit('refresh');
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

  defineExpose({
    getAutoSaveStatus: () => autoSaveStatus.value,
  });
</script>

<style lang="less" scoped>
  .document-section {
    margin-bottom: 8px;
  }
  .section-title {
    margin: 0 0 12px;
    font-size: 15px;
    font-weight: 600;
    color: var(--color-text-1);
  }
  .document-editor {
    /* 正文随内容撑开，由页面全局滚动，避免编辑器内部滚动条 */
    :deep(.rich-wrapper) {
      overflow: visible;
    }
    :deep(.editor-content),
    :deep(.halo-rich-text-editor .ProseMirror) {
      overflow: visible !important;
      max-height: none !important;
    }
  }
</style>
