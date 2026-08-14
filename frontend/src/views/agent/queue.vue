<template>
  <AgentPage>
    <a-tabs v-model:active-key="queueTab" type="rounded" class="mb-4">
      <a-tab-pane key="tasks" title="任务队列" />
      <a-tab-pane key="leases" title="执行租约" />
      <a-tab-pane key="triggers" title="调度规则" />
    </a-tabs>
    <MsCard v-if="queueTab === 'tasks'" simple>
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <a-input-search
          v-model="query.keyword"
          class="w-[260px]"
          allow-clear
          placeholder="搜索任务名称或 ID"
          @search="search"
        />
        <a-select v-model="query.status" class="w-[180px]" allow-clear placeholder="运行状态" @change="search">
          <a-option v-for="status in statuses" :key="status" :value="status">{{ status }}</a-option>
        </a-select>
        <a-select v-model="query.executionMode" class="w-[140px]" allow-clear placeholder="执行模式" @change="search">
          <a-option value="RUNNER">RUNNER</a-option>
          <a-option value="AGENT">AGENT</a-option>
        </a-select>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
      <a-table
        :data="tasks"
        :loading="loading"
        row-key="id"
        :pagination="pagination"
        @page-change="changePage"
        @page-size-change="changePageSize"
      >
        <template #columns>
          <a-table-column title="任务" :width="300">
            <template #cell="{ record }">
              <a-link @click="openTask(record.id)">{{ record.name || record.id }}</a-link>
              <div class="mt-1 text-xs text-[var(--color-text-3)]">{{ record.id }}</div>
            </template>
          </a-table-column>
          <a-table-column title="运行状态" :width="170">
            <template #cell="{ record }"
              ><a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag></template
            >
          </a-table-column>
          <a-table-column title="业务结论" data-index="verdict" :width="160" />
          <a-table-column title="执行器" :width="150">
            <template #cell="{ record }"
              >{{ record.executionMode }}<span v-if="record.agentType"> / {{ record.agentType }}</span></template
            >
          </a-table-column>
          <a-table-column title="调度" data-index="dispatchMode" :width="90" />
          <a-table-column title="进度" :width="140">
            <template #cell="{ record }"
              >{{ record.totalCount - record.unexecutedCount }} / {{ record.totalCount }}</template
            >
          </a-table-column>
          <a-table-column title="尝试" :width="90">
            <template #cell="{ record }">{{ record.attemptCount || 0 }} / {{ record.maxAttempts || 0 }}</template>
          </a-table-column>
          <a-table-column title="更新时间" :width="180">
            <template #cell="{ record }">{{ formatTime(record.updateTime) }}</template>
          </a-table-column>
        </template>
      </a-table>
    </MsCard>
    <MsCard v-else-if="queueTab === 'leases'" simple>
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
          <a-table-column title="执行器"
            ><template #cell="{ record }"
              >{{ record.executorType || 'RUNNER' }} / {{ record.executorId || record.runnerId || '-' }}</template
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
    <MsCard v-else simple>
      <div class="mb-4 flex items-center justify-between">
        <div>
          <div class="text-base font-medium">调度规则</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]"
            >支持 Cron、签名 Webhook 和人工立即触发；Webhook 密钥仅在创建或轮换时展示一次。</div
          >
        </div>
        <div class="flex gap-2">
          <a-button :loading="triggerLoading" @click="loadTriggers">刷新</a-button>
          <a-button v-permission="['AI_EXECUTION:RUN']" type="primary" @click="openTrigger()">新建规则</a-button>
        </div>
      </div>
      <a-table :data="triggers" :loading="triggerLoading" :pagination="false" row-key="id">
        <template #columns>
          <a-table-column title="名称" data-index="name" />
          <a-table-column title="类型" data-index="triggerType" :width="100" />
          <a-table-column title="配置">
            <template #cell="{ record }">
              <span v-if="record.triggerType === 'CRON'">{{ record.cronExpression }} · {{ record.timezone }}</span>
              <span v-else-if="record.triggerType === 'EVENT'">{{ record.eventType }}</span>
              <span v-else>仅手动执行</span>
            </template>
          </a-table-column>
          <a-table-column title="启用" :width="80">
            <template #cell="{ record }"
              ><a-switch
                v-permission="['AI_EXECUTION:RUN']"
                :model-value="record.enabled"
                @change="toggleTrigger(record)"
            /></template>
          </a-table-column>
          <a-table-column title="下次执行" :width="180">
            <template #cell="{ record }">{{ formatTime(record.nextFireAt) }}</template>
          </a-table-column>
          <a-table-column title="最近结果" :width="130">
            <template #cell="{ record }"
              ><a-tag v-if="record.lastFireStatus" :color="record.lastFireStatus === 'CREATED' ? 'green' : 'red'">{{
                record.lastFireStatus
              }}</a-tag
              ><span v-else>-</span></template
            >
          </a-table-column>
          <a-table-column title="操作" :width="300">
            <template #cell="{ record }">
              <a-space>
                <a-link v-permission="['AI_EXECUTION:RUN']" @click="openTrigger(record)">编辑</a-link>
                <a-link v-permission="['AI_EXECUTION:RUN']" @click="fireTrigger(record)">立即执行</a-link>
                <a-link @click="openHistory(record)">历史</a-link>
                <a-link
                  v-if="record.triggerType === 'EVENT'"
                  v-permission="['AI_EXECUTION:RUN']"
                  @click="rotateSecret(record)"
                  >轮换密钥</a-link
                >
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </MsCard>

    <a-modal
      v-model:visible="triggerVisible"
      :title="triggerForm.id ? '编辑调度规则' : '新建调度规则'"
      :ok-loading="triggerSaving"
      width="720px"
      unmount-on-close
      @before-ok="saveTrigger"
    >
      <a-form :model="triggerForm" layout="vertical">
        <div class="grid grid-cols-2 gap-x-4">
          <a-form-item label="规则名称" required><a-input v-model="triggerForm.name" /></a-form-item>
          <a-form-item label="触发类型" required>
            <a-select v-model="triggerForm.triggerType" :disabled="!!triggerForm.id">
              <a-option value="CRON">Cron 定时</a-option><a-option value="EVENT">Webhook 事件</a-option
              ><a-option value="MANUAL">仅手动</a-option>
            </a-select>
          </a-form-item>
          <a-form-item v-if="triggerForm.triggerType === 'CRON'" label="Quartz Cron" required
            ><a-input v-model="triggerForm.cronExpression" placeholder="0 0 2 * * ?"
          /></a-form-item>
          <a-form-item v-if="triggerForm.triggerType === 'CRON'" label="时区" required
            ><a-input v-model="triggerForm.timezone"
          /></a-form-item>
          <a-form-item v-if="triggerForm.triggerType === 'EVENT'" label="事件类型" required
            ><a-input v-model="triggerForm.eventType" placeholder="CI_BUILD_COMPLETED"
          /></a-form-item>
          <a-form-item v-if="triggerForm.triggerType === 'EVENT'" label="事件过滤 JSON"
            ><a-input v-model="triggerForm.eventFilter" placeholder='{"branch":"main"}'
          /></a-form-item>
          <a-form-item label="并发策略"
            ><a-select v-model="triggerForm.concurrencyPolicy"
              ><a-option value="FORBID">禁止重叠</a-option><a-option value="ALLOW">允许并发</a-option></a-select
            ></a-form-item
          >
          <a-form-item label="错过执行"
            ><a-select v-model="triggerForm.missedPolicy"
              ><a-option value="FIRE_ONCE">补执行一次</a-option><a-option value="SKIP">跳过</a-option></a-select
            ></a-form-item
          >
          <a-form-item label="任务名称" required><a-input v-model="triggerForm.taskName" /></a-form-item>
          <a-form-item label="执行模式"
            ><a-select v-model="triggerForm.executionMode"
              ><a-option value="RUNNER">RUNNER</a-option><a-option value="AGENT">AGENT</a-option></a-select
            ></a-form-item
          >
          <a-form-item
            v-if="triggerForm.executionMode === 'AGENT'"
            label="Agent 类型"
            :extra="
              configuredAgents.length
                ? '仅展示当前项目已配置且当前用户可使用的 Agent'
                : '当前项目没有可用的 Agent，请先在能力与授权中完成配置'
            "
          >
            <a-select v-model="triggerForm.agentType" :disabled="!configuredAgents.length">
              <a-option v-for="agent in configuredAgents" :key="agent.name" :value="agent.name.toUpperCase()">
                {{ agent.name }}
              </a-option>
            </a-select>
          </a-form-item>
          <a-form-item label="目标地址"
            ><a-input v-model="triggerForm.targetUrl" placeholder="https://test.example.com"
          /></a-form-item>
        </div>
        <a-form-item label="用例 ID" required extra="多个 ID 用逗号或换行分隔；触发时会再次按项目权限和快照规则校验。"
          ><a-textarea v-model="triggerForm.caseIds" :auto-size="{ minRows: 3, maxRows: 6 }"
        /></a-form-item>
        <a-form-item label="任务目标"
          ><a-textarea v-model="triggerForm.objective" :auto-size="{ minRows: 2, maxRows: 4 }"
        /></a-form-item>
        <a-form-item label="启用"><a-switch v-model="triggerForm.enabled" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:visible="secretVisible" title="Webhook 密钥（仅展示一次）" :footer="false" :mask-closable="false">
      <a-alert type="warning" class="mb-3">请立即保存。关闭后平台不会再次返回明文密钥。</a-alert>
      <div class="break-all rounded bg-[var(--color-fill-2)] p-3 font-mono text-sm">{{ oneTimeSecret }}</div>
    </a-modal>

    <a-drawer v-model:visible="historyVisible" :width="720" title="触发历史">
      <a-table :data="histories" :loading="historyLoading" :pagination="false" row-key="id">
        <template #columns>
          <a-table-column title="触发时间" :width="180"
            ><template #cell="{ record }">{{ formatTime(record.fireTime) }}</template></a-table-column
          >
          <a-table-column title="状态" data-index="status" :width="110" />
          <a-table-column title="任务 ID" data-index="taskId" :width="220" />
          <a-table-column title="说明" data-index="message" />
        </template>
      </a-table>
    </a-drawer>
  </AgentPage>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import AgentPage from './components/AgentPage.vue';

  import type {
    AiExecutionAgentOption,
    AiExecutionMode,
    AiExecutionTask,
    AiRunnerLease,
    AiTaskTrigger,
    AiTaskTriggerHistory,
  } from '@/api/modules/ai-execution';
  import {
    createAiTaskTrigger,
    fireAiTaskTrigger,
    getAiExecutionAgents,
    getAiExecutionLeases,
    listAiTaskTriggerHistory,
    listAiTaskTriggers,
    rotateAiTaskTriggerSecret,
    searchAiExecutionTasks,
    updateAiTaskTrigger,
  } from '@/api/modules/ai-execution';
  import { useAppStore } from '@/store';

  const appStore = useAppStore();
  const router = useRouter();
  const loading = ref(false);
  const queueTab = ref('tasks');
  const tasks = ref<AiExecutionTask[]>([]);
  const leases = ref<AiRunnerLease[]>([]);
  const leaseStatus = ref<string>();
  const leaseLoading = ref(false);
  const total = ref(0);
  const statuses = [
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
    'EXPIRED',
  ];
  const query = reactive({
    keyword: '',
    status: undefined as string | undefined,
    executionMode: undefined as AiExecutionMode | undefined,
    current: 1,
    pageSize: 20,
  });
  const triggerLoading = ref(false);
  const triggerSaving = ref(false);
  const triggerVisible = ref(false);
  const secretVisible = ref(false);
  const oneTimeSecret = ref('');
  const triggers = ref<AiTaskTrigger[]>([]);
  const executionAgents = ref<AiExecutionAgentOption[]>([]);
  const configuredAgents = computed(() => executionAgents.value.filter((item) => item.configured));
  const histories = ref<AiTaskTriggerHistory[]>([]);
  const historyVisible = ref(false);
  const historyLoading = ref(false);
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
    executionMode: 'RUNNER' as AiExecutionMode,
    agentType: 'CODEX',
    targetUrl: '',
    caseIds: '',
  });
  const pagination = computed(() => ({
    current: query.current,
    pageSize: query.pageSize,
    total: total.value,
    showTotal: true,
    showPageSize: true,
  }));
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  function statusColor(status: string) {
    if (['SUCCESS', 'PARTIAL_SUCCESS'].includes(status)) {
      return 'green';
    }
    if (['FAILED', 'CANCELED', 'EXPIRED'].includes(status)) {
      return 'red';
    }
    if (['WAITING_LOGIN', 'WAITING_HUMAN', 'PAUSED'].includes(status)) {
      return 'orange';
    }
    return 'blue';
  }

  async function load() {
    if (!appStore.currentProjectId) return;
    loading.value = true;
    try {
      const result = await searchAiExecutionTasks({
        projectId: appStore.currentProjectId,
        ...query,
        keyword: query.keyword.trim() || undefined,
      });
      tasks.value = result.items || [];
      total.value = result.total || 0;
    } finally {
      loading.value = false;
    }
  }
  function search() {
    query.current = 1;
    load();
  }
  function changePage(current: number) {
    query.current = current;
    load();
  }
  function changePageSize(pageSize: number) {
    query.pageSize = pageSize;
    query.current = 1;
    load();
  }
  function openTask(id: string) {
    router.push({ path: '/case-management/automation-execution', query: { executionTaskId: id } });
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
    try {
      const [triggerResult, agentResult] = await Promise.all([
        listAiTaskTriggers(appStore.currentProjectId),
        getAiExecutionAgents(appStore.currentProjectId),
      ]);
      triggers.value = triggerResult || [];
      executionAgents.value = agentResult || [];
      if (!configuredAgents.value.some((item) => item.name.toUpperCase() === triggerForm.agentType)) {
        triggerForm.agentType = configuredAgents.value[0]?.name.toUpperCase() || '';
      }
    } finally {
      triggerLoading.value = false;
    }
  }
  async function loadLeases() {
    leaseLoading.value = true;
    try {
      leases.value = await getAiExecutionLeases(leaseStatus.value);
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
      executionMode: 'RUNNER',
      agentType: 'CODEX',
      targetUrl: '',
      caseIds: '',
    });
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
        executionMode: template.executionMode || 'RUNNER',
        agentType: template.agentType || 'CODEX',
        targetUrl: template.targetUrl || '',
        caseIds: (template.caseIds || []).join('\n'),
      });
    }
    triggerVisible.value = true;
  }
  async function saveTrigger(done: (closed: boolean) => void) {
    const caseIds = parseCaseIds(triggerForm.caseIds);
    if (!triggerForm.name.trim() || !triggerForm.taskName.trim() || caseIds.length === 0) {
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
    if (
      triggerForm.executionMode === 'AGENT' &&
      !configuredAgents.value.some((item) => item.name.toUpperCase() === triggerForm.agentType)
    ) {
      Message.warning('请选择当前项目可用的 Agent');
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
        taskTemplate: {
          projectId: appStore.currentProjectId,
          name: triggerForm.taskName.trim(),
          objective: triggerForm.objective.trim() || undefined,
          caseIds,
          targetUrl: triggerForm.targetUrl.trim() || undefined,
          executionMode: triggerForm.executionMode,
          agentType: triggerForm.executionMode === 'AGENT' ? (triggerForm.agentType as any) : undefined,
          confirmed: true,
        },
      };
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
    try {
      histories.value = (await listAiTaskTriggerHistory(record.id)) || [];
    } finally {
      historyLoading.value = false;
    }
  }
  watch(queueTab, (value) => {
    if (value === 'triggers') loadTriggers();
    else if (value === 'leases') loadLeases();
  });
  watch(
    () => appStore.currentProjectId,
    () => {
      query.current = 1;
      load();
      loadTriggers();
    }
  );
  onMounted(() => {
    load();
    loadTriggers();
  });
</script>
