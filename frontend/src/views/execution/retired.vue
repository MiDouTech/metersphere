<template>
  <a-result
    status="info"
    title="此入口已停用"
    subtitle="个人 Agent 无需配置这些高级功能。请从执行中心访问任务、测试平台配置和平台质量门禁。"
  >
    <template #extra><a-button type="primary" @click="back">返回可访问页面</a-button></template>
  </a-result>
</template>

<script setup lang="ts">
  import { useRouter } from 'vue-router';

  import { getFirstRouteNameByPermission } from '@/utils/permission';

  const router = useRouter();
  function back() {
    router.replace({ name: getFirstRouteNameByPermission(router.getRoutes()) });
  }
</script>
