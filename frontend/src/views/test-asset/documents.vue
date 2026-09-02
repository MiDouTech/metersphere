<template>
  <TestAssetPage>
    <MsCard simple>
      <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <div class="text-base font-medium">业务文档</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]"
            >项目内已授权的需求与业务资料；解析成功后自动形成不可变资产版本。</div
          >
        </div>
        <div class="flex gap-2">
          <input ref="fileInput" class="hidden" type="file" :accept="acceptedTypes" @change="handleFileChange" />
          <a-button v-permission="['FUNCTIONAL_CASE_AI:UPLOAD']" :loading="uploading" @click="fileInput?.click()"
            >上传文档</a-button
          >
          <a-button v-permission="['FUNCTIONAL_CASE_AI:GENERATE']" type="primary" @click="openAiGenerate()"
            >AI 生成用例</a-button
          >
        </div>
      </div>
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <a-input-search
          v-model="query.keyword"
          class="w-[280px]"
          allow-clear
          placeholder="搜索名称、摘要或 ID"
          @search="search"
        />
        <a-select v-model="query.parseStatus" class="w-[180px]" allow-clear placeholder="解析状态" @change="search">
          <a-option value="UPLOADED">待解析</a-option><a-option value="PARSING">解析中</a-option>
          <a-option value="PARSED">解析成功</a-option><a-option value="FAILED">解析失败</a-option>
        </a-select>
        <a-select
          v-model="query.creationSources"
          multiple
          allow-clear
          class="w-[200px]"
          placeholder="建立方式"
          @change="search"
        >
          <a-option v-for="item in sourceOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
        </a-select>
        <a-tree-select
          v-model="query.categoryId"
          allow-clear
          allow-search
          class="w-[200px]"
          :data="categories"
          :field-names="{ key: 'id', title: 'name', children: 'children' }"
          placeholder="资产分类"
          @change="search"
        />
        <a-checkbox v-model="query.includeDescendants" @change="search">含子分类</a-checkbox>
        <a-button :loading="loading" @click="load">刷新</a-button>
        <a-button v-if="canAssignCategory" :disabled="!selectedDocumentIds.length" @click="batchCategoryVisible = true"
          >批量归类</a-button
        >
      </div>
      <a-table
        v-model:selected-keys="selectedDocumentIds"
        :data="documents"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
        :row-selection="canAssignCategory ? { type: 'checkbox', showCheckedAll: true } : undefined"
        @page-change="changePage"
        @page-size-change="changePageSize"
      >
        <template #empty><a-empty description="暂无业务文档，可上传 Markdown、Word、PDF、网页文件或图片。" /></template>
        <template #columns>
          <a-table-column title="文档" :width="330">
            <template #cell="{ record }">
              <div class="font-medium">{{ record.originalName }}</div>
              <div class="mt-1 truncate text-xs text-[var(--color-text-3)]">{{ record.summary || record.id }}</div>
            </template>
          </a-table-column>
          <a-table-column title="解析状态" :width="120">
            <template #cell="{ record }"
              ><a-tag :color="statusColor(record.parseStatus)">{{ statusText(record.parseStatus) }}</a-tag></template
            >
          </a-table-column>
          <a-table-column title="建立方式" :width="120"
            ><template #cell="{ record }">
              <a-tooltip v-if="record.creationSource === 'UNKNOWN'" content="历史来源信息不足，待治理">
                <a-tag :color="sourceMeta(record.creationSource).color">{{
                  sourceMeta(record.creationSource).label
                }}</a-tag>
              </a-tooltip>
              <a-tag v-else :color="sourceMeta(record.creationSource).color">{{
                sourceMeta(record.creationSource).label
              }}</a-tag>
            </template></a-table-column
          >
          <a-table-column title="资产分类" :width="210">
            <template #cell="{ record }">
              <a-tree-select
                v-if="canAssignCategory"
                v-model="record.categoryId"
                class="asset-category-select w-full"
                allow-clear
                allow-search
                :data="categories"
                :field-names="{ key: 'id', title: 'name', children: 'children' }"
                @change="(value) => changeDocumentCategory(record, value as string | undefined)"
              >
                <template #label
                  ><a-tooltip :content="record.categoryPath || '未分类'">{{
                    record.categoryPath || '未分类'
                  }}</a-tooltip></template
                >
              </a-tree-select>
              <a-tooltip v-else :content="record.categoryPath || '未分类'">{{
                record.categoryPath || '未分类'
              }}</a-tooltip>
            </template>
          </a-table-column>
          <a-table-column title="资产版本" :width="120">
            <template #cell="{ record }"
              ><a-link v-if="record.assetVersionNo" @click="openVersions(record)">v{{ record.assetVersionNo }}</a-link
              ><span v-else>-</span></template
            >
          </a-table-column>
          <a-table-column title="解析器" data-index="parserType" :width="180" />
          <a-table-column title="所有者" data-index="createUser" :width="140" />
          <a-table-column title="更新时间" :width="180"
            ><template #cell="{ record }">{{ formatTime(record.updateTime) }}</template></a-table-column
          >
          <a-table-column title="操作" :width="330" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-link @click="openDetail(record)">详情</a-link>
                <a-link v-permission="['FUNCTIONAL_CASE_AI:READ']" @click="downloadDocument(record)">下载</a-link>
                <a-link
                  v-if="record.parseStatus === 'FAILED'"
                  v-permission="['FUNCTIONAL_CASE_AI:UPLOAD']"
                  @click="retryDocument(record)"
                  >重新解析</a-link
                >
                <a-link
                  v-permission="['FUNCTIONAL_CASE_AI:GENERATE']"
                  :disabled="record.parseStatus !== 'PARSED'"
                  @click="openAiGenerate(record)"
                  >生成用例</a-link
                >
                <a-link :disabled="!record.assetVersionNo" @click="openRelations(record)">追溯</a-link>
                <a-link
                  v-permission="['FUNCTIONAL_CASE_AI:UPLOAD']"
                  status="danger"
                  :disabled="record.parseStatus === 'PARSING'"
                  @click="removeDocument(record)"
                  >删除</a-link
                >
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </MsCard>

    <a-drawer v-model:visible="detailVisible" title="业务文档详情" :width="560" unmount-on-close>
      <a-spin :loading="detailLoading" class="block">
        <a-descriptions v-if="detail" :column="1" bordered>
          <a-descriptions-item label="文档名称">{{ detail.originalName }}</a-descriptions-item>
          <a-descriptions-item label="文档 ID">{{ detail.id }}</a-descriptions-item>
          <a-descriptions-item label="解析状态">{{ statusText(detail.parseStatus) }}</a-descriptions-item>
          <a-descriptions-item label="建立方式">{{
            sourceMeta(detailMetadata?.creationSource).label
          }}</a-descriptions-item>
          <a-descriptions-item label="所属分类">{{ detailMetadata?.categoryPath || '未分类' }}</a-descriptions-item>
          <a-descriptions-item label="解析器">{{ detail.parserType || '-' }}</a-descriptions-item>
          <a-descriptions-item label="文件大小">{{ formatSize(detail.fileSize) }}</a-descriptions-item>
          <a-descriptions-item label="SHA-256"
            ><span class="break-all font-mono text-xs">{{ detail.sha256 || '-' }}</span></a-descriptions-item
          >
          <a-descriptions-item label="摘要">{{ detail.summary || '-' }}</a-descriptions-item>
          <a-descriptions-item v-if="detail.errorMessage" label="错误信息">{{
            detail.errorMessage
          }}</a-descriptions-item>
          <a-descriptions-item label="更新时间">{{ formatTime(detail.updateTime) }}</a-descriptions-item>
        </a-descriptions>
      </a-spin>
    </a-drawer>
    <a-modal
      v-model:visible="batchCategoryVisible"
      title="批量归类业务文档"
      :ok-loading="batchCategoryLoading"
      @ok="applyBatchCategory"
    >
      <a-form :model="{ batchCategoryId }" layout="vertical">
        <a-form-item label="目标分类"
          ><a-tree-select
            v-model="batchCategoryId"
            allow-clear
            allow-search
            :data="categories"
            :field-names="{ key: 'id', title: 'name', children: 'children' }"
            placeholder="不选择则移动到未分类"
        /></a-form-item>
      </a-form>
      <a-alert>逐项校验资产权限；部分失败会单独提示。</a-alert>
    </a-modal>
  </TestAssetPage>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message, Modal } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import TestAssetPage from './components/TestAssetPage.vue';

  import type {
    TestAssetCategory,
    TestAssetCreationSource,
    TestAssetDocument,
    TestAssetMetadata,
  } from '@/api/modules/ai-execution';
  import {
    assignTestAssetCategory,
    batchAssignTestAssetCategory,
    getTestAssetMetadata,
    listTestAssetCategories,
    pageTestAssetDocuments,
  } from '@/api/modules/ai-execution';
  import {
    deleteAiSourceDocument,
    downloadAiSourceDocument,
    getAiSourceDocument,
    retryAiSourceDocument,
    uploadAiSourceDocument,
  } from '@/api/modules/case-management/caseGenerate';
  import useAppStore from '@/store/modules/app';
  import { downloadByteFile } from '@/utils';
  import { hasAnyPermission } from '@/utils/permission';

  import type { AiSourceDocument } from '@/models/caseManagement/caseGenerate';

  const appStore = useAppStore();
  const router = useRouter();
  const canAssignCategory = hasAnyPermission(['TEST_ASSET_CATEGORY:ASSIGN']);
  const fileInput = ref<HTMLInputElement>();
  const loading = ref(false);
  const uploading = ref(false);
  const detailVisible = ref(false);
  const detailLoading = ref(false);
  const detail = ref<AiSourceDocument>();
  const detailMetadata = ref<TestAssetMetadata>();
  const categories = ref<TestAssetCategory[]>([]);
  const documents = ref<TestAssetDocument[]>([]);
  const selectedDocumentIds = ref<string[]>([]);
  const batchCategoryVisible = ref(false);
  const batchCategoryLoading = ref(false);
  const batchCategoryId = ref<string>();
  const total = ref(0);
  const query = reactive({
    keyword: '',
    parseStatus: undefined as string | undefined,
    creationSources: [] as TestAssetCreationSource[],
    categoryId: undefined as string | undefined,
    includeDescendants: true,
    current: 1,
    pageSize: 20,
  });
  const sourceOptions: Array<{ value: TestAssetCreationSource; label: string; color: string }> = [
    { value: 'MANUAL', label: '人工建立', color: 'blue' },
    { value: 'AI', label: 'AI 建立', color: 'purple' },
    { value: 'IMPORT', label: '导入建立', color: 'cyan' },
    { value: 'SYNC', label: '同步建立', color: 'orange' },
    { value: 'AUTOMATION', label: '自动化建立', color: 'green' },
    { value: 'UNKNOWN', label: '来源不明', color: 'gray' },
  ];
  const sourceMeta = (value?: TestAssetCreationSource) =>
    sourceOptions.find((item) => item.value === value) || sourceOptions.at(-1)!;
  const pagination = computed(() => ({
    current: query.current,
    pageSize: query.pageSize,
    total: total.value,
    showTotal: true,
    showPageSize: true,
  }));
  const acceptedTypes =
    '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.html,.htm,.json,.xml,.yaml,.yml,.png,.jpg,.jpeg,.bmp,.gif,.webp';
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  const formatSize = (value?: number) => {
    if (value === undefined || value === null) return '-';
    if (value < 1024) return `${value} B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(2)} KB`;
    return `${(value / 1024 / 1024).toFixed(2)} MB`;
  };
  const statusText = (status: string) =>
    ({ UPLOADED: '待解析', PARSING: '解析中', PARSED: '解析成功', FAILED: '解析失败' }[status] || status);
  const statusColor = (status: string) =>
    ({ UPLOADED: 'gray', PARSING: 'blue', PARSED: 'green', FAILED: 'red' }[status] || 'gray');

  async function load() {
    if (!appStore.currentProjectId) return;
    loading.value = true;
    try {
      const result = await pageTestAssetDocuments({
        projectId: appStore.currentProjectId,
        ...query,
        keyword: query.keyword.trim() || undefined,
      });
      documents.value = result.list || [];
      total.value = result.total || 0;
    } finally {
      loading.value = false;
    }
  }
  async function changeDocumentCategory(document: TestAssetDocument, categoryId?: string) {
    const previous = document.categoryId;
    try {
      const metadata = await assignTestAssetCategory(appStore.currentProjectId, 'DOCUMENT', document.id, categoryId);
      document.categoryId = metadata.categoryId;
      document.categoryName = metadata.categoryName;
      document.categoryPath = metadata.categoryPath;
    } catch {
      document.categoryId = previous;
    }
  }
  async function applyBatchCategory() {
    batchCategoryLoading.value = true;
    try {
      const results = await batchAssignTestAssetCategory({
        categoryId: batchCategoryId.value,
        items: selectedDocumentIds.value.map((assetId) => ({
          projectId: appStore.currentProjectId,
          assetType: 'DOCUMENT',
          assetId,
        })),
      });
      const failed = results.filter((item) => !item.success).length;
      if (failed) Message.warning(`归类成功 ${results.length - failed} 项，失败 ${failed} 项`);
      else Message.success(`已归类 ${results.length} 项`);
      batchCategoryVisible.value = false;
      selectedDocumentIds.value = [];
      await load();
    } finally {
      batchCategoryLoading.value = false;
    }
  }
  function search() {
    query.current = 1;
    load();
  }
  function changePage(current: number) {
    query.current = current;
    load();
  }
  function changePageSize(pageSize: number) {
    query.pageSize = pageSize;
    query.current = 1;
    load();
  }
  function openAiGenerate(document?: TestAssetDocument) {
    router.push({
      path: '/case-management/caseGenerate',
      query: document ? { sourceDocumentId: document.id } : undefined,
    });
  }
  function openVersions(document: TestAssetDocument) {
    router.push({ path: '/test-assets/versions', query: { assetType: 'DOCUMENT', assetId: document.id } });
  }
  function openRelations(document: TestAssetDocument) {
    router.push({ path: '/test-assets/relations', query: { assetType: 'DOCUMENT', assetId: document.id } });
  }
  async function openDetail(document: TestAssetDocument) {
    if (!appStore.currentProjectId) return;
    detailVisible.value = true;
    detailLoading.value = true;
    try {
      [detail.value, detailMetadata.value] = await Promise.all([
        getAiSourceDocument(document.id, appStore.currentProjectId),
        getTestAssetMetadata(appStore.currentProjectId, 'DOCUMENT', document.id),
      ]);
    } finally {
      detailLoading.value = false;
    }
  }
  async function downloadDocument(document: TestAssetDocument) {
    if (!appStore.currentProjectId) return;
    const bytes = await downloadAiSourceDocument({ projectId: appStore.currentProjectId, id: document.id });
    downloadByteFile(bytes, document.originalName || `source-document-${document.id}`);
  }
  async function retryDocument(document: TestAssetDocument) {
    if (!appStore.currentProjectId) return;
    await retryAiSourceDocument({ projectId: appStore.currentProjectId, id: document.id });
    Message.success('已重新提交解析');
    await load();
  }
  function removeDocument(document: TestAssetDocument) {
    if (!appStore.currentProjectId) return;
    Modal.warning({
      title: '删除业务文档',
      content: `确认删除“${document.originalName}”吗？历史资产版本和追溯关系会保留。`,
      hideCancel: false,
      onOk: async () => {
        await deleteAiSourceDocument({ projectId: appStore.currentProjectId, id: document.id });
        Message.success('业务文档已删除');
        await load();
      },
    });
  }
  async function handleFileChange(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !appStore.currentProjectId) return;
    uploading.value = true;
    try {
      await uploadAiSourceDocument({ request: { projectId: appStore.currentProjectId }, file });
      Message.success('文档已上传，平台正在异步解析');
      await load();
    } finally {
      uploading.value = false;
    }
  }
  watch(
    () => appStore.currentProjectId,
    () => {
      query.current = 1;
      load();
    }
  );
  onMounted(async () => {
    categories.value = await listTestAssetCategories();
    await load();
  });
</script>

<style scoped lang="less">
  :deep(.asset-category-select .arco-select-view-single) {
    min-height: 30px;
    border: 1px solid transparent;
    background: transparent;
    cursor: pointer;
    &:hover,
    &.arco-select-view-focus {
      border-color: rgb(var(--primary-5));
      color: rgb(var(--primary-6));
      background: rgb(var(--primary-1));
    }
  }
</style>
