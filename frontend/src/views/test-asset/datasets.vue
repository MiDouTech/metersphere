<template>
  <section class="datasets">
    <a-space class="dataset-toolbar" wrap
      ><a-button
        type="primary"
        :loading="busy"
        :disabled="!canWrite || parseStatus === 'PENDING' || parseStatus === 'RUNNING'"
        @click="fileInput?.click()"
        >上传测试数据</a-button
      ><a-button :disabled="!canWrite" @click="create">人工新建</a-button></a-space
    >
    <p v-if="limits"
      >支持 {{ limits.formats.join('、') }}，最大 {{ Math.round(limits.maxBytes / 1024 / 1024) }} MB、{{
        limits.maxRows
      }}
      行。</p
    >
    <a-alert v-if="migration" :type="migration.counts.failed ? 'warning' : 'info'"
      >历史数据自动迁移：已完成 {{ migration.counts.done }}，待处理 {{ migration.counts.pending }}，进行中
      {{ migration.counts.running }}，失败 {{ migration.counts.failed }}。<a-button
        v-if="canWrite && migration.counts.failed"
        :loading="migrationBusy"
        @click="migrate(true)"
        >重试一个失败项</a-button
      ></a-alert
    ><a-alert v-if="migrationError" type="error"
      >{{ migrationError }}<a-button :loading="migrationBusy" @click="migrate()">刷新迁移状态</a-button></a-alert
    >
    <input ref="fileInput" type="file" hidden accept=".json,.txt,.csv,.xlsx,.xls" @change="upload" />
    <a-alert v-if="parseStatus" :type="parseStatus === 'FAILED' ? 'error' : 'info'"
      >上传解析：{{ parseLabel(parseStatus)
      }}<a-button v-if="parseStatus === 'FAILED'" @click="retryParse">重试解析</a-button></a-alert
    ><a-alert v-if="error" type="error">{{ error }}</a-alert>
    <div class="data-layout">
      <aside aria-label="数据分类"
        ><div class="category-header"
          ><a-link @click="filterCategory('')">全部分类</a-link
          ><a-space
            ><a-button
              type="text"
              aria-label="管理分类"
              :disabled="!canManageCategories"
              @click="categoryVisible = true"
              ><icon-settings /></a-button
            ><a-button type="text" aria-label="新增分类" :disabled="!canManageCategories" @click="openCreateCategory"
              ><icon-plus /></a-button></a-space></div
        ><a-tree
          :data="categories"
          :field-names="{ key: 'id', title: 'name' }"
          @select="(keys) => filterCategory(String(keys[0] || ''))"
      /></aside>
      <main>
        <a-space wrap class="list-toolbar"
          ><a-input-search v-model="keyword" placeholder="搜索测试数据" @search="search" /><a-button
            :disabled="!canWrite || !selected.length"
            @click="
              error = '';
              batchVisible = true;
            "
            >批量修改</a-button
          ><a-button :disabled="!canWrite || !selected.length" @click="startQueue">逐项修改内容</a-button
          ><a-button status="danger" :disabled="!canWrite || !selected.length" @click="deleteVisible = true"
            >批量删除</a-button
          ></a-space
        >
        <div class="table-region" role="region" aria-label="测试数据列表" tabindex="0">
          <a-table
            v-model:selected-keys="selected"
            :data="rows"
            row-key="id"
            :scroll="{ x: 1400 }"
            :loading="loading"
            :row-selection="{ type: 'checkbox', showCheckedAll: true }"
            :pagination="{ current: page, pageSize: 20, total }"
            @page-change="changePage"
          >
            <template #columns
              ><a-table-column title="名称" :width="220" ellipsis tooltip
                ><template #cell="{ record }"
                  ><a-link @click="open(record, false)">{{ record.name }}</a-link></template
                ></a-table-column
              ><a-table-column title="说明" data-index="description" :width="180" /><a-table-column
                title="分类"
                :width="160"
                ellipsis
                tooltip
                ><template #cell="{ record }">{{ categoryLabel(record.categoryId) }}</template></a-table-column
              ><a-table-column title="负责人" :width="120" ellipsis tooltip data-index="owner" /><a-table-column
                title="更新时间"
                :width="190"
                ellipsis
                tooltip
                ><template #cell="{ record }">{{
                  record.updateTime ? new Date(record.updateTime).toLocaleString() : '未记录'
                }}</template></a-table-column
              ><a-table-column title="格式" :width="90" ellipsis tooltip data-index="format" /><a-table-column
                title="解析状态"
                :width="110"
                ellipsis
                tooltip
                data-index="parseStatus"
              /><a-table-column title="修订" :width="80" ellipsis tooltip data-index="revision" /><a-table-column
                title="操作"
                :width="270"
                ><template #cell="{ record }"
                  ><a-space
                    ><a-link @click="open(record, false)">详情</a-link
                    ><a-link :disabled="!canWrite" @click="open(record, true)">修改</a-link
                    ><a-popconfirm content="确认删除？" @ok="remove(record)"
                      ><a-link status="danger" :disabled="!canWrite">删除</a-link></a-popconfirm
                    ><a-dropdown @select="(key) => moreAction(String(key), record)"
                      ><a-button aria-label="更多操作">更多操作</a-button
                      ><template #content
                        ><a-doption value="publish">发布到项目</a-doption><a-doption value="task">用于任务</a-doption
                        ><a-doption value="trace">项目引用追溯</a-doption
                        ><a-doption value="versions">历史版本</a-doption
                        ><a-doption value="file" :disabled="!record.hasFile"
                          >下载源上传文件（不含在线修改）</a-doption
                        ></template
                      ></a-dropdown
                    ></a-space
                  ></template
                ></a-table-column
              ></template
            >
          </a-table>
        </div>
        <a-button v-if="jobId && results.some((item) => item.status !== 'SUCCESS')" @click="retryFailed"
          >仅重试失败项</a-button
        ><a-alert v-for="item in results" :key="item.id" :type="item.status === 'SUCCESS' ? 'success' : 'warning'"
          >{{ item.id }}：{{ item.status }}
          {{ item.error ? '操作失败，请刷新后检查权限、引用和版本再重试' : '' }}</a-alert
        >
      </main>
    </div>
    <a-modal v-model:visible="quickCategoryVisible" title="新增分类" :on-before-ok="createCategory"
      ><a-alert v-if="error" type="error">{{ error }}</a-alert
      ><a-form :model="{ name: quickCategoryName }" layout="vertical"
        ><a-form-item label="分类名称" required
          ><a-input v-model="quickCategoryName" :max-length="100" placeholder="请输入分类名称" /></a-form-item></a-form
    ></a-modal>
    <a-modal v-model:visible="deleteVisible" title="批量删除测试数据" :on-before-ok="batchDelete"
      >确认删除所选数据？引用中的数据将被拒绝。<a-alert v-if="error" type="error">{{ error }}</a-alert></a-modal
    >
    <a-modal
      v-model:visible="editorVisible"
      :title="editing ? (draft.id ? '修改测试数据' : '新建测试数据') : '测试数据详情'"
      :footer="false"
      :width="900"
      @close="cancelEditor"
    >
      <a-alert v-if="error" data-testid="data-editor-error" class="dialog-error" type="error">{{ error }}</a-alert
      ><a-spin :loading="detailLoading" style="width: 100%"
        ><a-form :model="draft" layout="vertical"
          ><a-form-item label="名称"
            ><a-input v-model="draft.name" :readonly="editing ? undefined : true" /></a-form-item
          ><a-form-item label="说明"
            ><a-textarea
              v-model="draft.description"
              :textarea-attrs="{ readonly: editing ? undefined : true }"
              :max-length="2000" /></a-form-item
          ><a-form-item label="格式"
            ><a-select
              v-model="draft.format"
              :disabled="!!draft.id || !editing"
              :options="['JSON', 'TXT', 'CSV']" /></a-form-item
          ><a-form-item label="分类"
            ><a-tree-select
              v-model="draft.categoryId"
              :data="categories"
              :field-names="{ key: 'id', title: 'name' }"
              :disabled="!editing"
              allow-clear /></a-form-item
          ><a-form-item label="完整内容（JSON；CSV 使用 rows 二维数组；TXT 使用原文）"
            ><DataTableEditor
              v-if="editing && ['CSV', 'XLS', 'XLSX'].includes(draft.format || '')"
              :key="draft.id || draft.requestId"
              v-model="contentText"
              :format="draft.format" /><a-textarea
              v-else
              v-model="contentText"
              :textarea-attrs="{ readonly: editing ? undefined : true }"
              :auto-size="{ minRows: 12, maxRows: 25 }" /></a-form-item
          ><a-button
            v-if="editing"
            type="primary"
            :disabled="detailLoading || !complete"
            :loading="busy"
            @click="save"
            >{{ queue.length ? '保存并编辑下一项' : '保存' }}</a-button
          ></a-form
        ></a-spin
      >
    </a-modal>
    <a-modal v-model:visible="batchVisible" title="批量修改" :on-before-ok="applyCategory"
      ><a-alert v-if="error" data-testid="data-batch-error" class="dialog-error" type="error">{{ error }}</a-alert
      ><a-select
        v-model="categoryMode"
        :options="[
          { label: '分类不变', value: 'keep' },
          { label: '设置分类', value: 'set' },
          { label: '清空分类', value: 'clear' },
        ]"
      /><a-tree-select
        v-if="categoryMode === 'set'"
        v-model="batchCategory"
        :data="categories"
        :field-names="{ key: 'id', title: 'name' }"
        allow-clear
      /><a-select
        v-model="descriptionMode"
        :options="[
          { label: '说明不变', value: 'keep' },
          { label: '设置说明', value: 'set' },
          { label: '清空说明', value: 'clear' },
        ]"
      /><a-textarea
        v-if="descriptionMode === 'set'"
        v-model="batchDescription"
        placeholder="批量说明（留空保持原说明）"
        :max-length="2000"
      /><p>修改分类和说明，不覆盖内容。清空分类表示未分类。</p></a-modal
    >
    <a-modal v-model:visible="versionsVisible" title="历史版本" :footer="false"
      ><a-list
        ><a-list-item v-for="version in versions" :key="version.id"
          ><a-link @click="readVersion(version.id)">{{ version.versionNo || version.id }}</a-link></a-list-item
        ></a-list
      ></a-modal
    >
    <a-modal
      v-model:visible="publishVisible"
      :title="categoryTrace ? '选择追溯项目' : '选择执行项目并发布固定版本'"
      :on-before-ok="publishSelected"
      ><a-alert v-if="error" data-testid="data-publish-error" class="dialog-error" type="error">{{ error }}</a-alert
      ><a-input v-model="executionProject" placeholder="执行项目 ID（须具有该项目资产发布权限）" /><p>{{
        categoryTrace
          ? '仅查看所选项目的引用追溯，由服务端校验项目权限。'
          : '发布当前固定版本，项目权限由服务端校验；不随当前项目自动选择。'
      }}</p></a-modal
    ><TestAssetCategoryDrawer :key="app.currentOrgId" v-model:visible="categoryVisible" @changed="loadCategories" />
  </section>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';

  import DataTableEditor from './components/DataTableEditor.vue';
  import TestAssetCategoryDrawer from './components/TestAssetCategoryDrawer.vue';

  import { createTestAssetCategory } from '@/api/modules/ai-execution';
  import {
    batchDataAssets,
    type DataAsset,
    type DataCategory,
    type DataEdit,
    dataVersions,
    deleteDataAsset,
    downloadDataFile,
    getDataAsset,
    getDataContent,
    getDataJob,
    getDataLimits,
    getLegacyMigrationStatus,
    type LegacyMigrationStatus,
    listDataAssets,
    listDataCategories,
    publishDataAsset,
    retryDataJob,
    runLegacyMigration,
    saveDataAsset,
    uploadDataAsset,
  } from '@/api/modules/test-data-assets';
  import useAppStore from '@/store/modules/app';
  import { AppError } from '@/utils/appError';
  import businessAssetError from '@/utils/businessAssetError';
  import { hasAnyPermission } from '@/utils/permission';

  import { IconPlus, IconSettings } from '@arco-design/web-vue/es/icon';

  const migration = ref<LegacyMigrationStatus>();
  const migrationError = ref('');
  const migrationBusy = ref(false);
  let migrationGeneration = 0;
  let migrationTimer: ReturnType<typeof setTimeout> | undefined;
  const canManageCategories = computed(() => hasAnyPermission(['TEST_ASSET_CATEGORY:MANAGE']));
  const canWrite = computed(() => hasAnyPermission(['TEST_DATA_ASSET:READ+UPDATE'], ['ORGANIZATION', 'SYSTEM']));
  const publishVisible = ref(false);
  const executionProject = ref('');
  const publishTarget = ref<DataAsset>();
  const taskAfterPublish = ref(false);
  const app = useAppStore();
  const route = useRoute();
  const router = useRouter();
  const rows = ref<DataAsset[]>([]);
  const categories = ref<DataCategory[]>([]);
  const selected = ref<(string | number)[]>([]);
  const keyword = ref('');
  const categoryId = ref('');
  const page = ref(1);
  const total = ref(0);
  const error = ref('');
  const loading = ref(false);
  const busy = ref(false);
  const detailLoading = ref(false);
  const editorVisible = ref(false);
  const editing = ref(false);
  const complete = ref(false);
  const contentText = ref('');
  const draft = reactive<DataEdit>({});
  const queue = ref<DataAsset[]>([]);
  const fileInput = ref<HTMLInputElement>();
  const categoryVisible = ref(false);
  const quickCategoryVisible = ref(false);
  const quickCategoryName = ref('');
  function openCreateCategory() {
    if (!canManageCategories.value) return;
    error.value = '';
    quickCategoryName.value = '';
    quickCategoryVisible.value = true;
  }
  const batchVisible = ref(false);
  const deleteVisible = ref(false);
  const categoryMode = ref('keep');
  const descriptionMode = ref('keep');
  const categoryTrace = ref(false);
  const batchCategory = ref('');
  const batchDescription = ref('');
  const uploadRequests = new Map<string, string>();
  const jobId = ref('');
  const limits = ref<{ maxBytes: number; maxRows: number; formats: string[] }>();
  const parseJob = ref('');
  const parseStatus = ref('');
  let parseTimer: ReturnType<typeof setTimeout> | undefined;
  let detailRequest = 0;
  let active = true;
  let categoryRequest = 0;
  let request = 0;
  onBeforeUnmount(() => {
    clearTimeout(parseTimer);
    clearTimeout(migrationTimer);
    ++migrationGeneration;
    ++detailRequest;
    active = false;
    ++request;
    ++categoryRequest;
  });
  const results = ref<{ id: string; status: string; error?: string }[]>([]);
  const versionsVisible = ref(false);
  const versions = ref<{ id: string; versionNo?: number }[]>([]);
  const versionAsset = ref<DataAsset>();
  function categoryLabel(id?: string): string {
    if (!id || id === '__uncategorized__') return '默认分类';
    const find = (items: DataCategory[]): string | undefined =>
      items.map((item) => (item.id === id ? item.name : find(item.children || []))).find(Boolean);
    return find(categories.value) || '分类不可见';
  }
  function parseLabel(status: string) {
    return (
      (
        {
          PENDING: '等待解析',
          RUNNING: '正在解析',
          READY: '解析完成',
          SUCCESS: '解析完成',
          FAILED: '解析失败，可重试',
        } as Record<string, string>
      )[status] || status
    );
  }
  function fail(reason?: unknown) {
    if (!active) return;
    error.value = businessAssetError(reason, '操作失败，请检查权限、网络和数据版本；并发修改时请重新打开详情。');
  }
  async function load() {
    if (!active) return;
    const token = ++request;
    const organization = app.currentOrgId;
    loading.value = true;
    try {
      const data = await listDataAssets({
        keyword: keyword.value,
        categoryId: categoryId.value || undefined,
        page: page.value,
        pageSize: 20,
      });
      if (active && token === request && organization === app.currentOrgId) {
        rows.value = data.items;
        total.value = data.total;
        selected.value = [];
      }
    } catch (reason) {
      if (active && token === request && organization === app.currentOrgId) fail(reason);
    } finally {
      if (active && token === request && organization === app.currentOrgId) loading.value = false;
    }
  }
  async function loadCategories() {
    if (!active) return;
    const token = ++categoryRequest;
    const organization = app.currentOrgId;
    try {
      const flat = await listDataCategories();
      if (!active || token !== categoryRequest || organization !== app.currentOrgId) return;
      const nodes = new Map(
        flat.map((item) => [
          item.id,
          { ...item, name: `${item.name} (${item.assetCount})`, children: [] as DataCategory[] },
        ])
      );
      const roots: DataCategory[] = [];
      nodes.forEach((node) => {
        const parent = node.parentId ? nodes.get(node.parentId) : undefined;
        if (parent) parent.children.push(node);
        else roots.push(node);
      });
      categories.value = roots;
    } catch (reason) {
      if (active && token === categoryRequest && organization === app.currentOrgId) fail(reason);
    }
  }
  async function createCategory() {
    if (!active) return;
    if (!canManageCategories.value) {
      error.value = '暂无分类管理权限';
      return false;
    }
    const organization = app.currentOrgId;
    const name = quickCategoryName.value.trim();
    if (!name) {
      error.value = '请输入分类名称';
      return false;
    }
    try {
      await createTestAssetCategory({ name });
      if (!active || organization !== app.currentOrgId) return false;
      await loadCategories();
      return true;
    } catch (reason) {
      if (organization === app.currentOrgId) fail(reason);
      return false;
    }
  }
  async function migrate(retryMigrationFailures = false) {
    if (!active) return;
    if (migrationBusy.value || !app.currentOrgId) return;
    const token = migrationGeneration;
    const organization = app.currentOrgId;
    migrationBusy.value = true;
    migrationError.value = '';
    try {
      let status = await getLegacyMigrationStatus();
      if (token !== migrationGeneration || organization !== app.currentOrgId) return;
      if (
        canWrite.value &&
        status.canRun &&
        (status.counts.pending > 0 || status.counts.running > 0 || retryMigrationFailures)
      )
        status = await runLegacyMigration(organization, retryMigrationFailures);
      if (token !== migrationGeneration || organization !== app.currentOrgId) return;
      migration.value = status;
      if (status.hasMore) migrationTimer = setTimeout(() => migrate(), status.canRun ? 1000 : 10000);
      else {
        load();
        loadCategories();
      }
    } catch (reason) {
      if (token === migrationGeneration)
        migrationError.value = businessAssetError(reason, '历史数据迁移暂时失败，请刷新状态后重试。');
    } finally {
      if (token === migrationGeneration) migrationBusy.value = false;
    }
  }
  async function download(row: DataAsset) {
    if (!active) return;
    if (!row.hasFile) return;
    try {
      const blob = await downloadDataFile(row.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = row.name;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (reason) {
      fail(reason);
    }
  }
  function search() {
    page.value = 1;
    load();
  }
  function filterCategory(id: string) {
    categoryId.value = id;
    search();
  }
  function changePage(value: number) {
    page.value = value;
    load();
  }
  function resetDraft() {
    Object.keys(draft).forEach((key) => delete draft[key as keyof DataEdit]);
  }
  function cancelEditor() {
    detailLoading.value = false;
    ++detailRequest;
    queue.value = [];
    complete.value = false;
  }
  function create() {
    detailLoading.value = false;
    error.value = '';
    ++detailRequest;
    resetDraft();
    draft.requestId = crypto.randomUUID();
    draft.name = '';
    draft.format = 'JSON';
    draft.categoryId = '';
    contentText.value = '{}';
    complete.value = true;
    editing.value = true;
    editorVisible.value = true;
  }
  async function open(row: DataAsset, edit: boolean, versionId?: string) {
    if (!active) return;
    error.value = '';
    const token = ++detailRequest;
    editorVisible.value = true;
    detailLoading.value = true;
    complete.value = false;
    editing.value = edit;
    contentText.value = '';
    try {
      const asset = await getDataAsset(row.id);
      if (!active || token !== detailRequest) return;
      if (edit && versionId && versionId !== asset.currentVersionId)
        throw new Error('Historical editing is not supported');
      const fixedVersionId = edit ? asset.currentVersionId : versionId || asset.currentVersionId;
      if (!fixedVersionId) throw new Error('Immutable version is missing');
      const content = await getDataContent(row.id, fixedVersionId);
      if (!active || token !== detailRequest) return;
      resetDraft();
      Object.assign(draft, asset, { expectedRevision: asset.revision });
      contentText.value =
        typeof content.content === 'string' ? content.content : JSON.stringify(content.content, null, 2);
      complete.value = content.complete;
      if (!content.complete) error.value = '内容不完整，禁止保存以避免截断。';
      if (!edit && !route.params.dataId)
        await router.push({
          name: 'testAssetDataDetail',
          params: { dataId: row.id },
          ...(versionId ? { query: { versionId } } : {}),
        });
    } catch (reason) {
      if (token === detailRequest) fail(reason);
    } finally {
      if (token === detailRequest) detailLoading.value = false;
    }
  }
  async function save() {
    if (!active) return;
    const organization = app.currentOrgId;
    const editorToken = detailRequest;
    if (!complete.value || detailLoading.value || busy.value) return;
    if (!draft.name?.trim()) {
      error.value = '请输入名称';
      return;
    }
    busy.value = true;
    try {
      const content = draft.format === 'TXT' ? contentText.value : JSON.parse(contentText.value);
      await saveDataAsset({ ...draft, content });
      if (!active || organization !== app.currentOrgId || editorToken !== detailRequest) return;
      const next = queue.value.shift();
      if (next) await open(next, true);
      else editorVisible.value = false;
      await Promise.all([load(), loadCategories()]);
    } catch (reason) {
      fail(reason);
    } finally {
      busy.value = false;
    }
  }
  async function pollParse() {
    if (!active) return;
    const organization = app.currentOrgId;
    const id = parseJob.value;
    if (!id) return;
    try {
      const job = await getDataJob(id);
      if (!active || organization !== app.currentOrgId || id !== parseJob.value) return;
      parseStatus.value = job.status;
      if (['READY', 'SUCCESS', 'CANCELLED'].includes(job.status)) sessionStorage.removeItem('dataAssetParseJobId');
      if (job.status === 'PENDING' || job.status === 'RUNNING') parseTimer = setTimeout(pollParse, 2000);
      else {
        await Promise.all([load(), loadCategories()]);
      }
    } catch (reason) {
      if (!active || organization !== app.currentOrgId || id !== parseJob.value) return;
      if (reason instanceof AppError && (reason.status === 404 || reason.category === 'not-found')) {
        parseJob.value = '';
        parseStatus.value = '';
        sessionStorage.removeItem('dataAssetParseJobId');
        return;
      }
      fail(reason);
    }
  }
  async function retryParse() {
    if (!active) return;
    try {
      await retryDataJob(parseJob.value);
      await pollParse();
    } catch (reason) {
      fail(reason);
    }
  }
  async function upload(event: Event) {
    if (!active) return;
    const organization = app.currentOrgId;
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (limits.value && file.size > limits.value.maxBytes) {
      error.value = '文件超过允许大小，请选择较小的文件。';
      input.value = '';
      return;
    }
    busy.value = true;
    try {
      const fileKey = `${file.name}:${file.size}:${file.lastModified}`;
      const requestId = uploadRequests.get(fileKey) || crypto.randomUUID();
      uploadRequests.set(fileKey, requestId);
      const result = await uploadDataAsset(
        file,
        categoryId.value.startsWith('__') ? undefined : categoryId.value,
        requestId
      );
      if (!active || organization !== app.currentOrgId) return;
      if (result.jobId) {
        parseJob.value = result.jobId;
        sessionStorage.setItem('dataAssetParseJobId', result.jobId);
        await pollParse();
      }
      await Promise.all([load(), loadCategories()]);
    } catch (reason) {
      fail(reason);
    } finally {
      busy.value = false;
      input.value = '';
    }
  }
  async function remove(row: DataAsset) {
    if (!active) return;
    try {
      await deleteDataAsset(row);
      await Promise.all([load(), loadCategories()]);
    } catch (reason) {
      fail(reason);
    }
  }
  function chosen() {
    return rows.value.filter((row) => selected.value.includes(row.id));
  }
  function startQueue() {
    queue.value = chosen();
    const first = queue.value.shift();
    if (first) open(first, true);
  }
  async function batch(items: DataEdit[]) {
    if (!active) return;
    const organization = app.currentOrgId;
    try {
      const result = await batchDataAssets(items);
      if (!active || organization !== app.currentOrgId) return false;
      results.value = result.items;
      if (result.id) {
        jobId.value = result.id;
        sessionStorage.setItem('dataAssetBatchId', result.id);
      }
      await Promise.all([load(), loadCategories()]);
      return true;
    } catch (reason) {
      fail(reason);
      return false;
    }
  }
  async function retryFailed() {
    if (!active) return;
    try {
      results.value = (await retryDataJob(jobId.value)).items || [];
      await Promise.all([load(), loadCategories()]);
    } catch (reason) {
      fail(reason);
    }
  }
  function batchDelete() {
    return batch(chosen().map((row) => ({ id: row.id, expectedRevision: row.revision, delete: true })));
  }
  function applyCategory() {
    if (categoryMode.value === 'keep' && descriptionMode.value === 'keep') {
      error.value = '请选择至少一个修改字段';
      return false;
    }
    if (categoryMode.value === 'set' && !batchCategory.value) {
      error.value = '请选择分类';
      return false;
    }
    const changes = {
      ...(categoryMode.value === 'keep'
        ? {}
        : { categoryId: categoryMode.value === 'clear' ? '' : batchCategory.value }),
      ...(descriptionMode.value === 'keep'
        ? {}
        : { description: descriptionMode.value === 'clear' ? '' : batchDescription.value }),
    };
    return batch(chosen().map((row) => ({ id: row.id, expectedRevision: row.revision, ...changes })));
  }
  async function showVersions(row: DataAsset) {
    if (!active) return;
    const organization = app.currentOrgId;
    try {
      const value = await dataVersions(row.id);
      if (!active || organization !== app.currentOrgId) return;
      versions.value = value;
      versionAsset.value = row;
      versionsVisible.value = true;
    } catch (reason) {
      fail(reason);
    }
  }
  function readVersion(id: string) {
    if (versionAsset.value) {
      versionsVisible.value = false;
      open(versionAsset.value, false, id);
    }
  }
  async function publishSelected() {
    if (!active) return;
    const row = publishTarget.value;
    if (categoryTrace.value) {
      if (!row || !executionProject.value.trim()) {
        error.value = '请选择项目';
        return false;
      }
      router.push({
        path: '/test-assets/relations',
        query: { projectId: executionProject.value.trim(), assetType: 'DATASET', assetId: row.id },
      });
      return true;
    }
    if (!row?.currentVersionId || !executionProject.value.trim()) {
      error.value = '请选择执行项目及可用版本';
      return false;
    }
    const organization = app.currentOrgId;
    try {
      const published = await publishDataAsset(row.id, executionProject.value.trim(), row.currentVersionId);
      if (!active || organization !== app.currentOrgId) return false;
      if (taskAfterPublish.value)
        await router.push({
          name: 'executionTasks',
          query: {
            creating: '1',
            projectId: executionProject.value.trim(),
            assetType: 'DATASET',
            assetId: row.id,
            assetName: row.name,
            assetVersionId: published.id,
          },
        });
      return true;
    } catch (reason) {
      fail(reason);
      return false;
    }
  }
  function moreAction(key: string, row: DataAsset) {
    error.value = '';
    if (key === 'trace') {
      categoryTrace.value = true;
      publishTarget.value = row;
      executionProject.value = '';
      publishVisible.value = true;
    } else if (key === 'publish' || key === 'task') {
      categoryTrace.value = false;
      publishTarget.value = row;
      executionProject.value = '';
      taskAfterPublish.value = key === 'task';
      publishVisible.value = true;
    } else if (key === 'file') download(row);
    else showVersions(row);
  }
  watch(
    () => app.currentOrgId,
    () => {
      ++migrationGeneration;
      clearTimeout(migrationTimer);
      migrationBusy.value = false;
      migration.value = undefined;
      migrationError.value = '';
      migrate();
      categoryVisible.value = false;
      quickCategoryVisible.value = false;
      quickCategoryName.value = '';
      deleteVisible.value = false;
      batchVisible.value = false;
      batchCategory.value = '';
      batchDescription.value = '';
      categoryMode.value = 'keep';
      descriptionMode.value = 'keep';
      clearTimeout(parseTimer);
      parseJob.value = '';
      parseStatus.value = '';
      sessionStorage.removeItem('dataAssetParseJobId');
      ++request;
      cancelEditor();
      resetDraft();
      contentText.value = '';
      editorVisible.value = false;
      publishVisible.value = false;
      publishTarget.value = undefined;
      executionProject.value = '';
      rows.value = [];
      versions.value = [];
      versionsVisible.value = false;
      versionAsset.value = undefined;
      categories.value = [];
      results.value = [];
      selected.value = [];
      jobId.value = '';
      sessionStorage.removeItem('dataAssetBatchId');
      load();
      loadCategories();
    }
  );
  onMounted(async () => {
    migrate();
    try {
      const dataLimits = await getDataLimits();
      if (!active) return;
      limits.value = dataLimits;
    } catch (reason) {
      fail(reason);
    }
    if (!active) return;
    await Promise.all([load(), loadCategories()]);
    if (!active) return;
    const previousParse = sessionStorage.getItem('dataAssetParseJobId');
    if (previousParse) {
      parseJob.value = previousParse;
      await pollParse();
      if (!active) return;
    }
    const previousJob = sessionStorage.getItem('dataAssetBatchId');
    if (previousJob) {
      try {
        const job = await getDataJob(previousJob);
        if (!active) return;
        jobId.value = job.id;
        results.value = job.items || [];
      } catch {
        if (!active) return;
        sessionStorage.removeItem('dataAssetBatchId');
      }
    }
    const id = route.params.dataId || route.query.dataId;
    if (typeof id === 'string') await open({ id } as DataAsset, false);
  });
</script>

<style scoped>
  .dialog-error {
    position: sticky;
    top: 0;
    z-index: 1;
  }
  .datasets {
    padding: 20px;
    min-width: 0;
    max-width: 100%;
  }
  .category-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--color-border-2);
  }
  .dataset-toolbar {
    margin-bottom: 12px;
  }
  .data-layout {
    display: flex;
    gap: 20px;
    margin-top: 16px;
  }
  aside {
    flex: 0 1 230px;
    min-width: 150px;
  }
  main {
    flex: 1;
    min-width: 0;
  }
  .list-toolbar {
    max-inline-size: 100%;
  }
  .table-region :deep(.arco-table-element) {
    min-width: 1400px;
  }
  .table-region :deep(.arco-table-th),
  .table-region :deep(.arco-table-td) {
    white-space: nowrap;
  }
  .table-region {
    overflow: auto;
    max-inline-size: 100%;
    margin-top: 16px;
  }
</style>
