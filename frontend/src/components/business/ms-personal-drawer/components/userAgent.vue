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
      <a-alert v-if="unsupportedDevices.length" type="error" class="mb-4">
        {{ t('ms.personal.userAgent.versionUnsupported', { version: installInfo?.minimumVersion }) }}
      </a-alert>
      <div class="grid grid-cols-1 gap-3 lg:grid-cols-3">
        <a-card v-for="provider in visibleProviders" :key="provider.id" :title="provider.name" :bordered="true">
          <template #extra><a-tag v-if="provider.experimental" color="orange">Experimental</a-tag></template>
          <div class="mb-3 text-sm text-[var(--color-text-3)]">{{ t(provider.descriptionKey) }}</div>
          <div v-if="connection(provider.id)" class="mb-3 space-y-1 text-sm">
            <div>{{ t('ms.personal.userAgent.status') }}: {{ connection(provider.id)?.status }}</div>
            <div>{{ t('ms.personal.userAgent.device') }}: {{ connection(provider.id)?.deviceName || '-' }}</div>
            <div>{{ t('ms.personal.userAgent.account') }}: {{ connection(provider.id)?.maskedAccount || '-' }}</div>
          </div>
          <a-select
            v-if="!connection(provider.id) && onlineDevices.length > 1"
            v-model="selectedDeviceIds[provider.id]"
            class="mb-3 w-full"
            :placeholder="t('ms.personal.userAgent.selectDevice')"
          >
            <a-option v-for="device in onlineDevices" :key="device.id" :value="device.id">
              {{ device.deviceName }} · {{ device.bridgeVersion || '-' }}
            </a-option>
          </a-select>
          <div class="flex flex-wrap gap-2">
            <a-button
              v-if="!connection(provider.id)"
              v-permission="['SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT']"
              type="primary"
              @click="createAndAuthorize(provider.id)"
            >
              {{ t('ms.personal.userAgent.connect') }}
            </a-button>
            <a-button
              v-if="connection(provider.id)"
              v-permission="['SYSTEM_PERSONAL_AI_AGENT:READ']"
              @click="openConnectionDetail(connection(provider.id)!.id)"
            >
              {{ t('ms.personal.userAgent.details') }}
            </a-button>
            <a-button
              v-if="connection(provider.id) && connection(provider.id)?.status !== 'CONNECTED'"
              v-permission="['SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT']"
              type="primary"
              @click="authorizeConnection(connection(provider.id)!.id)"
            >
              {{ t('ms.personal.userAgent.authorize') }}
            </a-button>
            <a-button
              v-if="connection(provider.id)"
              v-permission="['SYSTEM_PERSONAL_AI_AGENT:READ+REVOKE']"
              status="danger"
              @click="revokeConnection(connection(provider.id)!.id)"
            >
              {{ t('ms.personal.userAgent.revoke') }}
            </a-button>
            <a-button
              v-if="connection(provider.id)?.status === 'CONNECTED'"
              v-permission="['SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT']"
              @click="authorizeConnection(connection(provider.id)!.id)"
            >
              {{ t('ms.personal.userAgent.reauthorize') }}
            </a-button>
            <a-button
              v-if="connection(provider.id)?.status === 'REVOKED'"
              v-permission="['SYSTEM_PERSONAL_AI_AGENT:READ+REVOKE']"
              status="danger"
              type="outline"
              @click="removeConnection(connection(provider.id)!.id)"
              >{{ t('common.delete') }}</a-button
            >
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
              <a-button
                v-permission="['SYSTEM_PERSONAL_AI_AGENT:READ+REVOKE']"
                type="text"
                status="danger"
                @click="revokeDevice(record.id)"
              >
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
      <a-alert
        v-if="
          wizardStep === 2 && installInfo && !installInfo.managedDownloadAvailable && !installInfo.windowsDownloadUrl
        "
        type="warning"
        class="mb-4"
      >
        {{ t('ms.personal.userAgent.downloadUnavailable') }}
      </a-alert>
      <div class="flex justify-end gap-2">
        <a-button @click="closeWizard">{{ t('common.cancel') }}</a-button>
        <a-button
          v-if="wizardStep === 2"
          :disabled="!installInfo?.managedDownloadAvailable && !installInfo?.windowsDownloadUrl"
          :loading="downloadLoading"
          @click="downloadAgent"
        >
          {{ t('ms.personal.userAgent.downloadInstall') }}
        </a-button>
        <a-button v-if="wizardStep === 2" type="primary" @click="launchAgent">
          {{ t('ms.personal.userAgent.installedRetry') }}
        </a-button>
      </div>
    </a-modal>

    <a-modal v-model:visible="detailVisible" :title="t('ms.personal.userAgent.detailTitle')" :footer="false">
      <a-descriptions v-if="connectionDetail" :column="1" bordered>
        <a-descriptions-item :label="t('ms.personal.userAgent.status')">{{
          connectionDetail.status
        }}</a-descriptions-item>
        <a-descriptions-item :label="t('ms.personal.userAgent.device')">{{
          connectionDetail.deviceName || '-'
        }}</a-descriptions-item>
        <a-descriptions-item :label="t('ms.personal.userAgent.account')">{{
          connectionDetail.maskedAccount || '-'
        }}</a-descriptions-item>
        <a-descriptions-item :label="t('ms.personal.userAgent.capabilities')">{{
          formatCapabilities(connectionDetail.capabilities)
        }}</a-descriptions-item>
        <a-descriptions-item :label="t('ms.personal.userAgent.references')">
          {{ t('ms.personal.userAgent.deleteImpact', { ...(connectionDetailImpact || emptyImpact) }) }}
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
  import { Message, Modal } from '@arco-design/web-vue';

  import {
    authorizeUserAgentConnection,
    createAgentBridgePairing,
    createUserAgentConnection,
    deleteUserAgentConnection,
    downloadManagedAgentBridge,
    getAgentBridgeInstallInfo,
    getAgentBridgePairingStatus,
    getUserAgentConnection,
    getUserAgentConnectionImpact,
    getUserAgentFeatures,
    listAgentBridgeDevices,
    listUserAgentConnections,
    revokeAgentBridgeDevice,
    revokeUserAgentConnection,
  } from '@/api/modules/setting/userAgent';
  import { useI18n } from '@/hooks/useI18n';
  import { downloadByteFile } from '@/utils';

  import type {
    AgentBridgeDevice,
    AgentBridgeInstallInfo,
    UserAgentConnection,
    UserAgentConnectionImpact,
    UserAgentFeatureFlags,
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
  const features = ref<UserAgentFeatureFlags>({ enabled: false, workbuddy: false, codex: false, cursor: false });
  const downloadLoading = ref(false);
  const pendingProvider = ref<UserAgentProvider>();
  const pendingPairingId = ref('');
  const pendingPairingCode = ref('');
  const pendingPairingExpiry = ref(0);
  const pendingDeviceId = ref('');
  const pendingConnectionId = ref('');
  const selectedDeviceIds = reactive<Partial<Record<UserAgentProvider, string>>>({});
  const detailVisible = ref(false);
  const connectionDetail = ref<UserAgentConnection>();
  const connectionDetailImpact = ref<UserAgentConnectionImpact>();
  const emptyImpact: UserAgentConnectionImpact = { conversationCount: 0, executionCount: 0, activeExecutionCount: 0 };
  let setupCompleting = false;
  let pollingTimer: number | undefined;
  let launchFallbackTimer: number | undefined;
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
  const visibleProviders = computed(() =>
    providerDefinitions.filter(
      (provider) => features.value[provider.id.toLowerCase() as 'workbuddy' | 'codex' | 'cursor']
    )
  );
  function versionAtLeast(current?: string, minimum?: string) {
    const parse = (value = '') =>
      value
        .replace(/^[vV]/, '')
        .split(/[.-]/)
        .map((part) => Number(part) || 0);
    const left = parse(current);
    const right = parse(minimum);
    for (let i = 0; i < Math.max(left.length, right.length); i += 1) {
      if ((left[i] || 0) !== (right[i] || 0)) return (left[i] || 0) > (right[i] || 0);
    }
    return true;
  }
  const unsupportedDevices = computed(() =>
    devices.value.filter(
      (item) => item.status !== 'REVOKED' && !versionAtLeast(item.bridgeVersion, installInfo.value?.minimumVersion)
    )
  );
  const onlineDevices = computed(() =>
    devices.value.filter(
      (item) => item.status === 'ONLINE' && versionAtLeast(item.bridgeVersion, installInfo.value?.minimumVersion)
    )
  );
  const connection = (provider: UserAgentProvider) => connections.value.find((item) => item.provider === provider);

  async function reload(silent = false) {
    if (!silent) loading.value = true;
    try {
      [features.value, connections.value, devices.value, installInfo.value] = await Promise.all([
        getUserAgentFeatures(),
        listUserAgentConnections(),
        listAgentBridgeDevices(),
        getAgentBridgeInstallInfo(),
      ]);
      if (pendingPairingId.value && !pendingDeviceId.value) {
        const pairing = await getAgentBridgePairingStatus(pendingPairingId.value);
        if (pairing.status === 'CONSUMED' && pairing.deviceId) pendingDeviceId.value = pairing.deviceId;
        if (pairing.status === 'EXPIRED') wizardError.value = t('ms.personal.userAgent.setupExpired');
      }
      visibleProviders.value.forEach((provider) => {
        if (!onlineDevices.value.some((device) => device.id === selectedDeviceIds[provider.id])) {
          selectedDeviceIds[provider.id] = onlineDevices.value[0]?.id;
        }
      });
      // eslint-disable-next-line no-use-before-define
      await continueSetupIfReady();
    } finally {
      loading.value = false;
    }
  }

  function persistSetup() {
    if (!pendingProvider.value || !pendingPairingId.value) return;
    sessionStorage.setItem(
      'ms-user-agent-setup',
      JSON.stringify({
        provider: pendingProvider.value,
        pairingId: pendingPairingId.value,
        expiresAt: pendingPairingExpiry.value,
        deviceId: pendingDeviceId.value || undefined,
        connectionId: pendingConnectionId.value || undefined,
      })
    );
  }

  async function startPairing(provider: UserAgentProvider) {
    const pairing = await createAgentBridgePairing({ provider });
    pendingPairingId.value = pairing.pairingId;
    pendingPairingCode.value = pairing.pairingCode;
    pendingPairingExpiry.value = pairing.expiresAt;
    persistSetup();
    return pairing;
  }

  async function createAndAuthorize(provider: UserAgentProvider) {
    const device =
      onlineDevices.value.find((item) => item.id === selectedDeviceIds[provider]) ||
      (onlineDevices.value.length === 1 ? onlineDevices.value[0] : undefined);
    if (!device) {
      // eslint-disable-next-line no-use-before-define
      await beginSetup(provider);
      return;
    }
    const created = await createUserAgentConnection({ provider, deviceId: device.id });
    pendingProvider.value = provider;
    pendingDeviceId.value = device.id;
    pendingConnectionId.value = created.id;
    wizardVisible.value = true;
    wizardStep.value = 3;
    wizardMessage.value = t('ms.personal.userAgent.startingSignIn');
    await authorizeUserAgentConnection(created.id);
    Message.info(t('ms.personal.userAgent.authorizationStarted'));
    persistSetup();
    await reload(true);
  }

  async function beginSetup(provider: UserAgentProvider) {
    window.clearTimeout(launchFallbackTimer);
    wizardVisible.value = true;
    wizardStep.value = 1;
    wizardError.value = '';
    wizardMessage.value = t('ms.personal.userAgent.detectingMessage');
    pendingProvider.value = provider;
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
    window.clearTimeout(launchFallbackTimer);
    if (!pendingPairingCode.value || Date.now() >= pendingPairingExpiry.value) {
      wizardError.value = t('ms.personal.userAgent.setupExpired');
      return;
    }
    wizardError.value = '';
    const scheme = installInfo.value?.protocolScheme || 'metersphere-agent';
    const query = new URLSearchParams({
      platformUrl: window.location.origin,
      pairingCode: pendingPairingCode.value,
      provider: pendingProvider.value || '',
    });
    window.location.href = `${scheme}://pair?${query.toString()}`;
    wizardMessage.value = t('ms.personal.userAgent.waitingForAgent');
    launchFallbackTimer = window.setTimeout(() => {
      if (wizardVisible.value && wizardStep.value === 2 && onlineDevices.value.length === 0) {
        wizardMessage.value =
          installInfo.value?.managedDownloadAvailable || installInfo.value?.windowsDownloadUrl
            ? t('ms.personal.userAgent.protocolLaunchFallback')
            : t('ms.personal.userAgent.downloadUnavailable');
      }
    }, 3000);
  }

  async function downloadAgent() {
    if (!installInfo.value?.managedDownloadAvailable && !installInfo.value?.windowsDownloadUrl) {
      wizardError.value = t('ms.personal.userAgent.downloadUnavailable');
      return;
    }
    downloadLoading.value = true;
    try {
      if (installInfo.value.managedDownloadAvailable) {
        const bytes = await downloadManagedAgentBridge();
        downloadByteFile(bytes, 'metersphere-agent-windows-x64.zip');
      } else {
        window.open(installInfo.value.windowsDownloadUrl, '_blank', 'noopener');
      }
      wizardMessage.value = t('ms.personal.userAgent.downloadStarted');
    } finally {
      downloadLoading.value = false;
    }
  }

  async function continueSetupIfReady() {
    if (!wizardVisible.value || !pendingProvider.value || setupCompleting) return;
    if (pendingConnectionId.value) {
      const current = connections.value.find((item) => item.id === pendingConnectionId.value);
      if (current?.status === 'CONNECTED') {
        sessionStorage.removeItem('ms-user-agent-setup');
        wizardStep.value = 4;
        wizardMessage.value = t('ms.personal.userAgent.completedMessage');
        pendingPairingCode.value = '';
      }
      return;
    }
    const device = onlineDevices.value.find((item) => item.id === pendingDeviceId.value);
    if (!device) return;
    setupCompleting = true;
    window.clearTimeout(launchFallbackTimer);
    wizardStep.value = 3;
    wizardMessage.value = t('ms.personal.userAgent.startingSignIn');
    try {
      const created = await createUserAgentConnection({ provider: pendingProvider.value, deviceId: device.id });
      pendingConnectionId.value = created.id;
      await authorizeUserAgentConnection(created.id);
      wizardMessage.value = t('ms.personal.userAgent.authorizationStarted');
      pendingPairingCode.value = '';
      persistSetup();
    } catch (error) {
      wizardError.value = String((error as Error).message || error);
    } finally {
      setupCompleting = false;
    }
  }

  function closeWizard() {
    window.clearTimeout(launchFallbackTimer);
    wizardVisible.value = false;
    pendingPairingCode.value = '';
    pendingPairingId.value = '';
    pendingDeviceId.value = '';
    pendingConnectionId.value = '';
    sessionStorage.removeItem('ms-user-agent-setup');
  }

  async function revokeConnection(id: string) {
    await revokeUserAgentConnection(id);
    await reload(true);
  }
  async function removeConnection(id: string) {
    const impact = await getUserAgentConnectionImpact(id);
    Modal.warning({
      title: t('ms.personal.userAgent.deleteTitle'),
      content: t('ms.personal.userAgent.deleteImpact', { ...impact }),
      hideCancel: false,
      onOk: async () => {
        await deleteUserAgentConnection(id);
        await reload(true);
      },
    });
  }
  function formatCapabilities(value?: string) {
    if (!value) return '-';
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed.join('、') || '-' : value;
    } catch {
      return value;
    }
  }
  async function openConnectionDetail(id: string) {
    [connectionDetail.value, connectionDetailImpact.value] = await Promise.all([
      getUserAgentConnection(id),
      getUserAgentConnectionImpact(id),
    ]);
    detailVisible.value = true;
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
        const saved = JSON.parse(savedSetup) as {
          provider: UserAgentProvider;
          pairingId: string;
          expiresAt: number;
          deviceId?: string;
          connectionId?: string;
        };
        if (saved.expiresAt > Date.now()) {
          pendingProvider.value = saved.provider;
          pendingPairingId.value = saved.pairingId;
          pendingPairingExpiry.value = saved.expiresAt;
          pendingDeviceId.value = saved.deviceId || '';
          pendingConnectionId.value = saved.connectionId || '';
          wizardVisible.value = true;
          wizardStep.value = saved.connectionId ? 3 : 2;
          wizardMessage.value = saved.connectionId
            ? t('ms.personal.userAgent.startingSignIn')
            : t('ms.personal.userAgent.waitingForAgent');
        } else sessionStorage.removeItem('ms-user-agent-setup');
      } catch {
        sessionStorage.removeItem('ms-user-agent-setup');
      }
    }
    reload();
    pollingTimer = window.setInterval(() => reload(true), 5_000);
  });
  onBeforeUnmount(() => {
    window.clearInterval(pollingTimer);
    window.clearTimeout(launchFallbackTimer);
  });
</script>
