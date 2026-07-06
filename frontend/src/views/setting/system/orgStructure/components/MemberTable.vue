<template>
  <div class="flex h-full flex-col">
    <div class="mb-[16px] flex flex-wrap items-center gap-[12px]">
      <a-input-search
        v-model:model-value="keyword"
        :placeholder="t('orgStructure.member.search')"
        allow-clear
        class="w-[240px]"
        :max-length="255"
        @search="searchHandler"
        @press-enter="searchHandler"
        @clear="searchHandler"
      />
      <a-select
        v-model:model-value="enableFilter"
        :placeholder="t('orgStructure.member.status')"
        allow-clear
        class="w-[140px]"
        @change="searchHandler"
      >
        <a-option v-for="item in enableStatusOptions" :key="String(item.value)" :value="item.value">
          {{ t(item.label) }}
        </a-option>
      </a-select>
      <a-select
        v-model:model-value="syncStatusFilter"
        :placeholder="t('orgStructure.member.syncStatus')"
        allow-clear
        class="w-[140px]"
        @change="searchHandler"
      >
        <a-option v-for="item in memberSyncStatusOptions" :key="item.value" :value="item.value">
          {{ t(item.label) }}
        </a-option>
      </a-select>
    </div>
    <ms-base-table
      v-bind="propsRes"
      class="flex-1"
      row-class="cursor-pointer"
      v-on="propsEvent"
      @row-click="handleRowClick"
    >
      <template #name="{ record }">
        <span class="text-[rgb(var(--primary-6))]">{{ record.name }}</span>
      </template>
      <template #enable="{ record }">
        <div v-if="record.enable" class="flex items-center">
          <icon-check-circle-fill class="mr-[2px] text-[rgb(var(--success-6))]" />
          {{ t('orgStructure.member.status.enable') }}
        </div>
        <div v-else class="flex items-center text-[var(--color-text-4)]">
          <MsIcon type="icon-icon_disable" class="mr-[2px]" />
          {{ t('orgStructure.member.status.disable') }}
        </div>
      </template>
      <template #syncStatus="{ record }">
        {{ getMemberSyncStatusLabel(record.syncStatus) }}
      </template>
      <template #syncTime="{ record }">
        {{ formatTime(record.syncTime) }}
      </template>
    </ms-base-table>
    <MemberDetailDrawer
      v-model:visible="detailVisible"
      :member-id="currentMemberId"
      :organization-id="organizationId"
    />
  </div>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue';
  import dayjs from 'dayjs';

  import MsBaseTable from '@/components/pure/ms-table/base-table.vue';
  import useTable from '@/components/pure/ms-table/useTable';
  import MemberDetailDrawer from './MemberDetailDrawer.vue';

  import { getMemberPage } from '@/api/modules/setting/orgStructure';
  import { useI18n } from '@/hooks/useI18n';

  import type { OrgStructureMemberItem } from '@/models/setting/orgStructure';

  import { enableStatusOptions, MEMBER_SYNC_STATUS, memberSyncStatusOptions, memberTableColumns } from '../config';

  const props = defineProps<{
    organizationId: string;
    departmentId?: string;
  }>();

  const { t } = useI18n();
  const keyword = ref('');
  const enableFilter = ref<boolean | undefined>();
  const syncStatusFilter = ref<number | undefined>();
  const detailVisible = ref(false);
  const currentMemberId = ref('');

  const { propsRes, propsEvent, loadList, setLoadListParams } = useTable(getMemberPage, {
    columns: memberTableColumns,
    selectable: false,
    showSetting: false,
    heightUsed: 360,
    scroll: { x: 1000 },
  });

  function formatTime(time?: number) {
    return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-';
  }

  function getMemberSyncStatusLabel(status?: number) {
    if (status === MEMBER_SYNC_STATUS.SYNCED) {
      return t('orgStructure.member.syncStatus.synced');
    }
    if (status === MEMBER_SYNC_STATUS.FAILED) {
      return t('orgStructure.member.syncStatus.failed');
    }
    return t('orgStructure.member.syncStatus.notSynced');
  }

  function buildParams() {
    setLoadListParams({
      organizationId: props.organizationId,
      departmentId: props.departmentId,
      keyword: keyword.value || undefined,
      enable: enableFilter.value,
      syncStatus: syncStatusFilter.value,
    });
  }

  async function loadData() {
    if (!props.organizationId) {
      propsRes.value.data = [];
      return;
    }
    buildParams();
    await loadList();
  }

  function searchHandler() {
    loadData();
  }

  function handleRowClick(record: OrgStructureMemberItem) {
    currentMemberId.value = record.id;
    detailVisible.value = true;
  }

  watch(
    () => [props.organizationId, props.departmentId],
    () => {
      loadData();
    },
    { immediate: true }
  );

  defineExpose({
    loadData,
  });
</script>
