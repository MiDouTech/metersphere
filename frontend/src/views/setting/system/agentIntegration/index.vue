<template>
  <div>
    <McpOnboardingPanel />

    <MsCard simple>
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <div class="text-base font-medium">{{ t('system.agentIntegration.myTokens') }}</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]">
            {{ t('system.agentIntegration.myTokensDesc') }}
          </div>
        </div>
        <div class="flex items-center gap-2">
          <a-input-search
            v-model:model-value="keyword"
            :placeholder="t('system.agentIntegration.searchToken')"
            class="w-[260px]"
            allow-clear
            @search="searchParams"
            @press-enter="searchParams"
            @clear="searchParams"
          />
          <a-button type="primary" @click="openCreateModal">
            {{ t('system.agentIntegration.createToken') }}
          </a-button>
        </div>
      </div>

      <ms-base-table v-bind="propsRes" no-disable v-on="propsEvent">
        <template #enable="{ record }">
          <a-switch :model-value="record.enable" size="small" :before-change="(val) => toggleEnable(val, record)" />
        </template>
        <template #action="{ record }">
          <div class="flex gap-2">
            <MsButton @click="rotateToken(record)">
              {{ t('system.agentIntegration.rotate') }}
            </MsButton>
            <MsButton status="danger" @click="removeToken(record)">
              {{ t('common.delete') }}
            </MsButton>
          </div>
        </template>
      </ms-base-table>
    </MsCard>

    <a-modal
      v-model:visible="createVisible"
      :title="t('system.agentIntegration.createToken')"
      :ok-loading="createLoading"
      unmount-on-close
      @ok="handleCreate"
      @cancel="resetCreateForm"
    >
      <a-form ref="createFormRef" :model="createForm" layout="vertical">
        <a-form-item
          field="name"
          :label="t('system.agentIntegration.tokenName')"
          required
          :rules="[{ required: true, message: t('system.agentIntegration.tokenNameRequired') }]"
        >
          <a-input v-model="createForm.name" />
        </a-form-item>

        <a-form-item field="clientType" :label="t('system.agentIntegration.clientType')">
          <a-select v-model="createForm.clientType">
            <a-option value="CODEX">Codex</a-option>
            <a-option value="CHATGPT">ChatGPT</a-option>
            <a-option value="CURSOR">Cursor</a-option>
            <a-option value="WORKBUDDY">WorkBuddy</a-option>
            <a-option value="GENERIC">Other MCP</a-option>
          </a-select>
        </a-form-item>

        <a-form-item
          field="projectIds"
          :label="t('system.agentIntegration.projectIds')"
          :extra="t('system.agentIntegration.projectIdsHelp')"
        >
          <a-select
            v-model="createForm.projectIds"
            multiple
            allow-search
            allow-clear
            :filter-option="false"
            :placeholder="t('system.agentIntegration.projectIdsPlaceholder')"
            :loading="projectLoading"
            @search="searchProjects"
            @popup-visible-change="(visible: boolean) => visible && searchProjects('')"
          >
            <a-option v-for="p in projectOptions" :key="p.id" :value="p.id" :label="p.name">
              {{ p.name }}
            </a-option>
          </a-select>
        </a-form-item>

        <a-form-item
          field="scopes"
          :label="t('system.agentIntegration.scopes')"
          required
          :rules="[{ required: true, message: t('system.agentIntegration.scopesRequired') }]"
        >
          <div class="grid grid-cols-1 gap-2 md:grid-cols-2">
            <button
              v-for="scope in scopeOptions"
              :key="scope.value"
              type="button"
              class="rounded border p-3 text-left"
              :class="
                createForm.scopes === scope.value
                  ? 'border-[rgb(var(--primary-6))] bg-[var(--color-primary-light-1)]'
                  : 'border-[var(--color-border-2)]'
              "
              @click="createForm.scopes = scope.value"
            >
              <div class="font-medium">{{ scope.value }}</div>
              <div class="mt-1 text-xs text-[var(--color-text-3)]">{{ scope.label }}</div>
            </button>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:visible="tokenVisible"
      :title="t('system.agentIntegration.tokenCreated')"
      :footer="false"
      :mask-closable="false"
    >
      <a-alert type="warning" class="mb-4">
        {{ createdToken?.warning }}
      </a-alert>
      <div class="break-all rounded bg-[var(--color-fill-2)] p-3 text-sm">{{ createdToken?.token }}</div>
      <div class="mt-4 flex justify-end">
        <a-button type="primary" @click="copyToken">{{ t('common.copy') }}</a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { reactive, ref } from 'vue';
  import { FormInstance, Message } from '@arco-design/web-vue';

  import MsButton from '@/components/pure/ms-button/index.vue';
  import MsCard from '@/components/pure/ms-card/index.vue';
  import MsBaseTable from '@/components/pure/ms-table/base-table.vue';
  import type { MsTableColumn } from '@/components/pure/ms-table/type';
  import useTable from '@/components/pure/ms-table/useTable';
  import McpOnboardingPanel from './components/McpOnboardingPanel.vue';

  import {
    type AgentTokenCreateResult,
    type AgentTokenListItem,
    createAgentToken,
    deleteAgentToken,
    disableAgentToken,
    enableAgentToken,
    getAgentTokenPage,
    rotateAgentToken,
  } from '@/api/modules/setting/agentIntegration';
  import { getSystemProjectList } from '@/api/modules/system';
  import { useI18n } from '@/hooks/useI18n';
  import useModal from '@/hooks/useModal';

  const { t } = useI18n();
  const { openModal } = useModal();

  const keyword = ref('');
  const createVisible = ref(false);
  const createLoading = ref(false);
  const tokenVisible = ref(false);
  const createdToken = ref<AgentTokenCreateResult>();
  const createFormRef = ref<FormInstance>();
  const createForm = reactive({
    name: '',
    projectIds: [] as string[],
    scopes: 'AGENT_ALL',
    clientType: 'CODEX',
  });

  const projectLoading = ref(false);
  const projectOptions = ref<{ id: string; name: string }[]>([]);

  const scopeOptions = [
    { value: 'AGENT_ALL', label: t('system.agentIntegration.scopeAgentAll') },
    { value: 'FUNCTIONAL_ALL', label: t('system.agentIntegration.scopeCase') },
    { value: 'BUG_WRITE', label: t('system.agentIntegration.scopeBug') },
    { value: 'FUNCTIONAL_READ', label: 'Read functional cases only' },
    { value: 'FUNCTIONAL_SUBMIT', label: 'Submit execution results only' },
    { value: 'BUG_READ', label: 'Read bugs only' },
  ];

  const columns: MsTableColumn = [
    { title: 'system.agentIntegration.tokenName', dataIndex: 'name', showTooltip: true },
    { title: 'system.agentIntegration.displayPrefix', dataIndex: 'displayPrefix', width: 180, showTooltip: true },
    {
      title: 'system.agentIntegration.projectIds',
      dataIndex: 'projectScopeLabel',
      width: 160,
      showTooltip: true,
    },
    { title: 'system.agentIntegration.clientType', dataIndex: 'clientType', width: 120 },
    { title: 'system.agentIntegration.scopes', dataIndex: 'scopes', width: 160 },
    { title: 'system.agentIntegration.invocationCount', dataIndex: 'invocationCount', width: 120 },
    { title: 'system.agentIntegration.enable', dataIndex: 'enable', slotName: 'enable', width: 100 },
    { title: 'common.operation', slotName: 'action', fixed: 'right', width: 180 },
  ];

  const { propsRes, propsEvent, loadList, setLoadListParams } = useTable(getAgentTokenPage, {
    columns,
    scroll: { x: '100%' },
    selectable: false,
    heightUsed: 280,
  });

  function searchParams() {
    setLoadListParams({ keyword: keyword.value });
    loadList();
  }

  async function searchProjects(keywordText: string) {
    projectLoading.value = true;
    try {
      const list = (await getSystemProjectList(keywordText || '')) || [];
      projectOptions.value = list.map((item: { id: string; name: string }) => ({
        id: item.id,
        name: item.name,
      }));
    } finally {
      projectLoading.value = false;
    }
  }

  function openCreateModal() {
    createVisible.value = true;
    searchProjects('');
  }

  function resetCreateForm() {
    createForm.name = '';
    createForm.projectIds = [];
    createForm.scopes = 'AGENT_ALL';
    createForm.clientType = 'CODEX';
  }

  async function handleCreate() {
    const valid = await createFormRef.value?.validate();
    if (valid) return;
    createLoading.value = true;
    try {
      createdToken.value = await createAgentToken({
        name: createForm.name,
        projectIds: createForm.projectIds?.length ? createForm.projectIds : [],
        scopes: createForm.scopes,
        clientType: createForm.clientType,
      });
      createVisible.value = false;
      tokenVisible.value = true;
      resetCreateForm();
      loadList();
    } finally {
      createLoading.value = false;
    }
  }

  async function copyToken() {
    if (!createdToken.value?.token) return;
    await navigator.clipboard.writeText(createdToken.value.token);
    Message.success(t('common.copySuccess'));
  }

  async function toggleEnable(val: string | number | boolean, record: AgentTokenListItem) {
    try {
      if (val) {
        await enableAgentToken(record.id);
      } else {
        await disableAgentToken(record.id);
      }
      Message.success(t('common.updateSuccess'));
      loadList();
      return true;
    } catch (error) {
      return false;
    }
  }

  async function rotateToken(record: AgentTokenListItem) {
    createdToken.value = await rotateAgentToken(record.id);
    tokenVisible.value = true;
    loadList();
  }

  function removeToken(record: AgentTokenListItem) {
    openModal({
      type: 'error',
      title: t('common.deleteConfirmTitle'),
      content: t('system.agentIntegration.deleteConfirm', { name: record.name }),
      onBeforeOk: async () => {
        await deleteAgentToken(record.id);
        Message.success(t('common.deleteSuccess'));
        loadList();
      },
    });
  }

  loadList();
</script>
