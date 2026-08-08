<template>
  <section class="mcp-onboarding-summary">
    <div class="min-w-0">
      <div class="text-sm font-medium">{{ t('system.agentIntegration.mcpTitle') }}</div>
      <div class="mt-1 text-sm text-[var(--color-text-3)]">
        {{ t('system.agentIntegration.mcpDesc') }}
      </div>
      <div v-if="manifest" class="mt-1 text-xs text-[var(--color-text-3)]">
        <span v-if="manifest.available">
          {{ t('system.agentIntegration.mcpVersion', { version: manifest.version || '-' }) }}
        </span>
        <span v-else class="text-[rgb(var(--danger-6))]">
          {{ manifest.description || t('system.agentIntegration.mcpUnavailable') }}
        </span>
      </div>
    </div>

    <a-alert type="info" class="my-3">
      {{ t('system.agentIntegration.mcpHint') }}
    </a-alert>

    <a-button type="text" size="small" class="!px-0" @click="detailsVisible = !detailsVisible">
      {{
        detailsVisible
          ? t('system.agentIntegration.hideConnectionDetails')
          : t('system.agentIntegration.showConnectionDetails')
      }}
    </a-button>

    <div v-if="detailsVisible" class="mt-2">
      <div class="mb-3 rounded bg-[var(--color-fill-2)] p-3 text-sm text-[var(--color-text-2)]">
        <div class="mb-1 font-medium text-[var(--color-text-1)]">
          {{ t('system.agentIntegration.platformAddress') }}
        </div>
        <div>{{ t('system.agentIntegration.platformTest') }}: https://msp.ebcone.net</div>
        <div>{{ t('system.agentIntegration.platformProd') }}: https://msp.ebcone.cn</div>
      </div>

      <div class="mb-1 text-sm font-medium">{{ t('system.agentIntegration.mcpScopeHelp') }}</div>
      <ul class="mb-0 list-disc pl-5 text-sm text-[var(--color-text-3)]">
        <li>{{ t('system.agentIntegration.scopeAgentAll') }}: {{ t('system.agentIntegration.scopeAgentAllDesc') }}</li>
        <li>
          {{ t('system.agentIntegration.scopeProjectRead') }}: {{ t('system.agentIntegration.scopeProjectReadDesc') }}
        </li>
        <li>{{ t('system.agentIntegration.scopeCase') }}: {{ t('system.agentIntegration.scopeCaseDesc') }}</li>
        <li>{{ t('system.agentIntegration.scopeBug') }}: {{ t('system.agentIntegration.scopeBugDesc') }}</li>
      </ul>
    </div>
  </section>
</template>

<script setup lang="ts">
  import { ref } from 'vue';

  import type { AgentMcpManifest } from '@/api/modules/setting/agentIntegration';
  import { useI18n } from '@/hooks/useI18n';

  const { t } = useI18n();
  defineProps<{ manifest?: AgentMcpManifest }>();
  const detailsVisible = ref(false);
</script>

<style scoped lang="less">
  .mcp-onboarding-summary {
    padding: 12px;
    min-width: 0;
    max-width: 100%;
    border: 1px solid var(--color-border-2);
    border-radius: var(--border-radius-small);
    background: var(--color-fill-1);
  }
</style>
