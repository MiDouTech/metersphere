<template>
  <a-modal
    v-model:visible="visible"
    title="选择已有测试计划并确认评审范围"
    :width="800"
    :ok-loading="busy"
    :on-before-cancel="() => !busy"
    :ok-text="preview ? '确认创建并关联' : '预览功能用例范围'"
    :ok-button-props="{
      disabled: busy || (preview ? !itemsReady || !acknowledged || Date.now() >= preview.expiresAt : !selected),
    }"
    @before-ok="submit"
  >
    <template v-if="!preview">
      <a-input-search v-model="keyword" placeholder="搜索同项目未关联且可编辑的真实计划" @search="search(1)" />
      <a-alert v-if="failed" type="error">候选加载失败，请重试；不会按无候选处理。</a-alert>
      <a-table :data="candidates" row-key="id" :loading="busy" :pagination="false" @row-click="selectCandidate">
        <template #columns>
          <a-table-column title="选择" :width="60"
            ><template #cell="{ record }"
              ><a-radio v-model="selected" :value="record.id" :disabled="busy || failed" uninject-group-context
                ><span class="sr-only">选择 {{ record.num }} {{ record.name }}</span></a-radio
              ></template
            ></a-table-column
          >
          <a-table-column title="编号" data-index="num" :width="90" /><a-table-column
            title="计划名称"
            data-index="name"
            ellipsis
            tooltip
          /><a-table-column title="状态" data-index="status" :width="120" />
        </template>
        <template #empty>暂无可选择的测试计划</template>
      </a-table>
      <a-pagination :current="candidatePage" :total="total" :page-size="20" @change="search" />
    </template>
    <template v-else>
      <a-alert v-if="recoverySettings" class="mb-3"
        >继续原创建操作，保留原预留评审ID和原设置：{{ recoverySettings.name }}；{{
          recoverySettings.reviewPassRule === 'SINGLE' ? '单人通过' : '全部通过'
        }}；评审人{{
          recoverySettings.reviewerCount
        }}名。重新核对当前计划范围后才会创建，不会自动改选目录或评审人。</a-alert
      >
      <a-alert type="warning">{{ preview.message }}</a-alert>
      <p class="my-3"
        >新增 {{ preview.difference.added.length }}，移出 0，保留 0；有效至
        {{ new Date(preview.expiresAt).toLocaleString() }}</p
      >
      <a-list :data="items" :loading="busy"
        ><template #item="{ item }"
          ><a-list-item>#{{ item.num }} {{ item.name }} · 待评审</a-list-item></template
        ></a-list
      >
      <a-pagination :current="itemPage" :total="preview.difference.added.length" :page-size="20" @change="loadItems" />
      <a-checkbox v-model="acknowledged" class="mt-3">已知晓仅使用所选计划功能用例，创建评审并建立唯一关联</a-checkbox>
      <a-button size="small" class="ml-2" @click="refreshPreview">重新预览</a-button>
      <a-alert v-if="failed" type="warning" class="mt-2"
        >请求未完成，不能认定已创建或未创建。请保留原请求重试；原操作
        {{ preview.operationId }} 可从本人操作记录查询。</a-alert
      >
    </template>
  </a-modal>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';

  import type {
    LinkedReviewRecoveryPreview,
    RelationCandidate,
    RelationDifferenceItem,
    RelationPreview,
  } from '@/api/modules/plan-review';
  import {
    confirmNewLinkedReview,
    getNewReviewPlanCandidates,
    getPlanReviewPreviewItems,
    previewNewLinkedReview,
    recoverNewLinkedReviewPreview,
  } from '@/api/modules/plan-review';
  import useAppStore from '@/store/modules/app';

  import type { TableData } from '@arco-design/web-vue';

  const appStore = useAppStore();
  const visible = ref(false);
  const busy = ref(false);
  const failed = ref(false);
  const selected = ref('');
  const keyword = ref('');
  const candidates = ref<RelationCandidate[]>([]);
  const total = ref(0);
  const candidatePage = ref(1);
  function selectCandidate(record: TableData) {
    const candidate = candidates.value.find((item) => item.id === record.id);
    if (!busy.value && !failed.value && candidate) selected.value = candidate.id;
  }
  const preview = ref<RelationPreview & { operationId: string }>();
  const acknowledged = ref(false);
  const items = ref<RelationDifferenceItem[]>([]);
  const itemsReady = ref(false);
  const itemPage = ref(1);
  let context = 0;
  let key = '';
  let draft: Record<string, unknown> = {};
  let created: ((id: string) => void) | undefined;
  let recoveryId: string | undefined;
  const recoverySettings = ref<LinkedReviewRecoveryPreview['settingsSummary']>();
  async function search(page: number) {
    const current = ++context;
    busy.value = true;
    failed.value = false;
    selected.value = '';
    candidates.value = [];
    candidatePage.value = page;
    try {
      const result = await getNewReviewPlanCandidates(String(draft.projectId), keyword.value, page);
      if (current !== context) return;
      candidates.value = result.items;
      total.value = result.total;
    } catch {
      if (current === context) failed.value = true;
    } finally {
      if (current === context) busy.value = false;
    }
  }
  async function loadItems(page: number) {
    if (!preview.value) return;
    itemsReady.value = false;
    const current = ++context;
    busy.value = true;
    failed.value = false;
    itemPage.value = page;
    items.value = [];
    try {
      const result = await getPlanReviewPreviewItems(preview.value.previewId, 'added', page);
      if (current === context) {
        items.value = result.items;
        itemsReady.value = true;
      }
    } catch {
      if (current === context) {
        failed.value = true;
        acknowledged.value = false;
      }
    } finally {
      if (current === context) busy.value = false;
    }
  }
  async function refreshPreview() {
    const current = ++context;
    busy.value = true;
    failed.value = false;
    acknowledged.value = false;
    try {
      const result = recoveryId
        ? await recoverNewLinkedReviewPreview(String(draft.projectId), recoveryId)
        : await previewNewLinkedReview({ planId: selected.value, requestKey: key, draft });
      if (current !== context) return;
      if (recoveryId) {
        const recovery = result as LinkedReviewRecoveryPreview;
        key = recovery.requestKey;
        recoverySettings.value = recovery.settingsSummary;
      }
      preview.value = result;
      await loadItems(1);
    } catch {
      if (current === context) failed.value = true;
    } finally {
      if (current === context) busy.value = false;
    }
  }
  async function submit() {
    if (busy.value || draft.projectId !== appStore.currentProjectId) return false;
    if (!preview.value) {
      if (!selected.value) return false;
      await refreshPreview();
      return false;
    }
    if (!itemsReady.value || !acknowledged.value || Date.now() >= preview.value.expiresAt) return false;
    const current = ++context;
    busy.value = true;
    try {
      const result = await confirmNewLinkedReview({
        previewId: preview.value.previewId,
        requestKey: key,
        acknowledged: true,
      });
      if (current !== context) return false;
      if (!result.reviewId) throw new Error('Missing created review');
      created?.(result.reviewId);
      return true;
    } catch {
      if (current === context) failed.value = true;
      return false;
    } finally {
      if (current === context) busy.value = false;
    }
  }
  function open(value: Record<string, unknown>, onCreated: (id: string) => void) {
    context++;
    draft = structuredClone(value);
    created = onCreated;
    key = crypto.randomUUID();
    recoveryId = undefined;
    recoverySettings.value = undefined;
    preview.value = undefined;
    itemsReady.value = false;
    acknowledged.value = false;
    failed.value = false;
    selected.value = '';
    keyword.value = '';
    visible.value = true;
    search(1);
  }
  function openRecovery(value: LinkedReviewRecoveryPreview, onCreated: (id: string) => void) {
    if (value.projectId !== appStore.currentProjectId) return;
    context++;
    draft = { projectId: value.projectId };
    created = onCreated;
    key = value.requestKey;
    recoveryId = value.operationId;
    recoverySettings.value = value.settingsSummary;
    selected.value = value.planId;
    preview.value = value;
    itemsReady.value = false;
    acknowledged.value = false;
    failed.value = false;
    visible.value = true;
    loadItems(1);
  }
  watch(
    () => appStore.currentProjectId,
    () => {
      context++;
      visible.value = false;
      created = undefined;
      preview.value = undefined;
      recoveryId = undefined;
      recoverySettings.value = undefined;
      busy.value = false;
    }
  );
  watch(visible, (value) => {
    if (!value) {
      context++;
      created = undefined;
      busy.value = false;
      recoveryId = undefined;
      recoverySettings.value = undefined;
    }
  });
  onBeforeUnmount(() => {
    context++;
    created = undefined;
  });
  defineExpose({ open, openRecovery });
</script>
