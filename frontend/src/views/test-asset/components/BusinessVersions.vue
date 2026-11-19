<template>
  <section aria-labelledby="business-versions-title" class="mb-6">
    <h2 id="business-versions-title">业务资产版本</h2>
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
    <p v-if="error" role="alert">{{ error }}</p>
    <label for="version-asset">业务资产</label>
    <select id="version-asset" v-model="assetId" @change="loadDetail">
      <option value="">请选择业务资产</option>
      <option v-for="asset in assets" :key="asset.id" :value="asset.id">{{ asset.title }}</option>
    </select>
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
  const canWrite = computed(() => hasAnyPermission(['BUSINESS_ASSET:WRITE']));
  const current = computed(() => detail.value?.versions.find((v) => v.id === detail.value?.currentVersionId));
  let generation = 0;
  async function loadDetail() {
    const run = ++generation;
    detail.value = undefined;
    error.value = '';
    if (!assetId.value) return;
    loading.value = true;
    try {
      const value = await getBusinessAsset(app.currentProjectId, assetId.value);
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
    try {
      const result = await MSR.post<{ assetId: string }>({
        url: `/business-assets/legacy/${encodeURIComponent(id)}/import`,
        data: { projectId: app.currentProjectId },
      });
      const active = await listBusinessAssets(app.currentProjectId);
      assets.value = active;
      assetId.value = result.assetId;
      legacy.value = legacy.value.filter((item) => item.id !== id);
      await loadDetail();
    } catch (failure) {
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
      await selectBusinessVersion(
        assetId.value,
        app.currentProjectId,
        target.value.id,
        detail.value.revision,
        reason.value
      );
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
  watch(
    [() => app.currentProjectId, () => route.query.businessAssetId],
    async () => {
      ++generation;
      detail.value = undefined;
      selected.value = undefined;
      switchVisible.value = false;
      assets.value = [];
      legacy.value = [];
      if (!app.currentProjectId || !hasAnyPermission(['BUSINESS_ASSET:READ'])) return;
      const project = app.currentProjectId;
      try {
        const [active, archived] = await Promise.all([
          listBusinessAssets(project),
          listBusinessAssets(project, '', true),
        ]);
        if (project !== app.currentProjectId) return;
        assets.value = [...active, ...archived];
        const unassigned = await MSR.get<{ id: string; title: string; versionCount: number }[]>({
          url: '/business-assets/legacy-unassigned',
          params: { projectId: project },
        });
        if (project !== app.currentProjectId) return;
        legacy.value = unassigned;
        assetId.value = String(route.query.businessAssetId || '');
        await loadDetail();
      } catch (failure) {
        error.value = businessAssetError(failure, '业务资产版本目录加载失败。');
      }
    },
    { immediate: true }
  );
</script>
