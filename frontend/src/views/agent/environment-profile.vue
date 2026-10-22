<template>
  <AgentPage>
    <MsCard simple>
      <div class="mb-4 flex items-center justify-between">
        <div>
          <div class="text-base font-medium">测试平台配置</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]"
            >管理测试地址、登录方式和凭据引用。地址连接检查不代表已经成功登录。</div
          >
        </div>
        <a-space>
          <a-button :loading="loading" @click="load">刷新</a-button>
          <a-button v-permission="['AI_EXECUTION:RUN']" type="primary" @click="openForm()">新增配置</a-button>
        </a-space>
      </div>
      <a-alert v-if="errorMessage" type="error" class="mb-4">{{ errorMessage }}</a-alert>
      <a-table :data="profiles" :loading="loading" v-bind="tablePaging" row-key="id">
        <template #empty><a-empty description="当前项目尚未配置测试平台" /></template>
        <template #columns>
          <a-table-column title="名称" data-index="name" />
          <a-table-column title="环境" data-index="environmentType" :width="110" />
          <a-table-column title="基础地址" data-index="baseUrl" />
          <a-table-column title="受管环境资产" :width="150">
            <template #cell="{ record }">{{
              record.environmentAssetId ? '已绑定（执行前复核）' : '未绑定 · 个人直连'
            }}</template>
          </a-table-column>
          <a-table-column title="Runner" data-index="runnerType" :width="110" />
          <a-table-column title="状态" :width="100">
            <template #cell="{ record }"
              ><a-tag :color="record.enabled ? 'green' : 'gray'">{{
                record.enabled ? '启用' : '停用'
              }}</a-tag></template
            >
          </a-table-column>
          <a-table-column title="操作" :width="240">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" :disabled="!record.enabled" @click="verify(record)">验证</a-button>
                <a-button v-permission="['AI_EXECUTION:RUN']" type="text" @click="openForm(record)">编辑</a-button>
                <a-button type="text" @click="selectedPlatform = record">登录与角色</a-button>
                <a-button v-permission="['AI_EXECUTION:RUN']" type="text" @click="toggle(record)">{{
                  record.enabled ? '停用' : '启用'
                }}</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </MsCard>

    <a-drawer
      :visible="!!selectedPlatform"
      :width="960"
      :footer="false"
      :title="`${selectedPlatform?.name || ''} · 登录与角色`"
      @cancel="closePlatform"
    >
      <template v-if="selectedPlatform">
        <a-alert class="mb-4">以下配置只关联当前测试平台。保存后可在平台编辑中选择默认登录方式和角色凭据。</a-alert>
        <LoginProfile :key="`login:${selectedPlatform.id}`" :platform-id="selectedPlatform.id" />
        <CredentialReference
          v-if="hasAnyPermission(['AI_CREDENTIAL:READ_METADATA'])"
          :key="`credential:${selectedPlatform.id}`"
          :platform-environment-id="selectedPlatform.environmentId"
        />
      </template>
    </a-drawer>
    <a-modal
      v-model:visible="visible"
      :title="form.id ? '编辑测试平台配置' : '新增测试平台配置'"
      :ok-loading="saving"
      @before-ok="save"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="名称" required><a-input v-model="form.name" :max-length="255" /></a-form-item>
        <a-form-item label="环境类型" required>
          <a-select v-model="form.environmentType"
            ><a-option value="TEST">测试</a-option><a-option value="STAGING">预发布</a-option></a-select
          >
        </a-form-item>
        <a-form-item
          label="受管任务环境资产"
          extra="个人直连无需绑定。受管任务请选择已发布的环境资产；不改变原凭据范围。"
        >
          <a-select
            v-model="form.environmentAssetId"
            allow-clear
            allow-search
            :filter-option="false"
            :loading="assetsLoading"
            :disabled="!hasAnyPermission(['PROJECT_ENVIRONMENT:READ'])"
            placeholder="输入名称搜索已发布环境"
            @search="searchAssets"
            @popup-visible-change="onAssetPopup"
          >
            <a-option v-for="item in environmentAssets" :key="item.id" :value="item.id">{{ item.name }}</a-option>
          </a-select>
          <div v-if="assetError" class="mt-1 text-[rgb(var(--danger-6))]">{{ assetError }}</div>
        </a-form-item>
        <a-form-item label="基础地址" required
          ><a-input v-model="form.baseUrl" placeholder="https://test.example.com"
        /></a-form-item>
        <a-form-item label="允许 Origin" required extra="每行一个，必须包含基础地址的 Origin">
          <a-textarea v-model="form.allowedOriginsText" :auto-size="{ minRows: 2, maxRows: 6 }" />
        </a-form-item>
        <div class="grid grid-cols-2 gap-x-4">
          <a-form-item label="Runner 类型" required
            ><a-select v-model="form.runnerType"
              ><a-option value="BROWSER">Browser</a-option><a-option value="API">API</a-option></a-select
            ></a-form-item
          >
          <a-form-item label="网络区域"><a-input v-model="form.networkZone" /></a-form-item>
        </div>
        <a-form-item label="所需能力" extra="逗号分隔"
          ><a-input v-model="form.capabilitiesText" placeholder="WEB_UI,SCREENSHOT"
        /></a-form-item>
        <a-form-item v-if="form.id" label="自动登录配置"
          ><a-select v-model="form.loginProfileId" allow-clear
            ><a-option
              v-for="item in loginProfiles.filter((x) => x.environmentProfileId === form.id && x.enabled)"
              :key="item.id"
              :value="item.id"
              >{{ item.name }}</a-option
            ></a-select
          ></a-form-item
        >
        <a-form-item v-if="form.id" label="默认凭据引用"
          ><a-select v-model="form.defaultCredentialReferenceId" allow-clear
            ><a-option
              v-for="item in credentials.filter((x) => x.environmentId === form.environmentId && x.enabled)"
              :key="item.id"
              :value="item.id"
              >{{ item.name }} · {{ item.businessRole }}</a-option
            ></a-select
          ></a-form-item
        >
        <a-form-item label="状态"><a-switch v-model="form.enabled" /></a-form-item>
      </a-form>
    </a-modal>
  </AgentPage>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import AgentPage from './components/AgentPage.vue';
  import CredentialReference from './credential-reference.vue';
  import LoginProfile from './login-profile.vue';

  import type {
    AiCredentialReference,
    AiEnvironmentProfile,
    AiEnvironmentProfileRequest,
    AiLoginProfile,
    TestAssetCatalogItem,
  } from '@/api/modules/ai-execution';
  import {
    createAiEnvironmentProfile,
    disableAiEnvironmentProfile,
    enableAiEnvironmentProfile,
    listAiCredentialReferences,
    listAiEnvironmentProfiles,
    listAiLoginProfiles,
    pageTestAssetCatalog,
    updateAiEnvironmentProfile,
    verifyAiEnvironmentProfile,
  } from '@/api/modules/ai-execution';
  import useClientTablePaging from '@/hooks/useClientTablePaging';
  import useListRefresh from '@/hooks/useListRefresh';
  import useAppStore from '@/store/modules/app';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';
  import { hasAnyPermission } from '@/utils/permission';
  import { platformUrl } from '@/utils/platformConfiguration';

  const appStore = useAppStore();
  const selectedPlatform = ref<AiEnvironmentProfile>();
  const profiles = ref<AiEnvironmentProfile[]>([]);
  const loginProfiles = ref<AiLoginProfile[]>([]);
  const credentials = ref<AiCredentialReference[]>([]);
  const tablePaging = useClientTablePaging('environment-profile', () => profiles.value.length);
  const loading = ref(false);
  const saving = ref(false);
  const visible = ref(false);
  const errorMessage = ref('');
  const environmentAssets = ref<TestAssetCatalogItem[]>([]);
  const assetsLoading = ref(false);
  const assetError = ref('');
  let assetSequence = 0;
  const form = reactive({
    id: '',
    name: '',
    environmentId: '',
    environmentAssetId: '',
    environmentType: 'TEST' as 'TEST' | 'STAGING',
    baseUrl: '',
    allowedOriginsText: '',
    runnerType: 'BROWSER' as 'BROWSER' | 'API',
    networkZone: '',
    capabilitiesText: '',
    loginProfileId: '',
    defaultCredentialReferenceId: '',
    enabled: true,
    version: 0,
  });

  function safeMessage(error: unknown) {
    return formatAppErrorMessage(ensureAppError(error, '请求失败，请稍后重试'));
  }
  async function searchAssets(keyword = '') {
    if (!hasAnyPermission(['PROJECT_ENVIRONMENT:READ'])) return;
    const sequence = ++assetSequence;
    const projectId = appStore.currentProjectId;
    assetsLoading.value = true;
    assetError.value = '';
    try {
      const result = await pageTestAssetCatalog({
        projectId,
        assetType: 'ENVIRONMENT',
        keyword,
        status: 'PUBLISHED',
        current: 1,
        pageSize: 100,
      });
      if (sequence !== assetSequence || projectId !== appStore.currentProjectId || !visible.value) return;
      environmentAssets.value = result.list.filter((item) => !!item.assetVersionId);
      if (result.total > 100) assetError.value = '结果较多，请输入更完整的环境名称缩小范围';
    } catch (error) {
      if (sequence === assetSequence) assetError.value = safeMessage(error);
    } finally {
      if (sequence === assetSequence) assetsLoading.value = false;
    }
  }
  function onAssetPopup(open: boolean) {
    if (open) searchAssets();
  }
  let loadSequence = 0;
  async function load() {
    const sequence = ++loadSequence;
    const projectId = appStore.currentProjectId;
    if (!projectId) {
      loading.value = false;
      return;
    }
    loading.value = true;
    errorMessage.value = '';
    try {
      const response = await Promise.all([
        listAiEnvironmentProfiles(projectId),
        listAiLoginProfiles(projectId),
        hasAnyPermission(['AI_CREDENTIAL:READ_METADATA']) ? listAiCredentialReferences(projectId) : Promise.resolve([]),
      ]);
      if (sequence !== loadSequence || projectId !== appStore.currentProjectId) return;
      [profiles.value, loginProfiles.value, credentials.value] = response;
    } catch (error) {
      if (sequence !== loadSequence) return;
      errorMessage.value = safeMessage(error);
    } finally {
      if (sequence === loadSequence) loading.value = false;
    }
  }
  async function closePlatform() {
    selectedPlatform.value = undefined;
    await load();
  }
  function openForm(record?: AiEnvironmentProfile) {
    Object.assign(
      form,
      record
        ? {
            id: record.id,
            name: record.name,
            environmentId: record.environmentId,
            environmentAssetId: record.environmentAssetId || '',
            environmentType: record.environmentType,
            baseUrl: record.baseUrl,
            allowedOriginsText: record.allowedOrigins.join('\n'),
            runnerType: record.runnerType,
            networkZone: record.networkZone || '',
            capabilitiesText: record.requiredCapabilities.join(','),
            loginProfileId: record.loginProfileId || '',
            defaultCredentialReferenceId: record.defaultCredentialReferenceId || '',
            enabled: record.enabled,
            version: record.version,
          }
        : {
            id: '',
            name: '',
            environmentId: '',
            environmentAssetId: '',
            environmentType: 'TEST',
            baseUrl: '',
            allowedOriginsText: '',
            runnerType: 'BROWSER',
            networkZone: '',
            capabilitiesText: '',
            loginProfileId: '',
            defaultCredentialReferenceId: '',
            enabled: true,
            version: 0,
          }
    );
    visible.value = true;
    environmentAssets.value = [];
    assetError.value = '';
  }
  async function save(done: (closed: boolean) => void) {
    const origins = form.allowedOriginsText
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter(Boolean);
    if (!form.name.trim() || !form.baseUrl.trim() || !origins.length) {
      Message.warning('请完整填写名称、基础地址和允许 Origin');
      done(false);
      return;
    }
    if (saving.value) {
      done(false);
      return;
    }
    try {
      const base = platformUrl(form.baseUrl);
      if (!origins.map((value) => platformUrl(value, true).origin).includes(base.origin)) {
        throw new Error('允许 Origin 必须包含基础地址的 Origin');
      }
    } catch (error) {
      Message.warning((error as Error).message);
      done(false);
      return;
    }
    const payload: AiEnvironmentProfileRequest = {
      projectId: appStore.currentProjectId,
      environmentId: form.environmentId.trim(),
      environmentAssetId: form.environmentAssetId || '',
      name: form.name.trim(),
      baseUrl: form.baseUrl.trim(),
      allowedOrigins: origins,
      environmentType: form.environmentType,
      runnerType: form.runnerType,
      networkZone: form.networkZone.trim() || undefined,
      requiredCapabilities: form.capabilitiesText
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean),
      enabled: form.enabled,
      version: form.version,
      loginProfileId: form.loginProfileId || undefined,
      defaultCredentialReferenceId: form.defaultCredentialReferenceId || undefined,
    };
    saving.value = true;
    try {
      if (form.id) await updateAiEnvironmentProfile(form.id, payload);
      else await createAiEnvironmentProfile(payload);
      Message.success('保存成功');
      done(true);
      await load();
    } catch (error) {
      Message.error(safeMessage(error));
      done(false);
    } finally {
      saving.value = false;
    }
  }
  async function verify(record: AiEnvironmentProfile) {
    try {
      const result = await verifyAiEnvironmentProfile(record.id);
      if (result.valid) Message.success(`地址连接检查通过（尚未验证登录） · ${result.traceId}`);
      else Message.error(`验证未通过：${result.checks.join('、')} · ${result.traceId}`);
    } catch (error) {
      Message.error(safeMessage(error));
    }
  }
  async function toggle(record: AiEnvironmentProfile) {
    try {
      if (record.enabled) await disableAiEnvironmentProfile(record.id);
      else await enableAiEnvironmentProfile(record.id);
      Message.success(record.enabled ? '已停用' : '已启用');
      await load();
    } catch (error) {
      Message.error(safeMessage(error));
    }
  }
  watch(
    () => appStore.currentProjectId,
    () => {
      visible.value = false;
      profiles.value = [];
      selectedPlatform.value = undefined;
      loginProfiles.value = [];
      credentials.value = [];
      assetSequence += 1;
      environmentAssets.value = [];
      assetsLoading.value = false;
      assetError.value = '';
      load();
    }
  );
  onMounted(() => {
    if (appStore.currentProjectId) load();
  });
  useListRefresh(
    load,
    () => !!appStore.currentProjectId && !loading.value && !visible.value && !selectedPlatform.value
  );
  onBeforeUnmount(() => {
    loadSequence += 1;
    assetSequence += 1;
  });
</script>
