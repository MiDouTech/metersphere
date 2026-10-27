<template>
  <section
    v-if="facts && steps.length"
    class="execution-facts mb-4 rounded border border-solid border-[var(--color-border-2)] p-4"
  >
    <div class="mb-2 font-medium">当前执行事实</div>
    <a-alert class="mb-3">执行者提交的结果；平台完成结构校验，不代表独立复核业务结果。原始用例定义保持独立。</a-alert>
    <div v-if="facts?.evidenceScope === 'CASE'" class="mb-3 text-[var(--color-text-2)]">
      本次用例执行证据
      {{ Array.isArray(facts.evidenceIds) ? facts.evidenceIds.length : 0 }} 项（请在附件区查看）；图片不按步骤分配。
    </div>
    <div
      v-for="(step, index) in steps"
      :key="index"
      class="mb-3 border-0 border-b border-solid border-[var(--color-border-2)] pb-3"
    >
      <div>执行记录 {{ index + 1 }} · 原步骤 {{ step.stepNum }} · {{ step.result }}</div>
      <div class="mt-1 whitespace-pre-wrap text-[var(--color-text-2)]">{{ step.actual }}</div>
      <div v-if="facts?.evidenceScope !== 'CASE'" class="text-xs text-[var(--color-text-3)]"
        >关联证据 {{ Array.isArray(step.evidenceIds) ? step.evidenceIds.length : 0 }} 项</div
      >
    </div>
  </section>
</template>

<script setup lang="ts">
  import { computed } from 'vue';

  const props = defineProps<{ facts?: Record<string, unknown> | null }>();
  const steps = computed(() =>
    Array.isArray(props.facts?.steps)
      ? (props.facts.steps.filter((step) => step && typeof step === 'object') as Record<string, unknown>[])
      : []
  );
</script>
