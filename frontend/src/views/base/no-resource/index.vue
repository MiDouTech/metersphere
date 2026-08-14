<template>
  <NoPermissionLayoutVue>
    <div class="mt-[16px] flex flex-col items-center gap-[12px]">
      <span v-if="permissionCode" class="text-[var(--color-text-3)]">
        {{ t('common.missingPermission') }}：{{ permissionCode }}
      </span>
      <a-button type="primary" @click="goBack">{{ t('common.backToPrevious') }}</a-button>
    </div>
  </NoPermissionLayoutVue>
</template>

<script lang="ts" setup>
  import { useRoute, useRouter } from 'vue-router';

  import NoPermissionLayoutVue from '@/layout/no-permission-layout.vue';

  import { useI18n } from '@/hooks/useI18n';

  const route = useRoute();
  const router = useRouter();
  const { t } = useI18n();
  const permissionCode = computed(() => route.query.permission as string | undefined);

  function goBack() {
    const redirect = route.query.redirect as string | undefined;
    if (redirect && redirect !== route.fullPath) {
      router.push(redirect);
    } else {
      router.back();
    }
  }
</script>
