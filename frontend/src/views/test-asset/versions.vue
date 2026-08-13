<template>
  <TestAssetPage>
    <MsCard simple>
      <div class="mb-4">
        <div class="text-base font-medium">资产版本</div>
        <div class="mt-1 text-sm text-[var(--color-text-3)]">每一行都是不可变内容快照；任务引用固定版本，不随正式资产后续更新漂移。</div>
      </div>
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <a-input-search v-model="query.keyword" class="w-[260px]" allow-clear placeholder="搜索资产名称或 ID" @search="search" />
        <a-select v-model="query.assetType" class="w-[160px]" allow-clear placeholder="资产类型" @change="search">
          <a-option value="DOCUMENT">业务文档</a-option><a-option value="CASE">功能用例</a-option>
          <a-option value="TASK">测试任务</a-option><a-option value="PLAN">测试计划</a-option>
        </a-select>
        <a-input v-model="query.assetId" class="w-[260px]" allow-clear placeholder="精确资产 ID" @press-enter="search" />
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
      <a-table :data="versions" :loading="loading" row-key="id" :pagination="pagination" @page-change="changePage" @page-size-change="changePageSize">
        <template #empty><a-empty description="暂无符合条件的资产版本。" /></template>
        <a-table-column title="类型" :width="120"><template #cell="{ record }"><a-tag>{{ record.assetType }}</a-tag></template></a-table-column>
        <a-table-column title="资产" :width="330"><template #cell="{ record }"><div class="font-medium">{{ record.assetName || record.assetId }}</div><div class="mt-1 text-xs text-[var(--color-text-3)]">{{ record.assetId }}</div></template></a-table-column>
        <a-table-column title="版本" :width="100"><template #cell="{ record }">v{{ record.versionNo }}</template></a-table-column>
        <a-table-column title="来源版本" data-index="sourceVersion" :width="180" />
        <a-table-column title="状态" :width="120"><template #cell="{ record }"><a-tag color="green">{{ record.status }}</a-tag></template></a-table-column>
        <a-table-column title="内容摘要" :width="230"><template #cell="{ record }"><span class="font-mono text-xs">{{ shortHash(record.contentHash) }}</span></template></a-table-column>
        <a-table-column title="发布时间" :width="180"><template #cell="{ record }">{{ formatTime(record.publishedAt) }}</template></a-table-column>
        <a-table-column title="操作" :width="90" fixed="right"><template #cell="{ record }"><a-link @click="trace(record)">追溯</a-link></template></a-table-column>
      </a-table>
    </MsCard>
  </TestAssetPage>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import dayjs from 'dayjs';
  import { useRoute, useRouter } from 'vue-router';
  import TestAssetPage from './components/TestAssetPage.vue';
  import { pageTestAssetVersions } from '@/api/modules/ai-execution';
  import type { TestAssetVersion } from '@/api/modules/ai-execution';
  import useAppStore from '@/store/modules/app';

  const appStore = useAppStore(); const route = useRoute(); const router = useRouter();
  const loading = ref(false); const versions = ref<TestAssetVersion[]>([]); const total = ref(0);
  const query = reactive({ keyword: '', assetType: String(route.query.assetType || '') || undefined as string | undefined, assetId: String(route.query.assetId || ''), current: 1, pageSize: 20 });
  const pagination = computed(() => ({ current: query.current, pageSize: query.pageSize, total: total.value, showTotal: true, showPageSize: true }));
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  const shortHash = (value?: string) => (value ? `${value.slice(0, 12)}…${value.slice(-8)}` : '-');
  async function load() { if (!appStore.currentProjectId) return; loading.value = true; try { const result = await pageTestAssetVersions({ projectId: appStore.currentProjectId, ...query, keyword: query.keyword.trim() || undefined, assetId: query.assetId.trim() || undefined }); versions.value = result.list || []; total.value = result.total || 0; } finally { loading.value = false; } }
  function search() { query.current = 1; load(); } function changePage(current: number) { query.current = current; load(); } function changePageSize(pageSize: number) { query.pageSize = pageSize; query.current = 1; load(); }
  function trace(record: TestAssetVersion) { router.push({ path: '/test-assets/relations', query: { assetType: record.assetType, assetId: record.assetId } }); }
  watch(() => appStore.currentProjectId, () => { query.current = 1; load(); }); onMounted(load);
</script>
