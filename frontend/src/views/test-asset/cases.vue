<template>
  <div class="p-4">
    <MsCard simple>
      <div class="mb-4 flex items-start justify-between gap-4">
        <div>
          <div class="text-base font-medium">用例资产</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]"
            >用例项目仅是资产分类目录，不会创建可进入的业务项目。</div
          >
        </div>
        <a-space class="asset-toolbar-actions">
          <a-button v-permission="['AI_EXECUTION:READ']" @click="router.push({ name: 'AgentPageObject' })"
            >页面对象</a-button
          >
          <a-button v-permission="['AI_EXECUTION:READ']" @click="router.push({ name: 'AgentBusinessFlow' })"
            >业务流</a-button
          >
          <a-button
            v-if="canAdd"
            v-visible-permission="{
              code: 'CASE_ASSET_ADD_BUTTON',
              permissions: ['CASE_ASSET:READ+ADD'],
              typeList: ['ORGANIZATION'],
            }"
            v-operable-permission="{
              code: 'CASE_ASSET_ADD_BUTTON',
              permissions: ['CASE_ASSET:READ+ADD'],
              typeList: ['ORGANIZATION'],
            }"
            :loading="backfillLoading"
            @click="backfillCatalogs"
            >同步历史项目及用例</a-button
          >
          <a-button
            v-if="canAdd"
            v-visible-permission="{
              code: 'CASE_ASSET_ADD_BUTTON',
              permissions: ['CASE_ASSET:READ+ADD'],
              typeList: ['ORGANIZATION'],
            }"
            v-operable-permission="{
              code: 'CASE_ASSET_ADD_BUTTON',
              permissions: ['CASE_ASSET:READ+ADD'],
              typeList: ['ORGANIZATION'],
            }"
            type="primary"
            @click="openCatalogCreate"
            >新建用例项目</a-button
          >
        </a-space>
      </div>
      <a-alert v-if="historySyncJob" class="mt-3" :type="historySyncJob.failed ? 'warning' : 'info'">
        历史同步 {{ historySyncJob.status }}：项目成功 {{ historySyncJob.success || 0 }}/{{
          historySyncJob.total || 0
        }}， 用例新增 {{ historySyncJob.caseCreated || 0 }}、更新 {{ historySyncJob.caseUpdated || 0 }}、跳过
        {{ historySyncJob.caseSkipped || 0 }}
        <a-button
          v-if="canAdd && ['FAILED', 'PARTIAL_SUCCESS'].includes(historySyncJob.status)"
          v-operable-permission="{
            code: 'CASE_ASSET_ADD_BUTTON',
            permissions: ['CASE_ASSET:READ+ADD'],
            typeList: ['ORGANIZATION'],
          }"
          class="ml-2"
          size="mini"
          @click="retryHistorySync"
          >重试失败项</a-button
        >
      </a-alert>

      <div class="asset-layout">
        <aside class="catalog-panel">
          <a-input-search
            v-model="catalogQuery.keyword"
            allow-clear
            placeholder="搜索目录名称或 ID"
            @search="searchCatalogs"
            @clear="searchCatalogs"
          />
          <a-spin :loading="catalogLoading" class="mt-3 block">
            <a-empty v-if="!catalogs.length" description="暂无用例项目" />
            <div
              v-for="item in catalogs"
              :key="item.id"
              class="catalog-item"
              :class="{ selected: item.id === selectedCatalogId }"
              @click="selectCatalog(item.id)"
            >
              <div class="min-w-0 flex-1">
                <div class="catalog-name">{{ item.name }}</div>
                <div class="catalog-id">ID: {{ item.id }}</div>
              </div>
              <a-dropdown v-if="canUpdate || canDelete" trigger="click" @click.stop>
                <a-button type="text" size="mini"><icon-more /></a-button>
                <template #content>
                  <a-doption
                    v-if="canUpdate"
                    v-operable-permission="{
                      code: 'CASE_ASSET_UPDATE_BUTTON',
                      permissions: ['CASE_ASSET:READ+UPDATE'],
                      typeList: ['ORGANIZATION'],
                    }"
                    @click="openCatalogEdit(item)"
                    >重命名</a-doption
                  >
                  <a-doption
                    v-if="canDelete"
                    v-operable-permission="{
                      code: 'CASE_ASSET_DELETE_BUTTON',
                      permissions: ['CASE_ASSET:READ+DELETE'],
                      typeList: ['ORGANIZATION'],
                    }"
                    class="text-red-6"
                    @click="confirmDeleteCatalog(item)"
                    >删除</a-doption
                  >
                </template>
              </a-dropdown>
            </div>
          </a-spin>
          <a-pagination
            class="mt-3"
            simple
            :current="catalogQuery.current"
            :page-size="catalogQuery.pageSize"
            :total="catalogTotal"
            @change="changeCatalogPage"
          />
        </aside>

        <main class="min-w-0 flex-1">
          <div class="mb-4 flex flex-wrap items-center gap-3">
            <span class="font-medium">{{ selectedCatalog?.name || '请选择用例项目' }}</span>
            <a-input-search
              v-model="caseQuery.keyword"
              class="w-[260px]"
              allow-clear
              placeholder="搜索用例 ID 或名称"
              @search="searchCases"
              @clear="searchCases"
            />
            <a-select
              v-model="caseQuery.creationSources"
              multiple
              allow-clear
              class="w-[200px]"
              placeholder="建立方式"
              @change="searchCases"
            >
              <a-option v-for="item in sourceOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
            </a-select>
            <a-tree-select
              v-model="caseQuery.assetCategoryId"
              allow-clear
              allow-search
              class="w-[200px]"
              :data="assetCategories"
              :field-names="{ key: 'id', title: 'name', children: 'children' }"
              placeholder="资产分类"
              @change="searchCases"
            />
            <a-checkbox v-model="caseQuery.includeAssetCategoryDescendants" @change="searchCases">含子分类</a-checkbox>
            <div class="asset-toolbar-actions ml-auto flex gap-2">
              <a-select
                v-model="environmentProfileId"
                class="w-[220px]"
                allow-clear
                placeholder="选择 AI 执行环境"
                :loading="environmentLoading"
              >
                <a-option v-for="profile in environmentProfiles" :key="profile.id" :value="profile.id">
                  {{ profile.name }}
                </a-option>
              </a-select>
              <a-button
                :loading="readinessLoading"
                :disabled="!environmentProfileId || !cases.length"
                @click="checkReadiness"
                >检查 AI 可执行性</a-button
              >
              <a-button
                v-if="canAdd"
                v-operable-permission="{
                  code: 'CASE_ASSET_ADD_BUTTON',
                  permissions: ['CASE_ASSET:READ+ADD'],
                  typeList: ['ORGANIZATION'],
                }"
                :disabled="!selectedCatalogId"
                @click="caseModalVisible = true"
                >新建用例</a-button
              >
              <a-button
                v-if="canImport"
                v-visible-permission="{
                  code: 'CASE_ASSET_IMPORT_BUTTON',
                  permissions: ['CASE_ASSET:READ+IMPORT'],
                  typeList: ['ORGANIZATION'],
                }"
                v-operable-permission="{
                  code: 'CASE_ASSET_IMPORT_BUTTON',
                  permissions: ['CASE_ASSET:READ+IMPORT'],
                  typeList: ['ORGANIZATION'],
                }"
                :disabled="!selectedCatalogId"
                @click="importVisible = true"
                >导入测试用例</a-button
              >
              <a-button :loading="caseLoading" :disabled="!selectedCatalogId" @click="loadCases">刷新</a-button>
              <a-button v-permission="['TEST_ASSET_CATEGORY:MANAGE']" @click="categoryDrawerVisible = true"
                >分类管理</a-button
              >
            </div>
          </div>
          <a-table
            :data="cases"
            :loading="caseLoading"
            :pagination="casePagination"
            row-key="id"
            @page-change="changeCasePage"
            @page-size-change="changeCasePageSize"
          >
            <template #empty
              ><a-empty :description="selectedCatalogId ? '当前目录暂无资产用例' : '请先选择用例项目'"
            /></template>
            <template #columns>
              <a-table-column title="用例 ID" data-index="id" :width="205" ellipsis tooltip>
                <template #cell="{ record }"
                  ><a-link @click="viewCase(record)">{{ record.id }}</a-link></template
                >
              </a-table-column>
              <a-table-column title="用例名称" data-index="name" :width="250" ellipsis tooltip>
                <template #cell="{ record }"
                  ><a-link @click="viewCase(record)">{{ record.name }}</a-link></template
                >
              </a-table-column>
              <a-table-column title="用例等级" :width="100"
                ><template #cell="{ record }">{{ getPriority(record) }}</template></a-table-column
              >
              <a-table-column title="标签" :width="180"
                ><template #cell="{ record }"
                  ><a-space wrap
                    ><a-tag v-for="tag in record.tags || []" :key="tag">{{ tag }}</a-tag></a-space
                  ></template
                ></a-table-column
              >
              <a-table-column title="建立方式" :width="120">
                <template #cell="{ record }">
                  <a-tooltip v-if="record.creationSource === 'UNKNOWN'" content="历史来源信息不足，待治理">
                    <a-tag :color="sourceMeta(record.creationSource).color">{{
                      sourceMeta(record.creationSource).label
                    }}</a-tag>
                  </a-tooltip>
                  <a-tag v-else :color="sourceMeta(record.creationSource).color">{{
                    sourceMeta(record.creationSource).label
                  }}</a-tag>
                </template>
              </a-table-column>
              <a-table-column title="资产分类" :width="220">
                <template #cell="{ record }">
                  <a-tree-select
                    v-if="canAssignAssetCategory"
                    v-model="record.assetCategoryId"
                    class="asset-category-select w-full"
                    allow-clear
                    allow-search
                    :data="assetCategories"
                    :field-names="{ key: 'id', title: 'name', children: 'children' }"
                    @click.stop
                    @change="(value) => changeCaseCategory(record, value as string | undefined)"
                  >
                    <template #label
                      ><a-tooltip :content="record.assetCategoryPath || '未分类'">{{
                        record.assetCategoryPath || '未分类'
                      }}</a-tooltip></template
                    >
                  </a-tree-select>
                  <a-tooltip v-else :content="record.assetCategoryPath || '未分类'">
                    <span>{{ record.assetCategoryPath || '未分类' }}</span>
                  </a-tooltip>
                </template>
              </a-table-column>
              <a-table-column title="已引用项目" :width="190">
                <template #cell="{ record }">
                  <a-tooltip
                    :content="(record.referencedProjects || []).map((p: any) => p.name).join('、') || '尚未进入测试计划'"
                  >
                    <a-link v-if="record.referencedProjectCount" @click="openReferencedProjects(record)">{{
                      (record.referencedProjects || []).map((p: any) => p.name).join('、')
                    }}</a-link>
                    <span v-else>-</span>
                    <span v-if="record.referencedProjectCount > 3"> 等 {{ record.referencedProjectCount }} 个</span>
                  </a-tooltip>
                </template>
              </a-table-column>
              <a-table-column title="AI 可执行性" :width="180">
                <template #cell="{ record }">
                  <a-tooltip :content="readinessByCase[record.id]?.missingItems?.join('、') || '请选择环境并执行检查'">
                    <a-tag :color="readinessColor(readinessByCase[record.id]?.automationReadiness)">
                      {{ readinessByCase[record.id]?.automationReadiness || '未检查' }}
                    </a-tag>
                  </a-tooltip>
                </template>
              </a-table-column>
              <a-table-column title="更新人" data-index="updateUserName" :width="110" />
              <a-table-column title="更新时间" :width="165"
                ><template #cell="{ record }">{{ formatTime(record.updateTime) }}</template></a-table-column
              >
              <a-table-column v-if="canDelete" title="操作" :width="90" fixed="right"
                ><template #cell="{ record }"
                  ><a-link
                    v-operable-permission="{
                      code: 'CASE_ASSET_DELETE_BUTTON',
                      permissions: ['CASE_ASSET:READ+DELETE'],
                      typeList: ['ORGANIZATION'],
                    }"
                    status="danger"
                    @click="confirmDeleteCase(record)"
                    >删除</a-link
                  ></template
                ></a-table-column
              >
            </template>
          </a-table>
        </main>
      </div>
    </MsCard>

    <a-modal
      v-model:visible="catalogModalVisible"
      :title="editingCatalog ? '重命名用例项目' : '新建用例项目'"
      @ok="saveCatalog"
    >
      <a-form :model="{ catalogName }" layout="vertical"
        ><a-form-item label="目录名称" required
          ><a-input v-model="catalogName" :max-length="255" show-word-limit /></a-form-item
      ></a-form>
      <a-alert v-if="!editingCatalog">此操作只在用例资产中新建分类目录，不会新增业务项目。</a-alert>
    </a-modal>
    <a-modal v-model:visible="caseModalVisible" title="新建资产用例" @ok="saveCase">
      <a-form :model="caseForm" layout="vertical">
        <a-form-item label="用例名称" required><a-input v-model="caseForm.name" /></a-form-item>
        <a-form-item label="编辑模式"
          ><a-radio-group v-model="caseForm.caseEditType"
            ><a-radio value="STEP">步骤</a-radio><a-radio value="TEXT">文本</a-radio></a-radio-group
          ></a-form-item
        >
        <a-form-item label="前置条件"><a-textarea v-model="caseForm.prerequisite" /></a-form-item>
        <a-form-item :label="caseForm.caseEditType === 'STEP' ? '用例步骤（JSON/文本）' : '用例描述'"
          ><a-textarea v-model="caseForm.body" :auto-size="{ minRows: 4, maxRows: 10 }"
        /></a-form-item>
        <a-form-item label="预期结果"><a-textarea v-model="caseForm.expectedResult" /></a-form-item>
      </a-form>
    </a-modal>
    <CaseAssetFileImport v-model:visible="importVisible" :catalog-id="selectedCatalogId" @success="loadCases" />
    <TestAssetCategoryDrawer
      v-model:visible="categoryDrawerVisible"
      @changed="
        async () => {
          assetCategories = await listTestAssetCategories();
          await loadCases();
        }
      "
    />
    <a-modal v-model:visible="referencedVisible" title="已引用项目" :footer="false">
      <a-table
        :data="referencedProjects"
        :loading="referencedLoading"
        :pagination="referencedPagination"
        row-key="id"
        @page-change="changeReferencedPage"
      >
        <template #columns
          ><a-table-column title="项目名称" data-index="name" /><a-table-column title="项目 ID" data-index="id"
        /></template>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message, Modal } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import MsCard from '@/components/pure/ms-card/index.vue';
  import CaseAssetFileImport from './components/CaseAssetFileImport.vue';
  import TestAssetCategoryDrawer from './components/TestAssetCategoryDrawer.vue';

  import {
    type AiCaseExecutability,
    type AiEnvironmentProfile,
    assignTestAssetCategory,
    checkAiCaseExecutability,
    listAiEnvironmentProfiles,
    listTestAssetCategories,
    type TestAssetCategory,
    type TestAssetCreationSource,
  } from '@/api/modules/ai-execution';
  import {
    backfillCaseAssetCatalogs,
    type CaseAssetCatalog,
    type CaseAssetHistorySyncJob,
    createCaseAsset,
    createCaseAssetCatalog,
    deleteCaseAsset,
    deleteCaseAssetCatalog,
    getCaseAssetHistorySyncJob,
    getCaseAssetList,
    getCaseAssetReferencedProjects,
    getLatestCaseAssetHistorySyncJob,
    pageCaseAssetCatalogs,
    retryCaseAssetHistorySyncJob,
    updateCaseAssetCatalog,
  } from '@/api/modules/case-management/featureCase';
  import useAppStore from '@/store/modules/app';
  import { hasAnyPermission } from '@/utils/permission';

  import type { CaseManagementTable } from '@/models/caseManagement/featureCase';
  import { TestAssetRouteEnum } from '@/enums/routeEnum';

  const router = useRouter();
  const appStore = useAppStore();
  const canAdd = hasAnyPermission(['CASE_ASSET:READ+ADD']);
  const canUpdate = hasAnyPermission(['CASE_ASSET:READ+UPDATE']);
  const canDelete = hasAnyPermission(['CASE_ASSET:READ+DELETE']);
  const canImport = hasAnyPermission(['CASE_ASSET:READ+IMPORT']);
  const canAssignAssetCategory = hasAnyPermission(['TEST_ASSET_CATEGORY:ASSIGN']);
  const categoryDrawerVisible = ref(false);
  const catalogLoading = ref(false);
  const backfillLoading = ref(false);
  const historySyncJob = ref<CaseAssetHistorySyncJob>();
  const caseLoading = ref(false);
  const catalogs = ref<CaseAssetCatalog[]>([]);
  const catalogTotal = ref(0);
  const selectedCatalogId = ref('');
  const cases = ref<CaseManagementTable[]>([]);
  const environmentProfiles = ref<AiEnvironmentProfile[]>([]);
  const environmentProfileId = ref('');
  const environmentLoading = ref(false);
  const readinessLoading = ref(false);
  const readinessByCase = reactive<Record<string, AiCaseExecutability>>({});
  const caseTotal = ref(0);
  const catalogQuery = reactive({ current: 1, pageSize: 12, keyword: '' });
  const caseQuery = reactive({
    current: 1,
    pageSize: 20,
    keyword: '',
    creationSources: [] as TestAssetCreationSource[],
    assetCategoryId: undefined as string | undefined,
    includeAssetCategoryDescendants: true,
  });
  const assetCategories = ref<TestAssetCategory[]>([]);
  const sourceOptions: Array<{ value: TestAssetCreationSource; label: string; color: string }> = [
    { value: 'MANUAL', label: '人工建立', color: 'blue' },
    { value: 'AI', label: 'AI 建立', color: 'purple' },
    { value: 'IMPORT', label: '导入建立', color: 'cyan' },
    { value: 'SYNC', label: '同步建立', color: 'orange' },
    { value: 'AUTOMATION', label: '自动化建立', color: 'green' },
    { value: 'UNKNOWN', label: '来源不明', color: 'gray' },
  ];
  const sourceMeta = (source?: TestAssetCreationSource) =>
    sourceOptions.find((item) => item.value === source) || sourceOptions.at(-1)!;
  const selectedCatalog = computed(() => catalogs.value.find((item) => item.id === selectedCatalogId.value));
  const casePagination = computed(() => ({
    current: caseQuery.current,
    pageSize: caseQuery.pageSize,
    total: caseTotal.value,
    showTotal: true,
    showPageSize: true,
  }));
  const catalogModalVisible = ref(false);
  const editingCatalog = ref<CaseAssetCatalog>();
  const catalogName = ref('');
  const caseModalVisible = ref(false);
  const importVisible = ref(false);
  const referencedVisible = ref(false);
  const referencedLoading = ref(false);
  const referencedCaseId = ref('');
  const referencedProjects = ref<Array<{ id: string; name: string }>>([]);
  const referencedQuery = reactive({ current: 1, pageSize: 20, total: 0 });
  const referencedPagination = computed(() => ({
    current: referencedQuery.current,
    pageSize: referencedQuery.pageSize,
    total: referencedQuery.total,
  }));
  const caseForm = reactive({
    name: '',
    caseEditType: 'STEP' as 'STEP' | 'TEXT',
    prerequisite: '',
    body: '',
    expectedResult: '',
  });
  const formatTime = (value?: number | string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  const getPriority = (record: CaseManagementTable) =>
    record.priority || record.customFields?.find((field: any) => field.fieldName === '用例等级')?.value || '-';
  function readinessColor(readiness?: string) {
    if (readiness === 'READY') return 'green';
    if (readiness === 'PARTIAL') return 'orange';
    if (readiness === 'NOT_READY') return 'red';
    return 'gray';
  }

  async function loadEnvironmentProfiles() {
    if (!appStore.currentProjectId) return;
    environmentLoading.value = true;
    try {
      environmentProfiles.value = await listAiEnvironmentProfiles(appStore.currentProjectId);
      if (!environmentProfiles.value.some((item) => item.id === environmentProfileId.value)) {
        environmentProfileId.value = environmentProfiles.value.find((item) => item.enabled)?.id || '';
      }
    } finally {
      environmentLoading.value = false;
    }
  }

  async function checkReadiness() {
    if (!appStore.currentProjectId || !environmentProfileId.value || !cases.value.length) return;
    readinessLoading.value = true;
    try {
      const result = await checkAiCaseExecutability({
        projectId: appStore.currentProjectId,
        environmentProfileId: environmentProfileId.value,
        caseIds: cases.value.map((item) => item.id),
      });
      result.forEach((item) => {
        readinessByCase[item.caseId] = item;
        const source = cases.value.find((record) => record.id === item.caseId || (record as any).refId === item.caseId);
        if (source) readinessByCase[source.id] = item;
      });
    } finally {
      readinessLoading.value = false;
    }
  }

  watch(() => appStore.currentProjectId, loadEnvironmentProfiles);

  async function loadCases() {
    if (!selectedCatalogId.value) {
      cases.value = [];
      caseTotal.value = 0;
      return;
    }
    caseLoading.value = true;
    try {
      const result = await getCaseAssetList({
        catalogId: selectedCatalogId.value,
        ...caseQuery,
        keyword: caseQuery.keyword.trim() || undefined,
      } as any);
      cases.value = result.list || [];
      caseTotal.value = result.total || 0;
    } finally {
      caseLoading.value = false;
    }
  }
  async function changeCaseCategory(record: CaseManagementTable, categoryId?: string) {
    const previous = record.assetCategoryId;
    try {
      const metadata = await assignTestAssetCategory(record.projectId, 'CASE', record.refId || record.id, categoryId);
      record.assetCategoryId = metadata.categoryId;
      record.assetCategoryName = metadata.categoryName;
      record.assetCategoryPath = metadata.categoryPath;
    } catch {
      record.assetCategoryId = previous;
    }
  }
  async function loadCatalogs() {
    catalogLoading.value = true;
    try {
      const result = await pageCaseAssetCatalogs({
        ...catalogQuery,
        keyword: catalogQuery.keyword.trim() || undefined,
      });
      catalogs.value = result.list || [];
      catalogTotal.value = result.total || 0;
      if (!catalogs.value.some((item) => item.id === selectedCatalogId.value))
        selectedCatalogId.value = catalogs.value[0]?.id || '';
      await loadCases();
    } finally {
      catalogLoading.value = false;
    }
  }
  function searchCatalogs() {
    catalogQuery.current = 1;
    loadCatalogs();
  }
  function changeCatalogPage(current: number) {
    catalogQuery.current = current;
    loadCatalogs();
  }
  function selectCatalog(id: string) {
    selectedCatalogId.value = id;
    caseQuery.current = 1;
    loadCases();
  }
  function searchCases() {
    caseQuery.current = 1;
    loadCases();
  }
  function changeCasePage(current: number) {
    caseQuery.current = current;
    loadCases();
  }
  function changeCasePageSize(pageSize: number) {
    caseQuery.current = 1;
    caseQuery.pageSize = pageSize;
    loadCases();
  }
  function openCatalogCreate() {
    editingCatalog.value = undefined;
    catalogName.value = '';
    catalogModalVisible.value = true;
  }
  const waitHistorySync = async (jobId: string, deadline = Date.now() + 10 * 60 * 1000): Promise<void> => {
    historySyncJob.value = await getCaseAssetHistorySyncJob(jobId);
    if (['SUCCESS', 'FAILED', 'PARTIAL_SUCCESS'].includes(historySyncJob.value.status)) {
      if (historySyncJob.value.failed) Message.warning('历史同步部分失败，可点击“重试失败项”继续');
      else Message.success('历史项目及用例同步完成');
      await loadCatalogs();
      return;
    }
    if (Date.now() >= deadline) {
      Message.warning('历史同步仍在后台执行，请稍后刷新查看');
      return;
    }
    await new Promise<void>((resolve) => {
      window.setTimeout(resolve, 1000);
    });
    await waitHistorySync(jobId, deadline);
  };
  async function backfillCatalogs() {
    backfillLoading.value = true;
    try {
      const result = await backfillCaseAssetCatalogs();
      Message.success(`历史同步任务已提交，共 ${result.total} 个项目`);
      await waitHistorySync(result.jobId);
    } finally {
      backfillLoading.value = false;
    }
  }
  async function retryHistorySync() {
    if (!historySyncJob.value) return;
    await retryCaseAssetHistorySyncJob(historySyncJob.value.jobId);
    backfillLoading.value = true;
    try {
      await waitHistorySync(historySyncJob.value.jobId);
    } finally {
      backfillLoading.value = false;
    }
  }

  async function restoreLatestHistorySync() {
    const latest = await getLatestCaseAssetHistorySyncJob();
    historySyncJob.value = 'jobId' in latest ? latest : undefined;
    if (historySyncJob.value?.status === 'RUNNING' || historySyncJob.value?.status === 'PENDING') {
      backfillLoading.value = true;
      try {
        await waitHistorySync(historySyncJob.value.jobId);
      } finally {
        backfillLoading.value = false;
      }
    }
  }
  function openCatalogEdit(item: CaseAssetCatalog) {
    editingCatalog.value = item;
    catalogName.value = item.name;
    catalogModalVisible.value = true;
  }
  async function saveCatalog() {
    if (!catalogName.value.trim()) {
      Message.warning('请输入目录名称');
      return false;
    }
    if (editingCatalog.value) await updateCaseAssetCatalog(editingCatalog.value.id, catalogName.value.trim());
    else await createCaseAssetCatalog(catalogName.value.trim());
    Message.success('保存成功');
    await loadCatalogs();
    return true;
  }
  function confirmDeleteCatalog(item: CaseAssetCatalog) {
    Modal.warning({
      title: '删除用例项目',
      content: '仅空目录可删除，删除不会影响业务项目。',
      hideCancel: false,
      onOk: async () => {
        await deleteCaseAssetCatalog(item.id);
        await loadCatalogs();
      },
    });
  }
  async function saveCase() {
    if (!caseForm.name.trim()) {
      Message.warning('请输入用例名称');
      return false;
    }
    const created = await createCaseAsset({
      catalogId: selectedCatalogId.value,
      name: caseForm.name.trim(),
      caseEditType: caseForm.caseEditType,
      prerequisite: caseForm.prerequisite,
      steps: caseForm.caseEditType === 'STEP' ? caseForm.body : '',
      textDescription: caseForm.caseEditType === 'TEXT' ? caseForm.body : '',
      expectedResult: caseForm.expectedResult,
    });
    Object.assign(caseForm, { name: '', caseEditType: 'STEP', prerequisite: '', body: '', expectedResult: '' });
    Message.success('资产用例已创建');
    await router.push({
      name: TestAssetRouteEnum.TEST_ASSET_CASE_DETAIL,
      params: { catalogId: selectedCatalogId.value, caseId: created.id },
    });
    return true;
  }
  function viewCase(record: CaseManagementTable) {
    router.push({
      name: TestAssetRouteEnum.TEST_ASSET_CASE_DETAIL,
      params: { catalogId: selectedCatalogId.value, caseId: record.id },
      query: { keyword: caseQuery.keyword, page: String(caseQuery.current) },
    });
  }
  function confirmDeleteCase(record: CaseManagementTable) {
    Modal.warning({
      title: '删除资产用例',
      content: '删除后不能再被新导入；已创建的项目副本和历史测试计划不受影响。',
      hideCancel: false,
      onOk: async () => {
        await deleteCaseAsset(selectedCatalogId.value, record.id);
        await loadCases();
      },
    });
  }
  async function loadReferencedProjects() {
    referencedLoading.value = true;
    try {
      const result = await getCaseAssetReferencedProjects(
        selectedCatalogId.value,
        referencedCaseId.value,
        referencedQuery.current,
        referencedQuery.pageSize
      );
      referencedProjects.value = result.list || [];
      referencedQuery.total = result.total || 0;
    } finally {
      referencedLoading.value = false;
    }
  }
  function openReferencedProjects(record: CaseManagementTable) {
    referencedCaseId.value = record.id;
    referencedQuery.current = 1;
    referencedVisible.value = true;
    loadReferencedProjects();
  }
  function changeReferencedPage(current: number) {
    referencedQuery.current = current;
    loadReferencedProjects();
  }
  onMounted(async () => {
    assetCategories.value = await listTestAssetCategories();
    await loadEnvironmentProfiles();
    await loadCatalogs();
    await restoreLatestHistorySync();
  });
</script>

<style scoped lang="less">
  .asset-layout {
    display: flex;
    gap: 16px;
  }
  .asset-toolbar-actions {
    margin-right: 100px;
  }
  @media (max-width: 1200px) {
    .asset-toolbar-actions {
      margin-right: 0;
      flex-wrap: wrap;
    }
  }
  .catalog-panel {
    padding-right: 16px;
    width: 310px;
    border-right: 1px solid var(--color-neutral-3);
    flex: none;
  }
  .catalog-item {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 8px;
    padding: 10px 8px;
    border-radius: 6px;
    cursor: pointer;
  }
  .catalog-item:hover,
  .catalog-item.selected {
    background: rgb(var(--primary-1));
  }
  .catalog-name {
    overflow: hidden;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: #722ed1;
  }
  .catalog-id {
    overflow: hidden;
    margin-top: 3px;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--color-text-3);
  }
</style>
