<template>
  <div class="p-6">
    <a-card title="确认用例删除">
      <a-alert v-if="error" type="error" class="mb-4">{{ error }}</a-alert>
      <a-spin :loading="loading" class="w-full">
        <template v-if="preview">
          <a-alert class="mb-4">
            {{
              preview.webRequest
                ? '请核对下方用例后选择处理方式。'
                : '此请求由你的 Agent 发起，确认后由 Agent 继续提交删除。'
            }}
            删除进入回收站，不永久移除用例。
          </a-alert>
          <p class="mb-3">{{ preview.message }}</p>
          <p class="mb-3">确认有效期：{{ new Date(preview.expiresAt).toLocaleString() }} · 状态：{{ stateLabel }}</p>
          <a-table :data="preview.targets" row-key="id" v-bind="paging">
            <template #columns
              ><a-table-column title="用例名称" data-index="name" /><a-table-column title="最后修改时间"
                ><template #cell="{ record }">{{
                  new Date(record.updateTime).toLocaleString()
                }}</template></a-table-column
              ></template
            >
          </a-table>
          <a-alert v-if="preview.activeTaskIds.length" type="warning" class="my-4">
            当前影响 {{ preview.activeTaskIds.length }} 个未结束任务。停止会阻止继续发布结果，不代表外部浏览器已经退出。
          </a-alert>
          <a-space class="mt-4" wrap>
            <a-button :disabled="!canDecide || saving" @click="decide('CANCEL')">取消删除</a-button>
            <a-button v-if="preview.activeTaskIds.length" :disabled="!canDecide || saving" @click="decide('WAIT')"
              >等待任务结束后删除</a-button
            >
            <a-button
              v-if="preview.activeTaskIds.length"
              status="danger"
              :loading="saving"
              :disabled="!canDecide"
              @click="decide('STOP')"
              >停止任务后删除</a-button
            >
            <a-button
              v-else
              type="primary"
              status="danger"
              :loading="saving"
              :disabled="!canDecide"
              @click="decide('DELETE')"
              >确认删除这些用例</a-button
            >
            <a-button
              v-if="preview.webRequest && ['APPROVED', 'WAITING'].includes(preview.state)"
              type="primary"
              :loading="saving"
              @click="execute"
              >继续删除</a-button
            >
            <a-button :disabled="saving" @click="load">刷新状态</a-button>
          </a-space>
        </template>
      </a-spin>
    </a-card>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import { Message } from '@arco-design/web-vue';

  import MSR from '@/api/http';
  import useClientTablePaging from '@/hooks/useClientTablePaging';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';

  interface Preview {
    previewId: string;
    projectId: string;
    targets: { id: string; name: string; updateTime: number }[];
    activeTaskIds: string[];
    state: string;
    expiresAt: number;
    message: string;
    webRequest: boolean;
  }
  const route = useRoute();
  const preview = ref<Preview>();
  const paging = useClientTablePaging(
    'case-delete-preview',
    () => preview.value?.targets.length || 0,
    () => route.fullPath
  );
  const loading = ref(false);
  const saving = ref(false);
  const error = ref('');
  let sequence = 0;
  const canDecide = computed(() => preview.value?.state === 'PENDING' && preview.value.expiresAt > Date.now());
  const labels: Record<string, string> = {
    PENDING: '待你确认',
    APPROVED: '已确认，等待继续处理',
    WAITING: '等待任务结束',
    CANCELLED: '已取消',
    COMPLETED: '删除已提交',
    EXPIRED: '确认已过期',
  };
  const stateLabel = computed(() => labels[preview.value?.state || ''] || '请刷新查看');
  async function load() {
    const request = ++sequence;
    loading.value = true;
    error.value = '';
    preview.value = undefined;
    try {
      const result = await MSR.get<Preview>({
        url: `/ai/case-deletions/${encodeURIComponent(String(route.params.id))}`,
      });
      if (request === sequence) preview.value = result;
    } catch (failure) {
      if (request === sequence)
        error.value = formatAppErrorMessage(ensureAppError(failure, '无法读取删除确认，请重新获取请求'));
    } finally {
      if (request === sequence) loading.value = false;
    }
  }
  async function execute() {
    if (!preview.value?.webRequest || saving.value) return;
    const id = preview.value.previewId;
    const captured = sequence;
    saving.value = true;
    error.value = '';
    try {
      const result = await MSR.post<{ state: string }>({ url: `/ai/case-deletions/${encodeURIComponent(id)}/execute` });
      if (captured !== sequence) return;
      Message.info(result.state === 'COMPLETED' ? '用例已移入回收站' : '关联任务尚未结束，请稍后继续');
      await load();
    } catch (failure) {
      if (captured === sequence) error.value = formatAppErrorMessage(ensureAppError(failure, '删除失败，请刷新后重试'));
    } finally {
      saving.value = false;
    }
  }
  async function decide(decision: string) {
    if (!canDecide.value || saving.value || !preview.value) return;
    const id = preview.value.previewId;
    const captured = sequence;
    saving.value = true;
    try {
      const result = await MSR.post<Preview>({
        url: `/ai/case-deletions/${encodeURIComponent(id)}/decision`,
        data: { decision },
      });
      if (captured !== sequence) return;
      preview.value = result;
      Message.success(result.webRequest ? '选择已保存' : '选择已保存，请让 Agent 继续查询处理结果');
      if (result.webRequest && result.state === 'APPROVED') {
        saving.value = false;
        await execute();
      }
    } catch (failure) {
      if (captured === sequence)
        error.value = formatAppErrorMessage(ensureAppError(failure, '保存选择失败，请刷新后重试'));
    } finally {
      saving.value = false;
    }
  }
  watch(() => route.params.id, load, { immediate: true });
  onBeforeUnmount(() => {
    sequence += 1;
  });
</script>
