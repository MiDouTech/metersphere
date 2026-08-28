<template>
  <div class="p-4">
    <AgentTabs />
    <slot />
  </div>
</template>

<script setup lang="ts">
  import AgentTabs from './AgentTabs.vue';
</script>
