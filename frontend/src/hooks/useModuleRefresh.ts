import { computed, ref } from 'vue';
import { Message } from '@arco-design/web-vue';

import { useI18n } from '@/hooks/useI18n';

export interface ModuleRefreshContext {
  reason: 'initial' | 'activated' | 'data-change' | 'manual';
  preserveViewState: true;
}

export interface ModuleRefreshResult {
  refreshedAt: number;
  partialFailures?: string[];
}

export type ModuleRefreshHandler = (
  context: ModuleRefreshContext
) => Promise<ModuleRefreshResult | void> | ModuleRefreshResult | void;

export function useModuleRefresh(handler?: ModuleRefreshHandler) {
  const { t } = useI18n();
  const refreshing = ref(false);
  const lastRefreshedAt = ref<number>();

  const lastRefreshedText = computed(() => {
    if (!lastRefreshedAt.value) {
      return '';
    }
    return t('common.lastRefreshTime', { time: new Date(lastRefreshedAt.value).toLocaleTimeString() });
  });

  async function refresh(reason: ModuleRefreshContext['reason'] = 'manual') {
    if (refreshing.value) {
      return;
    }
    refreshing.value = true;
    try {
      const result = await handler?.({
        reason,
        preserveViewState: true,
      });
      lastRefreshedAt.value = result?.refreshedAt || Date.now();
      Message.success(t('common.refreshSuccess'));
    } finally {
      refreshing.value = false;
    }
  }

  return {
    refreshing,
    lastRefreshedAt,
    lastRefreshedText,
    refresh,
  };
}
