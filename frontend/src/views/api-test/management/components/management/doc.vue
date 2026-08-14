<template>
  <div class="flex h-full flex-col px-[16px] pt-[8px]">
    <div class="mb-[12px] flex items-center gap-[12px]">
      <a-input-search
        v-model="keyword"
        class="w-[320px]"
        allow-clear
        :placeholder="t('apiTestManagement.searchPlaceholder')"
        @search="search"
        @clear="search"
      />
      <a-button :loading="loading" @click="load">{{ t('common.refresh') }}</a-button>
    </div>
    <a-table
      class="flex-1"
      :data="data"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @page-change="changePage"
      @page-size-change="changePageSize"
    >
      <template #empty><a-empty /></template>
      <a-table-column :title="t('apiTestManagement.apiName')" data-index="name" />
      <a-table-column title="Method" :width="120">
        <template #cell="{ record }">{{ record.protocol === 'HTTP' ? record.method : record.protocol }}</template>
      </a-table-column>
      <a-table-column :title="t('apiTestManagement.path')" data-index="path" />
      <a-table-column :title="t('apiTestManagement.apiStatus')" data-index="status" :width="140" />
      <a-table-column :title="t('common.updateTime')" :width="190">
        <template #cell="{ record }">{{ formatTime(record.updateTime) }}</template>
      </a-table-column>
      <a-table-column :title="t('common.operation')" :width="100" fixed="right">
        <template #cell="{ record }">
          <a-link @click="emit('open', record)">{{ t('common.detail') }}</a-link>
        </template>
      </a-table-column>
    </a-table>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import dayjs from 'dayjs';

  import { getDefinitionDocPage } from '@/api/modules/api-test/management';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';

  import type { ApiDefinitionDetail } from '@/models/apiTest/management';

  const props = defineProps<{ activeModule: string; offspringIds: string[]; selectedProtocols: string[] }>();
  const emit = defineEmits<{ (e: 'open', record: ApiDefinitionDetail): void }>();
  const { t } = useI18n();
  const appStore = useAppStore();
  const loading = ref(false);
  const data = ref<ApiDefinitionDetail[]>([]);
  const keyword = ref('');
  const current = ref(1);
  const pageSize = ref(20);
  const total = ref(0);
  const pagination = computed(() => ({
    current: current.value,
    pageSize: pageSize.value,
    total: total.value,
    showTotal: true,
    showPageSize: true,
  }));
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');

  async function load() {
    if (!appStore.currentProjectId) return;
    loading.value = true;
    try {
      const result = await getDefinitionDocPage({
        id: '',
        name: '',
        projectId: appStore.currentProjectId,
        versionId: '',
        refId: '',
        deleted: false,
        current: current.value,
        pageSize: pageSize.value,
        keyword: keyword.value.trim() || undefined,
        moduleIds:
          props.activeModule === 'all' || props.activeModule === 'root'
            ? []
            : [props.activeModule, ...props.offspringIds],
        protocols: props.selectedProtocols,
      });
      data.value = result.list || [];
      total.value = result.total || 0;
    } finally {
      loading.value = false;
    }
  }
  function search() {
    current.value = 1;
    load();
  }
  function changePage(value: number) {
    current.value = value;
    load();
  }
  function changePageSize(value: number) {
    pageSize.value = value;
    current.value = 1;
    load();
  }
  watch(
    () => [appStore.currentProjectId, props.activeModule, props.offspringIds, props.selectedProtocols],
    () => {
      current.value = 1;
      load();
    },
    { immediate: true, deep: true }
  );
</script>
