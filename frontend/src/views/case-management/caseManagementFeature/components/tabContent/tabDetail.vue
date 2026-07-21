<!-- eslint-disable vue/no-v-html -->
<template>
  <div class="caseDetailWrapper ml-1 !break-words break-all">
    <a-form ref="caseFormRef" class="rounded-[4px]" :model="detailForm" layout="vertical">
      <a-form-item
        class="relative"
        field="precondition"
        :label="t('system.orgTemplate.precondition')"
        asterisk-position="end"
      >
        <span class="absolute right-[6px] top-0">
          <a-button
            v-if="props.allowEdit && !props.isTestPlan"
            v-permission="['FUNCTIONAL_CASE:READ+UPDATE']"
            type="text"
            class="px-0"
            @click="prepositionEdit"
          >
            <MsIcon type="icon-icon_edit_outlined" class="mr-1 font-[16px] text-[rgb(var(--primary-5))]" />
            {{ t('caseManagement.featureCase.contentEdit') }}
          </a-button>
        </span>
        <MsRichText
          v-if="isEditPreposition"
          v-model:raw="detailForm.prerequisite"
          v-model:filed-ids="prerequisiteFileIds"
          :upload-image="handleUploadImage"
          :preview-url="`${PreviewEditorImageUrl}/${currentProjectId}`"
          class="mt-2"
        />

        <div
          v-else
          v-dompurify-html="detailForm?.prerequisite || '-'"
          class="markdown-body list-item-css !break-words break-all"
        >
        </div>
      </a-form-item>
      <StepDescription v-model:caseEditType="detailForm.caseEditType" :is-test-plan="props.isTestPlan" />
      <!-- 步骤描述 -->
      <div v-if="detailForm.caseEditType === 'STEP'" class="mb-[20px] w-full">
        <AddStep
          v-model:step-list="stepData"
          :is-scroll-y="false"
          :is-test-plan="props.isTestPlan"
          :is-disabled-test-plan="props.isDisabledTestPlan"
          :is-disabled="!isEditPreposition && !props.enableExecute && !props.autoSave"
          :enable-execute="props.enableExecute || props.isTestPlan"
          :case-result="detailForm.lastExecuteResult"
          @change="handleStepChange"
          @set-case-result="handleSetCaseResult"
          @report-defect="handleReportDefect"
        />
      </div>
      <!-- 文本描述 -->
      <MsRichText
        v-if="detailForm.caseEditType === 'TEXT' && isEditPreposition"
        v-model:raw="detailForm.textDescription"
        v-model:filed-ids="textDescriptionFileIds"
        :upload-image="handleUploadImage"
        :preview-url="`${PreviewEditorImageUrl}/${currentProjectId}`"
      />
      <div
        v-if="detailForm.caseEditType === 'TEXT' && !isEditPreposition"
        v-dompurify-html="detailForm.textDescription || '-'"
        class="markdown-body !break-words break-all"
      >
      </div>
      <a-form-item
        v-if="detailForm.caseEditType === 'TEXT'"
        field="remark"
        class="mt-[20px]"
        :label="t('caseManagement.featureCase.expectedResult')"
      >
        <MsRichText
          v-if="detailForm.caseEditType === 'TEXT' && isEditPreposition"
          v-model:raw="detailForm.expectedResult"
          v-model:filed-ids="expectedResultFileIds"
          :upload-image="handleUploadImage"
          :preview-url="`${PreviewEditorImageUrl}/${currentProjectId}`"
        />
        <div
          v-else
          v-dompurify-html="detailForm.expectedResult || '-'"
          class="markdown-body !break-words break-all"
        ></div>
      </a-form-item>
      <a-form-item field="description" :label="t('caseManagement.featureCase.remark')">
        <MsRichText
          v-if="isEditPreposition"
          v-model:filed-ids="descriptionFileIds"
          v-model:raw="detailForm.description"
          :upload-image="handleUploadImage"
          :preview-url="`${PreviewEditorImageUrl}/${currentProjectId}`"
        />
        <div v-else v-dompurify-html="detailForm.description || '-'" class="markdown-body !break-words break-all"></div>
      </a-form-item>
      <div v-if="isEditPreposition" class="flex justify-end">
        <a-button type="secondary" @click="handleCancel">{{ t('common.cancel') }}</a-button>
        <a-button class="ml-[12px]" type="primary" :loading="confirmLoading" @click="handleOK">
          {{ t('common.save') }}
        </a-button>
      </div>
      <div v-if="!props.isTestPlan" v-permission="['FUNCTIONAL_CASE:READ+UPDATE']">
        <AddAttachment v-model:file-list="fileList" multiple @change="handleChange" @link-file="associatedFile">
          <template v-if="props.showCaseNav" #labelRight>
            <div class="case-nav-actions inline-flex items-center gap-2 font-normal">
              <a-button size="small" :disabled="!props.canGoPrev" @click="emit('prevCase')">
                {{ t('caseManagement.featureCase.prevCase') }}
              </a-button>
              <a-button size="small" type="primary" :disabled="!props.canGoNext" @click="emit('nextCase')">
                {{ t('caseManagement.featureCase.nextCase') }}
              </a-button>
            </div>
          </template>
        </AddAttachment>
      </div>
    </a-form>
    <!-- 文件列表开始 -->
    <div class="w-[90%]">
      <div v-if="!props.allowEdit || props.isTestPlan" class="mb-[16px] font-medium text-[var(--color-text-1)]">
        {{ t('caseManagement.featureCase.attachment') }}
      </div>
      <MsFileList
        ref="fileListRef"
        v-model:file-list="fileList"
        :show-tab="false"
        :request-params="{
          caseId: detailForm.id,
          projectId: currentProjectId,
        }"
        :upload-func="uploadOrAssociationFile"
        :show-delete="false"
        @finish="uploadFileOver"
      >
        <template #actions="{ item }">
          <div v-if="props.allowEdit">
            <!-- 本地文件 -->
            <div v-if="item.local || item.status === 'init'" class="flex items-center font-normal">
              <MsButton
                v-if="item.file.type.includes('/image')"
                type="button"
                status="primary"
                class="!mr-0"
                @click="handlePreview(item)"
              >
                {{ t('ms.upload.preview') }}
              </MsButton>
              <a-divider v-if="item.file.type.includes('/image')" direction="vertical" />
              <SaveAsFilePopover
                v-if="!props.isTestPlan && item.uid === activeTransferFileParams?.uid"
                v-model:visible="transferVisible"
                :saving-file="activeTransferFileParams"
                :file-save-as-source-id="(form.id as string)"
                :file-save-as-api="transferFileRequest"
                :file-module-options-api="getTransferFileTree"
                source-id-key="caseId"
                @finish="emit('updateSuccess')"
              />
              <MsButton
                v-if="props.allowEdit && !props.isTestPlan && hasAllPermission(['FUNCTIONAL_CASE:READ+UPDATE'])"
                type="button"
                status="primary"
                class="!mr-0"
                @click="transferFileHandler(item)"
              >
                {{ t('caseManagement.featureCase.storage') }}
              </MsButton>
              <a-divider
                v-if="props.allowEdit && !props.isTestPlan && hasAllPermission(['FUNCTIONAL_CASE:READ+UPDATE'])"
                direction="vertical"
              />
              <MsButton
                v-if="
                  item.status === 'done' &&
                  props.allowEdit &&
                  !props.isTestPlan &&
                  hasAllPermission(['FUNCTIONAL_CASE:READ+UPDATE'])
                "
                type="button"
                status="primary"
                class="!mr-0"
                @click="downloadFile(item)"
              >
                {{ t('caseManagement.featureCase.download') }}
              </MsButton>
              <a-divider
                v-if="
                  item.status === 'done' &&
                  props.allowEdit &&
                  !props.isTestPlan &&
                  hasAllPermission(['FUNCTIONAL_CASE:READ+UPDATE'])
                "
                direction="vertical"
              />
              <MsButton
                v-if="item.status !== 'uploading' && props.allowEdit && !props.isTestPlan"
                type="button"
                :status="item.deleteContent ? 'primary' : 'danger'"
                class="!mr-0"
                @click="deleteFileHandler(item)"
              >
                {{ t(item.deleteContent) || t('ms.upload.delete') }}
              </MsButton>
            </div>
            <!-- 关联文件 -->
            <div v-else class="flex items-center font-normal">
              <MsButton
                v-if="item.file.type.includes('/image')"
                type="button"
                status="primary"
                class="!mr-0"
                @click="handlePreview(item)"
              >
                {{ t('ms.upload.preview') }}
              </MsButton>
              <a-divider v-if="item.file.type.includes('/image')" direction="vertical" />
              <MsButton
                v-if="item.status === 'done'"
                type="button"
                status="primary"
                class="!mr-0"
                @click="downloadFile(item)"
              >
                {{ t('caseManagement.featureCase.download') }}
              </MsButton>
              <a-divider v-if="item.status === 'done'" direction="vertical" />
              <MsButton
                v-if="
                  item.isUpdateFlag &&
                  props.allowEdit &&
                  !props.isTestPlan &&
                  hasAllPermission(['FUNCTIONAL_CASE:READ+UPDATE'])
                "
                type="button"
                status="primary"
                class="!mr-0"
                @click="handleUpdateFile(item)"
              >
                {{ t('common.update') }}
              </MsButton>
              <a-divider
                v-if="
                  item.isUpdateFlag &&
                  props.allowEdit &&
                  !props.isTestPlan &&
                  hasAllPermission(['FUNCTIONAL_CASE:READ+UPDATE'])
                "
                direction="vertical"
              />
              <MsButton
                v-if="
                  item.status !== 'uploading' &&
                  props.allowEdit &&
                  !props.isTestPlan &&
                  hasAllPermission(['FUNCTIONAL_CASE:READ+UPDATE'])
                "
                type="button"
                :status="item.deleteContent ? 'primary' : 'danger'"
                class="!mr-0"
                @click="deleteFileHandler(item)"
              >
                {{ t(item.deleteContent) }}
              </MsButton>
            </div>
          </div>
        </template>
        <template #title="{ item }">
          <span v-if="item.isUpdateFlag" class="ml-4 flex items-center font-normal text-[rgb(var(--warning-6))]"
            ><icon-exclamation-circle-fill /> <span>{{ t('caseManagement.featureCase.fileIsUpdated') }}</span>
          </span>
        </template>
      </MsFileList>
    </div>
    <!-- 详情内嵌评论（非底部悬浮） -->
    <div
      v-if="!props.isTestPlan"
      v-permission="['FUNCTIONAL_CASE:READ+COMMENT']"
      class="mt-6 border-t border-[var(--color-text-n8)] pt-4"
    >
      <div class="mb-3 flex items-center justify-between">
        <div class="font-medium text-[var(--color-text-1)]">{{ t('caseManagement.featureCase.inlineComment') }}</div>
        <MsButton type="button" status="primary" class="!mr-0" @click="emit('gotoComments')">
          {{ t('caseManagement.featureCase.viewAllComments') }}
        </MsButton>
      </div>
      <inputComment
        v-model:content="inlineCommentContent"
        v-model:notice-user-ids="inlineNoticeUserIds"
        v-model:filed-ids="inlineUploadFileIds"
        :preview-url="`${PreviewEditorImageUrl}/${currentProjectId}`"
        is-show-avatar
        :is-use-bottom="false"
        :upload-image="handleUploadImage"
        @publish="handleInlinePublish"
      />
    </div>
    <div>
      <MsUpload
        v-model:file-list="fileList"
        accept="none"
        :auto-upload="false"
        :sub-text="
          acceptType === 'jar' ? '' : t('project.fileManagement.normalFileSubText', { size: appStore.getFileMaxSize })
        "
        multiple
        draggable
        size-unit="MB"
        :is-all-screen="true"
        class="mb-[16px]"
        @change="handleChange"
      />
    </div>
    <LinkFileDrawer
      v-model:visible="showDrawer"
      :get-tree-request="getModules"
      :get-count-request="getModulesCount"
      :get-list-request="getAssociatedFileListUrl"
      :get-list-fun-params="getListFunParams"
      @save="saveSelectAssociatedFile"
    />
  </div>
  <a-image-preview v-model:visible="previewVisible" :src="imageUrl" />
  <AddDefectDrawer
    v-model:visible="showDefectDrawer"
    :extra-params="{ caseId: detailForm.id }"
    @success="emit('updateSuccess')"
  />
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue';
  import { FormInstance, Message } from '@arco-design/web-vue';

  import MsButton from '@/components/pure/ms-button/index.vue';
  import type { FormRuleItem } from '@/components/pure/ms-form-create/types';
  import MsRichText from '@/components/pure/ms-rich-text/MsRichText.vue';
  import MsFileList from '@/components/pure/ms-upload/fileList.vue';
  import MsUpload from '@/components/pure/ms-upload/index.vue';
  import type { MsFileItem } from '@/components/pure/ms-upload/types';
  import AddAttachment from '@/components/business/ms-add-attachment/index.vue';
  import SaveAsFilePopover from '@/components/business/ms-add-attachment/saveAsFilePopover.vue';
  import inputComment from '@/components/business/ms-comment/input.vue';
  import type { CommentParams } from '@/components/business/ms-comment/types';
  import LinkFileDrawer from '@/components/business/ms-link-file/associatedFileDrawer.vue';
  import AddStep from '../addStep.vue';
  import StepDescription from '@/views/case-management/caseManagementFeature/components/tabContent/stepDescription.vue';
  import AddDefectDrawer from '@/views/case-management/components/addDefectDrawer/index.vue';

  import {
    checkFileIsUpdateRequest,
    createCommentList,
    deleteFileOrCancelAssociation,
    downloadFileRequest,
    editorUploadFile,
    getAssociatedFileListUrl,
    getTransferFileTree,
    previewFile,
    transferFileRequest,
    updateCaseRequest,
    updateFile,
    uploadOrAssociationFile,
  } from '@/api/modules/case-management/featureCase';
  import { getModules, getModulesCount } from '@/api/modules/project-management/fileManagement';
  import { PreviewEditorImageUrl } from '@/api/requrls/case-management/featureCase';
  import { useI18n } from '@/hooks/useI18n';
  import useModal from '@/hooks/useModal';
  import useShortcutSave from '@/hooks/useShortcutSave';
  import useAppStore from '@/store/modules/app';
  import { characterLimit, downloadByteFile, getGenerateId, sleep } from '@/utils';
  import { scrollIntoView } from '@/utils/dom';
  import { hasAllPermission } from '@/utils/permission';

  import type { AssociatedList, DetailCase, StepList } from '@/models/caseManagement/featureCase';
  import type { TableQueryParams } from '@/models/common';

  import { convertToFile } from '../utils';

  const caseFormRef = ref<FormInstance>();

  const appStore = useAppStore();
  const { openModal } = useModal();
  const currentProjectId = computed(() => appStore.currentProjectId);

  const { t } = useI18n();

  const props = withDefaults(
    defineProps<{
      form: DetailCase;
      allowEdit?: boolean; // 是否允许编辑
      formRules?: FormRuleItem[]; // 编辑表单
      isTestPlan?: boolean; // 测试计划页面的
      isDisabledTestPlan?: boolean; // 测试计划页面-已归档
      isEdit?: boolean; // 是否为编辑状态
      /** 启用步骤实际结果/执行结果/附件 */
      enableExecute?: boolean;
      /** 步骤等变更自动保存（无需点保存） */
      autoSave?: boolean;
      /** 展示用例上下条切换 */
      showCaseNav?: boolean;
      canGoPrev?: boolean;
      canGoNext?: boolean;
    }>(),
    {
      allowEdit: true, // 是否允许编辑
      isEdit: false,
      enableExecute: false,
      autoSave: false,
      showCaseNav: false,
      canGoPrev: false,
      canGoNext: false,
    }
  );

  const emit = defineEmits<{
    (e: 'updateSuccess'): void;
    (e: 'prevCase'): void;
    (e: 'nextCase'): void;
    (e: 'gotoComments'): void;
  }>();

  const detailForm = ref<Record<string, any>>({
    projectId: currentProjectId.value,
    templateId: '',
    name: '',
    prerequisite: '',
    caseEditType: 'STEP',
    steps: '',
    textDescription: '',
    expectedResult: '',
    description: '',
    publicCase: false,
    moduleId: '',
    versionId: '',
    tags: [],
    customFields: [],
    relateFileMetaIds: [],
  });

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

  const isEditPreposition = ref<boolean>(props.isEdit); // 非编辑状态

  // 更改类型
  const handleSelectType = (value: string | number | Record<string, any> | undefined) => {
    detailForm.value.caseEditType = value as string;
  };

  // 获取类型样式
  function getSelectTypeClass(type: string) {
    return detailForm.value.caseEditType === type
      ? ['bg-[rgb(var(--primary-1))]', '!text-[rgb(var(--primary-5))]']
      : [];
  }

  // 编辑前置操作
  function prepositionEdit() {
    isEditPreposition.value = !isEditPreposition.value;
  }

  const fileList = ref<MsFileItem[]>([]);
  const acceptType = ref('none'); // 模块-上传文件类型
  const getListFunParams = ref<TableQueryParams>({
    combine: {
      hiddenIds: [],
    },
  });

  const showDrawer = ref<boolean>(false);
  function associatedFile() {
    showDrawer.value = true;
  }

  const attachmentsList = ref([]);

  // 后台传过来的local文件的item列表
  const oldLocalFileList = computed(() => {
    return attachmentsList.value.filter((item: any) => item.local);
  });

  // 后台已保存本地文件
  const currentOldLocalFileList = computed(() => {
    return fileList.value.filter((item) => item.local && item.status !== 'init').map((item: any) => item.uid);
  });

  // 已经关联过的id列表
  const associateFileIds = computed(() => {
    return attachmentsList.value.filter((item: any) => !item.local).map((item: any) => item.id);
  });

  // 当前新增传过来的关联list
  const currentAlreadyAssociateFileList = computed(() => {
    return fileList.value
      .filter((item) => !item.local && !associateFileIds.value.includes(item.uid))
      .map((item: any) => item.uid);
  });

  // 新增关联文件ID列表
  const newAssociateFileListIds = computed(() => {
    return fileList.value
      .filter((item: any) => !item.local && !associateFileIds.value.includes(item.uid))
      .map((item: any) => item.uid);
  });

  // 删除本地上传的文件id
  const deleteFileMetaIds = computed(() => {
    return oldLocalFileList.value
      .filter((item: any) => !currentOldLocalFileList.value.includes(item.id))
      .map((item: any) => item.id);
  });

  // 取消关联文件id
  const unLinkFilesIds = computed(() => {
    const deleteAssociateFileIds = fileList.value
      .filter(
        (item: any) =>
          !currentAlreadyAssociateFileList.value.includes(item.uid) && associateFileIds.value.includes(item.uid)
      )
      .map((item) => item.uid);
    return associateFileIds.value.filter(
      (id: string) => !currentAlreadyAssociateFileList.value.includes(id) && !deleteAssociateFileIds.includes(id)
    );
  });

  // 前置操作附件id
  const prerequisiteFileIds = ref<string[]>([]);
  // 文本描述附件id
  const textDescriptionFileIds = ref<string[]>([]);
  // 预期结果附件id
  const expectedResultFileIds = ref<string[]>([]);
  // 描述附件id
  const descriptionFileIds = ref<string[]>([]);

  // 所有附近文件id
  const allAttachmentsFileIds = computed(() => {
    return [
      ...prerequisiteFileIds.value,
      ...textDescriptionFileIds.value,
      ...expectedResultFileIds.value,
      ...descriptionFileIds.value,
    ];
  });

  // 处理编辑详情参数
  function getParams() {
    const steps = stepData.value.map((item, index) => {
      return {
        id: item.id,
        num: index,
        desc: item.step,
        result: item.expected,
        actualResult: item.actualResult,
        executeResult: item.executeResult,
        attachmentIds: item.attachmentIds || [],
        attachmentNames: item.attachmentNames || [],
      };
    });

    const customFieldsArr = props.formRules?.map((item: any) => {
      return {
        fieldId: item.field,
        value: Array.isArray(item.value) ? JSON.stringify(item.value) : item.value,
      };
    });

    // 汇总用例级执行结果：有失败→失败；否则有阻塞→阻塞；否则全跳过/通过→通过；无结果则保留已标记值
    const execList = steps.map((s) => s.executeResult).filter(Boolean) as string[];
    let { lastExecuteResult } = detailForm.value;
    if (execList.length) {
      if (execList.includes('ERROR')) lastExecuteResult = 'ERROR';
      else if (execList.includes('BLOCKED')) lastExecuteResult = 'BLOCKED';
      else if (execList.every((r) => r === 'SKIP')) lastExecuteResult = 'SKIP';
      else if (execList.every((r) => r === 'SUCCESS' || r === 'SKIP')) lastExecuteResult = 'SUCCESS';
    }
    detailForm.value.lastExecuteResult = lastExecuteResult;

    const pendingFiles = stepData.value.flatMap((item: any) => item._pendingFiles || []);

    return {
      request: {
        ...detailForm.value,
        steps: JSON.stringify(steps),
        lastExecuteResult,
        deleteFileMetaIds: deleteFileMetaIds.value,
        unLinkFilesIds: unLinkFilesIds.value,
        newAssociateFileListIds: newAssociateFileListIds.value,
        customFields: customFieldsArr,
        caseDetailFileIds: allAttachmentsFileIds.value,
      },
      fileList: [...fileList.value.filter((item: any) => item.status === 'init'), ...pendingFiles],
    };
  }

  const confirmLoading = ref<boolean>(false);
  let autoSaveTimer: ReturnType<typeof setTimeout> | null = null;

  async function persistCase(silent = false) {
    try {
      confirmLoading.value = true;
      await updateCaseRequest(getParams());
      // 清掉已提交的临时步骤文件
      stepData.value.forEach((item: any) => {
        item._pendingFiles = [];
      });
      if (!silent) {
        Message.success(t('caseManagement.featureCase.editSuccess'));
        isEditPreposition.value = false;
      }
      emit('updateSuccess');
      return true;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
      return false;
    } finally {
      confirmLoading.value = false;
    }
  }

  function handleStepChange() {
    if (!props.autoSave) return;
    if (autoSaveTimer) clearTimeout(autoSaveTimer);
    autoSaveTimer = setTimeout(() => {
      persistCase(true);
    }, 600);
  }

  async function handleSetCaseResult(result: string) {
    detailForm.value.lastExecuteResult = result;
    stepData.value.forEach((item) => {
      item.executeResult = result;
    });
    // 触发步骤列表响应，保证表格展示即时刷新
    stepData.value = [...stepData.value];
    if (autoSaveTimer) clearTimeout(autoSaveTimer);
    const ok = await persistCase(true);
    if (!ok) return;
    if (props.canGoNext) {
      emit('nextCase');
    } else {
      Message.info(t('caseManagement.featureCase.lastCaseTip'));
    }
  }

  const showDefectDrawer = ref(false);
  function handleReportDefect() {
    if (!detailForm.value.id) return;
    showDefectDrawer.value = true;
  }

  const inlineCommentContent = ref('');
  const inlineNoticeUserIds = ref<string[]>([]);
  const inlineUploadFileIds = ref<string[]>([]);
  async function handleInlinePublish(currentContent: string) {
    if (!detailForm.value.id || !currentContent) return;
    try {
      const params: CommentParams = {
        caseId: detailForm.value.id,
        notifier: inlineNoticeUserIds.value.join(';'),
        replyUser: '',
        parentId: '',
        content: currentContent,
        event: inlineNoticeUserIds.value.length ? 'AT' : 'COMMENT',
        uploadFileIds: inlineUploadFileIds.value,
      };
      await createCommentList(params);
      inlineCommentContent.value = '';
      inlineNoticeUserIds.value = [];
      inlineUploadFileIds.value = [];
      Message.success(t('common.publishSuccessfully'));
      emit('updateSuccess');
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log(error);
    }
  }

  function handleOK() {
    caseFormRef.value?.validate().then(async (res: any) => {
      if (!res) {
        await persistCase(false);
      }
      return scrollIntoView(document.querySelector('.arco-form-item-message'), { block: 'center' });
    });
  }

  function setStepData(steps: string) {
    if (steps) {
      stepData.value = JSON.parse(steps).map((item: any) => {
        return {
          id: item.id,
          step: item.desc,
          expected: item.result,
          actualResult: item.actualResult,
          executeResult: item.executeResult,
          attachmentIds: item.attachmentIds || [],
          attachmentNames: item.attachmentNames || [],
        };
      });
    } else {
      stepData.value = [];
    }
  }

  function handleCancel() {
    detailForm.value = { ...props.form };
    const { steps } = detailForm.value;
    setStepData(steps);
    isEditPreposition.value = false;
  }

  const fileListRef = ref<InstanceType<typeof MsFileList>>();

  // 删除本地文件
  async function deleteFileHandler(item: MsFileItem) {
    if (!item.local) {
      try {
        const params = {
          id: item.uid,
          local: item.local,
          caseId: detailForm.value.id,
          projectId: currentProjectId.value,
        };
        await deleteFileOrCancelAssociation(params);
        Message.success(
          item.local ? t('caseManagement.featureCase.deleteSuccess') : t('caseManagement.featureCase.cancelLinkSuccess')
        );
        emit('updateSuccess');
      } catch (error) {
        console.log(error);
      }
    } else {
      openModal({
        type: 'error',
        title: t('caseManagement.featureCase.deleteFile', { name: characterLimit(item?.name) }),
        content: t('caseManagement.featureCase.deleteFileTip'),
        okText: t('common.confirmDelete'),
        cancelText: t('common.cancel'),
        okButtonProps: {
          status: 'danger',
        },
        onBeforeOk: async () => {
          try {
            const params = {
              id: item.uid,
              local: item.local,
              caseId: detailForm.value.id,
              projectId: currentProjectId.value,
            };
            await deleteFileOrCancelAssociation(params);
            Message.success(
              item.local
                ? t('caseManagement.featureCase.deleteSuccess')
                : t('caseManagement.featureCase.cancelLinkSuccess')
            );
            emit('updateSuccess');
          } catch (error) {
            console.log(error);
          }
        },
        hideCancel: false,
      });
    }
  }
  const transferVisible = ref<boolean>(false);

  // 下载文件
  async function downloadFile(item: MsFileItem) {
    try {
      const prams = {
        projectId: currentProjectId.value,
        caseId: detailForm.value.id,
        fileId: item.uid,
        local: item.local,
      };
      const res = await downloadFileRequest(prams);
      downloadByteFile(res, `${item.name}`);
    } catch (error) {
      console.log(error);
    }
  }
  const checkUpdateFileIds = ref<string[]>([]);

  // 检测更新文件
  async function getCheckFileIds(fileIds: string[]) {
    try {
      checkUpdateFileIds.value = await checkFileIsUpdateRequest(fileIds);
    } catch (error) {
      console.log(error);
    }
  }

  // 获取详情
  async function getDetails() {
    const { steps, attachments } = detailForm.value;
    setStepData(steps);
    const fileIds = (attachments || []).map((item: any) => item.id);
    if (fileIds.length) {
      await getCheckFileIds(fileIds);
    }

    attachmentsList.value = attachments || [];
    // 处理文件列表
    fileList.value = (attachments || [])
      .map((fileInfo: any) => {
        return {
          ...fileInfo,
          name: fileInfo.fileName,
          isUpdateFlag: checkUpdateFileIds.value.includes(fileInfo.id),
        };
      })
      .map((fileInfo: any) => {
        return convertToFile(fileInfo);
      });
  }

  const imageUrl = ref('');
  const previewVisible = ref<boolean>(false);

  // 预览
  async function handlePreview(item: MsFileItem) {
    try {
      const res = await previewFile({
        projectId: currentProjectId.value,
        caseId: detailForm.value.id,
        fileId: item.uid,
        local: item.local,
      });
      const blob = new Blob([res], { type: 'image/jpeg' });
      imageUrl.value = URL.createObjectURL(blob);
      previewVisible.value = true;
    } catch (error) {
      console.log(error);
    }
  }

  async function restartUpload() {
    await sleep(300);
    fileListRef.value?.startUpload();
  }

  const activeTransferFileParams = ref<MsFileItem>();

  function transferFileHandler(item: MsFileItem) {
    activeTransferFileParams.value = {
      ...item,
    };
    transferVisible.value = true;
  }

  watch(
    () => props.form,
    (val) => {
      detailForm.value = { ...val };
      getDetails();
    },
    {
      deep: true,
    }
  );

  // 监视文件列表处理关联和本地文件
  watch(
    () => fileList.value,
    (arr) => {
      getListFunParams.value.combine.hiddenIds = arr.filter((item) => !item.local).map((item) => item.uid);
    },
    { deep: true, immediate: true }
  );

  async function startUpload(fileIds?: string[]) {
    try {
      const params = {
        request: {
          caseId: detailForm.value.id,
          projectId: currentProjectId.value,
          fileIds,
        },
        file: fileList.value.filter((item) => item.status === 'init').map((item) => item.file),
      };
      await uploadOrAssociationFile(params);
      emit('updateSuccess');
    } catch (error) {
      console.log(error);
    }
  }

  // 处理关联文件
  function saveSelectAssociatedFile(fileData: AssociatedList[], selectIds?: string[]) {
    startUpload(selectIds || []);
  }

  // 更新文件
  async function handleUpdateFile(item: MsFileItem) {
    try {
      await updateFile(currentProjectId.value, item.associationId);
      Message.success(t('common.updateSuccess'));
      emit('updateSuccess');
    } catch (error) {
      console.log(error);
    }
  }

  async function handleUploadImage(file: File) {
    const { data } = await editorUploadFile({
      fileList: [file],
    });
    return data;
  }

  async function uploadFileOver() {
    emit('updateSuccess');
  }

  function handleChange(_fileList: MsFileItem[], fileItem?: MsFileItem) {
    // 校验本地文件是否重复
    const isRepeat = _fileList.filter((item) => item.name === fileItem?.name && item.local).length > 1;
    if (isRepeat) {
      fileList.value = _fileList.reduce((prev: MsFileItem[], current: MsFileItem) => {
        const isExist = prev.find((item: any) => item.name === current.name);
        if (!isExist) {
          prev.push(current);
        }
        return prev;
      }, []);
    } else {
      fileList.value = _fileList.map((e) => {
        return {
          ...e,
          enable: true, // 是否启用
          local: true, // 是否本地文件
        };
      });
      restartUpload();
    }
  }

  const { registerCatchSaveShortcut, removeCatchSaveShortcut } = useShortcutSave(handleOK);
  onMounted(async () => {
    detailForm.value = { ...props.form };
    await getDetails();
    if (isEditPreposition.value) {
      registerCatchSaveShortcut();
    }
  });

  onBeforeUnmount(() => {
    removeCatchSaveShortcut();
  });

  defineExpose({
    handleOK,
    getParams,
    stepData,
  });
</script>

<style scoped lang="less">
  :deep(.arco-form-item-label) {
    font-weight: bold !important;
  }
</style>
