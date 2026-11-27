<template>
  <section
    ><a-alert v-if="validation" type="error">{{ validation }}</a-alert>
    <a-select
      v-if="sheets.length"
      v-model="sheet"
      :options="sheets.map((item, index) => ({ label: item.name, value: index }))"
      aria-label="工作表"
    />
    <div class="table-editor" tabindex="0" role="region" aria-label="编辑表格内容">
      <table
        ><caption>表格内容</caption
        ><thead
          ><tr
            ><th v-for="column in columns" :key="column" scope="col">{{ column }}</th></tr
          ></thead
        ><tbody
          ><tr v-for="(row, index) in rows.slice((page - 1) * 100, page * 100)" :key="index"
            ><td v-for="column in columns" :key="column"
              ><a-input
                :model-value="cellToText(row[column - 1])"
                :aria-label="`第 ${(page - 1) * 100 + index + 1} 行第 ${column} 列`"
                @update:model-value="(value) => update((page - 1) * 100 + index, column - 1, value)" /></td></tr></tbody
      ></table>
    </div>
    <a-pagination
      v-if="rows.length > 100"
      v-model:current="page"
      :total="rows.length"
      :page-size="100"
      aria-label="编辑表格分页"
    /><a-space
      ><a-button @click="addRow">添加行</a-button><a-button @click="addColumn">添加列</a-button
      ><a-button :disabled="!rows.length" @click="removeRow">删除末行</a-button></a-space
    >
  </section>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';

  const props = defineProps<{ modelValue: string; format?: string; identity?: string }>();
  const emit = defineEmits<{ (event: 'update:modelValue', value: string): void }>();
  const validation = ref('');
  const sheet = ref(0);
  const page = ref(1);
  const document = computed(() => {
    try {
      const parsed = JSON.parse(props.modelValue);
      return (parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}) as {
        rows?: unknown[][];
        sheets?: { name: string; rows: unknown[][] }[];
      };
    } catch {
      return {};
    }
  });
  const sheets = computed(() => (Array.isArray(document.value.sheets) ? document.value.sheets : []));
  const rows = computed<unknown[][]>(() =>
    sheets.value.length ? sheets.value[sheet.value]?.rows || [] : document.value.rows || []
  );
  const columns = computed(() => Math.max(1, ...rows.value.map((row) => row.length)));
  watch(
    () => props.format,
    () => {
      sheet.value = 0;
    }
  );
  watch(
    () => props.identity,
    () => {
      sheet.value = 0;
      page.value = 1;
    }
  );
  watch(sheet, () => {
    page.value = 1;
  });
  watch([() => sheets.value.length, () => rows.value.length], () => {
    sheet.value = Math.min(sheet.value, Math.max(0, sheets.value.length - 1));
    page.value = Math.min(page.value, Math.max(1, Math.ceil(rows.value.length / 100)));
  });
  function commit(value: unknown[][]) {
    if (sheets.value.length && !sheets.value[sheet.value]) return;
    const next = structuredClone(document.value);
    if (sheets.value.length) next.sheets![sheet.value].rows = value;
    else next.rows = value;
    emit('update:modelValue', JSON.stringify(next, null, 2));
  }
  function cellToText(value: unknown): string {
    if (value && typeof value === 'object' && 'type' in value && value.type === 'DATE' && 'value' in value)
      return String(value.value);
    return String(value ?? '');
  }
  function update(row: number, column: number, value: string) {
    const next = structuredClone(rows.value);
    const previous = next[row][column];
    validation.value = '';
    if (cellToText(previous) === value) return;
    if (!value.trim()) next[row][column] = null;
    else if (previous && typeof previous === 'object' && 'type' in previous && previous.type === 'DATE')
      next[row][column] = { ...previous, value };
    else if (typeof previous === 'number') {
      const number = Number(value);
      if (!Number.isFinite(number)) {
        validation.value = '请输入有效数字，当前单元格保留原值。';
        return;
      }
      next[row][column] = number;
    } else if (typeof previous === 'boolean') {
      if (!['true', 'false'].includes(value.toLowerCase())) {
        validation.value = '布尔值应为 true 或 false，当前单元格保留原值。';
        return;
      }
      next[row][column] = value.toLowerCase() === 'true';
    } else next[row][column] = value;
    commit(next);
  }
  function addRow() {
    const next = [...structuredClone(rows.value), Array.from({ length: columns.value }, () => '')];
    commit(next);
    page.value = Math.max(1, Math.ceil(next.length / 100));
  }
  function addColumn() {
    const next = rows.value.length ? rows.value.map((row) => [...row, '']) : [['', '']];
    commit(next);
  }
  function removeRow() {
    commit(rows.value.slice(0, -1));
  }
</script>

<style scoped>
  .table-editor {
    overflow: auto;
    max-block-size: 45vh;
    margin-bottom: 12px;
  }
  table {
    border-collapse: separate;
    min-width: 100%;
  }
  th {
    position: sticky;
    inset-block-start: 0;
    background: var(--color-bg-2);
  }
  td {
    min-width: 120px;
  }
</style>
