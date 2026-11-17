<template
  ><main class="p-4"><Configuration :project-id="app.currentProjectId" /></main
></template>

<script setup lang="ts">
  import Configuration from '@/components/business/hifox-handoff/configuration.vue';

  import { useAppStore } from '@/store';

  const app = useAppStore();
</script>
