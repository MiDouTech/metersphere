<template>
  <TestAssetPage :show-version-link="false">
    <header class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-xl font-medium">业务知识</h1>
      <TestAssetTabs />
    </header>
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3 rounded bg-[var(--color-bg-2)] p-3">
      <div class="flex min-w-0 flex-wrap items-center gap-2">
        <a-input-search v-model="keyword" class="w-56 max-w-full" placeholder="搜索业务知识标题" @search="search" />
        <a-select v-model="filters.type" class="w-40 max-w-full" allow-clear placeholder="知识类型">
          <a-option v-for="item in types" :key="item.value" :value="item.value">{{ item.label }}</a-option>
        </a-select>
        <a-input v-model="filters.scope" class="w-36 max-w-full" placeholder="适用范围" @press-enter="search" />
        <a-input v-model="filters.system" class="w-36 max-w-full" placeholder="系统" @press-enter="search" />
        <a-input v-model="filters.module" class="w-36 max-w-full" placeholder="模块" @press-enter="search" />
        <a-button :loading="loading" @click="search">查询</a-button>
      </div>
      <div v-if="capabilities.includes('KNOWLEDGE_EDIT')" class="flex flex-wrap items-center gap-2">
        <a-button @click="migrationVisible = !migrationVisible">存量候选迁移</a-button>
        <a-button type="primary" @click="create">新建知识</a-button>
      </div>
    </div>
    <a-card v-if="migrationVisible" title="受控批量迁移（只形成候选）"
      ><a-input v-model="migration.batchId" placeholder="本次唯一批次标识" /><a-input
        v-model="migration.environmentId"
        placeholder="已授权测试环境" /><a-select v-model="sourceProject" placeholder="来源项目"
        ><a-option v-for="project in projects" :key="project.id" :value="project.id">{{
          project.name
        }}</a-option></a-select
      ><a-button
        @click="
          migrationMode = true;
          chooseSource();
        "
        >添加固定来源版本</a-button
      ><a-list :data="migrationSources"
        ><template #item="{ item }"
          ><a-list-item>{{ item.assetId }} · {{ item.versionId }}</a-list-item></template
        ></a-list
      ><a-checkbox v-model="migration.previewOnly">仅预演</a-checkbox
      ><a-input-number v-model="migration.cursor" :min="0" /><a-space
        ><a-button :loading="saving" @click="migrateBatch">处理本批，返回断点</a-button
        ><a-button v-if="capabilities.includes('KNOWLEDGE_PUBLISH')" @click="withdrawBatch"
          >撤回该批仍有效的首次发布版本</a-button
        ></a-space
      ><ContentReader :value="migrationResult" /></a-card
    ><a-alert v-if="error" class="mb-4" type="warning">{{ error }} <a-link @click="load">重试</a-link></a-alert>
    <a-table
      :data="records"
      :loading="loading"
      row-key="id"
      :pagination="{ current: page, total, pageSize: 20 }"
      @page-change="changePage"
    >
      <template #empty><a-empty :description="keyword ? '未找到符合条件的知识' : '暂无可见业务知识'" /></template>
      <template #columns
        ><a-table-column title="标题"
          ><template #cell="{ record }"
            ><a-link @click="open(record)">{{ record.title }}</a-link></template
          ></a-table-column
        ><a-table-column title="类型" data-index="type" /><a-table-column
          title="适用范围"
          data-index="scope" /><a-table-column title="版本" data-index="version" /><a-table-column
          title="状态"
          data-index="state"
      /></template>
    </a-table>
    <a-drawer
      v-model:visible="visible"
      :width="820"
      :title="editing ? '维护业务知识' : detail?.title || '业务知识'"
      unmount-on-close
    >
      <a-alert class="mb-4">知识读取持续受来源权限限制；候选、草稿不会自动成为有效知识。</a-alert>
      <a-form v-if="editing" :model="form" layout="vertical">
        <a-form-item :label="`标题（NFC ${protocolTextSize(form.title).scalars} 字符）`" required
          ><a-input v-model="form.title"
        /></a-form-item>
        <a-form-item label="类型" required
          ><a-select v-model="form.type"
            ><a-option v-for="type in types" :key="type.value" :value="type.value">{{ type.label }}</a-option></a-select
          ></a-form-item
        >
        <a-form-item label="适用范围"
          ><a-textarea v-model="form.scope" placeholder="系统、环境、角色及数据范围"
        /></a-form-item>
        <a-form-item label="系统"><a-input v-model="form.system" /></a-form-item
        ><a-form-item label="模块"><a-input v-model="form.module" /></a-form-item>
        <a-form-item label="前置条件"
          ><a-textarea v-model="form.preconditions" placeholder="没有前置条件时请明确填写“不需要”"
        /></a-form-item>
        <a-form-item label="方法与步骤"
          ><a-textarea v-model="form.body" :auto-size="{ minRows: 8, maxRows: 24 }"
        /></a-form-item>
        <a-form-item label="成功判据"><a-textarea v-model="form.successCriteria" /></a-form-item>
        <a-divider>输入与输出契约</a-divider
        ><a-space v-for="(field, index) in knowledgeFields" :key="index" class="mb-2"
          ><a-select v-model="field.kind"
            ><a-option value="input">输入</a-option><a-option value="output">输出</a-option></a-select
          ><a-input v-model="field.name" placeholder="字段" /><a-select v-model="field.type"
            ><a-option>string</a-option><a-option>number</a-option><a-option>integer</a-option
            ><a-option>boolean</a-option></a-select
          ><a-input v-model="field.unit" placeholder="单位" /><a-checkbox v-model="field.nullable">可空</a-checkbox
          ><a-button @click="knowledgeFields.splice(index, 1)">移除</a-button></a-space
        ><a-button @click="knowledgeFields.push({ kind: 'input', name: '', type: 'string', unit: '', nullable: false })"
          >添加输入或输出</a-button
        >
        <template v-if="form.type === 'DATA_PREPARATION'"
          ><a-divider>数据准备方法</a-divider
          ><a-form-item label="查询方法"><a-textarea v-model="preparation.query" /></a-form-item
          ><a-form-item label="创建方法（不需要时明确说明）"><a-textarea v-model="preparation.create" /></a-form-item
          ><a-form-item label="状态核实"><a-textarea v-model="preparation.verify" /></a-form-item
          ><a-form-item label="清理与保留边界"><a-textarea v-model="preparation.cleanup" /></a-form-item
          ><a-form-item label="凭据引用（不能填写凭据内容）"
            ><a-input-tag v-model="preparation.credentialRefs" /></a-form-item
        ></template>
        <a-form-item label="发布可读账号（逐项选择，仍受全部贡献来源权限限制）"
          ><a-input-tag v-model="publishReaders"
        /></a-form-item>
        <a-divider>固定知识关系</a-divider
        ><a-space v-for="(relation, index) in relations" :key="index" class="mb-2"
          ><a-input v-model="relation.knowledgeId" placeholder="关联知识" /><a-input-number
            v-model="relation.version"
            :min="1"
            placeholder="版本"
          /><a-button @click="relations.splice(index, 1)">移除</a-button></a-space
        ><a-button @click="relations.push({ knowledgeId: '', version: 1, bindings: {} })">添加关系</a-button>
        <a-divider>贡献来源</a-divider
        ><a-space
          ><a-select v-model="sourceProject" placeholder="选择有权来源项目"
            ><a-option v-for="project in projects" :key="project.id" :value="project.id">{{
              project.name
            }}</a-option></a-select
          ><a-button :disabled="!sourceProject" @click="chooseSource">选择来源版本</a-button></a-space
        >
        <a-list :data="form.sources" class="mt-3"
          ><template #item="{ item }"
            ><a-list-item
              >来源文档（固定版本）<template #actions
                ><a-link @click="readSource(item)">阅读</a-link></template
              ></a-list-item
            ></template
          ></a-list
        >
        <a-alert v-if="issues.length" type="warning" class="mt-3">草稿不可发布：{{ issues.join('；') }}</a-alert>
      </a-form>
      <template v-else-if="detail"
        ><a-descriptions :column="1" bordered
          ><a-descriptions-item label="适用范围">{{ detail.scope }}</a-descriptions-item
          ><a-descriptions-item label="前置条件">{{ detail.preconditions }}</a-descriptions-item
          ><a-descriptions-item label="成功判据">{{ detail.successCriteria }}</a-descriptions-item></a-descriptions
        ><div class="my-4 whitespace-pre-wrap">{{ detail.body }}</div
        ><a-divider>关联与来源</a-divider><ContentReader :value="detail.sources" /><ContentReader
          :value="detail.relations"
      /></template>
      <a-divider v-if="detail?.id && capabilities.includes('KNOWLEDGE_PUBLISH')">历史版本与使用记录</a-divider
      ><a-space v-for="version in history.versions" :key="Number(version.version)" class="mb-2"
        ><a-link @click="openVersion(Number(version.version))">版本 {{ version.version }} · {{ version.state }}</a-link
        ><a-button
          @click="
            restoreVersion = Number(version.version);
            govern('restore');
          "
          >恢复为新草稿</a-button
        ></a-space
      ><ContentReader :value="history.usage" />
      <template #footer
        ><a-space>
          <a-button v-if="editing" :loading="saving" type="primary" @click="save">保存草稿</a-button>
          <a-button v-else-if="capabilities.includes('KNOWLEDGE_EDIT')" @click="edit">编辑新版本</a-button>
          <a-button
            v-if="detail?.id && capabilities.includes('KNOWLEDGE_PUBLISH')"
            :loading="saving"
            @click="govern('publish')"
            >人工发布</a-button
          >
          <a-button v-if="detail?.id && capabilities.includes('KNOWLEDGE_PUBLISH')" @click="govern('disable')"
            >停用</a-button
          >
          <a-button
            v-if="detail?.id && capabilities.includes('KNOWLEDGE_PUBLISH')"
            status="danger"
            @click="confirmGovern('delete')"
            >停用并删除</a-button
          >
          <a-button
            v-if="detail?.id && capabilities.includes('KNOWLEDGE_PUBLISH')"
            status="danger"
            @click="confirmGovern('revoke')"
            >安全撤回</a-button
          >
        </a-space></template
      >
    </a-drawer>
    <a-modal v-model:visible="sourceVisible" title="选择已授权来源版本" :footer="false"
      ><a-list :data="sourceVersions"
        ><template #item="{ item }"
          ><a-list-item
            ><a-link @click="addSource(item)"
              >{{ item.assetName || '来源文档' }} · v{{ item.versionNo }}</a-link
            ></a-list-item
          ></template
        ></a-list
      ></a-modal
    >
    <a-modal v-model:visible="readerVisible" title="来源内容" :width="900" :footer="false"
      ><ContentReader :value="sourceContent"
    /></a-modal>
    <a-modal v-model:visible="environmentVisible" title="知识操作适用环境" @ok="performPending"
      ><a-input v-model="environmentId" placeholder="选择已获相应能力的环境标识" /><a-textarea
        v-model="reason"
        class="mt-3"
        placeholder="变更原因"
    /></a-modal>
  </TestAssetPage>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import { Message, Modal } from '@arco-design/web-vue';

  import TestAssetPage from '../components/TestAssetPage.vue';
  import TestAssetTabs from '../components/TestAssetTabs.vue';
  import ContentReader from './ContentReader.vue';

  import { pageTestAssetVersions, type TestAssetVersion } from '@/api/modules/ai-execution';
  import {
    getKnowledge,
    getKnowledgeHistory,
    governKnowledge,
    knowledgeCapabilities,
    type KnowledgeDocument,
    migrateSources,
    previewSource,
    type ProtocolRecord,
    saveKnowledge,
    searchKnowledge,
    type SourceReference,
    stableRequest,
    withdrawMigration,
  } from '@/api/modules/governed-execution';
  import { getProjectList } from '@/api/modules/project-management/project';
  import useAppStore from '@/store/modules/app';
  import { createLatestDetailLoader } from '@/utils/latestDetail';
  import { useProtocolMemoryClear } from '@/utils/protocol-memory';
  import protocolTextSize from '@/utils/protocol-text';

  import type { ProjectListItem } from '@/models/setting/project';

  const app = useAppStore();
  const projects = ref<ProjectListItem[]>([]);
  const sourceProject = ref('');
  const preparation = reactive({ query: '', create: '', verify: '', cleanup: '', credentialRefs: [] as string[] });
  const knowledgeFields = ref<Array<{ kind: string; name: string; type: string; unit: string; nullable: boolean }>>([]);
  async function loadProjects() {
    const org = app.currentOrgId;
    try {
      const result = await getProjectList(org);
      if (org === app.currentOrgId) projects.value = result;
    } catch {
      projects.value = [];
    }
  }
  const route = useRoute();
  const capabilities = ref<string[]>([]);
  const publishReaders = ref<string[]>([]);
  const relations = ref<Array<{ knowledgeId: string; version: number; bindings: ProtocolRecord }>>([]);
  const history = reactive<{ versions: ProtocolRecord[]; usage: ProtocolRecord[] }>({ versions: [], usage: [] });
  const restoreVersion = ref(0);
  const blank = (): KnowledgeDocument => ({
    schemaVersion: '1.0',
    title: '',
    type: 'OPERATION',
    body: '',
    scope: '',
    preconditions: '',
    successCriteria: '',
    sources: [],
    relations: [],
  });
  const form = reactive(blank());
  const keyword = ref('');
  const page = ref(1);
  const total = ref(0);
  const records = ref<KnowledgeDocument[]>([]);
  const loading = ref(false);
  const saving = ref(false);
  const error = ref('');
  const issues = ref<string[]>([]);
  const visible = ref(false);
  const editing = ref(false);
  const detail = ref<KnowledgeDocument>();
  const sourceVisible = ref(false);
  const sourceVersions = ref<TestAssetVersion[]>([]);
  const readerVisible = ref(false);
  const sourceContent = ref<unknown>();
  const filters = reactive({ type: '', scope: '', system: '', module: '' });
  const migrationVisible = ref(false);
  const migrationMode = ref(false);
  const migration = reactive({ batchId: '', environmentId: '', cursor: 0, previewOnly: true });
  const migrationSources = ref<Array<{ projectId: string; assetId: string; versionId: string }>>([]);
  const migrationResult = ref<ProtocolRecord>();
  const environmentVisible = ref(false);
  const environmentId = ref('');
  const reason = ref('');
  const pending = ref('');
  const types = [
    { value: 'RULE', label: '规则' },
    { value: 'NAVIGATION', label: '导航' },
    { value: 'OPERATION', label: '操作' },
    { value: 'DATA_PREPARATION', label: '数据准备' },
    { value: 'RECOVERY', label: '恢复' },
  ];
  async function load() {
    const org = app.currentOrgId;
    loading.value = true;
    error.value = '';
    try {
      const [result, allowed] = await Promise.all([
        searchKnowledge(org, keyword.value, page.value, filters),
        knowledgeCapabilities(org),
      ]);
      if (org !== app.currentOrgId) return;
      records.value = result.records;
      total.value = result.total;
      capabilities.value = allowed;
    } catch {
      error.value = '知识暂不可访问，请检查权限或按错误提示中的追踪编号联系管理员。';
      records.value = [];
      total.value = 0;
      detail.value = undefined;
      visible.value = false;
    } finally {
      loading.value = false;
    }
  }
  async function migrateBatch() {
    const org = app.currentOrgId;
    saving.value = true;
    try {
      const result = await migrateSources(org, { ...migration, sources: migrationSources.value });
      if (org !== app.currentOrgId) return;
      migrationResult.value = result;
      migration.cursor = Number(result.nextCursor);
      await load();
    } finally {
      saving.value = false;
    }
  }
  async function withdrawBatch() {
    const org = app.currentOrgId;
    const result = await withdrawMigration(org, {
      batchId: migration.batchId,
      environmentId: migration.environmentId,
      reason: reason.value || '人工按批次撤回',
    });
    if (org === app.currentOrgId) {
      migrationResult.value = result;
      await load();
    }
  }
  function search() {
    page.value = 1;
    load();
  }

  function changePage(value: number) {
    page.value = value;
    load();
  }
  const detailLoader = createLatestDetailLoader({
    fetch: async (serialized: string) => {
      const target = JSON.parse(serialized) as { id: string; version?: number };
      const org = app.currentOrgId;
      const [value, versions] = await Promise.all([
        getKnowledge(org, target.id, target.version),
        getKnowledgeHistory(org, target.id),
      ]);
      return { value, versions };
    },
    start: () => {
      detail.value = undefined;
      visible.value = false;
    },
    success: ({ value, versions }) => {
      detail.value = value;
      publishReaders.value = value.readers || [];
      Object.assign(history, versions);
      visible.value = true;
      editing.value = false;
    },
    failure: () => {
      detail.value = undefined;
      visible.value = false;
    },
    finish: () => {
      loading.value = false;
    },
  });
  function open(value: KnowledgeDocument) {
    return detailLoader.load(JSON.stringify({ id: value.id }));
  }
  function openVersion(version: number) {
    return detail.value?.id ? detailLoader.load(JSON.stringify({ id: detail.value.id, version })) : Promise.resolve();
  }
  function resetForm() {
    Object.keys(form).forEach((key) => delete (form as unknown as ProtocolRecord)[key]);
    Object.assign(form, blank());
  }
  function create() {
    detailLoader.invalidate();
    detail.value = undefined;
    resetForm();
    Object.assign(form, blank(), {
      system: '',
      module: '',
      origin: undefined,
      inputs: {},
      outputs: {},
      readers: undefined,
    });
    knowledgeFields.value = [];
    Object.assign(preparation, { query: '', create: '', verify: '', cleanup: '', credentialRefs: [] });
    relations.value = [];
    publishReaders.value = [];
    Object.assign(history, { versions: [], usage: [] });
    issues.value = [];
    editing.value = true;
    visible.value = true;
  }
  function edit() {
    if (detail.value) {
      Object.assign(form, JSON.parse(JSON.stringify(detail.value)));
      knowledgeFields.value = ['input', 'output'].flatMap((kind) =>
        Object.entries(kind === 'input' ? form.inputs || {} : form.outputs || {}).map(([name, value]) => {
          const field = value as ProtocolRecord;
          return { name, kind, type: String(field.type), unit: String(field.unit || ''), nullable: !!field.nullable };
        })
      );
      Object.assign(
        preparation,
        detail.value.preparation || { query: '', create: '', verify: '', cleanup: '', credentialRefs: [] }
      );
      relations.value = detail.value.relations.map((item) => ({
        knowledgeId: String(item.knowledgeId),
        version: Number(item.version),
        bindings: (item.bindings || {}) as ProtocolRecord,
      }));
    }
    editing.value = true;
  }
  function save() {
    pending.value = 'save';
    environmentVisible.value = true;
  }
  function govern(action: string) {
    pending.value = action;
    environmentVisible.value = true;
  }
  function confirmGovern(action: string) {
    Modal.warning({
      title: action === 'revoke' ? '安全撤回将暂停受影响执行' : '停用并从新任务检索中移除',
      content: '历史引用和执行事实保留，不会删除证据。',
      onOk: () => govern(action),
    });
  }
  async function performPending() {
    saving.value = true;
    try {
      if (pending.value === 'save') {
        const fields = (kind: string) =>
          Object.fromEntries(
            knowledgeFields.value
              .filter((field) => field.kind === kind)
              .map((field) => [
                field.name,
                {
                  ...(((kind === 'input' ? form.inputs : form.outputs)?.[field.name] || {}) as ProtocolRecord),
                  type: field.type,
                  unit: field.unit,
                  nullable: field.nullable,
                },
              ])
          );
        const content = {
          schemaVersion: form.schemaVersion,
          title: form.title,
          type: form.type,
          body: form.body,
          scope: form.scope,
          preconditions: form.preconditions,
          successCriteria: form.successCriteria,
          system: form.system || '',
          module: form.module || '',
          inputs: fields('input'),
          outputs: fields('output'),
          sources: form.sources,
          relations: relations.value,
          ...(form.type === 'DATA_PREPARATION' ? { preparation: { ...preparation } } : {}),
          ...(form.origin ? { origin: form.origin } : {}),
          ...(form.readers ? { readers: form.readers } : {}),
        };
        const result = await saveKnowledge(
          app.currentOrgId,
          stableRequest(`${app.currentOrgId}:knowledge:${detail.value?.id || 'new'}`, {
            environmentId: environmentId.value,
            content,
            ...(detail.value?.id
              ? { knowledgeId: detail.value.id, expectedKnowledgeVersion: detail.value.expectedKnowledgeVersion }
              : {}),
          })
        );
        issues.value = result.validationIssues;
        detail.value = {
          ...form,
          id: result.knowledgeId,
          version: result.version,
          expectedKnowledgeVersion: result.version,
        };
        Message.success('草稿已保存');
      } else if (detail.value?.id) {
        await governKnowledge(
          app.currentOrgId,
          pending.value,
          stableRequest(`${app.currentOrgId}:knowledge:${detail.value.id}:${pending.value}`, {
            knowledgeId: detail.value.id,
            expectedKnowledgeVersion: detail.value.expectedKnowledgeVersion,
            environmentId: environmentId.value,
            reason: reason.value || '人工维护',
            ...(pending.value === 'publish' && publishReaders.value.length ? { readers: publishReaders.value } : {}),
            ...(pending.value === 'restore' ? { version: restoreVersion.value } : {}),
          })
        );
        visible.value = false;
        await load();
      }
    } finally {
      saving.value = false;
    }
  }
  async function chooseSource() {
    const org = app.currentOrgId;
    const result = await pageTestAssetVersions({
      projectId: sourceProject.value,
      assetType: 'DOCUMENT',
      current: 1,
      pageSize: 100,
    });
    if (org !== app.currentOrgId) return;
    sourceVersions.value = result.list.filter((item) => item.status === 'PUBLISHED');
    sourceVisible.value = true;
  }
  function addSource(item: TestAssetVersion) {
    if (migrationMode.value) {
      migrationSources.value.push({ projectId: item.projectId, assetId: item.assetId, versionId: item.id });
      migrationMode.value = false;
      sourceVisible.value = false;
      return;
    }
    if (!form.sources.some((source) => source.versionId === item.id))
      form.sources.push({
        projectId: item.projectId,
        assetType: 'DOCUMENT',
        assetId: item.assetId,
        versionId: item.id,
        snapshotHash: item.contentHash,
        contributes: true,
      });
    sourceVisible.value = false;
  }
  const sourceLoader = createLatestDetailLoader({
    fetch: async (serialized: string) => {
      const source = JSON.parse(serialized) as SourceReference;
      return previewSource(source.projectId, source.assetId, source.versionId);
    },
    start: () => {
      sourceContent.value = undefined;
      readerVisible.value = false;
    },
    success: (value) => {
      sourceContent.value = value.content;
      readerVisible.value = true;
    },
    failure: () => {
      sourceContent.value = undefined;
      readerVisible.value = false;
    },
    finish: () => {
      loading.value = false;
    },
  });
  function readSource(source: SourceReference) {
    return sourceLoader.load(JSON.stringify(source));
  }
  watch(
    () => app.currentOrgId,
    () => {
      detailLoader.invalidate();
      sourceLoader.invalidate();
      records.value = [];
      detail.value = undefined;
      visible.value = false;
      page.value = 1;
      projects.value = [];
      sourceProject.value = '';
      loadProjects();
      load();
    }
  );
  useProtocolMemoryClear(() => {
    detailLoader.invalidate();
    sourceLoader.invalidate();
    records.value = [];
    detail.value = undefined;
    visible.value = false;
    sourceContent.value = undefined;
    readerVisible.value = false;
    resetForm();
    sourceVisible.value = false;
    sourceVersions.value = [];
    capabilities.value = [];
    migrationSources.value = [];
    migrationResult.value = undefined;
    migrationVisible.value = false;
    publishReaders.value = [];
    knowledgeFields.value = [];
    relations.value = [];
    Object.assign(history, { versions: [], usage: [] });
  });
  onMounted(async () => {
    await Promise.all([load(), loadProjects()]);
    const project = route.query.sourceProject;
    const asset = route.query.sourceAsset;
    const version = route.query.sourceVersion;
    if (typeof project !== 'string' || typeof asset !== 'string' || typeof version !== 'string') return;
    const org = app.currentOrgId;
    const source = await previewSource(project, asset, version);
    if (org !== app.currentOrgId) return;
    create();
    form.title = source.name;
    form.body = typeof source.content === 'string' ? source.content : JSON.stringify(source.content, null, 2);
    form.sources = [
      {
        projectId: project,
        assetType: 'DOCUMENT',
        assetId: asset,
        versionId: version,
        snapshotHash: source.snapshotHash,
        contributes: true,
      },
    ];
    form.origin = {
      sourceType: 'DOCUMENT',
      sourceId: asset,
      assetVersionId: version,
      snapshotHash: source.snapshotHash,
      resultSource: 'AGENT_REPORTED',
      validationMode: 'STRUCTURE_ONLY_V1',
    };
    issues.value = ['来源仅提取为编辑预览，请补齐适用范围、前置和成功判据后人工保存、发布'];
  });
</script>
