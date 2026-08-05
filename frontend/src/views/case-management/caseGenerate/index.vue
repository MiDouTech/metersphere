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
          <a-form-item :label="t('caseManagement.caseGenerate.model')">
            <a-select
              v-model:model-value="chatModelId"
              :options="modelOptions"
              allow-search
              :placeholder="t('caseManagement.caseGenerate.modelPlaceholder')"
            />
          </a-form-item>
          <a-form-item :label="t('caseManagement.caseGenerate.maxCases')">
            <a-input-number v-model:model-value="maxCases" :min="1" :max="100" class="w-full" />
          </a-form-item>
        </a-form>
        <input ref="fileInputRef" class="hidden" type="file" @change="handleFileChange" />
        <a-button class="mb-[12px] w-full" :loading="uploading" @click="fileInputRef?.click()">
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
            <div class="case-generate-message-role">{{ message.role === 'user' ? 'User' : 'AI' }}</div>
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
          <a-button size="small" :disabled="!activeDraft || !chatModelId.trim()" @click="regenerateActive">
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
  import { Message } from '@arco-design/web-vue';

  import DraftDetailForm from './components/DraftDetailForm.vue';

  import { AxiosCanceler } from '@/api/http/axiosCancel';
  import {
    batchSaveAiCaseDraft,
    cancelAiCaseGeneration,
    deleteAiCaseDraft,
    generateAiCaseDraft,
    pageAiCaseDraft,
    pageAiSourceDocument,
    regenerateAiCaseDraft,
    retryAiSourceDocument,
    updateAiCaseDraft,
    uploadAiSourceDocument,
  } from '@/api/modules/case-management/caseGenerate';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';
  import useAIStore from '@/store/modules/setting/ai';

  import type { AiCaseDraft, AiDraftStatus, AiSourceDocument } from '@/models/caseManagement/caseGenerate';
  import { CaseManagementRouteEnum } from '@/enums/routeEnum';

  defineOptions({
    name: CaseManagementRouteEnum.CASE_MANAGEMENT_CASE_GENERATE,
  });

  interface ConversationMessage {
    id: string;
    role: 'user' | 'assistant';
    content: string;
  }

  const { t } = useI18n();
  const appStore = useAppStore();
  const aiStore = useAIStore();
  const currentProjectId = computed(() => appStore.currentProjectId || '');
  const currentOrgId = computed(() => appStore.currentOrgId || '');
  const localStateKey = computed(() => `case-generate-workbench:${currentProjectId.value || 'none'}`);

  const prompt = ref('');
  const chatModelId = ref(
    localStorage.getItem('case-generate-chat-model-id') || localStorage.getItem('aiChatModel') || ''
  );
  const maxCases = ref(50);
  const generationForm = {};
  const conversationId = ref(`ai_case_conversation_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`);
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

  const modelOptions = computed(() =>
    (aiStore.aiSourceNameList || []).map((item) => ({
      label: item.name,
      value: item.id,
    }))
  );

  function createId(prefix: string) {
    return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  }

  function loadLocalState() {
    const raw = localStorage.getItem(localStateKey.value);
    if (!raw) {
      messages.value = [];
      selectedSourceDocumentIds.value = [];
      return;
    }
    try {
      const parsed = JSON.parse(raw);
      messages.value = Array.isArray(parsed.messages) ? parsed.messages : [];
      selectedSourceDocumentIds.value = Array.isArray(parsed.selectedSourceDocumentIds)
        ? parsed.selectedSourceDocumentIds
        : [];
      if (parsed.conversationId) {
        conversationId.value = parsed.conversationId;
      }
    } catch {
      messages.value = [];
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
        messages: messages.value,
        selectedSourceDocumentIds: selectedSourceDocumentIds.value,
      })
    );
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
    const generationId = createId('generation');
    activeGenerationId.value = generationId;
    messages.value.push({ id: createId('message'), role: 'user', content });
    try {
      const response = await generateAiCaseDraft({
        projectId: currentProjectId.value,
        prompt: content,
        chatModelId: chatModelId.value.trim(),
        conversationId: conversationId.value,
        organizationId: currentOrgId.value,
        maxCases: maxCases.value,
        sourceDocumentIds: selectedSourceDocumentIds.value,
        generationId,
      });
      messages.value.push({
        id: createId('message'),
        role: 'assistant',
        content: `${t('caseManagement.caseGenerate.generatedCount')}${response.createdCount}`,
      });
      draftPage.value = 1;
      await reloadDrafts();
      activeDraftId.value = response.drafts?.[0]?.id || activeDraftId.value;
      prompt.value = '';
      localStorage.setItem('case-generate-chat-model-id', chatModelId.value.trim());
      localStorage.setItem('aiChatModel', chatModelId.value.trim());
      if (response.warnings?.length) {
        Message.warning(response.warnings.join('; '));
      }
    } catch (error: any) {
      if (error?.message?.includes('cancel') || error?.__CANCEL__) {
        Message.info(t('caseManagement.caseGenerate.canceled'));
      } else {
        Message.error(error?.message || t('common.error'));
      }
    } finally {
      generating.value = false;
      activeGenerationId.value = '';
    }
  }

  async function stopGenerate() {
    const generationId = activeGenerationId.value;
    generating.value = false;
    const axiosCanceler = new AxiosCanceler();
    axiosCanceler.removeAllPending();
    if (generationId && currentProjectId.value) {
      try {
        await cancelAiCaseGeneration({
          projectId: currentProjectId.value,
          generationId,
        });
      } catch {
        // ignore cancel race
      }
    }
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
    if (!activeDraft.value || !currentProjectId.value || !currentOrgId.value || !chatModelId.value.trim()) {
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

  async function batchSave() {
    if (!currentProjectId.value || checkedDraftIds.value.length === 0) {
      return;
    }
    saving.value = true;
    try {
      const response = await batchSaveAiCaseDraft({
        projectId: currentProjectId.value,
        draftIds: checkedDraftIds.value,
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
    () => {
      loadLocalState();
      reloadDrafts();
      reloadSourceDocuments();
    },
    { immediate: true }
  );
  watch(
    () => modelOptions.value,
    (vals) => {
      if (!vals.length) {
        return;
      }
      if (!vals.some((item) => item.value === chatModelId.value)) {
        chatModelId.value = vals[0].value;
      }
    },
    { immediate: true }
  );
  watch([messages, selectedSourceDocumentIds, conversationId], persistLocalState, { deep: true });

  aiStore.getAISourceNameList();

  onBeforeUnmount(() => {
    window.clearTimeout(updateTimer);
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
