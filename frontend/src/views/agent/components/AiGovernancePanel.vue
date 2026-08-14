<template>
  <MsCard simple>
    <div class="mb-4 flex items-center justify-between">
      <div>
        <div class="text-base font-medium">项目 AI 治理</div>
        <div class="mt-1 text-sm text-[var(--color-text-3)]">这些规则由服务端在任务准入和工具调用时强制执行。</div>
      </div>
      <a-space>
        <a-button :loading="loading" @click="load">刷新</a-button>
        <a-button v-permission="['FUNCTIONAL_CASE_AI:CONFIG']" type="primary" :loading="saving" @click="save"
          >保存策略</a-button
        >
      </a-space>
    </div>
    <a-spin :loading="loading">
      <a-form :model="form" layout="vertical">
        <div class="grid gap-x-4 md:grid-cols-2 xl:grid-cols-3">
          <a-form-item label="允许的资源类型">
            <a-checkbox-group v-model="form.allowedResourceTypes">
              <a-checkbox value="MODEL_API">平台模型 API</a-checkbox>
              <a-checkbox value="USER_AGENT">个人 Agent</a-checkbox>
            </a-checkbox-group>
          </a-form-item>
          <a-form-item label="个人 Agent">
            <a-switch v-model="form.allowPersonalAgent" />
          </a-form-item>
          <a-form-item label="允许本地 Agent 工具">
            <a-switch v-model="form.allowLocalAgentTools" :disabled="!form.allowPersonalAgent" />
          </a-form-item>
          <a-form-item label="Agent Provider 白名单">
            <a-checkbox-group v-model="form.allowedAgentProviders" :disabled="!form.allowPersonalAgent">
              <a-checkbox value="CODEX">Codex</a-checkbox>
              <a-checkbox value="CURSOR">Cursor</a-checkbox>
              <a-checkbox value="WORKBUDDY">WorkBuddy</a-checkbox>
            </a-checkbox-group>
          </a-form-item>
          <a-form-item label="Agent 并发上限"
            ><a-input-number v-model="form.maxAgentConcurrentTasks" :min="1" :max="20"
          /></a-form-item>
          <a-form-item label="Agent 单次最长分钟"
            ><a-input-number v-model="form.maxAgentExecutionMinutes" :min="1" :max="240"
          /></a-form-item>
          <a-form-item label="用户每日 Agent 次数"
            ><a-input-number v-model="form.dailyAgentExecutionLimit" :min="1" :max="10000"
          /></a-form-item>
          <a-form-item label="项目 AI 总并发"
            ><a-input-number v-model="form.maxConcurrentTasks" :min="1" :max="100"
          /></a-form-item>
          <a-form-item label="每月 Token 配额"
            ><a-input-number v-model="form.monthlyTokenQuota" :min="1"
          /></a-form-item>
          <a-form-item label="项目文件配额（字节）"
            ><a-input-number v-model="form.projectFileQuota" :min="1"
          /></a-form-item>
          <a-form-item label="会话文件数量"
            ><a-input-number v-model="form.sessionFileLimit" :min="1" :max="1000"
          /></a-form-item>
          <a-form-item label="单文件上限（字节）"
            ><a-input-number v-model="form.singleFileLimit" :min="1"
          /></a-form-item>
        </div>
        <a-form-item label="模型白名单（留空表示使用全部已启用模型）">
          <a-input-tag v-model="form.allowedModelIds" placeholder="输入模型 source ID 后回车" allow-clear />
        </a-form-item>
        <a-form-item label="回退模型 source ID">
          <a-input v-model="form.fallbackModelId" allow-clear placeholder="必须属于上方白名单" />
        </a-form-item>
        <a-alert type="info">
          当前活动任务 {{ form.activeTasks }}；本月已用 Token {{ form.usedTokens }}；来源文件占用
          {{ form.usedFileBytes }} 字节。
        </a-alert>
      </a-form>
    </a-spin>
  </MsCard>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import { getAiProjectGovernance, saveAiProjectGovernance } from '@/api/modules/setting/aiGovernance';
  import { useAppStore } from '@/store';

  import type { AiProjectGovernance } from '@/models/setting/aiGovernance';

  const appStore = useAppStore();
  const loading = ref(false);
  const saving = ref(false);
  const form = reactive<AiProjectGovernance>({
    projectId: '',
    allowedModelIds: [],
    allowedResourceTypes: ['MODEL_API'],
    allowedAgentProviders: [],
    allowPersonalAgent: false,
    allowLocalAgentTools: false,
    maxAgentConcurrentTasks: 1,
    maxAgentExecutionMinutes: 15,
    dailyAgentExecutionLimit: 50,
    maxConcurrentTasks: 3,
    monthlyTokenQuota: 1000000,
    projectFileQuota: 1073741824,
    sessionFileLimit: 20,
    singleFileLimit: 52428800,
    usedTokens: 0,
    usedFileBytes: 0,
    activeTasks: 0,
  });
  async function load() {
    if (!appStore.currentProjectId) return;
    loading.value = true;
    try {
      Object.assign(form, await getAiProjectGovernance(appStore.currentProjectId));
    } finally {
      loading.value = false;
    }
  }
  async function save() {
    saving.value = true;
    try {
      form.projectId = appStore.currentProjectId;
      if (!form.allowPersonalAgent) {
        form.allowLocalAgentTools = false;
        form.allowedResourceTypes = form.allowedResourceTypes.filter((item) => item !== 'USER_AGENT');
        form.allowedAgentProviders = [];
      }
      Object.assign(form, await saveAiProjectGovernance({ ...form }));
      Message.success('项目 AI 治理策略已保存');
    } finally {
      saving.value = false;
    }
  }
  watch(() => appStore.currentProjectId, load);
  onMounted(load);
</script>
