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
      ><a-table
        :data="platformId ? items.filter((item) => item.environmentProfileId === platformId) : items"
        :loading="loading"
        v-bind="tablePaging"
        ><template #empty><a-empty description="暂无登录配置" /></template
        ><template #columns
          ><a-table-column title="名称" data-index="name" /><a-table-column title="环境配置"
            ><template #cell="{ record }">{{
              environments.find((item) => item.id === record.environmentProfileId)?.name || '待关联平台'
            }}</template></a-table-column
          ><a-table-column title="登录地址" data-index="loginUrl" /><a-table-column
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
                ><a-button v-permission="['AI_EXECUTION:RUN']" type="text" @click="open(record)">编辑</a-button
                ><a-button v-permission="['AI_EXECUTION:RUN']" type="text" @click="toggle(record)">{{
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
          ><a-form-item field="name" label="名称" required><a-input v-model="form.name" /></a-form-item
          ><a-form-item field="environmentProfileId" label="环境执行配置" required
            ><a-select v-model="form.environmentProfileId" :disabled="!!platformId"
              ><a-option v-for="e in environments" :key="e.id" :value="e.id"
                >{{ e.name }}（{{ e.enabled ? '已启用' : '已停用' }}）</a-option
              ></a-select
            ></a-form-item
          ><a-form-item field="loginUrl" label="登录地址" required
            ><a-input v-model="form.loginUrl" placeholder="https://test.example.com/login" /></a-form-item
          ><a-form-item field="mfaPolicy" label="MFA 策略" required
            ><a-select v-model="form.mfaPolicy"
              ><a-option value="BLOCK">BLOCK</a-option><a-option value="CHECKPOINT">CHECKPOINT</a-option></a-select
            ></a-form-item
          ><a-form-item field="timeoutMs" label="超时(ms)" required
            ><a-input-number v-model="form.timeoutMs" :min="1000" :max="60000" class="w-full" /></a-form-item
          ><a-form-item field="enabled" label="启用"><a-switch v-model="form.enabled" /></a-form-item></div
        ><a-form-item field="usernameLocator" label="用户名定位器 JSON" required
          ><a-textarea v-model="form.usernameLocator" /></a-form-item
        ><a-form-item field="passwordLocator" label="密码定位器 JSON" required
          ><a-textarea v-model="form.passwordLocator" /></a-form-item
        ><a-form-item field="submitLocator" label="提交定位器 JSON" required
          ><a-textarea v-model="form.submitLocator" /></a-form-item
        ><a-form-item field="successAssertion" label="成功断言 JSON" required
          ><a-textarea v-model="form.successAssertion" /></a-form-item
        ><a-form-item field="sessionValidation" label="会话断言 JSON"
          ><a-textarea v-model="form.sessionValidation" /></a-form-item></a-form></a-modal></AgentPage
></template>

<script setup lang="ts">
  import { onMounted, reactive, ref, watch } from 'vue';
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
  import useClientTablePaging from '@/hooks/useClientTablePaging';
  import useListRefresh from '@/hooks/useListRefresh';
  import useAppStore from '@/store/modules/app';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';
  import { platformUrl, validateLoginJson } from '@/utils/platformConfiguration';

  const props = defineProps<{ platformId?: string }>();

  const app = useAppStore();
  const items = ref<AiLoginProfile[]>([]);
  const environments = ref<AiEnvironmentProfile[]>([]);
  const tablePaging = useClientTablePaging(
    'login-profile',
    () =>
      (props.platformId ? items.value.filter((item) => item.environmentProfileId === props.platformId) : items.value)
        .length,
    () => props.platformId
  );
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
  const msg = (e: unknown) => {
    return formatAppErrorMessage(ensureAppError(e, '请求失败，请稍后重试'));
  };
  let loadSequence = 0;
  async function load() {
    const sequence = ++loadSequence;
    const projectId = app.currentProjectId;
    if (!projectId) {
      loading.value = false;
      return;
    }
    loading.value = true;
    error.value = '';
    try {
      const response = await Promise.all([listAiLoginProfiles(projectId), listAiEnvironmentProfiles(projectId)]);
      if (sequence !== loadSequence || projectId !== app.currentProjectId) return;
      [items.value, environments.value] = response;
    } catch (e) {
      if (sequence !== loadSequence) return;
      error.value = msg(e);
    } finally {
      if (sequence === loadSequence) loading.value = false;
    }
  }
  function open(r?: AiLoginProfile) {
    Object.assign(form, r ? { ...r, sessionValidation: r.sessionValidation || '' } : defaults);
    if (!r && props.platformId) {
      form.environmentProfileId = props.platformId;
      form.loginUrl = environments.value.find((item) => item.id === props.platformId)?.baseUrl || '';
    }
    visible.value = true;
  }
  async function save(done: (v: boolean) => void) {
    if (!form.name.trim() || !form.environmentProfileId || !form.loginUrl.trim()) {
      Message.warning('请填写必填字段');
      done(false);
      return;
    }
    if (saving.value) {
      done(false);
      return;
    }
    try {
      const login = platformUrl(form.loginUrl);
      const environment = environments.value.find((item) => item.id === form.environmentProfileId);
      if (
        !environment ||
        !environment.allowedOrigins.map((value) => platformUrl(value, true).origin).includes(login.origin)
      ) {
        throw new Error('登录地址必须属于所选平台允许的 Origin');
      }
      if (!form.timeoutMs || form.timeoutMs < 1000 || form.timeoutMs > 60000)
        throw new Error('超时必须在 1000～60000ms 之间');
    } catch (failure) {
      Message.warning((failure as Error).message);
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
        validateLoginJson(value, '定位器和断言');
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
      Message.success('保存成功');
      done(true);
      await load();
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
  watch(
    () => app.currentProjectId,
    () => {
      visible.value = false;
      items.value = [];
      environments.value = [];
      load();
    }
  );
  onMounted(() => {
    if (app.currentProjectId) load();
  });
  useListRefresh(load, () => !!app.currentProjectId && !loading.value && !visible.value);
  onBeforeUnmount(() => {
    loadSequence += 1;
  });
</script>
