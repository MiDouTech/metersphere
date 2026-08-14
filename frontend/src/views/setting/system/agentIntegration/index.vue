<template>
  <div>
    <template v-if="canReadTokens">
      <MsCard simple>
        <div class="mb-3">
          <div class="text-base font-medium">{{ t('system.agentIntegration.myTokens') }}</div>
          <div class="mt-1 text-sm text-[var(--color-text-3)]">
            {{ t('system.agentIntegration.myTokensDesc') }}
          </div>
        </div>
        <McpOnboardingPanel v-if="canConnectTokens" class="mb-4" @create-token="openCreateModal" />
        <div
          class="mb-4 gap-3"
          :class="compact ? 'flex flex-col items-stretch' : 'flex flex-wrap items-center justify-between'"
        >
          <div class="flex flex-wrap items-center gap-2" :class="compact ? 'justify-end' : ''">
            <a-input-search
              v-model:model-value="keyword"
              :placeholder="t('system.agentIntegration.searchToken')"
              :class="compact ? 'min-w-[220px] flex-1' : 'w-[260px]'"
              allow-clear
              @search="searchParams"
              @press-enter="searchParams"
              @clear="searchParams"
            />
            <a-button
              v-visible-permission="{
                code: 'AGENT_TOKEN_CREATE_BUTTON',
                permissions: ['SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT'],
                typeList: ['SYSTEM'],
              }"
              v-operable-permission="{
                code: 'AGENT_TOKEN_CREATE_BUTTON',
                permissions: ['SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT'],
                typeList: ['SYSTEM'],
              }"
              type="primary"
              @click="openCreateModal"
            >
              {{ t('system.agentIntegration.createToken') }}
            </a-button>
            <a-button
              v-visible-permission="{
                code: 'AGENT_TOKEN_DOWNLOAD_BUTTON',
                permissions: ['SYSTEM_PERSONAL_AI_AGENT:READ'],
                typeList: ['SYSTEM'],
              }"
              v-operable-permission="{
                code: 'AGENT_TOKEN_DOWNLOAD_BUTTON',
                permissions: ['SYSTEM_PERSONAL_AI_AGENT:READ'],
                typeList: ['SYSTEM'],
              }"
              :loading="downloadLoading"
              type="outline"
              @click="handleDownload"
            >
              {{ t('system.agentIntegration.mcpDownload') }}
            </a-button>
          </div>
        </div>

        <a-alert type="info" class="mb-4">
          {{ t('system.agentIntegration.myTokensSecretTip') }}
        </a-alert>

        <ms-base-table v-bind="propsRes" no-disable v-on="propsEvent">
          <template #enable="{ record }">
            <a-switch
              v-visible-permission="{
                code: 'AGENT_TOKEN_UPDATE_BUTTON',
                permissions: ['SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT'],
                typeList: ['SYSTEM'],
              }"
              v-operable-permission="{
                code: 'AGENT_TOKEN_UPDATE_BUTTON',
                permissions: ['SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT'],
                typeList: ['SYSTEM'],
              }"
              :model-value="record.enable"
              size="small"
              :before-change="(val) => toggleEnable(val, record)"
            />
          </template>
          <template #scopes="{ record }">
            {{ formatScopeLabel(record.scopes) }}
          </template>
          <template #lastUsedAt="{ record }">
            {{ formatTime(record.lastUsedAt) }}
          </template>
          <template #action="{ record }">
            <div class="flex gap-2">
              <MsButton
                v-visible-permission="{
                  code: 'AGENT_TOKEN_UPDATE_BUTTON',
                  permissions: ['SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT'],
                  typeList: ['SYSTEM'],
                }"
                v-operable-permission="{
                  code: 'AGENT_TOKEN_UPDATE_BUTTON',
                  permissions: ['SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT'],
                  typeList: ['SYSTEM'],
                }"
                @click="openEditModal(record)"
              >
                {{ t('system.agentIntegration.tokenSettings') }}
              </MsButton>
              <MsButton
                v-visible-permission="{
                  code: 'AGENT_TOKEN_DELETE_BUTTON',
                  permissions: ['SYSTEM_PERSONAL_AI_AGENT:READ+REVOKE'],
                  typeList: ['SYSTEM'],
                }"
                v-operable-permission="{
                  code: 'AGENT_TOKEN_DELETE_BUTTON',
                  permissions: ['SYSTEM_PERSONAL_AI_AGENT:READ+REVOKE'],
                  typeList: ['SYSTEM'],
                }"
                status="danger"
                @click="removeToken(record)"
              >
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
              <a-option value="CHATGPT">ChatGPT</a-option>
              <a-option value="CURSOR">Cursor</a-option>
              <a-option value="WORKBUDDY">WorkBuddy</a-option>
              <a-option value="GENERIC">{{ t('system.agentIntegration.clientOther') }}</a-option>
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
                <div class="font-medium">{{ scope.label }}</div>
                <div class="mt-1 text-xs text-[var(--color-text-3)]">{{ scope.desc }}</div>
              </button>
            </div>
          </a-form-item>
        </a-form>
      </a-modal>

      <a-modal
        v-model:visible="editVisible"
        :title="t('system.agentIntegration.tokenSettings')"
        :ok-loading="editLoading"
        unmount-on-close
        @ok="handleUpdate"
        @cancel="resetEditForm"
      >
        <a-form ref="editFormRef" :model="editForm" layout="vertical">
          <a-form-item
            field="name"
            :label="t('system.agentIntegration.tokenName')"
            required
            :rules="[{ required: true, message: t('system.agentIntegration.tokenNameRequired') }]"
          >
            <a-input v-model="editForm.name" />
          </a-form-item>

          <a-form-item
            field="projectIds"
            :label="t('system.agentIntegration.projectIds')"
            :extra="t('system.agentIntegration.projectIdsHelp')"
          >
            <a-select
              v-model="editForm.projectIds"
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
                  editForm.scopes === scope.value
                    ? 'border-[rgb(var(--primary-6))] bg-[var(--color-primary-light-1)]'
                    : 'border-[var(--color-border-2)]'
                "
                @click="editForm.scopes = scope.value"
              >
                <div class="font-medium">{{ scope.label }}</div>
                <div class="mt-1 text-xs text-[var(--color-text-3)]">{{ scope.desc }}</div>
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
        <div class="mb-2 font-medium">{{ t('system.agentIntegration.fullToken') }}</div>
        <div class="break-all rounded bg-[var(--color-fill-2)] p-3 text-sm">{{ createdToken?.token }}</div>
        <div class="mt-3 flex justify-end">
          <a-button type="primary" @click="copyToken">{{ t('system.agentIntegration.copyToken') }}</a-button>
        </div>
        <div class="mb-2 mt-4 font-medium">{{ t('system.agentIntegration.mcpConfig') }}</div>
        <pre
          class="max-h-[220px] overflow-auto whitespace-pre-wrap break-all rounded bg-[var(--color-fill-2)] p-3 text-xs"
          >{{ currentMcpConfig }}</pre
        >
        <div class="mt-3 flex justify-end">
          <a-button type="primary" @click="copyMcpConfig">{{ t('system.agentIntegration.copyMcpConfig') }}</a-button>
        </div>
      </a-modal>
    </template>

    <a-alert v-else type="warning">当前账号没有个人 Agent 接入读取权限。</a-alert>

    <AgentTokenGovernancePanel v-if="!compact && canGovernTokens" class="mt-4" />
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { FormInstance, Message } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import MsButton from '@/components/pure/ms-button/index.vue';
  import MsCard from '@/components/pure/ms-card/index.vue';
  import MsBaseTable from '@/components/pure/ms-table/base-table.vue';
  import type { MsTableColumn } from '@/components/pure/ms-table/type';
  import useTable from '@/components/pure/ms-table/useTable';
  import AgentTokenGovernancePanel from './components/AgentTokenGovernancePanel.vue';
  import McpOnboardingPanel from './components/McpOnboardingPanel.vue';

  import {
    type AgentMcpManifest,
    type AgentTokenCreateResult,
    type AgentTokenListItem,
    createAgentToken,
    deleteAgentToken,
    disableAgentToken,
    downloadAgentMcpBundle,
    enableAgentToken,
    getAgentMcpManifest,
    getAgentTokenPage,
    getPersonalAgentProjectList,
    updateAgentToken,
  } from '@/api/modules/setting/agentIntegration';
  import { useI18n } from '@/hooks/useI18n';
  import useModal from '@/hooks/useModal';
  import { downloadByteFile } from '@/utils';
  import { hasAnyPermission } from '@/utils/permission';

  withDefaults(
    defineProps<{
      compact?: boolean;
    }>(),
    {
      compact: false,
    }
  );

  const { t } = useI18n();
  const { openModal } = useModal();
  const canGovernTokens = computed(() => hasAnyPermission(['SYSTEM_USER:READ'], ['SYSTEM']));

  const keyword = ref('');
  const createVisible = ref(false);
  const createLoading = ref(false);
  const editVisible = ref(false);
  const editLoading = ref(false);
  const downloadLoading = ref(false);
  const canReadTokens = hasAnyPermission(['SYSTEM_PERSONAL_AI_AGENT:READ'], ['SYSTEM']);
  const canConnectTokens = hasAnyPermission(['SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT'], ['SYSTEM']);
  const tokenVisible = ref(false);
  const manifest = ref<AgentMcpManifest>();
  const createdToken = ref<AgentTokenCreateResult>();
  const createdClientType = ref('CHATGPT');
  const createFormRef = ref<FormInstance>();
  const editFormRef = ref<FormInstance>();
  const createForm = reactive({
    name: '',
    projectIds: [] as string[],
    scopes: 'AGENT_ALL',
    clientType: 'CHATGPT',
  });
  const editForm = reactive({
    id: '',
    name: '',
    projectIds: [] as string[],
    scopes: 'AGENT_ALL',
  });

  const projectLoading = ref(false);
  const projectOptions = ref<{ id: string; name: string }[]>([]);

  const scopeOptions = [
    {
      value: 'AGENT_ALL',
      label: t('system.agentIntegration.scopeAgentAll'),
      desc: t('system.agentIntegration.scopeAgentAllDesc'),
    },
    {
      value: 'FUNCTIONAL_ALL',
      label: t('system.agentIntegration.scopeCase'),
      desc: t('system.agentIntegration.scopeCaseDesc'),
    },
    {
      value: 'CASE_UPDATE;CASE_COMMENT;CASE_ATTACHMENT',
      label: t('system.agentIntegration.scopeCaseMaintain'),
      desc: t('system.agentIntegration.scopeCaseMaintainDesc'),
    },
    {
      value: 'CASE_DELETE',
      label: t('system.agentIntegration.scopeCaseDelete'),
      desc: t('system.agentIntegration.scopeCaseDeleteDesc'),
    },
    {
      value: 'BUG_WRITE',
      label: t('system.agentIntegration.scopeBug'),
      desc: t('system.agentIntegration.scopeBugDesc'),
    },
    {
      value: 'BUG_COMMENT;BUG_ATTACHMENT;BUG_RELATE',
      label: t('system.agentIntegration.scopeBugExtend'),
      desc: t('system.agentIntegration.scopeBugExtendDesc'),
    },
    {
      value: 'BUG_DELETE',
      label: t('system.agentIntegration.scopeBugDelete'),
      desc: t('system.agentIntegration.scopeBugDeleteDesc'),
    },
    {
      value: 'PROJECT_READ',
      label: t('system.agentIntegration.scopeProjectRead'),
      desc: t('system.agentIntegration.scopeProjectReadDesc'),
    },
    {
      value: 'FUNCTIONAL_READ',
      label: t('system.agentIntegration.scopeFunctionalRead'),
      desc: t('system.agentIntegration.scopeFunctionalReadDesc'),
    },
    {
      value: 'FUNCTIONAL_SUBMIT',
      label: t('system.agentIntegration.scopeFunctionalSubmit'),
      desc: t('system.agentIntegration.scopeFunctionalSubmitDesc'),
    },
    {
      value: 'BUG_READ',
      label: t('system.agentIntegration.scopeBugRead'),
      desc: t('system.agentIntegration.scopeBugReadDesc'),
    },
  ];

  const scopeLabelMap = computed<Record<string, string>>(() =>
    scopeOptions.reduce(
      (map, item) => ({
        ...map,
        [item.value]: item.label,
      }),
      {} as Record<string, string>
    )
  );

  function formatScopeLabel(scopes?: string) {
    if (!scopes) return '-';
    return scopes
      .split(/[;,]/)
      .map((scope) => scope.trim())
      .filter(Boolean)
      .map((scope) => scopeLabelMap.value[scope] || scope)
      .join('、');
  }

  function formatTime(time?: number) {
    return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-';
  }

  const columns: MsTableColumn = [
    { title: 'system.agentIntegration.tokenName', dataIndex: 'name', showTooltip: true },
    {
      title: 'system.agentIntegration.projectIds',
      dataIndex: 'projectScopeLabel',
      width: 160,
      showTooltip: true,
    },
    { title: 'system.agentIntegration.clientType', dataIndex: 'clientType', width: 120 },
    { title: 'system.agentIntegration.scopes', dataIndex: 'scopes', slotName: 'scopes', width: 180, showTooltip: true },
    { title: 'common.createTime', dataIndex: 'createTime', width: 170 },
    {
      title: 'system.agentIntegration.lastUsedAt',
      dataIndex: 'lastUsedAt',
      slotName: 'lastUsedAt',
      width: 170,
    },
    { title: 'system.agentIntegration.invocationCount', dataIndex: 'invocationCount', width: 120 },
    { title: 'system.agentIntegration.enable', dataIndex: 'enable', slotName: 'enable', width: 100 },
    { title: 'common.operation', slotName: 'action', fixed: 'right', width: 140 },
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
      const list = (await getPersonalAgentProjectList(keywordText || '')) || [];
      projectOptions.value = list.map((item: { id: string; name: string; num?: number }) => ({
        id: item.id,
        name: item.num ? `${item.name}（${item.num}）` : item.name,
      }));
    } finally {
      projectLoading.value = false;
    }
  }

  function openCreateModal() {
    createVisible.value = true;
    searchProjects('');
  }

  function openEditModal(record: AgentTokenListItem) {
    editForm.id = record.id;
    editForm.name = record.name;
    if (record.projectIds?.length) {
      editForm.projectIds = [...record.projectIds];
    } else if (record.projectId) {
      editForm.projectIds = [record.projectId];
    } else {
      editForm.projectIds = [];
    }
    editForm.scopes = record.scopes || 'AGENT_ALL';
    editVisible.value = true;
    searchProjects('');
  }

  function resetCreateForm() {
    createForm.name = '';
    createForm.projectIds = [];
    createForm.scopes = 'AGENT_ALL';
    createForm.clientType = 'CHATGPT';
  }

  function resetEditForm() {
    editForm.id = '';
    editForm.name = '';
    editForm.projectIds = [];
    editForm.scopes = 'AGENT_ALL';
  }

  function getMcpUrl() {
    return `${window.location.origin}/api/mcp`;
  }

  function buildMcpConfig(token?: string, clientType?: string) {
    if (!token) return '';
    const mcpUrl = getMcpUrl();
    if (clientType === 'CURSOR') {
      return JSON.stringify(
        {
          mcpServers: {
            metersphere: {
              url: mcpUrl,
              headers: {
                Authorization: `Bearer ${token}`,
              },
            },
          },
        },
        null,
        2
      );
    }
    if (clientType === 'WORKBUDDY' || clientType === 'GENERIC') {
      return JSON.stringify(
        {
          name: 'metersphere',
          transport: 'streamable-http',
          url: mcpUrl,
          headers: {
            Authorization: `Bearer ${token}`,
          },
        },
        null,
        2
      );
    }
    return JSON.stringify(
      {
        type: 'mcp',
        name: 'metersphere',
        server_url: mcpUrl,
        authorization: `Bearer ${token}`,
      },
      null,
      2
    );
  }

  const currentMcpConfig = computed(() => buildMcpConfig(createdToken.value?.token, createdClientType.value));

  async function handleCreate() {
    const valid = await createFormRef.value?.validate();
    if (valid) return;
    createLoading.value = true;
    try {
      createdClientType.value = createForm.clientType;
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

  async function handleUpdate() {
    const valid = await editFormRef.value?.validate();
    if (valid) return;
    editLoading.value = true;
    try {
      await updateAgentToken({
        id: editForm.id,
        name: editForm.name,
        projectIds: editForm.projectIds?.length ? editForm.projectIds : [],
        scopes: editForm.scopes,
      });
      Message.success(t('common.updateSuccess'));
      editVisible.value = false;
      resetEditForm();
      loadList();
    } finally {
      editLoading.value = false;
    }
  }

  async function handleDownload() {
    downloadLoading.value = true;
    try {
      const blob = await downloadAgentMcpBundle();
      const name = manifest.value?.fileName || 'metersphere-agent-skill.zip';
      downloadByteFile(blob, name);
      Message.success(t('system.agentIntegration.mcpDownloadSuccess'));
    } finally {
      downloadLoading.value = false;
    }
  }

  async function copyToken() {
    if (!createdToken.value?.token) return;
    await navigator.clipboard.writeText(createdToken.value.token);
    Message.success(t('common.copySuccess'));
  }

  async function copyMcpConfig() {
    if (!currentMcpConfig.value) return;
    await navigator.clipboard.writeText(currentMcpConfig.value);
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

  onMounted(async () => {
    if (!canReadTokens) return;
    try {
      manifest.value = await getAgentMcpManifest();
    } catch {
      manifest.value = { available: false };
    }
    loadList();
  });
</script>
