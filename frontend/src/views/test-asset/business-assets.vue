<template>
  <TestAssetPage>
    <h2>业务资产</h2>
    <a-alert v-if="error" type="error" role="alert">{{ error }}</a-alert>
    <div class="mb-4 flex flex-wrap items-end gap-3">
      <form aria-label="查询业务资产" @submit.prevent="load">
        <label for="business-keyword">业务名称</label>
        <input id="business-keyword" v-model="keyword" name="keyword" type="search" maxlength="255" />
        <button type="submit">查询</button>
      </form>
      <a-checkbox v-model="archived" @change="load">查看已归档</a-checkbox>
      <a-button v-if="canWrite" type="primary" @click="create">新建业务理解</a-button>
      <label v-if="canWrite" for="business-file">上传 Markdown 或 JSON 文档</label>
      <input v-if="canWrite" id="business-file" type="file" accept=".md,.json" @change="upload" />
    </div>
    <p role="status">{{ rows.length }} 份文档，{{ branchCount }} 个操作分支</p>
    <div class="flex gap-4">
      <aside class="w-[240px] shrink-0" aria-label="业务分类">
        <a-button @click="category = ''">全部业务（{{ rows.length }}）</a-button>
        <a-tree :data="tree" @select="selectCategory" />
      </aside>
      <a-table class="min-w-0 flex-1" :data="filtered" :loading="loading" row-key="id">
        <template #empty><a-empty description="暂无业务资产，可新建或由 AI 探索后上传" /></template>
        <template #columns>
          <a-table-column title="业务理解"
            ><template #cell="{ record }"
              >{{ record.title }} <a-tag v-if="record.restricted">历史私有</a-tag></template
            ></a-table-column
          >
          <a-table-column title="系统" data-index="systemKey" />
          <a-table-column title="模块" data-index="moduleKey" />
          <a-table-column title="流程" data-index="flowKey" />
          <a-table-column title="操作分支" data-index="branchCount" />
          <a-table-column title="当前版本" data-index="versionNo" />
          <a-table-column title="状态"
            ><template #cell="{ record }">{{ statusLabel(record.status) }}</template></a-table-column
          >
          <a-table-column title="操作"
            ><template #cell="{ record }">
              <a-space>
                <a-link @click="open(record)">查看</a-link>
                <a-link v-if="canWrite && !archived" @click="edit(record)">编辑与归类</a-link>
                <a-link v-if="canWrite && !archived" @click="prepareMerge(record)">合并</a-link>
                <a-link @click="versions(record)">资产版本</a-link>
                <a-link v-if="canWrite" @click="archive(record)">{{ archived ? '恢复' : '归档' }}</a-link>
              </a-space>
            </template></a-table-column
          >
        </template>
      </a-table>
    </div>
    <a-drawer v-model:visible="detailVisible" title="业务理解" :width="800" unmount-on-close>
      <a-spin v-if="detailLoading" />
      <a-alert v-if="detailError" type="error">{{ detailError }}</a-alert>
      <template v-if="detail">
        <a-alert v-if="detail.restricted"
          >此文档沿用历史创建者可见范围。如需项目共享，可合并到项目公共业务文档，原版本保留。</a-alert
        >
        <h3>{{ current?.snapshot.title }}</h3>
        <a-tabs>
          <a-tab-pane key="understanding" title="业务理解">
            <pre class="whitespace-pre-wrap break-words">{{ current?.snapshot.document.content }}</pre>
          </a-tab-pane>
          <a-tab-pane key="branches" title="操作分支">
            <a-list
              ><a-list-item v-for="branch in current?.snapshot.branches" :key="branch.branchKey">
                {{ branch.title }} · {{ branch.understandingStatus }}
                <a-link v-for="evidenceId in branch.evidenceRefs" :key="evidenceId" @click="previewEvidence(evidenceId)"
                  >查看证据</a-link
                >
              </a-list-item></a-list
            >
          </a-tab-pane>
          <a-tab-pane key="history" title="来源与变更"
            ><a-list>
              <a-list-item v-for="item in detail.audit" :key="`${item.createTime}-${item.versionId}`"
                >{{ item.actorId }} · {{ item.eventType }} · {{ item.reason }}</a-list-item
              >
            </a-list></a-tab-pane
          >
          <a-tab-pane key="relations" title="关联追溯"
            ><a-list
              ><a-list-item v-for="item in detail.relations" :key="`${item.sourceId}-${item.targetId}`"
                >{{ item.sourceId }} → {{ item.targetId }} · {{ item.relationType }}</a-list-item
              ></a-list
            ></a-tab-pane
          >
        </a-tabs>
      </template>
    </a-drawer>
    <a-modal v-model:visible="evidenceVisible" title="业务理解来源证据" :footer="false" @close="clearEvidence">
      <img v-if="evidenceUrl" :src="evidenceUrl" alt="执行器上传的业务理解来源证据" class="max-w-full" />
    </a-modal>
    <a-drawer v-model:visible="editorVisible" title="维护业务理解" :width="800" unmount-on-close>
      <form aria-label="业务理解编辑" @submit.prevent="save">
        <fieldset :disabled="saving" class="editor-fields"
          ><legend>业务理解内容</legend>
          <p v-if="saveError" role="alert">{{ saveError }}</p>
          <details v-if="comparisonContent"
            ><summary>当前生效业务理解（合并对照）</summary
            ><pre class="whitespace-pre-wrap break-words">{{ comparisonContent }}</pre>
          </details>
          <template v-if="mergeSource">
            <label for="merge-target">合并到已有业务资产</label>
            <select id="merge-target" v-model="mergeTarget" required @change="loadMergeTarget"
              ><option value="">请选择</option
              ><option v-for="item in rows.filter((r) => r.id !== mergeSource?.id)" :key="item.id" :value="item.id">{{
                item.title
              }}</option></select
            >
            <p>核对合并后的完整文档和分支再保存；源文档归档，原版本保留。</p>
          </template>
          <label for="business-title">业务标题（必填）</label>
          <input id="business-title" v-model="form.title" name="title" required maxlength="255" />
          <label for="business-system">系统名称</label
          ><input id="business-system" v-model="form.scope.systemKey" name="system" maxlength="128" />
          <label for="business-module">模块名称</label
          ><input id="business-module" v-model="form.scope.moduleKey" name="module" maxlength="128" />
          <label for="business-flow">业务流程名称</label
          ><input id="business-flow" v-model="form.scope.flowKey" name="flow" maxlength="128" />
          <label for="business-pages">页面名称（每行一个）</label
          ><textarea id="business-pages" v-model="pages" name="pages" rows="3" />
          <p>同一页面或流程的操作放在同一文档；范围不明确时保留为待归类。</p>
          <label for="business-content">业务理解 Markdown（必填）</label>
          <textarea
            id="business-content"
            v-model="form.document.content"
            name="content"
            rows="16"
            required
            maxlength="500000"
          />
          <fieldset
            ><legend>操作分支</legend>
            <div v-for="(branch, index) in form.branches" :key="index" class="mb-3">
              <label :for="`branch-title-${index}`">分支说明</label
              ><input
                :id="`branch-title-${index}`"
                v-model="branch.title"
                :name="`branch-title-${index}`"
                required
                maxlength="255"
              />
              <label :for="`branch-state-${index}`">理解依据</label
              ><select
                :id="`branch-state-${index}`"
                v-model="branch.understandingStatus"
                :name="`branch-state-${index}`"
                ><option value="OBSERVED">已观察</option
                ><option value="INFERRED">推断</option
                ><option value="UNCONFIRMED">待确认</option></select
              >
              <button type="button" @click="form.branches.splice(index, 1)">移除此分支</button>
            </div>
            <button type="button" @click="addBranch">增加操作分支</button>
          </fieldset>
          <label for="business-change">本次变化（必填）</label
          ><textarea id="business-change" v-model="form.changeSummary" name="change" required maxlength="1000" />
          <button type="submit" :disabled="saving">{{ saving ? '正在保存' : '保存新版本' }}</button>
        </fieldset>
      </form>
    </a-drawer>
  </TestAssetPage>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';

  import TestAssetPage from './components/TestAssetPage.vue';

  import MSR from '@/api/http/index';
  import type { BusinessDetail, BusinessRow, BusinessSubmission } from '@/api/modules/business-assets';
  import {
    archiveBusinessAsset,
    getBusinessAsset,
    listBusinessAssets,
    submitBusinessAsset,
  } from '@/api/modules/business-assets';
  import useAppStore from '@/store/modules/app';
  import businessAssetError from '@/utils/businessAssetError';
  import { businessAssetTree, matchesBusinessCategory } from '@/utils/businessAssetTree';
  import { hasAnyPermission } from '@/utils/permission';

  const app = useAppStore();
  const router = useRouter();
  const route = useRoute();
  const comparisonContent = ref('');
  const evidenceVisible = ref(false);
  const evidenceUrl = ref('');

  const rows = ref<BusinessRow[]>([]);
  const keyword = ref('');
  const archived = ref(false);
  const category = ref('');
  const loading = ref(false);
  const saving = ref(false);
  const error = ref('');
  const saveError = ref('');
  const detail = ref<BusinessDetail>();
  const detailVisible = ref(false);
  const detailLoading = ref(false);
  const detailError = ref('');
  let detailGeneration = 0;
  let editorRequest = 0;
  const editorVisible = ref(false);
  const pages = ref('');
  const mergeSource = ref<BusinessRow>();
  const mergeTarget = ref('');
  const canWrite = computed(() => hasAnyPermission(['BUSINESS_ASSET:WRITE']));
  const empty = (): BusinessSubmission => ({
    projectId: app.currentProjectId,
    schemaVersion: '1.0',
    operation: 'CREATE',
    baseVersion: 0,
    idempotencyKey: crypto.randomUUID(),
    scope: { systemKey: '', moduleKey: '', pageKeys: [], flowKey: '' },
    title: '',
    document: {
      format: 'markdown',
      content: '## 业务目标\n\n## 前置条件\n\n## 操作与业务规则\n\n## 关联流程\n\n## 待确认内容\n',
    },
    branches: [],
    changeSummary: '',
  });
  const form = ref<BusinessSubmission>(empty());
  let lastSubmittedContent = '';
  const current = computed(
    () => detail.value?.versions.find((v) => v.id === detail.value?.currentVersionId) || detail.value?.versions[0]
  );
  const branchCount = computed(() => rows.value.reduce((sum, row) => sum + row.branchCount, 0));
  const filtered = computed(() => rows.value.filter((row) => matchesBusinessCategory(row, category.value)));
  const tree = computed(() => businessAssetTree(rows.value));
  function selectCategory(keys: (string | number)[]) {
    category.value = String(keys[0] || '');
  }
  function statusLabel(status: string) {
    return (
      ({ CURRENT: '当前生效', CONFLICT: '待合并', UNCLASSIFIED: '待归类', HISTORY: '历史' } as Record<string, string>)[
        status
      ] || status
    );
  }
  let generation = 0;
  function clearEvidence() {
    if (evidenceUrl.value) URL.revokeObjectURL(evidenceUrl.value);
    evidenceUrl.value = '';
  }
  async function previewEvidence(id: string) {
    clearEvidence();
    const project = app.currentProjectId;
    try {
      const result = await MSR.get<{ contentType: string; content: string }>({
        url: `/external-executors/evidence/${encodeURIComponent(id)}`,
        params: { projectId: project },
      });
      if (project !== app.currentProjectId) return;
      const bytes = Uint8Array.from(atob(result.content), (c) => c.charCodeAt(0));
      evidenceUrl.value = URL.createObjectURL(new Blob([bytes], { type: result.contentType }));
      evidenceVisible.value = true;
    } catch (failure) {
      error.value = businessAssetError(failure, '证据读取失败，请检查权限或加密配置。');
    }
  }
  onBeforeUnmount(clearEvidence);
  async function load() {
    const run = ++generation;
    error.value = '';
    loading.value = true;
    try {
      const value = app.currentProjectId
        ? await listBusinessAssets(app.currentProjectId, keyword.value, archived.value)
        : [];
      if (run === generation) rows.value = value;
    } catch (failure) {
      if (run === generation) error.value = businessAssetError(failure, '业务资产加载失败，请检查权限或网络后重试。');
    } finally {
      if (run === generation) loading.value = false;
    }
  }
  function create() {
    ++editorRequest;
    comparisonContent.value = '';
    mergeSource.value = undefined;
    mergeTarget.value = '';
    form.value = empty();
    pages.value = '';
    saveError.value = '';
    editorVisible.value = true;
  }
  async function open(row: BusinessRow) {
    const run = ++detailGeneration;
    const project = app.currentProjectId;
    detail.value = undefined;
    detailError.value = '';
    detailVisible.value = true;
    detailLoading.value = true;
    try {
      const value = await getBusinessAsset(project, row.id);
      if (run === detailGeneration && project === app.currentProjectId) detail.value = value;
    } catch (failure) {
      if (run === detailGeneration && project === app.currentProjectId)
        detailError.value = businessAssetError(failure, '无法读取业务理解，请刷新后重试。');
    } finally {
      if (run === detailGeneration) detailLoading.value = false;
    }
  }
  async function edit(row: BusinessRow) {
    const run = ++editorRequest;
    const project = app.currentProjectId;
    mergeSource.value = undefined;
    comparisonContent.value = '';
    try {
      const value = await getBusinessAsset(app.currentProjectId, row.id);
      if (run !== editorRequest || project !== app.currentProjectId) return;
      const version = value.versions.find((v) => v.id === value.currentVersionId) || value.versions[0];
      form.value = {
        ...structuredClone(version.snapshot),
        projectId: app.currentProjectId,
        operation: 'UPDATE',
        assetId: row.id,
        baseVersion: value.versions.find((v) => v.id === value.currentVersionId)?.versionNo || 0,
        idempotencyKey: crypto.randomUUID(),
        changeSummary: '',
        source: undefined,
      };
      pages.value = form.value.scope.pageKeys.join('\n');
      saveError.value = '';
      editorVisible.value = true;
    } catch (failure) {
      error.value = businessAssetError(failure, '无法读取编辑内容，请重试。');
    }
  }
  async function editSelectedVersion() {
    const run = ++editorRequest;
    const assetId = String(route.query.assetId || '');
    const versionId = String(route.query.versionId || '');
    if (!assetId || !versionId || !canWrite.value || !app.currentProjectId) return;
    const project = app.currentProjectId;
    try {
      const value = await getBusinessAsset(project, assetId);
      if (project !== app.currentProjectId || run !== editorRequest) return;
      const candidate = value.versions.find((v) => v.id === versionId);
      const active = value.versions.find((v) => v.id === value.currentVersionId);
      if (!candidate) throw new Error('version');
      form.value = {
        ...structuredClone(candidate.snapshot),
        projectId: project,
        assetId,
        operation: 'UPDATE',
        baseVersion: active?.versionNo || 0,
        idempotencyKey: crypto.randomUUID(),
        source: undefined,
        changeSummary: `整理合并 v${candidate.versionNo}`,
      };
      pages.value = form.value.scope.pageKeys.join('\n');
      comparisonContent.value = active?.snapshot.document.content || '';
      mergeSource.value = undefined;
      saveError.value = '';
      editorVisible.value = true;
    } catch (failure) {
      error.value = businessAssetError(failure, '待整理版本不可访问，请刷新资产版本后重试。');
    }
  }
  function addBranch() {
    form.value.branches.push({
      branchKey: crypto.randomUUID(),
      title: '',
      understandingStatus: 'UNCONFIRMED',
      evidenceRefs: [],
    });
  }
  function prepareMerge(row: BusinessRow) {
    create();
    mergeSource.value = row;
  }
  async function loadMergeTarget() {
    const project = app.currentProjectId;
    const source = mergeSource.value;
    const target = rows.value.find((r) => r.id === mergeTarget.value);
    if (!source || !target) return;
    try {
      const sourceDetail = await getBusinessAsset(app.currentProjectId, source.id);
      const sourceVersion =
        sourceDetail.versions.find((v) => v.id === sourceDetail.currentVersionId) || sourceDetail.versions[0];
      await edit(target);
      if (project !== app.currentProjectId || form.value.assetId !== target.id) return;
      mergeSource.value = source;
      form.value.document.content += `\n\n## 合并业务理解：${sourceVersion.snapshot.title}\n\n${sourceVersion.snapshot.document.content}`;
      form.value.branches.push(
        ...sourceVersion.snapshot.branches.map((branch) => ({
          ...branch,
          branchKey: `${source.id.slice(0, 8)}-${branch.branchKey}`.slice(0, 128),
        }))
      );
      form.value.changeSummary = `合并业务资产 ${source.title}`;
    } catch (failure) {
      saveError.value = businessAssetError(failure, '无法读取合并源，请重新选择。');
    }
  }
  async function save() {
    if (saving.value) return;
    saving.value = true;
    saveError.value = '';
    form.value.scope.pageKeys = pages.value
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean);
    const fingerprint = JSON.stringify({ ...form.value, idempotencyKey: '' });
    if (lastSubmittedContent && fingerprint !== lastSubmittedContent) form.value.idempotencyKey = crypto.randomUUID();
    lastSubmittedContent = fingerprint;
    const submission = JSON.parse(JSON.stringify(form.value)) as BusinessSubmission;
    const project = app.currentProjectId;
    try {
      const result = mergeSource.value
        ? await MSR.post<{ status: string }>({
            url: `/business-assets/${mergeSource.value.id}/merge`,
            data: { sourceRevision: mergeSource.value.revision, target: submission },
          })
        : await submitBusinessAsset(submission);
      if (project !== app.currentProjectId || submission.idempotencyKey !== form.value.idempotencyKey) return;
      const messages: Record<string, string> = {
        CONFLICT: '内容已保留为冲突版本，请到资产版本比较并合并。',
        UNCLASSIFIED: '文档已保留，请补充业务范围完成归类。',
        CURRENT: '业务理解已保存为新版本',
      };
      Message.success(messages[result.status] || '提交已保存');
      editorVisible.value = false;
      await load();
    } catch (failure) {
      if (project === app.currentProjectId && submission.idempotencyKey === form.value.idempotencyKey)
        saveError.value = businessAssetError(failure, '保存失败，请检查权限、字段和网络后重试；原输入已保留。');
    } finally {
      saving.value = false;
    }
  }
  async function upload(event: Event) {
    const project = app.currentProjectId;
    const editorKey = form.value.idempotencyKey;
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    try {
      if (file.size > 1500000) throw new Error('size');
      const content = await file.text();
      if (project !== app.currentProjectId || editorKey !== form.value.idempotencyKey) return;
      create();
      if (file.name.endsWith('.json')) {
        const parsed = JSON.parse(content) as BusinessSubmission;
        if (parsed.schemaVersion !== '1.0' || !parsed.scope || !parsed.document || !Array.isArray(parsed.branches))
          throw new Error('format');
        form.value = {
          ...parsed,
          projectId: app.currentProjectId,
          idempotencyKey: crypto.randomUUID(),
          source: undefined,
        };
        pages.value = parsed.scope.pageKeys.join('\n');
      } else {
        form.value.title = file.name.replace(/\.md$/i, '');
        form.value.document.content = content;
        form.value.changeSummary = '上传业务理解文档';
      }
    } catch (failure) {
      error.value = businessAssetError(failure, '文件读取失败，请上传 1.5 MB 以内的 Markdown 或符合协议的 JSON。');
      editorVisible.value = false;
    } finally {
      input.value = '';
    }
  }
  function versions(row: BusinessRow) {
    router.push({ path: '/test-assets/versions', query: { businessAssetId: row.id } });
  }
  async function archive(row: BusinessRow) {
    try {
      await archiveBusinessAsset(
        row.id,
        app.currentProjectId,
        !archived.value,
        row.revision,
        archived.value ? '人工恢复业务资产' : '人工归档业务资产'
      );
      await load();
    } catch (failure) {
      error.value = businessAssetError(failure, '状态修改失败，资产可能已变化，请刷新后重试。');
    }
  }
  watch(
    () => app.currentProjectId,
    () => {
      rows.value = [];
      ++editorRequest;
      ++detailGeneration;
      clearEvidence();
      evidenceVisible.value = false;
      detailVisible.value = false;
      editorVisible.value = false;
      category.value = '';
      load();
    },
    { immediate: true }
  );
  watch([() => app.currentProjectId, () => route.query.assetId, () => route.query.versionId], editSelectedVersion, {
    immediate: true,
  });
</script>

<style scoped>
  form label {
    display: block;
    margin-block: 1rem 0.25rem;
  }
  .editor-fields {
    padding: 0;
    border: 0;
  }
  form input,
  form textarea,
  form select {
    padding: 0.5rem;
    width: 100%;
    max-width: 100%;
    border: 1px solid var(--color-border-3);
    color: var(--color-text-1);
    background: var(--color-bg-2);
    font: inherit;
  }
  form button {
    margin-block: 0.75rem;
    padding: 0.5rem 1rem;
    border: 1px solid var(--color-border-3);
  }
</style>
