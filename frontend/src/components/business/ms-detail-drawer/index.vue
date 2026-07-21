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
          :detail-id="props.detailId"
          :detail-index="props.detailIndex"
          :table-data="props.tableData"
          @loading-detail="setDetailLoading"
          @loaded="handleDetailLoaded"
        />
        <slot name="titleRight" :loading="loading" :detail="detail"></slot>
      </div>
    </div>
    <div class="min-h-0 flex-1 overflow-hidden">
      <slot :loading="loading" :detail="detail"></slot>
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
            :detail-id="props.detailId"
            :detail-index="props.detailIndex"
            :table-data="props.tableData"
            @loading-detail="setDetailLoading"
            @loaded="handleDetailLoaded"
          />
          <slot name="titleRight" :loading="loading" :detail="detail"></slot>
        </div>
      </div>
    </template>
    <slot :loading="loading" :detail="detail"></slot>
  </MsDrawer>
</template>

<script setup lang="ts">
  import MsDrawer from '@/components/pure/ms-drawer/index.vue';
  import type { MsPaginationI } from '@/components/pure/ms-table/type';
  import MsPrevNextButton from '@/components/business/ms-prev-next-button/index.vue';

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
      /** 同页嵌入（Tab 详情），不渲染抽屉 */
      embed?: boolean;
    }>(),
    {
      embed: false,
    }
  );

  const emit = defineEmits(['update:visible', 'loaded', 'loadingDetail', 'getDetail']);

  const prevNextButtonRef = ref<InstanceType<typeof MsPrevNextButton>>();

  const innerVisible = ref(false);

  watch(
    () => props.visible,
    (val) => {
      innerVisible.value = val;
    },
    { immediate: true }
  );

  watch(
    () => innerVisible.value,
    (val) => {
      emit('update:visible', val);
    }
  );

  const loading = ref(false);
  const detail = ref<any>({});

  function initDetail(id?: string) {
    prevNextButtonRef.value?.initDetail(id);
  }

  function openPrevDetail() {
    prevNextButtonRef.value?.openPrevDetail();
  }

  function openNextDetail() {
    prevNextButtonRef.value?.openNextDetail();
  }

  function handleDetailLoaded(val: any) {
    detail.value = val;
    emit('loaded', val);
  }

  function setDetailLoading() {
    emit('loadingDetail');
  }

  watch([() => innerVisible.value, () => props.detailId], () => {
    if (innerVisible.value) {
      nextTick(() => {
        if (props.tableData && props.pagination && props.pageChange) {
          initDetail();
        } else {
          emit('getDetail');
        }
      });
    }
  });

  defineExpose({
    initDetail,
    openPrevDetail,
    openNextDetail,
  });
</script>

<style lang="less" scoped></style>
