<template>
  <MsCard simple>
    <div class="mb-4 flex items-center justify-between">
      <a-button v-permission="['SYSTEM_USER:READ+ADD']" type="primary" @click="openCreateModal">
        {{ t('system.agentIntegration.createToken') }}
      </a-button>
      <a-input-search
        v-model:model-value="keyword"
        :placeholder="t('system.agentIntegration.searchToken')"
        class="w-[260px]"
        allow-clear
        @search="searchParams"
        @press-enter="searchParams"
        @clear="searchParams"
      />
    </div>
    <ms-base-table v-bind="propsRes" no-disable v-on="propsEvent">
      <template #enable="{ record }">
        <a-switch
          v-model:model-value="record.enable"
          v-permission="['SYSTEM_USER:READ+UPDATE']"
          size="small"
          :before-change="(val) => toggleEnable(val, record)"
        />
      </template>
      <template #action="{ record }">
        <MsButton v-permission="['SYSTEM_USER:READ+DELETE']" status="danger" @click="removeToken(record)">
          {{ t('common.delete') }}
        </MsButton>
      </template>
    </ms-base-table>
  </MsCard>

  <a-modal
    v-model:visible="createVisible"
    :title="t('system.agentIntegration.createToken')"
    :ok-loading="createLoading"
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
      <a-form-item
        field="userId"
        :label="t('system.agentIntegration.userId')"
        required
        :rules="[{ required: true, message: t('system.agentIntegration.userIdRequired') }]"
      >
        <a-input v-model="createForm.userId" />
      </a-form-item>
      <a-form-item field="projectId" :label="t('system.agentIntegration.defaultProjectId')">
        <a-input v-model="createForm.projectId" />
      </a-form-item>
      <a-form-item
        field="scopes"
        :label="t('system.agentIntegration.scopes')"
        required
        :rules="[{ required: true, message: t('system.agentIntegration.scopesRequired') }]"
      >
        <a-select v-model="createForm.scopes">
          <a-option value="AGENT_ALL">AGENT_ALL（闭环全能力）</a-option>
          <a-option value="FUNCTIONAL_ALL">FUNCTIONAL_ALL（仅读/回写）</a-option>
          <a-option value="FUNCTIONAL_READ">FUNCTIONAL_READ</a-option>
          <a-option value="FUNCTIONAL_SUBMIT">FUNCTIONAL_SUBMIT</a-option>
          <a-option value="PROJECT_WRITE">PROJECT_WRITE</a-option>
          <a-option value="CASE_WRITE">CASE_WRITE</a-option>
          <a-option value="PLAN_WRITE">PLAN_WRITE</a-option>
          <a-option value="REVIEW_WRITE">REVIEW_WRITE</a-option>
          <a-option value="BUG_WRITE">BUG_WRITE</a-option>
        </a-select>
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
</template>

<script setup lang="ts">
  import { reactive, ref } from 'vue';
  import { FormInstance, Message } from '@arco-design/web-vue';

  import MsButton from '@/components/pure/ms-button/index.vue';
  import MsCard from '@/components/pure/ms-card/index.vue';
  import MsBaseTable from '@/components/pure/ms-table/base-table.vue';
  import type { MsTableColumn } from '@/components/pure/ms-table/type';
  import useTable from '@/components/pure/ms-table/useTable';

  import {
    type AgentTokenCreateResult,
    type AgentTokenListItem,
    createAgentToken,
    deleteAgentToken,
    getAgentTokenPage,
    updateAgentToken,
  } from '@/api/modules/setting/agentIntegration';
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
    userId: '',
    projectId: '',
    scopes: 'AGENT_ALL',
  });

  const columns: MsTableColumn = [
    { title: 'system.agentIntegration.tokenName', dataIndex: 'name', showTooltip: true },
    { title: 'system.agentIntegration.userId', dataIndex: 'userId', width: 140 },
    { title: 'system.agentIntegration.defaultProjectId', dataIndex: 'projectId', width: 160, showTooltip: true },
    { title: 'system.agentIntegration.scopes', dataIndex: 'scopes', width: 160 },
    { title: 'system.agentIntegration.enable', dataIndex: 'enable', slotName: 'enable', width: 100 },
    { title: 'common.operation', slotName: 'action', fixed: 'right', width: 120 },
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

  function openCreateModal() {
    createVisible.value = true;
  }

  function resetCreateForm() {
    createForm.name = '';
    createForm.userId = '';
    createForm.projectId = '';
    createForm.scopes = 'AGENT_ALL';
  }

  async function handleCreate() {
    const valid = await createFormRef.value?.validate();
    if (valid) return;
    createLoading.value = true;
    try {
      createdToken.value = await createAgentToken({ ...createForm });
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
      await updateAgentToken({ id: record.id, enable: Boolean(val) });
      Message.success(t('common.updateSuccess'));
      return true;
    } catch (error) {
      return false;
    }
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
