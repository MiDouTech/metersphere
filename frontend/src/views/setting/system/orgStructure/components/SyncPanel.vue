<template>
  <div class="flex items-center gap-[12px]">
    <span v-if="statusText" class="text-[12px] text-[var(--color-text-3)]">{{ statusText }}</span>
    <a-button
      v-permission="['SYSTEM_ORGANIZATION_PROJECT:READ+UPDATE', 'ORGANIZATION_MEMBER:READ+UPDATE']"
      type="primary"
      :loading="syncing"
      :disabled="syncing || !organizationId"
      @click="handleManualSync"
    >
      {{ t('orgStructure.sync.manual') }}
    </a-button>
    <a-button :disabled="!organizationId" @click="logDrawerVisible = true">
      {{ t('orgStructure.sync.log') }}
    </a-button>
  </div>

  <MsDrawer
    v-model:visible="logDrawerVisible"
    :width="960"
    :title="t('orgStructure.sync.log.title')"
    :footer="false"
    unmount-on-close
  >
    <div class="mb-[16px]">
      <a-select
        v-model:model-value="logStatusFilter"
        :placeholder="t('orgStructure.sync.log.filterStatus')"
        allow-clear
        class="w-[180px]"
        @change="loadLogList"
      >
        <a-option v-for="item in syncLogStatusOptions" :key="item.value" :value="item.value">
          {{ t(item.label) }}
        </a-option>
      </a-select>
    </div>
    <ms-base-table v-bind="logPropsRes" v-on="logPropsEvent">
      <template #createTime="{ record }">
        {{ formatTime(record.createTime) }}
      </template>
      <template #syncMode="{ record }">
        {{ getSyncModeLabel(record.syncMode) }}
      </template>
      <template #syncStatus="{ record }">
        <a-tag :color="getSyncStatusColor(record.syncStatus)">
          {{ getSyncStatusLabel(record.syncStatus) }}
        </a-tag>
      </template>
      <template #deptStats="{ record }">
        {{ formatStats(record.deptSuccess, record.deptFailed, record.deptTotal) }}
      </template>
      <template #userStats="{ record }">
        {{ formatStats(record.userSuccess, record.userFailed, record.userTotal) }}
      </template>
      <template #durationMs="{ record }">
        {{ record.durationMs != null ? `${record.durationMs}ms` : '-' }}
      </template>
    </ms-base-table>
  </MsDrawer>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import MsDrawer from '@/components/pure/ms-drawer/index.vue';
  import MsBaseTable from '@/components/pure/ms-table/base-table.vue';
  import useTable from '@/components/pure/ms-table/useTable';

  import { getSyncLogPage, getSyncStatus, manualSync } from '@/api/modules/setting/orgStructure';
  import { useI18n } from '@/hooks/useI18n';

  import type { OrgWecomSyncStatus } from '@/models/setting/orgStructure';

  import { SYNC_LOG_STATUS, SYNC_MODE, syncLogStatusOptions, syncLogTableColumns } from '../config';

  const props = defineProps<{
    organizationId: string;
  }>();

  const emit = defineEmits<{
    (e: 'syncComplete'): void;
  }>();

  const { t } = useI18n();
  const syncing = ref(false);
  const syncStatus = ref<OrgWecomSyncStatus>();
  const logDrawerVisible = ref(false);
  const logStatusFilter = ref<string>();

  const {
    propsRes: logPropsRes,
    propsEvent: logPropsEvent,
    loadList: loadLogListInternal,
    setLoadListParams,
  } = useTable(getSyncLogPage, {
    columns: syncLogTableColumns,
    selectable: false,
    showSetting: false,
    heightUsed: 220,
    scroll: { x: '100%' },
  });

  function getSyncStatusLabel(status?: string) {
    if (status === SYNC_LOG_STATUS.SUCCESS) {
      return t('orgStructure.sync.status.success');
    }
    if (status === SYNC_LOG_STATUS.PARTIAL) {
      return t('orgStructure.sync.status.partial');
    }
    if (status === SYNC_LOG_STATUS.FAILED) {
      return t('orgStructure.sync.status.failed');
    }
    return status || '-';
  }

  const statusText = computed(() => {
    if (!props.organizationId) {
      return '';
    }
    if (!syncStatus.value?.syncStatus) {
      return `${t('orgStructure.sync.lastStatus')}: ${t('orgStructure.sync.noRecord')}`;
    }
    const time = syncStatus.value.lastSyncTime || syncStatus.value.logCreateTime;
    const timeText = time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-';
    return `${t('orgStructure.sync.lastStatus')}: ${getSyncStatusLabel(syncStatus.value.syncStatus)} (${timeText})`;
  });

  function formatTime(time?: number) {
    return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-';
  }

  function getSyncStatusColor(status?: string) {
    if (status === SYNC_LOG_STATUS.SUCCESS) {
      return 'green';
    }
    if (status === SYNC_LOG_STATUS.PARTIAL) {
      return 'orange';
    }
    if (status === SYNC_LOG_STATUS.FAILED) {
      return 'red';
    }
    return 'gray';
  }

  function getSyncModeLabel(mode?: string) {
    if (mode === SYNC_MODE.MANUAL) {
      return t('orgStructure.sync.mode.manual');
    }
    if (mode === SYNC_MODE.SCHEDULE) {
      return t('orgStructure.sync.mode.schedule');
    }
    if (mode === SYNC_MODE.LOGIN) {
      return t('orgStructure.sync.mode.login');
    }
    return mode || '-';
  }

  function formatStats(success?: number, failed?: number, total?: number) {
    if (total == null && success == null && failed == null) {
      return '-';
    }
    return `${success ?? 0}/${failed ?? 0}/${total ?? 0}`;
  }

  async function loadStatus() {
    if (!props.organizationId) {
      syncStatus.value = undefined;
      return;
    }
    syncStatus.value = await getSyncStatus(props.organizationId);
  }

  async function loadLogList() {
    if (!props.organizationId) {
      return;
    }
    setLoadListParams({
      organizationId: props.organizationId,
      syncStatus: logStatusFilter.value,
    });
    await loadLogListInternal();
  }

  async function handleManualSync() {
    if (!props.organizationId || syncing.value) {
      return;
    }
    try {
      syncing.value = true;
      const result = await manualSync(props.organizationId);
      if (result.syncStatus === SYNC_LOG_STATUS.FAILED) {
        Message.error(result.errorMessage || t('orgStructure.sync.status.failed'));
      } else {
        Message.success(t('orgStructure.sync.success'));
      }
      await loadStatus();
      emit('syncComplete');
    } catch (error: any) {
      if (error?.response?.status === 409) {
        Message.warning(t('orgStructure.sync.conflict'));
      }
    } finally {
      syncing.value = false;
    }
  }

  watch(
    () => props.organizationId,
    () => {
      loadStatus();
    },
    { immediate: true }
  );

  watch(logDrawerVisible, (visible) => {
    if (visible) {
      loadLogList();
    }
  });

  defineExpose({
    loadStatus,
  });
</script>
