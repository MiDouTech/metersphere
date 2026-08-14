<template>
  <div class="p-4">
    <MsCard simple>
      <div class="mb-5 flex items-center justify-between">
        <a-page-header title="资产用例详情" subtitle="此用例属于测试资产，无业务项目执行上下文" @back="goBack" />
        <a-space
          ><a-button @click="goBack">返回</a-button
          ><a-button v-if="canUpdate && !deleted" type="primary" :loading="saving" @click="save"
            >保存</a-button
          ></a-space
        >
      </div>
      <a-spin :loading="loading" class="block">
        <a-alert v-if="deleted" class="mb-4" type="warning"
          >该资产用例已被删除，仅保留历史查看；不能编辑或再次导入。</a-alert
        >
        <a-form :model="form" layout="vertical" class="mx-auto max-w-[900px]">
          <a-form-item label="用例 ID"><a-input :model-value="caseId" disabled /></a-form-item>
          <a-form-item label="用例名称" required
            ><a-input v-model="form.name" :disabled="deleted || !canUpdate"
          /></a-form-item>
          <a-form-item label="标签"
            ><a-input-tag v-model="form.tags" :disabled="deleted || !canUpdate" allow-clear
          /></a-form-item>
          <a-form-item label="编辑模式"
            ><a-radio-group v-model="form.caseEditType" :disabled="deleted || !canUpdate"
              ><a-radio value="STEP">步骤</a-radio><a-radio value="TEXT">文本</a-radio></a-radio-group
            ></a-form-item
          >
          <a-form-item label="前置条件"
            ><a-textarea
              v-model="form.prerequisite"
              :disabled="deleted || !canUpdate"
              :auto-size="{ minRows: 2, maxRows: 6 }"
          /></a-form-item>
          <a-form-item :label="form.caseEditType === 'STEP' ? '用例步骤（JSON/文本）' : '用例描述'"
            ><a-textarea v-model="form.body" :disabled="deleted || !canUpdate" :auto-size="{ minRows: 8, maxRows: 20 }"
          /></a-form-item>
          <a-form-item label="预期结果"
            ><a-textarea
              v-model="form.expectedResult"
              :disabled="deleted || !canUpdate"
              :auto-size="{ minRows: 3, maxRows: 10 }"
          /></a-form-item>
          <a-form-item label="备注"
            ><a-textarea v-model="form.description" :disabled="deleted || !canUpdate"
          /></a-form-item>
          <a-divider>自定义字段</a-divider>
          <a-form-item v-for="field in form.customFields" :key="field.fieldId" :label="field.name || field.fieldId">
            <a-input v-model="field.value" :disabled="deleted || !canUpdate" />
          </a-form-item>
          <a-divider>附件</a-divider>
          <div class="mb-3 flex items-center gap-2">
            <a-button v-if="canUpdate && !deleted" @click="attachmentInput?.click()">上传附件</a-button>
            <input ref="attachmentInput" class="hidden" type="file" multiple @change="uploadAttachments" />
          </div>
          <a-list :data="attachments" bordered>
            <template #item="{ item }">
              <a-list-item>
                <a-link @click="downloadAttachment(item)">{{ item.fileName }}</a-link>
                <template #actions
                  ><a-link v-if="canUpdate && !deleted" status="danger" @click="removeAttachment(item)"
                    >删除</a-link
                  ></template
                >
              </a-list-item>
            </template>
          </a-list>
        </a-form>
      </a-spin>
    </MsCard>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';

  import MsCard from '@/components/pure/ms-card/index.vue';

  import {
    deleteCaseAssetAttachment,
    downloadCaseAssetAttachment,
    getCaseAssetDetail,
    updateCaseAsset,
    uploadCaseAssetAttachment,
  } from '@/api/modules/case-management/featureCase';
  import { hasAnyPermission } from '@/utils/permission';

  import { TestAssetRouteEnum } from '@/enums/routeEnum';

  const route = useRoute();
  const router = useRouter();
  const catalogId = String(route.params.catalogId);
  const caseId = String(route.params.caseId);
  const loading = ref(false);
  const saving = ref(false);
  const canUpdate = hasAnyPermission(['CASE_ASSET:READ+UPDATE']);
  const deleted = ref(false);
  const attachmentInput = ref<HTMLInputElement>();
  const attachments = ref<any[]>([]);
  const form = reactive({
    name: '',
    tags: [] as string[],
    caseEditType: 'STEP' as 'STEP' | 'TEXT',
    prerequisite: '',
    body: '',
    expectedResult: '',
    description: '',
    customFields: [] as Array<{ fieldId: string; name?: string; value?: string }>,
  });
  async function load() {
    loading.value = true;
    try {
      const detail: any = await getCaseAssetDetail(catalogId, caseId);
      form.name = detail.name || '';
      form.caseEditType = detail.caseEditType || 'STEP';
      form.prerequisite = detail.prerequisite || '';
      form.body = form.caseEditType === 'STEP' ? detail.steps || '' : detail.textDescription || '';
      form.expectedResult = detail.expectedResult || '';
      form.description = detail.description || '';
      form.tags = detail.tags || [];
      form.customFields = (detail.customFields || []).map((field: any) => ({
        fieldId: field.fieldId || field.id,
        name: field.fieldName || field.name,
        value: field.value == null ? '' : String(field.value),
      }));
      attachments.value = detail.attachments || [];
      deleted.value = Boolean(detail.deleted);
    } finally {
      loading.value = false;
    }
  }
  async function save() {
    if (!form.name.trim()) {
      Message.warning('请输入用例名称');
      return;
    }
    saving.value = true;
    try {
      await updateCaseAsset({
        id: caseId,
        catalogId,
        name: form.name.trim(),
        tags: form.tags,
        customFields: form.customFields.map(({ fieldId, value }) => ({ fieldId, value })),
        attachments: attachments.value,
        caseEditType: form.caseEditType,
        prerequisite: form.prerequisite,
        steps: form.caseEditType === 'STEP' ? form.body : '',
        textDescription: form.caseEditType === 'TEXT' ? form.body : '',
        expectedResult: form.expectedResult,
        description: form.description,
      });
      Message.success('保存成功');
    } finally {
      saving.value = false;
    }
  }
  async function uploadAttachments(event: Event) {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files || []);
    await Promise.all(files.map((file) => uploadCaseAssetAttachment(catalogId, caseId, file)));
    input.value = '';
    await load();
  }
  async function removeAttachment(item: any) {
    await deleteCaseAssetAttachment(catalogId, caseId, item.id, item.local !== false);
    await load();
  }
  async function downloadAttachment(item: any) {
    const blob = await downloadCaseAssetAttachment(catalogId, caseId, item.id, item.local !== false);
    const url = URL.createObjectURL(blob as Blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = item.fileName || 'attachment';
    link.click();
    URL.revokeObjectURL(url);
  }
  function goBack() {
    router.push({ name: TestAssetRouteEnum.TEST_ASSET_CASES_PROJECT, query: route.query });
  }
  onMounted(load);
</script>
