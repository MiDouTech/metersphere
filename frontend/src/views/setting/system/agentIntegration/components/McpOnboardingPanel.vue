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
      <a-button
        v-permission="['SYSTEM_USER:READ']"
        :loading="downloadLoading"
        :disabled="!manifest?.available"
        type="primary"
        @click="handleDownload"
      >
        {{ t('system.agentIntegration.mcpDownload') }}
      </a-button>
    </div>

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
  import { onMounted, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import MsCard from '@/components/pure/ms-card/index.vue';

  import {
    type AgentMcpManifest,
    downloadAgentMcpBundle,
    getAgentMcpManifest,
  } from '@/api/modules/setting/agentIntegration';
  import { useI18n } from '@/hooks/useI18n';
  import { downloadByteFile } from '@/utils';

  const { t } = useI18n();

  const manifest = ref<AgentMcpManifest>();
  const downloadLoading = ref(false);

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

  onMounted(async () => {
    try {
      manifest.value = await getAgentMcpManifest();
    } catch {
      manifest.value = { available: false, description: t('system.agentIntegration.mcpUnavailable') };
    }
  });
</script>
