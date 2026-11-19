<template>
  <TestAssetPage>
    <MsCard simple>
      <a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert>
      <div class="mb-4">
        <div class="text-base font-medium">资产版本</div>
        <div class="mt-1 text-sm text-[var(--color-text-3)]"
          >每一行都是不可变内容快照；任务引用固定版本，不随正式资产后续更新漂移。</div
        >
      </div>
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <a-link
          v-if="query.assetType && catalogPaths[query.assetType]"
          @click="router.push(catalogPaths[query.assetType])"
        >
          查看此类资产目录
        </a-link>
        <a-input-search
          v-model="query.keyword"
          class="w-[260px]"
          allow-clear
          placeholder="搜索资产名称或 ID"
          @search="search"
        />
        <a-select v-model="query.assetType" class="w-[160px]" allow-clear placeholder="资产类型" @change="search">
          <a-option value="DOCUMENT">业务文档</a-option><a-option value="CASE">功能用例</a-option>
          <a-option value="DATASET">测试数据</a-option><a-option value="ENVIRONMENT">测试环境</a-option>
          <a-option value="COMMON_STEP">公共步骤</a-option><a-option value="API_DEFINITION">接口资产</a-option>
          <a-option value="EVIDENCE">执行证据</a-option><a-option value="BUG">缺陷资产</a-option>
          <a-option value="TASK">测试任务</a-option><a-option value="STEP">执行步骤</a-option>
        </a-select>
        <a-input
          v-model="query.assetId"
          class="w-[260px]"
          allow-clear
          placeholder="精确资产 ID"
          @press-enter="search"
        />
        <a-select
          v-model="query.creationSources"
          multiple
          allow-clear
          class="w-[190px]"
          placeholder="建立方式"
          @change="search"
        >
          <a-option v-for="item in sourceOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
        </a-select>
        <a-tree-select
          v-model="query.categoryId"
          allow-clear
          allow-search
          class="w-[190px]"
          :data="categories"
          :field-names="{ key: 'id', title: 'name', children: 'children' }"
          placeholder="资产分类"
          @change="search"
        />
        <a-checkbox v-model="query.includeDescendants" @change="search">含子分类</a-checkbox>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
      <a-alert v-if="comparisonBase" class="mb-3">
        比较基准为 {{ comparisonBase.assetName || comparisonBase.assetId }} v{{
          comparisonBase.versionNo
        }}。请选择同一文档的另一版本。
        <a-button type="text" @click="comparisonBase = undefined">清除基准</a-button>
      </a-alert>
      <a-table
        :data="versions"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
        @page-change="changePage"
        @page-size-change="changePageSize"
      >
        <template #empty><a-empty description="暂无符合条件的资产版本。" /></template>
        <template #columns>
          <a-table-column title="类型" :width="120"
            ><template #cell="{ record }"
              ><a-tag>{{ record.assetType }}</a-tag></template
            ></a-table-column
          >
          <a-table-column title="资产" :width="330"
            ><template #cell="{ record }"
              ><div class="font-medium">{{ record.assetName || record.assetId }}</div
              ><div class="mt-1 text-xs text-[var(--color-text-3)]">{{ record.assetId }}</div></template
            ></a-table-column
          >
          <a-table-column title="版本" :width="100"
            ><template #cell="{ record }">v{{ record.versionNo }}</template></a-table-column
          >
          <a-table-column title="建立方式" :width="120"
            ><template #cell="{ record }">
              <a-tag :color="sourceMeta(record.creationSource).color">{{
                sourceMeta(record.creationSource).label
              }}</a-tag>
            </template></a-table-column
          >
          <a-table-column title="资产分类" data-index="categoryPath" :width="210" ellipsis tooltip />
          <a-table-column title="来源版本" data-index="sourceVersion" :width="180" />
          <a-table-column title="状态" :width="120"
            ><template #cell="{ record }"
              ><a-tag color="green">{{ record.status }}</a-tag></template
            ></a-table-column
          >
          <a-table-column title="内容摘要" :width="230"
            ><template #cell="{ record }"
              ><span class="font-mono text-xs">{{ shortHash(record.contentHash) }}</span></template
            ></a-table-column
          >
          <a-table-column title="发布时间" :width="180"
            ><template #cell="{ record }">{{ formatTime(record.publishedAt) }}</template></a-table-column
          >
          <a-table-column title="操作" :width="250" fixed="right"
            ><template #cell="{ record }"
              ><a-space
                ><a-link @click="trace(record)">追溯</a-link
                ><a-link v-if="record.assetType === 'DOCUMENT'" @click="selectComparison(record)">
                  {{
                    comparisonBase?.assetId === record.assetId && comparisonBase?.id !== record.id ? '对比' : '设为基准'
                  }} </a-link
                ><a-popconfirm
                  v-if="record.status === 'PUBLISHED'"
                  content="停用后新任务不能再选择该版本，已冻结任务不受影响。"
                  @ok="deprecate(record)"
                  ><a-link v-permission="['AI_EXECUTION:RUN']" status="danger">停用</a-link></a-popconfirm
                ></a-space
              ></template
            ></a-table-column
          >
        </template>
      </a-table>
    </MsCard>
    <a-modal v-model:visible="comparisonVisible" title="文档版本对比" :width="960" :footer="false" unmount-on-close>
      <a-alert v-if="comparisonError" type="error">{{ comparisonError }}</a-alert>
      <a-spin v-else :loading="comparisonLoading" class="block">
        <div v-if="markdownComparison" class="h-[55vh]">
          <MsCodeEditor
            :title="`v${markdownComparison[0].versionNo} → v${markdownComparison[1].versionNo}`"
            :original-value="markdownComparison[0].body"
            :model-value="markdownComparison[1].body"
            language="PLAINTEXT"
            read-only
            diff-mode
            :show-language-change="false"
            :show-theme-change="false"
            :show-full-screen="false"
          />
        </div>
        <div v-else-if="comparisonContents" class="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <section v-for="entry in comparisonContents" :key="entry.id" class="min-w-0">
            <h3 class="mb-2 font-medium">v{{ entry.versionNo }}</h3>
            <ContentReader :value="entry.content" />
          </section>
        </div>
      </a-spin>
    </a-modal>
  </TestAssetPage>
</template>

<script setup lang="ts">
  import { computed, defineAsyncComponent, onMounted, reactive, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import dayjs from 'dayjs';

  import TestAssetPage from './components/TestAssetPage.vue';
  import ContentReader from './knowledge/ContentReader.vue';

  import type { TestAssetCategory, TestAssetCreationSource, TestAssetVersion } from '@/api/modules/ai-execution';
  import {
    deprecateTestAssetVersion,
    listTestAssetCategories,
    pageTestAssetVersions,
  } from '@/api/modules/ai-execution';
  import { previewSource } from '@/api/modules/governed-execution';
  import useAppStore from '@/store/modules/app';

  const MsCodeEditor = defineAsyncComponent(() => import('@/components/pure/ms-code-editor/index.vue'));
  const appStore = useAppStore();
  const route = useRoute();
  const router = useRouter();
  const loading = ref(false);
  const error = ref('');
  const versions = ref<TestAssetVersion[]>([]);
  const categories = ref<TestAssetCategory[]>([]);
  const total = ref(0);
  const comparisonBase = ref<TestAssetVersion>();
  const comparisonVisible = ref(false);
  const comparisonLoading = ref(false);
  const comparisonError = ref('');
  const comparisonContents = ref<Array<{ id: string; versionNo: number; content: unknown }>>();
  const markdownComparison = computed(() => {
    const entries = comparisonContents.value?.map(({ content, versionNo }) => ({
      versionNo,
      body: (content as { bodyMarkdown?: unknown } | undefined)?.bodyMarkdown,
    }));
    const left = entries?.[0];
    const right = entries?.[1];
    if (typeof left?.body !== 'string' || typeof right?.body !== 'string') return undefined;
    return [
      { versionNo: left.versionNo, body: left.body },
      { versionNo: right.versionNo, body: right.body },
    ] as const;
  });
  let comparisonRequest = 0;

  const catalogPaths: Record<string, string> = {
    DOCUMENT: '/test-assets/documents',
    CASE: '/test-assets/cases/project',
    DATASET: '/test-assets/datasets',
    ENVIRONMENT: '/test-assets/environments',
    COMMON_STEP: '/test-assets/common-steps',
    API_DEFINITION: '/test-assets/apis',
    EVIDENCE: '/test-assets/evidence',
    BUG: '/test-assets/bugs',
  };
  const query = reactive({
    keyword: '',
    assetType: String(route.query.assetType || '') || (undefined as string | undefined),
    assetId: String(route.query.assetId || ''),
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
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  const shortHash = (value?: string) => (value ? `${value.slice(0, 12)}…${value.slice(-8)}` : '-');
  async function load() {
    if (!appStore.currentProjectId) return;
    loading.value = true;
    error.value = '';
    try {
      const result = await pageTestAssetVersions({
        projectId: appStore.currentProjectId,
        ...query,
        keyword: query.keyword.trim() || undefined,
        assetId: query.assetId.trim() || undefined,
      });
      versions.value = result.list || [];
      total.value = result.total || 0;
    } catch (reason: any) {
      error.value = reason?.message || '资产版本加载失败，请稍后重试';
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
    query.pageSize = pageSize;
    query.current = 1;
    load();
  }
  async function selectComparison(record: TestAssetVersion) {
    const base = comparisonBase.value;
    if (!base || base.assetId !== record.assetId || base.id === record.id) {
      comparisonBase.value = record;
      return;
    }
    const projectId = appStore.currentProjectId;
    const request = ++comparisonRequest;
    comparisonContents.value = undefined;
    comparisonError.value = '';
    comparisonVisible.value = true;
    comparisonLoading.value = true;
    try {
      const results = await Promise.all(
        [base, record].map(async (entry) => {
          const preview = await previewSource(projectId, entry.assetId, entry.id);
          return { id: entry.id, versionNo: entry.versionNo, content: preview.content };
        })
      );
      if (request === comparisonRequest && projectId === appStore.currentProjectId) {
        comparisonContents.value = results;
      }
    } catch {
      if (request === comparisonRequest) {
        comparisonError.value = '无法读取所选版本，请检查访问权限后重试。';
      }
    } finally {
      if (request === comparisonRequest) comparisonLoading.value = false;
    }
  }

  function trace(record: TestAssetVersion) {
    router.push({ path: '/test-assets/relations', query: { assetType: record.assetType, assetId: record.assetId } });
  }
  async function deprecate(record: TestAssetVersion) {
    if (!appStore.currentProjectId) return;
    try {
      await deprecateTestAssetVersion(appStore.currentProjectId, record);
      await load();
    } catch (reason: any) {
      error.value = reason?.message || '资产版本停用失败，请稍后重试';
    }
  }
  watch(comparisonVisible, (visible) => {
    if (!visible) {
      comparisonRequest++;
      comparisonLoading.value = false;
      comparisonContents.value = undefined;
    }
  });
  watch(
    () => [appStore.currentOrgId, appStore.currentProjectId],
    () => {
      comparisonRequest++;
      comparisonBase.value = undefined;
      comparisonVisible.value = false;
      comparisonContents.value = undefined;
      query.current = 1;
      load();
    }
  );
  onMounted(async () => {
    categories.value = await listTestAssetCategories();
    await load();
  });
</script>
