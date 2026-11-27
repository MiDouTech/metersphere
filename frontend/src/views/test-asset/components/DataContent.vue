<template>
  <div>
    <template v-if="tables.length"
      ><section v-for="(table, index) in tables" :key="index"
        ><h3>{{ table.name }}</h3
        ><div class="content-table" tabindex="0" role="region" :aria-label="table.name"
          ><table
            ><caption>{{ table.name }}</caption
            ><thead
              ><tr
                ><th v-for="(_, column) in table.rows[0] || []" :key="column" scope="col">{{ column + 1 }}</th></tr
              ></thead
            ><tbody
              ><tr
                v-for="(row, rowIndex) in table.rows.slice((pages[index] || 0) * 100, ((pages[index] || 0) + 1) * 100)"
                :key="rowIndex"
                ><td v-for="(cell, column) in row" :key="column">{{ display(cell) }}</td></tr
              ></tbody
            ></table
          ></div
        ><a-pagination
          v-if="table.rows.length > 100"
          :current="(pages[index] || 0) + 1"
          :page-size="100"
          :total="table.rows.length"
          @change="(page) => (pages[index] = page - 1)" /></section
    ></template>
    <pre v-else>{{ textContent }}</pre>
  </div>
</template>

<script setup lang="ts">
  import { computed, reactive, watch } from 'vue';

  const props = defineProps<{ value: unknown; format?: string }>();
  const isTable = computed(() => ['CSV', 'XLS', 'XLSX'].includes(props.format || 'JSON'));
  const textContent = computed(() =>
    props.format === 'TXT' ? String(props.value ?? '') : JSON.stringify(props.value, null, 2)
  );
  const pages = reactive<Record<number, number>>({});
  watch(
    () => props.value,
    () => Object.keys(pages).forEach((key) => delete pages[Number(key)])
  );
  const tables = computed(() => {
    if (!isTable.value) return [];
    const value = props.value as { rows?: unknown[][]; sheets?: { name: string; rows: unknown[][] }[] } | null;
    if (value && Array.isArray(value.rows)) return [{ name: '数据内容', rows: value.rows }];
    if (value && Array.isArray(value.sheets)) return value.sheets;
    return [];
  });
  function display(value: unknown) {
    if (value && typeof value === 'object' && 'type' in value && value.type === 'DATE' && 'value' in value)
      return String(value.value);
    return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
  }
</script>

<style scoped>
  .content-table {
    overflow: auto;
    max-block-size: 70vh;
  }
  table {
    border-collapse: separate;
    border-spacing: 0;
    min-width: 100%;
  }
  th {
    position: sticky;
    inset-block-start: 0;
    background: var(--color-bg-2);
  }
  th,
  td {
    padding: 8px;
    border: 1px solid var(--color-border-2);
    white-space: pre-wrap;
  }
  pre {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }
</style>
