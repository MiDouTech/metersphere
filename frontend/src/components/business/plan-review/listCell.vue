<template>
  <span v-if="group">—</span>
  <span v-else-if="!value">关联信息待核实</span>
  <span v-else-if="value.restricted">无权查看关联对象</span>
  <span v-else-if="value.relationStatus === 'NONE'">未关联</span>
  <span v-else-if="!value.target">关联对象已失效</span>
  <div v-else class="min-w-0">
    <router-link
      :to="{
        name: side === 'plan' ? TestPlanRouteEnum.TEST_PLAN_REVIEW_DETAIL : TestPlanRouteEnum.TEST_PLAN_INDEX_DETAIL,
        query: { id: value.target.id },
      }"
      :title="value.target.name"
      class="block truncate text-[rgb(var(--primary-6))]"
      @click.stop
    >
      #{{ value.target.num }} {{ value.target.name }}
    </router-link>
    <div class="text-xs text-[var(--color-text-3)]"
      >{{ states[value.target.status] || '状态待核实' }} · {{ sync[value.syncState || ''] || '同步状态待核实' }}</div
    >
  </div>
</template>

<script setup lang="ts">
  import type { RelationListSummary, RelationSide } from '@/api/modules/plan-review';

  import { TestPlanRouteEnum } from '@/enums/routeEnum';

  defineProps<{ value?: RelationListSummary; side: RelationSide; group?: boolean }>();
  const states: Record<string, string> = {
    PREPARED: '待开始',
    UNDERWAY: '进行中',
    COMPLETED: '已完成',
    ARCHIVED: '已归档',
  };
  const sync: Record<string, string> = {
    PENDING: '待同步',
    APPLYING: '同步中',
    SYNCED: '已同步',
    DELAYED: '同步延迟',
    WAITING_RETRY: '等待重试',
    FAILED_MANUAL: '失败待人工',
    RECONFIRM_REQUIRED: '待重新确认',
    ENDED: '已结束',
  };
</script>
