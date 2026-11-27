<template>
  <section class="data-detail">
    <a-space
      ><a-button type="primary" @click="router.push('/test-assets/datasets')">&lt;返回列表</a-button
      ><span class="name-divider" aria-hidden="true">|</span><h2>{{ asset?.name || '测试数据详情' }}</h2></a-space
    ><a-alert v-if="error" type="error">{{ error }}</a-alert>
    <a-spin :loading="loading" style="width: 100%"
      ><a-tabs default-active-key="content"
        ><a-tab-pane key="content" title="数据内容"
          ><DataRecordContent
            v-if="asset && fixedVersion"
            :key="`${asset.id}:${fixedVersion}`"
            :asset-id="asset.id"
            :version-id="fixedVersion" /></a-tab-pane
        ><a-tab-pane key="info" title="基本信息"
          ><h3>当前资产基本信息</h3><p>格式：{{ asset?.format }} 修订：{{ asset?.revision }}</p
          ><p>解析状态：{{ asset?.parseStatus }}</p
          ><a-button v-if="asset?.parseStatus === 'FAILED' && asset?.parseJobId" @click="retry">重试解析</a-button
          ><p>说明：{{ asset?.description || '无' }}</p
          ><p>负责人：{{ asset?.owner || '未记录' }}；分类：{{ asset?.categoryId || '默认分类' }}</p
          ><p>当前版本：{{ asset?.currentVersionId }}</p
          ><a-select
            v-model="selectedVersion"
            :options="versions.map((v) => ({ label: `v${v.versionNo}`, value: v.id }))"
            placeholder="当前版本"
            allow-clear
            @change="load" /><h3>授权可见来源</h3><p v-if="!asset?.sources?.length">无可见来源记录</p
          ><p v-for="source in asset?.sources" :key="source.sourceFileId"
            >{{ source.sourceProjectName }} · {{ source.sourceFileId }} · {{ source.versionId }}</p
          ><h3>授权可见项目引用</h3><p v-if="!asset?.projectReferences?.length">无可见引用记录</p
          ><a-button
            v-for="reference in asset?.projectReferences"
            :key="reference.projectId"
            @click="
              router.push({
                path: '/test-assets/relations',
                query: { projectId: reference.projectId, assetType: 'DATASET', assetId: asset?.id },
              })
            "
            >{{ reference.projectName }}（{{ reference.referenceCount }}）</a-button
          ><DataVersionMetadata :snapshot="snapshot" /></a-tab-pane
      ></a-tabs>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';

  import DataRecordContent from './components/DataRecordContent.vue';
  import DataVersionMetadata from './components/DataVersionMetadata.vue';

  import {
    type DataAsset,
    type DataVersionMetadata as VersionSnapshot,
    dataVersions,
    getDataAsset,
    retryDataJob,
  } from '@/api/modules/test-data-assets';
  import useAppStore from '@/store/modules/app';
  import businessAssetError from '@/utils/businessAssetError';

  const route = useRoute();
  const router = useRouter();
  const app = useAppStore();
  const versions = ref<{ id: string; versionNo?: number }[]>([]);
  const selectedVersion = ref('');
  const fixedVersion = ref('');
  const asset = ref<DataAsset>();
  const snapshot = ref<VersionSnapshot>();
  const loading = ref(false);
  const error = ref('');
  let generation = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  async function load() {
    clearTimeout(timer);
    const token = ++generation;
    const organization = app.currentOrgId;
    versions.value = [];
    asset.value = undefined;
    snapshot.value = undefined;
    fixedVersion.value = '';
    loading.value = true;
    error.value = '';
    try {
      const id = String(route.params.dataId);
      const detail = await getDataAsset(id);
      if (token !== generation || organization !== app.currentOrgId) return;
      asset.value = detail;
      const versionRows = await dataVersions(id);
      if (token !== generation || organization !== app.currentOrgId) return;
      versions.value = versionRows;
      if (
        detail.parseStatus === 'FAILED' &&
        (!selectedVersion.value || selectedVersion.value === detail.currentVersionId)
      ) {
        error.value = '文件解析失败，可重试解析。';
        return;
      }
      if (
        detail.parseStatus &&
        !['READY', 'SUCCESS'].includes(detail.parseStatus) &&
        (!selectedVersion.value || selectedVersion.value === detail.currentVersionId)
      ) {
        timer = setTimeout(load, 2000);
        return;
      }
      fixedVersion.value = selectedVersion.value || detail.currentVersionId || '';
      snapshot.value = versionRows.find((version) => version.id === fixedVersion.value) || { metadataAvailable: false };
    } catch (reason) {
      if (token === generation)
        error.value = businessAssetError(reason, '无法读取测试数据，请检查权限、网络或数据是否已被删除。');
    } finally {
      if (token === generation) loading.value = false;
    }
  }
  async function retry() {
    if (!asset.value?.parseJobId) return;
    try {
      await retryDataJob(asset.value.parseJobId);
      await load();
    } catch (reason) {
      error.value = businessAssetError(reason, '解析重试失败，请稍后重试。');
    }
  }
  watch(
    [() => app.currentOrgId, () => route.params.dataId, () => route.query.versionId],
    (values, previous) => {
      selectedVersion.value =
        (!previous ||
          previous[0] === undefined ||
          (values[0] === previous[0] && (values[1] === previous[1] || values[2] !== previous[2]))) &&
        typeof route.query.versionId === 'string'
          ? route.query.versionId
          : '';
      load();
    },
    { immediate: true }
  );
  onBeforeUnmount(() => {
    ++generation;
    clearTimeout(timer);
  });
</script>

<style scoped>
  .data-detail {
    margin: 16px;
    padding: 24px;
    border-radius: 8px;
    background: var(--color-bg-2);
    overflow-wrap: anywhere;
  }
  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
  }
  h3 {
    margin: 24px 0 12px;
    font-size: 16px;
    font-weight: 600;
  }
  p {
    margin: 8px 0;
    line-height: 1.6;
  }
  :deep(.arco-select) {
    max-inline-size: 360px;
    margin-block: 12px;
  }
  pre {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }
</style>
