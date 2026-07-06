<template>
  <MsCard simple no-content-padding>
    <div class="flex items-center justify-between border-b border-[var(--color-text-n8)] px-[16px] py-[12px]">
      <div class="text-[16px] font-medium text-[var(--color-text-1)]">{{ t('orgStructure.title') }}</div>
      <div class="flex items-center gap-[12px]">
        <div v-if="isSystemView" class="flex items-center gap-[8px]">
          <MsSelect
            v-model:model-value="selectedOrgId"
            class="w-[220px]"
            :options="orgOptions"
            :search-keys="['name']"
            value-key="id"
            label-key="name"
            allow-search
            :placeholder="t('orgStructure.selectOrg')"
            @change="handleOrgChange"
          />
          <a-tooltip :content="t('orgStructure.selectOrgTip')">
            <MsIcon type="icon-icon_info_circle" class="text-[var(--color-text-4)]" />
          </a-tooltip>
        </div>
        <SyncPanel ref="syncPanelRef" :organization-id="activeOrgId" @sync-complete="handleSyncComplete" />
        <a-button
          v-permission="['SYSTEM_ORGANIZATION_PROJECT:READ+UPDATE', 'ORGANIZATION_MEMBER:READ+UPDATE']"
          type="outline"
          :disabled="!activeOrgId"
          @click="configDrawerVisible = true"
        >
          {{ t('orgStructure.config.button') }}
        </a-button>
      </div>
    </div>
    <MsSplitBox :size="0.22" :min="0.15" :max="0.4">
      <template #first>
        <div class="h-full p-[16px]">
          <DepartmentTree
            ref="departmentTreeRef"
            :organization-id="activeOrgId"
            @select-department="handleSelectDepartment"
          />
        </div>
      </template>
      <template #second>
        <div class="h-full p-[16px]">
          <MemberTable ref="memberTableRef" :organization-id="activeOrgId" :department-id="selectedDepartmentId" />
        </div>
      </template>
    </MsSplitBox>
    <SyncConfigDrawer v-model:visible="configDrawerVisible" :organization-id="activeOrgId" />
  </MsCard>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';
  import { useRoute } from 'vue-router';

  import MsCard from '@/components/pure/ms-card/index.vue';
  import MsSplitBox from '@/components/pure/ms-split-box/index.vue';
  import MsSelect from '@/components/business/ms-select';
  import DepartmentTree from './components/DepartmentTree.vue';
  import MemberTable from './components/MemberTable.vue';
  import SyncConfigDrawer from './components/SyncConfigDrawer.vue';
  import SyncPanel from './components/SyncPanel.vue';

  import { postOrgTable } from '@/api/modules/setting/organizationAndProject';
  import { useI18n } from '@/hooks/useI18n';
  import { useAppStore } from '@/store';

  import { SettingRouteEnum } from '@/enums/routeEnum';

  const { t } = useI18n();
  const route = useRoute();
  const appStore = useAppStore();

  const isSystemView = computed(() => route.name === SettingRouteEnum.SETTING_SYSTEM_ORG_STRUCTURE);
  const selectedOrgId = ref('');
  const selectedDepartmentId = ref<string>();
  const orgOptions = ref<{ id: string; name: string }[]>([]);
  const departmentTreeRef = ref<InstanceType<typeof DepartmentTree>>();
  const memberTableRef = ref<InstanceType<typeof MemberTable>>();
  const syncPanelRef = ref<InstanceType<typeof SyncPanel>>();
  const configDrawerVisible = ref(false);

  const activeOrgId = computed(() => {
    if (isSystemView.value) {
      return selectedOrgId.value;
    }
    return appStore.currentOrgId;
  });

  async function loadOrgOptions() {
    if (!isSystemView.value) {
      return;
    }
    const result = await postOrgTable({ current: 1, pageSize: 500 });
    orgOptions.value = (result.list || []).map((item: { id: string; name: string }) => ({
      id: item.id,
      name: item.name,
    }));
    if (!selectedOrgId.value && orgOptions.value.length > 0) {
      selectedOrgId.value = orgOptions.value[0].id;
    }
  }

  function handleOrgChange() {
    selectedDepartmentId.value = undefined;
    departmentTreeRef.value?.resetSelection();
  }

  function handleSelectDepartment(departmentId?: string) {
    selectedDepartmentId.value = departmentId;
  }

  async function handleSyncComplete() {
    await departmentTreeRef.value?.loadTree();
    await memberTableRef.value?.loadData();
    await syncPanelRef.value?.loadStatus();
  }

  onMounted(() => {
    loadOrgOptions();
  });
</script>
