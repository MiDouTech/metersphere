<template>
  <MsCard auto-width simple :min-width="0">
    <div class="mb-4">
      <h2 class="text-base font-medium">系统目录</h2>
      <p class="mt-1 text-sm text-[var(--color-text-3)]"
        >按业务系统和模块整理同一份用例资产；分类不会复制用例或改变项目目录。</p
      >
    </div>
    <a-alert v-if="failed" type="error" class="mb-3">读取失败，请点击刷新重试。</a-alert>
    <div class="system-assets-layout">
      <aside aria-label="业务系统和模块" class="system-directory">
        <a-space wrap class="mb-3">
          <a-button v-if="canAdd" type="primary" @click="openEditor('system')">新建系统</a-button>
          <a-button v-if="canAdd" :disabled="!selectedSystem" @click="openEditor('module')">新建模块</a-button>
          <a-button :loading="treeLoading" @click="refresh">刷新</a-button>
        </a-space>
        <a-space v-if="selectedSystem" wrap class="mb-3">
          <a-button v-if="canUpdate" size="small" @click="openEditor(selectedModule ? 'module' : 'system', true)"
            >重命名</a-button
          >
          <a-button v-if="canDelete" size="small" status="danger" @click="removeDirectory"
            >删除{{ selectedModule ? '模块' : '系统' }}</a-button
          >
        </a-space>
        <a-spin :loading="treeLoading" class="block">
          <a-tree :data="tree" :selected-keys="[selectedKey]" block-node @select="selectDirectory" />
        </a-spin>
      </aside>
      <section aria-label="系统用例列表" class="min-w-0 flex-1">
        <h3 class="mb-3 font-medium">{{ selectedTitle }}</h3>
        <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
          <a-button
            v-if="canUpdate"
            type="primary"
            :disabled="!selectedIds.length"
            @click="openClassification(selectedIds)"
            >批量分类 / 移动</a-button
          >
          <a-input-search
            v-model="keyword"
            :input-attrs="{ 'aria-label': '搜索系统目录用例' }"
            placeholder="搜索用例 ID 或名称"
            class="max-w-[320px]"
            allow-clear
            @search="search"
            @clear="search"
          />
        </div>
        <a-table
          :data="rows"
          :loading="loading"
          :pagination="pagination"
          row-key="id"
          :row-selection="{ type: 'checkbox', showCheckedAll: true }"
          :selected-keys="selectedIds"
          :scroll="{ x: 720 }"
          @selection-change="selectRows"
          @page-change="changePage"
        >
          <template #columns>
            <a-table-column title="用例名称" :width="260"
              ><template #cell="{ record }"
                ><a-link @click="openCase(record)">{{ record.name }}</a-link></template
              ></a-table-column
            >
            <a-table-column title="项目目录" data-index="assetCatalogName" :width="180" />
            <a-table-column title="ID" data-index="num" :width="110" />
            <a-table-column title="操作" :width="140"
              ><template #cell="{ record }"
                ><a-link v-if="canUpdate" @click="openClassification([record.id])">分类 / 移动</a-link></template
              ></a-table-column
            >
          </template>
        </a-table>
      </section>
    </div>
    <a-modal v-model:visible="editorVisible" :title="editorTitle" :on-before-ok="saveDirectory" unmount-on-close>
      <a-form :model="editor" layout="vertical">
        <a-form-item label="名称" field="name" :label-attrs="{ for: 'asset-system-name' }" required
          ><a-input v-model="editor.name" :input-attrs="{ id: 'asset-system-name' }" :max-length="255"
        /></a-form-item>
        <a-alert v-if="editor.kind === 'module' && !editor.id">{{
          selectedModule ? `在“${selectedModule.name}”下新建子模块` : '在当前系统下新建模块'
        }}</a-alert>
      </a-form>
    </a-modal>
    <a-modal
      v-model:visible="classificationVisible"
      title="用例系统分类"
      :on-before-ok="saveClassification"
      unmount-on-close
    >
      <a-form :model="classification" layout="vertical">
        <a-form-item label="业务系统" field="systemId" :label-attrs="{ for: 'asset-classification-system' }">
          <select
            id="asset-classification-system"
            v-model="classification.systemId"
            class="classification-select"
            @change="classification.moduleId = undefined"
          >
            <option :value="undefined">解除系统归属</option>
            <option v-for="system in systems" :key="system.id" :value="system.id">{{ system.name }}</option>
          </select>
        </a-form-item>
        <a-form-item label="系统模块" field="moduleId" :label-attrs="{ for: 'asset-classification-module' }">
          <select
            id="asset-classification-module"
            v-model="classification.moduleId"
            :disabled="!classification.systemId"
            class="classification-select"
          >
            <option :value="undefined">不选择模块</option>
            <option v-for="module in classificationModules" :key="module.id" :value="module.id">{{
              modulePath(module)
            }}</option>
          </select>
        </a-form-item>
        <a-alert>{{
          classification.systemId
            ? '仅变更系统分类，保留项目目录和用例 ID。'
            : '将解除所选用例的系统归属，用例仍保留在项目目录和待人工分类中。'
        }}</a-alert>
      </a-form>
    </a-modal>
  </MsCard>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { Message, Modal } from '@arco-design/web-vue';

  import MsCard from '@/components/pure/ms-card/index.vue';

  import MSR from '@/api/http/index';
  import {
    type AssetSystem,
    type AssetSystemModule,
    classifyAssetCases,
    deleteAssetSystem,
    deleteAssetSystemModule,
    getAssetSystems,
    saveAssetSystem,
    saveAssetSystemModule,
  } from '@/api/modules/case-management/assetSystems';
  import useAppStore from '@/store/modules/app';
  import { hasAnyPermission } from '@/utils/permission';

  import { TestAssetRouteEnum } from '@/enums/routeEnum';

  interface AssetRow {
    id: string;
    name: string;
    num: number;
    assetCatalogId: string;
    assetCatalogName: string;
  }
  interface DirectoryNode {
    key: string;
    title: string;
    children?: DirectoryNode[];
  }
  const app = useAppStore();
  const router = useRouter();
  const route = useRoute();
  const canAdd = hasAnyPermission(['CASE_ASSET:READ+ADD']);
  const canUpdate = hasAnyPermission(['CASE_ASSET:READ+UPDATE']);
  const canDelete = hasAnyPermission(['CASE_ASSET:READ+DELETE']);
  const systems = ref<AssetSystem[]>([]);
  const unclassifiedCount = ref(0);
  const selectedKey = ref('__all__');
  const rows = ref<AssetRow[]>([]);
  const selectedIds = ref<string[]>([]);
  const keyword = ref('');
  const current = ref(1);
  const total = ref(0);
  const loading = ref(false);
  const treeLoading = ref(false);
  const failed = ref(false);
  let sequence = 0;
  let active = true;
  let treeSequence = 0;
  const selectedSystem = computed(() =>
    systems.value.find(
      (system) => system.id === selectedKey.value || system.modules.some((module) => module.id === selectedKey.value)
    )
  );
  const selectedModule = computed(() =>
    selectedSystem.value?.modules.find((module) => module.id === selectedKey.value)
  );
  const selectedTitle = computed(
    () =>
      selectedModule.value?.name ||
      selectedSystem.value?.name ||
      (selectedKey.value === '__unclassified__' ? '待人工分类' : '全部系统用例')
  );
  const pagination = computed(() => ({ current: current.value, pageSize: 20, total: total.value }));
  const tree = computed<DirectoryNode[]>(() => {
    const systemNodes = systems.value.map((system) => {
      const nodes = new Map(
        system.modules.map((module) => [
          module.id,
          { key: module.id, title: `${module.name} (${module.caseCount})`, children: [] as DirectoryNode[] },
        ])
      );
      const roots: DirectoryNode[] = [];
      system.modules.forEach((module) => {
        const node = nodes.get(module.id)!;
        const parent = nodes.get(module.parentId);
        if (parent) parent.children.push(node);
        else roots.push(node);
      });
      return { key: system.id, title: `${system.name} (${system.caseCount})`, children: roots };
    });
    return [
      { key: '__all__', title: '全部用例' },
      { key: '__unclassified__', title: `待人工分类 (${unclassifiedCount.value})` },
      ...systemNodes,
    ];
  });
  const editorVisible = ref(false);
  const editor = reactive({ kind: 'system' as 'system' | 'module', id: '', name: '', systemId: '', parentId: 'NONE' });
  const editorTitle = computed(() => `${editor.id ? '重命名' : '新建'}${editor.kind === 'system' ? '系统' : '模块'}`);
  const classificationVisible = ref(false);
  const classification = reactive({
    ids: [] as string[],
    systemId: undefined as string | undefined,
    moduleId: undefined as string | undefined,
  });
  const classificationModules = computed(
    () => systems.value.find((system) => system.id === classification.systemId)?.modules || []
  );

  function modulePath(module: AssetSystemModule) {
    const parts = [module.name];
    const visited = new Set([module.id]);
    let parent = classificationModules.value.find((item) => item.id === module.parentId);
    while (parent && !visited.has(parent.id)) {
      visited.add(parent.id);
      parts.unshift(parent.name);
      const { parentId } = parent;
      parent = classificationModules.value.find((item) => item.id === parentId);
    }
    return parts.join(' / ');
  }
  async function loadRows() {
    if (!app.currentOrgId) return;
    const request = ++sequence;
    loading.value = true;
    selectedIds.value = [];
    failed.value = false;
    try {
      const result = await MSR.post<{ list: AssetRow[]; total: number }>({
        url: '/case-asset/case/page',
        data: {
          dimension: 'SYSTEM',
          current: current.value,
          pageSize: 20,
          keyword: keyword.value.trim() || undefined,
          systemId: selectedSystem.value?.id,
          systemModuleId: selectedModule.value?.id,
          unclassifiedSystem: selectedKey.value === '__unclassified__',
        },
      });
      if (request !== sequence || !active) return;
      rows.value = result.list || [];
      total.value = result.total || 0;
    } catch {
      if (request === sequence && active) {
        rows.value = [];
        total.value = 0;
        failed.value = true;
      }
    } finally {
      if (request === sequence && active) loading.value = false;
    }
  }
  async function refresh() {
    if (!app.currentOrgId) return;
    const request = ++treeSequence;
    treeLoading.value = true;
    try {
      const result = await getAssetSystems();
      if (request !== treeSequence || !active) return;
      systems.value = result.systems;
      unclassifiedCount.value = result.unclassifiedCount;
      if (!selectedSystem.value && !['__all__', '__unclassified__'].includes(selectedKey.value))
        selectedKey.value = '__all__';
      await loadRows();
    } catch {
      if (request === treeSequence && active) failed.value = true;
    } finally {
      if (request === treeSequence && active) treeLoading.value = false;
    }
  }
  function selectDirectory(keys: Array<string | number>) {
    selectedKey.value = String(keys[0] || '__all__');
    current.value = 1;
    loadRows();
  }
  function selectRows(keys: Array<string | number>) {
    selectedIds.value = keys.map(String);
  }
  function search() {
    current.value = 1;
    loadRows();
  }
  function changePage(page: number) {
    current.value = page;
    loadRows();
  }
  function openCase(row: AssetRow) {
    router.push({
      name: TestAssetRouteEnum.TEST_ASSET_CASE_DETAIL,
      params: { catalogId: row.assetCatalogId, caseId: row.id },
      query: { ...route.query, directory: 'system' },
    });
  }
  function openEditor(kind: 'system' | 'module', rename = false) {
    const target = kind === 'system' ? selectedSystem.value : selectedModule.value;
    Object.assign(editor, {
      kind,
      id: rename ? target?.id || '' : '',
      name: rename ? target?.name || '' : '',
      systemId: selectedSystem.value?.id || '',
      parentId: selectedModule.value?.id || 'NONE',
    });
    editorVisible.value = true;
  }
  async function saveDirectory() {
    if (!editor.name.trim()) {
      Message.warning('请输入名称');
      return false;
    }
    try {
      if (editor.kind === 'system') await saveAssetSystem(editor.name.trim(), editor.id || undefined);
      else await saveAssetSystemModule(editor.systemId, editor.name.trim(), editor.parentId, editor.id || undefined);
      await refresh();
      return true;
    } catch {
      return false;
    }
  }
  function removeDirectory() {
    const system = selectedSystem.value;
    if (!system) return;
    const module = selectedModule.value;
    Modal.warning({
      title: `删除${module ? '模块' : '系统'}`,
      content: `确认删除“${module?.name || system.name}”？有子模块或归属用例时不能删除。`,
      hideCancel: false,
      onBeforeOk: async () => {
        try {
          if (module) await deleteAssetSystemModule(system.id, module.id);
          else await deleteAssetSystem(system.id);
          selectedKey.value = '__all__';
          await refresh();
          return true;
        } catch {
          return false;
        }
      },
    });
  }
  function openClassification(ids: string[]) {
    classification.ids = [...ids];
    classification.systemId = selectedSystem.value?.id;
    classification.moduleId = selectedModule.value?.id;
    classificationVisible.value = true;
  }
  async function saveClassification() {
    try {
      await classifyAssetCases(classification.ids, classification.systemId, classification.moduleId);
      await refresh();
      Message.success('系统分类已保存');
      return true;
    } catch {
      return false;
    }
  }
  watch(
    () => app.currentOrgId,
    () => {
      sequence++;
      treeSequence++;
      systems.value = [];
      rows.value = [];
      total.value = 0;
      unclassifiedCount.value = 0;
      selectedIds.value = [];
      selectedKey.value = '__all__';
      current.value = 1;
      failed.value = false;
      loading.value = false;
      treeLoading.value = false;
      editorVisible.value = false;
      classificationVisible.value = false;
      if (app.currentOrgId) refresh();
    },
    { immediate: true }
  );
  onBeforeUnmount(() => {
    active = false;
    sequence++;
    treeSequence++;
  });
</script>

<style scoped>
  .classification-select {
    padding: 4px 8px;
    width: 100%;
    min-height: 32px;
    border: 1px solid var(--color-neutral-3);
    border-radius: 4px;
    color: var(--color-text-1);
    background: var(--color-bg-2);
    font: inherit;
  }
  .classification-select:focus-visible {
    outline: 2px solid rgb(var(--primary-6));
    outline-offset: 2px;
  }
  .system-assets-layout {
    display: flex;
    overflow: hidden;
    min-width: 0;
    max-width: 100%;
    gap: 24px;
  }
  .system-directory {
    padding-right: 16px;
    width: 260px;
    min-width: 0;
    overflow-wrap: anywhere;
    border-right: 1px solid var(--color-neutral-3);
    flex: none;
  }
  @media (max-width: 900px) {
    .system-assets-layout {
      flex-direction: column;
    }
    .system-directory {
      overflow: auto;
      width: 100%;
      max-height: 320px;
      border-right: none;
    }
  }
</style>
