<template>
  <div class="min-w-0 break-words">
    <template v-if="Array.isArray(value)"
      ><div v-for="(item, index) in value" :key="index" class="mb-3 border-l border-[var(--color-border-2)] pl-3"
        ><ContentReader :value="item" /></div
    ></template>
    <a-descriptions v-else-if="value && typeof value === 'object'" :column="1" bordered>
      <a-descriptions-item v-for="(item, key) in value as Record<string, unknown>" :key="key" :label="String(key)"
        ><ContentReader :value="item"
      /></a-descriptions-item>
    </a-descriptions>
    <span v-else class="whitespace-pre-wrap">{{ value == null ? '—' : String(value) }}</span>
  </div>
</template>

<script setup lang="ts">
  defineProps<{ value: unknown }>();
</script>
