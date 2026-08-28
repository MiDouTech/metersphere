<template>
  <MsCard simple>
    <div class="mb-4 flex items-center justify-between">
      <div
        ><div class="text-base font-medium">调度规则</div
        ><div class="mt-1 text-sm text-[var(--color-text-3)]"
          >支持 Cron、签名 Webhook 和人工立即触发；Webhook 密钥仅在创建或轮换时展示一次。</div
        ></div
      >
      <div class="flex gap-2"
        ><a-button :loading="loading" @click="$emit('refresh')">刷新</a-button
        ><a-button v-permission="['AI_TRIGGER:MANAGE']" type="primary" @click="$emit('create')">新建规则</a-button></div
      >
    </div>
    <a-alert v-if="error" type="error" class="mb-3">{{ error }}</a-alert>
    <a-table :data="items" :loading="loading" :pagination="false" row-key="id">
      <template #columns>
        <a-table-column title="名称" data-index="name" /><a-table-column
          title="类型"
          data-index="triggerType"
          :width="100"
        />
        <a-table-column title="配置"
          ><template #cell="{ record }"
            ><span v-if="record.triggerType === 'CRON'">{{ record.cronExpression }} · {{ record.timezone }}</span
            ><span v-else-if="record.triggerType === 'EVENT'">{{ record.eventType }}</span
            ><span v-else>仅手动执行</span></template
          ></a-table-column
        >
        <a-table-column title="启用" :width="80"
          ><template #cell="{ record }"
            ><a-switch
              v-permission="['AI_TRIGGER:MANAGE']"
              :model-value="record.enabled"
              @change="$emit('toggle', record)" /></template
        ></a-table-column>
        <a-table-column title="下次执行" :width="180"
          ><template #cell="{ record }">{{ formatTime(record.nextFireAt) }}</template></a-table-column
        >
        <a-table-column title="最近结果" :width="130"
          ><template #cell="{ record }"
            ><a-tag v-if="record.lastFireStatus" :color="record.lastFireStatus === 'CREATED' ? 'green' : 'red'">{{
              record.lastFireStatus
            }}</a-tag
            ><span v-else>-</span></template
          ></a-table-column
        >
        <a-table-column title="操作" :width="300"
          ><template #cell="{ record }"
            ><a-space
              ><a-link v-permission="['AI_TRIGGER:MANAGE']" @click="$emit('edit', record)">编辑</a-link
              ><a-link v-permission="['AI_TRIGGER:MANAGE']" @click="$emit('fire', record)">立即执行</a-link
              ><a-link @click="$emit('history', record)">历史</a-link
              ><a-link
                v-if="record.triggerType === 'EVENT'"
                v-permission="['AI_TRIGGER:MANAGE']"
                @click="$emit('rotate', record)"
                >轮换密钥</a-link
              ></a-space
            ></template
          ></a-table-column
        >
      </template>
      <template #empty><a-empty description="暂无调度规则" /></template>
    </a-table>
  </MsCard>
</template>

<script setup lang="ts">
  import type { AiTaskTrigger } from '@/api/modules/ai-execution';

  defineProps<{ items: AiTaskTrigger[]; loading: boolean; error?: string; formatTime: (value?: number) => string }>();
  defineEmits<{
    refresh: [];
    create: [];
    edit: [AiTaskTrigger];
    fire: [AiTaskTrigger];
    history: [AiTaskTrigger];
    rotate: [AiTaskTrigger];
    toggle: [AiTaskTrigger];
  }>();
</script>
