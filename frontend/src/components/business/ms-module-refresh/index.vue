<template>
  <a-tooltip :content="lastRefreshedText || t('common.refresh')">
    <a-button
      type="outline"
      class="arco-btn-outline--secondary"
      :loading="refreshing"
      :disabled="refreshing"
      @click="refresh"
    >
      <template #icon>
        <icon-refresh />
      </template>
      {{ t('common.refresh') }}
    </a-button>
  </a-tooltip>
</template>

<script setup lang="ts">
  import { useI18n } from '@/hooks/useI18n';
  import { ModuleRefreshHandler, useModuleRefresh } from '@/hooks/useModuleRefresh';

  const props = defineProps<{
    onRefresh?: ModuleRefreshHandler;
  }>();

  const { t } = useI18n();
  const { refreshing, lastRefreshedText, refresh } = useModuleRefresh(props.onRefresh);
</script>
