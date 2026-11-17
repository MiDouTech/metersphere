<template>
  <div v-for="(raw, name) in declarations" :key="name" class="my-2">
    <a-form-item :label="`${name} (${String(spec(raw).type)}${spec(raw).unit ? `, ${spec(raw).unit}` : ''})`">
      <a-select
        v-if="spec(raw).type === 'boolean'"
        :model-value="modelValue[name] as boolean"
        allow-clear
        @change="set(String(name), $event)"
        ><a-option :value="true">true</a-option><a-option :value="false">false</a-option></a-select
      >
      <a-input-number
        v-else-if="['number', 'integer'].includes(String(spec(raw).type))"
        :model-value="modelValue[name] as number"
        @change="set(String(name), $event)"
      />
      <a-textarea
        v-else-if="['object', 'array'].includes(String(spec(raw).type))"
        :model-value="JSON.stringify(modelValue[name])"
        placeholder="输入该字段的 JSON 值"
        @change="json(String(name), $event)"
      />
      <a-input v-else :model-value="modelValue[name] as string" @change="set(String(name), $event)" />
      <a-checkbox
        v-if="spec(raw).nullable"
        :model-value="modelValue[name] === null"
        @change="set(String(name), $event ? null : undefined)"
        >明确为空</a-checkbox
      >
    </a-form-item>
  </div>
</template>

<script setup lang="ts">
  import { Message } from '@arco-design/web-vue';

  import type { ProtocolRecord } from '@/api/modules/governed-execution';

  const props = defineProps<{ declarations: ProtocolRecord; modelValue: ProtocolRecord }>();
  const emit = defineEmits<{ 'update:modelValue': [value: ProtocolRecord] }>();
  const spec = (value: unknown) => value as ProtocolRecord;
  function set(name: string, value: unknown) {
    const next = { ...props.modelValue };
    if (value === undefined) delete next[name];
    else next[name] = value;
    emit('update:modelValue', next);
  }
  function json(name: string, value: string) {
    try {
      set(name, JSON.parse(value));
    } catch {
      Message.warning('该字段必须是合法 JSON 值');
    }
  }
</script>
