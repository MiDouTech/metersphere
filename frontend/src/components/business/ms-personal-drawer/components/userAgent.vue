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
            <div>{{ t('ms.personal.userAgent.status') }}：{{ connection(provider.id)?.status }}</div>
            <div>{{ t('ms.personal.userAgent.device') }}：{{ connection(provider.id)?.deviceName || '-' }}</div>
            <div>{{ t('ms.personal.userAgent.account') }}：{{ connection(provider.id)?.maskedAccount || '-' }}</div>
          </div>
          <div class="flex flex-wrap gap-2">
            <a-button v-if="!connection(provider.id)" type="primary" @click="startPairing(provider.id)">
              {{ t('ms.personal.userAgent.pair') }}
            </a-button>
            <a-button
              v-if="!connection(provider.id) && onlineDevices.length"
              @click="connectProvider(provider.id, onlineDevices[0].id)"
            >
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
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
  import { Message, Modal } from '@arco-design/web-vue';

  import {
    authorizeUserAgentConnection,
    createAgentBridgePairing,
    createUserAgentConnection,
    getUserAgentFeatures,
    listAgentBridgeDevices,
    listUserAgentConnections,
    revokeAgentBridgeDevice,
    revokeUserAgentConnection,
  } from '@/api/modules/setting/userAgent';
  import { useI18n } from '@/hooks/useI18n';

  import type {
    AgentBridgeDevice,
    UserAgentConnection,
    UserAgentFeatureFlags,
    UserAgentProvider,
  } from '@/models/setting/userAgent';

  const { t } = useI18n();
  const loading = ref(false);
  const flags = ref<UserAgentFeatureFlags>({ enabled: false, workbuddy: false, codex: false, cursor: false });
  const connections = ref<UserAgentConnection[]>([]);
  const devices = ref<AgentBridgeDevice[]>([]);
  let pollingTimer: number | undefined;
  const providerDefinitions = [
    {
      id: 'WORKBUDDY' as const,
      flag: 'workbuddy' as const,
      name: 'WorkBuddy',
      experimental: false,
      descriptionKey: 'ms.personal.userAgent.workbuddyDescription',
    },
    {
      id: 'CODEX' as const,
      flag: 'codex' as const,
      name: 'OpenAI Codex CLI',
      experimental: true,
      descriptionKey: 'ms.personal.userAgent.codexDescription',
    },
    {
      id: 'CURSOR' as const,
      flag: 'cursor' as const,
      name: 'Cursor Agent CLI',
      experimental: true,
      descriptionKey: 'ms.personal.userAgent.cursorDescription',
    },
  ];
  const visibleProviders = computed(() => providerDefinitions.filter((item) => flags.value[item.flag]));
  const onlineDevices = computed(() => devices.value.filter((item) => item.status === 'ONLINE'));
  const connection = (provider: UserAgentProvider) => connections.value.find((item) => item.provider === provider);

  async function reload(silent = false) {
    if (!silent) loading.value = true;
    try {
      flags.value = await getUserAgentFeatures();
      if (flags.value.enabled) {
        [connections.value, devices.value] = await Promise.all([listUserAgentConnections(), listAgentBridgeDevices()]);
      }
    } finally {
      loading.value = false;
    }
  }

  async function startPairing(provider: UserAgentProvider) {
    const pairing = await createAgentBridgePairing({ provider });
    Modal.info({
      title: t('ms.personal.userAgent.pairingCode'),
      content: `${pairing.pairingCode}\n${t('ms.personal.userAgent.pairingExpiry')}`,
      okText: t('common.confirm'),
    });
  }

  async function connectProvider(provider: UserAgentProvider, deviceId: string) {
    await createUserAgentConnection({ provider, deviceId });
    Message.success(t('ms.personal.userAgent.connectionCreated'));
    await reload(true);
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
    reload();
    pollingTimer = window.setInterval(() => reload(true), 10_000);
  });
  onBeforeUnmount(() => window.clearInterval(pollingTimer));
</script>
