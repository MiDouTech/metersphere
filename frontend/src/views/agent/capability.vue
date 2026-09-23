<template>
  <AgentPage>
    <a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert>
    <div class="grid gap-4 lg:grid-cols-2">
      <MsCard v-if="section === 'capabilities'" simple>
        <div class="mb-4 flex items-center justify-between">
          <div>
            <div class="text-base font-medium">执行能力</div>
            <div class="mt-1 text-sm text-[var(--color-text-3)]">网关配置决定平台可委派的 Agent 类型。</div>
          </div>
          <a-button :loading="loading" @click="load">刷新</a-button>
        </div>
        <a-list :data="agents" :bordered="false">
          <template #item="{ item }">
            <a-list-item>
              <a-list-item-meta :title="item.name" :description="item.message || item.protocol || '-'" />
              <template #actions>
                <a-tag :color="item.configured ? 'green' : 'gray'">{{ item.configured ? '已配置' : '未配置' }}</a-tag>
              </template>
              <div v-if="item.features?.length" class="mt-2 flex flex-wrap gap-2">
                <a-tag v-for="feature in item.features" :key="feature">{{ feature }}</a-tag>
              </div>
            </a-list-item>
          </template>
        </a-list>
      </MsCard>
      <a-space class="lg:col-span-2">
        <a-link v-permission="['SYSTEM_PERSONAL_AI_AGENT:READ']" @click="router.push({ name: 'agentAccess' })"
          >个人接入</a-link
        >
        <a-link @click="router.push('/setting/runtime/capability')">运行告警</a-link>
        <a-link
          v-permission="['FUNCTIONAL_CASE_AI:CONFIG']"
          @click="router.push('/setting/execution-settings/governance')"
          >项目执行策略</a-link
        >
      </a-space>
      <MsCard v-if="section === 'alerts'" simple class="lg:col-span-2">
        <div class="mb-4 flex items-center justify-between"
          ><div
            ><div class="text-base font-medium">AI 执行运维告警</div
            ><div class="mt-1 text-sm text-[var(--color-text-3)]"
              >汇总 Gateway、Runner、凭据、阻塞任务、人工超时、清理与回写异常。</div
            ></div
          ><a-button :loading="alertLoading" @click="loadAlerts">刷新</a-button></div
        >
        <a-alert v-if="alertError" type="error" class="mb-3">{{ alertError }}</a-alert>
        <a-table :data="alerts" :loading="alertLoading" :pagination="false" row-key="id">
          <template #columns>
            <a-table-column title="级别" :width="100"
              ><template #cell="{ record }"
                ><a-tag :color="record.severity === 'CRITICAL' ? 'red' : 'orange'">{{
                  record.severity
                }}</a-tag></template
              ></a-table-column
            >
            <a-table-column title="类型" data-index="alertType" :width="190" />
            <a-table-column title="说明" data-index="message" />
            <a-table-column title="Trace ID" data-index="traceId" :width="220" />
            <a-table-column title="状态" data-index="status" :width="130" />
            <a-table-column title="操作" :width="100"
              ><template #cell="{ record }"
                ><a-link
                  v-if="record.status === 'OPEN'"
                  v-permission="['AI_RUNNER:MANAGE']"
                  @click="acknowledge(record.id)"
                  >确认</a-link
                ></template
              ></a-table-column
            >
          </template>
          <template #empty><a-empty description="当前项目暂无运维告警" /></template>
        </a-table>
      </MsCard>
      <AiGovernancePanel
        v-if="section === 'policy'"
        v-permission="['FUNCTIONAL_CASE_AI:CONFIG']"
        class="lg:col-span-2"
      />
    </div>
  </AgentPage>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue';
  import { useRouter } from 'vue-router';

  import AgentPage from './components/AgentPage.vue';
  import AiGovernancePanel from './components/AiGovernancePanel.vue';

  import type { AiExecutionAgentOption, AiExecutionAlert } from '@/api/modules/ai-execution';
  import { acknowledgeAiExecutionAlert, getAiExecutionAgents, listAiExecutionAlerts } from '@/api/modules/ai-execution';
  import { useAppStore } from '@/store';

  const props = withDefaults(defineProps<{ section?: 'capabilities' | 'alerts' | 'policy' }>(), {
    section: 'capabilities',
  });
  const router = useRouter();
  const appStore = useAppStore();
  const loading = ref(false);
  const error = ref('');
  const agents = ref<AiExecutionAgentOption[]>([]);
  const alerts = ref<AiExecutionAlert[]>([]);
  const alertLoading = ref(false);
  const alertError = ref('');
  async function loadAlerts() {
    alertLoading.value = true;
    alertError.value = '';
    try {
      alerts.value = (await listAiExecutionAlerts(appStore.currentProjectId)) || [];
    } catch (reason: any) {
      alertError.value = '运维告警加载失败，请稍后重试';
    } finally {
      alertLoading.value = false;
    }
  }
  async function acknowledge(id: string) {
    try {
      await acknowledgeAiExecutionAlert(appStore.currentProjectId, id);
      await loadAlerts();
    } catch (reason: any) {
      alertError.value = '告警确认失败，请稍后重试';
    }
  }
  async function load() {
    loading.value = true;
    error.value = '';
    try {
      agents.value = (await getAiExecutionAgents(appStore.currentProjectId)) || [];
    } catch (reason: any) {
      error.value = '执行能力加载失败，请稍后重试';
    } finally {
      loading.value = false;
    }
  }
  watch(
    () => [props.section, appStore.currentProjectId],
    () => {
      agents.value = [];
      alerts.value = [];
      if (!appStore.currentProjectId) return;
      if (props.section === 'capabilities') load();
      if (props.section === 'alerts') loadAlerts();
    },
    { immediate: true }
  );
</script>
