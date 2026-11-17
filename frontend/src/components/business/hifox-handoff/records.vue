<template>
  <section>
    <h3>投递记录</h3>
    <div class="my-3 flex flex-wrap gap-2"
      ><a-input v-model="keyword" placeholder="计划ID或名称" :max-length="255" /><a-select
        v-model="state"
        allow-clear
        placeholder="HTTP状态"
        ><a-option v-for="(label, key) in filterStates" :key="key" :value="key">{{ label }}</a-option></a-select
      ><a-select v-model="sourceChanged" allow-clear placeholder="来源状态"
        ><a-option :value="false">来源未标记变化</a-option><a-option :value="true">来源曾变化</a-option></a-select
      >
      <a-range-picker
        v-model="createdRange"
        show-time
        value-format="timestamp"
        allow-clear
        :placeholder="['记录创建时间起', '记录创建时间止']"
      />
      <a-button :disabled="loading" @click="search">查询</a-button></div
    >
    <a-alert v-if="error" type="error"
      >读取失败或缺少配置管理及双侧读取权限 <a-button @click="load">重试</a-button></a-alert
    >
    <a-table v-else :data="rows" :loading="loading" :pagination="false" row-key="business_key">
      <template #columns
        ><a-table-column title="计划"
          ><template #cell="{ record }"
            >{{ record.source.planName }}（{{ record.source.planId }}）</template
          ></a-table-column
        ><a-table-column title="HTTP状态"
          ><template #cell="{ record }">{{ states[record.state] || record.state }}</template></a-table-column
        ><a-table-column title="来源状态"
          ><template #cell="{ record }">{{
            record.source_changed ? '来源曾变化' : '来源未标记变化'
          }}</template></a-table-column
        >
        <a-table-column title="记录更新时间"
          ><template #cell="{ record }">{{ new Date(record.update_time).toLocaleString() }}</template></a-table-column
        >
        <a-table-column title="次数/4" data-index="attempts_used" /><a-table-column
          title="HTTP码"
          data-index="http_status"
        /><a-table-column title="操作"
          ><template #cell="{ record }"><a-button @click="selected = record">详情</a-button></template></a-table-column
        ></template
      >
      <template #empty>{{
        keyword || state || sourceChanged !== undefined || createdRange?.length ? '无符合筛选的记录' : '暂无投递记录'
      }}</template>
    </a-table>
    <a-pagination v-if="!error" v-model:current="page" :page-size="20" :total="total" @change="load" />
    <a-modal :visible="!!selected" title="HTTP投递详情" :footer="false" @cancel="selected = undefined"
      ><Details v-if="selected" :value="selected"
    /></a-modal>
  </section>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';

  import Details from './details.vue';

  import type { Delivery } from '@/api/modules/hifox-handoff';
  import { getRecords, states } from '@/api/modules/hifox-handoff';
  import createLatestRequest from '@/utils/latestRequest';

  const filterStates = Object.fromEntries(Object.entries(states).filter(([key]) => key !== 'NONE'));
  const props = defineProps<{ projectId: string }>();
  const rows = ref<Delivery[]>([]);
  const selected = ref<Delivery>();
  const keyword = ref('');
  const state = ref<string>();
  const sourceChanged = ref<boolean>();
  const createdRange = ref<number[]>();
  const page = ref(1);
  const total = ref(0);
  const loading = ref(false);
  const error = ref(false);
  const request = createLatestRequest();
  async function load() {
    const seq = request.begin();
    loading.value = true;
    error.value = false;
    rows.value = [];
    selected.value = undefined;
    try {
      const result = await getRecords(
        props.projectId,
        page.value,
        keyword.value,
        state.value,
        sourceChanged.value,
        createdRange.value?.[0],
        createdRange.value?.[1]
      );
      if (!request.isCurrent(seq)) return;
      rows.value = result.items;
      total.value = result.total;
    } catch {
      if (request.isCurrent(seq)) error.value = true;
    } finally {
      if (request.isCurrent(seq)) loading.value = false;
    }
  }
  function search() {
    page.value = 1;
    load();
  }
  watch(
    () => props.projectId,
    () => {
      keyword.value = '';
      state.value = undefined;
      sourceChanged.value = undefined;
      createdRange.value = undefined;
      page.value = 1;
      total.value = 0;
      load();
    },
    { immediate: true }
  );
  onBeforeUnmount(() => {
    request.invalidate();
  });
</script>
