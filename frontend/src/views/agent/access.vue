<template>
  <AgentPage>
    <AgentIntegration compact />
    <MsCard v-if="canReadUserAgent" simple class="mt-4">
      <UserAgent />
    </MsCard>
  </AgentPage>
</template>

<script setup lang="ts">
  import MsCard from '@/components/pure/ms-card/index.vue';
  import UserAgent from '@/components/business/ms-personal-drawer/components/userAgent.vue';
  import AgentPage from './components/AgentPage.vue';
  import AgentIntegration from '@/views/setting/system/agentIntegration/index.vue';

  import { hasAnyPermission } from '@/utils/permission';

  const canReadUserAgent = hasAnyPermission(['SYSTEM_PERSONAL_AI_AGENT:READ'], ['SYSTEM']);
</script>
