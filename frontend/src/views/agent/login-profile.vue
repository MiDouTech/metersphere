<template
  ><AgentPage
    ><MsCard simple
      ><div class="mb-4 flex justify-between"
        ><div
          ><div class="text-base font-medium">自动登录配置</div
          ><div class="text-sm text-[var(--color-text-3)]">仅保存定位器和断言；账号密码来自运行时凭据引用。</div></div
        ><a-space
          ><a-button :loading="loading" @click="load">刷新</a-button
          ><a-button v-permission="['AI_EXECUTION:RUN']" type="primary" @click="open()">新增</a-button></a-space
        ></div
      ><a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert
      ><a-table :data="items" :loading="loading" :pagination="false"
        ><template #empty><a-empty description="暂无登录配置" /></template
        ><template #columns
          ><a-table-column title="名称" data-index="name" /><a-table-column
            title="环境配置"
            data-index="environmentProfileId"
          /><a-table-column title="登录地址" data-index="loginUrl" /><a-table-column
            title="MFA 策略"
            data-index="mfaPolicy"
          /><a-table-column title="状态"
            ><template #cell="{ record }"
              ><a-tag :color="record.enabled ? 'green' : 'gray'">{{
                record.enabled ? '启用' : '停用'
              }}</a-tag></template
            ></a-table-column
          ><a-table-column title="操作"
            ><template #cell="{ record }"
              ><a-space
                ><a-button type="text" @click="open(record)">编辑</a-button
                ><a-button type="text" @click="toggle(record)">{{
                  record.enabled ? '停用' : '启用'
                }}</a-button></a-space
              ></template
            ></a-table-column
          ></template
        ></a-table
      ></MsCard
    ><a-modal
      v-model:visible="visible"
      :title="form.id ? '编辑登录配置' : '新增登录配置'"
      width="800px"
      :ok-loading="saving"
      @before-ok="save"
      ><a-form :model="form" layout="vertical"
        ><div class="grid grid-cols-2 gap-x-4"
          ><a-form-item label="名称" required><a-input v-model="form.name" /></a-form-item
          ><a-form-item label="环境执行配置" required
            ><a-select v-model="form.environmentProfileId"
              ><a-option v-for="e in environments" :key="e.id" :value="e.id">{{ e.name }}</a-option></a-select
            ></a-form-item
          ><a-form-item label="登录地址" required><a-input v-model="form.loginUrl" /></a-form-item
          ><a-form-item label="MFA 策略" required
            ><a-select v-model="form.mfaPolicy"
              ><a-option value="BLOCK">BLOCK</a-option><a-option value="CHECKPOINT">CHECKPOINT</a-option></a-select
            ></a-form-item
          ><a-form-item label="超时(ms)" required
            ><a-input-number v-model="form.timeoutMs" :min="1000" :max="60000" class="w-full" /></a-form-item
          ><a-form-item label="启用"><a-switch v-model="form.enabled" /></a-form-item></div
        ><a-form-item label="用户名定位器 JSON" required><a-textarea v-model="form.usernameLocator" /></a-form-item
        ><a-form-item label="密码定位器 JSON" required><a-textarea v-model="form.passwordLocator" /></a-form-item
        ><a-form-item label="提交定位器 JSON" required><a-textarea v-model="form.submitLocator" /></a-form-item
        ><a-form-item label="成功断言 JSON" required><a-textarea v-model="form.successAssertion" /></a-form-item
        ><a-form-item label="会话断言 JSON"
          ><a-textarea v-model="form.sessionValidation" /></a-form-item></a-form></a-modal></AgentPage
></template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import AgentPage from './components/AgentPage.vue';

  import type { AiEnvironmentProfile, AiLoginProfile, AiLoginProfileRequest } from '@/api/modules/ai-execution';
  import {
    createAiLoginProfile,
    disableAiLoginProfile,
    enableAiLoginProfile,
    listAiEnvironmentProfiles,
    listAiLoginProfiles,
    updateAiLoginProfile,
  } from '@/api/modules/ai-execution';
  import useAppStore from '@/store/modules/app';

  const app = useAppStore();
  const items = ref<AiLoginProfile[]>([]);
  const environments = ref<AiEnvironmentProfile[]>([]);
  const loading = ref(false);
  const saving = ref(false);
  const visible = ref(false);
  const error = ref('');
  const defaults = {
    id: '',
    environmentProfileId: '',
    name: '',
    loginType: 'FORM' as const,
    loginUrl: '',
    usernameLocator: '{"strategy":"LABEL","label":"用户名"}',
    passwordLocator: '{"strategy":"LABEL","label":"密码"}',
    submitLocator: '{"strategy":"ROLE","role":"button","name":"登录"}',
    successAssertion:
      '{"contractVersion":"v1","type":"URL","operator":"NOT_EQUALS","expected":"/login","timeoutMs":15000}',
    sessionValidation: '',
    mfaPolicy: 'CHECKPOINT' as 'BLOCK' | 'CHECKPOINT',
    timeoutMs: 15000,
    enabled: true,
    version: 0,
  };
  const form = reactive({ ...defaults });
  const msg = (e: unknown) => (e as { message?: string })?.message || '请求失败';
  async function load() {
    loading.value = true;
    error.value = '';
    try {
      [items.value, environments.value] = await Promise.all([
        listAiLoginProfiles(app.currentProjectId),
        listAiEnvironmentProfiles(app.currentProjectId),
      ]);
    } catch (e) {
      error.value = msg(e);
    } finally {
      loading.value = false;
    }
  }
  function open(r?: AiLoginProfile) {
    Object.assign(form, r ? { ...r, sessionValidation: r.sessionValidation || '' } : defaults);
    visible.value = true;
  }
  async function save(done: (v: boolean) => void) {
    if (!form.name.trim() || !form.environmentProfileId || !form.loginUrl.trim()) {
      Message.warning('请填写必填字段');
      done(false);
      return;
    }
    const invalidJson = [
      form.usernameLocator,
      form.passwordLocator,
      form.submitLocator,
      form.successAssertion,
      ...(form.sessionValidation ? [form.sessionValidation] : []),
    ].some((value) => {
      try {
        JSON.parse(value);
        return false;
      } catch {
        return true;
      }
    });
    if (invalidJson) {
      Message.warning('定位器和断言必须是合法 JSON');
      done(false);
      return;
    }
    const data: AiLoginProfileRequest = {
      projectId: app.currentProjectId,
      environmentProfileId: form.environmentProfileId,
      name: form.name.trim(),
      loginType: 'FORM',
      loginUrl: form.loginUrl.trim(),
      usernameLocator: form.usernameLocator,
      passwordLocator: form.passwordLocator,
      submitLocator: form.submitLocator,
      successAssertion: form.successAssertion,
      sessionValidation: form.sessionValidation || undefined,
      mfaPolicy: form.mfaPolicy,
      timeoutMs: form.timeoutMs,
      enabled: form.enabled,
      version: form.version,
    };
    saving.value = true;
    try {
      if (form.id) await updateAiLoginProfile(form.id, data);
      else await createAiLoginProfile(data);
      await load();
      Message.success('保存成功');
      done(true);
    } catch (e) {
      Message.error(msg(e));
      done(false);
    } finally {
      saving.value = false;
    }
  }
  async function toggle(r: AiLoginProfile) {
    try {
      if (r.enabled) await disableAiLoginProfile(r.id);
      else await enableAiLoginProfile(r.id);
      await load();
    } catch (e) {
      Message.error(msg(e));
    }
  }
  onMounted(load);
</script>
