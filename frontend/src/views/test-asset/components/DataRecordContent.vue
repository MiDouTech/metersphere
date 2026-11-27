<template>
  <section aria-label="数据记录">
    <a-space wrap
      ><a-button type="primary" :disabled="!selected.length || loading || busy || !!pending" @click="copy(selected)"
        >批量复制</a-button
      ><a-button v-if="pending" :loading="busy" @click="retryMark">仅重试已使用标记</a-button
      ><a-button :disabled="loading" @click="load">刷新记录</a-button></a-space
    >
    <p role="status">{{ message }}</p
    ><a-alert v-if="error" type="error">{{ error }}</a-alert>
    <div class="record-region" role="region" aria-label="完整数据记录" tabindex="0">
      <a-table
        v-model:selected-keys="selected"
        :data="rows"
        row-key="recordKey"
        :loading="loading"
        :row-selection="{ type: 'checkbox', showCheckedAll: true }"
        :pagination="{ current: page, pageSize: 100, total }"
        @page-change="changePage"
      >
        <template #columns
          ><a-table-column title="序号" :width="80"
            ><template #cell="{ record }">{{ record.recordIndex + 1 }}</template></a-table-column
          ><a-table-column title="数据内容"
            ><template #cell="{ record }"
              ><button
                type="button"
                class="record-copy"
                :disabled="loading || busy || !!pending"
                @click="copy([record.recordKey])"
              >
                <pre>{{ display(record.content) }}</pre>
              </button></template
            ></a-table-column
          ><a-table-column title="使用状态" :width="110"
            ><template #cell="{ record }">{{ record.used ? '已使用' : '未使用' }}</template></a-table-column
          ></template
        >
      </a-table>
    </div>
  </section>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

  import {
    copyDataRecords,
    type DataRecord,
    getDataRecords,
    markDataRecordsUsed,
  } from '@/api/modules/test-data-assets';
  import useAppStore from '@/store/modules/app';
  import useUserStore from '@/store/modules/user';
  import businessAssetError from '@/utils/businessAssetError';

  const props = defineProps<{ assetId: string; versionId: string }>();
  const app = useAppStore();
  const user = useUserStore();
  const rows = ref<DataRecord[]>([]);
  const selected = ref<string[]>([]);
  const total = ref(0);
  const page = ref(1);
  const loading = ref(false);
  const busy = ref(false);
  const error = ref('');
  const message = ref('');
  interface OutboxJob {
    keys: string[];
    requestId: string;
    versionId: string;
    organization: string;
    actor: string;
    assetId: string;
    storageKey?: string;
  }
  const pending = ref<OutboxJob>();
  let active = true;
  const outboxPrefix = (job: Pick<OutboxJob, 'actor' | 'organization' | 'assetId'>) =>
    `data-record-outbox:${encodeURIComponent(job.actor)}:${encodeURIComponent(job.organization)}:${encodeURIComponent(
      job.assetId
    )}`;
  const currentScope = () => ({ actor: String(user.id), organization: app.currentOrgId, assetId: props.assetId });
  const belongsHere = (job: OutboxJob) =>
    job.actor === String(user.id) && job.organization === app.currentOrgId && job.assetId === props.assetId;
  function restoreOutbox() {
    try {
      const prefix = outboxPrefix(currentScope());
      pending.value = undefined;
      for (let index = 0; index < sessionStorage.length; index++) {
        const key = sessionStorage.key(index);
        if (key && (key === prefix || key.startsWith(`${prefix}:`))) {
          const job = JSON.parse(sessionStorage.getItem(key) || 'null');
          if (
            job &&
            belongsHere(job) &&
            typeof job.versionId === 'string' &&
            typeof job.requestId === 'string' &&
            Array.isArray(job.keys) &&
            job.keys.length > 0 &&
            job.keys.every((recordKey: unknown) => typeof recordKey === 'string')
          ) {
            pending.value = { ...job, storageKey: key };
            message.value = '发现待保存的使用状态，请仅重试标记。';
            return;
          }
        }
      }
    } catch {
      error.value = '无法读取待标记记录，请检查浏览器存储设置。';
    }
  }
  function notifyOutbox() {
    window.dispatchEvent(new window.Event('data-record-outbox-updated'));
  }
  function outboxUpdated() {
    if (active && !busy.value) restoreOutbox();
  }
  onMounted(() => window.addEventListener('data-record-outbox-updated', outboxUpdated));
  let generation = 0;
  let read = 0;
  function display(value: unknown) {
    return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
  }
  async function load() {
    const token = generation;
    const request = ++read;
    loading.value = true;
    rows.value = [];
    selected.value = [];
    try {
      const result = await getDataRecords(props.assetId, props.versionId, page.value);
      if (token !== generation || request !== read) return;
      rows.value = result.items;
      total.value = result.total;
    } catch (reason) {
      if (token === generation && request === read) error.value = businessAssetError(reason, '记录加载失败，请重试。');
    } finally {
      if (token === generation && request === read) loading.value = false;
    }
  }
  function changePage(value: number) {
    page.value = value;
    selected.value = [];
    load();
  }
  async function retryMark() {
    const job = pending.value;
    if (!active || !job || busy.value || !belongsHere(job)) return;
    const token = generation;
    busy.value = true;
    try {
      await markDataRecordsUsed(job.assetId, job.versionId, job.keys, job.requestId);
      const key = job.storageKey || `${outboxPrefix(job)}:${encodeURIComponent(job.requestId)}`;
      const raw = sessionStorage.getItem(key);
      if (raw && JSON.parse(raw).requestId === job.requestId) sessionStorage.removeItem(key);
      notifyOutbox();
      if (token !== generation || !active || !belongsHere(job)) return;
      rows.value.forEach((row) => {
        if (job.keys.includes(row.recordKey)) row.used = true;
      });
      pending.value = undefined;
      error.value = '';
      message.value = '已复制，使用状态已保存';
      restoreOutbox();
    } catch (reason) {
      if (token === generation && active)
        error.value = businessAssetError(reason, '已复制，但使用状态保存失败，请仅重试标记。');
    } finally {
      if (token === generation && active) busy.value = false;
    }
  }
  async function copy(keys: string[]) {
    if (!active || loading.value || busy.value || pending.value || !keys.length) return;
    const token = generation;
    const job: OutboxJob = {
      ...currentScope(),
      versionId: props.versionId,
      keys: [...keys],
      requestId: crypto.randomUUID(),
    };
    const key = `${outboxPrefix(job)}:${encodeURIComponent(job.requestId)}`;
    busy.value = true;
    error.value = '';
    message.value = '';
    try {
      const result = await copyDataRecords(job.assetId, job.versionId, job.keys);
      if (token !== generation || !active || !belongsHere(job)) return;
      await navigator.clipboard.writeText(result.content);
      // Clipboard success is an external side effect: preserve its original ownership even after view disposal.
      try {
        sessionStorage.setItem(key, JSON.stringify(job));
      } catch {
        if (token === generation && active && belongsHere(job)) {
          pending.value = { ...job, storageKey: key };
          error.value = '已复制，但浏览器无法保存待标记记录；离开页面前请重试标记。';
        }
        return;
      }
      notifyOutbox();
      if (token !== generation || !active || !belongsHere(job)) return;
      pending.value = { ...job, storageKey: key };
      message.value = '已复制，正在保存使用状态';
    } catch (reason) {
      if (token === generation && active)
        error.value = businessAssetError(reason, '复制失败，请检查剪贴板权限后重试。');
    } finally {
      if (token === generation && active) busy.value = false;
    }
    if (token === generation && active && pending.value) await retryMark();
  }
  watch(
    [() => props.assetId, () => props.versionId, () => app.currentOrgId, () => user.id],
    () => {
      ++generation;
      ++read;
      rows.value = [];
      selected.value = [];
      total.value = 0;
      page.value = 1;
      pending.value = undefined;
      busy.value = false;
      error.value = '';
      message.value = '';
      restoreOutbox();
      load();
    },
    { immediate: true }
  );
  onBeforeUnmount(() => {
    active = false;
    window.removeEventListener('data-record-outbox-updated', outboxUpdated);
    ++generation;
    ++read;
  });
</script>

<style scoped>
  .record-region {
    overflow: auto;
    max-inline-size: 100%;
  }
  .record-copy {
    padding: 8px;
    width: 100%;
    min-height: 32px;
    border: 0;
    text-align: left;
    color: var(--color-text-1);
    background: transparent;
    cursor: pointer;
  }
  pre {
    margin: 0;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }
</style>
