<template
  ><a-drawer :visible="visible" :width="720" title="触发历史" @update:visible="$emit('update:visible', $event)"
    ><a-alert v-if="error" type="error" class="mb-3">{{ error }}</a-alert
    ><a-table :data="items" :loading="loading" :pagination="false" row-key="id"
      ><template #columns
        ><a-table-column title="触发时间" :width="180"
          ><template #cell="{ record }">{{ formatTime(record.fireTime) }}</template></a-table-column
        ><a-table-column title="状态" data-index="status" :width="110" /><a-table-column
          title="任务 ID"
          data-index="taskId"
          :width="220" /><a-table-column title="说明" data-index="message" /></template
      ><template #empty><a-empty description="暂无触发历史" /></template></a-table></a-drawer
></template>

<script setup lang="ts">
  import type { AiTaskTriggerHistory } from '@/api/modules/ai-execution';

  defineProps<{
    visible: boolean;
    items: AiTaskTriggerHistory[];
    loading: boolean;
    error?: string;
    formatTime: (value?: number) => string;
  }>();
  defineEmits<{ 'update:visible': [boolean] }>();
</script>
