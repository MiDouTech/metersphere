<template>
  <div class="functional-comment-list min-w-0" :aria-busy="loading || busy">
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <span class="text-[var(--color-text-3)]">{{ t('ms.comment.rootTotal', { total }) }}</span>
      <CommentDeliveries :case-id="caseId" />
      <a-button :loading="loading" :disabled="busy || editing" @click="reload()">{{
        t('ms.comment.refresh')
      }}</a-button>
    </div>
    <a-alert v-if="errorMessage" type="error" class="mb-3">
      {{ errorMessage }}{{ rows.length ? `；${t('ms.comment.previousData')}` : '' }}
    </a-alert>
    <a-spin :loading="loading || busy" class="w-full">
      <MsComment
        v-if="rows.length"
        :key="`${caseId}:${current}`"
        :comment-list="rows"
        :upload-image="uploadImage"
        :preview-url="previewUrl"
        :permissions="['FUNCTIONAL_CASE:READ+COMMENT']"
        :disabled="busy"
        @update-or-add="updateOrAdd"
        @delete="remove"
        @reply-page="loadReplies"
        @editing-change="editing = $event"
      />
      <MsEmpty v-else-if="!loading && !errorMessage" />
    </a-spin>
    <a-pagination
      class="mt-4 flex flex-wrap gap-y-2"
      :total="total"
      :current="current"
      :page-size="pageSize"
      :page-size-options="[10, 20, 50, 100]"
      show-total
      show-page-size
      :disabled="loading || busy || editing"
      @change="changePage"
      @page-size-change="changeSize"
    />
  </div>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';
  import { Message, Modal } from '@arco-design/web-vue';

  import MsEmpty from '@/components/pure/ms-empty/index.vue';
  import CommentDeliveries from './comment-deliveries.vue';

  import {
    addOrUpdateCommentList,
    deleteCommentList,
    getCaseCommentPage,
  } from '@/api/modules/case-management/featureCase';
  import { useI18n } from '@/hooks/useI18n';
  import useLocalForage from '@/hooks/useLocalForage';
  import { useUserStore } from '@/store';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';

  import MsComment from './comment';
  import { CommentItem, CommentParams } from './types';

  const props = defineProps<{ caseId: string; previewUrl: string; uploadImage: (file: File) => Promise<any> }>();
  const emit = defineEmits<{ (event: 'changed'): void; (event: 'total', total: number): void }>();
  const { t } = useI18n();
  const { getItem, setItem } = useLocalForage();
  const preferenceKey = `ms:list-page-size:${useUserStore().id}:case-comments`;
  const rows = ref<CommentItem[]>([]);
  const total = ref(0);
  const current = ref(1);
  const pageSize = ref(10);
  const loading = ref(false);
  const busy = ref(false);
  const editing = ref(false);
  const errorMessage = ref('');
  let sequence = 0;
  let context = 0;
  let disposed = false;
  const replySequences = new Map<string, number>();
  const preferenceReady = getItem<number>(preferenceKey, true).then((size) => {
    if (size && [10, 20, 50, 100].includes(size)) pageSize.value = size;
  });

  async function reload(reset = false) {
    const request = ++sequence;
    const { caseId } = props;
    if (reset) current.value = 1;
    if (!caseId || disposed) return;
    loading.value = true;
    errorMessage.value = '';
    try {
      await preferenceReady;
      if (request !== sequence || disposed || caseId !== props.caseId) return;
      const result = await getCaseCommentPage(caseId, current.value, pageSize.value);
      if (request !== sequence || disposed || caseId !== props.caseId) return;
      rows.value = result.list;
      total.value = result.total;
      current.value = result.current;
      replySequences.clear();
      emit('total', total.value);
    } catch (error) {
      if (request !== sequence || disposed || caseId !== props.caseId) return;
      const detail = ensureAppError(error, t('ms.comment.loadFailed'));
      errorMessage.value = formatAppErrorMessage(detail);
      if (detail.category === 'permission' || [401, 403, 404].includes(detail.status || 0)) {
        rows.value = [];
        total.value = 0;
        emit('total', 0);
      }
    } finally {
      if (request === sequence) loading.value = false;
    }
  }
  function changePage(page: number) {
    current.value = page;
    editing.value = false;
    reload();
  }
  function changeSize(size: number) {
    if (![10, 20, 50, 100].includes(size)) return;
    pageSize.value = size;
    current.value = 1;
    editing.value = false;
    setItem(preferenceKey, size, true);
    reload();
  }
  async function loadReplies(parentId: string, page: number, size: number) {
    const parent = rows.value.find((row) => row.id === parentId);
    if (!parent || disposed) return;
    const capturedContext = context;
    const request = (replySequences.get(parentId) || 0) + 1;
    replySequences.set(parentId, request);
    parent.childLoading = true;
    parent.childError = '';
    try {
      const result = await getCaseCommentPage(props.caseId, page, size, parentId);
      if (
        disposed ||
        capturedContext !== context ||
        replySequences.get(parentId) !== request ||
        !rows.value.includes(parent)
      )
        return;
      parent.childComments = result.list;
      parent.childTotal = result.total;
      parent.childCurrent = result.current;
      parent.childPageSize = result.pageSize;
    } catch (error) {
      if (
        disposed ||
        capturedContext !== context ||
        replySequences.get(parentId) !== request ||
        !rows.value.includes(parent)
      )
        return;
      const detail = ensureAppError(error, t('ms.comment.loadFailed'));
      parent.childError = formatAppErrorMessage(detail);
      if (detail.category === 'permission' || [401, 403, 404].includes(detail.status || 0)) parent.childComments = [];
    } finally {
      if (replySequences.get(parentId) === request) parent.childLoading = false;
    }
  }
  async function updateOrAdd(item: CommentParams, callback: (ok: boolean) => void) {
    if (busy.value || item.caseId !== props.caseId) {
      callback(false);
      return;
    }
    const capturedContext = context;
    busy.value = true;
    try {
      await addOrUpdateCommentList(item);
      if (disposed || capturedContext !== context) return;
      callback(true);
      editing.value = false;
      emit('changed');
      await reload();
    } catch (error) {
      if (disposed || capturedContext !== context) return;
      callback(false);
      Message.error(formatAppErrorMessage(ensureAppError(error, t('common.publishFailed'))));
    } finally {
      if (capturedContext === context) busy.value = false;
    }
  }
  function remove(id: string) {
    const item = rows.value.flatMap((row) => [row, ...(row.childComments || [])]).find((row) => row.id === id);
    if (!item?.canDelete || busy.value) return;
    const { caseId } = props;
    const capturedContext = context;
    const version = item.updateTime;
    Modal.error({
      title: t('ms.comment.deleteConfirm'),
      content: t('ms.comment.deleteCaseContent'),
      okText: t('common.confirmDelete'),
      cancelText: t('common.cancel'),
      okButtonProps: { status: 'danger' },
      hideCancel: false,
      onBeforeOk: async () => {
        if (disposed || capturedContext !== context || busy.value) return false;
        busy.value = true;
        try {
          await deleteCommentList(id, caseId, version);
          if (disposed || capturedContext !== context) return true;
          Message.success(t('common.deleteSuccess'));
          emit('changed');
          await reload();
          return true;
        } catch (error) {
          if (!disposed && capturedContext === context)
            Message.error(formatAppErrorMessage(ensureAppError(error, t('ms.comment.loadFailed'))));
          return false;
        } finally {
          if (capturedContext === context) busy.value = false;
        }
      },
    });
  }
  watch(
    () => props.caseId,
    () => {
      context += 1;
      sequence += 1;
      replySequences.clear();
      rows.value = [];
      total.value = 0;
      current.value = 1;
      busy.value = false;
      loading.value = false;
      editing.value = false;
      errorMessage.value = '';
      emit('total', 0);
      reload();
    },
    { immediate: true }
  );
  onBeforeUnmount(() => {
    disposed = true;
    context += 1;
    sequence += 1;
    replySequences.clear();
  });
  defineExpose({ reload });
</script>
