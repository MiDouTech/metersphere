<template>
  <a-input-password
    v-model="inputValue"
    :placeholder="props.placeholder"
    :default-visibility="true"
    :disabled="props.disabled"
    allow-clear
    @clear="clearHandler"
    @input="inputHandler"
  />
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue';

  const props = defineProps<{
    placeholder?: string;
    modelValue?: string;
    value?: string;
    disabled?: boolean;
  }>();
  const inputValue = ref(props.modelValue || props.value || '');
  const emits = defineEmits<{
    (event: 'update:modelValue', value: string): void;
  }>();

  watch(
    () => inputValue.value,
    (val: string) => {
      emits('update:modelValue', val);
    }
  );

  watch(
    () => props.modelValue,
    (val) => {
      inputValue.value = val || '';
    }
  );

  function clearHandler() {
    inputValue.value = '';
    emits('update:modelValue', inputValue.value);
  }

  function inputHandler(value: string) {
    inputValue.value = value;
    emits('update:modelValue', inputValue.value);
  }
</script>

<style scoped></style>
