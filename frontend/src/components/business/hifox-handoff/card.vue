<template>
  <section class="mt-3 border p-3">
    <h3>Webhook测试计划消息</h3>
    <a-spin :loading="loading">
      <a-alert v-if="error" type="error"
        >推送信息加载失败或交接详情受限 <a-button @click="load">重试</a-button></a-alert
      >
      <template v-else-if="view">
        <p
          >资格：{{
            view.eligibility?.eligible
              ? `功能用例${view.eligibility.functionalCount}条已通过，快照有效`
              : view.eligibility?.reason || '未关联'
          }}</p
        >
        <p>{{ view.timeState }}</p
        ><p>{{ risk }}</p>
        <p v-if="view.legacy">旧版结果待人工核对，不自动重发；可证明从未发送时可预览单条迁移。</p>
        <Details v-if="view.delivery?.business_key" :value="view.delivery as Delivery" />
        <p v-else>尚无投递记录</p>
        <a-button :disabled="loading || busy" @click="load">刷新</a-button>
        <a-button v-if="canSend" :disabled="busy" @click="prepare">{{ pushLabel }}</a-button>
      </template>
    </a-spin>
    <a-modal v-model:visible="visible" title="确认单条推送/恢复" :on-before-ok="confirm">
      <p>{{ preview?.message }}</p
      ><p>原通过时间：{{ preview?.payload.occurredAt }}；{{ preview?.payload.interval }}</p>
      <Inspection v-if="preview?.payload.inspection" :value="preview.payload.inspection" />
      <p>已用 {{ preview?.payload.attemptsUsed }}/4；剩余 {{ preview?.payload.remaining }} 次</p>
      <p
        >接收配置版本 {{ preview?.payload.configuration.urlRevision }}，认证
        {{ preview?.payload.configuration.authMode }}</p
      >
      <a-checkbox v-model="acknowledged">已理解重复处理风险，确认仅续剩余预算</a-checkbox>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import Details from './details.vue';
  import Inspection from './inspection.vue';

  import type { Delivery, PreviewView, Summary } from '@/api/modules/hifox-handoff';
  import { confirmAction, getSummary, previewAction, risk } from '@/api/modules/hifox-handoff';
  import { useAppStore } from '@/store';
  import createLatestRequest from '@/utils/latestRequest';

  const props = defineProps<{ side: string; objectId: string }>();
  const app = useAppStore();
  const view = ref<Summary>();
  const preview = ref<PreviewView>();
  const loading = ref(false);
  const busy = ref(false);
  const error = ref(false);
  const visible = ref(false);
  const acknowledged = ref(false);
  const request = createLatestRequest();
  const canSend = computed(
    () =>
      props.side === 'plan' &&
      view.value?.canManage &&
      view.value.enabled &&
      view.value.eligibility?.eligible &&
      (!view.value.legacy || view.value.legacyCanMigrate) &&
      view.value.timeState !== '原通过时间待核实' &&
      (!view.value.delivery?.business_key ||
        (['PAUSED', 'FAILED', 'CANCELLED'].includes(view.value.delivery.state || '') &&
          (view.value.delivery.attempts_used || 0) < 4 &&
          !view.value.delivery.source_changed))
  );
  const pushLabel = computed(() => {
    if (view.value?.legacy) return '迁移未发送记录并推送';
    return view.value?.delivery?.business_key ? '恢复剩余重试' : '推送当前通过计划';
  });
  async function load() {
    const seq = request.begin();
    loading.value = true;
    error.value = false;
    view.value = undefined;
    visible.value = false;
    try {
      const result = await getSummary(app.currentProjectId, props.side, props.objectId);
      if (request.isCurrent(seq)) view.value = result;
    } catch {
      if (request.isCurrent(seq)) error.value = true;
    } finally {
      if (request.isCurrent(seq)) loading.value = false;
    }
  }
  async function prepare() {
    if (!canSend.value || busy.value) return;
    busy.value = true;
    const seq = request.current();
    try {
      let action = view.value?.delivery?.business_key ? 'RESUME' : 'PUSH';
      if (view.value?.legacy) action = 'MIGRATE_UNSENT';
      const result = await previewAction(app.currentProjectId, props.objectId, action);
      if (!request.isCurrent(seq)) return;
      preview.value = result;
      acknowledged.value = false;
      visible.value = true;
    } catch {
      if (request.isCurrent(seq)) Message.error('预览失败，请核对当前资格及权限');
    } finally {
      if (request.isCurrent(seq)) busy.value = false;
    }
  }
  async function confirm() {
    if (!acknowledged.value || busy.value || !preview.value || preview.value.expiresAt <= Date.now()) return false;
    busy.value = true;
    const seq = request.current();
    try {
      await confirmAction(app.currentProjectId, preview.value.previewId);
      if (request.isCurrent(seq)) {
        Message.success('已加入异步投递队列');
        busy.value = false;
        await load();
      }
      return true;
    } catch {
      if (request.isCurrent(seq)) Message.error('确认失败，请刷新并重新预览');
      return false;
    } finally {
      if (request.isCurrent(seq)) busy.value = false;
    }
  }
  watch(
    () => [app.currentProjectId, props.side, props.objectId],
    () => {
      busy.value = false;
      load();
    },
    { immediate: true }
  );
  onBeforeUnmount(() => {
    request.invalidate();
  });
</script>
