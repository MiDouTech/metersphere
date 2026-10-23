<template>
  <a-button @click="open">通知状态</a-button>
  <a-drawer v-model:visible="visible" title="评论通知状态" :width="720" :footer="false" unmount-on-close>
    <a-alert class="mb-3">仅显示本人评论或有管理权限的评论通知。外部通道的“已提交”不等于接收方已收到。</a-alert>
    <a-alert v-if="error" type="error" class="mb-3">{{ error }}</a-alert>
    <a-button class="mb-3" :loading="loading" @click="load">刷新</a-button>
    <a-table :data="rows" row-key="id" :loading="loading" :pagination="false">
      <template #columns>
        <a-table-column title="通道" data-index="channel" />
        <a-table-column title="状态"
          ><template #cell="{ record }">{{ labels[record.state] || record.state }}</template></a-table-column
        >
        <a-table-column title="操作"
          ><template #cell="{ record }"
            ><a-button
              v-if="['FAILED', 'UNCERTAIN', 'SUBMITTED'].includes(record.state)"
              :disabled="loading"
              @click="retry(record)"
              >确认重试</a-button
            ></template
          ></a-table-column
        >
      </template>
    </a-table>
    <a-pagination
      class="mt-4"
      v-bind="paging.pagination"
      @change="paging.onPageChange"
      @page-size-change="paging.onPageSizeChange"
    />
  </a-drawer>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';
  import { Modal } from '@arco-design/web-vue';

  import MSR from '@/api/http';
  import useClientTablePaging from '@/hooks/useClientTablePaging';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';

  const props = defineProps<{ caseId: string }>();
  interface Delivery {
    id: string;
    channel: string;
    state: string;
    updateTime: number;
  }
  const visible = ref(false);
  const rows = ref<Delivery[]>([]);
  const total = ref(0);
  const paging = useClientTablePaging(
    'comment-deliveries',
    () => total.value,
    () => props.caseId
  );
  const loading = ref(false);
  const error = ref('');
  let sequence = 0;
  const labels: Record<string, string> = {
    PENDING: '等待发送',
    SENDING: '正在提交',
    SENT: '已写入站内通知',
    SUBMITTED: '已提交，未取得回执',
    UNCERTAIN: '送达情况不确定',
    FAILED: '发送失败',
    CANCELLED: '已取消',
  };
  async function load() {
    const request = ++sequence;
    loading.value = true;
    error.value = '';
    try {
      const result = await MSR.get<{ items: Delivery[]; total: number }>({
        url: `/functional/case/comment/notices/${encodeURIComponent(props.caseId)}`,
        params: { current: paging.value.pagination.current, pageSize: paging.value.pagination.pageSize },
      });
      if (request === sequence) {
        rows.value = result.items;
        total.value = result.total;
      }
    } catch (failure) {
      if (request === sequence) {
        rows.value = [];
        error.value = formatAppErrorMessage(ensureAppError(failure, '无法读取通知状态'));
      }
    } finally {
      if (request === sequence) loading.value = false;
    }
  }
  function open() {
    visible.value = true;
    paging.value.onPageChange(1);
    load();
  }
  watch(
    () => [paging.value.pagination.current, paging.value.pagination.pageSize],
    () => {
      if (visible.value) load();
    }
  );
  function retry(row: Delivery) {
    const { caseId } = props;
    Modal.warning({
      title: '确认重新发送通知',
      content: '接收方可能已经收到此前的外部通知。重试可能产生重复消息，请先核实。',
      hideCancel: false,
      onBeforeOk: async () => {
        try {
          await MSR.post({
            url: `/functional/case/comment/notices/${encodeURIComponent(caseId)}/${row.id}/retry`,
            data: { expectedUpdateTime: row.updateTime },
          });
          if (caseId === props.caseId && visible.value) await load();
          return true;
        } catch (failure) {
          error.value = formatAppErrorMessage(ensureAppError(failure, '重试失败，请刷新状态'));
          return false;
        }
      },
    });
  }
  watch(
    () => props.caseId,
    () => {
      ++sequence;
      visible.value = false;
      rows.value = [];
      total.value = 0;
    }
  );
  watch(visible, (value) => {
    if (!value) ++sequence;
  });
  onBeforeUnmount(() => {
    ++sequence;
  });
</script>
