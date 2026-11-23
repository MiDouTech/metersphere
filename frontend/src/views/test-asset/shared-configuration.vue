<template>
  <TestAssetPage>
    <div class="asset-heading"><h2>公共配置</h2><p>按系统和环境统一管理配置，敏感值不在列表与历史版本中展示。</p></div>
    <a-alert v-if="error" type="error" class="mb-4">{{ error }} <a-link @click="load">重试</a-link></a-alert>
    <section class="asset-panel">
      <div class="asset-toolbar">
        <div class="asset-actions">
          <a-button v-if="canWrite" type="primary" @click="create">新增配置</a-button>
          <a-button v-if="canWrite" @click="batchInput?.click()">批量导入 JSON</a-button>
          <input
            ref="batchInput"
            class="asset-file-input"
            type="file"
            accept=".json"
            aria-label="批量导入配置"
            @change="batchImport"
          />
          <a-button :loading="loading" @click="load">刷新</a-button>
        </div>
        <div class="asset-search">
          <a-input-search v-model="keyword" allow-clear placeholder="搜索配置名称" aria-label="搜索配置名称" />
          <a-select v-model="systemFilter" allow-clear placeholder="全部系统" aria-label="系统筛选"
            ><a-option v-for="item in systems" :key="item" :value="item">{{ item }}</a-option></a-select
          >
          <a-select v-model="environmentFilter" allow-clear placeholder="全部环境" aria-label="环境筛选"
            ><a-option v-for="item in environments" :key="item" :value="item">{{ item }}</a-option></a-select
          >
          <a-select v-model="kindFilter" allow-clear placeholder="全部类型" aria-label="类型筛选"
            ><a-option value="PLAIN">普通配置</a-option><a-option value="ACCOUNT_PASSWORD">账号密码</a-option
            ><a-option value="OTHER">其他敏感配置</a-option><a-option value="DOCUMENT">配置文档</a-option></a-select
          >
        </div>
      </div>
      <a-table :data="filteredRows" :loading="loading" row-key="id" :scroll="{ x: 1000 }">
        <template #empty
          ><a-empty
            :description="
              error
                ? '配置加载失败，请重试'
                : rows.length
                ? '没有匹配配置，请调整检索条件'
                : '暂无公共配置，可新增或批量导入'
            "
        /></template>
        <template #columns>
          <a-table-column title="配置名称" data-index="name" />
          <a-table-column title="系统" data-index="systemKey" />
          <a-table-column title="环境" data-index="environmentKey" />
          <a-table-column title="类型"
            ><template #cell="{ record }"
              ><a-tag :color="record.kind === 'SECRET' ? 'purple' : 'gray'">{{
                configurationLabel(record)
              }}</a-tag></template
            ></a-table-column
          >
          <a-table-column title="当前版本"
            ><template #cell="{ record }">V{{ record.currentVersion }}</template></a-table-column
          >
          <a-table-column title="文档状态"
            ><template #cell="{ record }">{{ parseLabel(record.parseStatus) }}</template></a-table-column
          >
          <a-table-column title="操作" :width="260"
            ><template #cell="{ record }"
              ><a-space>
                <a-link v-if="canWrite" @click="edit(record)">编辑</a-link
                ><a-link @click="history(record)">版本</a-link>
                <a-link v-if="canWrite && record.kind === 'DOCUMENT'" @click="download(record)">下载原文档</a-link>
                <a-link v-if="canWrite && record.kind === 'DOCUMENT'" @click="retry(record)">重新解析</a-link>
              </a-space></template
            ></a-table-column
          >
        </template>
      </a-table>
    </section>
    <a-modal
      v-model:visible="editor"
      :title="form.id ? '编辑公共配置' : '新增公共配置'"
      :width="760"
      :modal-style="{ maxWidth: 'calc(100vw - 32px)' }"
      :footer="false"
      :mask-closable="false"
      :esc-to-close="!saving"
      :closable="!saving"
      unmount-on-close
      @close="closeEditor"
    >
      <a-form :model="form" layout="vertical" @submit-success="save">
        <a-alert v-if="saveError" type="error" class="mb-4">{{ saveError }}</a-alert>
        <a-form-item field="name" label="配置名称" :rules="[{ required: true, message: '请输入配置名称' }]"
          ><a-input v-model="form.name" :max-length="128" :disabled="saving" placeholder="例如：测试环境品牌商登录账号"
        /></a-form-item>
        <div class="configuration-grid">
          <a-form-item field="systemKey" label="系统名称" :rules="[{ required: true, message: '请输入系统名称' }]"
            ><a-input v-model="form.systemKey" :max-length="128" :disabled="saving"
          /></a-form-item>
          <a-form-item field="environmentKey" label="环境名称" :rules="[{ required: true, message: '请输入环境名称' }]"
            ><a-input v-model="form.environmentKey" :max-length="128" :disabled="saving"
          /></a-form-item>
        </div>
        <a-form-item field="kind" label="配置类型"
          ><a-select v-model="form.kind" :disabled="saving" @change="changeKind"
            ><a-option value="PLAIN">普通配置</a-option><a-option value="SECRET">敏感配置</a-option
            ><a-option value="DOCUMENT">配置文档</a-option></a-select
          ></a-form-item
        >
        <template v-if="form.kind === 'SECRET'">
          <a-radio-group
            v-if="form.id && originalKind === 'SECRET'"
            v-model="keepSecret"
            :disabled="saving"
            @change="resetFields"
            ><a-radio :value="true">保留原敏感内容</a-radio
            ><a-radio :value="false">替换全部敏感内容</a-radio></a-radio-group
          >
          <a-alert v-if="keepSecret" class="mt-4">保存仅更新名称与归属，原敏感内容保持不变；无需重新输入。</a-alert>
          <section v-else class="secret-section">
            <a-form-item label="敏感配置分类"
              ><a-radio-group v-model="secretType" :disabled="saving" @change="changeSecretType"
                ><a-radio value="ACCOUNT_PASSWORD">账号密码</a-radio
                ><a-radio value="OTHER">其他</a-radio></a-radio-group
              ></a-form-item
            >
            <div v-if="secretType === 'ACCOUNT_PASSWORD'" class="configuration-grid">
              <a-form-item label="账号" required
                ><a-input
                  v-model="username"
                  :disabled="saving"
                  :max-length="100000"
                  :input-attrs="{ 'aria-label': '账号', 'autocomplete': 'off' }"
                  placeholder="请输入账号"
              /></a-form-item>
              <a-form-item label="密码" required
                ><a-input-password
                  v-model="password"
                  :disabled="saving"
                  :max-length="100000"
                  :input-attrs="{ 'aria-label': '密码', 'autocomplete': 'new-password' }"
                  placeholder="请输入密码"
              /></a-form-item>
            </div>
            <div v-for="(field, index) in extraFields" :key="index" class="secret-field-row">
              <a-input
                v-model="field.name"
                :disabled="saving"
                :max-length="64"
                :input-attrs="{ 'aria-label': '字段名称' + (index + 1), 'autocomplete': 'off' }"
                placeholder="字段名称，如 Token"
              />
              <a-input-password
                v-model="field.value"
                :disabled="saving"
                :max-length="100000"
                :input-attrs="{ 'aria-label': '敏感值' + (index + 1), 'autocomplete': 'new-password' }"
                placeholder="请输入敏感值"
              />
              <a-button :disabled="saving" @click="extraFields.splice(index, 1)">移除</a-button>
            </div>
            <a-button
              type="text"
              :disabled="saving || extraFields.length >= (secretType === 'ACCOUNT_PASSWORD' ? 18 : 20)"
              @click="extraFields.push({ name: '', value: '' })"
              >＋ 添加自定义字段</a-button
            >
          </section>
          <p class="asset-description"
            >敏感值加密保存，不回填明文。替换时需填写完整内容，账号密码类最多追加18个字段。</p
          >
        </template>
        <a-form-item v-else-if="form.kind === 'DOCUMENT'" label="原始文档（不超过 2 MB）"
          ><input type="file" :disabled="saving" aria-label="上传配置文档" @change="upload" /><span>{{
            form.fileName
          }}</span></a-form-item
        >
        <a-form-item v-else field="value" label="配置内容" :rules="[{ required: true, message: '请输入配置内容' }]"
          ><a-textarea
            v-model="form.value"
            :disabled="saving || contentLoading"
            :auto-size="{ minRows: 4, maxRows: 10 }"
            :max-length="100000"
        /></a-form-item>
        <div class="configuration-footer"
          ><a-button :disabled="saving" @click="editor = false">取消</a-button
          ><a-button type="primary" html-type="submit" :loading="saving" :disabled="contentLoading || fileLoading"
            >保存新版本</a-button
          ></div
        >
      </a-form>
    </a-modal>
  </TestAssetPage>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message, Modal } from '@arco-design/web-vue';

  import TestAssetPage from './components/TestAssetPage.vue';

  import MSR from '@/api/http/index';
  import useAppStore from '@/store/modules/app';
  import businessAssetError from '@/utils/businessAssetError';
  import { hasAnyPermission } from '@/utils/permission';
  import { encodeSecretFields, type SecretField } from '@/utils/sharedConfigurationFields';

  interface Row {
    id: string;
    name: string;
    systemKey: string;
    environmentKey: string;
    kind: string;
    fileName: string;
    parseStatus: string;
    currentVersion: number;
    secretType?: string;
  }

  const batchInput = ref<HTMLInputElement>();
  const keyword = ref('');
  const systemFilter = ref('');
  const environmentFilter = ref('');
  const kindFilter = ref('');
  const secretType = ref('ACCOUNT_PASSWORD');
  const originalKind = ref('');
  const previousKind = ref('PLAIN');
  const previousSecretType = ref('ACCOUNT_PASSWORD');
  const keepSecret = ref(false);
  const username = ref('');
  const password = ref('');
  const extraFields = ref<SecretField[]>([]);
  const app = useAppStore();
  const router = useRouter();
  const rows = ref<Row[]>([]);
  const systems = computed(() => [...new Set(rows.value.map((row) => row.systemKey))]);
  const environments = computed(() => [...new Set(rows.value.map((row) => row.environmentKey))]);
  const filteredRows = computed(() =>
    rows.value.filter(
      (row) =>
        row.name.toLowerCase().includes(keyword.value.trim().toLowerCase()) &&
        (!systemFilter.value || row.systemKey === systemFilter.value) &&
        (!environmentFilter.value || row.environmentKey === environmentFilter.value) &&
        (!kindFilter.value || (row.kind === 'SECRET' ? row.secretType || 'OTHER' : row.kind) === kindFilter.value)
    )
  );
  const error = ref('');
  const saveError = ref('');
  const loading = ref(false);
  const saving = ref(false);
  const contentLoading = ref(false);
  const fileLoading = ref(false);
  let fileGeneration = 0;
  const editor = ref(false);
  let editorGeneration = 0;
  const empty = () => ({
    id: undefined as string | undefined,
    projectId: app.currentProjectId,
    systemKey: '',
    environmentKey: '',
    name: '',
    kind: 'PLAIN',
    value: '',
    fileName: '',
    expectedVersion: 0,
  });
  const form = ref(empty());
  const canWrite = computed(() => hasAnyPermission(['SHARED_CONFIGURATION:WRITE']));
  const kindLabel = (kind: string) => ({ PLAIN: '普通配置', SECRET: '敏感配置', DOCUMENT: '配置文档' }[kind] || kind);
  function configurationLabel(row: Row) {
    if (row.kind !== 'SECRET') return kindLabel(row.kind);
    return row.secretType === 'ACCOUNT_PASSWORD' ? '账号密码' : '其他敏感配置';
  }
  const parseLabel = (status: string) =>
    ({
      PARSED: '解析成功',
      FAILED: '解析失败，可重试',
      UNSUPPORTED: '已保存原件，暂不支持解析此格式',
      NOT_APPLICABLE: '无需解析',
    }[status] || status);
  let generation = 0;
  async function load() {
    const run = ++generation;
    error.value = '';
    loading.value = true;
    try {
      const value = app.currentProjectId
        ? await MSR.get<Row[]>({ url: '/shared-configurations', params: { projectId: app.currentProjectId } })
        : [];
      if (run === generation) rows.value = value;
    } catch (failure) {
      if (run === generation) error.value = businessAssetError(failure, '公共配置加载失败，请检查权限或网络。');
    } finally {
      if (run === generation) loading.value = false;
    }
  }
  function resetFields() {
    username.value = '';
    password.value = '';
    extraFields.value = secretType.value === 'OTHER' && !keepSecret.value ? [{ name: '', value: '' }] : [];
  }
  function create() {
    ++editorGeneration;
    keepSecret.value = false;
    originalKind.value = '';
    previousKind.value = 'PLAIN';
    contentLoading.value = false;
    previousSecretType.value = 'ACCOUNT_PASSWORD';
    secretType.value = 'ACCOUNT_PASSWORD';
    resetFields();
    form.value = empty();
    saveError.value = '';
    editor.value = true;
  }
  function clearSecret() {
    ++fileGeneration;
    fileLoading.value = false;
    contentLoading.value = false;
    form.value.value = '';
    form.value.fileName = '';
    username.value = '';
    password.value = '';
    extraFields.value = [];
  }
  function hasSensitiveInput() {
    return !!(
      form.value.value ||
      username.value ||
      password.value ||
      extraFields.value.some((field) => field.name || field.value)
    );
  }
  function changeKind() {
    const next = form.value.kind;
    const apply = () => {
      ++editorGeneration;
      clearSecret();
      previousKind.value = next;
      keepSecret.value = next === 'SECRET' && originalKind.value === 'SECRET';
      resetFields();
    };
    if (!hasSensitiveInput()) {
      apply();
      return;
    }
    Modal.confirm({
      title: '切换配置类型',
      content: '切换后将清空当前尚未保存的内容，是否继续？',
      onOk: apply,
      onCancel: () => {
        form.value.kind = previousKind.value;
      },
    });
  }
  function changeSecretType() {
    const next = secretType.value;
    const apply = () => {
      previousSecretType.value = next;
      resetFields();
    };
    if (!hasSensitiveInput()) {
      apply();
      return;
    }
    Modal.confirm({
      title: '切换敏感分类',
      content: '切换后将清空当前尚未保存的敏感字段，是否继续？',
      onOk: apply,
      onCancel: () => {
        secretType.value = previousSecretType.value;
      },
    });
  }
  function closeEditor() {
    ++editorGeneration;
    clearSecret();
    saveError.value = '';
  }
  async function edit(row: Row) {
    create();
    form.value = { ...empty(), ...row, expectedVersion: row.currentVersion, value: '' };
    originalKind.value = row.kind;
    previousKind.value = row.kind;
    keepSecret.value = row.kind === 'SECRET';
    secretType.value = row.secretType === 'ACCOUNT_PASSWORD' ? 'ACCOUNT_PASSWORD' : 'OTHER';
    previousSecretType.value = secretType.value;
    resetFields();
    const run = editorGeneration;
    const project = app.currentProjectId;
    const { id } = row;
    if (row.kind === 'PLAIN') {
      contentLoading.value = true;
      try {
        const value = await MSR.get<{ value: string }>({
          url: `/shared-configurations/${row.id}/versions/${row.currentVersion}`,
          params: { projectId: project },
        });
        if (run !== editorGeneration || project !== app.currentProjectId || form.value.id !== id || !editor.value)
          return;
        form.value.value = value.value;
      } catch (failure) {
        if (run === editorGeneration && project === app.currentProjectId && form.value.id === id && editor.value)
          saveError.value = businessAssetError(failure, '配置内容读取失败，请关闭后重试。');
      } finally {
        if (run === editorGeneration && project === app.currentProjectId && form.value.id === id && editor.value)
          contentLoading.value = false;
      }
    }
  }
  async function upload(event: Event) {
    const run = editorGeneration;
    const project = app.currentProjectId;
    const fileRun = ++fileGeneration;
    form.value.value = '';
    form.value.fileName = '';
    fileLoading.value = false;
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      saveError.value = '文件不能超过 2 MB。';
      return;
    }
    fileLoading.value = true;
    try {
      const bytes = new Uint8Array(await file.arrayBuffer());
      if (
        fileRun !== fileGeneration ||
        run !== editorGeneration ||
        project !== app.currentProjectId ||
        !editor.value ||
        form.value.kind !== 'DOCUMENT'
      )
        return;
      let binary = '';
      for (let i = 0; i < bytes.length; i += 8192) binary += String.fromCharCode(...bytes.subarray(i, i + 8192));
      form.value.value = btoa(binary);
      form.value.fileName = file.name;
      saveError.value = '';
    } catch (failure) {
      if (fileRun === fileGeneration && run === editorGeneration && project === app.currentProjectId && editor.value)
        saveError.value = businessAssetError(failure, '文件读取失败，请重新选择。');
    } finally {
      if (fileRun === fileGeneration) fileLoading.value = false;
    }
  }
  async function save() {
    if (saving.value || contentLoading.value || fileLoading.value) return;
    saving.value = true;
    saveError.value = '';
    const run = editorGeneration;
    const project = app.currentProjectId;
    try {
      const submission = { ...form.value, secretType: undefined as string | undefined, keepSecret: false };
      if (form.value.kind === 'SECRET') {
        submission.keepSecret = keepSecret.value;
        if (!keepSecret.value) {
          submission.secretType = secretType.value;
          submission.value = encodeSecretFields(secretType.value, [
            ...(secretType.value === 'ACCOUNT_PASSWORD'
              ? [
                  { name: 'username', value: username.value },
                  { name: 'password', value: password.value },
                ]
              : []),
            ...extraFields.value,
          ]);
        }
      }
      if (!submission.keepSecret && !submission.value.trim()) {
        saveError.value = '请填写配置内容或上传文档。';
        return;
      }
      await MSR.post({ url: '/shared-configurations', data: submission });
      if (run !== editorGeneration || project !== app.currentProjectId) return;
      clearSecret();
      editor.value = false;
      Message.success('配置已保存为新版本');
      await load();
    } catch (failure) {
      if (run === editorGeneration && project === app.currentProjectId)
        saveError.value =
          failure instanceof Error && failure.message.startsWith('请填写1至20')
            ? failure.message
            : businessAssetError(failure, '保存失败，请检查权限、内容及加密密钥配置；可重试。');
    } finally {
      saving.value = false;
    }
  }
  async function batchImport(event: Event) {
    const project = app.currentProjectId;
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    try {
      if (file.size > 2 * 1024 * 1024) throw new Error('size');
      const entries = JSON.parse(await file.text());
      if (project !== app.currentProjectId) return;
      if (!Array.isArray(entries) || !entries.length || entries.length > 100) throw new Error('format');
      await MSR.post({
        url: '/shared-configurations/batch',
        data: {
          entries: entries.map((entry) => ({
            ...entry,
            projectId: project,
            expectedVersion: entry.expectedVersion || 0,
          })),
        },
      });
      Message.success('公共配置已批量导入');
      await load();
    } catch (failure) {
      error.value = businessAssetError(
        failure,
        '批量导入失败，请检查 JSON 数组格式、权限、配置名称和密钥；本批次不保留部分写入。'
      );
    } finally {
      input.value = '';
    }
  }
  function history(row: Row) {
    router.push({ path: '/test-assets/versions', query: { configurationId: row.id } });
  }
  async function retry(row: Row) {
    try {
      const result = await MSR.post<{ parseStatus: string }>({
        url: `/shared-configurations/${row.id}/retry-parse`,
        data: { projectId: app.currentProjectId, version: row.currentVersion },
      });
      Message.info(parseLabel(result.parseStatus));
      await load();
    } catch (failure) {
      error.value = businessAssetError(failure, '解析重试失败，请检查配置密钥和权限。');
    }
  }
  async function download(row: Row) {
    try {
      const result = await MSR.get<{ value: string; fileName: string }>({
        url: `/shared-configurations/${row.id}/versions/${row.currentVersion}`,
        params: { projectId: app.currentProjectId, document: true },
      });
      const bytes = Uint8Array.from(atob(result.value), (c) => c.charCodeAt(0));
      const url = URL.createObjectURL(new Blob([bytes]));
      const link = document.createElement('a');
      link.href = url;
      link.download = result.fileName || 'configuration';
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (failure) {
      error.value = businessAssetError(failure, '文档下载失败，请检查权限或配置密钥。');
    }
  }
  watch(
    () => app.currentProjectId,
    () => {
      ++editorGeneration;
      clearSecret();
      editor.value = false;
      rows.value = [];
      keyword.value = '';
      systemFilter.value = '';
      environmentFilter.value = '';
      kindFilter.value = '';
      load();
    },
    { immediate: true }
  );
</script>

<style scoped>
  .configuration-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 20px;
  }
  .secret-section {
    margin-block-start: 16px;
    padding: 20px;
    border: 1px solid var(--color-border-2);
    border-radius: 6px;
    background: var(--color-fill-1);
  }
  .secret-field-row {
    display: flex;
    gap: 12px;
    margin-block-end: 12px;
  }
  .secret-field-row > :not(button) {
    flex: 1;
    min-inline-size: 0;
  }
  .configuration-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-block-start: 24px;
    padding-block-start: 20px;
    border-block-start: 1px solid var(--color-border-2);
  }
  @media (max-width: 720px) {
    .configuration-grid {
      grid-template-columns: minmax(0, 1fr);
      gap: 0;
    }
    .secret-field-row {
      flex-wrap: wrap;
    }
  }
</style>
