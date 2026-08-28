<template>
  <AgentPage>
    <MsCard simple>
      <div class="mb-4 flex items-center justify-between">
        <div>
          <div class="text-base font-medium">环境执行配置</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]"
            >限定 AI 测试可访问的地址、网络区与 Runner 能力；生产环境暂不开放。</div
          >
        </div>
        <a-space>
          <a-button :loading="loading" @click="load">刷新</a-button>
          <a-button v-permission="['AI_EXECUTION:RUN']" type="primary" @click="openForm()">新增配置</a-button>
        </a-space>
      </div>
      <a-alert v-if="errorMessage" type="error" class="mb-4">{{ errorMessage }}</a-alert>
      <a-table :data="profiles" :loading="loading" :pagination="false" row-key="id">
        <template #empty><a-empty description="当前项目尚未配置 AI 测试环境" /></template>
        <template #columns>
          <a-table-column title="名称" data-index="name" />
          <a-table-column title="环境" data-index="environmentType" :width="110" />
          <a-table-column title="基础地址" data-index="baseUrl" />
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
                <a-button v-permission="['AI_EXECUTION:RUN']" type="text" @click="toggle(record)">{{
                  record.enabled ? '停用' : '启用'
                }}</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </MsCard>

    <a-modal
      v-model:visible="visible"
      :title="form.id ? '编辑环境执行配置' : '新增环境执行配置'"
      :ok-loading="saving"
      @before-ok="save"
    >
      <a-form :model="form" layout="vertical">
        <a-form-item label="名称" required><a-input v-model="form.name" :max-length="255" /></a-form-item>
        <a-form-item label="环境 ID" required><a-input v-model="form.environmentId" /></a-form-item>
        <a-form-item label="环境类型" required>
          <a-select v-model="form.environmentType"
            ><a-option value="TEST">测试</a-option><a-option value="STAGING">预发布</a-option></a-select
          >
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
        <a-form-item label="自动登录配置"
          ><a-select v-model="form.loginProfileId" allow-clear
            ><a-option
              v-for="item in loginProfiles.filter((x) => x.environmentProfileId === form.id && x.enabled)"
              :key="item.id"
              :value="item.id"
              >{{ item.name }}</a-option
            ></a-select
          ></a-form-item
        >
        <a-form-item label="默认凭据引用"
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
  import { onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import AgentPage from './components/AgentPage.vue';

  import type {
    AiCredentialReference,
    AiEnvironmentProfile,
    AiEnvironmentProfileRequest,
    AiLoginProfile,
  } from '@/api/modules/ai-execution';
  import {
    createAiEnvironmentProfile,
    disableAiEnvironmentProfile,
    enableAiEnvironmentProfile,
    listAiCredentialReferences,
    listAiEnvironmentProfiles,
    listAiLoginProfiles,
    updateAiEnvironmentProfile,
    verifyAiEnvironmentProfile,
  } from '@/api/modules/ai-execution';
  import useAppStore from '@/store/modules/app';

  const appStore = useAppStore();
  const profiles = ref<AiEnvironmentProfile[]>([]);
  const loginProfiles = ref<AiLoginProfile[]>([]);
  const credentials = ref<AiCredentialReference[]>([]);
  const loading = ref(false);
  const saving = ref(false);
  const visible = ref(false);
  const errorMessage = ref('');
  const form = reactive({
    id: '',
    name: '',
    environmentId: '',
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
    const candidate = error as { message?: string };
    return candidate?.message || '请求失败，请稍后重试';
  }
  async function load() {
    loading.value = true;
    errorMessage.value = '';
    try {
      [profiles.value, loginProfiles.value, credentials.value] = await Promise.all([
        listAiEnvironmentProfiles(appStore.currentProjectId),
        listAiLoginProfiles(appStore.currentProjectId),
        listAiCredentialReferences(appStore.currentProjectId),
      ]);
    } catch (error) {
      errorMessage.value = safeMessage(error);
    } finally {
      loading.value = false;
    }
  }
  function openForm(record?: AiEnvironmentProfile) {
    Object.assign(
      form,
      record
        ? {
            id: record.id,
            name: record.name,
            environmentId: record.environmentId,
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
  }
  async function save(done: (closed: boolean) => void) {
    const origins = form.allowedOriginsText
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter(Boolean);
    if (!form.name.trim() || !form.environmentId.trim() || !form.baseUrl.trim() || !origins.length) {
      Message.warning('请完整填写名称、环境、基础地址和允许 Origin');
      done(false);
      return;
    }
    const payload: AiEnvironmentProfileRequest = {
      projectId: appStore.currentProjectId,
      environmentId: form.environmentId.trim(),
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
      await load();
      done(true);
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
      if (result.valid) Message.success(`DNS、TLS、连通性和 Runner 校验通过 · ${result.traceId}`);
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
  onMounted(load);
</script>
