<template>
  <TestAssetPage>
    <h2>公共配置</h2>
    <p>按系统和环境管理平台地址、账号密码及配置文档。密码不在列表和历史版本中显示。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <a-button v-if="canWrite" type="primary" @click="create">新增配置</a-button>
    <label v-if="canWrite" for="configuration-batch">批量导入 JSON（最多 100 条）</label>
    <input v-if="canWrite" id="configuration-batch" type="file" accept=".json" @change="batchImport" />
    <a-button :loading="loading" @click="load">刷新</a-button>
    <a-table :data="rows" :loading="loading" row-key="id">
      <template #columns>
        <a-table-column title="名称" data-index="name" /><a-table-column
          title="系统"
          data-index="systemKey"
        /><a-table-column title="环境" data-index="environmentKey" />
        <a-table-column title="类型"
          ><template #cell="{ record }">{{ kindLabel(record.kind) }}</template></a-table-column
        >
        <a-table-column title="版本" data-index="currentVersion" />
        <a-table-column title="文档状态"
          ><template #cell="{ record }">{{ parseLabel(record.parseStatus) }}</template></a-table-column
        >
        <a-table-column title="操作"
          ><template #cell="{ record }"
            ><a-space>
              <a-link v-if="canWrite" @click="edit(record)">编辑</a-link><a-link @click="history(record)">版本</a-link>
              <a-link v-if="canWrite && record.kind === 'DOCUMENT'" @click="download(record)">下载原文档</a-link>
              <a-link v-if="canWrite && record.kind === 'DOCUMENT'" @click="retry(record)">重新解析</a-link>
            </a-space></template
          ></a-table-column
        >
      </template>
    </a-table>
    <a-drawer v-model:visible="editor" title="维护公共配置" :width="650" unmount-on-close @cancel="clearSecret">
      <form aria-label="公共配置" @submit.prevent="save">
        <fieldset :disabled="saving" class="editor-fields"
          ><legend>公共配置内容</legend>
          <p v-if="saveError" role="alert">{{ saveError }}</p>
          <label for="config-name">名称（必填）</label
          ><input id="config-name" v-model="form.name" name="name" required maxlength="128" />
          <label for="config-system">系统名称（必填）</label
          ><input id="config-system" v-model="form.systemKey" name="system" required maxlength="128" />
          <label for="config-env">环境名称（必填）</label
          ><input id="config-env" v-model="form.environmentKey" name="environment" required maxlength="128" />
          <label for="config-kind">类型</label
          ><select id="config-kind" v-model="form.kind" name="kind" @change="clearSecret"
            ><option value="PLAIN">普通配置</option
            ><option value="SECRET">敏感配置（账号、密码）</option
            ><option value="DOCUMENT">配置文档</option></select
          >
          <template v-if="form.kind === 'DOCUMENT'"
            ><label for="config-file">原始文档（不超过 2 MB）</label
            ><input id="config-file" type="file" required @change="upload" /><p>{{ form.fileName }}</p></template
          >
          <template v-else-if="form.kind === 'SECRET'"
            ><label for="config-secret">敏感值（必填，编辑时重新输入）</label
            ><input
              id="config-secret"
              v-model="form.value"
              type="password"
              name="secret"
              autocomplete="new-password"
              required
              maxlength="100000"
          /></template>
          <template v-else
            ><label for="config-value">配置内容（必填）</label
            ><textarea id="config-value" v-model="form.value" name="value" required rows="6" maxlength="100000" />
          </template>
          <button type="submit" :disabled="saving">{{ saving ? '正在保存' : '保存新版本' }}</button>
        </fieldset>
      </form>
    </a-drawer>
  </TestAssetPage>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';

  import TestAssetPage from './components/TestAssetPage.vue';

  import MSR from '@/api/http/index';
  import useAppStore from '@/store/modules/app';
  import businessAssetError from '@/utils/businessAssetError';
  import { hasAnyPermission } from '@/utils/permission';

  interface Row {
    id: string;
    name: string;
    systemKey: string;
    environmentKey: string;
    kind: string;
    fileName: string;
    parseStatus: string;
    currentVersion: number;
  }

  const app = useAppStore();
  const router = useRouter();
  const rows = ref<Row[]>([]);
  const error = ref('');
  const saveError = ref('');
  const loading = ref(false);
  const saving = ref(false);
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
  function create() {
    ++editorGeneration;
    form.value = empty();
    saveError.value = '';
    editor.value = true;
  }
  function clearSecret() {
    form.value.value = '';
    form.value.fileName = '';
  }
  async function edit(row: Row) {
    create();
    form.value = { ...empty(), ...row, expectedVersion: row.currentVersion, value: '' };
    const run = editorGeneration;
    const project = app.currentProjectId;
    const { id } = row;
    if (row.kind === 'PLAIN') {
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
      }
    }
  }
  async function upload(event: Event) {
    const run = editorGeneration;
    const project = app.currentProjectId;
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      saveError.value = '文件不能超过 2 MB。';
      return;
    }
    try {
      const bytes = new Uint8Array(await file.arrayBuffer());
      if (run !== editorGeneration || project !== app.currentProjectId) return;
      let binary = '';
      for (let i = 0; i < bytes.length; i += 8192) binary += String.fromCharCode(...bytes.subarray(i, i + 8192));
      form.value.value = btoa(binary);
      form.value.fileName = file.name;
      saveError.value = '';
    } catch (failure) {
      saveError.value = businessAssetError(failure, '文件读取失败，请重新选择。');
    }
  }
  async function save() {
    if (saving.value) return;
    saving.value = true;
    saveError.value = '';
    const run = editorGeneration;
    const project = app.currentProjectId;
    const submission = { ...form.value };
    try {
      await MSR.post({ url: '/shared-configurations', data: submission });
      if (run !== editorGeneration || project !== app.currentProjectId) return;
      clearSecret();
      editor.value = false;
      Message.success('配置已保存为新版本');
      await load();
    } catch (failure) {
      if (run === editorGeneration && project === app.currentProjectId)
        saveError.value = businessAssetError(failure, '保存失败，请检查权限、内容及加密密钥配置；可重试。');
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
      load();
    },
    { immediate: true }
  );
</script>

<style scoped>
  form label {
    display: block;
    margin-block: 1rem 0.25rem;
  }
  form input,
  form select,
  form textarea {
    padding: 0.5rem;
    width: 100%;
    border: 1px solid var(--color-border-3);
    color: var(--color-text-1);
    background: var(--color-bg-2);
    font: inherit;
  }
  form button {
    margin-block: 1rem;
    padding: 0.5rem 1rem;
    border: 1px solid var(--color-border-3);
  }
  .editor-fields {
    padding: 0;
    border: 0;
  }
</style>
