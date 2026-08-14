<template>
  <div class="p-4">
    <MsCard simple>
      <div class="mb-4">
        <div class="text-base font-medium">用例资产</div>
        <div class="mt-1 text-sm text-[var(--color-text-3)]">跨项目只读查看当前账号有权访问的测试用例。</div>
      </div>

      <a-tabs :active-key="activeScope" type="rounded" size="small" @change="changeScope">
        <a-tab-pane v-if="showProjectTab" key="project" title="项目" />
        <a-tab-pane v-if="showSystemTab" key="system" title="系统" />
      </a-tabs>

      <template v-if="activeScope === 'project'">
        <div class="asset-layout">
          <div class="project-panel">
            <a-input-search
              v-model="projectQuery.keyword"
              allow-clear
              placeholder="搜索项目名称或 ID"
              @search="searchProjects"
              @clear="searchProjects"
            />
            <a-spin :loading="projectLoading" class="mt-3 block">
              <a-list :bordered="false" :data="projects">
                <template #item="{ item }">
                  <a-list-item
                    class="cursor-pointer"
                    :class="{ 'selected-project': item.id === selectedProjectId }"
                    @click="selectProject(item.id)"
                  >
                    <a-list-item-meta :title="item.name" :description="item.id" />
                  </a-list-item>
                </template>
              </a-list>
            </a-spin>
            <a-pagination
              class="mt-3"
              simple
              :current="projectQuery.current"
              :page-size="projectQuery.pageSize"
              :total="projectTotal"
              @change="changeProjectPage"
            />
          </div>

          <div class="min-w-0 flex-1">
            <div class="mb-4 flex flex-wrap items-center gap-3">
              <span class="font-medium">{{ selectedProject?.name || '请选择项目' }}</span>
              <a-input-search
                v-model="caseQuery.keyword"
                class="w-[260px]"
                allow-clear
                placeholder="搜索用例名称、编号或标签"
                @search="searchCases"
                @clear="searchCases"
              />
              <a-button :loading="caseLoading" :disabled="!selectedProjectId" @click="loadCases">刷新</a-button>
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
                ><a-empty :description="selectedProjectId ? '当前项目暂无测试用例' : '请选择项目查看测试用例'"
              /></template>
              <template #columns>
                <a-table-column title="用例 ID" data-index="id" :width="210" ellipsis tooltip />
                <a-table-column title="用例名称" data-index="name" :width="240" ellipsis tooltip />
                <a-table-column title="所属项目" :width="170"
                  ><template #cell>{{ selectedProject?.name || '-' }}</template></a-table-column
                >
                <a-table-column title="所属模块" data-index="moduleName" :width="160" ellipsis tooltip />
                <a-table-column title="用例等级" :width="100"
                  ><template #cell="{ record }">{{ getPriority(record) }}</template></a-table-column
                >
                <a-table-column title="状态" :width="110"
                  ><template #cell="{ record }">{{ statusText(record.reviewStatus) }}</template></a-table-column
                >
                <a-table-column title="标签" :width="190"
                  ><template #cell="{ record }"
                    ><a-space wrap
                      ><a-tag v-for="tag in record.tags || []" :key="tag">{{ tag }}</a-tag></a-space
                    ></template
                  ></a-table-column
                >
                <a-table-column title="创建人" data-index="createUserName" :width="120" />
                <a-table-column title="更新人" data-index="updateUserName" :width="120" />
                <a-table-column title="更新时间" :width="170"
                  ><template #cell="{ record }">{{ formatTime(record.updateTime) }}</template></a-table-column
                >
                <a-table-column title="操作" :width="90" fixed="right"
                  ><template #cell="{ record }"
                    ><a-link @click="viewCase(record)">查看</a-link></template
                  ></a-table-column
                >
              </template>
            </a-table>
          </div>
        </div>
      </template>
      <div v-else class="flex min-h-[420px] items-center justify-center">
        <a-empty description="系统级用例资产暂未开放" />
      </div>
    </MsCard>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import dayjs from 'dayjs';

  import MsCard from '@/components/pure/ms-card/index.vue';

  import { getCaseAssetList } from '@/api/modules/case-management/featureCase';
  import { pageCaseAssetProjects } from '@/api/modules/project-management/project';
  import { hasTabVisible } from '@/utils/permission';

  import type { CaseManagementTable } from '@/models/caseManagement/featureCase';
  import type { OrgProjectTableItem } from '@/models/setting/system/orgAndProject';
  import { CaseManagementRouteEnum, TestAssetRouteEnum } from '@/enums/routeEnum';

  import { enterProject } from '@/views/setting/utils';

  const route = useRoute();
  const router = useRouter();
  const activeScope = computed<'project' | 'system'>(() =>
    route.name === TestAssetRouteEnum.TEST_ASSET_CASES_SYSTEM ? 'system' : 'project'
  );
  const showProjectTab = computed(() => hasTabVisible('TEST_ASSET_CASE_PROJECT_TAB', ['PROJECT']));
  const showSystemTab = computed(() => hasTabVisible('TEST_ASSET_CASE_SYSTEM_TAB', ['PROJECT']));
  const projectLoading = ref(false);
  const caseLoading = ref(false);
  const projects = ref<OrgProjectTableItem[]>([]);
  const projectTotal = ref(0);
  const selectedProjectId = ref('');
  const cases = ref<CaseManagementTable[]>([]);
  const caseTotal = ref(0);
  const projectQuery = reactive({ current: 1, pageSize: 10, keyword: '' });
  const caseQuery = reactive({ current: 1, pageSize: 20, keyword: '' });
  const selectedProject = computed(() => projects.value.find((project) => project.id === selectedProjectId.value));
  const casePagination = computed(() => ({
    current: caseQuery.current,
    pageSize: caseQuery.pageSize,
    total: caseTotal.value,
    showTotal: true,
    showPageSize: true,
  }));
  const formatTime = (value?: number | string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  const statusText = (status?: string) =>
    ({ UN_REVIEWED: '未评审', UNDER_REVIEWED: '评审中', PASSED: '通过', UN_PASS: '未通过' }[status || ''] ||
    status ||
    '-');
  const getPriority = (record: CaseManagementTable) =>
    record.priority || record.customFields?.find((field) => field.fieldName === '用例等级')?.value || '-';

  async function loadProjects() {
    projectLoading.value = true;
    try {
      const result = await pageCaseAssetProjects({
        ...projectQuery,
        keyword: projectQuery.keyword.trim() || undefined,
      });
      projects.value = result.list || [];
      projectTotal.value = result.total || 0;
      if (!projects.value.some((item) => item.id === selectedProjectId.value))
        selectedProjectId.value = projects.value[0]?.id || '';
    } finally {
      projectLoading.value = false;
    }
  }
  async function loadCases() {
    if (!selectedProjectId.value || activeScope.value !== 'project') {
      cases.value = [];
      caseTotal.value = 0;
      return;
    }
    caseLoading.value = true;
    try {
      const result = await getCaseAssetList({
        projectId: selectedProjectId.value,
        ...caseQuery,
        keyword: caseQuery.keyword.trim() || undefined,
      });
      cases.value = result.list || [];
      caseTotal.value = result.total || 0;
    } finally {
      caseLoading.value = false;
    }
  }
  async function reloadProjectsAndCases() {
    await loadProjects();
    caseQuery.current = 1;
    await loadCases();
  }
  function searchProjects() {
    projectQuery.current = 1;
    reloadProjectsAndCases();
  }
  async function changeProjectPage(current: number) {
    projectQuery.current = current;
    await reloadProjectsAndCases();
  }
  function selectProject(id: string) {
    selectedProjectId.value = id;
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
  function changeScope(scope: string | number) {
    router.push({
      name:
        scope === 'system' ? TestAssetRouteEnum.TEST_ASSET_CASES_SYSTEM : TestAssetRouteEnum.TEST_ASSET_CASES_PROJECT,
    });
  }
  async function viewCase(record: CaseManagementTable) {
    const project = selectedProject.value;
    if (!project || !(await enterProject(project.id, project.organizationId))) return;
    await router.replace({
      name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE_DETAIL,
      params: { mode: 'edit' },
      query: { id: record.id, pId: project.id },
    });
  }
  watch(activeScope, (scope) => {
    if (scope === 'project' && !projects.value.length) reloadProjectsAndCases();
  });
  onMounted(() => {
    if (activeScope.value === 'project' && showProjectTab.value) reloadProjectsAndCases();
  });
</script>

<style scoped lang="less">
  .asset-layout {
    display: flex;
    gap: 16px;
  }
  .project-panel {
    padding-right: 16px;
    width: 300px;
    border-right: 1px solid var(--color-neutral-3);
    flex: none;
  }
  .selected-project {
    color: rgb(var(--primary-6));
    background: rgb(var(--primary-1));
  }
</style>
