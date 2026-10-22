<template>
  <div class="flex min-h-screen items-center justify-center">
    <a-result status="info" title="功能未启用" subtitle="当前平台未启用模型功能，请返回使用其他功能。">
      <template #extra
        ><a-button type="primary" @click="router.replace('/workstation/home')">返回工作台</a-button></template
      >
    </a-result>
  </div>
</template>

<script setup lang="ts">
  import { useRouter } from 'vue-router';

  const router = useRouter();
</script>
