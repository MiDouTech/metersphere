<template>
  <div class="flex items-center gap-[12px]">
    <span v-if="statusText" class="text-[12px] text-[var(--color-text-3)]">{{ statusText }}</span>
    <a-badge v-if="pendingConflictCount > 0" :count="pendingConflictCount" :max-count="99">
      <a-button :disabled="!organizationId" @click="openConflictModal">
        {{ t('orgStructure.sync.emailConflict.entry') }}
      </a-button>
    </a-badge>
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

  <a-modal
    v-model:visible="conflictModalVisible"
    :title="t('orgStructure.sync.emailConflict.title')"
    :footer="false"
    :width="820"
    unmount-on-close
  >
    <a-alert type="warning" class="mb-3">{{ t('orgStructure.sync.emailConflict.tip') }}</a-alert>
    <a-spin :loading="conflictLoading" class="w-full">
      <a-empty v-if="!conflictLoading && conflicts.length === 0" />
      <div v-for="item in conflicts" :key="item.id" class="mb-3 rounded border border-[var(--color-text-n8)] p-3">
        <div class="mb-2 text-[13px] text-[var(--color-text-1)]">
          {{ t('orgStructure.sync.emailConflict.wecomUser') }}: {{ item.wecomUserName || item.wecomUserid }} ({{
            item.wecomUserid
          }})
        </div>
        <div class="mb-2 text-[13px]"> {{ t('orgStructure.sync.emailConflict.email') }}: {{ item.conflictEmail }} </div>
        <div class="mb-3 text-[13px]">
          {{ t('orgStructure.sync.emailConflict.occupied') }}:
          {{ item.occupiedUserName || item.occupiedUserId }}
        </div>
        <div class="flex gap-2">
          <a-button size="small" :loading="resolvingId === item.id" @click="resolveConflict(item, 'SKIP')">
            {{ t('orgStructure.sync.emailConflict.skip') }}
          </a-button>
          <a-popconfirm
            :content="t('orgStructure.sync.emailConflict.overwriteConfirm')"
            @ok="resolveConflict(item, 'OVERWRITE')"
          >
            <a-button size="small" status="warning" :loading="resolvingId === item.id">
              {{ t('orgStructure.sync.emailConflict.overwrite') }}
            </a-button>
          </a-popconfirm>
          <a-button
            v-if="item.conflictScene === 'CREATE'"
            size="small"
            type="outline"
            :loading="resolvingId === item.id"
            @click="resolveConflict(item, 'CREATE')"
          >
            {{ t('orgStructure.sync.emailConflict.create') }}
          </a-button>
        </div>
      </div>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import MsDrawer from '@/components/pure/ms-drawer/index.vue';
  import MsBaseTable from '@/components/pure/ms-table/base-table.vue';
  import useTable from '@/components/pure/ms-table/useTable';

  import {
    getEmailConflictPending,
    getSyncLogPage,
    getSyncStatus,
    manualSync,
    resolveEmailConflict,
  } from '@/api/modules/setting/orgStructure';
  import { useI18n } from '@/hooks/useI18n';

  import type { OrgSyncEmailConflictItem, OrgWecomSyncStatus } from '@/models/setting/orgStructure';

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
  const conflictModalVisible = ref(false);
  const conflictLoading = ref(false);
  const conflicts = ref<OrgSyncEmailConflictItem[]>([]);
  const pendingConflictCount = ref(0);
  const resolvingId = ref('');

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

  async function loadConflicts(openWhenHasData = false) {
    if (!props.organizationId) {
      conflicts.value = [];
      pendingConflictCount.value = 0;
      return;
    }
    try {
      conflictLoading.value = true;
      const list = (await getEmailConflictPending(props.organizationId)) || [];
      conflicts.value = list;
      pendingConflictCount.value = list.length;
      if (openWhenHasData && list.length > 0) {
        conflictModalVisible.value = true;
      }
    } finally {
      conflictLoading.value = false;
    }
  }

  async function openConflictModal() {
    conflictModalVisible.value = true;
    await loadConflicts();
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
        const tips: string[] = [t('orgStructure.sync.success')];
        if ((result.userMissingMobile || 0) > 0) {
          tips.push(t('orgStructure.sync.missingMobile', { count: result.userMissingMobile }));
        }
        if ((result.userPlaceholderEmail || 0) + (result.userMissingEmail || 0) > 0) {
          tips.push(
            t('orgStructure.sync.missingEmail', {
              count: (result.userPlaceholderEmail || 0) + (result.userMissingEmail || 0),
            })
          );
        }
        Message.success(tips.join('；'));
      }
      await loadStatus();
      await loadConflicts((result.userEmailConflict || 0) > 0);
      emit('syncComplete');
    } catch (error: any) {
      if (error?.response?.status === 409) {
        Message.warning(t('orgStructure.sync.conflict'));
      }
    } finally {
      syncing.value = false;
    }
  }

  async function resolveConflict(item: OrgSyncEmailConflictItem, action: 'SKIP' | 'OVERWRITE' | 'CREATE') {
    try {
      resolvingId.value = item.id;
      await resolveEmailConflict({ id: item.id, action });
      Message.success(t('orgStructure.sync.emailConflict.resolved'));
      await loadConflicts();
      if (conflicts.value.length === 0) {
        conflictModalVisible.value = false;
      }
    } finally {
      resolvingId.value = '';
    }
  }

  watch(
    () => props.organizationId,
    () => {
      loadStatus();
      loadConflicts();
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
