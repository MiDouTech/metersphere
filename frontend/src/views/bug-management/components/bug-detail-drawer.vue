<template>
  <MsDetailDrawer
    ref="detailDrawerRef"
    v-model:visible="showDrawerVisible"
    :width="1100"
    :footer="false"
    :title="detailInfo?.num ? String(detailInfo.num) : ''"
    :tooltip-text="(detailInfo && detailInfo.title) || null"
    :detail-id="props.detailId"
    :detail-index="props.detailIndex"
    :get-detail-func="getBugDetail"
    :pagination="props.pagination"
    :table-data="props.tableData"
    :page-change="props.pageChange"
    show-full-screen
    unmount-on-close
    :mask="false"
    @loading-detail="setDetailLoading"
    @loaded="loadedBug"
    @get-detail="getDetail"
  >
    <template #titleName>
      <div class="bug-detail-title-full">
        {{ detailInfo?.num || '-' }}
      </div>
    </template>
    <template #titleLeft>
      <div class="flex items-center">
        <MsTag
          size="medium"
          :closable="false"
          :type="props.currentPlatform === detailInfo.platform ? 'primary' : 'default'"
          theme="light"
        >
          {{ detailInfo['platform'] }}
        </MsTag>
      </div>
    </template>
    <template #titleRight="{ loading }">
      <div class="rightButtons flex items-center">
        <MsButton
          v-visible-permission="{ code: 'BUG_DETAIL_EDIT_BUTTON', permissions: ['PROJECT_BUG:READ+UPDATE'] }"
          v-operable-permission="{ code: 'BUG_DETAIL_EDIT_BUTTON', permissions: ['PROJECT_BUG:READ+UPDATE'] }"
          type="icon"
          status="secondary"
          class="mr-4 !rounded-[var(--border-radius-small)]"
          :loading="editLoading"
          :disabled="loading || props.currentPlatform !== detailInfo.platform"
          @click="updateHandler"
        >
          <MsIcon type="icon-icon_edit_outlined" class="mr-1 font-[16px]" />
          {{ t('common.edit') }}
        </MsButton>
        <MsButton
          v-visible-permission="{ code: 'BUG_DETAIL_SHARE_BUTTON', permissions: ['PROJECT_BUG:READ'] }"
          v-operable-permission="{ code: 'BUG_DETAIL_SHARE_BUTTON', permissions: ['PROJECT_BUG:READ'] }"
          type="icon"
          status="secondary"
          class="mr-4 !rounded-[var(--border-radius-small)]"
          :loading="shareLoading"
          :disabled="loading"
          @click="shareHandler"
        >
          <MsIcon type="icon-icon_link-copy_outlined" class="mr-1 font-[16px]" />
          {{ t('caseManagement.featureCase.share') }}
        </MsButton>
        <MsButton
          v-visible-permission="{ code: 'BUG_DETAIL_FOLLOW_BUTTON', permissions: ['PROJECT_BUG:READ'] }"
          v-operable-permission="{ code: 'BUG_DETAIL_FOLLOW_BUTTON', permissions: ['PROJECT_BUG:READ'] }"
          type="icon"
          status="secondary"
          class="mr-4 !rounded-[var(--border-radius-small)]"
          :loading="followLoading"
          :disabled="loading"
          @click="followHandler"
        >
          <MsIcon
            :type="detailInfo.followFlag ? 'icon-icon_collect_filled' : 'icon-icon_collection_outlined'"
            class="mr-1 font-[16px]"
            :class="[detailInfo.followFlag ? 'text-[rgb(var(--warning-6))]' : '']"
          />
          {{ t('caseManagement.featureCase.follow') }}
        </MsButton>
        <MsButton
          v-if="
            hasButtonVisible('BUG_DETAIL_COPY_BUTTON', ['PROJECT_BUG:READ+ADD']) ||
            hasButtonVisible('BUG_DETAIL_DELETE_BUTTON', ['PROJECT_BUG:READ+DELETE'])
          "
          type="icon"
          status="secondary"
          class="mr-2 !rounded-[var(--border-radius-small)]"
        >
          <a-dropdown position="br" :hide-on-select="false">
            <div>
              <icon-more class="mr-1" />
              <span> {{ t('caseManagement.featureCase.more') }}</span>
            </div>
            <template #content>
              <a-doption
                v-visible-permission="{ code: 'BUG_DETAIL_COPY_BUTTON', permissions: ['PROJECT_BUG:READ+ADD'] }"
                :disabled="
                  props.currentPlatform !== detailInfo.platform ||
                  !hasButtonOperable('BUG_DETAIL_COPY_BUTTON', ['PROJECT_BUG:READ+ADD'])
                "
                @click="handleCopy"
              >
                <MsIcon type="icon-icon_copy_filled" class="font-[16px]" />
                {{ t('common.copy') }}
              </a-doption>
              <a-doption
                v-visible-permission="{ code: 'BUG_DETAIL_DELETE_BUTTON', permissions: ['PROJECT_BUG:READ+DELETE'] }"
                :disabled="!hasButtonOperable('BUG_DETAIL_DELETE_BUTTON', ['PROJECT_BUG:READ+DELETE'])"
                class="error-6 text-[rgb(var(--danger-6))]"
                @click="deleteHandler"
              >
                <MsIcon type="icon-icon_delete-trash_outlined1" class="font-[16px] text-[rgb(var(--danger-6))]" />
                {{ t('common.delete') }}
              </a-doption>
            </template>
          </a-dropdown>
        </MsButton>
      </div>
    </template>
    <template #default>
      <div ref="wrapperRef" class="bg-[var(--color-text-fff)]">
        <div class="header relative h-[48px] pl-2">
          <MsTab
            v-model:active-key="activeTab"
            :content-tab-list="contentTabList"
            :get-text-func="getTabBadge"
            class="no-content relative border-b"
          />
        </div>
        <div
          :class="`${!commentInputIsActive ? 'h-[calc(100vh-174px)]' : 'h-[calc(100vh-378px)]'} content-wrapper w-full`"
        >
          <a-spin :loading="detailLoading" class="h-full w-full">
            <div class="tab-pane-container h-full">
              <div v-if="activeTab === 'detail'" class="detail-merge-layout flex h-full min-h-0">
                <div class="leftWrapper min-w-0 flex-1 overflow-y-auto pr-4">
                  <BugDetailTab
                    ref="bugDetailTabRef"
                    :allow-edit="hasButtonOperable('BUG_DETAIL_EDIT_BUTTON', ['PROJECT_BUG:READ+UPDATE'])"
                    :detail-info="detailInfo"
                    :current-custom-fields="currentCustomFields"
                    :is-platform-default-template="isPlatformDefaultTemplate"
                    :platform-system-fields="platformSystemFields"
                    :current-platform="props.currentPlatform"
                    @update-success="updateSuccessHandler"
                  />
                  <div v-if="detailInfo.id" class="mt-6 border-t border-[var(--color-text-n8)] pt-4">
                    <div class="mb-3 font-medium text-[var(--color-text-1)]">
                      {{ t('bugManagement.detail.comment') }}
                    </div>
                    <CommentInput
                      v-if="hasButtonVisible('BUG_DETAIL_COMMENT_BUTTON', ['PROJECT_BUG:READ+COMMENT'])"
                      v-model:notice-user-ids="noticeUserIds"
                      v-model:filed-ids="uploadFileIds"
                      v-model:default-value="commentContent"
                      is-show-avatar
                      :upload-image="handleUploadImage"
                      :is-use-bottom="false"
                      :disabled="!hasButtonOperable('BUG_DETAIL_COMMENT_BUTTON', ['PROJECT_BUG:READ+COMMENT'])"
                      :preview-url="`${EditorPreviewFileUrl}/${appStore.currentProjectId}`"
                      @publish="publishHandler"
                    />
                    <div class="mt-4">
                      <CommentTab ref="detailCommentRef" :bug-id="detailInfo.id" />
                    </div>
                  </div>
                </div>
                <a-divider direction="vertical" class="!mx-0 !h-auto" />
                <div class="rightWrapper w-[332px] shrink-0 overflow-y-auto pl-4">
                  <div class="mb-3 font-medium text-[var(--color-text-1)]">
                    {{ t('bugManagement.detail.baseInfo') }}
                  </div>
                  <BasicInfo
                    v-model:tags="tags"
                    sidebar
                    :form-rule="formRules"
                    :detail="detailInfo"
                    :current-custom-fields="currentCustomFields"
                    :current-platform="props.currentPlatform"
                    :is-platform-default-template="isPlatformDefaultTemplate"
                    :loading="rightLoading"
                    :platform-system-fields="platformSystemFields"
                    @update-success="loadList"
                  />
                </div>
              </div>

              <BugCaseTab
                v-else-if="activeTab === 'case'"
                :bug-id="detailInfo.id"
                @update-case-success="updateSuccess"
              />

              <BugHistoryTab v-else-if="activeTab === 'history'" :bug-id="detailInfo.id" />
            </div>
          </a-spin>
        </div>
      </div>
    </template>
  </MsDetailDrawer>
  <DeleteModal
    :id="props.detailId"
    v-model:visible="deleteVisible"
    :name="detailInfo.title"
    :remote-func="deleteSingleBug"
    @submit="handleSingleDelete"
  />
</template>

<script setup lang="ts">
  import { useRoute, useRouter } from 'vue-router';
  import { useClipboard } from '@vueuse/core';
  import { Message } from '@arco-design/web-vue';

  import MsButton from '@/components/pure/ms-button/index.vue';
  import type { FormItem } from '@/components/pure/ms-form-create/types';
  import MsIcon from '@/components/pure/ms-icon-font/index.vue';
  import MsTab from '@/components/pure/ms-tab/index.vue';
  import type { MsPaginationI } from '@/components/pure/ms-table/type';
  import MsTag from '@/components/pure/ms-tag/ms-tag.vue';
  import CommentInput from '@/components/business/ms-comment/input.vue';
  import { CommentParams } from '@/components/business/ms-comment/types';
  import MsDetailDrawer from '@/components/business/ms-detail-drawer/index.vue';
  import BasicInfo from './basicInfo.vue';
  import BugCaseTab from './bugCaseTab.vue';
  import BugDetailTab from './bugDetailTab.vue';
  import BugHistoryTab from './bugHistoryTab.vue';
  import CommentTab from './commentTab.vue';

  import {
    createOrUpdateComment,
    deleteSingleBug,
    editorUploadFile,
    followBug,
    getBugDetail,
    getTemplateById,
  } from '@/api/modules/bug-management';
  import { EditorPreviewFileUrl } from '@/api/requrls/bug-management';
  import { useI18n } from '@/hooks/useI18n';
  import useModal from '@/hooks/useModal';
  import { useAppStore } from '@/store';
  import { hasButtonOperable, hasButtonVisible } from '@/utils/permission';

  import type { CustomFieldItem } from '@/models/bug-management';
  import { BugEditCustomField, BugEditFormObject } from '@/models/bug-management';
  import { BugManagementRouteEnum, RouteEnum } from '@/enums/routeEnum';

  import resolveBugFieldTooltip from '../bugFieldTips';
  import { resolveHandleUserFormValue } from '../utils';

  const DeleteModal = defineAsyncComponent(() => import('@/views/bug-management/components/deleteModal.vue'));

  const router = useRouter();
  const route = useRoute();
  const detailDrawerRef = ref<InstanceType<typeof MsDetailDrawer>>();
  const wrapperRef = ref();

  const { t } = useI18n();
  const { openDeleteModal } = useModal();
  const { copy, isSupported } = useClipboard({ legacy: true });

  const emit = defineEmits<{
    (e: 'submit'): void;
  }>();

  const props = defineProps<{
    visible: boolean;
    detailId: string; // 详情 id
    detailIndex?: number; // 详情 下标
    detailDefaultTab: string; // 详情默认 tab
    tableData?: any[]; // 表格数据
    pagination?: MsPaginationI; // 分页器对象
    pageChange?: (page: number) => Promise<void>; // 分页变更函数
    currentPlatform: string;
  }>();
  const caseCount = ref(0);
  const appStore = useAppStore();
  const commentContent = ref('');
  const detailCommentRef = ref();
  const noticeUserIds = ref<string[]>([]); // 通知人ids
  const formRules = ref<FormItem[]>([]); // 表单规则

  const currentProjectId = computed(() => appStore.currentProjectId);
  const showDrawerVisible = defineModel<boolean>('visible', { default: false });
  const bugDetailTabRef = ref();
  const isPlatformDefaultTemplate = ref(false);
  const rightLoading = ref(false);
  const detailLoading = ref(false);
  const activeTab = ref<string>('detail');
  const currentDetailId = ref(props.detailId);
  const bugRouteNames: string[] = [RouteEnum.BUG_MANAGEMENT_INDEX, RouteEnum.BUG_MANAGEMENT_DETAIL];

  const commentInputIsActive = computed(() => false);

  const detailInfo = ref<Record<string, any>>({ match: [] }); // 存储当前详情信息，通过loadBug 获取
  const tags = ref([]);
  const platformSystemFields = ref<BugEditCustomField[]>([]); // 平台系统字段
  // 处理表单格式（详情编辑禁止用 CREATE_USER 覆盖已有处理人）
  const getFormRules = (arr: BugEditCustomField[], valueObj: BugEditFormObject) => {
    formRules.value = [];
    if (Array.isArray(arr) && arr.length) {
      formRules.value = arr.map((item: any) => {
        const initValue = valueObj[item.fieldId];
        const initOptions = item.options ? item.options : JSON.parse(item.platformOptionJson);
        let fieldType = item.type;
        // 状态：按钮触发的下拉选择（SELECT）
        if (item.fieldId === 'status') {
          fieldType = 'SELECT';
        } else if (item.fieldId === 'handleUser') {
          fieldType = 'MULTIPLE_MEMBER';
        }
        return {
          type: fieldType,
          name: item.fieldId,
          label: item.fieldName,
          value: initValue,
          options: initOptions,
          required: item.required as boolean,
          platformPlaceHolder: item.platformPlaceHolder,
          tooltip: resolveBugFieldTooltip(item),
          props: {
            modelValue: initValue,
            options: initOptions,
            disabled: !hasButtonOperable('BUG_DETAIL_EDIT_BUTTON', ['PROJECT_BUG:READ+UPDATE']),
            multiple: item.fieldId === 'handleUser' ? true : undefined,
            ...(item.fieldId === 'status'
              ? {
                  allowClear: false,
                  allowSearch: false,
                  selectClass: 'bug-status-select',
                }
              : {}),
          },
        };
      });
    }
  };

  const currentCustomFields = ref<CustomFieldItem[]>([]);

  const getOptionFromTemplate = (field: CustomFieldItem | undefined) => {
    if (field) {
      return field.options ? field.options : JSON.parse(field.platformOptionJson);
    }
    return [];
  };
  // TODO:: Record<string, any>
  async function loadedBug(detail: BugEditFormObject) {
    currentDetailId.value = detail.id;
    // 是否平台默认模板
    isPlatformDefaultTemplate.value = detail.platformDefault;
    // 关闭loading
    detailLoading.value = false;
    const customFieldsRes = await getTemplateById({
      projectId: appStore.currentProjectId,
      id: detail.templateId,
      fromStatusId: detail.status,
      platformBugKey: detail.platformBugId,
      showLocal: detail.platform === 'Local',
    });
    // 详情信息, TAG赋值
    detailInfo.value = { ...detail };
    tags.value = detail.tags || [];
    caseCount.value = detailInfo.value.linkCaseCount;
    const tmpObj: Record<string, any> = { status: detailInfo.value.status };
    platformSystemFields.value = customFieldsRes.customFields.filter(
      (field: Record<string, any>) => field.platformSystemField
    );
    currentCustomFields.value = customFieldsRes.customFields || [];
    if (detailInfo.value.customFields && Array.isArray(detailInfo.value.customFields)) {
      const MULTIPLE_TYPE = ['MULTIPLE_SELECT', 'MULTIPLE_INPUT', 'CHECKBOX', 'MULTIPLE_MEMBER'];
      const SINGLE_TYPE = ['RADIO', 'SELECT', 'MEMBER'];
      detail.customFields.forEach((item: Record<string, any>) => {
        // 处理人：始终按多选成员解析，避免 MEMBER/MULTIPLE_MEMBER 不一致或选项未加载时被清空
        if (item.id === 'handleUser') {
          const multipleOptions = getOptionFromTemplate(
            currentCustomFields.value.find((filed: any) => item.id === filed.fieldId)
          );
          const optionsIds = (multipleOptions || []).map((e: any) => e.value);
          const raw = item.value ?? detail.handleUser;
          tmpObj[item.id] = resolveHandleUserFormValue(raw, optionsIds);
          return;
        }
        if (MULTIPLE_TYPE.includes(item.type)) {
          const multipleOptions = getOptionFromTemplate(
            currentCustomFields.value.find((filed: any) => item.id === filed.fieldId)
          );
          // 如果该值在选项中已经被删除掉
          const optionsIds = (multipleOptions || []).map((e: any) => e.value);
          if (item.value) {
            if (item.type !== 'MULTIPLE_INPUT') {
              tmpObj[item.id] = optionsIds.filter((e: any) => JSON.parse(item.value).includes(e));
            } else {
              tmpObj[item.id] = JSON.parse(item.value);
            }
          }
        } else if (item.type === 'INT' || item.type === 'FLOAT') {
          tmpObj[item.id] = Number(item.value);
        } else if (item.type === 'CASCADER') {
          if (item.value !== '') {
            const arr = JSON.parse(item.value);
            if (arr && arr instanceof Array && arr.length > 0) {
              tmpObj[item.id] = arr[arr.length - 1];
            }
          }
        } else if (SINGLE_TYPE.includes(item.type)) {
          const multipleOptions = getOptionFromTemplate(
            currentCustomFields.value.find((filed: any) => item.id === filed.fieldId)
          );
          // 如果该值在选项中已经被删除掉
          const optionsIds = (multipleOptions || []).map((e: any) => e.value);
          tmpObj[item.id] = optionsIds.find((e: any) => item.value === e) || '';
        } else {
          tmpObj[item.id] = item.value;
        }
      });
    }
    // 兜底：自定义字段未带处理人时，用详情主字段回填
    if (tmpObj.handleUser == null && detail.handleUser) {
      tmpObj.handleUser = resolveHandleUserFormValue(detail.handleUser);
    }
    // 初始化自定义字段
    platformSystemFields.value.forEach((item) => {
      item.defaultValue = tmpObj[item.fieldId];
    });

    getFormRules(
      customFieldsRes.customFields.filter((field: Record<string, any>) => !field.platformSystemField),
      tmpObj
    );
  }

  /**
   * 详情加载中
   */
  function setDetailLoading() {
    detailLoading.value = true;
  }

  /**
   * 获取 tab 的参数数量徽标
   */
  function getTabBadge(tabKey: string) {
    switch (tabKey) {
      case 'detail':
        return '';
      case 'case':
        return `${caseCount.value > 0 ? caseCount.value : ''}`;
      case 'history':
        return '';
      default:
        return '';
    }
  }

  const editLoading = ref<boolean>(false);

  async function getDetail() {
    const res = await getBugDetail(props.detailId);
    loadedBug(res);
  }

  function updateSuccess() {
    getDetail();
    emit('submit');
  }

  const tabList = [
    {
      value: 'detail',
      label: t('bugManagement.detail.detail'),
    },
    {
      value: 'case',
      label: t('bugManagement.detail.case'),
    },
    {
      value: 'history',
      label: t('bugManagement.detail.changeHistory'),
    },
  ];

  /**
   * 如果模块没有开启用例管理
   */
  const contentTabList = computed(() => {
    return appStore.currentMenuConfig.includes('caseManagement')
      ? tabList
      : tabList.filter((item) => item.value !== 'case');
  });

  function updateHandler() {
    if (!hasButtonOperable('BUG_DETAIL_EDIT_BUTTON', ['PROJECT_BUG:READ+UPDATE'])) {
      Message.warning(t('common.noPermission'));
      return;
    }
    router.push({
      name: RouteEnum.BUG_MANAGEMENT_DETAIL,
      query: {
        id: detailInfo.value.id,
      },
      params: {
        mode: 'edit',
      },
    });
  }

  function loadList() {
    detailDrawerRef.value?.initDetail();
    emit('submit');
  }

  const shareLoading = ref<boolean>(false);

  function shareHandler() {
    if (!hasButtonOperable('BUG_DETAIL_SHARE_BUTTON', ['PROJECT_BUG:READ'])) {
      Message.warning(t('common.noPermission'));
      return;
    }
    const bugListPath = router.resolve({ name: BugManagementRouteEnum.BUG_MANAGEMENT_INDEX }).fullPath;
    const query = new URLSearchParams({
      id: String(detailInfo.value.id),
      orgId: String(appStore.currentOrgId),
      pId: String(appStore.currentProjectId),
    }).toString();
    const url = `${window.location.origin}#${bugListPath}?${query}`;
    if (isSupported) {
      copy(url);
      Message.info(t('bugManagement.detail.shareTip'));
    } else {
      Message.error(t('common.copyNotSupport'));
    }
  }

  const followLoading = ref<boolean>(false);
  // 关注
  async function followHandler() {
    if (!hasButtonOperable('BUG_DETAIL_FOLLOW_BUTTON', ['PROJECT_BUG:READ'])) {
      Message.warning(t('common.noPermission'));
      return;
    }
    followLoading.value = true;
    try {
      await followBug(detailInfo.value.id, detailInfo.value.followFlag);
      Message.success(
        detailInfo.value.followFlag
          ? t('caseManagement.featureCase.cancelFollowSuccess')
          : t('caseManagement.featureCase.followSuccess')
      );
      detailInfo.value.followFlag = !detailInfo.value.followFlag;
      emit('submit');
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    } finally {
      followLoading.value = false;
    }
  }
  const deleteVisible = ref(false);

  // 删除用例
  function deleteHandler() {
    if (!hasButtonOperable('BUG_DETAIL_DELETE_BUTTON', ['PROJECT_BUG:READ+DELETE'])) {
      Message.warning(t('common.noPermission'));
      return;
    }
    deleteVisible.value = true;
  }

  const handleSingleDelete = () => {
    emit('submit');
    if (!props.pagination && !props.tableData) {
      showDrawerVisible.value = false;
    } else {
      detailDrawerRef.value?.openNextDetail();
    }
  };
  // 复制bug
  function handleCopy() {
    if (!hasButtonOperable('BUG_DETAIL_COPY_BUTTON', ['PROJECT_BUG:READ+ADD'])) {
      Message.warning(t('common.noPermission'));
      return;
    }
    router.push({
      name: RouteEnum.BUG_MANAGEMENT_DETAIL,
      query: {
        id: detailInfo.value.id,
      },
      params: {
        mode: 'copy',
      },
    });
  }

  const uploadFileIds = ref<string[]>([]);
  async function publishHandler(currentContent: string) {
    if (!hasButtonOperable('BUG_DETAIL_COMMENT_BUTTON', ['PROJECT_BUG:READ+COMMENT'])) {
      Message.warning(t('common.noPermission'));
      return;
    }
    try {
      const params = {
        bugId: detailInfo.value.id,
        notifier: noticeUserIds.value.join(';'),
        replyUser: '',
        parentId: '',
        content: currentContent,
        event: noticeUserIds.value.join(';') ? 'AT' : 'COMMENT', // 任务事件(仅评论: ’COMMENT‘; 评论并@: ’AT‘; 回复评论/回复并@: ’REPLY‘;)
        uploadFileIds: uploadFileIds.value,
      };
      await createOrUpdateComment(params as CommentParams);
      Message.success(t('common.publishSuccessfully'));
      commentContent.value = '';
      noticeUserIds.value = [];
      uploadFileIds.value = [];
      detailCommentRef.value?.initData(detailInfo.value.id);
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    }
  }

  async function handleUploadImage(file: File) {
    const { data } = await editorUploadFile({
      fileList: [file],
    });
    return data;
  }

  async function updateSuccessHandler() {
    if (props.pagination) {
      detailDrawerRef.value?.initDetail();
    } else {
      updateSuccess();
    }
  }

  watch(
    () => showDrawerVisible.value,
    (val) => {
      if (val) {
        if (props.detailDefaultTab && props.detailDefaultTab !== 'basicInfo' && props.detailDefaultTab !== 'comment') {
          activeTab.value = props.detailDefaultTab;
        } else {
          activeTab.value = 'detail';
        }
      }
    }
  );

  watch(
    () => route.name,
    (name) => {
      if (showDrawerVisible.value && !bugRouteNames.includes(String(name))) {
        showDrawerVisible.value = false;
      }
    }
  );
</script>

<style scoped lang="less">
  .bug-detail-title-full {
    max-width: 520px;
    font-weight: 500;
    white-space: normal;
    color: var(--color-text-1);
    word-break: break-word;
    line-height: 20px;
  }
  .detail-merge-layout {
    .ms-scroll-bar();
  }
  .leftWrapper {
    .ms-scroll-bar();
    .header {
      padding: 0 16px;
      border-bottom: 1px solid var(--color-text-n8);
    }
  }
  .content-wrapper {
    @apply overflow-y-auto overflow-x-hidden;
    .ms-scroll-bar();
  }
  .rightWrapper {
    .ms-scroll-bar();
    .baseItem {
      margin-bottom: 16px;
      height: 32px;
      line-height: 32px;
      @apply flex;
      .label {
        width: 84px;
        color: var(--color-text-3);
      }
    }
    :deep(.arco-form-item-layout-horizontal) {
      margin-bottom: 16px !important;
    }
    :deep(.arco-form-item-label-col > .arco-form-item-label) {
      color: var(--color-text-3) !important;
    }
    :deep(.arco-select-view-single) {
      border-color: transparent !important;
      .arco-select-view-suffix {
        visibility: hidden;
      }
      &:hover {
        border-color: rgb(var(--primary-5)) !important;
        .arco-select-view-suffix {
          visibility: visible !important;
        }
      }
      &:hover > .arco-input {
        font-weight: normal;
        text-decoration: none;
        color: var(--color-text-1);
      }
      & > .arco-input {
        font-weight: 500;
        text-decoration: underline;
        color: var(--color-text-1);
      }
    }
    :deep(.arco-input-tag) {
      border-color: transparent !important;
      &:hover {
        border-color: rgb(var(--primary-5)) !important;
      }
    }
    :deep(.arco-input-wrapper) {
      border-color: transparent !important;
      &:hover {
        border-color: rgb(var(--primary-5)) !important;
      }
    }
    :deep(.arco-select-view-multiple) {
      border-color: transparent !important;
      .arco-select-view-suffix {
        visibility: hidden;
      }
      &:hover {
        border-color: rgb(var(--primary-5)) !important;
        .arco-select-view-suffix {
          visibility: visible !important;
        }
      }
    }
    :deep(.arco-textarea-wrapper) {
      border-color: transparent !important;
      &:hover {
        border-color: rgb(var(--primary-5)) !important;
      }
    }
    :deep(.arco-input-number) {
      border-color: transparent !important;
      &:hover {
        border-color: rgb(var(--primary-5)) !important;
      }
    }
    :deep(.arco-picker) {
      border-color: transparent !important;
      .arco-picker-suffix {
        visibility: hidden;
      }
      &:hover {
        border-color: rgb(var(--primary-5)) !important;
        arco-picker-suffix {
          visibility: visible !important;
        }
      }
    }
  }
  .rightButtons {
    :deep(.ms-button--secondary):hover,
    :hover > .arco-icon {
      color: rgb(var(--primary-5)) !important;
      background: var(--color-bg-3);
      .arco-icon:hover {
        color: rgb(var(--primary-5)) !important;
      }
    }
  }
  .error-6 {
    color: rgb(var(--danger-6));
    &:hover {
      color: rgb(var(--danger-6));
    }
  }
  :deep(.tags-class .arco-form-item-label-col) {
    justify-content: flex-start !important;
  }
  .tab-pane-container {
    @apply flex-1 overflow-y-auto p-4;
    .ms-scroll-bar();
  }
  :deep(.arco-form-item-content) {
    overflow-wrap: anywhere;
  }
</style>
