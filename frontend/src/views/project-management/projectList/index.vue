<template>
  <div class="p-4">
    <MsCard simple>
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <div class="text-base font-medium">项目列表</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]">查看当前账号可访问的项目，并切换进入项目工作空间。</div>
        </div>
        <div class="flex flex-wrap items-center gap-3">
          <a-input-search
            v-model="query.keyword"
            class="w-[260px]"
            allow-clear
            placeholder="搜索项目名称、项目 ID 或编号"
            @search="search"
            @clear="search"
          />
          <a-select v-model="query.enable" class="w-[140px]" allow-clear placeholder="项目状态" @change="search">
            <a-option :value="true">启用</a-option>
            <a-option :value="false">禁用</a-option>
          </a-select>
          <a-button :loading="loading" @click="load">刷新</a-button>
        </div>
      </div>

      <a-table
        :data="projects"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1640 }"
        row-key="id"
        @page-change="changePage"
        @page-size-change="changePageSize"
      >
        <template #empty><a-empty description="暂无可访问项目" /></template>
        <template #columns>
          <a-table-column title="进入项目" :width="110" fixed="left">
            <template #cell="{ record }">
              <a-button
                v-visible-permission="{ code: 'PROJECT_LIST_ENTER_BUTTON', permissions: [] }"
                v-operable-permission="{ code: 'PROJECT_LIST_ENTER_BUTTON', permissions: [] }"
                type="primary"
                size="small"
                :disabled="!record.enable"
                @click="openProject(record)"
                >进入项目</a-button
              >
            </template>
          </a-table-column>
          <a-table-column title="项目 ID" :width="250">
            <template #cell="{ record }">
              <a-space>
                <span class="max-w-[190px] truncate">{{ record.id }}</span>
                <a-link
                  v-visible-permission="{ code: 'PROJECT_LIST_COPY_ID_BUTTON', permissions: [] }"
                  v-operable-permission="{ code: 'PROJECT_LIST_COPY_ID_BUTTON', permissions: [] }"
                  @click="copyProjectId(record.id)"
                  >复制</a-link
                >
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="项目名称" data-index="name" :width="220" ellipsis tooltip />
          <a-table-column title="成员" data-index="memberCount" :width="90">
            <template #cell="{ record }">
              <a-tooltip :content="memberPreviewText(record)"
                ><span>{{ record.memberCount ?? 0 }} 人</span></a-tooltip
              >
            </template>
          </a-table-column>
          <a-table-column title="状态" :width="90">
            <template #cell="{ record }">
              <a-tag :color="record.enable ? 'green' : 'gray'">{{ record.enable ? '启用' : '禁用' }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="描述" data-index="description" :width="240" ellipsis tooltip />
          <a-table-column title="创建人" data-index="createUser" :width="140" ellipsis tooltip />
          <a-table-column title="创建时间" :width="180">
            <template #cell="{ record }">{{ formatTime(record.createTime) }}</template>
          </a-table-column>
          <a-table-column title="操作" :width="190" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-link
                  v-visible-permission="{
                    code: 'PROJECT_LIST_ADD_MEMBER_BUTTON',
                    permissions: ['PROJECT_USER:READ+ADD'],
                  }"
                  v-operable-permission="{
                    code: 'PROJECT_LIST_ADD_MEMBER_BUTTON',
                    permissions: ['PROJECT_USER:READ+ADD'],
                  }"
                  :disabled="!record.enable || !record.canAddMember"
                  @click="addMember(record)"
                  >添加成员</a-link
                >
                <a-link
                  v-visible-permission="{ code: 'PROJECT_LIST_ENTER_BUTTON', permissions: [] }"
                  v-operable-permission="{ code: 'PROJECT_LIST_ENTER_BUTTON', permissions: [] }"
                  :disabled="!record.enable"
                  @click="openProject(record)"
                  >进入项目</a-link
                >
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </MsCard>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import MsCard from '@/components/pure/ms-card/index.vue';

  import { pageAccessibleProjects } from '@/api/modules/project-management/project';

  import type { OrgProjectTableItem } from '@/models/setting/system/orgAndProject';
  import { ProjectManagementRouteEnum } from '@/enums/routeEnum';

  import { enterProject } from '@/views/setting/utils';

  const loading = ref(false);
  const router = useRouter();
  const projects = ref<OrgProjectTableItem[]>([]);
  const total = ref(0);
  const query = reactive({ current: 1, pageSize: 20, keyword: '', enable: undefined as boolean | undefined });
  const pagination = computed(() => ({
    current: query.current,
    pageSize: query.pageSize,
    total: total.value,
    showTotal: true,
    showPageSize: true,
  }));

  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');
  const memberPreviewText = (record: OrgProjectTableItem) => {
    const names = record.memberPreview || [];
    if (!names.length) return '暂无成员';
    const remaining = Math.max((record.memberCount || 0) - names.length, 0);
    return remaining ? `${names.join('、')}，另有 ${remaining} 人` : names.join('、');
  };

  async function copyProjectId(id: string) {
    await navigator.clipboard.writeText(id);
    Message.success('项目 ID 已复制');
  }

  async function load() {
    loading.value = true;
    try {
      const result = await pageAccessibleProjects({
        ...query,
        keyword: query.keyword.trim() || undefined,
      });
      projects.value = result.list || [];
      total.value = result.total || 0;
    } finally {
      loading.value = false;
    }
  }

  function search() {
    query.current = 1;
    load();
  }

  function changePage(current: number) {
    query.current = current;
    load();
  }

  function changePageSize(pageSize: number) {
    query.current = 1;
    query.pageSize = pageSize;
    load();
  }

  async function openProject(record: OrgProjectTableItem) {
    await enterProject(record.id, record.organizationId);
  }

  async function addMember(record: OrgProjectTableItem) {
    if (!(await enterProject(record.id, record.organizationId))) return;
    await router.replace({
      name: ProjectManagementRouteEnum.PROJECT_MANAGEMENT_PERMISSION_MEMBER,
      query: { orgId: record.organizationId, pId: record.id, action: 'add-member' },
    });
  }

  onMounted(load);
</script>
