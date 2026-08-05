<template>
  <div class="case-generate-detail">
    <div class="mb-[12px] flex items-center justify-between">
      <div class="text-[16px] font-medium text-[var(--color-text-1)]">
        {{ t('caseManagement.caseGenerate.detail') }}
      </div>
      <a-tag v-if="draft.validationStatus === 'INVALID'" color="red">{{ draft.validationMessage }}</a-tag>
      <a-tag v-else-if="draft.validationStatus === 'READY'" color="green">
        {{ t('caseManagement.caseGenerate.statusReady') }}
      </a-tag>
    </div>
    <a-form :model="draft" layout="vertical">
      <a-form-item :label="t('caseManagement.caseGenerate.name')">
        <a-input :model-value="draft.name" @update:model-value="(value: string) => updateField('name', value)" />
      </a-form-item>
      <a-form-item :label="t('caseManagement.caseGenerate.level')">
        <a-select
          :model-value="draft.caseLevel || 'P1'"
          @update:model-value="(value) => updateField('caseLevel', String(value))"
        >
          <a-option value="P0">P0</a-option>
          <a-option value="P1">P1</a-option>
          <a-option value="P2">P2</a-option>
          <a-option value="P3">P3</a-option>
        </a-select>
      </a-form-item>
      <a-form-item :label="t('caseManagement.caseGenerate.editType')">
        <a-select
          :model-value="draft.editType || 'STEP'"
          @update:model-value="(value) => updateField('editType', String(value))"
        >
          <a-option value="STEP">STEP</a-option>
          <a-option value="TEXT">TEXT</a-option>
        </a-select>
      </a-form-item>
      <a-form-item :label="t('caseManagement.caseGenerate.moduleId')">
        <a-input
          :model-value="draft.moduleId"
          @update:model-value="(value: string) => updateField('moduleId', value)"
        />
      </a-form-item>
      <a-form-item :label="t('caseManagement.caseGenerate.templateId')">
        <a-input
          :model-value="draft.templateId"
          @update:model-value="(value: string) => updateField('templateId', value)"
        />
      </a-form-item>
      <a-form-item :label="t('caseManagement.caseGenerate.prerequisite')">
        <a-textarea
          :model-value="draft.prerequisite"
          :auto-size="{ minRows: 2, maxRows: 4 }"
          @update:model-value="(value: string) => updateField('prerequisite', value)"
        />
      </a-form-item>
      <a-form-item :label="t('caseManagement.caseGenerate.steps')">
        <a-textarea
          :model-value="draft.steps"
          :auto-size="{ minRows: 5, maxRows: 10 }"
          @update:model-value="(value: string) => updateField('steps', value)"
        />
      </a-form-item>
      <a-form-item :label="t('caseManagement.caseGenerate.expectedResult')">
        <a-textarea
          :model-value="draft.expectedResult"
          :auto-size="{ minRows: 3, maxRows: 6 }"
          @update:model-value="(value: string) => updateField('expectedResult', value)"
        />
      </a-form-item>
      <a-form-item :label="t('caseManagement.caseGenerate.tags')">
        <a-input :model-value="draft.tags" @update:model-value="(value: string) => updateField('tags', value)" />
      </a-form-item>
      <a-form-item :label="t('caseManagement.caseGenerate.customFields')">
        <a-textarea
          :model-value="draft.customFields"
          :auto-size="{ minRows: 2, maxRows: 5 }"
          @update:model-value="(value: string) => updateField('customFields', value)"
        />
      </a-form-item>
    </a-form>
    <div class="mt-[8px] text-[12px] text-[var(--color-text-3)]">
      {{ t('caseManagement.caseGenerate.autoSaved') }}
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from '@/hooks/useI18n';

  import type { AiCaseDraft } from '@/models/caseManagement/caseGenerate';

  const props = defineProps<{
    draft: AiCaseDraft;
  }>();

  const emit = defineEmits<{
    (event: 'update:draft', value: AiCaseDraft): void;
  }>();

  const { t } = useI18n();

  function updateField(field: keyof AiCaseDraft, value: string) {
    emit('update:draft', {
      ...props.draft,
      [field]: value,
    });
  }
</script>
