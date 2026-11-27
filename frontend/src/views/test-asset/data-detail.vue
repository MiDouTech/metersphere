<template>
  <section class="data-detail">
    <a-space
      ><a-button @click="router.push('/test-assets/datasets')">返回列表</a-button
      ><h2>{{ asset?.name || '测试数据详情' }}</h2
      ><a-tag>全局资产</a-tag></a-space
    >
    <input ref="fileInput" type="file" hidden accept=".csv,.txt,.json,.xls,.xlsx" @change="replaceFile" /><a-button
      :disabled="!canReplace || !asset || !['READY', 'SUCCESS', 'FAILED'].includes(asset.parseStatus || 'READY')"
      :loading="replacing"
      @click="fileInput?.click()"
      >替换文件生成新版本</a-button
    ><a-alert v-if="error" type="error">{{ error }}</a-alert>
    <a-spin :loading="loading" style="width: 100%"
      ><h3>当前资产基本信息</h3><p>格式：{{ asset?.format }} 修订：{{ asset?.revision }}</p
      ><p>解析状态：{{ asset?.parseStatus }}</p
      ><a-button v-if="asset?.parseStatus === 'FAILED' && asset?.parseJobId" @click="retry">重试解析</a-button
      ><p>说明：{{ asset?.description || '无' }}</p
      ><p>负责人：{{ asset?.owner || '未记录' }}；分类：{{ asset?.categoryId || '未分类' }}</p
      ><p>当前版本：{{ asset?.currentVersionId }}</p
      ><a-select
        v-model="selectedVersion"
        :options="versions.map((v) => ({ label: `v${v.versionNo}`, value: v.id }))"
        placeholder="当前版本"
        allow-clear
        @change="load"
      /><h3>授权可见来源</h3><p v-if="!asset?.sources?.length">无可见来源记录</p
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
      ><DataVersionMetadata :snapshot="snapshot" /><DataContent
        :value="content"
        :format="snapshot?.metadata?.format || asset?.format || 'JSON'"
      />
    </a-spin>
  </section>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';

  import DataContent from './components/DataContent.vue';
  import DataVersionMetadata from './components/DataVersionMetadata.vue';

  import {
    type DataAsset,
    type DataVersionMetadata as VersionSnapshot,
    dataVersions,
    getDataAsset,
    getDataContent,
    replaceDataFile,
    retryDataJob,
  } from '@/api/modules/test-data-assets';
  import useAppStore from '@/store/modules/app';
  import businessAssetError from '@/utils/businessAssetError';
  import { hasAnyPermission } from '@/utils/permission';

  const route = useRoute();
  const router = useRouter();
  const app = useAppStore();
  const versions = ref<{ id: string; versionNo?: number }[]>([]);
  const selectedVersion = ref('');
  const fileInput = ref<HTMLInputElement>();
  const replacing = ref(false);
  const replaceRequests = new Map<string, string>();
  const canReplace = computed(() => hasAnyPermission(['TEST_DATA_ASSET:READ+UPDATE'], ['ORGANIZATION', 'SYSTEM']));
  const asset = ref<DataAsset>();
  const content = ref<unknown>();
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
    content.value = undefined;
    snapshot.value = undefined;
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
      const body = await getDataContent(id, selectedVersion.value || undefined);
      if (token !== generation || organization !== app.currentOrgId) return;
      snapshot.value = body;
      content.value = body.content;
      if (!body.complete) error.value = '内容未完整加载，请重试。';
    } catch (reason) {
      if (token === generation)
        error.value = businessAssetError(reason, '无法读取测试数据，请检查权限、网络或数据是否已被删除。');
    } finally {
      if (token === generation) loading.value = false;
    }
  }
  async function replaceFile(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const current = asset.value;
    if (!file || !current) return;
    const organization = app.currentOrgId;
    const targetGeneration = generation;
    const targetId = String(route.params.dataId);
    const key = `${current.id}:${current.revision}:${file.name}:${file.size}:${file.lastModified}`;
    const requestId = replaceRequests.get(key) || crypto.randomUUID();
    replaceRequests.set(key, requestId);
    replacing.value = true;
    try {
      await replaceDataFile(current.id, file, current.revision, requestId);
      if (
        organization === app.currentOrgId &&
        targetGeneration === generation &&
        targetId === String(route.params.dataId)
      ) {
        selectedVersion.value = '';
        await load();
      }
    } catch (reason) {
      if (organization === app.currentOrgId)
        error.value = businessAssetError(reason, '文件替换失败，请检查格式、权限及修订版本，原文件保持不变。');
    } finally {
      if (targetId === String(route.params.dataId)) replacing.value = false;
      input.value = '';
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
      replacing.value = false;
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
