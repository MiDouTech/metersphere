<template>
  <TestAssetPage>
    <MsCard simple>
      <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <div class="text-base font-medium">业务文档</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]">项目内已授权的需求与业务资料；解析成功后自动形成不可变资产版本。</div>
        </div>
        <div class="flex gap-2">
          <input ref="fileInput" class="hidden" type="file" :accept="acceptedTypes" @change="handleFileChange" />
          <a-button v-permission="['FUNCTIONAL_CASE_AI:UPLOAD']" :loading="uploading" @click="fileInput?.click()">上传文档</a-button>
          <a-button v-permission="['FUNCTIONAL_CASE_AI:GENERATE']" type="primary" @click="openAiGenerate()">AI 生成用例</a-button>
        </div>
      </div>
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <a-input-search v-model="query.keyword" class="w-[280px]" allow-clear placeholder="搜索名称、摘要或 ID" @search="search" />
        <a-select v-model="query.parseStatus" class="w-[180px]" allow-clear placeholder="解析状态" @change="search">
          <a-option value="UPLOADED">待解析</a-option><a-option value="PARSING">解析中</a-option>
          <a-option value="PARSED">解析成功</a-option><a-option value="FAILED">解析失败</a-option>
        </a-select>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
      <a-table :data="documents" :loading="loading" row-key="id" :pagination="pagination" @page-change="changePage" @page-size-change="changePageSize">
        <template #empty><a-empty description="暂无业务文档，可上传 Markdown、Word、PDF、网页文件或图片。" /></template>
        <a-table-column title="文档" :width="330">
          <template #cell="{ record }">
            <div class="font-medium">{{ record.originalName }}</div>
            <div class="mt-1 truncate text-xs text-[var(--color-text-3)]">{{ record.summary || record.id }}</div>
          </template>
        </a-table-column>
        <a-table-column title="解析状态" :width="120">
          <template #cell="{ record }"><a-tag :color="statusColor(record.parseStatus)">{{ statusText(record.parseStatus) }}</a-tag></template>
        </a-table-column>
        <a-table-column title="资产版本" :width="120">
          <template #cell="{ record }"><a-link v-if="record.assetVersionNo" @click="openVersions(record)">v{{ record.assetVersionNo }}</a-link><span v-else>-</span></template>
        </a-table-column>
        <a-table-column title="解析器" data-index="parserType" :width="180" />
        <a-table-column title="所有者" data-index="createUser" :width="140" />
        <a-table-column title="更新时间" :width="180"><template #cell="{ record }">{{ formatTime(record.updateTime) }}</template></a-table-column>
        <a-table-column title="操作" :width="210" fixed="right">
          <template #cell="{ record }">
            <a-space>
              <a-link v-permission="['FUNCTIONAL_CASE_AI:GENERATE']" :disabled="record.parseStatus !== 'PARSED'" @click="openAiGenerate(record)">生成用例</a-link>
              <a-link :disabled="!record.assetVersionNo" @click="openRelations(record)">追溯</a-link>
            </a-space>
          </template>
        </a-table-column>
      </a-table>
    </MsCard>
  </TestAssetPage>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import dayjs from 'dayjs';
  import { useRouter } from 'vue-router';

  import TestAssetPage from './components/TestAssetPage.vue';
  import { pageTestAssetDocuments } from '@/api/modules/ai-execution';
  import type { TestAssetDocument } from '@/api/modules/ai-execution';
  import { uploadAiSourceDocument } from '@/api/modules/case-management/caseGenerate';
  import useAppStore from '@/store/modules/app';

  const appStore = useAppStore();
  const router = useRouter();
  const fileInput = ref<HTMLInputElement>();
  const loading = ref(false);
  const uploading = ref(false);
  const documents = ref<TestAssetDocument[]>([]);
  const total = ref(0);
  const query = reactive({ keyword: '', parseStatus: undefined as string | undefined, current: 1, pageSize: 20 });
  const pagination = computed(() => ({ current: query.current, pageSize: query.pageSize, total: total.value, showTotal: true, showPageSize: true }));
  const acceptedTypes = '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.html,.htm,.json,.xml,.yaml,.yml,.png,.jpg,.jpeg,.bmp,.gif,.webp';
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  const statusText = (status: string) => ({ UPLOADED: '待解析', PARSING: '解析中', PARSED: '解析成功', FAILED: '解析失败' }[status] || status);
  const statusColor = (status: string) => ({ UPLOADED: 'gray', PARSING: 'blue', PARSED: 'green', FAILED: 'red' }[status] || 'gray');

  async function load() {
    if (!appStore.currentProjectId) return;
    loading.value = true;
    try {
      const result = await pageTestAssetDocuments({ projectId: appStore.currentProjectId, ...query, keyword: query.keyword.trim() || undefined });
      documents.value = result.list || [];
      total.value = result.total || 0;
    } finally { loading.value = false; }
  }
  function search() { query.current = 1; load(); }
  function changePage(current: number) { query.current = current; load(); }
  function changePageSize(pageSize: number) { query.pageSize = pageSize; query.current = 1; load(); }
  function openAiGenerate(document?: TestAssetDocument) {
    router.push({ path: '/case-management/caseGenerate', query: document ? { sourceDocumentId: document.id } : undefined });
  }
  function openVersions(document: TestAssetDocument) { router.push({ path: '/test-assets/versions', query: { assetType: 'DOCUMENT', assetId: document.id } }); }
  function openRelations(document: TestAssetDocument) { router.push({ path: '/test-assets/relations', query: { assetType: 'DOCUMENT', assetId: document.id } }); }
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
    } finally { uploading.value = false; }
  }
  watch(() => appStore.currentProjectId, () => { query.current = 1; load(); });
  onMounted(load);
</script>
