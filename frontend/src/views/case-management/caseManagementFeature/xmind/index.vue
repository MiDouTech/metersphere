<template>
  <div class="xmind-case-page p-[16px]">
    <div class="mb-[16px] flex items-center justify-between">
      <div class="text-[14px] text-[var(--color-text-2)]">
        {{ t('caseManagement.featureCase.xmindCaseTip') }}
      </div>
      <div class="flex items-center gap-[12px]">
        <a-input-search
          v-model:model-value="keyword"
          allow-clear
          class="w-[220px]"
          :placeholder="t('caseManagement.featureCase.xmindSearchPlaceholder')"
          @search="loadList"
          @press-enter="loadList"
          @clear="loadList"
        />
        <a-button v-permission="['FUNCTIONAL_CASE:READ+ADD']" type="primary" @click="uploadVisible = true">
          {{ t('caseManagement.featureCase.uploadXmind') }}
        </a-button>
      </div>
    </div>
    <a-table :data="fileList" :pagination="pagination" :bordered="false" :loading="loading" row-key="id">
      <template #columns>
        <a-table-column :title="t('caseManagement.featureCase.xmindFileName')" data-index="name">
          <template #cell="{ record }">
            <a-button type="text" class="px-0" @click="openPreview(record)">{{ record.name }}</a-button>
          </template>
        </a-table-column>
        <a-table-column
          :title="t('caseManagement.featureCase.tableColumnUpdateTime')"
          data-index="updateTimeText"
          :width="180"
        />
        <a-table-column :title="t('caseManagement.featureCase.xmindUploader')" data-index="createUser" :width="140" />
        <a-table-column :title="t('common.operation')" :width="260">
          <template #cell="{ record }">
            <MsButton v-permission="['FUNCTIONAL_CASE:READ']" @click="openPreview(record)">
              {{ t('caseManagement.featureCase.xmindView') }}
            </MsButton>
            <MsButton v-permission="['FUNCTIONAL_CASE:READ']" @click="handleDownload(record)">
              {{ t('common.download') }}
            </MsButton>
            <MsButton v-permission="['FUNCTIONAL_CASE:READ+UPDATE']" @click="openRename(record)">
              {{ t('common.rename') }}
            </MsButton>
            <MsButton v-permission="['FUNCTIONAL_CASE:READ+DELETE']" status="danger" @click="handleDelete(record)">
              {{ t('common.delete') }}
            </MsButton>
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
      :ok-loading="uploading"
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
        :show-file-list="true"
        :auto-upload="false"
        :limit="1"
        :allow-repeat="false"
        :file-type-tip="t('caseManagement.featureCase.xmindImportTip')"
      />
    </a-modal>

    <a-modal
      v-model:visible="renameVisible"
      title-align="start"
      class="ms-modal-small"
      :title="t('common.rename')"
      :ok-loading="renaming"
      @ok="handleRename"
    >
      <a-input v-model:model-value="renameName" :max-length="255" allow-clear />
    </a-modal>

    <a-drawer v-model:visible="previewVisible" :width="960" :title="previewTitle" unmount-on-close :footer="false">
      <div v-if="previewLoading" class="flex min-h-[400px] items-center justify-center">
        <a-spin />
      </div>
      <MsMinderEditor
        v-else-if="previewJson"
        v-model:import-json="previewJson"
        :disabled="true"
        :can-show-float-menu="false"
        :xmind-interaction="true"
        :height="560"
      />
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
  import { nextTick, onActivated, onBeforeMount, reactive, ref, watch } from 'vue';
  import { FileItem, Message } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import MsButton from '@/components/pure/ms-button/index.vue';
  import MsMinderEditor from '@/components/pure/ms-minder-editor/minderEditor.vue';
  import type { MinderJson } from '@/components/pure/ms-minder-editor/props';
  import MsUpload from '@/components/pure/ms-upload/index.vue';

  import {
    deleteXmindFile,
    downloadXmindFile,
    getXmindFilePage,
    previewXmindFile,
    renameXmindFile,
    uploadXmindFile,
  } from '@/api/modules/case-management/xmindFile';
  import { useI18n } from '@/hooks/useI18n';
  import useModal from '@/hooks/useModal';
  import { useAppStore } from '@/store';
  import { characterLimit } from '@/utils';

  import type { XmindFileItem } from '@/models/caseManagement/xmindFile';

  const props = defineProps<{
    /** 父级 Tab 是否处于 Xmind；切回时补一次加载，避免空列表 */
    active?: boolean;
  }>();

  const { t } = useI18n();
  const { openModal } = useModal();
  const appStore = useAppStore();

  const loading = ref(false);
  const keyword = ref('');
  const fileList = ref<(XmindFileItem & { updateTimeText: string })[]>([]);
  const loadedOnce = ref(false);
  const pagination = reactive({
    current: 1,
    pageSize: 20,
    total: 0,
    showTotal: true,
    showPageSize: true,
    onChange: undefined as ((page: number) => void) | undefined,
    onPageSizeChange: undefined as ((size: number) => void) | undefined,
  });

  function normalizePageResult(res: any): { list: XmindFileItem[]; total: number } {
    const list = res?.list ?? res?.data?.list ?? (Array.isArray(res) ? res : []) ?? [];
    const total = res?.total ?? res?.data?.total ?? list.length ?? 0;
    return { list: Array.isArray(list) ? list : [], total: Number(total) || 0 };
  }

  async function loadList() {
    if (!appStore.currentProjectId) return;
    loading.value = true;
    try {
      const res = await getXmindFilePage({
        projectId: appStore.currentProjectId,
        current: pagination.current,
        pageSize: pagination.pageSize,
        keyword: keyword.value.trim(),
      });
      const { list, total } = normalizePageResult(res);
      fileList.value = list.map((item: XmindFileItem) => ({
        ...item,
        updateTimeText: item.updateTime ? dayjs(item.updateTime).format('YYYY-MM-DD HH:mm:ss') : '-',
      }));
      pagination.total = total;
      loadedOnce.value = true;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      loading.value = false;
    }
  }

  pagination.onChange = (page: number) => {
    pagination.current = page;
    loadList();
  };
  pagination.onPageSizeChange = (size: number) => {
    pagination.pageSize = size;
    pagination.current = 1;
    loadList();
  };

  const uploadVisible = ref(false);
  const uploading = ref(false);
  const uploadFileList = ref<FileItem[]>([]);

  const renameVisible = ref(false);
  const renaming = ref(false);
  const renameName = ref('');
  const renameId = ref('');

  const previewVisible = ref(false);
  const previewLoading = ref(false);
  const previewTitle = ref('');
  const previewJson = ref<MinderJson | null>(null);

  function handleUploadCancel() {
    uploadFileList.value = [];
    uploadVisible.value = false;
  }

  async function handleUpload() {
    const fileItem = uploadFileList.value[0];
    const raw = (fileItem as any)?.file as File | undefined;
    if (!raw) {
      Message.warning(t('caseManagement.featureCase.xmindSelectFile'));
      return;
    }
    uploading.value = true;
    try {
      await uploadXmindFile({ projectId: appStore.currentProjectId }, raw);
      Message.success(t('caseManagement.featureCase.xmindUploadSuccess'));
      handleUploadCancel();
      await loadList();
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      uploading.value = false;
    }
  }

  async function handleDownload(record: XmindFileItem) {
    try {
      const res = (await downloadXmindFile(record.id)) as any;
      const blob = res?.data instanceof Blob ? res.data : new Blob([res]);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = record.originalName || `${record.name}.xmind`;
      link.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    }
  }

  function openRename(record: XmindFileItem) {
    renameId.value = record.id;
    renameName.value = record.name;
    renameVisible.value = true;
  }

  async function handleRename() {
    if (!renameName.value.trim()) {
      Message.warning(t('common.nameNotNull'));
      return;
    }
    renaming.value = true;
    try {
      await renameXmindFile({ id: renameId.value, name: renameName.value.trim() });
      Message.success(t('common.updateSuccess'));
      renameVisible.value = false;
      await loadList();
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      renaming.value = false;
    }
  }

  async function openPreview(record: XmindFileItem) {
    previewTitle.value = record.name;
    previewVisible.value = true;
    previewLoading.value = true;
    previewJson.value = null;
    try {
      previewJson.value = await previewXmindFile(record.id);
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
      Message.error(t('caseManagement.featureCase.xmindPreviewFailed'));
    } finally {
      previewLoading.value = false;
    }
  }

  function handleDelete(record: XmindFileItem) {
    openModal({
      type: 'error',
      title: t('caseManagement.featureCase.xmindDeleteTitle', { name: characterLimit(record.name) }),
      content: t('caseManagement.featureCase.xmindDeleteContent'),
      okText: t('common.confirmDelete'),
      cancelText: t('common.cancel'),
      okButtonProps: { status: 'danger' },
      onBeforeOk: async () => {
        try {
          await deleteXmindFile(record.id);
          Message.success(t('common.deleteSuccess'));
          await loadList();
        } catch (error) {
          // eslint-disable-next-line no-console
          console.log(error);
        }
      },
      hideCancel: false,
    });
  }

  watch(
    () => props.active,
    (active) => {
      if (active && (!loadedOnce.value || fileList.value.length === 0)) {
        nextTick(() => loadList());
      }
    }
  );

  watch(
    () => appStore.currentProjectId,
    (projectId) => {
      if (projectId && props.active !== false) {
        pagination.current = 1;
        loadList();
      }
    }
  );

  onBeforeMount(() => {
    loadList();
  });

  onActivated(() => {
    if (props.active !== false) {
      loadList();
    }
  });
</script>
