<template>
  <MsCard simple>
    <div v-if="loading" class="flex min-h-[320px] items-center justify-center">
      <a-spin />
    </div>
    <div v-else class="test-report-detail p-[16px]">
      <div class="mb-[16px] flex flex-wrap items-center justify-between gap-[12px]">
        <div class="flex min-w-0 flex-1 items-center gap-[12px]">
          <a-input
            v-if="isEdit"
            v-model:model-value="reportName"
            class="max-w-[420px]"
            :max-length="255"
            :placeholder="t('caseManagement.testReport.namePlaceholder')"
          />
          <div v-else class="truncate text-[16px] font-medium text-[var(--color-text-1)]">{{ reportName }}</div>
          <a-tag v-if="planLabel" size="small" color="arcoblue">{{ planLabel }}</a-tag>
        </div>
        <div class="flex items-center gap-[8px]">
          <MsButton v-permission="['FUNCTIONAL_CASE:READ+UPDATE']" :loading="refreshLoading" @click="handleRefresh">
            {{ t('caseManagement.testReport.refreshStats') }}
          </MsButton>
          <a-button
            v-if="isEdit"
            v-permission="['FUNCTIONAL_CASE:READ+UPDATE']"
            type="primary"
            :loading="saveLoading"
            @click="handleSave"
          >
            {{ t('caseManagement.testReport.save') }}
          </a-button>
          <a-button v-else v-permission="['FUNCTIONAL_CASE:READ+UPDATE']" type="primary" @click="switchToEdit">
            {{ t('common.edit') }}
          </a-button>
        </div>
      </div>

      <!-- 一、版本概览 -->
      <section class="report-section">
        <h3 class="section-title">{{ t('caseManagement.testReport.section.versionOverview') }}</h3>
        <a-textarea
          v-if="isEdit"
          v-model:model-value="form.versionOverview"
          :auto-size="{ minRows: 3, maxRows: 8 }"
          allow-clear
        />
        <div v-else class="readonly-text">{{ form.versionOverview || '-' }}</div>
      </section>

      <!-- 二、测试范围与目的（仅测试内容，无测试依据） -->
      <section class="report-section">
        <h3 class="section-title">{{ t('caseManagement.testReport.section.testScope') }}</h3>
        <div class="mb-[8px] text-[13px] text-[var(--color-text-3)]">
          {{ t('caseManagement.testReport.section.testContent') }}
        </div>
        <a-textarea
          v-if="isEdit"
          v-model:model-value="form.testContent"
          :auto-size="{ minRows: 3, maxRows: 8 }"
          allow-clear
        />
        <div v-else class="readonly-text">{{ form.testContent || '-' }}</div>
      </section>

      <!-- 三、质量评估 -->
      <section class="report-section">
        <h3 class="section-title">{{ t('caseManagement.testReport.section.quality') }}</h3>
        <div class="mb-[8px] flex items-center gap-[8px] text-[14px] text-[var(--color-text-2)]">
          <span>{{ t('caseManagement.testReport.section.execStats') }}</span>
          <a-tooltip v-if="stats.passRateFormulaNote" :content="stats.passRateFormulaNote">
            <icon-question-circle class="text-[var(--color-text-4)]" />
          </a-tooltip>
        </div>
        <div v-if="isEdit" class="mb-[8px] text-[12px] text-[var(--color-text-4)]">
          {{ t('caseManagement.testReport.execStatsEditableTip') }}
        </div>
        <a-table :data="execStatsRows" :pagination="false" :bordered="{ cell: true }" size="small">
          <template #columns>
            <a-table-column :title="t('caseManagement.testReport.metric')" data-index="label" />
            <a-table-column :title="t('caseManagement.testReport.value')">
              <template #cell="{ record }">
                <a-input
                  v-if="isEdit && record.field === 'total'"
                  :model-value="String(editableExec.total)"
                  allow-clear
                  @update:model-value="
                    (v) => {
                      editableExec.total = Number(v) || 0;
                      recalcRates();
                    }
                  "
                />
                <a-input
                  v-else-if="isEdit && record.field === 'pass'"
                  :model-value="String(editableExec.pass)"
                  allow-clear
                  @update:model-value="
                    (v) => {
                      editableExec.pass = Number(v) || 0;
                      recalcRates();
                    }
                  "
                />
                <a-input
                  v-else-if="isEdit && record.field === 'fail'"
                  :model-value="String(editableExec.fail)"
                  allow-clear
                  @update:model-value="
                    (v) => {
                      editableExec.fail = Number(v) || 0;
                      recalcRates();
                    }
                  "
                />
                <a-input
                  v-else-if="isEdit && record.field === 'block'"
                  :model-value="String(editableExec.block)"
                  allow-clear
                  @update:model-value="
                    (v) => {
                      editableExec.block = Number(v) || 0;
                      recalcRates();
                    }
                  "
                />
                <a-input
                  v-else-if="isEdit && record.field === 'execRate'"
                  v-model:model-value="editableExec.execRate"
                  allow-clear
                />
                <a-input
                  v-else-if="isEdit && record.field === 'passRate'"
                  v-model:model-value="editableExec.passRate"
                  allow-clear
                />
                <span v-else>{{ record.value }}</span>
              </template>
            </a-table-column>
          </template>
        </a-table>

        <div class="mb-[8px] mt-[16px] text-[14px] text-[var(--color-text-2)]">
          {{ t('caseManagement.testReport.section.bugCharts') }}
        </div>
        <div class="grid gap-[16px] md:grid-cols-2">
          <div>
            <div class="mb-[8px] text-[13px] text-[var(--color-text-3)]">
              {{ t('caseManagement.testReport.bugHandlerStatus') }}
            </div>
            <div v-if="bugHandlerRows.length" class="mb-[8px] rounded border border-[var(--color-border-2)] p-[8px]">
              <MsChart height="260px" :options="bugHandlerBarOptions" />
            </div>
            <a-table :data="bugHandlerRows" :pagination="false" :bordered="{ cell: true }" size="small">
              <template #columns>
                <a-table-column :title="t('caseManagement.testReport.handler')" data-index="handler" />
                <a-table-column :title="t('caseManagement.testReport.status')" data-index="status" />
                <a-table-column :title="t('caseManagement.testReport.count')" data-index="count" />
              </template>
              <template #empty>
                {{ t('caseManagement.testReport.chartEmpty') }}
              </template>
            </a-table>
          </div>
          <div>
            <div class="mb-[8px] text-[13px] text-[var(--color-text-3)]">
              {{ t('caseManagement.testReport.bugType') }}
              <span v-if="stats.bugTypeMessage" class="ml-[4px] text-[12px] text-[var(--color-text-4)]">
                ({{ stats.bugTypeMessage }})
              </span>
            </div>
            <div v-if="bugTypeRows.length" class="mb-[8px] rounded border border-[var(--color-border-2)] p-[8px]">
              <MsChart height="260px" :options="bugTypePieOptions" />
            </div>
            <a-table :data="bugTypeRows" :pagination="false" :bordered="{ cell: true }" size="small">
              <template #columns>
                <a-table-column :title="t('caseManagement.testReport.type')" data-index="type" />
                <a-table-column :title="t('caseManagement.testReport.count')" data-index="count" />
              </template>
              <template #empty>
                {{ t('caseManagement.testReport.chartEmpty') }}
              </template>
            </a-table>
          </div>
        </div>
      </section>

      <!-- 四、遗留问题与风险 -->
      <section class="report-section">
        <h3 class="section-title">{{ t('caseManagement.testReport.section.risk') }}</h3>
        <div class="mb-[8px] text-[13px] text-[var(--color-text-3)]">
          {{ t('caseManagement.testReport.riskCaseTitle') }}
        </div>
        <a-table
          :data="stats.riskCases || []"
          :pagination="false"
          :bordered="{ cell: true }"
          size="small"
          class="mb-[12px]"
        >
          <template #columns>
            <a-table-column :title="t('caseManagement.testReport.risk.num')" data-index="num" :width="120" />
            <a-table-column :title="t('caseManagement.testReport.risk.name')" data-index="name" />
            <a-table-column
              :title="t('caseManagement.testReport.risk.result')"
              data-index="lastExecResult"
              :width="140"
            />
          </template>
          <template #empty>
            {{ t('caseManagement.testReport.riskEmpty') }}
          </template>
        </a-table>
        <div class="mb-[8px] text-[13px] text-[var(--color-text-3)]">
          {{ t('caseManagement.testReport.openBug.title') }}
        </div>
        <a-table
          :data="stats.openBugs || []"
          :pagination="false"
          :bordered="{ cell: true }"
          size="small"
          class="mb-[12px]"
        >
          <template #columns>
            <a-table-column :title="t('caseManagement.testReport.openBug.num')" data-index="num" :width="120" />
            <a-table-column :title="t('caseManagement.testReport.openBug.name')" data-index="title" />
            <a-table-column
              :title="t('caseManagement.testReport.openBug.status')"
              data-index="statusName"
              :width="120"
            />
            <a-table-column
              :title="t('caseManagement.testReport.openBug.handler')"
              data-index="handleUserName"
              :width="140"
            />
          </template>
          <template #empty>
            {{ t('caseManagement.testReport.openBugEmpty') }}
          </template>
        </a-table>
        <div class="mb-[8px] text-[13px] text-[var(--color-text-3)]">
          {{ t('caseManagement.testReport.section.riskNote') }}
        </div>
        <a-textarea
          v-if="isEdit"
          v-model:model-value="form.riskNote"
          :auto-size="{ minRows: 2, maxRows: 6 }"
          allow-clear
        />
        <div v-else class="readonly-text">{{ form.riskNote || '-' }}</div>
      </section>

      <!-- 五、测试结论与建议 -->
      <section class="report-section">
        <h3 class="section-title">{{ t('caseManagement.testReport.section.conclusion') }}</h3>
        <div class="mb-[8px] text-[13px] text-[var(--color-text-3)]">
          {{ t('caseManagement.testReport.section.conclusionResult') }}
        </div>
        <a-textarea
          v-if="isEdit"
          v-model:model-value="form.conclusionResult"
          :auto-size="{ minRows: 2, maxRows: 6 }"
          allow-clear
          class="mb-[12px]"
        />
        <div v-else class="readonly-text mb-[12px]">{{ form.conclusionResult || '-' }}</div>
        <div class="mb-[8px] text-[13px] text-[var(--color-text-3)]">
          {{ t('caseManagement.testReport.section.conclusionSuggestion') }}
        </div>
        <a-textarea
          v-if="isEdit"
          v-model:model-value="form.conclusionSuggestion"
          :auto-size="{ minRows: 2, maxRows: 6 }"
          allow-clear
        />
        <div v-else class="readonly-text">{{ form.conclusionSuggestion || '-' }}</div>
      </section>

      <!-- 页脚（无「六、附件」） -->
      <section class="report-section">
        <h3 class="section-title">{{ t('caseManagement.testReport.section.footer') }}</h3>
        <div class="grid gap-[12px] md:grid-cols-2">
          <a-form-item :label="t('caseManagement.testReport.footer.author')" class="mb-0">
            <a-input v-if="isEdit" v-model:model-value="form.author" allow-clear />
            <span v-else>{{ form.author || '-' }}</span>
          </a-form-item>
          <a-form-item :label="t('caseManagement.testReport.footer.date')" class="mb-0">
            <a-input v-if="isEdit" v-model:model-value="form.date" allow-clear />
            <span v-else>{{ form.date || '-' }}</span>
          </a-form-item>
        </div>
      </section>
    </div>
  </MsCard>
</template>

<script setup lang="ts">
  /**
   * @description 功能测试-测试报告-详情/编辑
   */
  import { useRoute, useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import MsChart from '@/components/pure/chart/index.vue';
  import MsButton from '@/components/pure/ms-button/index.vue';
  import MsCard from '@/components/pure/ms-card/index.vue';

  import {
    getTestReportDetail,
    refreshTestReportStats,
    updateTestReport,
  } from '@/api/modules/case-management/testReport';
  import { getTestPlanListWithoutPage } from '@/api/modules/test-plan/testPlan';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';
  import useUserStore from '@/store/modules/user';

  import {
    TestReportContent,
    TestReportExecStats,
    TestReportItem,
    TestReportStats,
  } from '@/models/caseManagement/testReport';
  import { CaseManagementRouteEnum } from '@/enums/routeEnum';

  defineOptions({
    name: CaseManagementRouteEnum.CASE_MANAGEMENT_TEST_REPORT_DETAIL,
  });

  const { t } = useI18n();
  const route = useRoute();
  const router = useRouter();
  const appStore = useAppStore();
  const userStore = useUserStore();

  const loading = ref(true);
  const saveLoading = ref(false);
  const refreshLoading = ref(false);
  const reportId = computed(() => (route.query.id as string) || '');
  const isEdit = computed(() => route.query.mode === 'edit');

  const reportName = ref('');
  const planId = ref<string | null>(null);
  const planLabel = ref('');
  const rawContent = ref<TestReportContent>({});

  const form = reactive({
    versionOverview: '',
    testContent: '',
    riskNote: '',
    conclusionResult: '',
    conclusionSuggestion: '',
    author: '',
    date: '',
  });

  const defaultExec = (): TestReportExecStats => ({
    total: 0,
    pass: 0,
    fail: 0,
    block: 0,
    execRate: '-',
    passRate: '-',
  });

  const defaultStats = (): TestReportStats => ({
    exec: defaultExec(),
    bugHandlerStatus: [],
    bugType: [],
    riskCases: [],
    openBugs: [],
  });

  const stats = ref<TestReportStats>(defaultStats());
  const editableExec = reactive<TestReportExecStats>(defaultExec());

  const execStatsRows = computed(() => {
    const exec = isEdit.value ? editableExec : stats.value.exec || defaultExec();
    return [
      { field: 'total', label: t('caseManagement.testReport.exec.total'), value: exec.total, editable: true },
      { field: 'pass', label: t('caseManagement.testReport.exec.pass'), value: exec.pass, editable: true },
      { field: 'fail', label: t('caseManagement.testReport.exec.fail'), value: exec.fail, editable: true },
      { field: 'block', label: t('caseManagement.testReport.exec.block'), value: exec.block, editable: true },
      { field: 'execRate', label: t('caseManagement.testReport.exec.execRate'), value: exec.execRate, editable: true },
      { field: 'passRate', label: t('caseManagement.testReport.exec.passRate'), value: exec.passRate, editable: true },
    ];
  });

  const bugHandlerRows = computed(() => {
    return (stats.value.bugHandlerStatus || []).map((item: Record<string, any>, index: number) => ({
      key: String(index),
      handler: item.handler || item.handleUser || item.handleUserName || item.name || '-',
      status: item.status || item.handleStatus || '-',
      count: item.count ?? item.value ?? 0,
    })) as any[];
  });

  const bugTypeRows = computed(() => {
    return (stats.value.bugType || []).map((item: Record<string, any>, index: number) => ({
      key: String(index),
      type: item.type || item.name || item.label || '-',
      count: item.count ?? item.value ?? 0,
    })) as any[];
  });

  const chartColors = ['#3370FF', '#F77234', '#00B42A', '#F53F3F', '#811FA3', '#14C9C9', '#FADC19', '#D91AD9'];

  const bugHandlerBarOptions = computed(() => {
    const rows = bugHandlerRows.value;
    const handlers = [...new Set(rows.map((r) => r.handler))];
    const statuses = [...new Set(rows.map((r) => r.status))];
    const series = statuses.map((status, idx) => ({
      name: status,
      type: 'bar',
      stack: 'total',
      barMaxWidth: 36,
      itemStyle: { color: chartColors[idx % chartColors.length] },
      data: handlers.map((handler) => {
        const hit = rows.find((r) => r.handler === handler && r.status === status);
        return hit ? Number(hit.count) || 0 : 0;
      }),
    }));
    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { type: 'scroll', bottom: 0 },
      grid: { left: 40, right: 16, top: 24, bottom: 48 },
      xAxis: {
        type: 'category',
        data: handlers,
        axisLabel: { interval: 0, rotate: handlers.length > 4 ? 30 : 0 },
      },
      yAxis: { type: 'value', minInterval: 1 },
      series,
    };
  });

  const bugTypePieOptions = computed(() => {
    const data = bugTypeRows.value.map((r, idx) => ({
      name: r.type,
      value: Number(r.count) || 0,
      itemStyle: { color: chartColors[idx % chartColors.length] },
    }));
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { type: 'scroll', bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['35%', '65%'],
          center: ['50%', '45%'],
          data,
          label: { formatter: '{b}\n{c}' },
        },
      ],
    };
  });

  function parseJson<T>(raw: string | undefined | null, fallback: T): T {
    if (!raw) return fallback;
    try {
      return JSON.parse(raw) as T;
    } catch {
      return fallback;
    }
  }

  function formatRate(numerator: number, denominator: number): string {
    if (denominator <= 0) return '-';
    return `${((numerator / denominator) * 100).toFixed(2)}%`;
  }

  function recalcRates() {
    const { total, pass, fail, block } = editableExec;
    editableExec.execRate = formatRate(pass + fail + block, total);
    editableExec.passRate = formatRate(pass, total - block - fail);
  }

  function applyExecToEditable(exec: Partial<TestReportExecStats> | undefined) {
    const base = { ...defaultExec(), ...(exec || {}) };
    editableExec.total = Number(base.total) || 0;
    editableExec.pass = Number(base.pass) || 0;
    editableExec.fail = Number(base.fail) || 0;
    editableExec.block = Number(base.block) || 0;
    editableExec.execRate = base.execRate || '-';
    editableExec.passRate = base.passRate || '-';
  }

  function versionOverviewToText(value: TestReportContent['versionOverview']): string {
    if (value == null) return '';
    if (typeof value === 'string') return value;
    if (typeof value === 'object') {
      if (typeof (value as any).text === 'string') return (value as any).text;
      if (typeof (value as any).html === 'string') return (value as any).html;
      const entries = Object.entries(value);
      if (!entries.length) return '';
      return entries.map(([k, v]) => `${k}: ${v ?? ''}`).join('\n');
    }
    return String(value);
  }

  function applyReport(report: TestReportItem) {
    reportName.value = report.name || '';
    planId.value = report.planId || null;
    rawContent.value = parseJson<TestReportContent>(report.content, {});
    const content = rawContent.value;
    form.versionOverview = versionOverviewToText(content.versionOverview);
    form.testContent = content.testScope?.content || '';
    form.riskNote = content.riskNote || '';
    form.conclusionResult = content.conclusion?.result || '';
    form.conclusionSuggestion = content.conclusion?.suggestion || '';
    form.author = content.footer?.author || userStore.name || report.createUser || '';
    form.date = content.footer?.date || dayjs(report.createTime || undefined).format('YYYY-MM-DD');
    const snapshot = parseJson<Partial<TestReportStats>>(report.statsSnapshot, {});
    stats.value = {
      ...defaultStats(),
      ...snapshot,
      exec: {
        ...defaultExec(),
        ...(snapshot.exec || {}),
      },
    };
    // 可编辑数字优先取 content.execStats，否则用快照
    applyExecToEditable(content.execStats || snapshot.exec);
    stats.value.exec = { ...editableExec };
  }

  function buildContentPayload(): string {
    const content: TestReportContent = {
      ...rawContent.value,
      versionOverview: form.versionOverview,
      testScope: {
        ...(rawContent.value.testScope || {}),
        content: form.testContent,
      },
      conclusion: {
        ...(rawContent.value.conclusion || {}),
        result: form.conclusionResult,
        suggestion: form.conclusionSuggestion,
      },
      riskNote: form.riskNote,
      execStats: { ...editableExec },
      footer: {
        author: form.author,
        date: form.date,
      },
    };
    return JSON.stringify(content);
  }

  async function resolvePlanLabel() {
    if (!planId.value) {
      planLabel.value = t('caseManagement.testReport.noPlan');
      return;
    }
    try {
      const list = await getTestPlanListWithoutPage(appStore.currentProjectId);
      const found = (list || []).find((item) => item.id === planId.value);
      planLabel.value = found?.name || planId.value;
    } catch {
      planLabel.value = planId.value;
    }
  }

  async function loadDetail() {
    if (!reportId.value) {
      router.replace({ name: CaseManagementRouteEnum.CASE_MANAGEMENT_TEST_REPORT });
      return;
    }
    loading.value = true;
    try {
      const report = await getTestReportDetail(reportId.value);
      applyReport(report);
      await resolvePlanLabel();
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      loading.value = false;
    }
  }

  function switchToEdit() {
    router.replace({
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_TEST_REPORT_DETAIL,
      query: { id: reportId.value, mode: 'edit' },
    });
  }

  async function handleSave() {
    if (!reportName.value.trim()) {
      Message.warning(t('caseManagement.testReport.nameRequired'));
      return;
    }
    saveLoading.value = true;
    try {
      const report = await updateTestReport({
        id: reportId.value,
        name: reportName.value.trim(),
        content: buildContentPayload(),
      });
      applyReport(report);
      Message.success(t('caseManagement.testReport.saveSuccess'));
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      saveLoading.value = false;
    }
  }

  async function handleRefresh() {
    refreshLoading.value = true;
    try {
      // 先保留本地未保存文字，刷新后由服务端覆盖 execStats
      const localTextContent = buildContentPayload();
      const localParsed = parseJson<TestReportContent>(localTextContent, {});
      const report = await refreshTestReportStats(reportId.value);
      const serverContent = parseJson<TestReportContent>(report.content, {});
      // 用本地文字覆盖服务端 content 中除 execStats 外的字段后再展示；保存时再落库
      const merged: TestReportContent = {
        ...serverContent,
        versionOverview: localParsed.versionOverview,
        testScope: localParsed.testScope,
        conclusion: localParsed.conclusion,
        riskNote: localParsed.riskNote,
        footer: localParsed.footer,
        execStats: serverContent.execStats,
      };
      applyReport({ ...report, content: JSON.stringify(merged) });
      Message.success(t('caseManagement.testReport.refreshSuccess'));
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      refreshLoading.value = false;
    }
  }

  watch(
    () => route.query.id,
    () => {
      loadDetail();
    }
  );

  onBeforeMount(() => {
    loadDetail();
  });
</script>

<style lang="less" scoped>
  .report-section {
    margin-bottom: 24px;
    padding-bottom: 16px;
    border-bottom: 1px solid var(--color-border-2);
    &:last-child {
      margin-bottom: 0;
      border-bottom: none;
    }
  }
  .section-title {
    margin: 0 0 12px;
    font-size: 15px;
    font-weight: 600;
    color: var(--color-text-1);
  }
  .readonly-text {
    min-height: 22px;
    white-space: pre-wrap;
    color: var(--color-text-1);
    word-break: break-word;
    line-height: 22px;
  }
</style>
