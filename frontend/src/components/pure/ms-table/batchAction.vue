<template>
  <div v-if="props.actionConfig" class="flex min-w-0 flex-row flex-wrap items-center gap-y-2">
    <slot name="count">
      <div class="title one-line-text">{{ t('msTable.batch.selected', { count: props.selectRowCount }) }}</div>
    </slot>
    <template v-for="(element, idx) in baseAction" :key="element.label">
      <a-divider v-if="element.isDivider" class="divider mx-0 my-[6px]" />
      <a-button
        v-if="!element.isDivider && !element.children && hasAllPermission(element.permission as string[]) && hasAnyPermission(element.anyPermission as string[])"
        class="ml-[8px]"
        :class="{
          'arco-btn-outline--danger': element.danger,
          'ml-[8px]': idx === 0,
        }"
        type="outline"
        :size="props.size"
        :disabled="actionsDisabled"
        @click="handleSelect(element)"
        >{{ t(element.label as string) }}</a-button
      >
      <!-- baseAction多菜单选择 -->

      <a-dropdown
        v-if="!element.isDivider && element.children && hasAllPermission(element.permission as string[]) && hasAnyPermission(element.anyPermission as string[])"
        position="tr"
        :size="props.size"
        @select="handleSelect"
      >
        <a-button
          class="ml-[8px]"
          :class="{
            'arco-btn-outline--danger': element.danger,
            'ml-[8px]': idx === 0,
          }"
          type="outline"
          :size="props.size"
          :disabled="actionsDisabled"
          >{{ t(element.label as string) }}</a-button
        >
        <template #content>
          <template v-for="item in element.children" :key="item.label">
            <a-divider v-if="item.isDivider" margin="4px" />
            <a-doption
              v-else-if="canUse(item)"
              :disabled="actionsDisabled"
              :value="item"
              :class="{ delete: item.danger }"
            >
              {{ t(item.label as string) }}
            </a-doption>
          </template>
        </template>
      </a-dropdown>
      <!-- baseAction多菜单选择 -->
      <a-button
        v-if="props.showSelectAllAfterExport && element.eventTag === 'export'"
        class="ml-[8px]"
        type="outline"
        :size="props.size"
        :disabled="props.selectAllDisabled"
        :aria-pressed="props.allPagesSelected"
        @click="emit('selectAllPages')"
        >{{ t(props.allPagesSelected ? 'msTable.cancelAll' : 'msTable.all') }}</a-button
      >
    </template>
    <div v-if="moreActionLength > 0" class="drop-down ml-[8px] flex items-center">
      <a-dropdown position="tr" @select="handleSelect">
        <a-button type="outline" :size="props.size" :disabled="actionsDisabled"
          ><MsIcon type="icon-icon_more_outlined"
        /></a-button>
        <template #content>
          <template v-for="element in moreAction" :key="element.label">
            <a-divider v-if="element.isDivider" margin="4px" />
            <a-doption
              v-else-if="canUse(element)"
              :disabled="actionsDisabled"
              :value="element"
              :class="{ delete: element.danger }"
            >
              {{ t(element.label as string) }}
            </a-doption>
          </template>
        </template>
      </a-dropdown>
    </div>
    <a-button
      class="clear-btn ml-[8px]"
      type="text"
      :size="props.size"
      :disabled="actionsDisabled"
      @click="emit('clear')"
    >
      {{ t('msTable.batch.clear') }}
    </a-button>
  </div>
</template>

<script lang="ts" setup>
  import MsIcon from '../ms-icon-font/index.vue';

  import { useI18n } from '@/hooks/useI18n';
  import { hasAllPermission, hasAnyPermission } from '@/utils/permission';

  import { BatchActionConfig, BatchActionParams } from './type';

  const { t } = useI18n();

  const props = defineProps<{
    selectRowCount?: number;
    disabled?: boolean;
    showSelectAllAfterExport?: boolean;
    selectAllDisabled?: boolean;
    allPagesSelected?: boolean;
    actionConfig?: BatchActionConfig;
    wrapperId: string;
    size?: 'mini' | 'small' | 'medium' | 'large';
  }>();
  const emit = defineEmits<{
    (e: 'batchAction', value: BatchActionParams): void;
    (e: 'clear'): void;
    (e: 'selectAllPages'): void;
  }>();

  const actionsDisabled = computed(() => !!props.disabled || !props.selectRowCount);
  const canUse = (item: BatchActionParams) =>
    hasAllPermission(item.permission as string[]) && hasAnyPermission(item.anyPermission as string[]);

  const handleSelect = (item: string | number | Record<string, any> | undefined | BatchActionParams) => {
    if (actionsDisabled.value || !item || typeof item !== 'object' || !canUse(item as BatchActionParams)) return;
    emit('batchAction', item as BatchActionParams);
  };

  function visibleActions(items: BatchActionParams[] = []): BatchActionParams[] {
    const permitted = items
      .filter((item) => item.isDivider || canUse(item))
      .map((item) => (item.children ? { ...item, children: visibleActions(item.children) } : item))
      .filter((item) => !item.children || item.children.length > 0);
    return permitted.filter(
      (item, index) => !item.isDivider || (index > 0 && index < permitted.length - 1 && !permitted[index - 1].isDivider)
    );
  }
  const baseAction = computed(() => visibleActions(props.actionConfig?.baseAction));
  const moreAction = computed(() => visibleActions(props.actionConfig?.moreAction));
  const moreActionLength = computed(() => moreAction.value.filter((item) => !item.isDivider).length);
</script>

<style lang="less" scoped>
  .title {
    display: flex;
    align-items: center;
    max-width: 400px;
    color: var(--color-text-2);
  }
  .delete {
    border-color: rgb(var(--danger-6)) !important;
    color: rgb(var(--danger-6));
  }
</style>
