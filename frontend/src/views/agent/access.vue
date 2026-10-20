<template>
  <PersonalDrawer :visible="true" initial-menu="agentIntegration" @update:visible="close" />
</template>

<script setup lang="ts">
  import { useRouter } from 'vue-router';

  import PersonalDrawer from '@/components/business/ms-personal-drawer/index.vue';

  import { getFirstRouteNameByPermission } from '@/utils/permission';

  const router = useRouter();
  function close() {
    router.replace({ name: getFirstRouteNameByPermission(router.getRoutes()) });
  }
</script>
