<template>
  <div class="flex h-screen items-center justify-center" role="status" aria-live="polite">
    <a-spin v-if="projectSwitchPending" tip="正在切换项目..." />
    <div v-else>
      <p>项目切换未完成，请重新登录后重试。</p>
      <a-button class="mt-4" @click="loginAgain">重新登录</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { projectSwitchPending } from '@/views/setting/utils';

  function loginAgain() {
    window.location.replace(`${window.location.origin}${window.location.pathname}#/login`);
  }
</script>
