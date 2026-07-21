<template>
  <MsDialog
    v-model:visible="isVisible"
    dialog-size="small"
    :title="t('caseManagement.featureCase.batchEditExecResult')"
    ok-text="common.update"
    :confirm="confirmHandler"
    :close="closeHandler"
    unmount-on-close
  >
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-form-item
        field="lastExecuteResult"
        :label="t('caseManagement.featureCase.execResult')"
        asterisk-position="end"
        :rules="[{ required: true, message: t('common.pleaseSelect') }]"
      >
        <a-radio-group v-model="form.lastExecuteResult" direction="vertical">
          <a-radio v-for="item in execOptions" :key="item.key" :value="item.key">
            <ExecuteResult :execute-result="item.key" />
          </a-radio>
        </a-radio-group>
      </a-form-item>
    </a-form>
  </MsDialog>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { FormInstance, Message } from '@arco-design/web-vue';

  import MsDialog from '@/components/pure/ms-dialog/index.vue';
  import type { BatchActionQueryParams } from '@/components/pure/ms-table/type';
  import ExecuteResult from '@/components/business/ms-case-associate/executeResult.vue';

  import { batchEditAttrs } from '@/api/modules/case-management/featureCase';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';

  import { TableQueryParams } from '@/models/common';
  import { LastExecuteResults } from '@/enums/caseEnum';

  import { executionResultMap } from './utils';

  const props = defineProps<{
    visible: boolean;
    batchParams: BatchActionQueryParams;
    activeFolder: string;
    offspringIds: string[];
    condition?: TableQueryParams;
  }>();

  const emit = defineEmits<{
    (e: 'update:visible', v: boolean): void;
    (e: 'success'): void;
  }>();

  const { t } = useI18n();
  const appStore = useAppStore();
  const formRef = ref<FormInstance>();
  const form = ref({ lastExecuteResult: LastExecuteResults.SUCCESS as string });

  const isVisible = computed({
    get: () => props.visible,
    set: (v) => emit('update:visible', v),
  });

  const execOptions = computed(() =>
    Object.values(executionResultMap).filter((item) => item.key !== LastExecuteResults.PENDING)
  );

  watch(
    () => props.visible,
    (v) => {
      if (v) {
        form.value.lastExecuteResult = LastExecuteResults.SUCCESS;
      }
    }
  );

  function closeHandler() {
    isVisible.value = false;
  }

  async function confirmHandler() {
    const err = await formRef.value?.validate();
    if (err) return;
    const params: TableQueryParams = {
      selectIds: props.batchParams.selectedIds || [],
      selectAll: !!props.batchParams.selectAll,
      excludeIds: props.batchParams.excludeIds || [],
      projectId: appStore.currentProjectId,
      moduleIds: props.activeFolder === 'all' ? [] : [props.activeFolder, ...(props.offspringIds || [])],
      lastExecuteResult: form.value.lastExecuteResult,
      condition: {
        ...(props.condition || {}),
      },
    };
    await batchEditAttrs(params);
    Message.success(t('common.updateSuccess'));
    emit('success');
    closeHandler();
  }
</script>
