<template>
  <MsCard simple>
    <div class="p-[16px]">
      <div class="mb-[16px] flex items-center justify-between">
        <a-input-search
          v-model:model-value="keyword"
          allow-clear
          :placeholder="t('caseManagement.testReport.searchPlaceholder')"
          class="w-[240px]"
          @search="searchList"
          @press-enter="searchList"
          @clear="searchList"
        />
        <MsButton v-permission="['FUNCTIONAL_CASE:READ+ADD']" @click="openGenerateModal">
          {{ t('caseManagement.testReport.generate') }}
        </MsButton>
      </div>
      <ms-base-table v-bind="propsRes" no-disable v-on="propsEvent">
        <template #name="{ record }">
          <a-button type="text" class="px-0 !text-[14px] !leading-[22px]" @click="openDetail(record.id, false)">
            <div class="one-line-text max-w-[280px]">{{ record.name }}</div>
          </a-button>
        </template>
        <template #planName="{ record }">
          <span>{{ record.planName || t('caseManagement.testReport.projectScope') }}</span>
        </template>
        <template #action="{ record }">
          <MsButton
            v-permission="['FUNCTIONAL_CASE:READ']"
            type="text"
            class="!mr-0"
            @click="openDetail(record.id, false)"
          >
            {{ t('common.detail') }}
          </MsButton>
          <a-divider v-permission="['FUNCTIONAL_CASE:READ']" direction="vertical" :margin="8" />
          <MsButton
            v-permission="['FUNCTIONAL_CASE:READ+UPDATE']"
            type="text"
            class="!mr-0"
            @click="openDetail(record.id, true)"
          >
            {{ t('common.edit') }}
          </MsButton>
          <a-divider v-permission="['FUNCTIONAL_CASE:READ+DELETE']" direction="vertical" :margin="8" />
          <MsButton
            v-permission="['FUNCTIONAL_CASE:READ+DELETE']"
            type="text"
            class="!mr-0"
            @click="handleDelete(record)"
          >
            {{ t('common.delete') }}
          </MsButton>
        </template>
        <template v-if="keyword.trim() === ''" #empty>
          <div class="flex w-full items-center justify-center p-[8px] text-[var(--color-text-4)]">
            {{ t('caseManagement.testReport.tableNoData') }}
            <MsButton v-permission="['FUNCTIONAL_CASE:READ+ADD']" class="ml-[8px]" @click="openGenerateModal">
              {{ t('caseManagement.testReport.generate') }}
            </MsButton>
          </div>
        </template>
      </ms-base-table>
    </div>

    <a-modal
      v-model:visible="generateVisible"
      title-align="start"
      class="ms-modal-small"
      :title="t('caseManagement.testReport.generateTitle')"
      :ok-loading="generateLoading"
      :ok-text="t('caseManagement.testReport.generate')"
      :mask-closable="false"
      unmount-on-close
      @before-ok="handleGenerate"
      @close="resetGenerateForm"
    >
      <a-form ref="generateFormRef" :model="generateForm" layout="vertical">
        <a-form-item
          field="name"
          :label="t('caseManagement.testReport.name')"
          :rules="[{ required: true, message: t('caseManagement.testReport.nameRequired') }]"
        >
          <a-input
            v-model:model-value="generateForm.name"
            :max-length="255"
            :placeholder="t('caseManagement.testReport.namePlaceholder')"
            allow-clear
          />
        </a-form-item>
        <a-form-item field="planId" :label="t('caseManagement.testReport.plan')">
          <a-select
            v-model:model-value="generateForm.planId"
            allow-clear
            allow-search
            :placeholder="t('caseManagement.testReport.planPlaceholder')"
            :loading="planLoading"
          >
            <a-option v-for="item in planOptions" :key="item.id" :value="item.id" :label="item.name">
              {{ item.name }}
            </a-option>
          </a-select>
          <div class="mt-[4px] text-[12px] text-[var(--color-text-4)]">
            {{ t('caseManagement.testReport.planOptional') }}
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </MsCard>
</template>

<script setup lang="ts">
  /**
   * @description 功能测试-测试报告-列表
   */
  import { onBeforeMount } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import MsButton from '@/components/pure/ms-button/index.vue';
  import MsCard from '@/components/pure/ms-card/index.vue';
  import MsBaseTable from '@/components/pure/ms-table/base-table.vue';
  import type { MsTableColumn } from '@/components/pure/ms-table/type';
  import useTable from '@/components/pure/ms-table/useTable';

  import { deleteTestReport, generateTestReport, getTestReportPage } from '@/api/modules/case-management/testReport';
  import { getTestPlanListWithoutPage } from '@/api/modules/test-plan/testPlan';
  import { useI18n } from '@/hooks/useI18n';
  import useModal from '@/hooks/useModal';
  import useAppStore from '@/store/modules/app';
  import { characterLimit } from '@/utils';

  import { TestReportItem } from '@/models/caseManagement/testReport';
  import { CaseManagementRouteEnum } from '@/enums/routeEnum';
  import { TableKeyEnum } from '@/enums/tableEnum';

  import type { FormInstance } from '@arco-design/web-vue';

  defineOptions({
    name: CaseManagementRouteEnum.CASE_MANAGEMENT_TEST_REPORT,
  });

  const { t } = useI18n();
  const { openModal } = useModal();
  const router = useRouter();
  const appStore = useAppStore();

  const keyword = ref('');
  const planNameMap = ref<Record<string, string>>({});
  const planOptions = ref<{ id: string; name: string }[]>([]);
  const planLoading = ref(false);

  const generateVisible = ref(false);
  const generateLoading = ref(false);
  const generateFormRef = ref<FormInstance>();
  const generateForm = reactive({
    name: '',
    planId: undefined as string | undefined,
  });

  const columns: MsTableColumn = [
    {
      title: 'caseManagement.testReport.name',
      dataIndex: 'name',
      slotName: 'name',
      showTooltip: true,
      width: 280,
    },
    {
      title: 'caseManagement.testReport.plan',
      dataIndex: 'planName',
      slotName: 'planName',
      showTooltip: true,
      width: 200,
    },
    {
      title: 'caseManagement.testReport.createUser',
      dataIndex: 'createUser',
      showTooltip: true,
      width: 140,
    },
    {
      title: 'caseManagement.testReport.updateTime',
      dataIndex: 'updateTime',
      width: 180,
    },
    {
      title: 'common.operation',
      slotName: 'action',
      dataIndex: 'operation',
      fixed: 'right',
      width: 200,
    },
  ];

  const { propsRes, propsEvent, loadList, setLoadListParams, setKeyword } = useTable(
    getTestReportPage,
    {
      tableKey: TableKeyEnum.CASE_MANAGEMENT_TEST_REPORT,
      columns,
      scroll: { x: 1000 },
      heightUsed: 256,
      paginationSize: 'mini',
      selectable: false,
      showSetting: false,
    },
    (item: any) => ({
      ...item,
      planName: item.planId ? planNameMap.value[item.planId] || item.planId : '',
      updateTime: item.updateTime ? dayjs(item.updateTime).format('YYYY-MM-DD HH:mm:ss') : '-',
    })
  );

  function searchList() {
    setKeyword(keyword.value);
    setLoadListParams({
      projectId: appStore.currentProjectId,
    });
    loadList();
  }

  async function loadPlanOptions() {
    if (!appStore.currentProjectId) return;
    planLoading.value = true;
    try {
      const list = await getTestPlanListWithoutPage(appStore.currentProjectId);
      planOptions.value = (list || []).map((item) => ({ id: item.id, name: item.name }));
      const map: Record<string, string> = {};
      planOptions.value.forEach((item) => {
        map[item.id] = item.name;
      });
      planNameMap.value = map;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      planLoading.value = false;
    }
  }

  function openDetail(id: string, edit: boolean) {
    router.push({
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_TEST_REPORT_DETAIL,
      query: { id, mode: edit ? 'edit' : 'view' },
    });
  }

  function defaultReportName() {
    return `测试报告-${dayjs().format('YYYYMMDD')}`;
  }

  function openGenerateModal() {
    generateForm.name = defaultReportName();
    generateForm.planId = undefined;
    generateVisible.value = true;
    if (!planOptions.value.length) {
      loadPlanOptions();
    }
  }

  function resetGenerateForm() {
    generateForm.name = defaultReportName();
    generateForm.planId = undefined;
    generateFormRef.value?.resetFields();
  }

  async function handleGenerate(done: (closed: boolean) => void) {
    const valid = await generateFormRef.value?.validate();
    if (valid) {
      done(false);
      return;
    }
    generateLoading.value = true;
    try {
      const res = await generateTestReport({
        projectId: appStore.currentProjectId,
        name: generateForm.name.trim(),
        planId: generateForm.planId || undefined,
      });
      Message.success(t('caseManagement.testReport.generateSuccess'));
      done(true);
      openDetail(res.id, true);
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
      done(false);
    } finally {
      generateLoading.value = false;
    }
  }

  function handleDelete(record: TestReportItem) {
    openModal({
      type: 'error',
      title: t('caseManagement.testReport.deleteTitle', { name: characterLimit(record.name) }),
      content: t('caseManagement.testReport.deleteContent'),
      okText: t('common.confirmDelete'),
      cancelText: t('common.cancel'),
      okButtonProps: { status: 'danger' },
      onBeforeOk: async () => {
        try {
          await deleteTestReport(record.id);
          Message.success(t('common.deleteSuccess'));
          loadList();
        } catch (error) {
          // eslint-disable-next-line no-console
          console.log(error);
        }
      },
      hideCancel: false,
    });
  }

  onBeforeMount(async () => {
    await loadPlanOptions();
    searchList();
  });
</script>

<style lang="less" scoped></style>
