<template>
  <AgentPage>
    <a-alert v-if="pageError" type="error" class="mb-4">{{ pageError }}</a-alert>
    <a-tabs v-model:active-key="queueTab" type="rounded" class="mb-4">
      <a-tab-pane key="leases" title="执行租约" />
      <a-tab-pane key="triggers" title="调度规则" />
    </a-tabs>
    <MsCard v-if="queueTab === 'leases'" simple>
      <div class="mb-4 flex items-center gap-3"
        ><a-select v-model="leaseStatus" class="w-[160px]" allow-clear placeholder="租约状态" @change="loadLeases"
          ><a-option value="ACTIVE">ACTIVE</a-option><a-option value="COMPLETED">COMPLETED</a-option
          ><a-option value="FAILED">FAILED</a-option><a-option value="EXPIRED">EXPIRED</a-option
          ><a-option value="RELEASED">RELEASED</a-option></a-select
        ><a-button :loading="leaseLoading" @click="loadLeases">刷新</a-button></div
      >
      <a-table :data="leases" :loading="leaseLoading" :pagination="false" row-key="id">
        <template #columns>
          <a-table-column title="租约 ID" data-index="id" :width="220" /><a-table-column
            title="任务 ID"
            data-index="taskId"
            :width="220"
          />
          <a-table-column title="执行批次 ID" data-index="executionId" :width="220" />
          <a-table-column title="执行通道" :width="170">
            <template #cell="{ record }">{{ executorChannelLabel(record.executorChannel) }}</template>
          </a-table-column>
          <a-table-column title="执行器"
            ><template #cell="{ record }"
              >{{ record.leaseOwnerType || record.executorType || 'RUNNER' }} /
              {{ record.leaseOwnerId || record.executorId || record.runnerId || '-' }}</template
            ></a-table-column
          >
          <a-table-column title="尝试" data-index="attempt" :width="80" /><a-table-column
            title="状态"
            data-index="status"
            :width="110"
          />
          <a-table-column title="最近心跳" :width="180"
            ><template #cell="{ record }">{{ formatTime(record.lastHeartbeatTime) }}</template></a-table-column
          >
          <a-table-column title="到期时间" :width="180"
            ><template #cell="{ record }">{{ formatTime(record.expireTime) }}</template></a-table-column
          >
        </template>
      </a-table>
    </MsCard>
    <TriggerList
      v-else
      :items="triggers"
      :loading="triggerLoading"
      :error="triggerError"
      :format-time="formatTime"
      @refresh="loadTriggers"
      @create="openTrigger()"
      @edit="openTrigger"
      @fire="fireTrigger"
      @history="openHistory"
      @rotate="rotateSecret"
      @toggle="toggleTrigger"
    />

    <TriggerForm
      v-model:visible="triggerVisible"
      :saving="triggerSaving"
      :model="triggerForm"
      :environment-profiles="environmentProfiles"
      :credential-references="credentialReferences"
      :model-profiles="modelProfiles"
      :prompt-templates="promptTemplates"
      @environment-change="onEnvironmentChange"
      @select-assets="assetSelectorVisible = true"
      @save="saveTrigger"
    />

    <a-modal v-model:visible="assetSelectorVisible" title="从用例资产选择" :width="980" @ok="confirmAssetSelection">
      <CaseAssetSelector
        :selected-ids="draftAssetIds"
        :target-project-id="appStore.currentProjectId"
        scene="SCHEDULE_RULE"
        @change="onAssetSelectionChange"
      />
      <a-alert class="mt-3">此处显示的是待导入资产，不会把资产 ID 当作项目用例 ID 保存。</a-alert>
    </a-modal>

    <a-modal v-model:visible="secretVisible" title="Webhook 密钥（仅展示一次）" :footer="false" :mask-closable="false">
      <a-alert type="warning" class="mb-3">请立即保存。关闭后平台不会再次返回明文密钥。</a-alert>
      <div class="break-all rounded bg-[var(--color-fill-2)] p-3 font-mono text-sm">{{ oneTimeSecret }}</div>
    </a-modal>

    <TriggerHistory
      v-model:visible="historyVisible"
      :items="histories"
      :loading="historyLoading"
      :error="historyError"
      :format-time="formatTime"
    />
  </AgentPage>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import { Message } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import CaseAssetSelector from '@/components/business/case-asset-selector/index.vue';
  import AgentPage from './components/AgentPage.vue';
  import TriggerForm from './trigger/TriggerForm.vue';
  import TriggerHistory from './trigger/TriggerHistory.vue';
  import TriggerList from './trigger/TriggerList.vue';

  import type {
    AiCredentialReference,
    AiEnvironmentProfile,
    AiExecutorChannel,
    AiModelProfile,
    AiPromptTemplateVersion,
    AiRunnerLease,
    AiTaskTrigger,
    AiTaskTriggerHistory,
  } from '@/api/modules/ai-execution';
  import {
    createAiTaskTrigger,
    fireAiTaskTrigger,
    getAiExecutionLeases,
    listAiCredentialReferences,
    listAiEnvironmentProfiles,
    listAiModelProfiles,
    listAiPromptTemplateVersions,
    listAiTaskTriggerHistory,
    listAiTaskTriggers,
    preflightAiExecution,
    rotateAiTaskTriggerSecret,
    updateAiTaskTrigger,
  } from '@/api/modules/ai-execution';
  import {
    getCaseAssetImportResult,
    getDefaultHubJob,
    importCasesFromAssets,
  } from '@/api/modules/case-management/featureCase';
  import { useAppStore } from '@/store';

  const appStore = useAppStore();
  const pageError = ref('');
  const route = useRoute();
  const queueTab = ref(route.query.tab === 'triggers' ? 'triggers' : 'leases');
  const leases = ref<AiRunnerLease[]>([]);
  const leaseStatus = ref<string>();
  const leaseLoading = ref(false);
  const triggerLoading = ref(false);
  const triggerError = ref('');
  const triggerSaving = ref(false);
  const triggerVisible = ref(false);
  const assetSelectorVisible = ref(false);
  const draftAssetIds = ref<string[]>([]);
  const pendingAssetIds = ref<string[]>([]);
  const assetImportIdempotencyKey = ref(crypto.randomUUID());
  const secretVisible = ref(false);
  const oneTimeSecret = ref('');
  const triggers = ref<AiTaskTrigger[]>([]);
  const histories = ref<AiTaskTriggerHistory[]>([]);
  const environmentProfiles = ref<AiEnvironmentProfile[]>([]);
  const credentialReferences = ref<AiCredentialReference[]>([]);
  const modelProfiles = ref<AiModelProfile[]>([]);
  const promptTemplates = ref<AiPromptTemplateVersion[]>([]);
  const historyVisible = ref(false);
  const historyLoading = ref(false);
  const historyError = ref('');
  const triggerForm = reactive({
    id: '',
    name: '',
    triggerType: 'CRON' as AiTaskTrigger['triggerType'],
    cronExpression: '0 0 2 * * ?',
    timezone: 'Asia/Shanghai',
    eventType: '',
    eventFilter: '',
    concurrencyPolicy: 'FORBID' as 'FORBID' | 'ALLOW',
    missedPolicy: 'FIRE_ONCE' as 'SKIP' | 'FIRE_ONCE',
    enabled: true,
    taskName: '',
    objective: '',
    environmentProfileId: '',
    credentialReferenceId: '',
    modelProfileId: '',
    promptTemplateId: '',
    runnerType: 'BROWSER',
    requiredCapabilities: ['BROWSER'] as string[],
    responsibleUserIds: '',
    caseIds: '',
  });
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  function executorChannelLabel(channel?: AiExecutorChannel) {
    if (channel === 'MODEL_API_RUNNER') return '模型执行器';
    if (channel === 'EXTERNAL_MCP_AGENT') return '外部 MCP Agent';
    return '-';
  }

  function parseCaseIds(value: string) {
    return [
      ...new Set(
        value
          .split(/[\s,，]+/)
          .map((item) => item.trim())
          .filter(Boolean)
      ),
    ];
  }
  async function loadTriggers() {
    if (!appStore.currentProjectId) return;
    triggerLoading.value = true;
    triggerError.value = '';
    try {
      const triggerResult = await listAiTaskTriggers(appStore.currentProjectId);
      triggers.value = triggerResult || [];
    } catch (error: any) {
      triggerError.value = error?.message || '调度规则加载失败，请稍后重试';
    } finally {
      triggerLoading.value = false;
    }
  }
  async function loadExecutionProfiles() {
    if (!appStore.currentProjectId) return;
    const [environments, models, credentials, prompts] = await Promise.all([
      listAiEnvironmentProfiles(appStore.currentProjectId),
      listAiModelProfiles(appStore.currentProjectId),
      listAiCredentialReferences(appStore.currentProjectId),
      listAiPromptTemplateVersions(appStore.currentProjectId),
    ]);
    environmentProfiles.value = environments.filter((item) => item.enabled);
    modelProfiles.value = models.filter((item) => item.enabled && item.lastVerifyStatus === 'SUCCESS');
    credentialReferences.value = credentials.filter((item) => item.enabled && item.status === 'ACTIVE');
    promptTemplates.value = prompts.filter((item) => item.status === 'PUBLISHED');
  }
  function onEnvironmentChange() {
    const profile = environmentProfiles.value.find((item) => item.id === triggerForm.environmentProfileId);
    triggerForm.runnerType = profile?.runnerType || 'BROWSER';
    triggerForm.requiredCapabilities = profile?.requiredCapabilities?.length
      ? [...profile.requiredCapabilities]
      : ['BROWSER'];
    if (profile?.defaultCredentialReferenceId) triggerForm.credentialReferenceId = profile.defaultCredentialReferenceId;
  }
  async function loadLeases() {
    leaseLoading.value = true;
    pageError.value = '';
    try {
      leases.value = await getAiExecutionLeases(leaseStatus.value);
    } catch (reason: any) {
      pageError.value = reason?.message || '执行租约加载失败，请稍后重试';
    } finally {
      leaseLoading.value = false;
    }
  }
  function resetTriggerForm() {
    Object.assign(triggerForm, {
      id: '',
      name: '',
      triggerType: 'CRON',
      cronExpression: '0 0 2 * * ?',
      timezone: 'Asia/Shanghai',
      eventType: '',
      eventFilter: '',
      concurrencyPolicy: 'FORBID',
      missedPolicy: 'FIRE_ONCE',
      enabled: true,
      taskName: '',
      objective: '',
      environmentProfileId: '',
      credentialReferenceId: '',
      modelProfileId: '',
      promptTemplateId: '',
      runnerType: 'BROWSER',
      requiredCapabilities: ['BROWSER'],
      responsibleUserIds: '',
      caseIds: '',
    });
    draftAssetIds.value = [];
    pendingAssetIds.value = [];
  }
  function onAssetSelectionChange(ids: string[]) {
    draftAssetIds.value = ids;
  }
  function confirmAssetSelection() {
    pendingAssetIds.value = [...draftAssetIds.value];
    assetImportIdempotencyKey.value = crypto.randomUUID();
    return true;
  }

  async function importPendingAssets() {
    if (!pendingAssetIds.value.length) return;
    const job = await importCasesFromAssets({
      targetProjectId: appStore.currentProjectId,
      selectMode: 'CASE_IDS',
      ids: pendingAssetIds.value,
      conflictStrategy: 'SKIP',
      copyAttachments: true,
      idempotencyKey: assetImportIdempotencyKey.value,
    });
    await new Promise<void>((resolve, reject) => {
      const deadline = Date.now() + 5 * 60 * 1000;
      const timer = setInterval(async () => {
        try {
          const status = await getDefaultHubJob(job.jobId);
          if (status.status === 'SUCCESS' || status.status === 'PARTIAL_SUCCESS') {
            clearInterval(timer);
            resolve();
          } else if (status.status === 'FAILED') {
            clearInterval(timer);
            reject(new Error(status.errorMessage || '资产用例导入失败'));
          } else if (Date.now() >= deadline) {
            clearInterval(timer);
            reject(new Error('资产用例导入超时，任务仍可能在后台执行，请稍后重试'));
          }
        } catch (error) {
          clearInterval(timer);
          reject(error);
        }
      }, 800);
    });
    const result = await getCaseAssetImportResult(job.jobId);
    const targetIds = result.filter((item) => item.targetCaseId).map((item) => item.targetCaseId as string);
    const failedIds = result.filter((item) => item.status === 'FAILED').map((item) => item.sourceCaseId);
    triggerForm.caseIds = [...new Set([...parseCaseIds(triggerForm.caseIds), ...targetIds])].join('\n');
    pendingAssetIds.value = failedIds;
    draftAssetIds.value = failedIds;
    if (failedIds.length) {
      assetImportIdempotencyKey.value = crypto.randomUUID();
      throw new Error(`有 ${failedIds.length} 条资产导入失败，成功项已保留；请重试失败项后再保存规则`);
    }
    pendingAssetIds.value = [];
    draftAssetIds.value = [];
    Message.success(`已导入 ${targetIds.length} 条项目用例，继续保存调度规则`);
  }
  function openTrigger(record?: AiTaskTrigger) {
    resetTriggerForm();
    if (record) {
      let template: Record<string, any> = {};
      try {
        template = JSON.parse(record.taskTemplate || '{}');
      } catch {
        template = {};
      }
      Object.assign(triggerForm, {
        id: record.id,
        name: record.name,
        triggerType: record.triggerType,
        cronExpression: record.cronExpression || '',
        timezone: record.timezone || 'Asia/Shanghai',
        eventType: record.eventType || '',
        eventFilter: record.eventFilter || '',
        concurrencyPolicy: record.concurrencyPolicy || 'FORBID',
        missedPolicy: record.missedPolicy || 'FIRE_ONCE',
        enabled: record.enabled,
        taskName: template.name || '',
        objective: template.objective || '',
        environmentProfileId: record.environmentProfileId || '',
        credentialReferenceId: record.credentialReferenceId || '',
        modelProfileId: record.modelProfileId || '',
        promptTemplateId: record.promptTemplateId || '',
        runnerType: record.runnerType || 'BROWSER',
        requiredCapabilities: record.requiredCapabilities ? JSON.parse(record.requiredCapabilities) : [],
        responsibleUserIds: record.responsibleUserIds ? JSON.parse(record.responsibleUserIds).join('\n') : '',
        caseIds: (template.caseIds || []).join('\n'),
      });
    }
    triggerVisible.value = true;
  }
  async function saveTrigger(done: (closed: boolean) => void) {
    if (pendingAssetIds.value.length) {
      try {
        triggerSaving.value = true;
        await importPendingAssets();
      } catch (error: any) {
        Message.error(error?.message || '资产用例导入失败，未保存调度规则');
        triggerSaving.value = false;
        done(false);
        return;
      }
    }
    const caseIds = parseCaseIds(triggerForm.caseIds);
    const responsibleUserIds = parseCaseIds(triggerForm.responsibleUserIds);
    if (
      !triggerForm.name.trim() ||
      !triggerForm.taskName.trim() ||
      caseIds.length === 0 ||
      !triggerForm.environmentProfileId ||
      !triggerForm.modelProfileId ||
      !triggerForm.promptTemplateId ||
      responsibleUserIds.length !== 3
    ) {
      Message.warning('请填写规则名称、任务名称和至少一个用例 ID');
      done(false);
      return;
    }
    if (triggerForm.triggerType === 'CRON' && !triggerForm.cronExpression.trim()) {
      Message.warning('Cron 表达式不能为空');
      done(false);
      return;
    }
    if (triggerForm.triggerType === 'EVENT' && !triggerForm.eventType.trim()) {
      Message.warning('事件类型不能为空');
      done(false);
      return;
    }
    triggerSaving.value = true;
    try {
      const data = {
        projectId: appStore.currentProjectId,
        name: triggerForm.name.trim(),
        triggerType: triggerForm.triggerType,
        cronExpression: triggerForm.cronExpression.trim() || undefined,
        timezone: triggerForm.timezone.trim() || 'Asia/Shanghai',
        eventType: triggerForm.eventType.trim() || undefined,
        eventFilter: triggerForm.eventFilter.trim() || undefined,
        concurrencyPolicy: triggerForm.concurrencyPolicy,
        missedPolicy: triggerForm.missedPolicy,
        enabled: triggerForm.enabled,
        environmentProfileId: triggerForm.environmentProfileId,
        credentialReferenceId: triggerForm.credentialReferenceId || undefined,
        modelProfileId: triggerForm.modelProfileId,
        promptTemplateId: triggerForm.promptTemplateId,
        runnerType: triggerForm.runnerType,
        requiredCapabilities: triggerForm.requiredCapabilities,
        responsibleUserIds,
        policy: { riskActionPolicy: 'SKIP_AND_REVIEW', scopeExpansionLimit: 0.15 },
        evidencePolicy: { screenshot: 'ON_FAILURE', redactSensitive: true },
        notificationPolicy: { channels: ['IN_APP'], firstResponseWins: true },
        taskTemplate: {
          projectId: appStore.currentProjectId,
          name: triggerForm.taskName.trim(),
          objective: triggerForm.objective.trim() || undefined,
          caseIds,
          environmentProfileId: triggerForm.environmentProfileId,
          credentialReferenceId: triggerForm.credentialReferenceId || undefined,
          modelProfileId: triggerForm.modelProfileId,
          preflightId: '',
          confirmed: true,
        },
      };
      const preview = await preflightAiExecution({
        projectId: appStore.currentProjectId,
        caseIds,
        environmentProfileId: triggerForm.environmentProfileId,
        credentialReferenceId: triggerForm.credentialReferenceId || undefined,
        modelProfileId: triggerForm.modelProfileId,
        promptTemplateId: triggerForm.promptTemplateId,
        runnerType: triggerForm.runnerType,
        requiredCapabilities: triggerForm.requiredCapabilities,
        policy: data.policy,
        taskOrigin: 'PLATFORM_SCHEDULED',
        responsibleUserIds,
      });
      if (preview.status !== 'PASSED') {
        Message.error(
          `Preflight 未通过：${preview.blockedReason || preview.blockedDetail || '配置不可执行'}；traceId=${
            preview.traceId
          }`
        );
        done(false);
        return;
      }
      const result = triggerForm.id ? await updateAiTaskTrigger(triggerForm.id, data) : await createAiTaskTrigger(data);
      if (result.webhookSecret) {
        oneTimeSecret.value = result.webhookSecret;
        secretVisible.value = true;
      }
      Message.success(triggerForm.id ? '调度规则已更新' : '调度规则已创建');
      await loadTriggers();
      done(true);
    } catch {
      done(false);
    } finally {
      triggerSaving.value = false;
    }
  }
  async function toggleTrigger(record: AiTaskTrigger) {
    let template: any;
    try {
      template = JSON.parse(record.taskTemplate);
    } catch {
      Message.error('任务模板损坏，无法修改');
      return false;
    }
    await updateAiTaskTrigger(record.id, {
      projectId: record.projectId,
      name: record.name,
      triggerType: record.triggerType,
      cronExpression: record.cronExpression,
      timezone: record.timezone,
      eventType: record.eventType,
      eventFilter: record.eventFilter,
      concurrencyPolicy: record.concurrencyPolicy,
      missedPolicy: record.missedPolicy,
      enabled: !record.enabled,
      environmentProfileId: record.environmentProfileId,
      credentialReferenceId: record.credentialReferenceId,
      modelProfileId: record.modelProfileId,
      promptTemplateId: record.promptTemplateId,
      runnerType: record.runnerType,
      requiredCapabilities: record.requiredCapabilities ? JSON.parse(record.requiredCapabilities) : [],
      responsibleUserIds: record.responsibleUserIds ? JSON.parse(record.responsibleUserIds) : [],
      taskTemplate: template,
    });
    await loadTriggers();
    return true;
  }
  async function fireTrigger(record: AiTaskTrigger) {
    const result = await fireAiTaskTrigger(record.id);
    Message.success(result.status === 'CREATED' ? `任务已创建：${result.taskId}` : `触发结果：${result.status}`);
    await loadTriggers();
  }
  async function rotateSecret(record: AiTaskTrigger) {
    const result = await rotateAiTaskTriggerSecret(record.id);
    oneTimeSecret.value = result.webhookSecret || '';
    secretVisible.value = true;
  }
  async function openHistory(record: AiTaskTrigger) {
    historyVisible.value = true;
    historyLoading.value = true;
    historyError.value = '';
    try {
      histories.value = (await listAiTaskTriggerHistory(record.id)) || [];
    } catch (error: any) {
      historyError.value = error?.message || '触发历史加载失败，请稍后重试';
    } finally {
      historyLoading.value = false;
    }
  }
  watch(
    () => route.query.tab,
    (value) => {
      queueTab.value = value === 'triggers' ? 'triggers' : 'leases';
    }
  );
  watch(queueTab, (value) => {
    if (value === 'triggers') loadTriggers();
    else if (value === 'leases') loadLeases();
  });
  watch(
    () => appStore.currentProjectId,
    () => {
      loadLeases();
      loadTriggers();
      loadExecutionProfiles();
    }
  );
  onMounted(() => {
    loadLeases();
    loadTriggers();
    loadExecutionProfiles();
  });
</script>
