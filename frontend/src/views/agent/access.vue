<template>
  <AgentPage>
    <a-tabs active-key="token" type="rounded">
      <a-tab-pane key="token" title="我的 Agent Token" />
    </a-tabs>
    <AgentIntegration compact />
  </AgentPage>
</template>

<script setup lang="ts">
  import AgentPage from './components/AgentPage.vue';
  import AgentIntegration from '@/views/setting/system/agentIntegration/index.vue';
</script>
