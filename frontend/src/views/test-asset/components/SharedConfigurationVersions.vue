<template>
  <section class="mb-6" aria-labelledby="configuration-versions-title">
    <h2 id="configuration-versions-title">公共配置版本</h2>
    <p v-if="error" role="alert">{{ error }}</p>
    <label for="configuration-version-id">配置</label>
    <select id="configuration-version-id" v-model="id" @change="loadVersions"
      ><option value="">请选择公共配置</option
      ><option v-for="item in configurations" :key="item.id" :value="item.id"
        >{{ item.systemKey }} / {{ item.environmentKey }} / {{ item.name }}</option
      ></select
    >
    <a-table :data="versions" row-key="versionNo" :loading="loading"
      ><template #columns>
        <a-table-column title="版本" data-index="versionNo" /><a-table-column
          title="类型"
          data-index="kind"
        /><a-table-column title="原始文档" data-index="fileName" /><a-table-column
          title="解析状态"
          data-index="parseStatus"
        />
        <a-table-column title="操作"
          ><template #cell="{ record }"
            ><a-link v-if="canWrite && record.versionNo !== current?.currentVersion" @click="choose(record.versionNo)"
              >设为当前版本</a-link
            ><span v-else-if="record.versionNo === current?.currentVersion">当前生效</span></template
          ></a-table-column
        >
      </template></a-table
    >
    <p>历史敏感值保持脱敏。切换仅影响后续会话，已有执行器继续使用已固定版本。</p>
  </section>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';

  import MSR from '@/api/http/index';
  import useAppStore from '@/store/modules/app';
  import businessAssetError from '@/utils/businessAssetError';
  import { hasAnyPermission } from '@/utils/permission';

  interface Configuration {
    id: string;
    name: string;
    systemKey: string;
    environmentKey: string;
    currentVersion: number;
  }
  interface Version {
    versionNo: number;
    kind: string;
    fileName: string;
    parseStatus: string;
  }
  const app = useAppStore();
  const route = useRoute();
  const configurations = ref<Configuration[]>([]);
  const id = ref('');
  const versions = ref<Version[]>([]);
  const error = ref('');
  const loading = ref(false);
  const current = computed(() => configurations.value.find((item) => item.id === id.value));
  const canWrite = computed(() => hasAnyPermission(['SHARED_CONFIGURATION:WRITE']));
  let generation = 0;
  async function loadVersions() {
    const run = ++generation;
    versions.value = [];
    if (!id.value) return;
    loading.value = true;
    try {
      const value = await MSR.get<Version[]>({
        url: `/shared-configurations/${id.value}/versions`,
        params: { projectId: app.currentProjectId },
      });
      if (run === generation) versions.value = value;
    } catch (failure) {
      if (run === generation) error.value = businessAssetError(failure, '配置版本读取失败。');
    } finally {
      if (run === generation) loading.value = false;
    }
  }
  async function choose(version: number) {
    if (!current.value) return;
    try {
      await MSR.post({
        url: `/shared-configurations/${id.value}/current-version`,
        data: { projectId: app.currentProjectId, version, expectedVersion: current.value.currentVersion },
      });
      current.value.currentVersion = version;
      error.value = '';
      await loadVersions();
    } catch (failure) {
      error.value = businessAssetError(failure, '配置版本切换失败，请刷新后重试。');
    }
  }
  watch(
    [() => app.currentProjectId, () => route.query.configurationId],
    async () => {
      const run = ++generation;
      configurations.value = [];
      versions.value = [];
      if (!app.currentProjectId || !hasAnyPermission(['SHARED_CONFIGURATION:READ'])) return;
      try {
        const value = await MSR.get<Configuration[]>({
          url: '/shared-configurations',
          params: { projectId: app.currentProjectId },
        });
        if (run !== generation) return;
        configurations.value = value;
        id.value = String(route.query.configurationId || '');
        await loadVersions();
      } catch (failure) {
        error.value = businessAssetError(failure, '配置目录读取失败。');
      }
    },
    { immediate: true }
  );
</script>
