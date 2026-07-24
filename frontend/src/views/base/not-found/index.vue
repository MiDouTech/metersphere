<template>
  <div class="absolute left-[50%] top-[50%] ml-[-95px] mt-[-121px] text-center">
    <a-result class="result" status="404" :subtitle="t('base.notFound.subtitle')"> </a-result>
    <div class="operation-row">
      <a-button key="back" type="primary" @click="back">{{ t('base.notFound.back') }}</a-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
  import { useRouter } from 'vue-router';

  import { useI18n } from '@/hooks/useI18n';
  import { getFirstRouteNameByPermission } from '@/utils/permission';

  const { t } = useI18n();
  const router = useRouter();
  const back = () => {
    router.push({ name: getFirstRouteNameByPermission(router.getRoutes()) });
  };
</script>
