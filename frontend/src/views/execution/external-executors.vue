<template>
  <div class="p-4">
    <h2>执行器监控与纠正</h2>
    <p>执行器独立探索和测试；MSP 记录过程、固定业务理解版本并下发纠正指令。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <a-button :loading="loading" @click="load">刷新执行器</a-button>
    <a-table :data="sessions" :loading="loading" row-key="id"
      ><template #columns>
        <a-table-column title="执行器" data-index="executorId" /><a-table-column title="会话" data-index="id" />
        <a-table-column title="状态"
          ><template #cell="{ record }">{{
            record.disconnected ? '连接中断，等待恢复' : record.status
          }}</template></a-table-column
        >
        <a-table-column title="操作"
          ><template #cell="{ record }"><a-link @click="open(record)">执行过程与纠正</a-link></template></a-table-column
        >
      </template></a-table
    >
    <a-drawer v-model:visible="visible" title="执行过程与纠正" :width="900">
      <template v-if="detail">
        <h3>执行事件</h3>
        <a-table :data="eventRows" :pagination="false" row-key="id"
          ><template #columns>
            <a-table-column title="事件" data-index="eventType" /><a-table-column
              title="执行观察"
              data-index="summary"
            /><a-table-column title="阶段" data-index="stage" /><a-table-column
              title="错误分类"
              data-index="errorCategory"
            /><a-table-column title="结果" data-index="outcome" /> </template
        ></a-table>
        <a-pagination :current="eventPage" :total="detail.eventTotal" :page-size="100" @change="loadEventHistory" />
        <h3>纠正指令与响应</h3>
        <a-list
          ><a-list-item v-for="command in commandRows" :key="command.id"
            >{{ command.instruction }} · {{ command.status }} ·
            {{ command.responseCategory || '等待执行器响应' }}</a-list-item
          ></a-list
        >
        <a-pagination
          :current="commandPage"
          :total="detail.commandTotal"
          :page-size="100"
          @change="loadCommandHistory"
        />
        <form v-if="canWrite && selected?.status === 'RUNNING'" aria-label="纠正执行" @submit.prevent="correct">
          <label for="correction-instruction">纠正内容（不要填写密码等秘密）</label>
          <textarea
            id="correction-instruction"
            v-model="instruction"
            name="instruction"
            rows="5"
            required
            maxlength="5000"
            class="w-full border p-2"
          />
          <button type="submit" :disabled="sending">{{ sending ? '正在下发' : '下发纠正指令' }}</button>
        </form>
        <a-button @click="refreshDetail">刷新过程和响应</a-button>
        <form
          v-if="canWrite && selected?.status === 'RUNNING'"
          aria-label="更新业务理解上下文"
          @submit.prevent="updateContext"
        >
          <h3>替换本次执行使用的业务理解</h3>
          <label for="context-asset">选择业务资产当前版本</label>
          <select id="context-asset" v-model="contextAssetId" name="context-asset" required
            ><option value="">请选择</option
            ><option
              v-for="item in availableAssets.filter((asset) => asset.currentVersionId)"
              :key="item.id"
              :value="item.id"
              >{{ item.title }}</option
            ></select
          >
          <label for="context-reason">替换原因</label
          ><input id="context-reason" v-model="contextReason" name="context-reason" required maxlength="500" />
          <button type="submit" :disabled="sending">更新上下文并通知执行器</button>
        </form>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue';

  import MSR from '@/api/http/index';
  import type { BusinessRow } from '@/api/modules/business-assets';
  import { listBusinessAssets } from '@/api/modules/business-assets';
  import useAppStore from '@/store/modules/app';
  import businessAssetError from '@/utils/businessAssetError';
  import { hasAnyPermission } from '@/utils/permission';

  interface Session {
    id: string;
    executorId: string;
    status: string;
    disconnected: boolean;
  }
  interface Detail {
    eventTotal: number;
    commandTotal: number;
    events: { id: string; summary: string; eventType: string; stage: string; errorCategory: string; outcome: string }[];
    commands: { id: string; instruction: string; status: string; responseCategory: string | null }[];
  }
  const app = useAppStore();
  const sessions = ref<Session[]>([]);
  const selected = ref<Session>();
  const detail = ref<Detail>();
  const commandRows = ref<Detail['commands']>([]);
  const commandPage = ref(1);
  const eventRows = ref<Detail['events']>([]);
  const eventPage = ref(1);
  const visible = ref(false);
  const error = ref('');
  const loading = ref(false);
  const sending = ref(false);
  const instruction = ref('');
  const availableAssets = ref<BusinessRow[]>([]);
  const contextAssetId = ref('');
  const contextReason = ref('');
  const contextPins = ref<{ assetId: string; versionId: string }[]>([]);
  const canWrite = computed(() => hasAnyPermission(['BUSINESS_ASSET:WRITE']));
  let generation = 0;
  let contextGeneration = 0;
  function activeContext(project: string, id: string, context: number) {
    return (
      context === contextGeneration && project === app.currentProjectId && selected.value?.id === id && visible.value
    );
  }
  async function load() {
    const run = ++generation;
    loading.value = true;
    try {
      const rows = app.currentProjectId
        ? await MSR.get<Session[]>({ url: '/external-executors', params: { projectId: app.currentProjectId } })
        : [];
      if (run === generation) {
        sessions.value = rows;
        if (selected.value) selected.value = rows.find((row) => row.id === selected.value?.id);
        error.value = '';
      }
    } catch (failure) {
      if (run === generation) error.value = businessAssetError(failure, '执行器状态读取失败，请检查权限或网络。');
    } finally {
      if (run === generation) loading.value = false;
    }
  }
  async function refreshDetail() {
    if (!selected.value || !visible.value) return;
    const { id } = selected.value;
    const project = app.currentProjectId;
    const context = contextGeneration;
    try {
      const value = await MSR.get<Detail>({ url: `/external-executors/${id}`, params: { projectId: project } });
      if (activeContext(project, id, context)) {
        detail.value = value;
        if (commandPage.value === 1) commandRows.value = value.commands;
        if (eventPage.value === 1) eventRows.value = value.events;
      }
    } catch (failure) {
      if (activeContext(project, id, context)) error.value = businessAssetError(failure, '执行过程读取失败，请重试。');
    }
  }
  async function loadCommandHistory(page: number) {
    if (!selected.value) return;
    const { id } = selected.value;
    const project = app.currentProjectId;
    const context = contextGeneration;
    try {
      const value = await MSR.get<{ records: Detail['commands']; total: number }>({
        url: `/external-executors/${id}/command-history`,
        params: { projectId: project, page, size: 100 },
      });
      if (activeContext(project, id, context)) {
        commandRows.value = value.records;
        commandPage.value = page;
        if (detail.value) detail.value.commandTotal = value.total;
      }
    } catch (failure) {
      if (activeContext(project, id, context)) error.value = businessAssetError(failure, '纠正历史读取失败，请重试。');
    }
  }
  async function loadEventHistory(page: number) {
    if (!selected.value) return;
    const { id } = selected.value;
    const project = app.currentProjectId;
    const context = contextGeneration;
    try {
      const value = await MSR.get<{ records: Detail['events']; total: number }>({
        url: `/external-executors/${id}/event-history`,
        params: { projectId: project, page, size: 100 },
      });
      if (activeContext(project, id, context)) {
        eventRows.value = value.records;
        eventPage.value = page;
        if (detail.value) detail.value.eventTotal = value.total;
      }
    } catch (failure) {
      if (activeContext(project, id, context)) error.value = businessAssetError(failure, '执行事件读取失败，请重试。');
    }
  }
  async function open(session: Session) {
    const project = app.currentProjectId;
    const context = ++contextGeneration;
    eventPage.value = 1;
    eventRows.value = [];
    commandPage.value = 1;
    commandRows.value = [];
    availableAssets.value = [];
    contextPins.value = [];
    contextAssetId.value = '';
    contextReason.value = '';
    selected.value = session;
    instruction.value = '';
    detail.value = undefined;
    visible.value = true;
    await refreshDetail();
    if (!activeContext(project, session.id, context)) return;
    try {
      const [assets, pins] = await Promise.all([
        listBusinessAssets(project),
        MSR.get<{ assetId: string; versionId: string }[]>({
          url: `/external-executors/${session.id}/context`,
          params: { projectId: project },
        }),
      ]);
      if (activeContext(project, session.id, context)) {
        availableAssets.value = assets;
        contextPins.value = pins;
        contextAssetId.value = '';
        contextReason.value = '';
      }
    } catch (failure) {
      if (activeContext(project, session.id, context))
        error.value = businessAssetError(failure, '业务理解上下文加载失败。');
    }
  }
  async function correct() {
    if (!selected.value || sending.value) return;
    sending.value = true;
    const project = app.currentProjectId;
    const session = selected.value.id;
    const context = contextGeneration;
    try {
      await MSR.post({
        url: `/external-executors/${session}/commands`,
        data: { projectId: project, instruction: instruction.value },
      });
      if (!activeContext(project, session, context)) return;
      instruction.value = '';
      await refreshDetail();
    } catch (failure) {
      if (activeContext(project, session, context))
        error.value = businessAssetError(failure, '纠正指令下发失败，输入已保留，请刷新状态后重试。');
    } finally {
      sending.value = false;
    }
  }
  async function updateContext() {
    const asset = availableAssets.value.find((item) => item.id === contextAssetId.value);
    if (!selected.value || !asset?.currentVersionId || sending.value) return;
    sending.value = true;
    const project = app.currentProjectId;
    const session = selected.value.id;
    const context = contextGeneration;
    try {
      await MSR.post({
        url: `/external-executors/${session}/context`,
        data: {
          projectId: project,
          assetId: asset.id,
          versionId: asset.currentVersionId,
          expectedVersionId: contextPins.value.find((pin) => pin.assetId === asset.id)?.versionId || '',
          reason: contextReason.value,
        },
      });
      if (!activeContext(project, session, context)) return;
      const old = contextPins.value.find((pin) => pin.assetId === asset.id);
      if (old) old.versionId = asset.currentVersionId;
      else contextPins.value.push({ assetId: asset.id, versionId: asset.currentVersionId });
      contextReason.value = '';
      await refreshDetail();
    } catch (failure) {
      if (activeContext(project, session, context))
        error.value = businessAssetError(failure, '上下文更新失败，请刷新后重新选择。');
    } finally {
      sending.value = false;
    }
  }
  const timer = window.setInterval(() => {
    load();
    refreshDetail();
  }, 10000);
  onBeforeUnmount(() => {
    window.clearInterval(timer);
    ++generation;
    ++contextGeneration;
  });
  watch(
    () => app.currentProjectId,
    () => {
      ++contextGeneration;
      sessions.value = [];
      selected.value = undefined;
      availableAssets.value = [];
      contextPins.value = [];
      contextAssetId.value = '';
      contextReason.value = '';
      visible.value = false;
      load();
    },
    { immediate: true }
  );
</script>
