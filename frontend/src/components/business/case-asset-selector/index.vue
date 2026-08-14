<template>
  <div class="selector-layout">
    <aside class="catalogs">
      <a-input-search
        v-model="catalogQuery.keyword"
        allow-clear
        placeholder="搜索用例项目"
        @search="loadCatalogs"
        @clear="loadCatalogs"
      />
      <a-spin :loading="catalogLoading" class="mt-2 block">
        <div
          v-for="item in catalogs"
          :key="item.id"
          class="catalog"
          :class="{ active: item.id === catalogId }"
          @click="selectCatalog(item.id)"
        >
          <div class="name">{{ item.name }}</div
          ><div class="id">{{ item.id }}</div>
        </div>
      </a-spin>
      <a-pagination
        simple
        :current="catalogQuery.current"
        :page-size="catalogQuery.pageSize"
        :total="catalogTotal"
        @change="changeCatalogPage"
      />
    </aside>
    <main class="min-w-0 flex-1">
      <div class="mb-2 flex items-center gap-2">
        <a-input-search
          v-model="caseQuery.keyword"
          class="w-[260px]"
          allow-clear
          placeholder="搜索用例 ID 或名称"
          @search="searchCases"
          @clear="searchCases"
        />
        <span class="text-sm text-[var(--color-text-3)]">已选 {{ selectedMap.size }} / {{ max }} 条</span>
      </div>
      <a-table
        v-model:selected-keys="pageSelectedKeys"
        row-key="id"
        :data="cases"
        :loading="caseLoading"
        :row-selection="rowSelection"
        :pagination="pagination"
        @selection-change="changeSelection"
        @page-change="changePage"
      >
        <template #columns>
          <a-table-column title="用例 ID" data-index="id" :width="190" ellipsis tooltip />
          <a-table-column title="用例名称" data-index="name" ellipsis tooltip />
          <a-table-column title="已引用项目" :width="120"
            ><template #cell="{ record }">{{ record.referencedProjectCount || 0 }}</template></a-table-column
          >
        </template>
      </a-table>
      <div v-if="selectedMap.size" class="selected-box">
        <a-tag v-for="item in selectedItems" :key="item.id" closable @close="remove(item.id)">{{ item.name }}</a-tag>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import type { CaseAssetCatalog } from '@/api/modules/case-management/featureCase';
  import {
    getCaseAssetList,
    getCaseAssetOptions,
    pageCaseAssetCatalogs,
  } from '@/api/modules/case-management/featureCase';

  import type { CaseManagementTable } from '@/models/caseManagement/featureCase';

  const props = withDefaults(
    defineProps<{
      selectedIds?: string[];
      max?: number;
      mode?: 'single' | 'multiple';
      targetProjectId?: string;
      scene?: string;
    }>(),
    { selectedIds: () => [], max: 500, mode: 'multiple', scene: 'PROJECT_IMPORT' }
  );
  const emit = defineEmits<{ (e: 'change', ids: string[], items: CaseManagementTable[]): void }>();
  const catalogs = ref<CaseAssetCatalog[]>([]);
  const catalogTotal = ref(0);
  const catalogId = ref('');
  const catalogLoading = ref(false);
  const cases = ref<CaseManagementTable[]>([]);
  const caseTotal = ref(0);
  const caseLoading = ref(false);
  const catalogQuery = reactive({ current: 1, pageSize: 10, keyword: '' });
  const caseQuery = reactive({ current: 1, pageSize: 10, keyword: '' });
  const selectedMap = reactive(new Map<string, CaseManagementTable>());
  const selectedItems = computed(() => Array.from(selectedMap.values()));
  const max = computed(() => (props.mode === 'single' ? 1 : props.max));
  const pageSelectedKeys = computed({
    get: () => cases.value.filter((item) => selectedMap.has(item.id)).map((item) => item.id),
    set: () => undefined,
  });
  const rowSelection = computed<any>(() => ({
    type: props.mode === 'single' ? 'radio' : 'checkbox',
    showCheckedAll: props.mode !== 'single',
  }));
  const pagination = computed(() => ({
    current: caseQuery.current,
    pageSize: caseQuery.pageSize,
    total: caseTotal.value,
    showTotal: true,
  }));
  async function loadCases() {
    if (!catalogId.value) {
      cases.value = [];
      caseTotal.value = 0;
      return;
    }
    caseLoading.value = true;
    try {
      const result = await getCaseAssetList({
        catalogId: catalogId.value,
        targetProjectId: props.targetProjectId,
        scene: props.scene,
        ...caseQuery,
        keyword: caseQuery.keyword.trim() || undefined,
      } as any);
      cases.value = result.list || [];
      caseTotal.value = result.total || 0;
      cases.value.forEach((item) => {
        if (props.selectedIds.includes(item.id) && !selectedMap.has(item.id)) selectedMap.set(item.id, item);
      });
    } finally {
      caseLoading.value = false;
    }
  }
  async function loadCatalogs() {
    catalogLoading.value = true;
    try {
      const result = await pageCaseAssetCatalogs({
        ...catalogQuery,
        keyword: catalogQuery.keyword.trim() || undefined,
      });
      catalogs.value = result.list || [];
      catalogTotal.value = result.total || 0;
      if (!catalogs.value.some((item) => item.id === catalogId.value)) catalogId.value = catalogs.value[0]?.id || '';
      await loadCases();
    } finally {
      catalogLoading.value = false;
    }
  }
  function emitChange() {
    emit('change', Array.from(selectedMap.keys()), selectedItems.value);
  }
  function changeSelection(keys: (string | number)[]) {
    const keySet = new Set(keys.map(String));
    let exceeded = false;
    cases.value.forEach((item) => {
      if (keySet.has(item.id)) {
        if (props.mode === 'single') selectedMap.clear();
        if (selectedMap.size < max.value || selectedMap.has(item.id)) selectedMap.set(item.id, item);
        else exceeded = true;
      } else selectedMap.delete(item.id);
    });
    if (exceeded) Message.warning(`最多选择 ${max.value} 条用例`);
    emitChange();
  }
  function remove(id: string) {
    selectedMap.delete(id);
    emitChange();
  }
  function selectCatalog(id: string) {
    catalogId.value = id;
    caseQuery.current = 1;
    loadCases();
  }
  function changeCatalogPage(current: number) {
    catalogQuery.current = current;
    loadCatalogs();
  }
  function searchCases() {
    caseQuery.current = 1;
    loadCases();
  }
  function changePage(current: number) {
    caseQuery.current = current;
    loadCases();
  }
  async function hydrateSelection(ids: string[]) {
    Array.from(selectedMap.keys()).forEach((id) => {
      if (!ids.includes(id)) selectedMap.delete(id);
    });
    const missing = ids.filter((id) => !selectedMap.has(id)).slice(0, max.value);
    if (missing.length) {
      const options = await getCaseAssetOptions({
        ids: missing,
        targetProjectId: props.targetProjectId,
        scene: props.scene,
      });
      options.forEach((item) => selectedMap.set(item.id, item));
    }
  }
  watch(() => props.selectedIds, hydrateSelection, { deep: true });
  onMounted(async () => {
    await hydrateSelection(props.selectedIds);
    await loadCatalogs();
  });
</script>

<style scoped lang="less">
  .selector-layout {
    display: flex;
    min-height: 400px;
    gap: 12px;
  }
  .catalogs {
    padding-right: 12px;
    width: 230px;
    border-right: 1px solid var(--color-neutral-3);
    flex: none;
  }
  .catalog {
    margin: 6px 0;
    padding: 8px;
    border-radius: 4px;
    cursor: pointer;
  }
  .catalog.active,
  .catalog:hover {
    background: rgb(var(--primary-1));
  }
  .catalog .name {
    font-weight: 700;
    color: #722ed1;
  }
  .catalog .id {
    font-size: 11px;
    color: var(--color-text-3);
  }
  .selected-box {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 8px;
    padding: 8px;
    border: 1px solid var(--color-neutral-3);
    border-radius: 4px;
  }
</style>
