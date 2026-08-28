<template>
  <TestAssetPage>
    <MsCard simple>
      <a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert>
      <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <div class="text-base font-medium">{{ config.title }}</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]">{{ config.description }}</div>
        </div>
        <div class="flex flex-wrap gap-2">
          <a-input-search
            v-model="query.keyword"
            class="w-[260px]"
            allow-clear
            placeholder="搜索名称、ID 或摘要"
            @search="search"
            @clear="search"
          />
          <a-input v-model="query.status" class="w-[150px]" allow-clear placeholder="状态" @press-enter="search" />
          <a-button :loading="loading" @click="load">刷新</a-button>
          <a-button @click="openSource">打开原业务模块</a-button>
        </div>
      </div>
      <a-table
        :data="records"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
        @page-change="changePage"
        @page-size-change="changePageSize"
      >
        <template #empty><a-empty :description="`暂无${config.title}`" /></template>
        <template #columns>
          <a-table-column title="名称" data-index="name" :width="240" ellipsis tooltip />
          <a-table-column title="类型" data-index="category" :width="140" />
          <a-table-column title="状态" :width="120"
            ><template #cell="{ record }"
              ><a-tag>{{ record.status || '-' }}</a-tag></template
            ></a-table-column
          >
          <a-table-column title="摘要" data-index="summary" :width="300" ellipsis tooltip />
          <a-table-column title="资产版本" :width="110"
            ><template #cell="{ record }"
              ><a-link v-if="record.assetVersionId" @click="openVersions(record)">v{{ record.assetVersionNo }}</a-link
              ><a-tag v-else color="orange">未发布</a-tag></template
            ></a-table-column
          >
          <a-table-column title="负责人" data-index="owner" :width="130" />
          <a-table-column title="更新时间" :width="170"
            ><template #cell="{ record }">{{ formatTime(record.updateTime) }}</template></a-table-column
          >
          <a-table-column title="操作" :width="230" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-link @click="openDetail(record)">详情</a-link>
                <a-link @click="openRelations(record)">追溯</a-link>
                <a-link v-if="assetType === 'EVIDENCE'" @click="previewEvidence(record)">预览</a-link>
                <a-link v-if="assetType === 'EVIDENCE'" @click="downloadEvidence(record)">下载</a-link>
                <a-link v-permission="['AI_EXECUTION:RUN']" @click="publishAsset(record)">发布执行版本</a-link>
                <a-link
                  v-permission="['AI_EXECUTION:RUN']"
                  :disabled="!record.assetVersionId"
                  @click="useInTask(record)"
                  >用于任务</a-link
                >
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </MsCard>
    <a-drawer v-model:visible="detailVisible" :title="`${config.title}详情`" :width="560" unmount-on-close>
      <a-descriptions v-if="detail" :column="1" bordered>
        <a-descriptions-item label="名称">{{ detail.name }}</a-descriptions-item>
        <a-descriptions-item label="资产 ID">{{ detail.id }}</a-descriptions-item>
        <a-descriptions-item label="类型">{{ detail.assetType }} / {{ detail.category || '-' }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ detail.status || '-' }}</a-descriptions-item>
        <a-descriptions-item label="版本">v{{ detail.assetVersionNo || 1 }}</a-descriptions-item>
        <a-descriptions-item label="内容哈希"
          ><span class="break-all font-mono text-xs">{{ detail.contentHash || '-' }}</span></a-descriptions-item
        >
        <a-descriptions-item label="摘要">{{ detail.summary || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-drawer>
    <a-modal
      v-model:visible="previewVisible"
      title="执行证据预览"
      :footer="false"
      unmount-on-close
      @close="clearPreview"
    >
      <a-image v-if="previewUrl" :src="previewUrl" width="100%" fit="contain" />
      <pre
        v-else-if="previewText"
        class="max-h-[60vh] overflow-auto whitespace-pre-wrap break-all rounded bg-[var(--color-fill-2)] p-3 text-xs"
        >{{ previewText }}</pre
      >
      <a-empty v-else description="该证据无法安全预览，请下载后查看" />
    </a-modal>
  </TestAssetPage>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import dayjs from 'dayjs';

  import MsCard from '@/components/pure/ms-card/index.vue';
  import TestAssetPage from './components/TestAssetPage.vue';

  import type { TestAssetCatalogItem, TestAssetCatalogType } from '@/api/modules/ai-execution';
  import {
    downloadAiExecutionArtifact,
    getTestAssetCatalogDetail,
    pageTestAssetCatalog,
    publishTestAssetCatalog,
  } from '@/api/modules/ai-execution';
  import useAppStore from '@/store/modules/app';
  import { downloadByteFile } from '@/utils';

  import { CaseManagementRouteEnum } from '@/enums/routeEnum';

  const route = useRoute();
  const router = useRouter();
  const appStore = useAppStore();
  const configs: Record<string, { type: TestAssetCatalogType; title: string; description: string; source: string }> = {
    'datasets': {
      type: 'DATASET',
      title: '测试数据',
      description: '项目 CSV、JSON 和表格测试数据的统一版本与任务引用。',
      source: '/project-management/fileManagement',
    },
    'environments': {
      type: 'ENVIRONMENT',
      title: '测试环境',
      description: '环境元数据的脱敏资产版本；密钥不会进入任务快照。',
      source: '/project-management/environmentManagement',
    },
    'common-steps': {
      type: 'COMMON_STEP',
      title: '公共步骤',
      description: '公共脚本、前后置处理及断言能力的统一资产目录。',
      source: '/project-management/commonScript',
    },
    'apis': {
      type: 'API_DEFINITION',
      title: '接口资产',
      description: '项目接口定义最新版本及其任务引用关系。',
      source: '/api-test/management',
    },
    'evidence': {
      type: 'EVIDENCE',
      title: '执行证据',
      description: 'Agent/Runner 回传的截图、HAR 和附件证据。',
      source: '/case-management/automationExecution',
    },
    'bugs': {
      type: 'BUG',
      title: '缺陷资产',
      description: '缺陷及其与用例、任务和证据的追溯入口。',
      source: '/bug-management/index',
    },
  };
  const segment = computed(() => route.path.split('/').filter(Boolean).at(-1) || 'datasets');
  const config = computed(() => configs[segment.value] || configs.datasets);
  const assetType = computed(() => config.value.type);
  const loading = ref(false);
  const records = ref<TestAssetCatalogItem[]>([]);
  const error = ref('');
  const total = ref(0);
  const detailVisible = ref(false);
  const detail = ref<TestAssetCatalogItem>();
  const previewVisible = ref(false);
  const previewUrl = ref('');
  const previewText = ref('');
  const query = reactive({ keyword: '', status: '', current: 1, pageSize: 20 });
  const pagination = computed(() => ({
    current: query.current,
    pageSize: query.pageSize,
    total: total.value,
    showTotal: true,
    showPageSize: true,
  }));
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');

  async function load() {
    if (!appStore.currentProjectId) return;
    loading.value = true;
    error.value = '';
    try {
      const result = await pageTestAssetCatalog({
        projectId: appStore.currentProjectId,
        assetType: assetType.value,
        keyword: query.keyword.trim() || undefined,
        status: query.status.trim() || undefined,
        current: query.current,
        pageSize: query.pageSize,
      });
      records.value = result.list || [];
      total.value = result.total || 0;
    } catch (reason: any) {
      error.value = reason?.message || `${config.value.title}加载失败，请稍后重试`;
    } finally {
      loading.value = false;
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
    query.current = 1;
    query.pageSize = pageSize;
    load();
  }
  async function openDetail(record: TestAssetCatalogItem) {
    if (!appStore.currentProjectId) return;
    detailVisible.value = true;
    detail.value = await getTestAssetCatalogDetail(appStore.currentProjectId, record.assetType, record.id);
  }
  function openVersions(record: TestAssetCatalogItem) {
    router.push({ path: '/test-assets/versions', query: { assetType: record.assetType, assetId: record.id } });
  }
  function openRelations(record: TestAssetCatalogItem) {
    router.push({ path: '/test-assets/relations', query: { assetType: record.assetType, assetId: record.id } });
  }
  function openSource() {
    router.push(config.value.source);
  }
  function useInTask(record: TestAssetCatalogItem) {
    if (!record.assetVersionId) return;
    router.push({
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_AUTOMATION_EXECUTION,
      query: {
        creating: '1',
        assetType: record.assetType,
        assetId: record.id,
        assetName: record.name,
        assetVersionId: record.assetVersionId,
      },
    });
  }
  async function publishAsset(record: TestAssetCatalogItem) {
    if (!appStore.currentProjectId) return;
    await publishTestAssetCatalog(appStore.currentProjectId, record.assetType, record.id);
    await load();
  }
  async function downloadEvidence(record: TestAssetCatalogItem) {
    if (!record.relatedId) return;
    const bytes = await downloadAiExecutionArtifact(record.relatedId, record.id);
    downloadByteFile(bytes, record.name || `evidence-${record.id}`);
  }
  function clearPreview() {
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = '';
    previewText.value = '';
  }
  async function previewEvidence(record: TestAssetCatalogItem) {
    if (!record.relatedId) return;
    clearPreview();
    const bytes = await downloadAiExecutionArtifact(record.relatedId, record.id);
    const blob = bytes instanceof Blob ? bytes : new Blob([bytes]);
    const textEvidence =
      /\.(txt|log|json|xml|csv)$/i.test(record.name || '') ||
      ['text/plain', 'text/csv', 'application/json', 'application/xml', 'text/xml'].includes(blob.type);
    if (textEvidence) previewText.value = (await blob.text()).slice(0, 1_000_000);
    else previewUrl.value = URL.createObjectURL(blob);
    previewVisible.value = true;
  }
  watch(
    () => [segment.value, appStore.currentProjectId],
    () => {
      query.current = 1;
      load();
    }
  );
  onMounted(load);
  onBeforeUnmount(clearPreview);
</script>
