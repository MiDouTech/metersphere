<template>
  <MsBaseTable v-bind="propsRes" ref="tableRef" :hoverable="false" v-on="propsEvent" @change="changeHandler">
    <template #index="{ rowIndex }">
      <div class="circle text-[12px] font-medium"> {{ rowIndex + 1 }}</div>
    </template>
    <template v-if="!props.isTestPlan" #caseStep="{ record }">
      <a-textarea
        :ref="(el: refItem) => setStepRefMap(el, record)"
        v-model="record.step"
        size="mini"
        :max-length="1000"
        :auto-size="true"
        class="w-max-[267px] param-input"
        :placeholder="t('system.orgTemplate.stepTip')"
        :disabled="props.isDisabled"
        @blur="blurHandler(record, 'step')"
      />
    </template>
    <template v-if="!props.isTestPlan" #expectedResult="{ record }">
      <a-textarea
        :ref="(el: refItem) => setExpectedRefMap(el, record)"
        v-model="record.expected"
        :max-length="1000"
        size="mini"
        :auto-size="true"
        class="w-max-[267px] param-input"
        :placeholder="t('system.orgTemplate.expectationTip')"
        :disabled="props.isDisabled"
        @blur="blurHandler(record, 'expected')"
      />
    </template>
    <template
      v-if="
        showExecuteColumns &&
        hasAnyPermission(['PROJECT_TEST_PLAN:READ+EXECUTE', 'FUNCTIONAL_CASE:READ+UPDATE']) &&
        !props.isDisabledTestPlan
      "
      #actualResult="{ record }"
    >
      <div v-if="props.isPreview">{{ record.actualResult }}</div>
      <a-textarea
        v-else
        v-model="record.actualResult"
        :max-length="1000"
        size="mini"
        :auto-size="true"
        class="w-max-[267px] param-input"
        :placeholder="t('system.orgTemplate.actualResultTip')"
        @change="emitStepChange"
      />
    </template>
    <template #lastExecResult="{ record }">
      <a-dropdown
        v-if="
          showExecuteColumns &&
          hasAnyPermission(['PROJECT_TEST_PLAN:READ+EXECUTE', 'FUNCTIONAL_CASE:READ+UPDATE']) &&
          !props.isDisabledTestPlan &&
          !props.isPreview
        "
        trigger="click"
        @select="(val) => onStepResultSelect(record, val)"
      >
        <a-button type="outline" size="mini" class="exec-result-dropdown arco-btn-outline--secondary">
          <ExecuteResult :execute-result="record.executeResult || LastExecuteResults.PENDING" />
          <icon-down class="ml-1 text-[12px] text-[var(--color-text-4)]" />
        </a-button>
        <template #content>
          <a-doption v-for="item in executionResultList" :key="item.key" :value="item.key">
            <ExecuteResult :execute-result="item.key" />
          </a-doption>
        </template>
      </a-dropdown>
      <span v-else class="text-[var(--color-text-2)]">
        <ExecuteResult :execute-result="record.executeResult || LastExecuteResults.PENDING" />
      </span>
    </template>
    <template v-if="showExecuteColumns" #stepAttachment="{ record }">
      <template v-if="isFeatureCaseExecute">
        <a-button
          v-if="!props.isPreview && !props.isDisabledTestPlan"
          v-permission="['PROJECT_BUG:READ+ADD']"
          type="outline"
          size="mini"
          class="arco-btn-outline--secondary !px-2"
          @click="emit('reportDefect', record)"
        >
          <template #icon>
            <icon-plus class="text-[14px]" />
          </template>
          {{ t('caseManagement.featureCase.reportDefect') }}
        </a-button>
        <span v-else class="text-[var(--color-text-4)]">-</span>
      </template>
      <div
        v-else
        class="step-attach-drop rounded border border-dashed border-[var(--color-text-n8)] p-2"
        @dragover.prevent
        @drop.prevent="(e) => onStepFileDrop(e, record)"
      >
        <a-upload
          v-if="!props.isPreview && !props.isDisabledTestPlan"
          :auto-upload="false"
          :show-file-list="false"
          multiple
          @change="(_, fileItem) => onStepFileSelect(fileItem, record)"
        >
          <template #upload-button>
            <div class="cursor-pointer text-center text-[12px] text-[var(--color-text-4)]">
              {{ t('caseManagement.featureCase.stepAttachTip') }}
            </div>
          </template>
        </a-upload>
        <div
          v-for="(name, idx) in record.attachmentNames || []"
          :key="`${record.id}-${idx}`"
          class="one-line-text text-[12px]"
        >
          {{ name }}
        </div>
      </div>
    </template>
    <template #operation="{ record }">
      <MsTableMoreAction
        v-if="!record.internal"
        :list="moreActionList"
        @select="(item:ActionsItem) => handleMoreActionSelect(item,record)"
      />
    </template>
  </MsBaseTable>
  <div class="mt-2 flex items-center justify-between gap-3">
    <a-button v-if="!props.isDisabled" class="px-0" type="text" @click="addStep">
      <template #icon>
        <icon-plus class="text-[14px]" />
      </template>
      {{ t('system.orgTemplate.addStep') }}
    </a-button>
    <div v-else></div>
    <div
      v-if="
        isFeatureCaseExecute &&
        hasAnyPermission(['PROJECT_TEST_PLAN:READ+EXECUTE', 'FUNCTIONAL_CASE:READ+UPDATE']) &&
        !props.isDisabledTestPlan &&
        !props.isPreview
      "
      class="flex flex-wrap items-center gap-2"
    >
      <button
        v-for="item in caseResultButtons"
        :key="item.key"
        type="button"
        class="case-result-btn"
        :class="[`case-result-btn--${item.theme}`, { 'is-active': props.caseResult === item.key }]"
        @click="emit('setCaseResult', item.key)"
      >
        <MsIcon :type="item.icon" :size="16" class="mr-1" />
        {{ t(item.label) }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue';
  import { TableChangeExtra, TableData } from '@arco-design/web-vue';

  import MsIcon from '@/components/pure/ms-icon-font/index.vue';
  import MsBaseTable from '@/components/pure/ms-table/base-table.vue';
  import { MsTableColumn, MsTableProps } from '@/components/pure/ms-table/type';
  import useTable from '@/components/pure/ms-table/useTable';
  import MsTableMoreAction from '@/components/pure/ms-table-more-action/index.vue';
  import { ActionsItem } from '@/components/pure/ms-table-more-action/types';
  import ExecuteResult from '@/components/business/ms-case-associate/executeResult.vue';

  import { useI18n } from '@/hooks/useI18n';
  import { getGenerateId } from '@/utils';
  import { hasAnyPermission } from '@/utils/permission';

  import type { StepList } from '@/models/caseManagement/featureCase';
  import { LastExecuteResults, StatusType } from '@/enums/caseEnum';

  import { executionResultMap } from '@/views/case-management/caseManagementFeature/components/utils';

  type refItem = Element | ComponentPublicInstance | null;
  const { t } = useI18n();

  const props = withDefaults(
    defineProps<{
      stepList: any;
      isDisabled?: boolean;
      isScrollY?: boolean;
      scrollY?: number;
      isTestPlan?: boolean;
      isDisabledTestPlan?: boolean;
      isPreview?: boolean; // 仅预览不展示状态可操作下拉和文本框
      /** 展示实际结果 / 执行结果 / 提缺陷（功能用例详情与测试计划执行） */
      enableExecute?: boolean;
      /** 用例级执行结果，用于右侧标记按钮高亮 */
      caseResult?: string;
    }>(),
    {
      isDisabled: false,
      isScrollY: true,
      enableExecute: false,
      caseResult: '',
    }
  );

  const emit = defineEmits<{
    (e: 'update:stepList', val: StepList[]): void;
    (e: 'change', val: StepList[]): void;
    (e: 'setCaseResult', result: string): void;
    (e: 'reportDefect', record: StepList): void;
  }>();

  const showExecuteColumns = computed(() => props.isTestPlan || props.enableExecute);
  /** 功能用例详情：用例级结果按钮 + 提缺陷（测试计划仍用步骤附件） */
  const isFeatureCaseExecute = computed(() => props.enableExecute && !props.isTestPlan);

  const executionResultList = computed(() =>
    Object.values(executionResultMap).filter((item) => item.key !== LastExecuteResults.PENDING)
  );

  const caseResultButtons = [
    {
      key: LastExecuteResults.SUCCESS,
      label: 'common.pass',
      icon: StatusType.SUCCESS,
      theme: 'pass',
    },
    {
      key: LastExecuteResults.ERROR,
      label: 'common.fail',
      icon: StatusType.ERROR,
      theme: 'fail',
    },
    {
      key: LastExecuteResults.BLOCKED,
      label: 'common.block',
      icon: StatusType.BLOCKED,
      theme: 'block',
    },
    {
      key: LastExecuteResults.SKIP,
      label: 'caseManagement.featureCase.skip',
      icon: StatusType.SKIP,
      theme: 'skip',
    },
  ];

  // 步骤描述
  const stepData = ref<StepList[]>([
    {
      id: getGenerateId(),
      step: '',
      expected: '',
      showStep: false,
      showExpected: false,
    },
  ]);

  function emitStepChange() {
    emit('update:stepList', stepData.value);
    emit('change', stepData.value);
  }

  function onStepResultSelect(record: StepList, val: string | number | Record<string, any> | undefined) {
    record.executeResult = String(val || '');
    emitStepChange();
  }

  function ensureAttachArrays(record: StepList) {
    if (!record.attachmentIds) record.attachmentIds = [];
    if (!record.attachmentNames) record.attachmentNames = [];
  }

  function appendStepFiles(record: StepList, files: File[]) {
    ensureAttachArrays(record);
    files.forEach((file) => {
      const tempId = getGenerateId();
      record.attachmentIds!.push(tempId);
      record.attachmentNames!.push(file.name);
      if (!(record as any)._pendingFiles) (record as any)._pendingFiles = [];
      (record as any)._pendingFiles.push(file);
    });
    emitStepChange();
  }

  function onStepFileSelect(fileItem: any, record: StepList) {
    const file = fileItem?.file as File | undefined;
    if (file) appendStepFiles(record, [file]);
  }

  function onStepFileDrop(e: DragEvent, record: StepList) {
    const files = Array.from(e.dataTransfer?.files || []);
    if (files.length) appendStepFiles(record, files);
  }

  const executeExtraColumns: MsTableColumn = !showExecuteColumns.value
    ? []
    : [
        {
          title: 'system.orgTemplate.actualResult',
          dataIndex: 'actualResult',
          slotName: 'actualResult',
          showDrag: true,
          showInTable: true,
          width: 180,
        },
        {
          title: 'system.orgTemplate.stepExecutionResult',
          dataIndex: 'executeResult',
          slotName: 'lastExecResult',
          showDrag: true,
          showInTable: true,
          width: 150,
        },
        {
          title: isFeatureCaseExecute.value
            ? 'caseManagement.featureCase.reportDefect'
            : 'caseManagement.featureCase.stepAttachment',
          dataIndex: 'attachmentIds',
          slotName: 'stepAttachment',
          showDrag: true,
          showInTable: true,
          width: isFeatureCaseExecute.value ? 120 : 160,
        },
      ];

  const templateFieldColumns = ref<MsTableColumn>([
    {
      title: 'system.orgTemplate.numberIndex',
      dataIndex: 'index',
      slotName: 'index',
      width: 100,
      showDrag: false,
      showInTable: true,
    },
    {
      title: 'system.orgTemplate.useCaseStep',
      slotName: 'caseStep',
      dataIndex: 'step',
      showDrag: true,
      showInTable: true,
    },
    {
      title: 'system.orgTemplate.expectedResult',
      dataIndex: 'expected',
      slotName: 'expectedResult',
      showDrag: true,
      showInTable: true,
    },
    ...executeExtraColumns,
    {
      title: 'system.orgTemplate.operation',
      slotName: 'operation',
      fixed: 'right',
      width: 120,
      showInTable: true,
      showDrag: false,
    },
  ]);

  const moreActions: ActionsItem[] = [
    {
      label: 'caseManagement.featureCase.copyStep',
      eventTag: 'copyStep',
    },
    {
      label: 'caseManagement.featureCase.InsertStepsBefore',
      eventTag: 'InsertStepsBefore',
    },
    {
      label: 'caseManagement.featureCase.afterInsertingSteps',
      eventTag: 'afterInsertingSteps',
    },
    {
      isDivider: true,
    },
    {
      label: 'common.delete',
      danger: true,
      eventTag: 'delete',
    },
  ];

  const moreActionList = computed(() => {
    return stepData.value.length <= 1 ? moreActions.slice(0, moreActions.length - 2) : moreActions;
  });

  const tableProps = ref<Partial<MsTableProps<StepList>>>({
    columns: templateFieldColumns.value,
    scroll: { x: '100%', y: props.isScrollY ? props.scrollY ?? 400 : '' },
    selectable: false,
    noDisable: true,
    showSetting: false,
    showPagination: false,
    draggable: { type: 'handle' },
    draggableCondition: true,
  });

  const { propsRes, propsEvent, setProps } = useTable(undefined, tableProps.value);

  watch(
    () => props.isDisabled,
    (val) => {
      tableProps.value.draggableCondition = !val;
    },
    {
      immediate: true,
    }
  );

  // 复制步骤
  function copyStep(record: StepList) {
    const index = stepData.value.map((item: any) => item.id).indexOf(record.id);
    const insertItem = {
      ...record,
      id: getGenerateId(),
    };
    stepData.value.splice(index + 1, 0, insertItem);
  }

  // 删除步骤
  function deleteStep(record: StepList) {
    stepData.value = stepData.value.filter((item: any) => item.id !== record.id);
    setProps({ data: stepData.value });
  }

  // 步骤之前插入步骤
  function insertStepsBefore(record: StepList) {
    const index = stepData.value.map((item: any) => item.id).indexOf(record.id);
    const insertItem = {
      id: getGenerateId(),
      step: '',
      expected: '',
      showStep: false,
      showExpected: false,
    };
    stepData.value.splice(index, 0, insertItem);
  }

  // 步骤之后插入步骤
  function afterInsertingSteps(record: StepList) {
    const index = stepData.value.map((item: any) => item.id).indexOf(record.id);
    const insertItem = {
      id: getGenerateId(),
      step: '',
      expected: '',
      showStep: false,
      showExpected: false,
    };
    stepData.value.splice(index + 1, 0, insertItem);
  }

  // 更多操作
  const handleMoreActionSelect = (item: ActionsItem, record: StepList) => {
    switch (item.eventTag) {
      case 'copyStep':
        copyStep(record);
        break;
      case 'InsertStepsBefore':
        insertStepsBefore(record);
        break;
      case 'afterInsertingSteps':
        afterInsertingSteps(record);
        break;
      default:
        deleteStep(record);
        break;
    }
  };

  // 添加步骤
  const addStep = () => {
    stepData.value.push({
      id: getGenerateId(),
      step: '',
      expected: '',
      showStep: false,
      showExpected: false,
    });
  };

  const refStepMap: Record<string, any> = {};
  function setStepRefMap(el: refItem, record: StepList) {
    if (el) {
      refStepMap[`${record.id}`] = el;
    }
  }
  const expectedRefMap: Record<string, any> = {};

  function setExpectedRefMap(el: refItem, record: StepList) {
    if (el) {
      expectedRefMap[`${record.id}`] = el;
    }
  }

  // 失去焦点回调
  function blurHandler(record: StepList, type: string) {
    if (props.isDisabled) return;
    if (type === 'step') {
      record.showStep = false;
    } else {
      record.showExpected = false;
    }
  }

  const tableRef = ref<InstanceType<typeof MsBaseTable> | null>(null);

  watchEffect(() => {
    if (props.isDisabled) {
      tableRef.value?.initColumn(templateFieldColumns.value.slice(0, templateFieldColumns.value.length - 1));
    } else {
      tableRef.value?.initColumn(templateFieldColumns.value);
    }
  });

  function changeHandler(data: TableData[], extra: TableChangeExtra, currentData: TableData[]) {
    if (!currentData || currentData.length === 1) {
      return false;
    }
    stepData.value = data as StepList[];
  }

  watch(
    () => stepData.value,
    (val) => {
      emit('update:stepList', val);
      setProps({ data: stepData.value });
    },
    { deep: true }
  );

  watch(
    () => props.stepList,
    () => {
      stepData.value = props.stepList;
    },
    {
      immediate: true,
    }
  );

  onBeforeMount(() => {
    setProps({ data: stepData.value });
  });
</script>

<style scoped lang="less">
  .circle {
    width: 16px;
    height: 16px;
    line-height: 16px;
    border-radius: 50%;
    text-align: center;
    color: var(--color-text-4);
    background: var(--color-text-n8);
  }
  .exec-result-dropdown {
    @apply inline-flex items-center;

    justify-content: space-between;
    min-width: 110px;
  }
  .case-result-btn {
    @apply inline-flex items-center;

    padding: 0 14px;
    height: 32px;
    font-size: 13px;
    border: 1px solid transparent;
    border-radius: 16px;
    background: #ffffff;
    transition: all 0.15s ease;
    cursor: pointer;
    line-height: 1;
    &:hover {
      opacity: 0.9;
    }
    &--pass {
      border-color: rgb(var(--success-6));
      color: rgb(var(--success-6));
      &.is-active {
        color: #ffffff;
        background: rgb(var(--success-6));
      }
    }
    &--fail {
      border-color: rgb(var(--danger-6));
      color: rgb(var(--danger-6));
      &.is-active {
        color: #ffffff;
        background: rgb(var(--danger-6));
      }
    }
    &--block {
      border-color: rgb(var(--warning-6));
      color: rgb(var(--warning-6));
      &.is-active {
        color: #ffffff;
        background: rgb(var(--warning-6));
      }
    }
    &--skip {
      border-color: var(--color-text-n8);
      color: var(--color-text-4);
      &.is-active {
        color: #ffffff;
        background: var(--color-text-4);
      }
    }
  }
  :deep(.param-input:not(.arco-input-focus, .arco-select-view-focus)) {
    &:not(:hover) {
      border-color: transparent !important;
      .arco-input::placeholder {
        @apply invisible;
      }
      .arco-select-view-icon {
        @apply invisible;
      }
      .arco-select-view-value {
        color: var(--color-text-brand);
      }
    }
  }
  :deep(.arco-textarea-wrapper.arco-textarea-disabled) {
    background: transparent;
  }
</style>
