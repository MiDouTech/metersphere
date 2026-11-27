<template>
  <section class="datasets">
    <a-space
      ><a-button
        type="primary"
        :loading="busy"
        :disabled="!canWrite || parseStatus === 'PENDING' || parseStatus === 'RUNNING'"
        @click="fileInput?.click()"
        >上传测试数据</a-button
      ><a-button :disabled="!canWrite" @click="create">人工新建</a-button><a-tag>全局资产</a-tag
      ><a-button @click="legacyVisible = !legacyVisible">历史项目数据</a-button></a-space
    >
    <a-button
      :disabled="!canWrite"
      @click="
        error = '';
        importVisible = true;
      "
      >导入历史项目文件</a-button
    >
    <p v-if="limits"
      >支持 {{ limits.formats.join('、') }}，最大 {{ limits.maxBytes }} 字节、{{ limits.maxRows }} 行。</p
    >
    <input ref="fileInput" type="file" hidden accept=".json,.txt,.csv,.xlsx,.xls" @change="upload" />
    <a-alert v-if="parseStatus" :type="parseStatus === 'FAILED' ? 'error' : 'info'"
      >上传解析：{{ parseLabel(parseStatus)
      }}<a-button v-if="parseStatus === 'FAILED'" @click="retryParse">重试解析</a-button></a-alert
    ><a-alert v-if="error" type="error">{{ error }}</a-alert> <Catalog v-if="legacyVisible" /><div
      v-else
      class="data-layout"
    >
      <aside aria-label="数据分类"
        ><a-button long @click="filterCategory('')">全部分类</a-button
        ><a-button long @click="filterCategory('__uncategorized__')">未分类</a-button
        ><a-tree
          :data="categories"
          :field-names="{ key: 'id', title: 'name' }"
          @select="(keys) => filterCategory(String(keys[0] || ''))"
        /><a-button @click="categoryVisible = true">管理分类</a-button></aside
      >
      <main>
        <a-space
          ><a-input-search v-model="keyword" placeholder="搜索测试数据" @search="search" /><a-button
            :disabled="!canWrite || !selected.length"
            @click="
              error = '';
              batchVisible = true;
            "
            >批量修改</a-button
          ><a-button :disabled="!canWrite || !selected.length" @click="startQueue">逐项修改内容</a-button
          ><a-popconfirm content="确认删除所选数据？引用中的数据将被拒绝。" @ok="batchDelete"
            ><a-button status="danger" :disabled="!canWrite || !selected.length">批量删除</a-button></a-popconfirm
          ></a-space
        >
        <div class="table-region" role="region" aria-label="测试数据列表" tabindex="0">
          <a-table
            v-model:selected-keys="selected"
            :data="rows"
            row-key="id"
            :loading="loading"
            :row-selection="{ type: 'checkbox', showCheckedAll: true }"
            :pagination="{ current: page, pageSize: 20, total }"
            @page-change="changePage"
          >
            <template #columns
              ><a-table-column title="名称"
                ><template #cell="{ record }"
                  ><a-link @click="open(record, false)">{{ record.name }}</a-link></template
                ></a-table-column
              ><a-table-column title="说明" data-index="description" :width="180" /><a-table-column title="分类"
                ><template #cell="{ record }">{{ categoryLabel(record.categoryId) }}</template></a-table-column
              ><a-table-column title="负责人" data-index="owner" /><a-table-column title="更新时间"
                ><template #cell="{ record }">{{
                  record.updateTime ? new Date(record.updateTime).toLocaleString() : '未记录'
                }}</template></a-table-column
              ><a-table-column title="格式" data-index="format" /><a-table-column
                title="解析状态"
                data-index="parseStatus"
              /><a-table-column title="修订" data-index="revision" /><a-table-column title="操作" :width="270"
                ><template #cell="{ record }"
                  ><a-space
                    ><a-link @click="open(record, false)">详情</a-link
                    ><a-link :disabled="!canWrite" @click="open(record, true)">修改</a-link
                    ><a-popconfirm content="确认删除？" @ok="remove(record)"
                      ><a-link status="danger" :disabled="!canWrite">删除</a-link></a-popconfirm
                    ><a-dropdown @select="(key) => moreAction(String(key), record)"
                      ><a-button aria-label="更多操作">…</a-button
                      ><template #content
                        ><a-doption value="publish">发布到项目</a-doption><a-doption value="task">用于任务</a-doption
                        ><a-doption value="trace">项目引用追溯</a-doption><a-doption value="source">来源治理</a-doption
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
    <a-modal v-model:visible="importVisible" title="导入历史项目文件" :on-before-ok="importLegacy"
      ><a-alert v-if="error" data-testid="data-import-error" class="dialog-error" type="error">{{ error }}</a-alert
      ><a-form :model="{}" layout="vertical"
        ><a-form-item label="来源项目 ID"><a-input v-model="importProject" /></a-form-item
        ><a-form-item label="来源文件 ID"><a-input v-model="importFileId" /></a-form-item
        ><p>请从历史项目数据中确认来源项目及文件；将校验该项目权限并保留来源映射。</p></a-form
      ></a-modal
    ><a-modal
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

  import Catalog from './catalog.vue';
  import DataTableEditor from './components/DataTableEditor.vue';
  import TestAssetCategoryDrawer from './components/TestAssetCategoryDrawer.vue';

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
    importLegacyData,
    listDataAssets,
    listDataCategories,
    publishDataAsset,
    retryDataJob,
    saveDataAsset,
    uploadDataAsset,
  } from '@/api/modules/test-data-assets';
  import useAppStore from '@/store/modules/app';
  import { AppError } from '@/utils/appError';
  import businessAssetError from '@/utils/businessAssetError';
  import { hasAnyPermission } from '@/utils/permission';

  const canWrite = computed(() => hasAnyPermission(['TEST_DATA_ASSET:READ+UPDATE'], ['ORGANIZATION', 'SYSTEM']));
  const publishVisible = ref(false);
  const executionProject = ref('');
  const publishTarget = ref<DataAsset>();
  const taskAfterPublish = ref(false);
  const importVisible = ref(false);
  const importProject = ref('');
  const importFileId = ref('');
  const legacyVisible = ref(false);
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
  const batchVisible = ref(false);
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
  onBeforeUnmount(() => clearTimeout(parseTimer));
  const results = ref<{ id: string; status: string; error?: string }[]>([]);
  const versionsVisible = ref(false);
  const versions = ref<{ id: string; versionNo?: number }[]>([]);
  const versionAsset = ref<DataAsset>();
  let request = 0;
  let detailRequest = 0;
  function categoryLabel(id?: string): string {
    if (!id) return '未分类';
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
    error.value = businessAssetError(reason, '操作失败，请检查权限、网络和数据版本；并发修改时请重新打开详情。');
  }
  async function load() {
    const token = ++request;
    loading.value = true;
    try {
      const data = await listDataAssets({
        keyword: keyword.value,
        categoryId: categoryId.value || undefined,
        page: page.value,
        pageSize: 20,
      });
      if (token === request) {
        rows.value = data.items;
        total.value = data.total;
        selected.value = [];
      }
    } catch (reason) {
      fail(reason);
    } finally {
      if (token === request) loading.value = false;
    }
  }
  async function loadCategories() {
    const organization = app.currentOrgId;
    try {
      const flat = await listDataCategories();
      if (organization !== app.currentOrgId) return;
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
      fail(reason);
    }
  }
  async function importLegacy() {
    if (!importProject.value.trim() || !importFileId.value.trim()) {
      error.value = '请填写来源项目和文件 ID';
      return false;
    }
    try {
      await importLegacyData(importProject.value.trim(), importFileId.value.trim());
      await Promise.all([load(), loadCategories()]);
      return true;
    } catch (reason) {
      fail(reason);
      return false;
    }
  }
  async function download(row: DataAsset) {
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
    error.value = '';
    const token = ++detailRequest;
    editorVisible.value = true;
    detailLoading.value = true;
    complete.value = false;
    editing.value = edit;
    contentText.value = '';
    try {
      const asset = await getDataAsset(row.id);
      if (token !== detailRequest) return;
      if (edit && versionId && versionId !== asset.currentVersionId)
        throw new Error('Historical editing is not supported');
      const fixedVersionId = edit ? asset.currentVersionId : versionId || asset.currentVersionId;
      if (!fixedVersionId) throw new Error('Immutable version is missing');
      const content = await getDataContent(row.id, fixedVersionId);
      if (token !== detailRequest) return;
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
      if (organization !== app.currentOrgId || editorToken !== detailRequest) return;
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
    const organization = app.currentOrgId;
    const id = parseJob.value;
    if (!id) return;
    try {
      const job = await getDataJob(id);
      if (organization !== app.currentOrgId || id !== parseJob.value) return;
      parseStatus.value = job.status;
      if (['READY', 'SUCCESS', 'CANCELLED'].includes(job.status)) sessionStorage.removeItem('dataAssetParseJobId');
      if (job.status === 'PENDING' || job.status === 'RUNNING') parseTimer = setTimeout(pollParse, 2000);
      else {
        await Promise.all([load(), loadCategories()]);
      }
    } catch (reason) {
      if (organization !== app.currentOrgId || id !== parseJob.value) return;
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
    try {
      await retryDataJob(parseJob.value);
      await pollParse();
    } catch (reason) {
      fail(reason);
    }
  }
  async function upload(event: Event) {
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
      if (organization !== app.currentOrgId) return;
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
    const organization = app.currentOrgId;
    try {
      const result = await batchDataAssets(items);
      if (organization !== app.currentOrgId) return false;
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
    const organization = app.currentOrgId;
    try {
      const value = await dataVersions(row.id);
      if (organization !== app.currentOrgId) return;
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
      if (organization !== app.currentOrgId) return false;
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
    } else if (key === 'source') {
      legacyVisible.value = true;
    } else if (key === 'file') download(row);
    else showVersions(row);
  }
  watch(
    () => app.currentOrgId,
    () => {
      categoryVisible.value = false;
      batchVisible.value = false;
      importVisible.value = false;
      legacyVisible.value = false;
      batchCategory.value = '';
      batchDescription.value = '';
      categoryMode.value = 'keep';
      descriptionMode.value = 'keep';
      importProject.value = '';
      importFileId.value = '';
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
    try {
      limits.value = await getDataLimits();
    } catch (reason) {
      fail(reason);
    }
    await Promise.all([load(), loadCategories()]);
    const previousParse = sessionStorage.getItem('dataAssetParseJobId');
    if (previousParse) {
      parseJob.value = previousParse;
      await pollParse();
    }
    const previousJob = sessionStorage.getItem('dataAssetBatchId');
    if (previousJob) {
      try {
        const job = await getDataJob(previousJob);
        jobId.value = job.id;
        results.value = job.items || [];
      } catch {
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
  }
  .data-layout {
    display: flex;
    gap: 20px;
    margin-top: 16px;
  }
  aside {
    flex: 0 0 230px;
  }
  main {
    flex: 1;
    min-width: 0;
  }
  .table-region {
    overflow: auto;
    max-inline-size: 100%;
    margin-top: 16px;
  }
</style>
