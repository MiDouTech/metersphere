<template>
  <AgentPage>
    <MsCard simple>
      <div class="mb-4 flex items-center justify-between">
        <div
          ><div class="text-base font-medium">MAP Gateway 模型配置</div
          ><div class="mt-1 text-sm text-[var(--color-text-3)]"
            >平台仅保存 Gateway 逻辑模型和密钥引用，不保存上游 Provider 密钥。</div
          ></div
        >
        <a-space
          ><a-button :loading="loading" @click="load">刷新</a-button
          ><a-button v-permission="['AI_MODEL:MANAGE']" type="primary" @click="open()">新增配置</a-button></a-space
        >
      </div>
      <a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert>
      <a-table :data="items" :loading="loading" :pagination="false" row-key="id">
        <template #empty><a-empty description="暂无模型配置" /></template>
        <template #columns>
          <a-table-column title="名称" data-index="name" />
          <a-table-column title="逻辑模型" data-index="logicalModelPublicId" />
          <a-table-column title="App Caller" data-index="gatewayAppCaller" />
          <a-table-column title="Prompt 策略" data-index="promptPolicyId" />
          <a-table-column title="能力"
            ><template #cell="{ record }">{{
              record.requiredCapabilities?.join(', ') || '-'
            }}</template></a-table-column
          >
          <a-table-column title="验证状态"
            ><template #cell="{ record }"
              ><a-tag
                :color="
                  record.lastVerifyStatus === 'SUCCESS'
                    ? 'green'
                    : record.lastVerifyStatus === 'FAILED'
                    ? 'red'
                    : 'orange'
                "
                >{{ record.lastVerifyStatus || '未验证' }}</a-tag
              ></template
            ></a-table-column
          >
          <a-table-column title="操作" :width="220"
            ><template #cell="{ record }"
              ><a-space
                ><a-button v-permission="['AI_MODEL:VERIFY']" type="text" @click="verify(record)">验证</a-button
                ><a-button v-permission="['AI_MODEL:MANAGE']" type="text" @click="open(record)">编辑</a-button
                ><a-button v-permission="['AI_MODEL:MANAGE']" type="text" @click="toggle(record)">{{
                  record.enabled ? '停用' : '启用'
                }}</a-button></a-space
              ></template
            ></a-table-column
          >
        </template>
      </a-table>
    </MsCard>
    <a-modal
      v-model:visible="visible"
      :title="form.id ? '编辑模型配置' : '新增模型配置'"
      :ok-loading="saving"
      width="720px"
      @before-ok="save"
    >
      <a-form :model="form" layout="vertical">
        <div class="grid grid-cols-2 gap-x-4">
          <a-form-item label="名称" required><a-input v-model="form.name" /></a-form-item>
          <a-form-item label="App Caller" required><a-input v-model="form.gatewayAppCaller" /></a-form-item>
          <a-form-item label="逻辑模型公开 ID" required><a-input v-model="form.logicalModelPublicId" /></a-form-item>
          <a-form-item label="Prompt 策略 ID" required><a-input v-model="form.promptPolicyId" /></a-form-item>
          <a-form-item label="Gateway Prompt 策略"><a-input v-model="form.gatewayPromptPolicyId" /></a-form-item>
          <a-form-item label="币种" required><a-input v-model="form.currency" /></a-form-item>
          <a-form-item label="请求超时(ms)" required
            ><a-input-number v-model="form.requestTimeoutMs" :min="1000" :max="300000" class="w-full"
          /></a-form-item>
          <a-form-item label="最大输出 Token" required
            ><a-input-number v-model="form.maxOutputTokens" :min="1" :max="65536" class="w-full"
          /></a-form-item>
          <a-form-item label="单次最大成本"
            ><a-input-number v-model="form.maxCostAmount" :min="0" class="w-full"
          /></a-form-item>
          <a-form-item label="启用"><a-switch v-model="form.enabled" /></a-form-item>
        </div>
        <a-form-item
          label="Gateway Service Key 引用"
          required
          extra="仅接受受控 Secret Provider 引用，例如 vault://mount/path#field"
          ><a-input-password v-model="form.gatewayServiceKeyRef" autocomplete="new-password"
        /></a-form-item>
        <a-form-item label="必需能力" required
          ><a-select v-model="form.requiredCapabilities" multiple allow-create
            ><a-option value="STRUCTURED_OUTPUT">STRUCTURED_OUTPUT</a-option
            ><a-option value="TOOL_CALLING">TOOL_CALLING</a-option><a-option value="VISION">VISION</a-option></a-select
          ></a-form-item
        >
      </a-form>
    </a-modal>
  </AgentPage>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import AgentPage from './components/AgentPage.vue';

  import type { AiModelProfile, AiModelProfileRequest } from '@/api/modules/ai-execution';
  import {
    createAiModelProfile,
    disableAiModelProfile,
    enableAiModelProfile,
    listAiModelProfiles,
    updateAiModelProfile,
    verifyAiModelProfile,
  } from '@/api/modules/ai-execution';
  import useAppStore from '@/store/modules/app';

  const app = useAppStore();
  const items = ref<AiModelProfile[]>([]);
  const loading = ref(false);
  const saving = ref(false);
  const visible = ref(false);
  const error = ref('');
  const form = reactive({
    id: '',
    name: '',
    gatewayAppCaller: '',
    gatewayServiceKeyRef: '',
    logicalModelPublicId: '',
    promptPolicyId: '',
    gatewayPromptPolicyId: '',
    requiredCapabilities: ['STRUCTURED_OUTPUT'] as string[],
    requestTimeoutMs: 60000,
    maxOutputTokens: 4096,
    maxCostAmount: undefined as number | undefined,
    currency: 'CNY',
    enabled: true,
    version: 0,
  });
  const message = (e: unknown) => (e as { message?: string })?.message || '请求失败，请稍后重试';
  async function load() {
    loading.value = true;
    error.value = '';
    try {
      items.value = await listAiModelProfiles(app.currentProjectId);
    } catch (e) {
      error.value = message(e);
    } finally {
      loading.value = false;
    }
  }
  function open(item?: AiModelProfile) {
    Object.assign(
      form,
      item
        ? {
            ...item,
            gatewayServiceKeyRef: '',
            gatewayPromptPolicyId: item.gatewayPromptPolicyId || '',
            maxCostAmount: item.maxCostAmount,
          }
        : {
            id: '',
            name: '',
            gatewayAppCaller: '',
            gatewayServiceKeyRef: '',
            logicalModelPublicId: '',
            promptPolicyId: '',
            gatewayPromptPolicyId: '',
            requiredCapabilities: ['STRUCTURED_OUTPUT'],
            requestTimeoutMs: 60000,
            maxOutputTokens: 4096,
            maxCostAmount: undefined,
            currency: 'CNY',
            enabled: true,
            version: 0,
          }
    );
    visible.value = true;
  }
  async function save(done: (closed: boolean) => void) {
    if (
      ![
        form.name,
        form.gatewayAppCaller,
        form.gatewayServiceKeyRef,
        form.logicalModelPublicId,
        form.promptPolicyId,
        form.currency,
      ].every((v) => v.trim()) ||
      !form.requiredCapabilities.length
    ) {
      Message.warning('请完整填写必填字段；编辑时也必须提交新的 Service Key 引用');
      done(false);
      return;
    }
    const data: AiModelProfileRequest = {
      projectId: app.currentProjectId,
      name: form.name.trim(),
      gatewayAppCaller: form.gatewayAppCaller.trim(),
      gatewayServiceKeyRef: form.gatewayServiceKeyRef.trim(),
      logicalModelPublicId: form.logicalModelPublicId.trim(),
      promptPolicyId: form.promptPolicyId.trim(),
      gatewayPromptPolicyId: form.gatewayPromptPolicyId.trim() || undefined,
      requiredCapabilities: [...new Set(form.requiredCapabilities)],
      requestTimeoutMs: form.requestTimeoutMs,
      maxOutputTokens: form.maxOutputTokens,
      maxCostAmount: form.maxCostAmount,
      currency: form.currency.trim().toUpperCase(),
      enabled: form.enabled,
      version: form.version,
    };
    saving.value = true;
    try {
      if (form.id) await updateAiModelProfile(form.id, data);
      else await createAiModelProfile(data);
      form.gatewayServiceKeyRef = '';
      Message.success('保存成功');
      await load();
      done(true);
    } catch (e) {
      Message.error(message(e));
      done(false);
    } finally {
      saving.value = false;
    }
  }
  async function verify(item: AiModelProfile) {
    try {
      await verifyAiModelProfile(item.id);
      Message.success('Gateway 连通性及模型能力验证通过');
      await load();
    } catch (e) {
      Message.error(message(e));
      await load();
    }
  }
  async function toggle(item: AiModelProfile) {
    try {
      if (item.enabled) await disableAiModelProfile(item.id);
      else await enableAiModelProfile(item.id);
      Message.success(item.enabled ? '已停用' : '已启用，执行前仍会重新预检');
      await load();
    } catch (e) {
      Message.error(message(e));
    }
  }
  onMounted(load);
</script>
