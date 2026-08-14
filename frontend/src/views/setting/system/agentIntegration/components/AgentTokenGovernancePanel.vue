<template>
  <MsCard simple>
    <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
      <div>
        <div class="text-base font-medium">Token 治理</div>
        <div class="mt-1 text-sm text-[var(--color-text-3)]"
          >管理员查看全局 Agent Token、撤销风险凭据并导出审计记录。</div
        >
      </div>
      <div class="flex flex-wrap gap-2">
        <a-input-search
          v-model="query.keyword"
          class="w-[240px]"
          allow-clear
          placeholder="用户、名称或 Token 前缀"
          @search="search"
          @clear="search"
        />
        <a-select v-model="query.status" class="w-[140px]" allow-clear placeholder="状态" @change="search">
          <a-option value="ACTIVE">ACTIVE</a-option>
          <a-option value="DISABLED">DISABLED</a-option>
          <a-option value="REVOKED">REVOKED</a-option>
          <a-option value="EXPIRED">EXPIRED</a-option>
        </a-select>
        <a-button :loading="loading" @click="load">刷新</a-button>
        <a-button v-permission="['SYSTEM_USER:READ']" :loading="exporting" @click="exportAudit">导出审计</a-button>
      </div>
    </div>
    <a-table
      :data="records"
      :loading="loading"
      row-key="id"
      :pagination="pagination"
      @page-change="changePage"
      @page-size-change="changePageSize"
    >
      <a-table-column title="名称" data-index="name" :width="180" />
      <a-table-column title="用户 ID" data-index="userId" :width="180" />
      <a-table-column title="客户端" data-index="clientType" :width="110" />
      <a-table-column title="项目范围" data-index="projectScopeLabel" :width="180" ellipsis tooltip />
      <a-table-column title="Scope" data-index="scopes" :width="220" ellipsis tooltip />
      <a-table-column title="状态" :width="110"
        ><template #cell="{ record }"
          ><a-tag>{{ record.status || (record.enable ? 'ACTIVE' : 'DISABLED') }}</a-tag></template
        ></a-table-column
      >
      <a-table-column title="最近使用" :width="170"
        ><template #cell="{ record }">{{ formatTime(record.lastUsedAt) }}</template></a-table-column
      >
      <a-table-column title="调用次数" data-index="invocationCount" :width="100" />
      <a-table-column title="操作" :width="90" fixed="right">
        <template #cell="{ record }">
          <a-link
            v-permission="['SYSTEM_USER:UPDATE']"
            status="danger"
            :disabled="record.status === 'REVOKED'"
            @click="revoke(record)"
            >撤销</a-link
          >
        </template>
      </a-table-column>
    </a-table>
  </MsCard>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { Message, Modal } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import MsCard from '@/components/pure/ms-card/index.vue';

  import type { AgentTokenListItem } from '@/api/modules/setting/agentIntegration';
  import {
    exportAdminAgentTokenAudit,
    getAdminAgentTokenPage,
    revokeAdminAgentToken,
  } from '@/api/modules/setting/agentIntegration';
  import { downloadByteFile } from '@/utils';

  const loading = ref(false);
  const exporting = ref(false);
  const records = ref<AgentTokenListItem[]>([]);
  const total = ref(0);
  const query = reactive({ keyword: '', status: undefined as string | undefined, current: 1, pageSize: 20 });
  const pagination = computed(() => ({
    current: query.current,
    pageSize: query.pageSize,
    total: total.value,
    showTotal: true,
    showPageSize: true,
  }));
  const formatTime = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-');

  async function load() {
    loading.value = true;
    try {
      const result = await getAdminAgentTokenPage({ ...query, keyword: query.keyword.trim() || undefined });
      records.value = result.list || [];
      total.value = result.total || 0;
    } finally {
      loading.value = false;
    }
  }
  function search() {
    query.current = 1;
    load();
  }
  function changePage(current: number) {
    query.current = current;
    load();
  }
  function changePageSize(pageSize: number) {
    query.current = 1;
    query.pageSize = pageSize;
    load();
  }
  function revoke(record: AgentTokenListItem) {
    Modal.warning({
      title: '撤销 Agent Token',
      content: `撤销“${record.name}”后，使用该 Token 的 Agent 调用将立即失效。`,
      hideCancel: false,
      onOk: async () => {
        await revokeAdminAgentToken(record.id);
        Message.success('Token 已撤销');
        await load();
      },
    });
  }
  async function exportAudit() {
    exporting.value = true;
    try {
      const bytes = await exportAdminAgentTokenAudit({ keyword: query.keyword.trim() || undefined });
      downloadByteFile(bytes, `agent-audit-${dayjs().format('YYYYMMDD-HHmmss')}.csv`);
    } finally {
      exporting.value = false;
    }
  }
  onMounted(load);
</script>
