<template>
  <section aria-label="业务资产版本" class="mb-6">
    <div class="asset-toolbar">
      <div class="asset-actions"><a-button :loading="loading" @click="reload">刷新</a-button></div>
      <div class="asset-search"
        ><a-select
          v-model="assetId"
          allow-search
          allow-clear
          placeholder="请选择业务资产"
          aria-label="业务资产"
          style="width: 360px"
          @change="loadDetail"
          ><a-option v-for="asset in assets" :key="asset.id" :value="asset.id">{{ asset.title }}</a-option></a-select
        ></div
      >
    </div>
    <a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert>
    <a-alert v-if="legacyError" type="warning" class="mb-4"
      >{{ legacyError }} <a-link @click="loadLegacy">重试历史目录</a-link></a-alert
    >
    <section v-if="legacy.length" aria-label="待确认归属的历史业务理解">
      <h3>待确认归属的历史业务理解</h3
      ><p>仅显示本人创建、没有项目来源的历史文档。确认归属后，全部版本进入当前项目的资产版本。</p>
      <a-list
        ><a-list-item v-for="item in legacy" :key="item.id"
          >{{ item.title }}（{{ item.versionCount }} 个版本）
          <a-popconfirm
            v-if="canWrite"
            content="将此历史业务理解归入当前项目？原始版本全部保留。"
            @ok="importLegacy(item.id)"
            ><a-link>归入当前项目</a-link></a-popconfirm
          >
        </a-list-item></a-list
      >
    </section>
    <a-alert class="mb-4">历史快照不可修改；版本切换仅影响后续任务，执行中的任务保持原版本。</a-alert>
    <a-empty
      v-if="!detail && !loading && !error"
      :description="assets.length ? '请选择资产查看版本' : '暂无业务资产版本'"
    />
    <a-table v-if="detail" :data="detail.versions" row-key="id" :loading="loading">
      <template #columns>
        <a-table-column title="版本" data-index="versionNo" />
        <a-table-column title="状态"
          ><template #cell="{ record }">{{
            record.id === detail.currentVersionId
              ? '当前生效'
              : record.status === 'CONFLICT'
              ? '待合并'
              : record.status === 'UNCLASSIFIED'
              ? '待归类'
              : '历史'
          }}</template></a-table-column
        >
        <a-table-column title="变化"
          ><template #cell="{ record }">{{ record.snapshot.changeSummary }}</template></a-table-column
        >
        <a-table-column title="操作"
          ><template #cell="{ record }"
            ><a-space>
              <a-link @click="selected = record">查看与比较</a-link>
              <a-link @click="download(record)">下载</a-link>
              <a-link
                v-if="canWrite && ['CONFLICT', 'UNCLASSIFIED'].includes(record.status)"
                @click="mergeVersion(record)"
                >整理合并为新版本</a-link
              >
              <a-link v-if="canWrite && record.status === 'HISTORY'" @click="choose(record)">设为当前版本</a-link>
            </a-space></template
          ></a-table-column
        >
      </template>
    </a-table>
    <a-modal v-model:visible="switchVisible" title="替换当前业务理解" :ok-loading="saving" @before-ok="confirmSwitch">
      <label for="version-reason">切换原因（必填）</label>
      <a-textarea id="version-reason" v-model="reason" :max-length="500" />
      <p>新任务读取所选版本；历史版本和已开始任务的引用保留。</p>
    </a-modal>
    <a-drawer :visible="!!selected" title="业务理解版本比较" :width="1000" @cancel="selected = undefined">
      <div v-if="selected" class="grid grid-cols-2 gap-4">
        <section
          ><h3>所选 v{{ selected.versionNo }}</h3
          ><pre class="whitespace-pre-wrap break-words">{{ selected.snapshot.document.content }}</pre>
        </section>
        <section
          ><h3>当前版本</h3><pre class="whitespace-pre-wrap break-words">{{ current?.snapshot.document.content }}</pre>
        </section>
      </div>
    </a-drawer>
  </section>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';

  import MSR from '@/api/http/index';
  import type { BusinessDetail, BusinessRow, BusinessVersion } from '@/api/modules/business-assets';
  import { getBusinessAsset, listBusinessAssets, selectBusinessVersion } from '@/api/modules/business-assets';
  import useAppStore from '@/store/modules/app';
  import businessAssetError from '@/utils/businessAssetError';
  import { hasAnyPermission } from '@/utils/permission';

  const globalScope = '';
  const app = useAppStore();
  const route = useRoute();
  const router = useRouter();
  const assets = ref<BusinessRow[]>([]);
  const legacy = ref<{ id: string; title: string; versionCount: number }[]>([]);
  const assetId = ref('');
  const detail = ref<BusinessDetail>();
  const selected = ref<BusinessVersion>();
  const target = ref<BusinessVersion>();
  const reason = ref('');
  const switchVisible = ref(false);
  const loading = ref(false);
  const saving = ref(false);
  const error = ref('');
  const legacyError = ref('');
  const canWrite = computed(() => hasAnyPermission(['BUSINESS_ASSET:WRITE'], ['ORGANIZATION', 'SYSTEM']));
  const current = computed(() => detail.value?.versions.find((v) => v.id === detail.value?.currentVersionId));
  let generation = 0;
  let catalogGeneration = 0;
  let legacyGeneration = 0;
  async function loadDetail() {
    const run = ++generation;
    detail.value = undefined;
    error.value = '';
    if (!assetId.value) {
      loading.value = false;
      return;
    }
    loading.value = true;
    try {
      const value = await getBusinessAsset(globalScope, assetId.value);
      if (run === generation) detail.value = value;
    } catch (failure) {
      if (run === generation) error.value = businessAssetError(failure, '版本读取失败，请检查权限或网络。');
    } finally {
      if (run === generation) loading.value = false;
    }
  }
  function choose(version: BusinessVersion) {
    target.value = version;
    reason.value = '';
    switchVisible.value = true;
  }
  async function importLegacy(id: string) {
    const project = app.currentOrgId;
    const run = ++generation;
    try {
      const result = await MSR.post<{ assetId: string }>({
        url: `/business-assets/legacy/${encodeURIComponent(id)}/import`,
        data: { projectId: globalScope },
      });
      if (project !== app.currentOrgId || run !== generation) return;
      const active = await listBusinessAssets(globalScope);
      if (project !== app.currentOrgId || run !== generation) return;
      assets.value = active;
      assetId.value = result.assetId;
      legacy.value = legacy.value.filter((item) => item.id !== id);
      await loadDetail();
    } catch (failure) {
      if (project === app.currentOrgId && run === generation)
        error.value = businessAssetError(failure, '历史业务理解导入失败，请检查项目权限后重试。');
    }
  }
  function mergeVersion(version: BusinessVersion) {
    router.push({ path: '/test-assets/business', query: { assetId: assetId.value, versionId: version.id } });
  }
  async function confirmSwitch() {
    if (!reason.value.trim() || !target.value || !detail.value) {
      error.value = '请填写版本切换原因。';
      return false;
    }
    saving.value = true;
    try {
      await selectBusinessVersion(assetId.value, globalScope, target.value.id, detail.value.revision, reason.value);
      await loadDetail();
      return true;
    } catch (failure) {
      error.value = businessAssetError(failure, '切换失败，资产可能已变化，请刷新后重试。');
      return false;
    } finally {
      saving.value = false;
    }
  }
  function download(version: BusinessVersion) {
    const url = URL.createObjectURL(
      new Blob([version.snapshot.document.content], { type: 'text/markdown;charset=utf-8' })
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = `business-${assetId.value}-v${version.versionNo}.md`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  async function loadLegacy() {
    const project = app.currentOrgId;
    const run = ++legacyGeneration;
    legacyError.value = '';
    try {
      const value = await MSR.get<{ id: string; title: string; versionCount: number }[]>({
        url: '/business-assets/legacy-unassigned',
        params: { projectId: globalScope },
      });
      if (run === legacyGeneration && project === app.currentOrgId) legacy.value = value;
    } catch (failure) {
      if (run === legacyGeneration && project === app.currentOrgId)
        legacyError.value = businessAssetError(failure, '历史归属目录加载失败，不影响当前业务资产版本查看。');
    }
  }
  async function reload() {
    const run = ++catalogGeneration;
    const project = app.currentOrgId;
    error.value = '';
    loading.value = true;
    try {
      const [active, archived] = await Promise.all([
        listBusinessAssets(globalScope),
        listBusinessAssets(globalScope, '', true),
      ]);
      if (run !== catalogGeneration || project !== app.currentOrgId) return;
      assets.value = [...active, ...archived];
      if (!assets.value.some((asset) => asset.id === assetId.value)) assetId.value = '';
      await loadDetail();
    } catch (failure) {
      if (run === catalogGeneration) error.value = businessAssetError(failure, '业务资产版本目录加载失败，请重试。');
    } finally {
      if (run === catalogGeneration && project === app.currentOrgId) loading.value = false;
    }
  }
  watch(
    [() => app.currentOrgId, () => route.query.businessAssetId],
    async () => {
      ++generation;
      ++catalogGeneration;
      ++legacyGeneration;
      detail.value = undefined;
      selected.value = undefined;
      switchVisible.value = false;
      assets.value = [];
      legacy.value = [];
      legacyError.value = '';
      assetId.value = String(route.query.businessAssetId || '');
      if (!hasAnyPermission(['BUSINESS_ASSET:READ'], ['ORGANIZATION', 'SYSTEM'])) return;
      await Promise.all([reload(), loadLegacy()]);
    },
    { immediate: true }
  );
</script>
