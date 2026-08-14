<template>
  <AgentPage>
    <div class="grid gap-4 lg:grid-cols-2">
      <MsCard simple>
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
      <MsCard simple>
        <div class="mb-4">
          <div class="text-base font-medium">我的授权</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]">Token 只显示范围和使用状态，密钥不会再次返回。</div>
        </div>
        <a-table :data="tokens" :loading="loading" :pagination="false" row-key="id">
          <template #columns>
            <a-table-column title="名称" data-index="name" />
            <a-table-column title="客户端" data-index="clientType" :width="110" />
            <a-table-column title="权限范围" data-index="scopes" />
            <a-table-column title="状态" :width="90">
              <template #cell="{ record }"
                ><a-tag :color="record.enable ? 'green' : 'gray'">{{
                  record.enable ? '启用' : '停用'
                }}</a-tag></template
              >
            </a-table-column>
          </template>
        </a-table>
      </MsCard>
      <AiGovernancePanel v-permission="['FUNCTIONAL_CASE_AI:CONFIG']" class="lg:col-span-2" />
    </div>
  </AgentPage>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue';

  import AgentPage from './components/AgentPage.vue';
  import AiGovernancePanel from './components/AiGovernancePanel.vue';

  import type { AiExecutionAgentOption } from '@/api/modules/ai-execution';
  import { getAiExecutionAgents } from '@/api/modules/ai-execution';
  import type { AgentTokenListItem } from '@/api/modules/setting/agentIntegration';
  import { getAgentTokenPage } from '@/api/modules/setting/agentIntegration';
  import { useAppStore } from '@/store';

  const appStore = useAppStore();
  const loading = ref(false);
  const agents = ref<AiExecutionAgentOption[]>([]);
  const tokens = ref<AgentTokenListItem[]>([]);
  async function load() {
    loading.value = true;
    try {
      const [agentResult, tokenResult] = await Promise.all([
        getAiExecutionAgents(appStore.currentProjectId),
        getAgentTokenPage({ current: 1, pageSize: 100 }),
      ]);
      agents.value = agentResult || [];
      tokens.value = tokenResult.list || [];
    } finally {
      loading.value = false;
    }
  }
  onMounted(load);
</script>
