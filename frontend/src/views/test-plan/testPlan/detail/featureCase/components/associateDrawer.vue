<template>
  <MsCaseAssociate
    v-model:visible="innerVisible"
    :current-select-case="CaseLinkEnum.FUNCTIONAL"
    :project-id="innerProject"
    :get-modules-func="getCaseModuleTree"
    :get-table-func="getTestPlanCaseList"
    :confirm-loading="confirmLoading"
    :associated-ids="[]"
    :type="RequestModuleEnum.CASE_MANAGEMENT"
    :table-params="{ testPlanId: props.testPlanId }"
    :modules-params="{ projectId: innerProject }"
    :module-count-params="{ projectId: innerProject }"
    :view-type="ViewTypeEnum.FUNCTIONAL_CASE"
    hide-project-select
    @close="emit('close')"
    @save="saveHandler"
  />
</template>

<script setup lang="ts">
  import { useVModel } from '@vueuse/core';

  import MsCaseAssociate from '@/components/business/ms-case-associate/index.vue';
  import { RequestModuleEnum } from '@/components/business/ms-case-associate/utils';

  import { getCaseModuleTree } from '@/api/modules/case-management/featureCase';
  import { associateFunctionalCase, getTestPlanCaseList } from '@/api/modules/test-plan/testPlan';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';

  import type { TableQueryParams } from '@/models/common';
  import { ViewTypeEnum } from '@/enums/advancedFilterEnum';
  import { CaseLinkEnum } from '@/enums/caseEnum';

  import Message from '@arco-design/web-vue/es/message';

  const props = defineProps<{
    visible: boolean;
    project: string;
    testPlanId: string;
  }>();

  const emit = defineEmits<{
    (e: 'update:visible', val: boolean): void;
    (e: 'update:project', val: string): void;
    (e: 'success'): void;
    (e: 'close'): void;
  }>();

  const { t } = useI18n();
  const appStore = useAppStore();
  const innerVisible = useVModel(props, 'visible', emit);
  const innerProject = useVModel(props, 'project', emit);
  const confirmLoading = ref(false);

  async function saveHandler(params: TableQueryParams) {
    try {
      confirmLoading.value = true;
      await associateFunctionalCase({
        ...params,
        testPlanId: props.testPlanId,
        projectId: params.projectId || appStore.currentProjectId,
      });
      Message.success(t('caseManagement.featureCase.associatedSuccess'));
      emit('success');
      innerVisible.value = false;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      confirmLoading.value = false;
    }
  }
</script>
