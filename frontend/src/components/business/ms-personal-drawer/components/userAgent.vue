<template>
  <div class="h-full overflow-auto">
    <div class="mb-4">
      <div class="text-lg font-medium">{{ t('ms.personal.userAgent.title') }}</div>
      <div class="mt-1 text-[var(--color-text-3)]">{{ t('ms.personal.userAgent.description') }}</div>
    </div>

    <a-alert class="mb-4" type="warning" show-icon>
      {{ t('ms.personal.userAgent.securityNotice') }}
    </a-alert>

    <a-spin :loading="loading" class="w-full">
      <div class="grid grid-cols-1 gap-3 lg:grid-cols-3">
        <a-card v-for="provider in visibleProviders" :key="provider.id" :title="provider.name" :bordered="true">
          <template #extra><a-tag v-if="provider.experimental" color="orange">Experimental</a-tag></template>
          <div class="mb-3 text-sm text-[var(--color-text-3)]">{{ t(provider.descriptionKey) }}</div>
          <div v-if="connection(provider.id)" class="mb-3 space-y-1 text-sm">
            <div>{{ t('ms.personal.userAgent.status') }}: {{ connection(provider.id)?.status }}</div>
            <div>{{ t('ms.personal.userAgent.device') }}: {{ connection(provider.id)?.deviceName || '-' }}</div>
            <div>{{ t('ms.personal.userAgent.account') }}: {{ connection(provider.id)?.maskedAccount || '-' }}</div>
          </div>
          <div class="flex flex-wrap gap-2">
            <a-button v-if="!connection(provider.id)" type="primary" @click="createAndAuthorize(provider.id)">
              {{ t('ms.personal.userAgent.connect') }}
            </a-button>
            <a-button
              v-if="connection(provider.id) && connection(provider.id)?.status !== 'CONNECTED'"
              type="primary"
              @click="authorizeConnection(connection(provider.id)!.id)"
            >
              {{ t('ms.personal.userAgent.authorize') }}
            </a-button>
            <a-button
              v-if="connection(provider.id)"
              status="danger"
              @click="revokeConnection(connection(provider.id)!.id)"
            >
              {{ t('ms.personal.userAgent.revoke') }}
            </a-button>
          </div>
        </a-card>
      </div>

      <div class="mb-2 mt-6 text-base font-medium">{{ t('ms.personal.userAgent.devices') }}</div>
      <a-table :data="devices" :pagination="false" row-key="id">
        <template #columns>
          <a-table-column :title="t('ms.personal.userAgent.device')" data-index="deviceName" />
          <a-table-column :title="t('ms.personal.userAgent.os')" data-index="osType" />
          <a-table-column :title="t('ms.personal.userAgent.bridgeVersion')" data-index="bridgeVersion" />
          <a-table-column :title="t('ms.personal.userAgent.status')" data-index="status" />
          <a-table-column :title="t('common.operation')">
            <template #cell="{ record }">
              <a-button type="text" status="danger" @click="revokeDevice(record.id)">
                {{ t('ms.personal.userAgent.revoke') }}
              </a-button>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-spin>

    <a-modal
      v-model:visible="wizardVisible"
      :title="t('ms.personal.userAgent.setupTitle')"
      :footer="false"
      :mask-closable="false"
    >
      <a-steps :current="wizardStep" small class="mb-6">
        <a-step :description="t('ms.personal.userAgent.detecting')" />
        <a-step :description="t('ms.personal.userAgent.installing')" />
        <a-step :description="t('ms.personal.userAgent.signingIn')" />
        <a-step :description="t('ms.personal.userAgent.completed')" />
      </a-steps>
      <a-alert v-if="wizardError" type="error" class="mb-4">{{ wizardError }}</a-alert>
      <div class="mb-4 text-[var(--color-text-2)]">{{ wizardMessage }}</div>
      <div v-if="wizardStep === 2" class="mb-4 rounded bg-[var(--color-fill-2)] p-3 text-sm">
        {{ t('ms.personal.userAgent.installNotice') }}
      </div>
      <div class="flex justify-end gap-2">
        <a-button @click="closeWizard">{{ t('common.cancel') }}</a-button>
        <a-button v-if="wizardStep === 2" :disabled="!installInfo?.windowsDownloadUrl" @click="downloadAgent">
          {{ t('ms.personal.userAgent.downloadInstall') }}
        </a-button>
        <a-button v-if="wizardStep === 2" type="primary" @click="launchAgent">
          {{ t('ms.personal.userAgent.installedRetry') }}
        </a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import {
    authorizeUserAgentConnection,
    createAgentBridgePairing,
    createUserAgentConnection,
    getAgentBridgeInstallInfo,
    listAgentBridgeDevices,
    listUserAgentConnections,
    revokeAgentBridgeDevice,
    revokeUserAgentConnection,
  } from '@/api/modules/setting/userAgent';
  import { useI18n } from '@/hooks/useI18n';

  import type {
    AgentBridgeDevice,
    AgentBridgeInstallInfo,
    UserAgentConnection,
    UserAgentProvider,
  } from '@/models/setting/userAgent';

  const { t } = useI18n();
  const loading = ref(false);
  const connections = ref<UserAgentConnection[]>([]);
  const devices = ref<AgentBridgeDevice[]>([]);
  const wizardVisible = ref(false);
  const wizardStep = ref(1);
  const wizardMessage = ref('');
  const wizardError = ref('');
  const installInfo = ref<AgentBridgeInstallInfo>();
  const pendingProvider = ref<UserAgentProvider>();
  const pendingPairingCode = ref('');
  const pendingPairingExpiry = ref(0);
  let setupCompleting = false;
  let pollingTimer: number | undefined;
  const providerDefinitions = [
    {
      id: 'WORKBUDDY' as const,
      name: 'WorkBuddy',
      experimental: false,
      descriptionKey: 'ms.personal.userAgent.workbuddyDescription',
    },
    {
      id: 'CURSOR' as const,
      name: 'Cursor Agent CLI',
      experimental: true,
      descriptionKey: 'ms.personal.userAgent.cursorDescription',
    },
    {
      id: 'CODEX' as const,
      name: 'ChatGPT',
      experimental: true,
      descriptionKey: 'ms.personal.userAgent.chatgptDescription',
    },
  ];
  const visibleProviders = providerDefinitions;
  const onlineDevices = computed(() => devices.value.filter((item) => item.status === 'ONLINE'));
  const connection = (provider: UserAgentProvider) => connections.value.find((item) => item.provider === provider);

  async function reload(silent = false) {
    if (!silent) loading.value = true;
    try {
      [connections.value, devices.value] = await Promise.all([listUserAgentConnections(), listAgentBridgeDevices()]);
      // eslint-disable-next-line no-use-before-define
      await continueSetupIfReady();
    } finally {
      loading.value = false;
    }
  }

  async function startPairing(provider: UserAgentProvider) {
    const pairing = await createAgentBridgePairing({ provider });
    pendingPairingCode.value = pairing.pairingCode;
    pendingPairingExpiry.value = pairing.expiresAt;
    return pairing;
  }

  async function createAndAuthorize(provider: UserAgentProvider) {
    const device = onlineDevices.value[0];
    if (!device) {
      // eslint-disable-next-line no-use-before-define
      await beginSetup(provider);
      return;
    }
    const created = await createUserAgentConnection({ provider, deviceId: device.id });
    Message.success(t('ms.personal.userAgent.connectionCreated'));
    await authorizeUserAgentConnection(created.id);
    Message.info(t('ms.personal.userAgent.authorizationStarted'));
    await reload(true);
  }

  async function beginSetup(provider: UserAgentProvider) {
    wizardVisible.value = true;
    wizardStep.value = 1;
    wizardError.value = '';
    wizardMessage.value = t('ms.personal.userAgent.detectingMessage');
    pendingProvider.value = provider;
    sessionStorage.setItem('ms-user-agent-setup', JSON.stringify({ provider, expiresAt: Date.now() + 5 * 60_000 }));
    try {
      installInfo.value = await getAgentBridgeInstallInfo();
      await startPairing(provider);
      wizardStep.value = 2;
      wizardMessage.value = t('ms.personal.userAgent.installRequired');
      // eslint-disable-next-line no-use-before-define
      launchAgent();
    } catch (error) {
      wizardError.value = String((error as Error).message || error);
    }
  }

  function launchAgent() {
    if (!pendingPairingCode.value || Date.now() >= pendingPairingExpiry.value) {
      wizardError.value = t('ms.personal.userAgent.setupExpired');
      return;
    }
    const scheme = installInfo.value?.protocolScheme || 'metersphere-agent';
    const query = new URLSearchParams({
      platformUrl: window.location.origin,
      pairingCode: pendingPairingCode.value,
      provider: pendingProvider.value || '',
    });
    window.location.href = `${scheme}://pair?${query.toString()}`;
    wizardMessage.value = t('ms.personal.userAgent.waitingForAgent');
  }

  function downloadAgent() {
    if (installInfo.value?.windowsDownloadUrl) window.open(installInfo.value.windowsDownloadUrl, '_blank', 'noopener');
  }

  async function continueSetupIfReady() {
    if (!wizardVisible.value || !pendingProvider.value || setupCompleting) return;
    const device = onlineDevices.value[0];
    if (!device) return;
    setupCompleting = true;
    wizardStep.value = 3;
    wizardMessage.value = t('ms.personal.userAgent.startingSignIn');
    try {
      const created = await createUserAgentConnection({ provider: pendingProvider.value, deviceId: device.id });
      await authorizeUserAgentConnection(created.id);
      sessionStorage.removeItem('ms-user-agent-setup');
      wizardStep.value = 4;
      wizardMessage.value = t('ms.personal.userAgent.authorizationStarted');
      pendingProvider.value = undefined;
      pendingPairingCode.value = '';
      await reload(true);
    } catch (error) {
      wizardError.value = String((error as Error).message || error);
    } finally {
      setupCompleting = false;
    }
  }

  function closeWizard() {
    wizardVisible.value = false;
    pendingPairingCode.value = '';
    sessionStorage.removeItem('ms-user-agent-setup');
  }

  async function revokeConnection(id: string) {
    await revokeUserAgentConnection(id);
    await reload(true);
  }
  async function authorizeConnection(id: string) {
    await authorizeUserAgentConnection(id);
    Message.info(t('ms.personal.userAgent.authorizationStarted'));
  }
  async function revokeDevice(id: string) {
    await revokeAgentBridgeDevice(id);
    await reload(true);
  }

  onMounted(() => {
    const savedSetup = sessionStorage.getItem('ms-user-agent-setup');
    if (savedSetup) {
      try {
        const saved = JSON.parse(savedSetup) as { provider: UserAgentProvider; expiresAt: number };
        if (saved.expiresAt > Date.now()) {
          pendingProvider.value = saved.provider;
          wizardVisible.value = true;
          wizardStep.value = 2;
          wizardMessage.value = t('ms.personal.userAgent.waitingForAgent');
          getAgentBridgeInstallInfo().then((value) => {
            installInfo.value = value;
          });
        } else sessionStorage.removeItem('ms-user-agent-setup');
      } catch {
        sessionStorage.removeItem('ms-user-agent-setup');
      }
    }
    reload();
    pollingTimer = window.setInterval(() => reload(true), 10_000);
  });
  onBeforeUnmount(() => window.clearInterval(pollingTimer));
</script>
