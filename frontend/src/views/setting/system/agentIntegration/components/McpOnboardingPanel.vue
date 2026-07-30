<template>
  <MsCard simple class="mb-4">
    <div class="mb-3 flex flex-wrap items-start justify-between gap-3">
      <div>
        <div class="text-base font-medium">{{ t('system.agentIntegration.mcpTitle') }}</div>
        <div class="mt-1 text-sm text-[var(--color-text-3)]">
          {{ t('system.agentIntegration.mcpDesc') }}
        </div>
        <div v-if="manifest" class="mt-2 text-xs text-[var(--color-text-3)]">
          <span v-if="manifest.available">
            {{ t('system.agentIntegration.mcpVersion', { version: manifest.version || '-' }) }}
            · Node {{ manifest.nodeEngine || '>=18' }}
          </span>
          <span v-else class="text-[rgb(var(--danger-6))]">
            {{ manifest.description || t('system.agentIntegration.mcpUnavailable') }}
          </span>
        </div>
      </div>
      <div class="flex flex-wrap gap-2">
        <a-button
          v-permission="['SYSTEM_USER:READ']"
          :loading="downloadLoading"
          :disabled="!manifest?.available"
          type="primary"
          @click="handleDownload"
        >
          {{ t('system.agentIntegration.mcpDownload') }}
        </a-button>
        <a-button @click="copyConfig">{{ t('system.agentIntegration.mcpCopyConfig') }}</a-button>
      </div>
    </div>

    <a-form :model="form" layout="vertical" class="max-w-[720px]">
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item :label="t('system.agentIntegration.mcpBaseUrl')">
            <a-input v-model="form.baseUrl" :placeholder="defaultBaseUrl" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item :label="t('system.agentIntegration.defaultProjectId')">
            <a-input v-model="form.projectId" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item :label="t('system.agentIntegration.mcpToken')">
            <a-input-password v-model="form.token" :placeholder="t('system.agentIntegration.mcpTokenPlaceholder')" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item :label="t('system.agentIntegration.mcpTestPlanId')">
            <a-input v-model="form.testPlanId" allow-clear />
          </a-form-item>
        </a-col>
        <a-col :span="24">
          <a-form-item :label="t('system.agentIntegration.mcpDistPath')">
            <a-input v-model="form.distPath" :placeholder="t('system.agentIntegration.mcpDistPathPlaceholder')" />
          </a-form-item>
        </a-col>
      </a-row>
    </a-form>

    <a-alert type="info" class="mb-3">
      {{ t('system.agentIntegration.mcpHint') }}
    </a-alert>

    <div class="mb-1 text-sm font-medium">{{ t('system.agentIntegration.mcpScopeHelp') }}</div>
    <ul class="mb-0 list-disc pl-5 text-sm text-[var(--color-text-3)]">
      <li>AGENT_ALL — {{ t('system.agentIntegration.scopeAgentAll') }}</li>
      <li>BUG_READ / BUG_WRITE — {{ t('system.agentIntegration.scopeBug') }}</li>
      <li>FUNCTIONAL_ALL / CASE_WRITE / PLAN_WRITE / REVIEW_WRITE — {{ t('system.agentIntegration.scopeCase') }}</li>
    </ul>
  </MsCard>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import MsCard from '@/components/pure/ms-card/index.vue';

  import {
    type AgentMcpManifest,
    downloadAgentMcpBundle,
    getAgentMcpManifest,
  } from '@/api/modules/setting/agentIntegration';
  import { useI18n } from '@/hooks/useI18n';
  import { downloadByteFile } from '@/utils';

  const props = defineProps<{
    presetToken?: string;
    presetProjectId?: string;
  }>();

  const { t } = useI18n();

  const defaultBaseUrl = typeof window !== 'undefined' ? window.location.origin : '';
  const manifest = ref<AgentMcpManifest>();
  const downloadLoading = ref(false);

  const form = reactive({
    baseUrl: defaultBaseUrl,
    projectId: props.presetProjectId || '',
    token: props.presetToken || '',
    testPlanId: '',
    distPath: 'metersphere-mcp/dist/index.js',
  });

  function buildMcpJson() {
    const baseUrl = (form.baseUrl || defaultBaseUrl).replace(/\/$/, '');
    return JSON.stringify(
      {
        mcpServers: {
          metersphere: {
            command: 'node',
            args: [form.distPath || 'metersphere-mcp/dist/index.js'],
            env: {
              MS_BASE_URL: baseUrl,
              MS_AGENT_TOKEN: form.token || 'msat_YOUR_TOKEN',
              MS_PROJECT_ID: form.projectId || 'your-project-id',
              MS_TEST_PLAN_ID: form.testPlanId || '',
            },
          },
        },
      },
      null,
      2
    );
  }

  async function copyConfig() {
    await navigator.clipboard.writeText(buildMcpJson());
    Message.success(t('system.agentIntegration.mcpCopySuccess'));
  }

  async function handleDownload() {
    downloadLoading.value = true;
    try {
      const blob = await downloadAgentMcpBundle();
      const name = manifest.value?.fileName || 'metersphere-mcp.zip';
      downloadByteFile(blob, name);
      Message.success(t('system.agentIntegration.mcpDownloadSuccess'));
    } finally {
      downloadLoading.value = false;
    }
  }

  function applyPreset(token?: string, projectId?: string) {
    if (token) form.token = token;
    if (projectId) form.projectId = projectId;
  }

  defineExpose({ applyPreset, copyConfig, buildMcpJson });

  onMounted(async () => {
    try {
      manifest.value = await getAgentMcpManifest();
    } catch {
      manifest.value = { available: false, description: t('system.agentIntegration.mcpUnavailable') };
    }
  });
</script>
