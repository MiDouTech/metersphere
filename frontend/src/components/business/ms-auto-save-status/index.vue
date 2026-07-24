<template>
  <div class="inline-flex items-center gap-2 text-[12px] text-[var(--color-text-4)]">
    <template v-if="status === 'locked-readonly'">
      <span class="text-[rgb(var(--warning-6))]">{{ lockMessage || t('common.autoSave.lockedByOther') }}</span>
    </template>
    <template v-else-if="status === 'saving'">
      <span>{{ t('common.autoSave.saving') }}</span>
    </template>
    <template v-else-if="status === 'dirty'">
      <span>{{ t('common.autoSave.dirty') }}</span>
    </template>
    <template v-else-if="status === 'error'">
      <span class="text-[rgb(var(--danger-6))]">{{ t('common.autoSave.saveFailed') }}</span>
      <MsButton v-if="showRetry" type="text" @click="emit('retry')">{{ t('common.autoSave.retry') }}</MsButton>
    </template>
    <template v-else-if="lastSavedAt">
      <span>{{ t('common.autoSave.saved') }} · {{ timeText }}</span>
    </template>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';

  import MsButton from '@/components/pure/ms-button/index.vue';

  import type { AutoSaveStatus } from '@/hooks/useAutoSaveEditor';
  import { useI18n } from '@/hooks/useI18n';

  const props = defineProps<{
    status: AutoSaveStatus;
    lastSavedAt?: number | null;
    lockMessage?: string;
    showRetry?: boolean;
  }>();

  const emit = defineEmits<{ (e: 'retry'): void }>();

  const { t } = useI18n();

  const timeText = computed(() => {
    if (!props.lastSavedAt) return '';
    const d = new Date(props.lastSavedAt);
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    return `${hh}:${mm}`;
  });
</script>
