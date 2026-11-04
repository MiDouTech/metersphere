<template>
  <a-modal
    v-model:visible="visible"
    title="复制计划：确认关联评审设置"
    :width="800"
    :ok-loading="busy"
    :on-before-cancel="() => !busy"
    :cancel-button-props="{ disabled: busy }"
    @before-ok="save"
  >
    <a-alert type="warning"
      >计划组本身不创建评审；每个真实子计划分别创建一份评审。原计划的关联关系和意见不会复制。</a-alert
    >
    <SettingsForm
      v-if="visible && requiresSettings"
      ref="settingsRef"
      :key="requestKey"
      :project-id="projectId"
      :plan-name="planName"
      :disabled="busy || confirmed !== undefined"
    />
    <a-alert v-if="!requiresSettings" class="mt-3"
      >本次只复制空计划组，不创建评审，无需评审设置。提交时仍会重新核对真实范围。</a-alert
    >
    <a-alert v-if="confirmed" class="mt-3"
      >本次确认参数已固定；重试只补未完成项，并复用原预留对象。不会重做已成功项，也不会自动改用其他目录或人员。</a-alert
    >
    <a-alert v-if="failed" type="warning"
      >复制结果待确认，请保留此窗口重试同一请求，不要重复新建；也可从详情关联卡查询当前用户操作记录。</a-alert
    >
    <template v-if="outcome">
      <p class="my-3"
        >{{ unknown ? '上次已确认结果（本轮结果待查询）' : '本轮已确认结果' }}：已创建
        {{ outcome.createdCount }} 项，未完成 {{ outcome.failedCount }} 项。</p
      >
      <a-list :data="outcome.items"
        ><template #item="{ item }"
          ><a-list-item>
            <div class="min-w-0"
              ><div class="truncate" :title="item.sourceName"
                >{{ item.sourceNum !== undefined && item.sourceNum !== null ? `#${item.sourceNum}` : '' }}
                {{ item.sourceName || '复制来源' }}</div
              >
              <div
                >{{ item.created ? '已创建' : '未完成' }} ·
                {{ item.sourceType === 'GROUP' ? '计划组不创建评审' : syncLabel(item.syncState)
                }}<span v-if="item.safeReason"> · {{ item.safeReason }}</span></div
              ></div
            >
          </a-list-item></template
        ></a-list
      >
    </template>
  </a-modal>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';

  import SettingsForm from './settingsForm.vue';

  import type { PlanReviewCopyOutcome, PlanReviewSettings } from '@/api/modules/plan-review';
  import useAppStore from '@/store/modules/app';

  const visible = ref(false);
  const busy = ref(false);
  const failed = ref(false);
  const projectId = ref('');
  const planName = ref('');
  const requestKey = ref('');
  const settingsRef = ref<InstanceType<typeof SettingsForm>>();
  const requiresSettings = ref(true);
  const outcome = ref<PlanReviewCopyOutcome>();
  const unknown = ref(false);
  type Request = { requestKey: string; reviewSettings?: PlanReviewSettings };
  let submit: ((value: Request) => Promise<PlanReviewCopyOutcome>) | undefined;
  const confirmed = ref<Request>();
  const appStore = useAppStore();
  let context = 0;
  function syncLabel(state?: string) {
    return (
      (
        {
          PENDING: '待同步',
          APPLYING: '同步中',
          SYNCED: '已同步',
          WAITING_RETRY: '等待重试',
          FAILED_MANUAL: '同步失败待处理',
          RECONFIRM_REQUIRED: '待重新确认',
          ENDED: '关联已结束',
          DELAYED: '同步延迟',
        } as Record<string, string>
      )[state || ''] || '同步状态请查关联记录'
    );
  }
  function open(project: string, name: string, action: NonNullable<typeof submit>, settingsRequired = true) {
    if (busy.value) return;
    context++;
    projectId.value = project;
    planName.value = name;
    submit = action;
    requestKey.value = crypto.randomUUID();
    confirmed.value = undefined;
    outcome.value = undefined;
    unknown.value = false;
    requiresSettings.value = settingsRequired;
    failed.value = false;
    visible.value = true;
  }
  async function save() {
    if (!submit || busy.value || projectId.value !== appStore.currentProjectId) return false;
    const current = context;
    busy.value = true;
    try {
      if (!confirmed.value) {
        const settings = requiresSettings.value ? await settingsRef.value?.validate() : undefined;
        if (current !== context || (requiresSettings.value && !settings)) return false;
        confirmed.value = { requestKey: requestKey.value, reviewSettings: settings };
      }
      const result = await submit(confirmed.value);
      if (current !== context) return false;
      outcome.value = result;
      unknown.value = false;
      failed.value = result.failedCount > 0;
      return result.failedCount === 0;
    } catch {
      if (current === context) {
        failed.value = true;
        unknown.value = true;
      }
      return false;
    } finally {
      if (current === context) busy.value = false;
    }
  }
  function invalidate() {
    context++;
    visible.value = false;
    busy.value = false;
    confirmed.value = undefined;
    outcome.value = undefined;
    submit = undefined;
  }
  watch(() => appStore.currentProjectId, invalidate);
  onBeforeUnmount(invalidate);
  defineExpose({ open });
</script>
