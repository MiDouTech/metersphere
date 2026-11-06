<template>
  <div ref="root" class="min-w-0 flex-1" tabindex="-1" data-relation-list-title aria-label="列表关联操作">
    <LinkCard
      v-if="selected && !loading"
      :key="`${side}:${selected.id}:${project}:${context}`"
      :side="side"
      :object-id="selected.id"
      :object-name="selected.name"
      :object-num="selected.num"
      :object-status="selected.status"
      @refresh="refresh"
    />
    <div v-else class="flex flex-wrap items-center gap-2">
      <a-button disabled>{{ side === 'plan' ? '关联评审计划' : '关联测试计划' }}</a-button>
      <a-button disabled>重新预览并确认同步</a-button>
      <a-button disabled>解除关联</a-button>
      <a-button disabled>关联管理</a-button>
      <span role="status">{{ reason }}</span>
    </div>
    <p v-if="notice" role="status">{{ notice }}</p>
  </div>
</template>

<script setup lang="ts">
  import { computed, nextTick, ref, watch } from 'vue';

  import LinkCard from './linkCard.vue';

  import type { RelationSide } from '@/api/modules/plan-review';

  interface Row {
    id: string;
    name: string;
    num?: number;
    status: string;
    type?: string;
    children?: Row[];
  }
  const props = defineProps<{
    side: RelationSide;
    rows: Row[];
    selectedIds: string[];
    expandedIds?: string[];
    project: string;
    loading?: boolean;
    context: string;
    reload: () => Promise<unknown>;
  }>();
  const emit = defineEmits<{ (e: 'clear'): void }>();
  const root = ref<HTMLElement>();
  const notice = ref('');
  const visibleRows = computed(() => {
    function collect(rows: Row[]): Row[] {
      return rows.flatMap((row) => [
        row,
        ...(row.children && (!props.expandedIds || props.expandedIds.includes(row.id)) ? collect(row.children) : []),
      ]);
    }
    return collect(props.rows);
  });
  const selected = computed(() => {
    if (props.selectedIds.length !== 1) return undefined;
    const row = visibleRows.value.find((item) => item.id === props.selectedIds[0]);
    return row?.type === 'GROUP' ? undefined : row;
  });
  const reason = computed(() => {
    if (props.loading) return '列表读取中，请稍候';
    if (!props.selectedIds.length) return '请先勾选一条测试/评审计划';
    if (props.selectedIds.length > 1) return '一次请选择一条进行关联';
    if (visibleRows.value.find((row) => row.id === props.selectedIds[0])?.type === 'GROUP') return '计划组不能建立关联';
    return '列表已变化，请重新选择';
  });
  watch(
    () => [props.context, props.project],
    () => {
      emit('clear');
      root.value?.focus();
    },
    { flush: 'sync' }
  );
  watch(
    () => [visibleRows.value, props.loading],
    () => {
      if (!props.loading && props.selectedIds.some((id) => !visibleRows.value.some((row) => row.id === id))) {
        emit('clear');
        notice.value = '列表已变化，请重新选择';
        nextTick(() => root.value?.focus());
      }
    }
  );
  async function refresh(outcome?: 'paired' | 'unlinked') {
    const id = selected.value?.id;
    await props.reload();
    await nextTick();
    if (!id || !visibleRows.value.some((row) => row.id === id)) {
      emit('clear');
      notice.value = '列表已变化，请重新选择';
      root.value?.focus();
      return;
    }
    const row = document.querySelector<HTMLElement>(`[data-relation-result="${props.side}-${CSS.escape(id)}"]`);
    if (outcome === 'unlinked') root.value?.querySelector<HTMLButtonElement>('button:not(:disabled)')?.focus();
    else if (row) row.focus({ preventScroll: true });
    else root.value?.focus();
  }
</script>
