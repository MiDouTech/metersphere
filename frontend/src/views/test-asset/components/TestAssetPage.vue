<template>
  <div class="p-4">
    <div
      v-if="hasPageVisible('TEST_ASSET_VERSIONS_PAGE') && route.path !== '/test-assets/versions'"
      class="mb-3 flex justify-end"
    >
      <a-link @click="router.push('/test-assets/versions')">按版本检索资产与引用</a-link>
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
  import { useRoute, useRouter } from 'vue-router';

  import { hasPageVisible } from '@/utils/permission';

  const route = useRoute();
  const router = useRouter();
</script>
