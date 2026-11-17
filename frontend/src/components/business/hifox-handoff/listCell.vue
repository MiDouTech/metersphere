<template>
  <span v-if="group">—</span>
  <span v-else-if="!value">交接信息待核实</span>
  <span v-else-if="value.restricted">交接详情受限</span>
  <div v-else>
    <span>{{ states[value.state] || '状态待核实' }}</span>
    <p v-if="value.sourceChanged" class="text-xs text-[rgb(var(--warning-6))]">来源曾变化，请核对</p>
  </div>
</template>

<script setup lang="ts">
  import { states as httpStates } from '@/api/modules/hifox-handoff';
  import type { HandoffListSummary } from '@/api/modules/plan-review';

  defineProps<{ value?: HandoffListSummary; group?: boolean }>();
  const states = httpStates;
</script>
