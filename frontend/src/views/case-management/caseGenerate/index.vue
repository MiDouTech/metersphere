<template>
  <div class="case-generate-page">
    <div class="case-generate-header">
      <div>
        <div class="text-[20px] font-medium text-[var(--color-text-1)]">
          {{ t('caseManagement.caseGenerate.title') }}
        </div>
        <div class="mt-[4px] text-[13px] text-[var(--color-text-3)]">
          {{ t('caseManagement.caseGenerate.subtitle') }}
        </div>
      </div>
      <a-alert class="case-generate-alert" type="info" show-icon>
        {{ t('caseManagement.caseGenerate.serverPersistTip') }}
      </a-alert>
    </div>

    <div class="case-generate-workbench" :style="gridStyle">
      <section class="case-generate-panel">
        <div class="case-generate-panel-title">{{ t('caseManagement.caseGenerate.conversation') }}</div>
        <a-form :model="generationForm" layout="vertical">
          <a-form-item :label="t('caseManagement.caseGenerate.aiResource')">
            <a-select
              v-model:model-value="chatModelId"
              :options="modelOptions"
              allow-search
              :placeholder="t('caseManagement.caseGenerate.resourcePlaceholder')"
            />
            <div v-if="selectedResource?.unavailableReason" class="mt-[6px] text-[12px] text-[rgb(var(--danger-6))]">
              {{ resourceUnavailableText(selectedResource.unavailableReason) }}
            </div>
          </a-form-item>
        </a-form>
        <input ref="fileInputRef" class="hidden" type="file" @change="handleFileChange" />
        <a-button class="mb-[12px] w-full" :loading="uploading" :disabled="!chatModelId" @click="fileInputRef?.click()">
          {{ t('caseManagement.caseGenerate.uploadDesign') }}
        </a-button>
        <div class="case-generate-source-list">
          <div
            v-for="document in sourceDocuments"
            :key="document.id"
            class="case-generate-source-item"
            :class="{ active: selectedSourceDocumentIds.includes(document.id) }"
            @click="toggleSourceDocument(document.id)"
          >
            <div class="min-w-0 flex-1">
              <div class="truncate text-[13px]">{{ document.originalName }}</div>
              <div class="mt-[4px] flex flex-wrap gap-[4px]">
                <a-tag size="small" :color="getDocumentStatusColor(document.parseStatus)">
                  {{ getDocumentStatusText(document.parseStatus) }}
                </a-tag>
                <a-tag v-if="document.duplicate" size="small" color="orange">Duplicate</a-tag>
              </div>
              <div v-if="document.errorMessage" class="mt-[4px] truncate text-[12px] text-[rgb(var(--danger-6))]">
                {{ document.errorMessage }}
              </div>
            </div>
            <a-button
              v-if="document.parseStatus === 'FAILED'"
              type="text"
              size="mini"
              @click.stop="retrySourceDocument(document.id)"
            >
              {{ t('caseManagement.caseGenerate.retry') }}
            </a-button>
          </div>
        </div>
        <div class="case-generate-messages">
          <div v-for="message in messages" :key="message.id" class="case-generate-message" :class="message.role">
            <div class="case-generate-message-role flex items-center gap-[6px]">
              <span>{{ message.role === 'user' ? 'User' : 'AI' }}</span>
              <a-tag v-if="message.resourceId" size="small">{{ messageResourceLabel(message) }}</a-tag>
            </div>
            <div class="case-generate-message-content">{{ message.content }}</div>
          </div>
        </div>
        <a-textarea
          v-model:model-value="prompt"
          :placeholder="t('caseManagement.caseGenerate.inputPlaceholder')"
          :auto-size="{ minRows: 4, maxRows: 8 }"
          :max-length="4000"
          show-word-limit
        />
        <div class="mt-[12px] flex justify-end gap-[8px]">
          <a-button :disabled="!generating" @click="stopGenerate">
            {{ t('caseManagement.caseGenerate.stop') }}
          </a-button>
          <a-button
            type="primary"
            :loading="generating"
            :disabled="!prompt.trim() || !chatModelId.trim()"
            @click="sendPrompt"
          >
            {{ t('caseManagement.caseGenerate.send') }}
          </a-button>
        </div>
      </section>

      <div class="case-generate-resizer" @mousedown="startResize('left', $event)" />

      <section class="case-generate-panel">
        <div class="case-generate-panel-title">{{ t('caseManagement.caseGenerate.draftList') }}</div>
        <div class="mb-[12px] flex items-center gap-[8px]">
          <a-select v-model:model-value="statusFilter" class="min-w-0 flex-1" @change="handleStatusFilterChange">
            <a-option value="ALL">{{ t('caseManagement.caseGenerate.statusAll') }}</a-option>
            <a-option value="DRAFT">{{ t('caseManagement.caseGenerate.statusDraft') }}</a-option>
            <a-option value="READY">{{ t('caseManagement.caseGenerate.statusReady') }}</a-option>
            <a-option value="INVALID">{{ t('caseManagement.caseGenerate.statusInvalid') }}</a-option>
            <a-option value="FAILED">{{ t('caseManagement.caseGenerate.statusFailed') }}</a-option>
            <a-option value="SAVED">{{ t('caseManagement.caseGenerate.statusSaved') }}</a-option>
          </a-select>
          <a-checkbox :model-value="allChecked" @change="toggleAll">
            {{ t('caseManagement.caseGenerate.selectAll') }}
          </a-checkbox>
        </div>
        <div class="mb-[12px] flex gap-[8px]">
          <a-button size="small" :disabled="checkedDraftIds.length === 0" @click="deleteChecked">
            {{ t('caseManagement.caseGenerate.delete') }}
          </a-button>
          <a-button
            size="small"
            :disabled="!activeDraft || !chatModelId.trim() || selectedResource?.resourceType !== 'MODEL_API'"
            @click="regenerateActive"
          >
            {{ t('caseManagement.caseGenerate.regenerate') }}
          </a-button>
          <a-button
            size="small"
            type="primary"
            :loading="saving"
            :disabled="checkedDraftIds.length === 0"
            @click="batchSave"
          >
            {{ t('caseManagement.caseGenerate.batchSave') }}
          </a-button>
        </div>
        <a-empty v-if="drafts.length === 0" :description="t('caseManagement.caseGenerate.noDraft')" />
        <div v-else class="case-generate-draft-list">
          <div
            v-for="draft in drafts"
            :key="draft.id"
            class="case-generate-draft-item"
            :class="{ active: draft.id === activeDraftId }"
            @click="selectDraft(draft.id)"
          >
            <a-checkbox :model-value="checkedDraftIds.includes(draft.id)" @click.stop @change="toggleDraft(draft.id)" />
            <div class="min-w-0 flex-1">
              <div class="truncate text-[14px] font-medium text-[var(--color-text-1)]">{{ draft.name }}</div>
              <div class="mt-[4px] flex flex-wrap items-center gap-[6px]">
                <a-tag size="small" :color="getStatusColor(draft.draftStatus)">{{
                  getStatusText(draft.draftStatus)
                }}</a-tag>
                <a-tag v-if="draft.validationStatus === 'INVALID'" size="small" color="red">Invalid</a-tag>
                <a-tag v-if="draft.duplicate" size="small" color="orange">Duplicate</a-tag>
              </div>
              <div
                v-if="draft.validationMessage"
                class="mt-[4px] truncate text-[12px]"
                :class="
                  draft.validationStatus === 'INVALID' ? 'text-[rgb(var(--danger-6))]' : 'text-[rgb(var(--warning-6))]'
                "
              >
                {{ draft.validationMessage }}
              </div>
            </div>
            <a-button class="detail-button" type="text" size="mini" @click.stop="openMobileDetail(draft.id)">
              {{ t('caseManagement.caseGenerate.openDetail') }}
            </a-button>
          </div>
        </div>
        <div class="mt-[12px] flex justify-end">
          <a-pagination
            v-model:current="draftPage"
            v-model:page-size="draftPageSize"
            size="mini"
            :total="draftTotal"
            show-total
            show-page-size
            :page-size-options="[10, 20, 50]"
            @change="reloadDrafts"
            @page-size-change="handleDraftPageSizeChange"
          />
        </div>
      </section>

      <div class="case-generate-resizer" @mousedown="startResize('middle', $event)" />

      <section class="case-generate-panel case-generate-detail-panel">
        <DraftDetailForm v-if="activeDraft" v-model:draft="activeDraft" />
        <a-empty v-else :description="t('caseManagement.caseGenerate.noDraft')" />
      </section>
    </div>

    <a-drawer v-model:visible="detailDrawerVisible" :width="520" :title="t('caseManagement.caseGenerate.detail')">
      <DraftDetailForm v-if="activeDraft" v-model:draft="activeDraft" />
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue';
  import { Message, Modal } from '@arco-design/web-vue';

  import DraftDetailForm from './components/DraftDetailForm.vue';

  import type {
    AiCaseAgentEvent,
    AiResourceType,
    AiSelectableResource,
  } from '@/api/modules/case-management/caseGenerate';
  import {
    batchSaveAiCaseDraft,
    cancelAiCaseAgentChat,
    createAiCaseAgentConversation,
    deleteAiCaseDraft,
    getAiCaseAgentConversation,
    getAiCaseAgentExecution,
    listAiCaseAgentEvents,
    listAiCaseAgentResources,
    pageAiCaseAgentMessages,
    pageAiCaseDraft,
    pageAiSourceDocument,
    regenerateAiCaseDraft,
    retryAiSourceDocument,
    streamAiCaseAgentChat,
    subscribeAiSourceDocumentEvents,
    switchAiCaseAgentResource,
    updateAiCaseDraft,
    uploadAiSourceDocument,
  } from '@/api/modules/case-management/caseGenerate';
  import { useI18n } from '@/hooks/useI18n';
  import useModal from '@/hooks/useModal';
  import useAppStore from '@/store/modules/app';

  import type { AiCaseDraft, AiDraftStatus, AiSourceDocument } from '@/models/caseManagement/caseGenerate';
  import { CaseManagementRouteEnum } from '@/enums/routeEnum';

  defineOptions({
    name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE_GENERATE,
  });

  interface ConversationMessage {
    id: string;
    role: 'user' | 'assistant';
    content: string;
    status?: 'streaming' | 'completed' | 'failed' | 'canceled';
    requestId?: string;
    resourceType?: AiResourceType;
    resourceId?: string;
  }

  const { t } = useI18n();
  const { openModal } = useModal();
  const appStore = useAppStore();
  const currentProjectId = computed(() => appStore.currentProjectId || '');
  const currentOrgId = computed(() => appStore.currentOrgId || '');
  const localStateKey = computed(() => `case-generate-workbench:${currentProjectId.value || 'none'}`);

  const prompt = ref('');
  const chatModelId = ref(
    localStorage.getItem('case-generate-chat-model-id') || localStorage.getItem('aiChatModel') || ''
  );
  const generationForm = {};
  const conversationId = ref('');
  const conversationResourceId = ref('');
  const conversationResourceType = ref<AiResourceType>('MODEL_API');
  const availableResources = ref<AiSelectableResource[]>([]);
  const generating = ref(false);
  const activeGenerationId = ref('');
  const saving = ref(false);
  const statusFilter = ref<'ALL' | AiDraftStatus>('ALL');
  const draftPage = ref(1);
  const draftPageSize = ref(20);
  const draftTotal = ref(0);
  const messages = ref<ConversationMessage[]>([]);
  const drafts = ref<AiCaseDraft[]>([]);
  const sourceDocuments = ref<AiSourceDocument[]>([]);
  const selectedSourceDocumentIds = ref<string[]>([]);
  const activeDraftId = ref('');
  const checkedDraftIds = ref<string[]>([]);
  const detailDrawerVisible = ref(false);
  const fileInputRef = ref<HTMLInputElement>();
  const uploading = ref(false);
  const leftWidth = ref(30);
  const middleWidth = ref(35);
  let updateTimer: number | undefined;
  let unsubscribeDocumentEvents: (() => void) | undefined;
  let abortAgentStream: (() => void) | undefined;

  const selectedResource = computed(() => availableResources.value.find((item) => item.id === chatModelId.value));
  const modelOptions = computed(() => {
    const groups = [
      {
        label: t('caseManagement.caseGenerate.platformModels'),
        test: (item: AiSelectableResource) => item.resourceType === 'MODEL_API' && !item.personal,
      },
      {
        label: t('caseManagement.caseGenerate.personalModels'),
        test: (item: AiSelectableResource) => item.resourceType === 'MODEL_API' && item.personal,
      },
      {
        label: t('caseManagement.caseGenerate.myAgents'),
        test: (item: AiSelectableResource) => item.resourceType === 'USER_AGENT',
      },
    ];
    return groups
      .map((group) => ({
        label: group.label,
        options: availableResources.value.filter(group.test).map((item) => ({
          label: `${item.displayName} · ${item.provider}${item.experimental ? ' (Experimental)' : ''}`,
          value: item.id,
          disabled: Boolean(item.unavailableReason),
        })),
      }))
      .filter((group) => group.options.length > 0);
  });

  function createId(prefix: string) {
    return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  }

  function loadLocalState() {
    const raw = localStorage.getItem(localStateKey.value);
    if (!raw) {
      selectedSourceDocumentIds.value = [];
      return;
    }
    try {
      const parsed = JSON.parse(raw);
      selectedSourceDocumentIds.value = Array.isArray(parsed.selectedSourceDocumentIds)
        ? parsed.selectedSourceDocumentIds
        : [];
      if (parsed.conversationId) {
        conversationId.value = parsed.conversationId;
      }
    } catch {
      selectedSourceDocumentIds.value = [];
    }
  }

  function persistLocalState() {
    if (!currentProjectId.value) {
      return;
    }
    localStorage.setItem(
      localStateKey.value,
      JSON.stringify({
        conversationId: conversationId.value,
        selectedSourceDocumentIds: selectedSourceDocumentIds.value,
      })
    );
  }

  async function loadAgentWorkspace() {
    messages.value = [];
    conversationResourceId.value = '';
    if (!currentProjectId.value) {
      availableResources.value = [];
      conversationId.value = '';
      return;
    }
    availableResources.value = (await listAiCaseAgentResources(currentProjectId.value)) || [];
    if (!availableResources.value.some((item) => item.id === chatModelId.value && !item.unavailableReason)) {
      chatModelId.value = availableResources.value.find((item) => !item.unavailableReason)?.id || '';
    }
    if (!conversationId.value) return;
    try {
      const conversation = await getAiCaseAgentConversation(conversationId.value, currentProjectId.value);
      conversationResourceId.value = conversation.resourceId || conversation.modelSourceId || '';
      conversationResourceType.value = conversation.resourceType || 'MODEL_API';
      chatModelId.value = conversationResourceId.value;
      const history = await pageAiCaseAgentMessages({
        projectId: currentProjectId.value,
        conversationId: conversationId.value,
        pageSize: 100,
      });
      messages.value = (history.records || [])
        .filter((item) => item.role === 'USER' || item.role === 'ASSISTANT')
        .map((item) => ({
          id: item.id,
          role: item.role === 'USER' ? 'user' : 'assistant',
          content: item.content || '',
          status: item.status.toLowerCase() as ConversationMessage['status'],
          requestId: item.requestId,
          resourceType: item.resourceType,
          resourceId: item.resourceId,
        }));
    } catch {
      conversationId.value = '';
      conversationResourceId.value = '';
      messages.value = [];
    }
  }

  function confirmResourceSwitch(previous: AiResourceType, next: AiResourceType) {
    if (previous === 'MODEL_API' && next === 'MODEL_API') return Promise.resolve(true);
    return new Promise<boolean>((resolve) => {
      Modal.confirm({
        title: t('caseManagement.caseGenerate.resourceSwitchTitle'),
        content: t('caseManagement.caseGenerate.resourceSwitchWarning'),
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
        onClose: () => resolve(false),
      });
    });
  }

  function resourceUnavailableText(reason?: string) {
    const reasonMessageMap: Record<string, string> = {
      AGENT_OFFLINE: t('caseManagement.caseGenerate.agentOffline'),
      AGENT_AUTH_EXPIRED: t('caseManagement.caseGenerate.agentAuthExpired'),
      AI_RESOURCE_NOT_ALLOWED: t('caseManagement.caseGenerate.resourceNotAllowed'),
    };
    return reasonMessageMap[reason || ''] || reason || t('caseManagement.caseGenerate.resourceUnavailable');
  }

  async function ensureConversation() {
    const resource = selectedResource.value;
    if (!resource || resource.unavailableReason) throw new Error(resourceUnavailableText(resource?.unavailableReason));
    if (!conversationId.value) {
      const conversation = await createAiCaseAgentConversation({
        projectId: currentProjectId.value,
        organizationId: currentOrgId.value,
        resourceType: resource.resourceType,
        resourceId: resource.id,
        modelSourceId: resource.resourceType === 'MODEL_API' ? resource.id : undefined,
        title: prompt.value.trim().slice(0, 80) || undefined,
      });
      conversationId.value = conversation.id;
      conversationResourceId.value = conversation.resourceId;
      conversationResourceType.value = conversation.resourceType;
      persistLocalState();
    } else if (conversationResourceId.value !== resource.id) {
      const confirmed = await confirmResourceSwitch(conversationResourceType.value, resource.resourceType);
      if (!confirmed) {
        chatModelId.value = conversationResourceId.value;
        throw new Error(t('caseManagement.caseGenerate.resourceSwitchCanceled'));
      }
      const conversation = await switchAiCaseAgentResource({
        projectId: currentProjectId.value,
        conversationId: conversationId.value,
        resourceType: resource.resourceType,
        resourceId: resource.id,
      });
      conversationResourceId.value = conversation.resourceId;
      conversationResourceType.value = conversation.resourceType;
    }
    return conversationId.value;
  }

  function messageResourceLabel(message: ConversationMessage) {
    const resource = availableResources.value.find((item) => item.id === message.resourceId);
    return resource?.displayName || message.resourceId || message.resourceType || '';
  }

  function handleAgentEvent(event: AiCaseAgentEvent, temporaryAssistantId: string) {
    const payload = event.payload ? JSON.parse(event.payload) : {};
    const assistant = messages.value.find((item) => item.requestId === event.requestId && item.role === 'assistant');
    if (event.eventType === 'message-start' && assistant) {
      assistant.id = payload.messageId || assistant.id;
    } else if (event.eventType === 'content-delta' && assistant) {
      assistant.content += payload.delta || '';
    } else if (event.eventType === 'message-completed' && assistant) {
      assistant.content = payload.content ?? assistant.content;
      assistant.status = 'completed';
    } else if (event.eventType === 'error') {
      if (assistant) assistant.status = 'failed';
      Message.error(payload.message || t('common.error'));
    } else if (event.eventType === 'execution-completed' && assistant) {
      assistant.status = String(payload.status || '').toLowerCase() as ConversationMessage['status'];
    } else if (event.eventType === 'drafts-changed') {
      // reloadDrafts is declared later with the other draft list operations.
      // eslint-disable-next-line no-use-before-define
      reloadDrafts().catch(() => Message.error(t('caseManagement.caseGenerate.loadDraftFailed')));
    }
    if (!assistant && event.eventType === 'message-start') {
      messages.value.push({
        id: payload.messageId || temporaryAssistantId,
        role: 'assistant',
        content: '',
        status: 'streaming',
        requestId: event.requestId,
        resourceType: conversationResourceType.value,
        resourceId: conversationResourceId.value,
      });
    }
  }

  async function recoverAgentExecution(requestId: string, temporaryAssistantId: string, sequence: { value: number }) {
    for (let attempt = 0; attempt < 120; attempt += 1) {
      // Recovery polling is intentionally sequential to preserve event order.
      // eslint-disable-next-line no-await-in-loop
      const recovered = await listAiCaseAgentEvents(currentProjectId.value, requestId, sequence.value);
      recovered.forEach((event) => {
        if (event.sequence > sequence.value) {
          sequence.value = event.sequence;
          handleAgentEvent(event, temporaryAssistantId);
        }
      });
      // Execution status must be checked after applying recovered events.
      // eslint-disable-next-line no-await-in-loop
      const execution = await getAiCaseAgentExecution(currentProjectId.value, requestId);
      if (['COMPLETED', 'FAILED', 'CANCELED'].includes(execution.status)) return;
      // Backoff must complete before the next polling attempt.
      // eslint-disable-next-line no-await-in-loop
      await new Promise<void>((resolve) => {
        window.setTimeout(resolve, 1000);
      });
    }
    throw new Error('Agent 执行仍在后台运行，请稍后刷新会话');
  }

  function scheduleUpdateDraft(draft: AiCaseDraft) {
    window.clearTimeout(updateTimer);
    updateTimer = window.setTimeout(async () => {
      try {
        const saved = await updateAiCaseDraft(draft);
        const index = drafts.value.findIndex((item) => item.id === saved.id);
        if (index >= 0) {
          drafts.value[index] = saved;
        }
      } catch (error) {
        Message.error(t('caseManagement.caseGenerate.autoSaveFailed'));
      }
    }, 800);
  }

  const activeDraft = computed({
    get: () => drafts.value.find((draft) => draft.id === activeDraftId.value),
    set: (next?: AiCaseDraft) => {
      if (!next) {
        return;
      }
      const index = drafts.value.findIndex((draft) => draft.id === next.id);
      if (index >= 0) {
        drafts.value[index] = next;
        scheduleUpdateDraft(next);
      }
    },
  });

  const allChecked = computed(
    () => drafts.value.length > 0 && drafts.value.every((draft) => checkedDraftIds.value.includes(draft.id))
  );

  const gridStyle = computed(() => {
    const rightWidth = Math.max(24, 100 - leftWidth.value - middleWidth.value);
    return {
      gridTemplateColumns: `${leftWidth.value}% 8px ${middleWidth.value}% 8px ${rightWidth}%`,
    };
  });

  async function reloadDrafts() {
    if (!currentProjectId.value) {
      drafts.value = [];
      activeDraftId.value = '';
      draftTotal.value = 0;
      return;
    }
    const response = await pageAiCaseDraft({
      projectId: currentProjectId.value,
      draftStatus: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
      current: draftPage.value,
      pageSize: draftPageSize.value,
    });
    drafts.value = response.records || [];
    draftTotal.value = response.total || 0;
    checkedDraftIds.value = checkedDraftIds.value.filter((id) => drafts.value.some((draft) => draft.id === id));
    if (!drafts.value.some((draft) => draft.id === activeDraftId.value)) {
      activeDraftId.value = drafts.value[0]?.id || '';
    }
  }

  function handleDraftPageSizeChange() {
    draftPage.value = 1;
    reloadDrafts();
  }

  function handleStatusFilterChange() {
    draftPage.value = 1;
    reloadDrafts();
  }

  async function reloadSourceDocuments() {
    if (!currentProjectId.value) {
      sourceDocuments.value = [];
      selectedSourceDocumentIds.value = [];
      return;
    }
    const response = await pageAiSourceDocument({
      projectId: currentProjectId.value,
      current: 1,
      pageSize: 20,
    });
    sourceDocuments.value = response.records || [];
    selectedSourceDocumentIds.value = selectedSourceDocumentIds.value.filter((id) =>
      sourceDocuments.value.some((document) => document.id === id && document.parseStatus === 'PARSED')
    );
  }

  async function sendPrompt() {
    const content = prompt.value.trim();
    if (!content || !currentProjectId.value || !currentOrgId.value || !chatModelId.value.trim()) {
      return;
    }
    generating.value = true;
    const requestId = createId('request');
    const temporaryAssistantId = createId('assistant');
    activeGenerationId.value = requestId;
    try {
      await ensureConversation();
      messages.value.push({
        id: createId('message'),
        role: 'user',
        content,
        status: 'completed',
        requestId,
        resourceType: selectedResource.value?.resourceType,
        resourceId: selectedResource.value?.id,
      });
      messages.value.push({
        id: temporaryAssistantId,
        role: 'assistant',
        content: '',
        status: 'streaming',
        requestId,
        resourceType: selectedResource.value?.resourceType,
        resourceId: selectedResource.value?.id,
      });
      prompt.value = '';
      const eventSequence = { value: 0 };
      const stream = streamAiCaseAgentChat(
        {
          projectId: currentProjectId.value,
          conversationId: conversationId.value,
          requestId,
          message: content,
          resourceType: selectedResource.value?.resourceType,
          resourceId: selectedResource.value?.id,
          modelSourceId: selectedResource.value?.resourceType === 'MODEL_API' ? selectedResource.value.id : undefined,
        },
        (event) => {
          if (event.sequence <= eventSequence.value) return;
          eventSequence.value = event.sequence;
          handleAgentEvent(event, temporaryAssistantId);
        }
      );
      abortAgentStream = stream.abort;
      await stream.promise;
      const assistant = messages.value.find((item) => item.requestId === requestId && item.role === 'assistant');
      if (assistant?.status === 'streaming') {
        await recoverAgentExecution(requestId, temporaryAssistantId, eventSequence);
      }
      localStorage.setItem('case-generate-chat-model-id', chatModelId.value.trim());
      localStorage.setItem('aiChatModel', chatModelId.value.trim());
      await reloadDrafts();
    } catch (error: any) {
      const assistant = messages.value.find((item) => item.requestId === requestId && item.role === 'assistant');
      if (assistant && assistant.status === 'streaming') assistant.status = 'failed';
      if (error?.name === 'AbortError') {
        Message.info(t('caseManagement.caseGenerate.canceled'));
      } else {
        Message.error(error?.message || t('common.error'));
      }
    } finally {
      generating.value = false;
      activeGenerationId.value = '';
      abortAgentStream = undefined;
    }
  }

  async function stopGenerate() {
    const requestId = activeGenerationId.value;
    if (requestId && currentProjectId.value) {
      try {
        await cancelAiCaseAgentChat({ projectId: currentProjectId.value, requestId });
      } catch {
        // ignore cancel race
      }
    }
    abortAgentStream?.();
    generating.value = false;
    activeGenerationId.value = '';
    Message.info(t('caseManagement.caseGenerate.canceled'));
  }

  function selectDraft(id: string) {
    activeDraftId.value = id;
  }

  function openMobileDetail(id: string) {
    selectDraft(id);
    detailDrawerVisible.value = true;
  }

  function toggleDraft(id: string) {
    checkedDraftIds.value = checkedDraftIds.value.includes(id)
      ? checkedDraftIds.value.filter((item) => item !== id)
      : [...checkedDraftIds.value, id];
  }

  function toggleAll() {
    checkedDraftIds.value = allChecked.value ? [] : drafts.value.map((draft) => draft.id);
  }

  async function deleteChecked() {
    if (!currentProjectId.value || checkedDraftIds.value.length === 0) {
      return;
    }
    await deleteAiCaseDraft({ projectId: currentProjectId.value, draftIds: checkedDraftIds.value });
    Message.success(t('common.deleteSuccess'));
    await reloadDrafts();
  }

  async function regenerateActive() {
    if (
      !activeDraft.value ||
      !currentProjectId.value ||
      !currentOrgId.value ||
      !chatModelId.value.trim() ||
      selectedResource.value?.resourceType !== 'MODEL_API'
    ) {
      return;
    }
    const response = await regenerateAiCaseDraft({
      projectId: currentProjectId.value,
      draftId: activeDraft.value.id,
      prompt: prompt.value,
      chatModelId: chatModelId.value.trim(),
      conversationId: conversationId.value,
      organizationId: currentOrgId.value,
    });
    drafts.value = [...(response.drafts || []), ...drafts.value];
    activeDraftId.value = response.drafts?.[0]?.id || activeDraftId.value;
  }

  async function handleFileChange(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file || !currentProjectId.value) {
      return;
    }
    uploading.value = true;
    try {
      await ensureConversation();
      const document = await uploadAiSourceDocument({
        request: { projectId: currentProjectId.value, conversationId: conversationId.value },
        file,
      });
      sourceDocuments.value = [document, ...sourceDocuments.value];
      Message.success(t('caseManagement.caseGenerate.uploadSuccess'));
      window.setTimeout(reloadSourceDocuments, 1500);
    } finally {
      uploading.value = false;
      if (fileInputRef.value) {
        fileInputRef.value.value = '';
      }
    }
  }

  function toggleSourceDocument(id: string) {
    const document = sourceDocuments.value.find((item) => item.id === id);
    if (!document || document.parseStatus !== 'PARSED') {
      return;
    }
    selectedSourceDocumentIds.value = selectedSourceDocumentIds.value.includes(id)
      ? selectedSourceDocumentIds.value.filter((item) => item !== id)
      : [...selectedSourceDocumentIds.value, id];
  }

  async function retrySourceDocument(id: string) {
    if (!currentProjectId.value) {
      return;
    }
    await retryAiSourceDocument({ projectId: currentProjectId.value, id });
    Message.success(t('caseManagement.caseGenerate.retrySubmitted'));
    await reloadSourceDocuments();
  }

  function batchSave() {
    if (!currentProjectId.value || checkedDraftIds.value.length === 0) {
      return;
    }
    openModal({
      type: 'warning',
      title: '确认保存为正式用例',
      content: `将把选中的 ${checkedDraftIds.value.length} 条草稿逐条保存为正式功能用例，保存成功后不可作为草稿继续编辑。`,
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      hideCancel: false,
      // executeBatchSave is declared next to keep the modal trigger readable.
      // eslint-disable-next-line no-use-before-define
      onBeforeOk: executeBatchSave,
    });
  }

  async function executeBatchSave() {
    saving.value = true;
    try {
      const response = await batchSaveAiCaseDraft({
        projectId: currentProjectId.value,
        draftIds: checkedDraftIds.value,
        confirmed: true,
      });
      if (response.failureCount > 0) {
        Message.warning(
          `${t('caseManagement.caseGenerate.savePartial')}${response.successCount}/${response.results.length}`
        );
      } else {
        Message.success(t('common.saveSuccess'));
      }
      await reloadDrafts();
    } finally {
      saving.value = false;
    }
  }

  function getStatusText(status: AiDraftStatus) {
    const map: Record<AiDraftStatus, string> = {
      DRAFT: t('caseManagement.caseGenerate.statusDraft'),
      VALIDATING: t('caseManagement.caseGenerate.statusValidating'),
      INVALID: t('caseManagement.caseGenerate.statusInvalid'),
      READY: t('caseManagement.caseGenerate.statusReady'),
      SAVING: t('caseManagement.caseGenerate.statusSaving'),
      SAVED: t('caseManagement.caseGenerate.statusSaved'),
      FAILED: t('caseManagement.caseGenerate.statusFailed'),
    };
    return map[status] || status;
  }

  function getStatusColor(status: AiDraftStatus) {
    const map: Record<AiDraftStatus, string> = {
      DRAFT: 'blue',
      VALIDATING: 'arcoblue',
      INVALID: 'red',
      READY: 'green',
      SAVING: 'arcoblue',
      SAVED: 'green',
      FAILED: 'red',
    };
    return map[status] || 'gray';
  }

  function getDocumentStatusText(status: AiSourceDocument['parseStatus']) {
    const map: Record<AiSourceDocument['parseStatus'], string> = {
      UPLOADED: t('caseManagement.caseGenerate.documentUploaded'),
      PARSING: t('caseManagement.caseGenerate.documentParsing'),
      PARSED: t('caseManagement.caseGenerate.documentParsed'),
      GENERATING: t('caseManagement.caseGenerate.statusGenerating'),
      GENERATED: t('caseManagement.caseGenerate.statusSaved'),
      FAILED: t('caseManagement.caseGenerate.statusFailed'),
    };
    return map[status] || status;
  }

  function getDocumentStatusColor(status: AiSourceDocument['parseStatus']) {
    const map: Record<AiSourceDocument['parseStatus'], string> = {
      UPLOADED: 'gray',
      PARSING: 'arcoblue',
      PARSED: 'green',
      GENERATING: 'arcoblue',
      GENERATED: 'green',
      FAILED: 'red',
    };
    return map[status] || 'gray';
  }

  function startResize(type: 'left' | 'middle', event: MouseEvent) {
    const startX = event.clientX;
    const startLeft = leftWidth.value;
    const startMiddle = middleWidth.value;
    const container = (event.currentTarget as HTMLElement).parentElement;
    const containerWidth = container?.clientWidth || 1;
    const onMove = (moveEvent: MouseEvent) => {
      const delta = ((moveEvent.clientX - startX) / containerWidth) * 100;
      if (type === 'left') {
        leftWidth.value = Math.min(45, Math.max(24, startLeft + delta));
        return;
      }
      middleWidth.value = Math.min(45, Math.max(26, startMiddle + delta));
    };
    const onUp = () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }

  watch(
    currentProjectId,
    async () => {
      unsubscribeDocumentEvents?.();
      unsubscribeDocumentEvents = undefined;
      loadLocalState();
      await Promise.all([loadAgentWorkspace(), reloadDrafts(), reloadSourceDocuments()]);
      if (currentProjectId.value) {
        unsubscribeDocumentEvents = subscribeAiSourceDocumentEvents(
          currentProjectId.value,
          (event) => {
            const document = sourceDocuments.value.find((item) => item.id === event.documentId);
            if (document) {
              document.parseStatus = event.status as AiSourceDocument['parseStatus'];
              document.errorMessage = event.message;
            }
            if (event.status === 'PARSED' || event.status === 'FAILED') reloadSourceDocuments();
          },
          undefined,
          reloadSourceDocuments
        );
      }
    },
    { immediate: true }
  );
  watch([selectedSourceDocumentIds, conversationId], persistLocalState, { deep: true });

  onBeforeUnmount(() => {
    window.clearTimeout(updateTimer);
    unsubscribeDocumentEvents?.();
    abortAgentStream?.();
  });
</script>

<style scoped>
  .case-generate-page {
    padding: 16px;
    height: 100%;
    min-height: calc(100vh - 128px);
    background: var(--color-fill-2);
  }
  .case-generate-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16px;
    gap: 16px;
  }
  .case-generate-alert {
    width: 520px;
    max-width: 48%;
  }
  .case-generate-workbench {
    display: grid;
    height: calc(100vh - 190px);
    min-height: 620px;
  }
  .case-generate-panel {
    overflow: hidden;
    padding: 16px;
    min-width: 0;
    border: 1px solid var(--color-border-2);
    border-radius: 6px;
    background: var(--color-bg-2);
  }
  .case-generate-panel-title {
    margin-bottom: 12px;
    font-size: 16px;
    font-weight: 500;
    color: var(--color-text-1);
  }
  .case-generate-resizer {
    cursor: col-resize;
  }
  .case-generate-resizer::after {
    display: block;
    margin: 0 auto;
    width: 2px;
    height: 100%;
    background: transparent;
    content: '';
  }
  .case-generate-resizer:hover::after {
    background: rgb(var(--primary-5));
  }
  .case-generate-messages {
    overflow: auto;
    margin-bottom: 12px;
    height: calc(100% - 470px);
    min-height: 180px;
  }
  .case-generate-source-list {
    overflow: auto;
    margin-bottom: 12px;
    max-height: 150px;
  }
  .case-generate-source-item {
    display: flex;
    align-items: flex-start;
    margin-bottom: 6px;
    padding: 8px;
    border: 1px solid var(--color-border-2);
    border-radius: 6px;
    gap: 8px;
    cursor: pointer;
  }
  .case-generate-source-item.active {
    border-color: rgb(var(--primary-5));
    background: rgb(var(--primary-1));
  }
  .case-generate-message {
    margin-bottom: 8px;
    padding: 10px;
    border-radius: 6px;
  }
  .case-generate-message.user {
    background: rgb(var(--primary-1));
  }
  .case-generate-message.assistant {
    background: var(--color-fill-2);
  }
  .case-generate-message-role {
    margin-bottom: 4px;
    font-size: 12px;
    color: var(--color-text-3);
  }
  .case-generate-message-content {
    white-space: pre-wrap;
  }
  .case-generate-draft-list {
    overflow: auto;
    height: calc(100% - 104px);
  }
  .case-generate-draft-item {
    display: flex;
    align-items: flex-start;
    margin-bottom: 8px;
    padding: 12px;
    border: 1px solid var(--color-border-2);
    border-radius: 6px;
    gap: 8px;
    cursor: pointer;
  }
  .case-generate-draft-item.active {
    border-color: rgb(var(--primary-5));
    background: rgb(var(--primary-1));
  }
  .detail-button {
    display: none;
  }
  .case-generate-detail-panel {
    overflow: auto;
  }

  @media (max-width: 1280px) {
    .case-generate-workbench {
      grid-template-columns: 40% 8px 1fr !important;
    }
    .case-generate-workbench > .case-generate-resizer:nth-of-type(2),
    .case-generate-detail-panel {
      display: none;
    }
    .detail-button {
      display: inline-flex;
    }
    .case-generate-alert {
      display: none;
    }
  }
</style>
