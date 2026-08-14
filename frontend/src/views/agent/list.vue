<template>
  <AgentPage>
    <div class="mb-4 grid grid-cols-2 gap-4 lg:grid-cols-5">
      <MsCard v-for="item in metrics" :key="item.label" simple>
        <div class="text-sm text-[var(--color-text-3)]">{{ item.label }}</div>
        <div class="mt-2 text-2xl font-semibold">{{ item.value }}</div>
      </MsCard>
    </div>
    <MsCard simple>
      <div class="mb-4 flex items-center justify-between">
        <div>
          <div class="text-base font-medium">Agent / Runner 实例</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]">在线状态按 90 秒心跳窗口实时计算。</div>
        </div>
        <a-space>
          <a-button :loading="loading" @click="load">刷新</a-button>
          <a-button v-permission="['AI_EXECUTION:ADMIN']" type="primary" @click="registerVisible = true"
            >注册 Runner</a-button
          >
        </a-space>
      </div>
      <a-table :data="runners" :loading="loading" :pagination="false" row-key="id">
        <template #empty><a-empty description="尚未注册 Runner，请前往 Agent 集成完成注册。" /></template>
        <template #columns>
          <a-table-column title="名称" data-index="name" />
          <a-table-column title="状态" :width="110">
            <template #cell="{ record }">
              <a-tag :color="record.status === 'ONLINE' ? 'green' : record.status === 'STALE' ? 'orange' : 'gray'">
                {{ record.status }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="版本" :width="150">
            <template #cell="{ record }"
              >{{ record.runnerVersion || '-' }} / {{ record.contractVersion || '-' }}</template
            >
          </a-table-column>
          <a-table-column title="系统" data-index="operatingSystem" :width="140" />
          <a-table-column title="能力">
            <template #cell="{ record }">{{ record.browserCapabilities || '-' }}</template>
          </a-table-column>
          <a-table-column title="隔离" :width="120">
            <template #cell="{ record }">
              <a-tag :color="record.isolationMode && record.isolationMode !== 'UNDECLARED' ? 'green' : 'orange'">
                {{ record.isolationMode || 'UNDECLARED' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="并发" :width="110">
            <template #cell="{ record }">{{ record.activeCount || 0 }} / {{ record.maxConcurrency || 0 }}</template>
          </a-table-column>
          <a-table-column title="最后心跳" :width="180">
            <template #cell="{ record }">{{ formatTime(record.lastHeartbeatTime) }}</template>
          </a-table-column>
        </template>
      </a-table>
    </MsCard>
    <a-modal
      v-model:visible="registerVisible"
      title="注册 Runner"
      :ok-loading="registering"
      @before-ok="submitRegister"
    >
      <a-form :model="registerForm" layout="vertical">
        <div class="grid grid-cols-2 gap-x-4">
          <a-form-item label="名称" required><a-input v-model="registerForm.name" /></a-form-item>
          <a-form-item label="Runner 版本" required
            ><a-input v-model="registerForm.runnerVersion" placeholder="1.0.0"
          /></a-form-item>
          <a-form-item label="操作系统"
            ><a-input v-model="registerForm.operatingSystem" placeholder="Windows / Linux"
          /></a-form-item>
          <a-form-item label="最大并发"
            ><a-input-number v-model="registerForm.maxConcurrency" :min="1" :max="20" class="w-full"
          /></a-form-item>
          <a-form-item label="隔离模式" required>
            <a-select v-model="registerForm.isolationMode"
              ><a-option value="PROCESS">PROCESS</a-option><a-option value="CONTAINER">CONTAINER</a-option
              ><a-option value="VM">VM</a-option><a-option value="UNDECLARED">UNDECLARED</a-option></a-select
            >
          </a-form-item>
          <a-form-item label="环境标签"
            ><a-input v-model="registerForm.environmentLabels" placeholder='{"region":"cn"}'
          /></a-form-item>
        </div>
        <a-form-item label="浏览器能力"
          ><a-textarea v-model="registerForm.browserCapabilities" placeholder='["chromium"]'
        /></a-form-item>
      </a-form>
    </a-modal>
    <a-modal
      v-model:visible="tokenVisible"
      title="Runner 注册凭据（仅展示一次）"
      :footer="false"
      :mask-closable="false"
    >
      <a-alert type="warning" class="mb-3">请立即复制并保存 Runner Token。关闭后平台不会再次返回明文。</a-alert>
      <div class="mb-2 text-sm">Runner ID：{{ registered?.runnerId }}</div>
      <div class="break-all rounded bg-[var(--color-fill-2)] p-3 font-mono text-sm">{{ registered?.runnerToken }}</div>
      <div class="mt-3 flex justify-end"><a-button type="primary" @click="copyRunnerToken">复制 Token</a-button></div>
    </a-modal>
  </AgentPage>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import AgentPage from './components/AgentPage.vue';

  import type { AiExecutionOperations, AiRunner, AiRunnerRegisterResult } from '@/api/modules/ai-execution';
  import { getAiExecutionOperations, getAiRunners, registerAiRunner } from '@/api/modules/ai-execution';

  const loading = ref(false);
  const runners = ref<AiRunner[]>([]);
  const operations = ref<AiExecutionOperations>();
  const registerVisible = ref(false);
  const tokenVisible = ref(false);
  const registering = ref(false);
  const registered = ref<AiRunnerRegisterResult>();
  const registerForm = reactive({
    name: '',
    runnerVersion: '',
    contractVersion: 'v1',
    operatingSystem: '',
    browserCapabilities: '',
    environmentLabels: '',
    isolationMode: 'CONTAINER' as 'UNDECLARED' | 'PROCESS' | 'CONTAINER' | 'VM',
    maxConcurrency: 1,
  });
  const metrics = computed(() => [
    { label: '在线 Agent', value: operations.value?.onlineRunnerCount || 0 },
    { label: '活动租约', value: operations.value?.activeLeaseCount || 0 },
    { label: '排队任务', value: operations.value?.queuedTaskCount || 0 },
    { label: '疑似卡住', value: operations.value?.stuckTaskCount || 0 },
    { label: '回写积压', value: operations.value?.writebackBacklogCount || 0 },
  ]);
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');

  async function load() {
    loading.value = true;
    try {
      const [runnerResult, operationResult] = await Promise.all([getAiRunners(), getAiExecutionOperations()]);
      runners.value = runnerResult || [];
      operations.value = operationResult;
    } finally {
      loading.value = false;
    }
  }
  async function submitRegister(done: (closed: boolean) => void) {
    if (!registerForm.name.trim() || !registerForm.runnerVersion.trim()) {
      Message.warning('请填写名称和 Runner 版本');
      done(false);
      return;
    }
    registering.value = true;
    try {
      registered.value = await registerAiRunner({
        ...registerForm,
        name: registerForm.name.trim(),
        runnerVersion: registerForm.runnerVersion.trim(),
        operatingSystem: registerForm.operatingSystem.trim() || undefined,
        browserCapabilities: registerForm.browserCapabilities.trim() || undefined,
        environmentLabels: registerForm.environmentLabels.trim() || undefined,
      });
      tokenVisible.value = true;
      await load();
      done(true);
    } catch {
      done(false);
    } finally {
      registering.value = false;
    }
  }
  async function copyRunnerToken() {
    if (!registered.value?.runnerToken) return;
    await navigator.clipboard.writeText(registered.value.runnerToken);
    Message.success('Runner Token 已复制');
  }
  onMounted(load);
</script>
