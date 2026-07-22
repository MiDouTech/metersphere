<template>
  <MsCard simple no-content-padding>
    <a-tabs v-model:active-key="activeTab" class="no-content" @change="handleTabChange">
      <a-tab-pane key="execute" :title="t('menu.caseManagement.executeCase')" />
      <a-tab-pane key="xmind" :title="t('menu.caseManagement.xmindCase')" />
    </a-tabs>
    <a-divider margin="0" />
    <!-- 用 v-show 保留执行用例树/表状态，避免切到 Xmind 再回来时因 keep-alive 缓存跳过 mountedLoad 导致无数据 -->
    <div v-show="activeTab === 'execute'" class="h-full">
      <!-- 高级搜索时仍展示左侧树，便于点模块退出高级搜索并刷新列表 -->
      <MsSplitBox :not-show-first="false">
        <template #first>
          <div class="p-[16px] pb-0">
            <div class="feature-case h-[100%] min-w-0 overflow-x-hidden">
              <a-input
                v-model:model-value="groupKeyword"
                :placeholder="t('caseManagement.caseReview.folderSearchPlaceholder')"
                allow-clear
                class="mb-[16px]"
                :max-length="255"
              />
              <div class="case h-[38px] min-w-0">
                <div
                  class="flex min-w-0 flex-1 items-center"
                  :class="getActiveClass('all')"
                  @click="setActiveFolder('all')"
                >
                  <MsIcon type="icon-icon_folder_filled1" class="folder-icon shrink-0" />
                  <div class="folder-name mx-[4px] truncate">{{ t('caseManagement.featureCase.allCase') }}</div>
                  <div class="folder-count shrink-0">({{ modulesCount.all || 0 }})</div>
                </div>
                <div class="ml-auto flex shrink-0 items-center">
                  <a-tooltip :content="isExpandAll ? t('common.expandAllSubModule') : t('common.collapseAllSubModule')">
                    <MsButton type="icon" status="secondary" class="!mr-0 p-[4px]" @click="expandHandler">
                      <MsIcon :type="isExpandAll ? 'icon-icon_folder_collapse1' : 'icon-icon_folder_expansion1'" />
                    </MsButton>
                  </a-tooltip>
                  <MsPopConfirm
                    v-if="hasAnyPermission(['FUNCTIONAL_CASE:READ+ADD'])"
                    v-model:visible="addSubVisible"
                    :is-delete="false"
                    :title="t('caseManagement.featureCase.addSubModule')"
                    :all-names="rootModulesName"
                    :loading="confirmLoading"
                    :ok-text="t('common.confirm')"
                    :field-config="{
                      placeholder: t('caseManagement.featureCase.addGroupTip'),
                      nameExistTipText: t('project.fileManagement.nameExist'),
                    }"
                    @confirm="confirmHandler"
                  >
                    <MsButton type="icon" class="!mr-0 p-[2px]">
                      <MsIcon
                        type="icon-icon_create_planarity"
                        size="18"
                        class="text-[rgb(var(--primary-5))] hover:text-[rgb(var(--primary-4))]"
                      />
                    </MsButton>
                  </MsPopConfirm>
                </div>
              </div>
              <div class="h-[calc(100vh-220px)]">
                <FeatureCaseTree
                  ref="caseTreeRef"
                  v-model:selected-keys="selectedKeys"
                  v-model:group-keyword="groupKeyword"
                  :all-names="rootModulesName"
                  :active-folder="activeFolder"
                  :is-expand-all="isExpandAll"
                  :modules-count="modulesCount"
                  :is-modal="false"
                  @case-node-select="caseNodeSelect"
                  @init="setRootModules"
                  @drag-update="dragUpdate"
                  @delete-node="deleteNode"
                />
              </div>
            </div>
          </div>
          <div class="flex-1">
            <a-divider class="!my-0 !mb-0" />
            <div class="case h-[40px] !px-[24px]" @click="setActiveFolder('recycle')">
              <div class="flex items-center" :class="getActiveClass('recycle')">
                <MsIcon type="icon-icon_delete-trash_outlined1" class="folder-icon" />
                <div class="folder-name mx-[4px]">{{ t('common.recycle') }}</div>
              </div>
              <div class="folder-count">{{ recycleModulesCount.all || 0 }}</div>
            </div>
          </div>
        </template>
        <template #second>
          <div class="h-full p-[16px_16px]">
            <CaseTable
              ref="caseTableRef"
              :active-folder="activeFolder"
              :offspring-ids="offspringIds"
              :module-name="activeFolderName"
              :module-count-is-init="moduleCountIsInit"
              @init="initModulesCount"
              @init-modules="initModules"
              @set-active-folder="setActiveFolder('all')"
            />
          </div>
        </template>
      </MsSplitBox>
    </div>
    <!-- Xmind 同样用 v-show，避免切 Tab 销毁重建后列表请求被路由守卫取消 -->
    <div v-show="activeTab === 'xmind'" class="h-full">
      <XmindCasePage :active="activeTab === 'xmind'" />
    </div>
  </MsCard>
</template>

<script setup lang="ts">
  /**
   * @description 功能测试-功能用例
   */
  import { computed, nextTick, ref } from 'vue';
  import { useRoute, useRouter } from 'vue-router';

  import MsButton from '@/components/pure/ms-button/index.vue';
  import MsCard from '@/components/pure/ms-card/index.vue';
  import MsIcon from '@/components/pure/ms-icon-font/index.vue';
  import MsPopConfirm, { ConfirmValue } from '@/components/pure/ms-popconfirm/index.vue';
  import MsSplitBox from '@/components/pure/ms-split-box/index.vue';
  import { MsTreeNodeData } from '@/components/business/ms-tree/types';
  import CaseTable from './components/caseTable.vue';
  import FeatureCaseTree from './components/caseTree.vue';
  import XmindCasePage from './xmind/index.vue';

  import { createCaseModuleTree } from '@/api/modules/case-management/featureCase';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';
  import useFeatureCaseStore from '@/store/modules/case/featureCase';
  import { hasAnyPermission } from '@/utils/permission';

  import type { CreateOrUpdateModule } from '@/models/caseManagement/featureCase';
  import { TableQueryParams } from '@/models/common';
  import { CaseManagementRouteEnum } from '@/enums/routeEnum';

  import Message from '@arco-design/web-vue/es/message';

  defineOptions({
    name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE,
  });

  const route = useRoute();

  const router = useRouter();

  const appStore = useAppStore();
  const { t } = useI18n();
  const currentProjectId = computed(() => appStore.currentProjectId);
  const featureCaseStore = useFeatureCaseStore();

  type CaseSubTab = 'execute' | 'xmind';
  const activeTab = ref<CaseSubTab>((route.query.tab as CaseSubTab) === 'xmind' ? 'xmind' : 'execute');

  function handleTabChange(key: string | number) {
    activeTab.value = key as CaseSubTab;
    // 延后改 query，降低与列表请求并发被守卫取消的概率（列表侧已 ignoreCancelToken）
    nextTick(() => {
      const query = { ...route.query };
      if (key === 'execute') {
        delete query.tab;
      } else {
        query.tab = String(key);
      }
      router.replace({ query });
    });
  }

  const isExpandAll = ref(false);
  const activeCaseType = ref<'folder' | 'module'>('folder'); // 激活用例类型
  const rootModulesName = ref<string[]>([]); // 根模块名称列表
  const groupKeyword = ref<string>('');
  // 全部展开或折叠
  const expandHandler = () => {
    isExpandAll.value = !isExpandAll.value;
  };

  const activeFolder = ref<string>(featureCaseStore.moduleId[0] || 'all');
  const activeFolderName = ref('');

  // 选中节点（与树 v-model 双向同步）
  const selectedKeys = computed({
    get: () => [activeFolder.value],
    set: (val) => {
      const key = val?.[0];
      if (key !== undefined && key !== null && String(key) !== activeFolder.value) {
        activeFolder.value = String(key);
      }
    },
  });

  const offspringIds = ref<string[]>([]);
  const caseTreeRef = ref();
  const caseTableRef = ref();

  // 设置当前激活用例类型公共用例|全部用例|回收站
  const setActiveFolder = (type: string) => {
    activeFolder.value = type;
    offspringIds.value = [];
    if (['public', 'all', 'recycle'].includes(type)) {
      activeCaseType.value = 'folder';
    }
    if (type === 'recycle') {
      router.push({
        name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE_RECYCLE,
      });
    }
    // 点「全部/回收站」时若处于高级搜索则退出并刷新
    nextTick(() => {
      if (caseTableRef.value?.isAdvancedSearchMode) {
        caseTableRef.value?.exitAdvancedSearchAndRefresh?.();
      }
    });
  };

  // 获取激活用例类型样式
  const getActiveClass = (type: string) => {
    return activeFolder.value === type ? 'folder-text case-active' : 'folder-text';
  };

  const confirmLoading = ref(false);
  const addSubVisible = ref(false);

  // 处理用例树节点选中：退出高级搜索并按模块刷新列表
  function caseNodeSelect(keys: string[], _offspringIds: string[], node: MsTreeNodeData) {
    const nextId = keys?.[0] != null ? String(keys[0]) : '';
    if (!nextId) return;
    activeFolder.value = nextId;
    activeCaseType.value = 'module';
    offspringIds.value = [..._offspringIds];
    featureCaseStore.setModuleId([nextId]);
    activeFolderName.value = node?.title || node?.name;
    nextTick(() => {
      if (caseTableRef.value?.isAdvancedSearchMode) {
        caseTableRef.value?.exitAdvancedSearchAndRefresh?.();
      }
    });
  }

  // 添加子模块
  async function confirmHandler(formValue: ConfirmValue) {
    try {
      confirmLoading.value = true;
      const params: CreateOrUpdateModule = {
        projectId: currentProjectId.value,
        name: formValue.field,
        parentId: 'NONE',
      };
      await createCaseModuleTree(params);
      Message.success(t('caseManagement.featureCase.addSuccess'));
      caseTreeRef.value.initModules();
      addSubVisible.value = false;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      confirmLoading.value = false;
    }
  }

  /**
   * 设置根模块名称列表
   * @param names 根模块名称列表
   */
  function setRootModules(names: string[]) {
    rootModulesName.value = names;
  }

  // 表格搜索参数
  const tableFilterParams = ref<TableQueryParams>({
    moduleIds: [],
    projectId: '',
  });

  const modulesCount = computed(() => {
    return featureCaseStore.modulesCount;
  });

  const recycleModulesCount = computed(() => {
    return featureCaseStore.recycleModulesCount;
  });

  const moduleCountIsInit = ref(false);
  /**
   * 右侧表格数据刷新后，若当前展示的是模块，则刷新模块树的统计数量
   */
  async function initModulesCount(params: TableQueryParams, refreshModule = false) {
    if (refreshModule) {
      caseTreeRef.value.initModules();
    }
    await featureCaseStore.getCaseModulesCount(params);
    moduleCountIsInit.value = true;
    featureCaseStore.getRecycleModulesCount(params);
    tableFilterParams.value = { ...params };
  }

  function initModules() {
    caseTreeRef.value.initModules();
  }

  function deleteNode() {
    nextTick(() => {
      if (activeFolder.value !== 'all') {
        setActiveFolder('all');
      } else {
        caseTableRef.value?.initData();
      }
    });
  }

  function dragUpdate() {
    caseTableRef.value.initData();
  }

  onBeforeUnmount(() => {
    const routeName = [
      CaseManagementRouteEnum.CASE_MANAGEMENT_CASE,
      CaseManagementRouteEnum.CASE_MANAGEMENT_CASE_DETAIL,
    ];
    if (!routeName.includes(route.name as CaseManagementRouteEnum)) {
      featureCaseStore.setModuleId(['all']);
    }
  });
</script>

<style scoped lang="less">
  .case {
    padding: 8px 4px;
    border-radius: var(--border-radius-small);
    @apply flex cursor-pointer items-center justify-between;
    &:hover {
      background-color: rgb(var(--primary-1));
    }
    .folder-icon {
      margin-right: 4px;
      color: var(--color-text-4);
    }
    .folder-name {
      color: var(--color-text-1);
    }
    .folder-count {
      margin-left: 4px;
      color: var(--color-text-4);
    }
    .case-active {
      .folder-icon,
      .folder-name,
      .folder-count {
        color: rgb(var(--primary-5));
      }
    }
    .back {
      margin-right: 8px;
      width: 20px;
      height: 20px;
      border: 1px solid #ffffff;
      background: linear-gradient(90deg, rgb(var(--primary-9)) 3.36%, #ffffff 100%);
      box-shadow: 0 0 7px rgb(15 0 78 / 9%);
      .arco-icon {
        color: rgb(var(--primary-5));
      }
      @apply flex cursor-pointer items-center rounded-full;
    }
  }
  .recycle {
    @apply absolute bottom-0 pb-4;

    background-color: var(--color-text-fff);
    :deep(.arco-divider-horizontal) {
      margin: 8px 0;
    }
    .recycle-bin {
      @apply bottom-0 flex items-center;

      background-color: var(--color-text-fff);
      .recycle-count {
        margin-left: 4px;
        color: var(--color-text-4);
      }
    }
  }
</style>
