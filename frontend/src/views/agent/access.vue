<template>
  <AgentPage>
    <a-tabs v-model:active-key="activeTab" type="rounded" @change="handleTabChange">
      <a-tab-pane key="token" title="我的 Agent Token" />
      <a-tab-pane v-if="canReadUserAgent" key="user-agent" title="我的 AI Agent" />
    </a-tabs>
    <AgentIntegration v-if="activeTab === 'token'" compact />
    <MsCard v-else-if="canReadUserAgent" simple>
      <UserAgent />
    </MsCard>
  </AgentPage>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { useRoute, useRouter } from 'vue-router';

  import MsCard from '@/components/pure/ms-card/index.vue';
  import UserAgent from '@/components/business/ms-personal-drawer/components/userAgent.vue';
  import AgentPage from './components/AgentPage.vue';
  import AgentIntegration from '@/views/setting/system/agentIntegration/index.vue';

  import { hasAnyPermission } from '@/utils/permission';

  const route = useRoute();
  const router = useRouter();
  const canReadUserAgent = hasAnyPermission(['SYSTEM_PERSONAL_AI_AGENT:READ'], ['SYSTEM']);
  const activeTab = computed<'token' | 'user-agent'>({
    get: () => (route.query.tab === 'user-agent' && canReadUserAgent ? 'user-agent' : 'token'),
    set: (tab) => {
      router.replace({ query: { ...route.query, tab } });
    },
  });

  function handleTabChange(tab: string | number) {
    activeTab.value = String(tab) as 'token' | 'user-agent';
  }
</script>
