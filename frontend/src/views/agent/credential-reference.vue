<template>
  <AgentPage
    ><MsCard simple>
      <div class="mb-4 flex items-center justify-between"
        ><div
          ><div class="text-base font-medium">凭据引用</div
          ><div class="mt-1 text-sm text-[var(--color-text-3)]"
            >仅保存 Secret Provider 引用，不展示或传递真实密钥。</div
          ></div
        ><a-space
          ><a-button :loading="loading" @click="load">刷新</a-button
          ><a-button v-permission="['AI_CREDENTIAL:MANAGE']" type="primary" @click="open()">新增引用</a-button></a-space
        ></div
      >
      <a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert>
      <a-table :data="items" :loading="loading" :pagination="false" row-key="id"
        ><template #empty><a-empty description="暂无凭据引用" /></template
        ><template #columns>
          <a-table-column title="名称" data-index="name" /><a-table-column
            title="环境 ID"
            data-index="environmentId"
          /><a-table-column title="业务角色" data-index="businessRole" />
          <a-table-column title="类型" data-index="credentialType" /><a-table-column
            title="Provider"
            data-index="providerType"
          />
          <a-table-column title="状态"
            ><template #cell="{ record }"
              ><a-tag
                :color="record.status === 'ACTIVE' ? 'green' : record.status === 'UNAVAILABLE' ? 'red' : 'orange'"
                >{{ record.status }}</a-tag
              ></template
            ></a-table-column
          >
          <a-table-column title="Secret 版本" data-index="secretVersion" /><a-table-column title="最后验证"
            ><template #cell="{ record }">{{
              record.lastVerifiedAt ? new Date(record.lastVerifiedAt).toLocaleString() : '-'
            }}</template></a-table-column
          >
          <a-table-column title="过期时间"
            ><template #cell="{ record }">{{
              record.expiresAt ? new Date(record.expiresAt).toLocaleString() : '长期'
            }}</template></a-table-column
          >
          <a-table-column title="操作" :width="220"
            ><template #cell="{ record }"
              ><a-space
                ><a-button
                  v-permission="['AI_CREDENTIAL:VERIFY']"
                  type="text"
                  :disabled="!record.enabled"
                  @click="verify(record)"
                  >验证</a-button
                ><a-button v-permission="['AI_CREDENTIAL:MANAGE']" type="text" @click="open(record)">更新引用</a-button
                ><a-button v-permission="['AI_CREDENTIAL:MANAGE']" type="text" @click="toggle(record)">{{
                  record.enabled ? '停用' : '启用'
                }}</a-button></a-space
              ></template
            ></a-table-column
          >
        </template></a-table
      >
    </MsCard>
    <a-modal
      v-model:visible="visible"
      :title="form.id ? '更新凭据引用' : '新增凭据引用'"
      :ok-loading="saving"
      @before-ok="save"
      ><a-form :model="form" layout="vertical">
        <div class="grid grid-cols-2 gap-x-4"
          ><a-form-item label="名称" required><a-input v-model="form.name" /></a-form-item
          ><a-form-item label="环境 ID" required><a-input v-model="form.environmentId" /></a-form-item>
          <a-form-item label="凭据类型" required
            ><a-select v-model="form.credentialType"
              ><a-option value="USERNAME_PASSWORD">账号密码</a-option><a-option value="TOKEN">Token</a-option
              ><a-option value="API_KEY">API Key</a-option
              ><a-option value="OAUTH_CLIENT">OAuth Client</a-option></a-select
            ></a-form-item
          >
          <a-form-item label="Provider" required
            ><a-select v-model="form.providerType"
              ><a-option value="VAULT">Vault</a-option
              ><a-option value="ENV">ENV（仅受控非生产环境）</a-option></a-select
            ></a-form-item
          ></div
        >
        <a-form-item label="业务角色" required
          ><a-input v-model="form.businessRole" placeholder="TEST_ADMIN"
        /></a-form-item>
        <a-form-item
          label="Secret 引用"
          required
          extra="保存后不会回显；Vault：vault://mount/path#field；ENV：env://TEST_ADMIN_SECRET"
          ><a-input-password v-model="form.secretRef" autocomplete="new-password"
        /></a-form-item>
        <a-form-item label="用户名提示"><a-input v-model="form.usernameHint" /></a-form-item
        ><a-form-item label="过期时间"
          ><a-date-picker
            v-model="form.expiresAt"
            show-time
            value-format="timestamp"
            class="w-full"
            allow-clear /></a-form-item
        ><a-form-item label="启用"><a-switch v-model="form.enabled" /></a-form-item> </a-form></a-modal
  ></AgentPage>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import AgentPage from './components/AgentPage.vue';

  import type { AiCredentialReference, AiCredentialReferenceRequest } from '@/api/modules/ai-execution';
  import {
    createAiCredentialReference,
    disableAiCredentialReference,
    enableAiCredentialReference,
    listAiCredentialReferences,
    updateAiCredentialReference,
    verifyAiCredentialReference,
  } from '@/api/modules/ai-execution';
  import useAppStore from '@/store/modules/app';

  const app = useAppStore();
  const items = ref<AiCredentialReference[]>([]);
  const loading = ref(false);
  const saving = ref(false);
  const visible = ref(false);
  const error = ref('');
  const form = reactive({
    id: '',
    name: '',
    environmentId: '',
    credentialType: 'USERNAME_PASSWORD' as AiCredentialReference['credentialType'],
    businessRole: '',
    providerType: 'ENV' as AiCredentialReference['providerType'],
    secretRef: '',
    usernameHint: '',
    expiresAt: undefined as number | undefined,
    enabled: true,
    version: 0,
  });
  const msg = (e: unknown) => (e as { message?: string })?.message || '请求失败，请稍后重试';
  async function load() {
    loading.value = true;
    error.value = '';
    try {
      items.value = await listAiCredentialReferences(app.currentProjectId);
    } catch (e) {
      error.value = msg(e);
    } finally {
      loading.value = false;
    }
  }
  function open(r?: AiCredentialReference) {
    Object.assign(
      form,
      r
        ? {
            id: r.id,
            name: r.name,
            environmentId: r.environmentId,
            credentialType: r.credentialType,
            businessRole: r.businessRole,
            providerType: r.providerType,
            secretRef: '',
            usernameHint: r.usernameHint || '',
            expiresAt: r.expiresAt,
            enabled: r.enabled,
            version: r.version,
          }
        : {
            id: '',
            name: '',
            environmentId: '',
            credentialType: 'USERNAME_PASSWORD',
            businessRole: '',
            providerType: 'ENV',
            secretRef: '',
            usernameHint: '',
            expiresAt: undefined,
            enabled: true,
            version: 0,
          }
    );
    visible.value = true;
  }
  async function save(done: (closed: boolean) => void) {
    if (!form.name.trim() || !form.environmentId.trim() || !form.businessRole.trim() || !form.secretRef.trim()) {
      Message.warning('请完整填写必填字段，更新时也必须提交新的 Secret 引用');
      done(false);
      return;
    }
    if (form.expiresAt && form.expiresAt <= Date.now()) {
      Message.warning('过期时间必须晚于当前时间');
      done(false);
      return;
    }
    const data: AiCredentialReferenceRequest = {
      projectId: app.currentProjectId,
      environmentId: form.environmentId.trim(),
      name: form.name.trim(),
      credentialType: form.credentialType,
      businessRole: form.businessRole.trim(),
      providerType: form.providerType,
      secretRef: form.secretRef.trim(),
      usernameHint: form.usernameHint.trim() || undefined,
      expiresAt: form.expiresAt,
      enabled: form.enabled,
      version: form.version,
    };
    saving.value = true;
    try {
      if (form.id) await updateAiCredentialReference(form.id, data);
      else await createAiCredentialReference(data);
      form.secretRef = '';
      Message.success('保存成功');
      await load();
      done(true);
    } catch (e) {
      Message.error(msg(e));
      done(false);
    } finally {
      saving.value = false;
    }
  }
  async function verify(r: AiCredentialReference) {
    try {
      const x = await verifyAiCredentialReference(r.id);
      if (x.valid) Message.success(`验证通过 · ${x.traceId}`);
      else Message.error(`${x.message} · ${x.traceId}`);
      await load();
    } catch (e) {
      Message.error(msg(e));
    }
  }
  async function toggle(r: AiCredentialReference) {
    try {
      if (r.enabled) await disableAiCredentialReference(r.id);
      else await enableAiCredentialReference(r.id);
      Message.success(r.enabled ? '已停用' : '已启用，使用前请重新验证');
      await load();
    } catch (e) {
      Message.error(msg(e));
    }
  }
  onMounted(load);
</script>
