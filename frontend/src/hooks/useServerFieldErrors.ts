import { nextTick, ref } from 'vue';

import { useI18n } from '@/hooks/useI18n';
import { AppError } from '@/utils/appError';

export interface ServerFieldData {
  status: 'error';
  message: string;
}

export default function useServerFieldErrors(fieldMap: Record<string, string> = {}) {
  const { t } = useI18n();
  const fieldErrors = ref<Record<string, string>>({});

  function localize(message: string) {
    const translated = message.includes('.') ? t(message) : message;
    return translated && translated !== message ? translated : message;
  }

  function focusFirstField(field: string) {
    nextTick(() => {
      const escaped = typeof CSS !== 'undefined' && CSS.escape ? CSS.escape(field) : field;
      const element = document.querySelector<HTMLElement>(`[name="${escaped}"], [data-field="${escaped}"] input`);
      element?.focus();
    });
  }

  function applyError(error: unknown): Record<string, ServerFieldData> | undefined {
    if (!(error instanceof AppError) || !error.fieldErrors || !Object.keys(error.fieldErrors).length) return undefined;
    fieldErrors.value = Object.fromEntries(
      Object.entries(error.fieldErrors).map(([field, message]) => [fieldMap[field] || field, localize(message)])
    );
    const [firstField] = Object.keys(fieldErrors.value);
    if (firstField) focusFirstField(firstField);
    return Object.fromEntries(
      Object.entries(fieldErrors.value).map(([field, message]) => [field, { status: 'error', message }])
    );
  }

  function clearField(field: string) {
    const mappedField = fieldMap[field] || field;
    if (!(mappedField in fieldErrors.value)) return;
    const next = { ...fieldErrors.value };
    delete next[mappedField];
    fieldErrors.value = next;
  }

  function clearAll() {
    fieldErrors.value = {};
  }

  return { fieldErrors, applyError, clearField, clearAll };
}
