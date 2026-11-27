<template
  ><section aria-label="所选内容版本元信息"
    ><h3>所选内容版本元信息</h3
    ><p v-if="!snapshot?.metadataAvailable">此版本未保存元信息，名称、说明、分类及格式未知。</p
    ><dl v-else
      ><dt>当时名称</dt><dd>{{ snapshot.metadata?.name ?? '未知' }}</dd
      ><dt>当时说明</dt><dd>{{ snapshot.metadata?.description ?? '未知' }}</dd
      ><dt>当时分类</dt><dd>{{ category }}</dd
      ><dt>当时格式</dt><dd>{{ snapshot.metadata?.format ?? '未知' }}</dd></dl
    ></section
  ></template
>

<script setup lang="ts">
  import { computed } from 'vue';

  import type { DataVersionMetadata } from '@/api/modules/test-data-assets';

  const props = defineProps<{ snapshot?: DataVersionMetadata }>();
  const category = computed(() => {
    const { snapshot } = props;
    if (snapshot?.metadata?.categoryId) return snapshot.metadata.categoryId;
    if (snapshot?.metadataSource === 'LEGACY_SOURCE_VERSION') return '历史分类未知';
    if (snapshot?.metadata && 'categoryId' in snapshot.metadata) return '未分类';
    return '未知';
  });
</script>

<style scoped>
  section {
    margin-block: 24px;
    padding: 16px;
    border: 1px solid var(--color-border-2);
    border-radius: 6px;
  }
  h3 {
    margin: 0 0 12px;
    font-weight: 600;
  }
  dl {
    display: grid;
    grid-template-columns: max-content minmax(0, 1fr);
    gap: 8px 16px;
    margin: 0;
    overflow-wrap: anywhere;
  }
  dt {
    color: var(--color-text-3);
  }
  dd {
    margin: 0;
    white-space: pre-wrap;
  }
</style>
