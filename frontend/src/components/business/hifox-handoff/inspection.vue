<template>
  <section class="my-3 break-all">
    <p>原通过时间：{{ value.timeEvidence.occurredAt || '待核实' }}</p>
    <p>时间证据引用：{{ value.timeEvidence.reference || '待核实' }}</p>
    <p>时间证据修订：{{ value.timeEvidence.revision || '待核实' }}</p>
    <p>来源版本：{{ value.sourceVersion.revision }}</p>
    <p>成员版本：{{ value.sourceVersion.memberVersion }}；规则版本：{{ value.sourceVersion.ruleRevision }}</p>
    <p>完成快照摘要：{{ value.sourceVersion.snapshotHash }}</p>
    <p>功能用例：{{ value.functionalCount }}条（不包含API/场景）</p>
    <p>已冻结event：{{ value.frozenEventId || '尚未首次冻结' }}</p>
    <p v-if="!value.pauseReasons.length">暂无暂停或停止原因</p>
    <ul v-else
      ><li v-for="reason in value.pauseReasons" :key="reason">{{ reasons[reason] || reason }}</li></ul
    >
  </section>
</template>

<script setup lang="ts">
  import type { Inspection } from '@/api/modules/hifox-handoff';

  defineProps<{ value: Inspection }>();
  const reasons: Record<string, string> = {
    DISABLED: '推送已关闭',
    PG_REVOKED: '出站授权已撤销或等待显式恢复',
    SOURCE_ACCESS_UNAVAILABLE: '来源权限待核实',
    SOURCE_CHANGED: '来源已变化',
    URL_CHANGED: '接收地址已变更',
  };
</script>
