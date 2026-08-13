<template>
  <div class="automation-execution-page">
    <div class="automation-execution-header">
      <div>
        <div class="text-[18px] font-medium text-[var(--color-text-1)]">
          {{ t('menu.bugManagement.automationExecution') }}
        </div>
        <div class="mt-[4px] text-[12px] text-[var(--color-text-3)]">
          {{ t('bugManagement.automationExecution.subtitle') }}
        </div>
      </div>
      <div class="flex flex-wrap gap-[8px]">
        <a-button v-if="executionTaskId" @click="backToTaskList">返回任务列表</a-button>
        <a-button @click="refreshAll">{{ t('common.refresh') }}</a-button>
        <a-button
          v-if="task?.confirmRequired || task?.status === 'WAITING_CONFIRMATION'"
          type="primary"
          :loading="actionLoading"
          @click="handleConfirm"
        >
          {{ t('bugManagement.automationExecution.confirm') }}
        </a-button>
        <a-button
          v-if="task?.status === 'WAITING_LOGIN' || task?.status === 'PAUSED'"
          type="primary"
          :loading="actionLoading"
          @click="handleLoginReady"
        >
          {{ t('bugManagement.automationExecution.loginReady') }}
        </a-button>
        <a-button :disabled="!canPause" :loading="actionLoading" @click="handlePause">
          {{ t('bugManagement.automationExecution.pause') }}
        </a-button>
        <a-button status="danger" :disabled="!canCancel" :loading="actionLoading" @click="handleCancel">
          {{ t('bugManagement.automationExecution.stop') }}
        </a-button>
        <a-button :disabled="!canRetry" :loading="actionLoading" @click="handleRetry">
          {{ t('bugManagement.automationExecution.retryFailed') }}
        </a-button>
      </div>
    </div>

    <section v-if="!executionTaskId" class="task-center-panel">
      <div class="mb-[12px] flex flex-wrap items-center gap-[8px]">
        <a-input-search
          v-model:model-value="taskSearch.keyword"
          class="w-[260px]"
          allow-clear
          placeholder="搜索任务名称、目标或 ID"
          @search="reloadTaskList"
          @press-enter="reloadTaskList"
        />
        <a-select v-model:model-value="taskSearch.status" class="w-[160px]" allow-clear placeholder="运行状态">
          <a-option v-for="status in taskStatuses" :key="status" :value="status">{{ status }}</a-option>
        </a-select>
        <a-select v-model:model-value="taskSearch.verdict" class="w-[170px]" allow-clear placeholder="业务结论">
          <a-option v-for="verdict in taskVerdicts" :key="verdict" :value="verdict">{{ verdict }}</a-option>
        </a-select>
        <a-select v-model:model-value="taskSearch.executionMode" class="w-[130px]" allow-clear placeholder="执行方式">
          <a-option value="RUNNER">RUNNER</a-option>
          <a-option value="AGENT">AGENT</a-option>
        </a-select>
        <a-button type="primary" :loading="taskListLoading" @click="reloadTaskList">查询</a-button>
      </div>
      <a-table
        :data="taskList"
        :loading="taskListLoading"
        :pagination="false"
        row-key="id"
        @row-click="openTask"
      >
        <template #columns>
          <a-table-column title="任务" :width="280">
            <template #cell="{ record }">
              <div class="font-medium">{{ record.name || record.id }}</div>
              <div class="mt-[2px] truncate text-[12px] text-[var(--color-text-3)]">{{ record.objective || record.id }}</div>
            </template>
          </a-table-column>
          <a-table-column title="运行状态" data-index="status" :width="150" />
          <a-table-column title="业务结论" :width="170">
            <template #cell="{ record }">
              <a-tag :color="verdictColor(record.verdict)">{{ record.verdict || '待判定' }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="执行器" :width="150">
            <template #cell="{ record }">{{ record.executionMode }} / {{ record.agentType || record.runnerId || '-' }}</template>
          </a-table-column>
          <a-table-column title="进度" :width="130">
            <template #cell="{ record }">{{ (record.successCount || 0) + (record.failedCount || 0) }}/{{ record.totalCount || 0 }}</template>
          </a-table-column>
          <a-table-column title="尝试" :width="90">
            <template #cell="{ record }">{{ record.attemptCount || 0 }}/{{ record.maxAttempts || 3 }}</template>
          </a-table-column>
          <a-table-column title="创建时间" :width="180">
            <template #cell="{ record }">{{ formatTime(record.createTime) }}</template>
          </a-table-column>
        </template>
      </a-table>
      <div class="mt-[12px] flex justify-end">
        <a-pagination
          v-model:current="taskSearch.current"
          v-model:page-size="taskSearch.pageSize"
          :total="taskListTotal"
          show-total
          show-page-size
          @change="reloadTaskList"
          @page-size-change="reloadTaskList"
        />
      </div>
    </section>

    <div v-else class="automation-execution-workbench">
      <section class="automation-panel">
        <div class="panel-title">{{ t('bugManagement.automationExecution.conversation') }}</div>
        <a-form :model="draftForm" layout="vertical" class="mb-[12px]">
          <a-form-item :label="t('bugManagement.automationExecution.executionMode')">
            <a-radio-group v-model:model-value="draftForm.executionMode" type="button">
              <a-radio value="RUNNER">{{ t('bugManagement.automationExecution.executionModeRunner') }}</a-radio>
              <a-radio value="AGENT">{{ t('bugManagement.automationExecution.executionModeAgent') }}</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item
            v-if="draftForm.executionMode === 'AGENT'"
            :label="t('bugManagement.automationExecution.executionAgent')"
          >
            <a-select
              v-model:model-value="draftForm.agentType"
              :options="agentSelectOptions"
              :placeholder="t('bugManagement.automationExecution.executionAgentPlaceholder')"
            />
          </a-form-item>
          <a-form-item
            v-if="draftForm.executionMode === 'RUNNER'"
            :label="t('bugManagement.automationExecution.model')"
          >
            <a-select
              v-model:model-value="chatModelId"
              :options="modelOptions"
              allow-search
              :placeholder="t('bugManagement.automationExecution.modelPlaceholder')"
            />
          </a-form-item>
          <a-form-item :label="t('bugManagement.automationExecution.targetUrl')">
            <a-input v-model:model-value="draftForm.targetUrl" :placeholder="'https://...'" :max-length="500" />
          </a-form-item>
          <a-form-item :label="t('bugManagement.automationExecution.environment')">
            <a-input v-model:model-value="draftForm.environmentId" :max-length="100" />
          </a-form-item>
        </a-form>
        <div class="message-list">
          <div v-for="message in messages" :key="message.id" class="message-item" :class="message.role">
            <div class="message-role">{{ message.role === 'user' ? 'User' : 'AI' }}</div>
            <div class="message-content">{{ message.content }}</div>
          </div>
          <a-empty v-if="messages.length === 0" :description="t('bugManagement.automationExecution.noMessage')" />
        </div>
        <a-textarea
          v-model:model-value="prompt"
          :placeholder="t('bugManagement.automationExecution.inputPlaceholder')"
          :auto-size="{ minRows: 3, maxRows: 6 }"
          :max-length="2000"
          show-word-limit
        />
        <div class="mt-[12px] flex flex-wrap justify-end gap-[8px]">
          <a-button :loading="resolveLoading" :disabled="!prompt.trim()" @click="sendPrompt">
            {{ t('bugManagement.automationExecution.resolveScope') }}
          </a-button>
          <a-button
            type="primary"
            :loading="createLoading"
            :disabled="
              !canCreateFromResolve ||
              (draftForm.executionMode === 'RUNNER' && !chatModelId) ||
              !draftForm.targetUrl ||
              !draftForm.environmentId ||
              (draftForm.executionMode === 'AGENT' && !selectedAgentConfigured)
            "
            @click="createFromResolve"
          >
            {{ t('bugManagement.automationExecution.createTask') }}
          </a-button>
        </div>

        <div v-if="resolveResult" class="scope-preview mt-[12px]">
          <div class="panel-subtitle mb-[8px]">{{ t('bugManagement.automationExecution.scopePreview') }}</div>
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item :label="t('bugManagement.automationExecution.status')">
              {{ resolveResult.status || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.total')">
              {{ resolveResult.total ?? 0 }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.estimatedMinutes')">
              {{ resolveResult.estimatedMinutes ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.plan')">
              {{ resolveResult.testPlanId || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.confirmRequired')">
              {{ resolveResult.confirmationRequired ? resolveResult.confirmationReason : '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.parseConfidence')">
              {{ resolveResult.parseConfidence ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.snapshotHash')">
              {{ resolveResult.caseSnapshotHash || '-' }}
            </a-descriptions-item>
          </a-descriptions>
          <div
            v-if="(resolveResult.matchedReasons || []).length"
            class="mt-[8px] text-[12px] text-[var(--color-text-3)]"
          >
            {{ (resolveResult.matchedReasons || []).join('；') }}
          </div>
          <a-alert
            v-if="resolveResult.highRisk"
            class="mt-[8px]"
            type="warning"
            :content="
              t('bugManagement.automationExecution.highRiskTip', {
                keywords: (resolveResult.highRiskSignals || []).join(', '),
              })
            "
          />
          <a-checkbox v-if="resolveResult.confirmationRequired" v-model:model-value="resolveConfirmed" class="mt-[8px]">
            {{ t('bugManagement.automationExecution.confirmScopeCheckbox') }}
          </a-checkbox>
          <a-table
            v-if="(resolveResult.cases || []).length"
            class="mt-[8px]"
            size="small"
            :pagination="false"
            :scroll="{ y: 160 }"
            :data="resolveResult.cases || []"
            row-key="caseId"
          >
            <template #columns>
              <a-table-column title="ID" data-index="num" :width="80" />
              <a-table-column :title="t('bugManagement.automationExecution.caseName')" data-index="name" />
            </template>
          </a-table>
          <a-table
            v-if="(resolveResult.candidatePlans || []).length"
            class="mt-[8px]"
            size="small"
            :pagination="false"
            :data="resolveResult.candidatePlans || []"
            row-key="id"
          >
            <template #columns>
              <a-table-column :title="t('bugManagement.automationExecution.plan')" data-index="name" />
              <a-table-column :title="t('bugManagement.automationExecution.status')" data-index="status" :width="120" />
              <a-table-column
                :title="t('bugManagement.automationExecution.total')"
                data-index="associatedCaseCount"
                :width="100"
              />
            </template>
          </a-table>
        </div>
        <a-alert class="mt-[12px]" type="info" show-icon>
          {{ t('bugManagement.automationExecution.scopeTip') }}
        </a-alert>
      </section>

      <section class="automation-panel">
        <div class="panel-title">{{ t('bugManagement.automationExecution.executionPanel') }}</div>
        <a-empty v-if="!executionTaskId" :description="t('bugManagement.automationExecution.empty')" />
        <a-spin v-else :loading="loading" class="w-full">
          <a-descriptions v-if="task" :column="2" size="small" bordered>
            <a-descriptions-item :label="t('bugManagement.automationExecution.taskId')">
              {{ task.id }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.status')">
              <a-tag>{{ task.status }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="业务结论">
              <a-tag :color="verdictColor(task.verdict)">{{ task.verdict || '待判定' }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="结论说明">
              {{ task.verdictReason || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.project')">
              {{ task.projectId }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.plan')">
              {{ task.testPlanId || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.model')">
              {{ task.providerId || chatModelId || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.executionMode')">
              {{ task.executionMode || 'RUNNER' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.executionAgent')">
              {{ task.agentType || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.startTime')">
              {{ formatTime(task.createTime) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.writebackStatus')">
              {{ task.writebackStatus || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.artifactStatus')">
              {{ task.artifactStatus || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.success')">
              {{ task.successCount || 0 }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.failed')">
              {{ task.failedCount || 0 }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.blocked')">
              {{ task.blockedCount || 0 }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.unexecuted')">
              {{ task.unexecutedCount || 0 }}
            </a-descriptions-item>
          </a-descriptions>

          <a-alert
            v-if="task?.confirmRequired || task?.status === 'WAITING_CONFIRMATION'"
            class="mt-[12px]"
            type="warning"
            :content="task.confirmationReason || t('bugManagement.automationExecution.confirmRequired')"
          />
          <a-alert
            v-if="task?.status === 'WAITING_LOGIN'"
            class="mt-[12px]"
            type="warning"
            :content="t('bugManagement.automationExecution.waitingLogin')"
          />
          <a-alert
            v-if="task?.status === 'PARTIAL_SUCCESS'"
            class="mt-[12px]"
            type="warning"
            :content="t('bugManagement.automationExecution.partialSuccessTip')"
          />
          <a-alert class="mt-[12px]" type="info" :content="t('bugManagement.automationExecution.runnerPlaceholder')" />

          <div class="mt-[12px] flex items-center justify-between">
            <div class="panel-subtitle">{{ t('bugManagement.automationExecution.caseProgress') }}</div>
          </div>
          <a-table
            v-if="task"
            class="mt-[8px]"
            :data="task.cases || []"
            :pagination="false"
            :scroll="{ y: 220 }"
            row-key="caseId"
            size="small"
          >
            <template #columns>
              <a-table-column title="ID" data-index="caseNum" :width="90" />
              <a-table-column :title="t('bugManagement.automationExecution.caseName')" data-index="caseName" />
              <a-table-column :title="t('bugManagement.automationExecution.status')" data-index="status" :width="120" />
              <a-table-column :title="t('bugManagement.automationExecution.result')" data-index="result" :width="100" />
              <a-table-column :title="t('bugManagement.automationExecution.error')" data-index="errorMessage" />
            </template>
          </a-table>

          <div class="panel-subtitle mt-[12px]">{{ t('bugManagement.automationExecution.evidence') }}</div>
          <div v-if="artifacts.length" class="evidence-list mt-[8px]">
            <div v-for="artifact in artifacts" :key="artifact.id" class="evidence-item">
              <a-image :src="artifact.downloadPath" width="140" height="88" fit="cover" />
              <div class="mt-[4px] truncate text-[12px]">{{ artifact.purpose }} · {{ artifact.fileName }}</div>
              <div class="truncate text-[11px] text-[var(--color-text-3)]">{{ artifact.sha256 }}</div>
            </div>
          </div>
          <a-empty v-else :description="t('bugManagement.automationExecution.noEvidence')" />

          <div class="mt-[12px] flex items-center justify-between gap-[8px]">
            <div class="panel-subtitle">{{ t('bugManagement.automationExecution.eventLog') }}</div>
            <div class="flex items-center gap-[8px]">
              <a-select v-model:model-value="eventLevel" size="mini" class="w-[120px]" @change="resetEvents">
                <a-option value="ALL">ALL</a-option>
                <a-option value="INFO">INFO</a-option>
                <a-option value="WARN">WARN</a-option>
                <a-option value="ERROR">ERROR</a-option>
              </a-select>
              <a-button size="mini" @click="downloadEvents">
                {{ t('bugManagement.automationExecution.downloadLog') }}
              </a-button>
            </div>
          </div>
          <div class="event-list">
            <div v-for="event in filteredEvents" :key="`${event.sequence}-${event.id}`" class="event-item">
              <a-tag size="small" :color="eventLevelColor(event.level)">{{ event.level }}</a-tag>
              <span class="event-time">{{ formatTime(event.eventTime) }}</span>
              <span class="event-type">{{ event.eventType }}</span>
              <span class="event-message">{{ event.message }}</span>
            </div>
            <a-empty v-if="filteredEvents.length === 0" :description="t('bugManagement.automationExecution.noEvent')" />
          </div>
        </a-spin>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { Message, type TableData } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import {
    type AiExecutionAgentOption,
    type AiExecutionAgentType,
    type AiExecutionArtifact,
    type AiExecutionEvent,
    type AiExecutionMode,
    type AiExecutionResolveResult,
    type AiExecutionTask,
    cancelAiExecutionTask,
    confirmAiExecutionTask,
    createAiExecutionTask,
    getAiExecutionAgents,
    getAiExecutionArtifacts,
    getAiExecutionEvents,
    getAiExecutionTask,
    loginReadyAiExecutionTask,
    pauseAiExecutionTask,
    resolveAiExecutionScope,
    retryAiExecutionTask,
    searchAiExecutionTasks,
  } from '@/api/modules/ai-execution';
  import { useI18n } from '@/hooks/useI18n';
  import { useAppStore } from '@/store';
  import useAIStore from '@/store/modules/setting/ai';

  const route = useRoute();
  const router = useRouter();
  const { t } = useI18n();
  const appStore = useAppStore();
  const aiStore = useAIStore();

  const loading = ref(false);
  const actionLoading = ref(false);
  const resolveLoading = ref(false);
  const createLoading = ref(false);
  const task = ref<AiExecutionTask>();
  const taskListLoading = ref(false);
  const taskList = ref<AiExecutionTask[]>([]);
  const taskListTotal = ref(0);
  const taskSearch = reactive({
    keyword: '',
    status: undefined as string | undefined,
    verdict: undefined as string | undefined,
    executionMode: undefined as AiExecutionMode | undefined,
    current: 1,
    pageSize: 20,
  });
  const taskStatuses = [
    'WAITING_CONFIRMATION',
    'QUEUED',
    'PREPARING_BROWSER',
    'WAITING_LOGIN',
    'WAITING_HUMAN',
    'RUNNING',
    'PAUSED',
    'WRITING_BACK',
    'SUCCESS',
    'PARTIAL_SUCCESS',
    'FAILED',
    'CANCELED',
  ];
  const taskVerdicts = [
    'PASSED',
    'PRODUCT_FAILED',
    'ENV_FAILED',
    'DATA_FAILED',
    'AGENT_FAILED',
    'BLOCKED',
    'INCONCLUSIVE',
  ];
  const events = ref<AiExecutionEvent[]>([]);
  const artifacts = ref<AiExecutionArtifact[]>([]);
  const agentOptions = ref<AiExecutionAgentOption[]>([]);
  const eventCursor = ref(0);
  const eventLevel = ref('ALL');
  const prompt = ref('');
  const draftForm = reactive({
    executionMode: 'RUNNER' as AiExecutionMode,
    agentType: undefined as AiExecutionAgentType | undefined,
    targetUrl: '',
    environmentId: '',
  });
  const resolveResult = ref<AiExecutionResolveResult>();
  const resolveConfirmed = ref(false);
  const chatModelId = ref(localStorage.getItem('aiChatModel') || '');
  const messages = ref<Array<{ id: string; role: 'user' | 'assistant'; content: string }>>([]);
  const lastResolvedPrompt = ref('');
  let pollTimer: number | undefined;

  const executionTaskId = computed(() => (route.query.executionTaskId as string | undefined) || undefined);
  const modelOptions = computed(() =>
    (aiStore.aiSourceNameList || []).map((item) => ({
      label: item.name,
      value: item.id,
    }))
  );
  const agentSelectOptions = computed(() =>
    agentOptions.value.map((item) => ({
      label: item.configured
        ? item.name
        : `${item.name} (${t('bugManagement.automationExecution.agentNotConfigured')})`,
      value: item.name.toUpperCase() as AiExecutionAgentType,
      disabled: !item.configured,
    }))
  );
  const selectedAgentConfigured = computed(
    () =>
      draftForm.executionMode !== 'AGENT' ||
      agentOptions.value.some((item) => item.configured && item.name.toUpperCase() === draftForm.agentType)
  );
  const filteredEvents = computed(() =>
    events.value.filter((item) => eventLevel.value === 'ALL' || item.level === eventLevel.value)
  );
  const canCancel = computed(
    () => !!task.value && !['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELED', 'EXPIRED'].includes(task.value.status)
  );
  const canPause = computed(
    () => !!task.value && ['RUNNING', 'WAITING_LOGIN', 'PREPARING_BROWSER'].includes(task.value.status)
  );
  const canRetry = computed(
    () => !!task.value && ((task.value.failedCount || 0) > 0 || (task.value.blockedCount || 0) > 0)
  );
  const canCreateFromResolve = computed(() => {
    if (!resolveResult.value?.executable) {
      return false;
    }
    if ((resolveResult.value.candidatePlans || []).length > 1 && !resolveResult.value.testPlanId) {
      return false;
    }
    if (resolveResult.value.confirmationRequired && !resolveConfirmed.value) {
      return false;
    }
    if (resolveResult.value.testPlanId) {
      return true;
    }
    if ((resolveResult.value.cases || []).length > 0) {
      return true;
    }
    return (resolveResult.value.total || 0) > 0 && !!resolveResult.value.confirmationRequired;
  });

  function formatTime(value?: number) {
    if (!value) {
      return '-';
    }
    return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
  }

  function eventLevelColor(level?: string) {
    if (level === 'ERROR') return 'red';
    if (level === 'WARN') return 'orange';
    return 'blue';
  }

  function verdictColor(verdict?: string) {
    if (verdict === 'PASSED') return 'green';
    if (verdict === 'PRODUCT_FAILED') return 'red';
    if (verdict === 'BLOCKED' || verdict === 'INCONCLUSIVE') return 'orange';
    if (verdict) return 'purple';
    return 'gray';
  }

  async function reloadTaskList() {
    if (!appStore.currentProjectId || executionTaskId.value) return;
    taskListLoading.value = true;
    try {
      const response = await searchAiExecutionTasks({
        projectId: appStore.currentProjectId,
        keyword: taskSearch.keyword.trim() || undefined,
        status: taskSearch.status,
        verdict: taskSearch.verdict,
        executionMode: taskSearch.executionMode,
        current: taskSearch.current,
        pageSize: taskSearch.pageSize,
      });
      taskList.value = response.items || [];
      taskListTotal.value = response.total || 0;
    } finally {
      taskListLoading.value = false;
    }
  }

  async function openTask(record: TableData) {
    await router.push({ query: { ...route.query, executionTaskId: String(record.id) } });
  }

  async function backToTaskList() {
    const query = { ...route.query };
    delete query.executionTaskId;
    await router.push({ query });
  }

  async function loadTask() {
    if (!executionTaskId.value) {
      task.value = undefined;
      return;
    }
    loading.value = true;
    try {
      task.value = await getAiExecutionTask(executionTaskId.value);
      if (task.value.providerId) {
        chatModelId.value = task.value.providerId;
      }
    } catch {
      Message.error(t('bugManagement.automationExecution.loadFailed'));
    } finally {
      loading.value = false;
    }
  }

  async function loadEvents(reset = false) {
    if (!executionTaskId.value) {
      events.value = [];
      eventCursor.value = 0;
      return;
    }
    if (reset) {
      events.value = [];
      eventCursor.value = 0;
    }
    const response = await getAiExecutionEvents(executionTaskId.value, {
      cursor: eventCursor.value,
      limit: 100,
    });
    const nextEvents = response.events || [];
    if (nextEvents.length) {
      events.value = [...events.value, ...nextEvents];
      eventCursor.value = response.cursor || nextEvents[nextEvents.length - 1].sequence;
    }
  }

  async function loadArtifacts() {
    artifacts.value = executionTaskId.value ? await getAiExecutionArtifacts(executionTaskId.value) : [];
  }

  async function refreshAll() {
    if (!executionTaskId.value) {
      await reloadTaskList();
      return;
    }
    await loadTask();
    await loadEvents(true);
    await loadArtifacts();
  }

  function resetEvents() {
    // filter only; keep loaded events
  }

  async function loadExecutionAgents(projectId?: string) {
    if (!projectId) {
      agentOptions.value = [];
      draftForm.agentType = undefined;
      return;
    }
    try {
      agentOptions.value = await getAiExecutionAgents(projectId);
      if (
        draftForm.agentType &&
        !agentOptions.value.some((item) => item.configured && item.name.toUpperCase() === draftForm.agentType)
      ) {
        draftForm.agentType = undefined;
      }
    } catch {
      agentOptions.value = [];
      draftForm.agentType = undefined;
    }
  }

  async function sendPrompt() {
    const content = prompt.value.trim();
    if (!content) {
      return;
    }
    if (!appStore.currentProjectId) {
      Message.warning(t('bugManagement.automationExecution.projectRequired'));
      return;
    }
    messages.value.push({ id: `${Date.now()}_u`, role: 'user', content });
    lastResolvedPrompt.value = content;
    prompt.value = '';
    resolveLoading.value = true;
    try {
      const result = await resolveAiExecutionScope({
        projectId: appStore.currentProjectId,
        query: content,
      });
      resolveResult.value = result;
      resolveConfirmed.value = false;
      const summary = [
        result.message || t('bugManagement.automationExecution.resolveDone'),
        `total=${result.total ?? 0}`,
        result.testPlanId ? `plan=${result.testPlanId}` : 'plan=none',
        result.confirmationRequired ? `confirm=${result.confirmationReason}` : 'confirm=no',
        result.highRisk ? `highRisk=${(result.highRiskSignals || []).join('/')}` : '',
      ]
        .filter(Boolean)
        .join('；');
      messages.value.push({ id: `${Date.now()}_a`, role: 'assistant', content: summary });
      if (chatModelId.value) {
        localStorage.setItem('aiChatModel', chatModelId.value);
      }
    } catch (error: any) {
      messages.value.push({
        id: `${Date.now()}_a`,
        role: 'assistant',
        content: error?.message || t('bugManagement.automationExecution.resolveFailed'),
      });
    } finally {
      resolveLoading.value = false;
    }
  }

  async function createFromResolve() {
    if (!canCreateFromResolve.value || !resolveResult.value || !appStore.currentProjectId) {
      return;
    }
    createLoading.value = true;
    try {
      if (draftForm.executionMode === 'AGENT' && !selectedAgentConfigured.value) {
        Message.warning(t('bugManagement.automationExecution.executionAgentRequired'));
        return;
      }
      const hasPlan = !!resolveResult.value.testPlanId;
      const projectWide = !hasPlan && !!resolveResult.value.confirmationRequired;
      const caseIds =
        hasPlan || projectWide ? [] : (resolveResult.value.cases || []).map((item) => item.caseId).filter(Boolean);
      const taskCreated = await createAiExecutionTask({
        projectId: appStore.currentProjectId,
        testPlanId: resolveResult.value.testPlanId,
        caseIds,
        source: 'WORKBENCH',
        selectionMode: resolveResult.value.selectionMode || 'NATURAL_LANGUAGE',
        prompt: lastResolvedPrompt.value,
        resolvedFilter: resolveResult.value.resolvedFilter
          ? JSON.stringify(resolveResult.value.resolvedFilter)
          : undefined,
        policySnapshot: JSON.stringify({ screenshotMode: 'AFTER_STEP', fullPage: true }),
        confirmed: resolveResult.value.confirmationRequired ? resolveConfirmed.value : undefined,
        projectWide: projectWide || undefined,
        providerId: draftForm.executionMode === 'RUNNER' ? chatModelId.value || undefined : undefined,
        executionMode: draftForm.executionMode,
        agentType: draftForm.executionMode === 'AGENT' ? draftForm.agentType : undefined,
        environmentId: draftForm.environmentId || undefined,
        targetUrl: draftForm.targetUrl || undefined,
        browserType: 'chromium',
        loginMode: 'MANUAL',
        idempotencyKey: `workbench-${appStore.currentProjectId}-${Date.now()}`,
      });
      Message.success(t('bugManagement.automationExecution.taskCreated'));
      await router.replace({
        query: {
          ...route.query,
          executionTaskId: taskCreated.id,
        },
      });
    } finally {
      createLoading.value = false;
    }
  }

  async function handleConfirm() {
    if (!executionTaskId.value) return;
    actionLoading.value = true;
    try {
      task.value = await confirmAiExecutionTask(executionTaskId.value);
      await loadEvents();
      Message.success(t('common.success'));
    } finally {
      actionLoading.value = false;
    }
  }

  async function handleLoginReady() {
    if (!executionTaskId.value) return;
    actionLoading.value = true;
    try {
      task.value = await loginReadyAiExecutionTask(executionTaskId.value);
      await loadEvents();
      Message.success(t('common.success'));
    } finally {
      actionLoading.value = false;
    }
  }

  async function handlePause() {
    if (!executionTaskId.value) return;
    actionLoading.value = true;
    try {
      task.value = await pauseAiExecutionTask(executionTaskId.value);
      await loadEvents();
      Message.success(t('bugManagement.automationExecution.paused'));
    } finally {
      actionLoading.value = false;
    }
  }

  async function handleCancel() {
    if (!executionTaskId.value) return;
    actionLoading.value = true;
    try {
      task.value = await cancelAiExecutionTask(executionTaskId.value);
      await loadEvents();
      Message.success(t('bugManagement.automationExecution.canceled'));
    } finally {
      actionLoading.value = false;
    }
  }

  async function handleRetry() {
    if (!executionTaskId.value) return;
    actionLoading.value = true;
    try {
      task.value = await retryAiExecutionTask(executionTaskId.value);
      await loadEvents();
      Message.success(t('common.success'));
    } finally {
      actionLoading.value = false;
    }
  }

  function downloadEvents() {
    const lines = filteredEvents.value.map(
      (item) => `[${formatTime(item.eventTime)}] [${item.level}] [${item.eventType}] ${item.message || ''}`
    );
    const blob = new Blob([lines.join('\n')], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `ai-execution-${executionTaskId.value || 'log'}.txt`;
    link.click();
    URL.revokeObjectURL(url);
  }

  function startPolling() {
    window.clearInterval(pollTimer);
    if (!executionTaskId.value) {
      return;
    }
    pollTimer = window.setInterval(async () => {
      try {
        await loadTask();
        await loadEvents(false);
        await loadArtifacts();
      } catch {
        // ignore transient poll errors
      }
    }, 3000);
  }

  watch(
    executionTaskId,
    async () => {
      await refreshAll();
      startPolling();
    },
    { immediate: true }
  );

  watch(
    () => appStore.currentProjectId,
    (projectId) => loadExecutionAgents(projectId),
    { immediate: true }
  );

  watch(
    () => [taskSearch.status, taskSearch.verdict, taskSearch.executionMode],
    () => {
      taskSearch.current = 1;
      reloadTaskList();
    }
  );

  watch(
    () => modelOptions.value,
    (vals) => {
      if (!vals.length) return;
      if (!vals.some((item) => item.value === chatModelId.value)) {
        chatModelId.value = vals[0].value;
      }
    },
    { immediate: true }
  );

  onMounted(() => {
    aiStore.getAISourceNameList();
  });

  onBeforeUnmount(() => {
    window.clearInterval(pollTimer);
  });
</script>

<style scoped>
  .automation-execution-page {
    display: flex;
    padding: 16px;
    height: 100%;
    min-height: calc(100vh - 128px);
    background: var(--color-fill-2);
    flex-direction: column;
    gap: 12px;
  }
  .automation-execution-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 12px;
  }
  .automation-execution-workbench {
    display: grid;
    grid-template-columns: 35% 1fr;
    gap: 12px;
    min-height: 0;
    flex: 1;
  }
  .task-center-panel {
    overflow: auto;
    padding: 16px;
    min-height: 0;
    border-radius: 8px;
    background: var(--color-bg-1);
    flex: 1;
  }
  .automation-panel {
    display: flex;
    padding: 16px;
    min-height: 0;
    border-radius: 8px;
    background: var(--color-bg-1);
    flex-direction: column;
  }
  .panel-title {
    margin-bottom: 12px;
    font-size: 14px;
    font-weight: 600;
    color: var(--color-text-1);
  }
  .panel-subtitle {
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-2);
  }
  .message-list,
  .event-list {
    overflow: auto;
    margin-bottom: 12px;
    padding: 8px;
    min-height: 120px;
    max-height: 220px;
    border: 1px solid var(--color-border-2);
    border-radius: 6px;
    flex: 1;
  }
  .evidence-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 8px;
  }
  .evidence-item {
    padding: 8px;
    min-width: 0;
    border: 1px solid var(--color-border-2);
    border-radius: 4px;
  }
  .scope-preview {
    overflow: auto;
    max-height: 320px;
  }
  .message-item {
    margin-bottom: 8px;
    padding: 8px;
    border-radius: 6px;
    background: var(--color-fill-2);
  }
  .message-item.user {
    background: rgb(var(--primary-1));
  }
  .message-role {
    margin-bottom: 4px;
    font-size: 12px;
    color: var(--color-text-3);
  }
  .event-item {
    display: grid;
    align-items: start;
    padding: 6px 0;
    font-size: 12px;
    border-bottom: 1px solid var(--color-border-2);
    grid-template-columns: 56px 150px 140px 1fr;
    gap: 8px;
  }
  .event-message {
    word-break: break-word;
    color: var(--color-text-1);
  }
  @media (max-width: 1200px) {
    .automation-execution-workbench {
      grid-template-columns: 1fr;
    }
  }
</style>
