<template>
  <div class="p-[16px]">
    <a-card :bordered="false">
      <template #title>{{ t('menu.bugManagement.automationExecution') }}</template>
      <template #extra>
        <a-button v-if="executionTaskId" type="primary" @click="loadTask">{{ t('common.refresh') }}</a-button>
      </template>

      <a-empty v-if="!executionTaskId" :description="t('bugManagement.automationExecution.empty')" />
      <a-spin v-else :loading="loading" class="w-full">
        <a-descriptions v-if="task" :column="3" bordered>
          <a-descriptions-item :label="t('bugManagement.automationExecution.taskId')">
            {{ task.id }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('bugManagement.automationExecution.status')">
            <a-tag>{{ task.status }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item :label="t('bugManagement.automationExecution.total')">
            {{ task.totalCount || 0 }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('bugManagement.automationExecution.success')">
            {{ task.successCount || 0 }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('bugManagement.automationExecution.failed')">
            {{ task.failedCount || 0 }}
          </a-descriptions-item>
          <a-descriptions-item :label="t('bugManagement.automationExecution.unexecuted')">
            {{ task.unexecutedCount || 0 }}
          </a-descriptions-item>
        </a-descriptions>

        <a-alert
          v-if="task?.confirmRequired"
          class="mt-[16px]"
          type="warning"
          :content="task.confirmationReason || t('bugManagement.automationExecution.confirmRequired')"
        />

        <a-table v-if="task" class="mt-[16px]" :data="task.cases || []" :pagination="false" row-key="id">
          <template #columns>
            <a-table-column title="ID" data-index="caseNum" :width="120" />
            <a-table-column :title="t('bugManagement.automationExecution.caseName')" data-index="caseName" />
            <a-table-column :title="t('bugManagement.automationExecution.status')" data-index="status" :width="160" />
            <a-table-column :title="t('bugManagement.automationExecution.result')" data-index="result" :width="160" />
            <a-table-column :title="t('bugManagement.automationExecution.error')" data-index="errorMessage" />
          </template>
        </a-table>
      </a-spin>
    </a-card>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import { Message } from '@arco-design/web-vue';

  import { type AiExecutionTask, getAiExecutionTask } from '@/api/modules/ai-execution';
  import { useI18n } from '@/hooks/useI18n';

  const route = useRoute();
  const { t } = useI18n();

  const loading = ref(false);
  const task = ref<AiExecutionTask>();
  const executionTaskId = computed(() => route.query.executionTaskId as string | undefined);

  async function loadTask() {
    if (!executionTaskId.value) {
      return;
    }
    try {
      loading.value = true;
      task.value = await getAiExecutionTask(executionTaskId.value);
    } catch (error) {
      Message.error(t('bugManagement.automationExecution.loadFailed'));
    } finally {
      loading.value = false;
    }
  }

  watch(executionTaskId, () => loadTask());

  onMounted(() => {
    loadTask();
  });
</script>
