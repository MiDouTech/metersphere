<template>
  <div class="p-4">
    <a-tabs v-model:active-key="tab" lazy-load>
      <a-tab-pane v-if="canReadPlatforms" key="platform" title="测试平台"><EnvironmentProfile /></a-tab-pane>
      <a-tab-pane v-if="canReadPlatforms" key="login" title="登录方式"><LoginProfile /></a-tab-pane>
      <a-tab-pane v-if="canReadCredentials" key="credentials" title="角色与凭据"><CredentialReference /></a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
  import { computed, defineAsyncComponent } from 'vue';
  import { useRoute, useRouter } from 'vue-router';

  import { hasAnyPermission } from '@/utils/permission';

  const EnvironmentProfile = defineAsyncComponent(() => import('@/views/agent/environment-profile.vue'));
  const LoginProfile = defineAsyncComponent(() => import('@/views/agent/login-profile.vue'));
  const CredentialReference = defineAsyncComponent(() => import('@/views/agent/credential-reference.vue'));
  const route = useRoute();
  const router = useRouter();
  const canReadPlatforms = computed(() => hasAnyPermission(['AI_EXECUTION:READ']));
  const canReadCredentials = computed(() => hasAnyPermission(['AI_CREDENTIAL:READ_METADATA']));
  const tab = computed({
    get() {
      const allowed = [
        ...(canReadPlatforms.value ? ['platform', 'login'] : []),
        ...(canReadCredentials.value ? ['credentials'] : []),
      ];
      return allowed.includes(String(route.query.tab)) ? String(route.query.tab) : allowed[0];
    },
    set(value: string) {
      router.replace({ query: { ...route.query, tab: value } });
    },
  });
</script>
