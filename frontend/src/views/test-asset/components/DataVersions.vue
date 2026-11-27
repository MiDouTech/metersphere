<template>
  <section aria-label="测试数据版本">
    <a-alert v-if="error" type="error">{{ error }}</a-alert>
    <a-input-search v-model="keyword" placeholder="搜索测试数据" @search="search" />
    <a-spin :loading="listLoading"
      ><ul
        ><li v-for="asset in assets" :key="asset.id"
          ><a-button
            :data-asset="asset.id"
            :type="asset.id === selected ? 'primary' : 'secondary'"
            @click="selected = asset.id"
            >{{ asset.name }}</a-button
          ></li
        ></ul
      ><a-empty v-if="!listLoading && !assets.length" description="暂无测试数据"
    /></a-spin>
    <a-pagination v-model:current="page" :page-size="20" :total="total" @change="loadAssets" />
    <a-spin :loading="versionLoading"
      ><p v-if="!selected">请选择测试数据查看历史版本。</p
      ><a-empty v-else-if="!versionLoading && !versions.length" description="暂无历史版本" /><article
        v-for="version in versions"
        :key="version.id"
        ><h3>v{{ version.versionNo }}</h3
        ><DataVersionMetadata :snapshot="version" /><a-button :data-version="version.id" @click="view(version.id)"
          >查看固定版本内容</a-button
        ></article
      ></a-spin
    >
  </section>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue';
  import { useRouter } from 'vue-router';

  import DataVersionMetadata from './DataVersionMetadata.vue';

  import { type DataAsset, type DataVersion, dataVersions, listDataAssets } from '@/api/modules/test-data-assets';
  import useAppStore from '@/store/modules/app';
  import businessAssetError from '@/utils/businessAssetError';

  const app = useAppStore();
  const router = useRouter();
  const assets = ref<DataAsset[]>([]);
  const versions = ref<DataVersion[]>([]);
  const selected = ref('');
  const keyword = ref('');
  const page = ref(1);
  const total = ref(0);
  const error = ref('');
  const listLoading = ref(false);
  const versionLoading = ref(false);
  let listGeneration = 0;
  let versionGeneration = 0;
  async function loadAssets() {
    ++versionGeneration;
    selected.value = '';
    versions.value = [];
    versionLoading.value = false;
    const run = ++listGeneration;
    const organization = app.currentOrgId;
    listLoading.value = true;
    error.value = '';
    try {
      const response = await listDataAssets({ keyword: keyword.value, page: page.value, pageSize: 20 });
      if (run !== listGeneration || organization !== app.currentOrgId) return;
      assets.value = response.items;
      total.value = response.total;
    } catch (reason) {
      if (run === listGeneration) error.value = businessAssetError(reason, '测试数据目录读取失败，请重试。');
    } finally {
      if (run === listGeneration) listLoading.value = false;
    }
  }
  async function loadVersions() {
    const run = ++versionGeneration;
    const organization = app.currentOrgId;
    const id = selected.value;
    versions.value = [];
    error.value = '';
    if (!id) {
      versionLoading.value = false;
      return;
    }
    versionLoading.value = true;
    try {
      const response = await dataVersions(id);
      if (run !== versionGeneration || organization !== app.currentOrgId || id !== selected.value) return;
      versions.value = response;
    } catch (reason) {
      if (run === versionGeneration) error.value = businessAssetError(reason, '测试数据历史版本读取失败，请重试。');
    } finally {
      if (run === versionGeneration) versionLoading.value = false;
    }
  }
  function search() {
    page.value = 1;
    loadAssets();
  }
  function view(versionId: string) {
    router.push({ name: 'testAssetDataDetail', params: { dataId: selected.value }, query: { versionId } });
  }
  watch(selected, loadVersions);
  watch(
    () => app.currentOrgId,
    () => {
      ++listGeneration;
      ++versionGeneration;
      assets.value = [];
      versions.value = [];
      error.value = '';
      selected.value = '';
      keyword.value = '';
      page.value = 1;
      total.value = 0;
      loadAssets();
    },
    { immediate: true }
  );
</script>
