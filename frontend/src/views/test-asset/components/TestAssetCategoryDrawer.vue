<template>
  <a-drawer v-model:visible="visible" title="资产分类管理" :width="720" unmount-on-close @open="load">
    <a-alert v-if="error" type="error" class="mb-3">{{ error }}</a-alert>
    <div class="grid h-full grid-cols-2 gap-4">
      <div class="border-r pr-4">
        <a-input-search
          v-model="keyword"
          allow-clear
          placeholder="搜索分类"
          class="mb-3"
          @search="load"
          @clear="load"
        />
        <a-spin :loading="loading" class="w-full">
          <a-tree
            v-if="categories.length"
            block-node
            :data="categories"
            :field-names="{ key: 'id', title: 'name', children: 'children' }"
            @select="selectNode"
          />
          <a-empty v-else description="暂无资产分类" />
        </a-spin>
      </div>
      <div>
        <a-form :model="form" layout="vertical">
          <a-form-item label="当前分类"
            ><span>{{ selected?.path || '根目录' }}</span></a-form-item
          >
          <a-form-item label="分类名称" required>
            <a-input v-model="form.name" :max-length="100" show-word-limit placeholder="1～100 个字符" />
          </a-form-item>
          <a-form-item label="父分类">
            <a-tree-select
              v-model="form.parentId"
              allow-clear
              allow-search
              :data="parentOptions"
              :field-names="{ key: 'id', title: 'name', children: 'children' }"
              placeholder="不选择表示根分类"
            />
          </a-form-item>
          <a-space>
            <a-button type="primary" :loading="saving" @click="save">{{ selected ? '保存修改' : '新建分类' }}</a-button>
            <a-button @click="createChild">新建子分类</a-button>
            <a-button @click="resetForm">新建根分类</a-button>
            <a-button v-if="selected" :disabled="selectedIndex <= 0" @click="moveSelected(-1)">上移</a-button>
            <a-button
              v-if="selected"
              :disabled="selectedIndex < 0 || selectedIndex >= selectedSiblings.length - 1"
              @click="moveSelected(1)"
              >下移</a-button
            >
            <a-button v-if="selected" status="danger" @click="deleteVisible = true">删除</a-button>
          </a-space>
        </a-form>
      </div>
    </div>
    <a-modal v-model:visible="deleteVisible" title="删除资产分类" :ok-loading="saving" @ok="remove">
      <a-alert type="warning" class="mb-3">删除不会删除资产；分类非空时请选择处理方式。</a-alert>
      <a-form :model="deleteForm" layout="vertical">
        <a-form-item label="处理方式">
          <a-select v-model="deleteForm.strategy" allow-clear placeholder="空分类可不选择">
            <a-option value="UNCLASSIFY">资产转为未分类，子分类提升一级</a-option>
            <a-option value="MIGRATE">资产和子分类迁移到目标分类</a-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="deleteForm.strategy === 'MIGRATE'" label="目标分类">
          <a-tree-select
            v-model="deleteForm.targetCategoryId"
            :data="parentOptions"
            :field-names="{ key: 'id', title: 'name', children: 'children' }"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-drawer>
</template>

<script setup lang="ts">
  import { Message } from '@arco-design/web-vue';

  import type { TestAssetCategory } from '@/api/modules/ai-execution';
  import {
    createTestAssetCategory,
    deleteTestAssetCategory,
    listTestAssetCategories,
    reorderTestAssetCategories,
    updateTestAssetCategory,
  } from '@/api/modules/ai-execution';

  const visible = defineModel<boolean>('visible', { required: true });
  const emit = defineEmits<{ (e: 'changed'): void }>();
  const categories = ref<TestAssetCategory[]>([]);
  const selected = ref<TestAssetCategory>();
  const keyword = ref('');
  const loading = ref(false);
  const saving = ref(false);
  const error = ref('');
  const form = reactive<{ name: string; parentId?: string }>({ name: '', parentId: undefined });
  const deleteVisible = ref(false);
  const deleteForm = reactive<{ strategy?: string; targetCategoryId?: string }>({});

  function omitBranch(nodes: TestAssetCategory[], id?: string): TestAssetCategory[] {
    return nodes
      .filter((node) => node.id !== id)
      .map((node) => ({ ...node, children: omitBranch(node.children || [], id) }));
  }
  const parentOptions = computed(() => omitBranch(categories.value, selected.value?.id));
  function findSiblings(nodes: TestAssetCategory[], parentId?: string): TestAssetCategory[] {
    if (!parentId) return nodes;
    const directParent = nodes.find((node) => node.id === parentId);
    if (directParent) return directParent.children || [];
    return nodes.map((node) => findSiblings(node.children || [], parentId)).find((result) => result.length) || [];
  }
  const selectedSiblings = computed(() => findSiblings(categories.value, selected.value?.parentId));
  const selectedIndex = computed(() => selectedSiblings.value.findIndex((item) => item.id === selected.value?.id));
  async function load() {
    loading.value = true;
    error.value = '';
    try {
      categories.value = await listTestAssetCategories(keyword.value.trim() || undefined);
    } catch (reason: any) {
      error.value = reason?.message || '分类加载失败，请稍后重试';
    } finally {
      loading.value = false;
    }
  }
  function selectNode(_keys: Array<string | number>, data: any) {
    selected.value = data.node as TestAssetCategory | undefined;
    form.name = selected.value?.name || '';
    form.parentId = selected.value?.parentId || undefined;
  }
  function resetForm() {
    selected.value = undefined;
    form.name = '';
    form.parentId = undefined;
  }
  function createChild() {
    const parentId = selected.value?.id;
    selected.value = undefined;
    form.name = '';
    form.parentId = parentId;
  }
  async function save() {
    if (!form.name.trim()) {
      Message.warning('请输入分类名称');
      return;
    }
    saving.value = true;
    try {
      if (selected.value)
        await updateTestAssetCategory(selected.value.id, { name: form.name, parentId: form.parentId });
      else await createTestAssetCategory({ name: form.name, parentId: form.parentId });
      Message.success('分类已保存');
      resetForm();
      await load();
      emit('changed');
    } finally {
      saving.value = false;
    }
  }
  async function moveSelected(offset: number) {
    if (!selected.value) return;
    const ids = selectedSiblings.value.map((item) => item.id);
    const from = ids.indexOf(selected.value.id);
    const to = from + offset;
    if (from < 0 || to < 0 || to >= ids.length) return;
    [ids[from], ids[to]] = [ids[to], ids[from]];
    saving.value = true;
    try {
      await reorderTestAssetCategories(ids);
      await load();
      emit('changed');
    } finally {
      saving.value = false;
    }
  }
  async function remove() {
    if (!selected.value) return;
    saving.value = true;
    try {
      await deleteTestAssetCategory(selected.value.id, deleteForm);
      Message.success('分类已删除');
      deleteVisible.value = false;
      resetForm();
      await load();
      emit('changed');
    } finally {
      saving.value = false;
    }
  }
</script>
