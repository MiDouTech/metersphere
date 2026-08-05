<template>
  <div class="case-generate-detail">
    <div class="mb-[12px] flex items-center justify-between gap-[8px]">
      <div class="text-[16px] font-medium text-[var(--color-text-1)]">
        {{ t('caseManagement.caseGenerate.detail') }}
      </div>
      <div class="flex flex-wrap gap-[4px]">
        <a-tag v-if="draft.duplicate" color="orange">
          {{ t('caseManagement.caseGenerate.duplicateWarning') }}
        </a-tag>
        <a-tag v-if="draft.validationStatus === 'INVALID'" color="red">{{ draft.validationMessage }}</a-tag>
        <a-tag v-else-if="draft.validationStatus === 'READY'" color="green">
          {{ t('caseManagement.caseGenerate.statusReady') }}
        </a-tag>
      </div>
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
        <a-tree-select
          :model-value="draft.moduleId"
          :data="moduleTree"
          allow-search
          :filter-tree-node="filterTreeNode"
          :field-names="{ title: 'name', key: 'id', children: 'children' }"
          :tree-props="{ virtualListProps: { height: 200 } }"
          :placeholder="t('caseManagement.caseGenerate.modulePlaceholder')"
          @update:model-value="(value) => updateField('moduleId', String(value || ''))"
        />
      </a-form-item>
      <a-form-item :label="t('caseManagement.caseGenerate.templateId')">
        <a-select
          :model-value="draft.templateId"
          allow-search
          :placeholder="t('caseManagement.caseGenerate.templatePlaceholder')"
          @update:model-value="(value) => updateField('templateId', String(value || ''))"
        >
          <a-option v-for="item in templateOptions" :key="item.id" :value="item.id">
            {{ item.name }}
          </a-option>
        </a-select>
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
        <MsTagsInput :model-value="tagList" class="w-full" @change="handleTagsChange" />
      </a-form-item>
      <a-form-item v-if="draft.sourceReferences" :label="t('caseManagement.caseGenerate.sourceReferences')">
        <a-textarea :model-value="draft.sourceReferences" :auto-size="{ minRows: 2, maxRows: 4 }" readonly />
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
  import { computed, onMounted, ref, watch } from 'vue';

  import MsTagsInput from '@/components/pure/ms-tags-input/index.vue';

  import { getCaseModuleTree } from '@/api/modules/case-management/featureCase';
  import { getProjectTemplateList } from '@/api/modules/setting/template';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';

  import type { AiCaseDraft } from '@/models/caseManagement/caseGenerate';
  import type { ModuleTreeNode } from '@/models/common';

  import type { TreeNodeData } from '@arco-design/web-vue';

  const props = defineProps<{
    draft: AiCaseDraft;
  }>();

  const emit = defineEmits<{
    (event: 'update:draft', value: AiCaseDraft): void;
  }>();

  const { t } = useI18n();
  const appStore = useAppStore();
  const moduleTree = ref<ModuleTreeNode[]>([]);
  const templateOptions = ref<Array<{ id: string; name: string }>>([]);

  const tagList = computed(() => {
    if (!props.draft.tags) {
      return [];
    }
    try {
      const parsed = JSON.parse(props.draft.tags);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return props.draft.tags
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean);
    }
  });

  function updateField(field: keyof AiCaseDraft, value: string) {
    emit('update:draft', {
      ...props.draft,
      [field]: value,
    });
  }

  function handleTagsChange(value: string[]) {
    updateField('tags', JSON.stringify(value || []));
  }

  function filterTreeNode(searchValue: string, nodeData: TreeNodeData) {
    return (nodeData as ModuleTreeNode).name.toLowerCase().indexOf(searchValue.toLowerCase()) > -1;
  }

  async function loadOptions() {
    const projectId = appStore.currentProjectId;
    if (!projectId) {
      moduleTree.value = [];
      templateOptions.value = [];
      return;
    }
    const [modules, templates] = await Promise.all([
      getCaseModuleTree({ projectId }),
      getProjectTemplateList({ projectId, scene: 'FUNCTIONAL' }),
    ]);
    moduleTree.value = modules || [];
    templateOptions.value = ((templates as Array<{ id: string; name: string }>) || []).map((item) => ({
      id: item.id,
      name: item.name,
    }));
  }

  onMounted(loadOptions);
  watch(() => appStore.currentProjectId, loadOptions);
</script>
