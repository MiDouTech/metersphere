<template>
  <section class="mb-6" aria-label="公共配置版本">
    <div class="asset-toolbar">
      <div class="asset-actions"><a-button :loading="loading" @click="reload">刷新</a-button></div>
      <div class="asset-search"
        ><a-select
          v-model="id"
          allow-search
          allow-clear
          placeholder="请选择公共配置"
          aria-label="公共配置"
          style="width: 400px"
          @change="loadVersions"
          ><a-option v-for="item in configurations" :key="item.id" :value="item.id"
            >{{ item.systemKey }} / {{ item.environmentKey }} / {{ item.name }}</a-option
          ></a-select
        ></div
      >
    </div>
    <a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert>
    <a-alert class="mb-4">历史敏感值保持脱敏。切换仅影响后续会话，已有执行器继续使用已固定版本。</a-alert>
    <a-table :data="versions" row-key="versionNo" :loading="loading" :scroll="{ x: 950 }"
      ><template #empty><a-empty :description="id ? '暂无配置版本' : '请选择公共配置查看版本'" /></template
      ><template #columns>
        <a-table-column title="版本" data-index="versionNo" /><a-table-column title="类型"
          ><template #cell="{ record }">{{
            record.kind === 'SECRET'
              ? record.secretType === 'ACCOUNT_PASSWORD'
                ? '账号密码'
                : '其他敏感配置'
              : record.kind === 'DOCUMENT'
              ? '配置文档'
              : '普通配置'
          }}</template></a-table-column
        ><a-table-column title="原始文档" data-index="fileName" /><a-table-column title="解析状态"
          ><template #cell="{ record }">{{ parseLabel(record.parseStatus) }}</template></a-table-column
        >
        <a-table-column title="更新人" data-index="actorId" />
        <a-table-column title="更新时间"
          ><template #cell="{ record }">{{
            dayjs(record.createTime).format('YYYY-MM-DD HH:mm')
          }}</template></a-table-column
        >
        <a-table-column title="操作"
          ><template #cell="{ record }"
            ><a-link v-if="canWrite && record.versionNo !== current?.currentVersion" @click="choose(record.versionNo)"
              >设为当前版本</a-link
            ><span v-else-if="record.versionNo === current?.currentVersion">当前生效</span></template
          ></a-table-column
        >
      </template></a-table
    >
    <a-modal
      v-model:visible="switchVisible"
      title="切换公共配置版本"
      :ok-loading="switching"
      @before-ok="confirmSwitch"
    >
      <a-alert>新会话使用所选版本，执行中的会话保持原版本。</a-alert>
      <p>确认将当前配置切换至 V{{ targetVersion }}？</p>
      <a-form-item label="切换原因" required
        ><a-textarea v-model="switchReason" :max-length="500" placeholder="请输入切换原因"
      /></a-form-item>
      <a-alert v-if="switchError" type="error">{{ switchError }}</a-alert>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import dayjs from 'dayjs';

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
    secretType?: string;
    fileName: string;
    parseStatus: string;
    actorId: string;
    createTime: number;
  }
  const globalScope = '';
  const app = useAppStore();
  const route = useRoute();
  const configurations = ref<Configuration[]>([]);
  const id = ref('');
  const versions = ref<Version[]>([]);
  const error = ref('');
  const loading = ref(false);
  const switching = ref(false);
  const switchVisible = ref(false);
  const targetVersion = ref(0);
  const switchReason = ref('');
  const switchError = ref('');
  const current = computed(() => configurations.value.find((item) => item.id === id.value));
  const canWrite = computed(() => hasAnyPermission(['SHARED_CONFIGURATION:WRITE'], ['ORGANIZATION', 'SYSTEM']));
  const parseLabel = (status: string) =>
    ({ PARSED: '解析成功', FAILED: '解析失败', UNSUPPORTED: '暂不支持解析', NOT_APPLICABLE: '无需解析' }[status] ||
    status);
  let generation = 0;
  let catalogGeneration = 0;
  async function loadVersions() {
    const run = ++generation;
    versions.value = [];
    error.value = '';
    if (!id.value) {
      loading.value = false;
      return;
    }
    loading.value = true;
    try {
      const value = await MSR.get<Version[]>({
        url: `/shared-configurations/${id.value}/versions`,
        params: { projectId: globalScope },
      });
      if (run === generation) versions.value = value;
    } catch (failure) {
      if (run === generation) error.value = businessAssetError(failure, '配置版本读取失败。');
    } finally {
      if (run === generation) loading.value = false;
    }
  }
  function choose(version: number) {
    targetVersion.value = version;
    switchReason.value = '';
    switchError.value = '';
    switchVisible.value = true;
  }
  async function confirmSwitch() {
    if (!current.value || switching.value) return false;
    if (!switchReason.value.trim()) {
      switchError.value = '请输入切换原因';
      return false;
    }
    const project = app.currentOrgId;
    const configurationId = id.value;
    const version = targetVersion.value;
    switching.value = true;
    try {
      await MSR.post({
        url: `/shared-configurations/${id.value}/current-version`,
        data: {
          projectId: globalScope,
          version,
          expectedVersion: current.value.currentVersion,
          reason: switchReason.value.trim(),
        },
      });
      if (project !== app.currentOrgId || configurationId !== id.value) return true;
      current.value.currentVersion = version;
      error.value = '';
      await loadVersions();
      return true;
    } catch (failure) {
      switchError.value = businessAssetError(failure, '配置版本切换失败，请刷新后重试。');
      return false;
    } finally {
      switching.value = false;
    }
  }
  async function reload() {
    const run = ++catalogGeneration;
    error.value = '';
    loading.value = true;
    try {
      const value = await MSR.get<Configuration[]>({
        url: '/shared-configurations',
        params: { projectId: globalScope },
      });
      if (run !== catalogGeneration) return;
      configurations.value = value;
      if (!value.some((item) => item.id === id.value)) id.value = '';
      await loadVersions();
    } catch (failure) {
      if (run === catalogGeneration) error.value = businessAssetError(failure, '配置目录读取失败，请重试。');
    } finally {
      if (run === catalogGeneration) loading.value = false;
    }
  }
  watch(
    [() => app.currentOrgId, () => route.query.configurationId],
    async () => {
      ++generation;
      ++catalogGeneration;
      configurations.value = [];
      versions.value = [];
      switchVisible.value = false;
      id.value = String(route.query.configurationId || '');
      if (!hasAnyPermission(['SHARED_CONFIGURATION:READ'], ['ORGANIZATION', 'SYSTEM'])) return;
      await reload();
    },
    { immediate: true }
  );
</script>
