<template>
  <!-- 嵌入模式：同页 Tab 详情，不使用抽屉 -->
  <div v-if="props.embed" v-show="innerVisible" class="ms-detail-embed flex h-full flex-col bg-[var(--color-text-fff)]">
    <div class="flex h-[56px] shrink-0 items-center overflow-hidden border-b border-[var(--color-text-n8)] px-4">
      <div class="flex flex-1 items-center overflow-hidden">
        <a-tooltip :content="props.tooltipText ? props.tooltipText : props.title" position="bottom">
          <slot name="titleName">
            <div class="one-line-text">
              {{ props.title }}
            </div>
          </slot>
        </a-tooltip>
        <div class="mx-4 flex items-center">
          <slot name="titleLeft" :loading="loading" :detail="detail"></slot>
        </div>
      </div>
      <div class="ml-auto flex items-center">
        <MsPrevNextButton
          v-if="props.tableData && props.pagination && props.pageChange"
          ref="prevNextButtonRef"
          v-model:loading="loading"
          class="mr-[16px]"
          :page-change="props.pageChange"
          :pagination="props.pagination"
          :get-detail-func="props.getDetailFunc"
          navigation-only
          :detail-id="props.detailId"
          :detail-index="props.detailIndex"
          :table-data="props.tableData"
          @change="handleNavChange"
        />
        <slot name="titleRight" :loading="loading" :detail="detail"></slot>
      </div>
    </div>
    <div class="min-h-0 flex-1 overflow-auto">
      <a-alert v-if="loadError" type="error" class="m-4"
        >{{ loadError }}<a-button type="text" @click="initDetail()">重试</a-button></a-alert
      >
      <slot v-else :loading="loading" :detail="detail"></slot>
    </div>
    <div v-if="$slots.footer" class="shrink-0 border-t border-[var(--color-text-n8)] px-4 py-3">
      <slot name="footer" :loading="loading" :detail="detail"></slot>
    </div>
  </div>
  <MsDrawer
    v-else
    v-model:visible="innerVisible"
    :width="props.width"
    :footer="false"
    class="ms-drawer"
    :show-full-screen="props.showFullScreen"
    no-content-padding
    unmount-on-close
  >
    <template #title>
      <div class="flex flex-1 items-center overflow-hidden">
        <div class="flex flex-1 items-center overflow-hidden">
          <a-tooltip :content="props.tooltipText ? props.tooltipText : props.title" position="bottom">
            <slot name="titleName">
              <div class="one-line-text">
                {{ props.title }}
              </div>
            </slot>
          </a-tooltip>
          <div class="mx-4 flex items-center">
            <slot name="titleLeft" :loading="loading" :detail="detail"></slot>
          </div>
        </div>
        <div class="ml-auto flex items-center">
          <MsPrevNextButton
            v-if="props.tableData && props.pagination && props.pageChange"
            ref="prevNextButtonRef"
            v-model:loading="loading"
            class="mr-[16px]"
            :page-change="props.pageChange"
            :pagination="props.pagination"
            :get-detail-func="props.getDetailFunc"
            navigation-only
            :detail-id="props.detailId"
            :detail-index="props.detailIndex"
            :table-data="props.tableData"
            @change="handleNavChange"
          />
          <slot name="titleRight" :loading="loading" :detail="detail"></slot>
        </div>
      </div>
    </template>
    <a-alert v-if="loadError" type="error" class="m-4"
      >{{ loadError }}<a-button type="text" @click="initDetail()">重试</a-button></a-alert
    >
    <slot v-else :loading="loading" :detail="detail"></slot>
  </MsDrawer>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';

  import MsDrawer from '@/components/pure/ms-drawer/index.vue';
  import type { MsPaginationI } from '@/components/pure/ms-table/type';
  import MsPrevNextButton from '@/components/business/ms-prev-next-button/index.vue';

  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';
  import { createLatestDetailLoader } from '@/utils/latestDetail';

  const props = withDefaults(
    defineProps<{
      visible: boolean;
      title: string;
      width: number;
      detailId: string;
      tooltipText?: string;
      detailIndex?: number;
      tableData?: any[];
      pagination?: MsPaginationI;
      showFullScreen?: boolean;
      pageChange?: (page: number) => Promise<void>;
      getDetailFunc: (id: string) => Promise<any>;
      getDetailWithSignal?: (id: string, signal: AbortSignal) => Promise<any>;
      /** 同页嵌入（Tab 详情），不渲染抽屉 */
      embed?: boolean;
    }>(),
    {
      embed: false,
    }
  );

  const emit = defineEmits(['update:visible', 'loaded', 'loadingDetail', 'getDetail', 'change']);

  const prevNextButtonRef = ref<InstanceType<typeof MsPrevNextButton>>();

  const innerVisible = ref(!!props.visible);
  const loading = ref(false);
  const detail = ref<any>({});
  const loadError = ref('');
  const currentTargetId = ref(props.detailId);

  watch(
    () => props.visible,
    (val) => {
      if (innerVisible.value !== val) {
        innerVisible.value = val;
      }
    }
  );

  watch(
    () => innerVisible.value,
    (val) => {
      // 避免挂载时 false→emit 把父级 v-model 打回 false
      if (val !== props.visible) {
        emit('update:visible', val);
      }
    }
  );

  function handleDetailLoaded(val: any) {
    loadError.value = '';
    detail.value = val || {};
    emit('loaded', val);
  }

  function handleDetailFailure(error: unknown) {
    detail.value = {};
    loadError.value = formatAppErrorMessage(ensureAppError(error, '详情加载失败，请关闭后重试'));
  }

  const detailLoader = createLatestDetailLoader({
    fetch: (id, signal) => props.getDetailWithSignal?.(id, signal) ?? props.getDetailFunc(id),
    start: () => {
      loading.value = true;
      loadError.value = '';
      detail.value = {};
      emit('loadingDetail');
    },
    success: handleDetailLoaded,
    failure: handleDetailFailure,
    finish: () => {
      loading.value = false;
    },
  });

  function initDetail(id?: string) {
    if (!innerVisible.value) return;
    if (id) currentTargetId.value = id;
    return detailLoader.load(currentTargetId.value);
  }
  function handleNavChange(payload: { id: string; index: number }) {
    emit('change', payload);
    initDetail(payload.id);
  }

  function openPrevDetail() {
    prevNextButtonRef.value?.openPrevDetail();
  }

  function openNextDetail() {
    prevNextButtonRef.value?.openNextDetail();
  }

  watch(
    [() => innerVisible.value, () => props.detailId],
    ([visible, id]) => {
      if (visible) initDetail(id);
      else detailLoader.invalidate();
    },
    { immediate: true }
  );
  onBeforeUnmount(detailLoader.invalidate);
  defineExpose({
    initDetail,
    openPrevDetail,
    openNextDetail,
  });
</script>

<style lang="less" scoped></style>
