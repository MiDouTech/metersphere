<template>
  <a-spin class="h-full w-full" :loading="loading">
    <a-input
      v-model:model-value="keyword"
      :placeholder="t('orgStructure.department.search')"
      allow-clear
      class="mb-[12px]"
      :max-length="255"
    />
    <div class="h-[calc(100vh-280px)] overflow-auto">
      <MsTree
        v-model:selected-keys="selectedKeys"
        :data="treeData"
        :keyword="keyword"
        :empty-text="t('common.noData')"
        :default-expand-all="true"
        block-node
        :field-names="{
          title: 'name',
          key: 'id',
          children: 'children',
        }"
        @select="handleSelect"
      >
        <template #title="nodeData">
          <div
            class="inline-flex w-full min-w-0 items-center gap-[4px]"
            :class="{ 'text-[var(--color-text-4)]': nodeData.deptStatus === 0 }"
          >
            <span class="one-line-text min-w-0 flex-1">{{ nodeData.name }}</span>
            <span class="shrink-0 text-[var(--color-text-brand)]">({{ nodeData.totalUserCount ?? 0 }})</span>
            <a-tag v-if="nodeData.deptStatus === 0" size="small" color="gray">{{
              t('orgStructure.department.disabled')
            }}</a-tag>
          </div>
        </template>
      </MsTree>
    </div>
  </a-spin>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue';

  import MsTree from '@/components/business/ms-tree/index.vue';
  import type { MsTreeNodeData } from '@/components/business/ms-tree/types';

  import { getDepartmentTree } from '@/api/modules/setting/orgStructure';
  import { useI18n } from '@/hooks/useI18n';

  import type { DepartmentTreeNode } from '@/models/setting/orgStructure';

  const props = defineProps<{
    organizationId: string;
  }>();

  const emit = defineEmits<{
    (e: 'selectDepartment', departmentId: string | undefined): void;
  }>();

  const { t } = useI18n();
  const loading = ref(false);
  const keyword = ref('');
  const treeData = ref<DepartmentTreeNode[]>([]);
  const selectedKeys = ref<string[]>([]);

  async function loadTree() {
    if (!props.organizationId) {
      treeData.value = [];
      return;
    }
    try {
      loading.value = true;
      treeData.value = await getDepartmentTree(props.organizationId);
    } finally {
      loading.value = false;
    }
  }

  function handleSelect(_keys: Array<string | number>, node: MsTreeNodeData) {
    emit('selectDepartment', (node as DepartmentTreeNode)?.id);
  }

  function resetSelection() {
    selectedKeys.value = [];
    emit('selectDepartment', undefined);
  }

  watch(
    () => props.organizationId,
    () => {
      resetSelection();
      loadTree();
    },
    { immediate: true }
  );

  defineExpose({
    loadTree,
    resetSelection,
  });
</script>
