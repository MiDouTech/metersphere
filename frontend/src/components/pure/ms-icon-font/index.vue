<template>
  <svg
    v-if="props.type === 'icon-icon_robot'"
    class="arco-icon"
    :width="props.size || 14"
    :height="props.size || 14"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M12 3v3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
    <circle cx="12" cy="2.5" r="1.25" fill="currentColor" />
    <rect x="4.5" y="6" width="15" height="13" rx="3" stroke="currentColor" stroke-width="1.8" />
    <circle cx="9" cy="12" r="1.25" fill="currentColor" />
    <circle cx="15" cy="12" r="1.25" fill="currentColor" />
    <path d="M8.5 16h7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
    <path d="M2.5 10v5M21.5 10v5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
  </svg>
  <icon-font v-else :type="props.type" :size="props.size || 14" />
</template>

<script lang="ts" setup>
  import { Icon } from '@arco-design/web-vue';

  import '@/assets/icon-font/iconfont';

  const props = defineProps<{
    type: string;
    size?: string | number;
    rotate?: number;
    spin?: boolean;
  }>();

  const IconFont = Icon.addFromIconFontCn({
    extraProps: { ...props },
  });
</script>
