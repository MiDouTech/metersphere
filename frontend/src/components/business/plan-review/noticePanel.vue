<template>
  <a-modal v-model:visible="visible" title="关联通知记录" :width="820" :footer="false" :on-before-cancel="() => !busy">
    <a-alert>收件箱可读不代表用户已阅读。通知补投不会恢复成员同步；成员恢复须单独预览确认。</a-alert>
    <a-alert v-if="error" type="error" class="my-3">通知记录或操作未确认成功，请刷新查询；不会显示为已送达。</a-alert>
    <a-button class="my-3" :disabled="busy" @click="refresh">刷新</a-button>
    <a-empty v-if="!busy && !error && !items.length" description="暂无持久故障通知记录" />
    <a-list :data="items"
      ><template #item="{ item }"
        ><a-list-item>
          <div class="w-full">
            <p>{{ reasonLabel(item.reason) }} · 故障轮 {{ item.fault_round }} · {{ time(item.detected_at) }}</p>
            <p>{{ item.ended_at ? '原关联已结束' : item.recovered_at ? '业务已恢复' : '业务尚未恢复' }}</p>
            <a-alert v-if="item.noEligibleRecipients" type="warning"
              >无有效收件人，待处理。由项目现有账号管理者恢复账号或显式任命负责人后，再由有权者预览补投；不会自动选替代人。</a-alert
            >
            <div v-for="(delivery, index) in item.deliveries" :key="index" class="my-2">
              {{ delivery.type === 'FAULT' ? '原故障' : '业务恢复' }}：{{ stateLabel(delivery.state) }} · 本轮失败
              {{ delivery.attempts }} 次 · 重试轮 {{ delivery.retry_round }}
              <span v-if="delivery.next_retry_at"> · 下次 {{ time(delivery.next_retry_at) }}</span>
              <span v-if="delivery.readable_at"> · 可读于 {{ time(delivery.readable_at) }}</span>
              <p v-if="delivery.safe_error">{{ delivery.safe_error }}</p>
            </div>
            <a-button v-if="canRecover" :disabled="busy" @click="preview(item.id)">预览重试未送达通知</a-button>
          </div>
        </a-list-item></template
      ></a-list
    >
    <a-alert v-if="approval" class="my-3"
      >本次重新核对：有资格 {{ approval.eligibleCount }} 个账号，无资格
      {{ approval.ineligibleCount }} 个账号。只补未可读通知，原故障先于恢复通知，不重复发送已可读项。</a-alert
    >
    <a-button v-if="approval" :disabled="busy" type="primary" @click="confirm">确认补投</a-button>
  </a-modal>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';

  import type { PlanReviewFault, RelationSide } from '@/api/modules/plan-review';
  import {
    confirmPlanReviewNoticeRetry,
    getPlanReviewNotices,
    previewPlanReviewNoticeRetry,
  } from '@/api/modules/plan-review';

  const props = defineProps<{ projectId: string }>();
  const visible = ref(false);
  const busy = ref(false);
  const error = ref(false);
  const canRecover = ref(false);
  const items = ref<PlanReviewFault[]>([]);
  const approval = ref<Awaited<ReturnType<typeof previewPlanReviewNoticeRetry>>>();
  let relation = '';
  let side: RelationSide = 'plan';
  let context = 0;
  const time = (value: number) => new Date(value).toLocaleString();
  const reasonLabel = (value: string) =>
    ((
      { GRANT_REVOKED: '同步服务授权失效', RETRY_EXHAUSTED: '成员同步重试耗尽', DELAYED: '成员同步延迟' } as Record<
        string,
        string
      >
    )[value] || '关联待处理事项');
  const stateLabel = (value: string) =>
    ((
      {
        PENDING: '等待投递',
        WAITING_RETRY: '等待重试',
        FAILED_MANUAL: '通知失败待人工',
        INELIGIBLE: '账号或项目资格失效',
        READABLE: '收件箱已可读',
      } as Record<string, string>
    )[value] || '状态待核实');
  async function refresh() {
    if (busy.value || !relation) return;
    const current = context;
    busy.value = true;
    error.value = false;
    try {
      const response = await getPlanReviewNotices(props.projectId, side, relation);
      if (current !== context) return;
      items.value = response.items;
      canRecover.value = response.canRecover;
    } catch {
      if (current === context) error.value = true;
    } finally {
      if (current === context) busy.value = false;
    }
  }
  function open(id: string, objectSide: RelationSide) {
    if (busy.value) return;
    context++;
    relation = id;
    side = objectSide;
    items.value = [];
    approval.value = undefined;
    canRecover.value = false;
    visible.value = true;
    refresh();
  }
  async function preview(id: string) {
    if (busy.value || !canRecover.value) return;
    const current = context;
    busy.value = true;
    error.value = false;
    approval.value = undefined;
    try {
      const response = await previewPlanReviewNoticeRetry(props.projectId, id);
      if (current === context) approval.value = response;
    } catch {
      if (current === context) error.value = true;
    } finally {
      if (current === context) busy.value = false;
    }
  }
  async function confirm() {
    if (busy.value || !approval.value) return;
    if (approval.value.expiresAt <= Date.now()) {
      approval.value = undefined;
      error.value = true;
      return;
    }
    const current = context;
    busy.value = true;
    error.value = false;
    try {
      await confirmPlanReviewNoticeRetry(props.projectId, approval.value.previewId);
      if (current !== context) return;
      approval.value = undefined;
    } catch {
      if (current === context) error.value = true;
      return;
    } finally {
      if (current === context) busy.value = false;
    }
    if (current === context) await refresh();
  }
  function invalidate() {
    context++;
    visible.value = false;
    busy.value = false;
    relation = '';
    items.value = [];
    approval.value = undefined;
  }
  watch(() => props.projectId, invalidate);
  watch(visible, (value) => {
    if (!value) invalidate();
  });
  onBeforeUnmount(invalidate);
  defineExpose({ open });
</script>
