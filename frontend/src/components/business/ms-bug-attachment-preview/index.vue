<template>
  <a-modal
    v-model:visible="visible"
    :title="title"
    :footer="false"
    :width="960"
    unmount-on-close
    @cancel="clearPreview"
  >
    <a-spin :loading="loading" class="block w-full">
      <pre v-if="previewType === 'text'" class="attachment-preview-text">{{ textContent }}</pre>
      <div v-else-if="previewType === 'html'" v-dompurify-html="htmlContent" class="attachment-preview-html"></div>
      <iframe
        v-else-if="previewUrl"
        :src="previewUrl"
        title="Attachment preview"
        class="h-[70vh] w-full rounded border border-solid border-[var(--color-text-n8)]"
      ></iframe>
      <a-empty v-else />
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import { MsFileItem } from '@/components/pure/ms-upload/types';

  import { downloadFileRequest } from '@/api/modules/bug-management';
  import { useI18n } from '@/hooks/useI18n';

  const MAX_BROWSER_PREVIEW_SIZE = 50 * 1024 * 1024;

  const props = defineProps<{ projectId: string; bugId: string }>();
  const { t } = useI18n();
  const visible = ref(false);
  const loading = ref(false);
  const title = ref('');
  const previewUrl = ref('');
  const textContent = ref('');
  const htmlContent = ref('');
  const previewType = ref<'iframe' | 'text' | 'html'>('iframe');

  function getFileName(item: MsFileItem) {
    return item.name || item.file?.name || '';
  }

  function getExtension(item: MsFileItem) {
    const name = getFileName(item);
    const index = name.lastIndexOf('.');
    return index > -1 ? name.slice(index + 1).toLowerCase() : '';
  }

  function mimeType(item: MsFileItem) {
    const types: Record<string, string> = {
      pdf: 'application/pdf',
      txt: 'text/plain;charset=utf-8',
      csv: 'text/csv;charset=utf-8',
      docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      xls: 'application/vnd.ms-excel',
      xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    };
    return types[getExtension(item)] || item.file?.type || 'application/octet-stream';
  }

  function clearPreview() {
    if (previewUrl.value.startsWith('blob:')) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = '';
    textContent.value = '';
    htmlContent.value = '';
    previewType.value = 'iframe';
  }

  function escapeHtml(value: string) {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  async function renderSpreadsheet(blob: Blob) {
    const XLSX = await import('xlsx');
    const workbook = XLSX.read(await blob.arrayBuffer(), { type: 'array' });
    previewType.value = 'html';
    htmlContent.value = workbook.SheetNames.map((sheetName, index) => {
      const table = XLSX.utils.sheet_to_html(workbook.Sheets[sheetName], { id: `attachment-preview-sheet-${index}` });
      return `<section class="attachment-preview-sheet"><h3>${escapeHtml(sheetName)}</h3>${table}</section>`;
    }).join('');
  }

  async function renderDocx(blob: Blob) {
    const mammoth = await import('mammoth');
    const result = await mammoth.convertToHtml({ arrayBuffer: await blob.arrayBuffer() });
    previewType.value = 'html';
    htmlContent.value = result.value || `<p>${t('common.noData')}</p>`;
  }

  async function open(item: MsFileItem) {
    clearPreview();
    const extension = getExtension(item);
    if (extension === 'doc') {
      Message.warning(t('common.legacyDocPreviewUnsupported'));
      return;
    }
    visible.value = true;
    loading.value = true;
    title.value = getFileName(item);
    const request = {
      projectId: props.projectId,
      bugId: props.bugId,
      fileId: item.uid,
      associated: !item.local,
      fileName: getFileName(item),
    };
    try {
      const bytes = await downloadFileRequest(request);
      const blob = new Blob([bytes], { type: mimeType(item) });
      if (blob.size > MAX_BROWSER_PREVIEW_SIZE) throw new Error('ATTACHMENT_PREVIEW_TOO_LARGE');
      if (['txt', 'log', 'csv', 'md', 'markdown', 'json', 'xml', 'yml', 'yaml'].includes(extension)) {
        previewType.value = 'text';
        textContent.value = await blob.text();
      } else if (['xls', 'xlsx'].includes(extension)) {
        await renderSpreadsheet(blob);
      } else if (extension === 'docx') {
        await renderDocx(blob);
      } else {
        previewType.value = 'iframe';
        previewUrl.value = URL.createObjectURL(blob);
      }
    } catch (error) {
      Message.error(
        error instanceof Error && error.message === 'ATTACHMENT_PREVIEW_TOO_LARGE'
          ? '文件超过 50MB，请下载后查看'
          : t('common.operationFailed')
      );
      visible.value = false;
    } finally {
      loading.value = false;
    }
  }

  watch(visible, (value) => {
    if (!value) clearPreview();
  });
  onBeforeUnmount(clearPreview);
  defineExpose({ open });
</script>

<style lang="less" scoped>
  .attachment-preview-text {
    overflow: auto;
    margin: 0;
    padding: 16px;
    max-height: 70vh;
    white-space: pre-wrap;
    background: var(--color-fill-1);
    overflow-wrap: anywhere;
  }
  .attachment-preview-html {
    overflow: auto;
    padding: 16px;
    max-height: 70vh;
    background: var(--color-bg-1);
  }
  .attachment-preview-html :deep(table) {
    border-collapse: collapse;
  }
  .attachment-preview-html :deep(td),
  .attachment-preview-html :deep(th) {
    padding: 6px 10px;
    border: 1px solid var(--color-border-2);
  }
</style>
