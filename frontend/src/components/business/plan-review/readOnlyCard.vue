<template>
  <section class="my-3 rounded border p-3" aria-label="计划与评审关联（只读）">
    <a-spin :loading="loading">
      <a-alert v-if="error" type="error">关联信息加载失败 <a-button @click="load">重试</a-button></a-alert>
      <PlanReviewListCell v-else :side="side" :value="value" />
      <p class="mt-2 text-xs">关联变更与管理请返回同项目列表，勾选当前对象后从上方操作区发起。</p>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';

  import PlanReviewListCell from './listCell.vue';

  import type { RelationListSummary, RelationSide, RelationSummary } from '@/api/modules/plan-review';
  import { getPlanReview } from '@/api/modules/plan-review';
  import useAppStore from '@/store/modules/app';

  const props = defineProps<{ side: RelationSide; objectId: string; objectStatus?: string }>();
  const emit = defineEmits<{ (e: 'change', summary: RelationSummary): void }>();
  const appStore = useAppStore();
  const value = ref<RelationListSummary>();
  const error = ref(false);
  const loading = ref(false);
  let sequence = 0;
  async function load() {
    const current = ++sequence;
    value.value = undefined;
    error.value = false;
    if (!props.objectId) return;
    loading.value = true;
    try {
      const summary = await getPlanReview(props.side, props.objectId);
      if (current !== sequence) return;
      value.value = { ...summary, syncState: summary.sync?.state };
      emit('change', summary);
    } catch {
      if (current === sequence) error.value = true;
    } finally {
      if (current === sequence) loading.value = false;
    }
  }
  watch(() => [props.side, props.objectId, appStore.currentProjectId], load, { immediate: true });
  onBeforeUnmount(() => {
    sequence++;
  });
</script>
