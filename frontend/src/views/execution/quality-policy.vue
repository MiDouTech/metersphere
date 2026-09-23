<template>
  <div class="quality-policy-page">
    <a-space direction="vertical" fill>
      <a-typography-title :heading="4">门禁策略</a-typography-title>
      <a-alert>管理项目策略草稿与发布版本。执行门禁接入尚未完成，发布策略不会使现有任务自动受控。</a-alert>
      <a-alert v-if="error" type="error">{{ error }}</a-alert>
      <a-space>
        <a-button :loading="loading" @click="load">刷新</a-button>
        <a-button
          v-permission="['QUALITY_POLICY:MANAGE']"
          type="primary"
          :disabled="!schema || loading"
          @click="create"
        >
          新建草稿
        </a-button>
        <span>已发布版本不可修改，回退需复制为新草稿后发布。</span>
      </a-space>
      <a-space v-if="currentPolicy">
        <strong>当前发布版本：{{ currentPolicy.versionNo }}</strong>
        <a-button :disabled="busy" @click="inspect(currentPolicy)">查看当前版本</a-button>
        <a-button v-permission="['QUALITY_POLICY:MANAGE']" :disabled="busy" @click="copy(currentPolicy)"
          >复制当前版本</a-button
        >
      </a-space>
      <a-table
        :data="items"
        :loading="loading"
        row-key="id"
        :pagination="{ current: page, pageSize: 20, total }"
        @page-change="changePage"
      >
        <template #columns>
          <a-table-column title="版本" data-index="versionNo" />
          <a-table-column title="状态">
            <template #cell="{ record }">
              {{ record.id === currentId ? '当前发布版本' : record.status === 'DRAFT' ? '草稿' : '历史发布版本' }}
            </template>
          </a-table-column>
          <a-table-column title="发布原因" data-index="changeReason" />
          <a-table-column title="操作">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" @click="inspect(record)">查看</a-button>
                <a-button
                  v-if="record.status === 'DRAFT'"
                  v-permission="['QUALITY_POLICY:MANAGE']"
                  type="text"
                  @click="edit(record)"
                  >编辑</a-button
                >
                <a-button v-permission="['QUALITY_POLICY:MANAGE']" type="text" @click="copy(record)"
                  >复制为新草稿</a-button
                >
                <a-button
                  v-if="record.status === 'DRAFT'"
                  v-permission="['QUALITY_POLICY:PUBLISH']"
                  type="text"
                  @click="preparePublish(record)"
                  >发布</a-button
                >
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-space>
    <a-modal
      v-model:visible="editorVisible"
      :title="readonly ? '查看策略' : editing ? '编辑草稿' : '新建草稿'"
      :width="850"
      :footer="false"
      :mask-closable="false"
      :closable="!busy"
      :esc-to-close="!busy"
    >
      <a-space direction="vertical" fill>
        <a-alert v-if="editorError" type="error">{{ editorError }}</a-alert>
        <a-spin v-if="detailLoading" />
        <a-descriptions v-if="readonly && publication" :column="1" title="发布审计">
          <a-descriptions-item label="操作者">{{ publication.actor }}</a-descriptions-item>
          <a-descriptions-item label="发布时间">{{
            new Date(publication.publishedAt).toLocaleString()
          }}</a-descriptions-item>
          <a-descriptions-item label="前一版本 ID">{{
            publication.previousPolicyId || '首次发布'
          }}</a-descriptions-item>
          <a-descriptions-item label="发布前摘要">{{ publication.previousHash || '无' }}</a-descriptions-item>
          <a-descriptions-item label="发布后摘要">{{ publication.publishedHash }}</a-descriptions-item>
          <a-descriptions-item label="原因">{{ publication.reason }}</a-descriptions-item>
        </a-descriptions>
        <a-alert v-else-if="readonly && !detailLoading && editing?.status === 'PUBLISHED' && !editorError"
          >此历史版本没有发布事件记录，不推断补造审计。</a-alert
        >
        <a-alert v-for="issue in issues" :key="`${issue.path}:${issue.message}`" type="error">
          {{ issue.path || '/' }}：{{ issue.message }}
        </a-alert>
        <a-space v-if="!readonly">
          <a-button :loading="busy" :disabled="mode === 'form'" @click="switchToForm">可视化表单</a-button>
          <a-button :disabled="busy || mode === 'json'" @click="switchToJson">JSON 编辑 / 粘贴导入</a-button>
        </a-space>
        <a-textarea
          v-if="mode === 'json' || readonly"
          v-model="raw"
          :readonly="readonly"
          :disabled="busy"
          :auto-size="{ minRows: 15, maxRows: 25 }"
          aria-label="门禁策略 JSON"
        />
        <a-form v-else :model="form" layout="vertical" :disabled="busy">
          <a-form-item label="策略名称" required><a-input v-model="form.name" :max-length="100" /></a-form-item>
          <a-form-item label="必需截图时点" required>
            <a-checkbox-group v-model="form.roles" :options="roleOptions" />
          </a-form-item>
          <a-form-item label="允许图片类型" required>
            <a-checkbox-group v-model="form.mimeTypes" :options="mimeOptions" />
          </a-form-item>
          <a-form-item label="单个证据大小上限（字节）" required>
            <a-input-number v-model="form.maxBytes" :min="1" :max="maxBytes" :precision="0" />
          </a-form-item>
          <a-form-item label="允许断言操作符" required>
            <a-checkbox-group v-model="form.operators" :options="operatorOptions" />
          </a-form-item>
          <a-alert>预期值固定来自冻结合同。身份、范围、证据完整性和独立审批要求不能通过配置关闭。</a-alert>
        </a-form>
        <a-alert v-if="validationMessage" type="success">{{ validationMessage }}</a-alert>
        <a-space>
          <a-button v-if="!readonly" :loading="busy" @click="validate">校验</a-button>
          <a-button v-if="!readonly" type="primary" :loading="busy" @click="save">保存草稿</a-button>
          <a-button :disabled="busy" @click="exportJson">导出 JSON</a-button>
          <a-button :disabled="busy" @click="editorVisible = false">关闭</a-button>
        </a-space>
      </a-space>
    </a-modal>
    <a-modal
      v-model:visible="publishVisible"
      title="发布策略版本"
      :width="950"
      :footer="false"
      :mask-closable="false"
      :closable="!busy"
      :esc-to-close="!busy"
    >
      <a-space direction="vertical" fill>
        <a-alert type="warning">请比较当前版本与待发布内容。当前阶段未提供执行材料试算；本次仅发布策略配置。</a-alert>
        <a-alert v-if="publishError" type="error">{{ publishError }}</a-alert>
        <div class="policy-comparison">
          <div
            ><strong>当前版本</strong><pre>{{ currentJson }}</pre>
          </div>
          <div
            ><strong>待发布版本</strong><pre>{{ pendingJson }}</pre>
          </div>
        </div>
        <a-textarea v-model="publishReason" :max-length="1000" placeholder="填写发布或回退原因" aria-label="发布原因" />
        <a-space>
          <a-button v-permission="['QUALITY_POLICY:PUBLISH']" type="primary" :loading="busy" @click="publish"
            >确认发布</a-button
          >
          <a-button :disabled="busy" @click="publishVisible = false">取消</a-button>
        </a-space>
      </a-space>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, reactive, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import type {
    PolicyPublication,
    PolicySchema,
    PolicyValidation,
    QualityPolicy,
    QualityPolicyDocument,
  } from '@/api/modules/execution-quality';
  import {
    getQualityPolicy,
    getQualityPolicySchema,
    listQualityPolicies,
    publishQualityPolicy,
    saveQualityPolicy,
    validateQualityPolicy,
  } from '@/api/modules/execution-quality';
  import useAppStore from '@/store/modules/app';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';

  const app = useAppStore();
  const items = ref<QualityPolicy[]>([]);
  const page = ref(1);
  const total = ref(0);
  const publication = ref<PolicyPublication | null>(null);
  const detailLoading = ref(false);
  const currentId = ref<string | null>(null);
  const currentPolicy = ref<QualityPolicy | null>(null);
  const schema = ref<PolicySchema>();
  const loading = ref(false);
  const busy = ref(false);
  const error = ref('');
  const editorError = ref('');
  const publishError = ref('');
  const editorVisible = ref(false);
  const publishVisible = ref(false);
  const readonly = ref(false);
  const editing = ref<QualityPolicy>();
  const pending = ref<QualityPolicy>();
  const publishBase = ref<string | null>(null);
  const publishReason = ref('');
  const raw = ref('');
  const mode = ref<'form' | 'json'>('form');
  const issues = ref<PolicyValidation['errors']>([]);
  const validationMessage = ref('');
  const form = reactive({
    name: '',
    roles: ['AFTER_ACTION'],
    mimeTypes: ['image/png'],
    maxBytes: 10485760,
    operators: ['EQUALS'],
  });
  let epoch = 0;
  let editorEpoch = 0;
  const fields = (id: string) =>
    schema.value?.properties.rules.items.oneOf.find((r) => r.properties.ruleId.enum[0] === id)?.properties.parameters
      .properties;
  const roleOptions = computed(() => fields('Q-EVIDENCE-01')?.requiredRoles.items?.enum || []);
  const mimeOptions = computed(() => fields('Q-EVIDENCE-01')?.allowedMimeTypes.items?.enum || []);
  const operatorOptions = computed(() => fields('Q-ASSERT-01')?.allowedOperators.items?.enum || []);
  const maxBytes = computed(() => fields('Q-EVIDENCE-01')?.maxArtifactBytes.maximum || 1);
  const pretty = (json: string) => JSON.stringify(JSON.parse(json), null, 2);
  const currentJson = computed(() => {
    const policy = currentPolicy.value;
    if (policy) return pretty(policy.rulesJson);
    return publishBase.value ? '无法读取当前发布版本，请刷新后重试。' : '尚无发布版本';
  });
  const pendingJson = computed(() => (pending.value ? pretty(pending.value.rulesJson) : ''));
  const message = (e: unknown) => formatAppErrorMessage(ensureAppError(e, '请求失败，请稍后重试'));

  async function load() {
    const requestEpoch = ++epoch;
    editorEpoch += 1;
    items.value = [];
    schema.value = undefined;
    currentId.value = null;
    currentPolicy.value = null;
    editorVisible.value = false;
    publishVisible.value = false;
    error.value = '';
    if (!app.currentProjectId) {
      error.value = '请先选择项目';
      loading.value = false;
      return;
    }
    loading.value = true;
    try {
      const [listing, document] = await Promise.all([
        listQualityPolicies(app.currentProjectId, page.value),
        getQualityPolicySchema(app.currentProjectId),
      ]);
      if (requestEpoch !== epoch) return;
      items.value = listing.items;
      total.value = listing.total;
      currentId.value = listing.currentPolicyId;
      currentPolicy.value = listing.currentPolicy;
      schema.value = document;
    } catch (e) {
      if (requestEpoch === epoch) error.value = message(e);
    } finally {
      if (requestEpoch === epoch) loading.value = false;
    }
  }
  function resetEditor() {
    editorError.value = '';
    issues.value = [];
    validationMessage.value = '';
  }
  function documentText() {
    if (mode.value === 'json' || readonly.value) return raw.value;
    return JSON.stringify(
      {
        schemaVersion: 'quality-policy.v1',
        name: form.name,
        rules: [
          {
            ruleId: 'Q-EVIDENCE-01',
            parameters: {
              requiredRoles: form.roles,
              allowedMimeTypes: form.mimeTypes,
              maxArtifactBytes: form.maxBytes,
            },
          },
          {
            ruleId: 'Q-ASSERT-01',
            parameters: { allowedOperators: form.operators, expectedSource: 'FROZEN_CONTRACT' },
          },
        ],
      },
      null,
      2
    );
  }
  function create() {
    if (busy.value) return;
    editorEpoch += 1;
    resetEditor();
    editing.value = undefined;
    readonly.value = false;
    mode.value = 'form';
    Object.assign(form, {
      name: '外部 Agent 标准验收',
      roles: ['AFTER_ACTION'],
      mimeTypes: ['image/png'],
      maxBytes: maxBytes.value,
      operators: ['EQUALS'],
    });
    editorVisible.value = true;
  }
  function inspect(policy: QualityPolicy) {
    if (busy.value) return;
    const session = ++editorEpoch;
    resetEditor();
    publication.value = null;
    detailLoading.value = true;
    editing.value = policy;
    readonly.value = true;
    raw.value = pretty(policy.rulesJson);
    editorVisible.value = true;
    getQualityPolicy(policy.id, policy.projectId)
      .then((detail) => {
        if (session !== editorEpoch || !editorVisible.value) return;
        publication.value = detail.publication;
      })
      .catch((e) => {
        if (session === editorEpoch) editorError.value = message(e);
      })
      .finally(() => {
        if (session === editorEpoch) detailLoading.value = false;
      });
  }
  function edit(policy: QualityPolicy) {
    if (busy.value) return;
    inspect(policy);
    readonly.value = false;
    mode.value = 'json';
  }
  function copy(policy: QualityPolicy) {
    if (busy.value) return;
    edit(policy);
    editing.value = undefined;
  }
  function switchToJson() {
    raw.value = documentText();
    mode.value = 'json';
    resetEditor();
  }
  async function check(): Promise<PolicyValidation | undefined> {
    const projectId = app.currentProjectId;
    const requestEpoch = epoch;
    const session = editorEpoch;
    resetEditor();
    const result = await validateQualityPolicy(projectId, documentText());
    if (requestEpoch !== epoch || session !== editorEpoch || !editorVisible.value) return undefined;
    issues.value = result.errors;
    return result;
  }
  async function switchToForm() {
    if (busy.value) return;
    busy.value = true;
    const session = editorEpoch;
    try {
      const result = await check();
      if (!result?.valid || !result.normalizedJson) return;
      const doc: QualityPolicyDocument = JSON.parse(result.normalizedJson);
      const evidence = doc.rules.find((r) => r.ruleId === 'Q-EVIDENCE-01')?.parameters;
      const assertion = doc.rules.find((r) => r.ruleId === 'Q-ASSERT-01')?.parameters;
      if (!evidence || !assertion) return;
      Object.assign(form, {
        name: doc.name,
        roles: evidence.requiredRoles,
        mimeTypes: evidence.allowedMimeTypes,
        maxBytes: evidence.maxArtifactBytes,
        operators: assertion.allowedOperators,
      });
      mode.value = 'form';
    } catch (e) {
      if (session === editorEpoch) editorError.value = message(e);
    } finally {
      busy.value = false;
    }
  }
  async function validate() {
    if (busy.value) return;
    busy.value = true;
    const session = editorEpoch;
    try {
      if ((await check())?.valid) validationMessage.value = '配置校验通过；尚未发布，也未执行材料试算';
    } catch (e) {
      if (session === editorEpoch) editorError.value = message(e);
    } finally {
      busy.value = false;
    }
  }
  async function save() {
    if (busy.value) return;
    busy.value = true;
    const requestEpoch = epoch;
    const projectId = app.currentProjectId;
    const session = editorEpoch;
    const target = editing.value ? { ...editing.value } : undefined;
    try {
      const result = await check();
      if (!result?.valid || !result.normalizedJson || requestEpoch !== epoch) return;
      await saveQualityPolicy(projectId, result.normalizedJson, target);
      if (requestEpoch !== epoch || session !== editorEpoch) return;
      editorVisible.value = false;
      Message.success('草稿已保存');
      await load();
    } catch (e) {
      if (requestEpoch === epoch && session === editorEpoch) editorError.value = message(e);
    } finally {
      busy.value = false;
    }
  }
  function exportJson() {
    const url = URL.createObjectURL(new Blob([documentText()], { type: 'application/json' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'quality-policy.json';
    link.click();
    URL.revokeObjectURL(url);
  }
  function preparePublish(policy: QualityPolicy) {
    if (busy.value) return;
    pending.value = policy;
    publishBase.value = currentId.value;
    publishReason.value = '';
    publishError.value = '';
    publishVisible.value = true;
  }
  async function publish() {
    if (busy.value || !pending.value) return;
    if (!publishReason.value.trim()) {
      publishError.value = '请填写发布原因';
      return;
    }
    if (publishBase.value && currentPolicy.value?.id !== publishBase.value) {
      publishError.value = '请先核对当前版本，无法比较时不能发布';
      return;
    }
    busy.value = true;
    const requestEpoch = epoch;
    try {
      await publishQualityPolicy(pending.value, publishBase.value, publishReason.value);
      if (requestEpoch !== epoch) return;
      publishVisible.value = false;
      Message.success('策略版本已发布');
      await load();
    } catch (e) {
      if (requestEpoch === epoch) publishError.value = message(e);
    } finally {
      busy.value = false;
    }
  }
  function changePage(value: number) {
    if (busy.value) return;
    page.value = value;
    load();
  }
  watch(
    editorVisible,
    (visible) => {
      if (!visible) editorEpoch += 1;
    },
    { flush: 'sync' }
  );
  watch(
    () => app.currentProjectId,
    () => {
      page.value = 1;
      load();
    },
    { immediate: true }
  );
  watch(
    () => [raw.value, JSON.stringify(form)],
    () => {
      validationMessage.value = '';
      issues.value = [];
    }
  );
</script>

<style scoped>
  .quality-policy-page {
    padding: 24px;
  }
  .policy-comparison {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
  }
  .policy-comparison > div {
    min-width: 0;
  }
  pre {
    overflow: auto;
    max-height: 320px;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }
</style>
