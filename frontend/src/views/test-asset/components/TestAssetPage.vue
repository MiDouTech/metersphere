<template
  ><div class="test-asset-page"><slot /></div
></template>

<style scoped>
  .test-asset-page {
    padding: 24px;
    min-inline-size: 0;
  }
  .test-asset-page :deep(.asset-heading) {
    margin-block-end: 24px;
  }
  .test-asset-page :deep(.asset-heading h2) {
    margin: 0 0 8px;
    font-size: 20px;
    font-weight: 600;
    color: var(--color-text-1);
  }
  .test-asset-page :deep(.asset-heading p),
  .test-asset-page :deep(.asset-description) {
    font-size: 13px;
    color: var(--color-text-3);
    margin-block: 8px 16px;
  }
  .test-asset-page :deep(.asset-panel) {
    padding: 20px;
    border: 1px solid var(--color-border-2);
    border-radius: 8px;
    background: var(--color-bg-2);
    min-inline-size: 0;
  }
  .test-asset-page :deep(.asset-toolbar),
  .test-asset-page :deep(.asset-actions),
  .test-asset-page :deep(.asset-search) {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
  }
  .test-asset-page :deep(.asset-panel .arco-link) {
    white-space: nowrap;
  }
  .test-asset-page :deep(.asset-toolbar) {
    margin-block-end: 20px;
  }
  .test-asset-page :deep(.asset-search) {
    margin-inline-start: auto;
    justify-content: flex-end;
  }
  .test-asset-page :deep(.asset-search .arco-input-wrapper),
  .test-asset-page :deep(.asset-search .arco-select-view) {
    inline-size: 180px;
  }
  .test-asset-page :deep(.asset-file-input) {
    display: none;
  }
  .test-asset-page :deep(.asset-statistics) {
    display: flex;
    flex-wrap: wrap;
    gap: 24px;
    padding-block: 4px 20px;
    color: var(--color-text-3);
  }
  .test-asset-page :deep(.asset-statistics strong) {
    font-size: 22px;
    color: var(--color-text-1);
    margin-inline-end: 6px;
  }
  @media (max-width: 1000px) {
    .test-asset-page {
      padding: 16px;
    }
    .test-asset-page :deep(.asset-search) {
      inline-size: 100%;
    }
  }
</style>
