<template>
  <a-alert type="error" :show-icon="true" class="ms-error-notice">
    <template #title>{{ title || t('common.loadFailedTitle') }}</template>
    <div class="flex flex-col gap-2">
      <span>{{ error.message }}</span>
      <span v-if="traceText" class="text-[12px] text-[var(--color-text-3)]">{{ traceText }}</span>
      <div v-if="error.retryable || traceText" class="flex items-center gap-2">
        <a-button v-if="error.retryable" type="primary" size="small" @click="emit('retry')">
          {{ t('common.retry') }}
        </a-button>
        <a-button v-if="traceText" type="text" size="small" @click="copyDiagnostic">
          {{ t('common.copyDiagnostic') }}
        </a-button>
      </div>
    </div>
  </a-alert>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import { useI18n } from '@/hooks/useI18n';
  import type { AppError } from '@/utils/appError';

  const props = withDefaults(defineProps<{ error: AppError; title?: string }>(), { title: undefined });

  const emit = defineEmits<{ (event: 'retry'): void }>();
  const { t } = useI18n();

  const traceText = computed(() => {
    const values = [
      props.error.code ? `${t('common.errorCode')}: ${props.error.code}` : '',
      props.error.requestId ? `${t('common.requestId')}: ${props.error.requestId}` : '',
    ].filter(Boolean);
    return values.join(' · ');
  });

  async function copyDiagnostic() {
    try {
      await navigator.clipboard.writeText(traceText.value);
      Message.success(t('common.copySuccess'));
    } catch {
      Message.error(t('common.copyFailed'));
    }
  }
</script>
