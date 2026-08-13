<template>
  <TestAssetPage>
    <MsCard simple>
      <div class="mb-4"><div class="text-base font-medium">关联追溯</div><div class="mt-1 text-sm text-[var(--color-text-3)]">从业务文档、用例或任务任一端检索，查看来源与执行关系。</div></div>
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <a-input-search v-model="query.keyword" class="w-[240px]" allow-clear placeholder="搜索任一端名称或 ID" @search="search" />
        <a-select v-model="query.relationType" class="w-[170px]" allow-clear placeholder="关系类型" @change="search">
          <a-option value="DERIVED_FROM">来源于</a-option><a-option value="EXECUTES">执行</a-option><a-option value="COVERS">覆盖</a-option><a-option value="PRODUCES">产出</a-option><a-option value="REPLACES">替代</a-option>
        </a-select>
        <a-select v-model="query.assetType" class="w-[150px]" allow-clear placeholder="端点类型" @change="search"><a-option value="DOCUMENT">文档</a-option><a-option value="CASE">用例</a-option><a-option value="TASK">任务</a-option></a-select>
        <a-input v-model="query.assetId" class="w-[240px]" allow-clear placeholder="任一端资产 ID" @press-enter="search" />
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
      <a-table :data="relations" :loading="loading" row-key="id" :pagination="pagination" @page-change="changePage" @page-size-change="changePageSize">
        <template #empty><a-empty description="暂无符合条件的资产关系。" /></template>
        <a-table-column title="来源资产" :width="310"><template #cell="{ record }"><AssetEndpoint :type="record.sourceAssetType" :name="record.sourceAssetName" :id="record.sourceAssetId" :version="record.sourceVersionNo" /></template></a-table-column>
        <a-table-column title="关系" :width="150"><template #cell="{ record }"><a-tag color="arcoblue">{{ record.relationType }}</a-tag></template></a-table-column>
        <a-table-column title="目标资产" :width="310"><template #cell="{ record }"><AssetEndpoint :type="record.targetAssetType" :name="record.targetAssetName" :id="record.targetAssetId" :version="record.targetVersionNo" /></template></a-table-column>
        <a-table-column title="创建人" data-index="createdBy" :width="140" /><a-table-column title="创建时间" :width="180"><template #cell="{ record }">{{ formatTime(record.createdAt) }}</template></a-table-column>
      </a-table>
    </MsCard>
  </TestAssetPage>
</template>

<script setup lang="ts">
  import { computed, defineComponent, h, onMounted, reactive, ref, watch } from 'vue';
  import dayjs from 'dayjs'; import { useRoute } from 'vue-router';
  import TestAssetPage from './components/TestAssetPage.vue'; import { pageTestAssetRelations } from '@/api/modules/ai-execution'; import type { TestAssetRelation } from '@/api/modules/ai-execution'; import useAppStore from '@/store/modules/app';
  const AssetEndpoint = defineComponent({ props: { type: String, name: String, id: String, version: Number }, setup(props) { return () => h('div', [h('div', { class: 'flex items-center gap-2 font-medium' }, [h('span', props.name || props.id), props.version ? h('span', { class: 'text-xs text-[var(--color-text-3)]' }, `v${props.version}`) : null]), h('div', { class: 'mt-1 text-xs text-[var(--color-text-3)]' }, `${props.type || '-'} · ${props.id || '-'}`)]); } });
  const appStore = useAppStore(); const route = useRoute(); const loading = ref(false); const relations = ref<TestAssetRelation[]>([]); const total = ref(0);
  const query = reactive({ keyword: '', relationType: undefined as string | undefined, assetType: String(route.query.assetType || '') || undefined as string | undefined, assetId: String(route.query.assetId || ''), current: 1, pageSize: 20 });
  const pagination = computed(() => ({ current: query.current, pageSize: query.pageSize, total: total.value, showTotal: true, showPageSize: true })); const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  async function load() { if (!appStore.currentProjectId) return; loading.value = true; try { const result = await pageTestAssetRelations({ projectId: appStore.currentProjectId, ...query, keyword: query.keyword.trim() || undefined, assetId: query.assetId.trim() || undefined }); relations.value = result.list || []; total.value = result.total || 0; } finally { loading.value = false; } }
  function search() { query.current = 1; load(); } function changePage(current: number) { query.current = current; load(); } function changePageSize(pageSize: number) { query.pageSize = pageSize; query.current = 1; load(); }
  watch(() => appStore.currentProjectId, () => { query.current = 1; load(); }); onMounted(load);
</script>
