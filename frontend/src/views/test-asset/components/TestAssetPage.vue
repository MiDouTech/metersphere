<template>
  <div class="p-4">
    <TestAssetTabs />
    <slot />
  </div>
</template>

<script setup lang="ts">
  import TestAssetTabs from './TestAssetTabs.vue';
</script>
