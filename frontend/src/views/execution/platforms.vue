<template>
  <div class="p-4">
    <div class="mb-3 flex items-center justify-between gap-3">
      <span>从测试平台进入对应的登录方式与角色，沿用已有配置和历史引用。</span>
      <a-button @click="legacyVisible = true">查看已有配置关联</a-button>
    </div>
    <EnvironmentProfile v-if="canReadPlatforms" />
    <CredentialReference v-else-if="canReadCredentials" />
    <a-drawer v-model:visible="legacyVisible" title="已有配置关联" :width="960" :footer="false" unmount-on-close>
      <a-alert class="mb-3"
        >配置 ID
        和历史任务引用保持不变。请按平台名称核对关联；显示“待关联平台”的记录需明确选择目标，不自动猜测迁移。</a-alert
      >
      <a-tabs v-model:active-key="tab" lazy-load>
        <a-tab-pane v-if="canReadPlatforms" key="login" title="登录方式"><LoginProfile /></a-tab-pane>
        <a-tab-pane v-if="canReadCredentials" key="credentials" title="角色与凭据"><CredentialReference /></a-tab-pane>
      </a-tabs>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
  import { computed, defineAsyncComponent, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';

  import { hasAnyPermission } from '@/utils/permission';

  const EnvironmentProfile = defineAsyncComponent(() => import('@/views/agent/environment-profile.vue'));
  const LoginProfile = defineAsyncComponent(() => import('@/views/agent/login-profile.vue'));
  const CredentialReference = defineAsyncComponent(() => import('@/views/agent/credential-reference.vue'));
  const route = useRoute();
  const router = useRouter();
  const legacyVisible = ref(false);
  watch(
    () => route.query.tab,
    (value) => {
      if (value === 'login' || value === 'credentials') legacyVisible.value = true;
    },
    { immediate: true }
  );
  const canReadPlatforms = computed(() => hasAnyPermission(['AI_EXECUTION:READ']));
  const canReadCredentials = computed(() => hasAnyPermission(['AI_CREDENTIAL:READ_METADATA']));
  const tab = computed({
    get() {
      const allowed = [
        ...(canReadPlatforms.value ? ['login'] : []),
        ...(canReadCredentials.value ? ['credentials'] : []),
      ];
      return allowed.includes(String(route.query.tab)) ? String(route.query.tab) : allowed[0];
    },
    set(value: string) {
      router.replace({ query: { ...route.query, tab: value } });
    },
  });
</script>
