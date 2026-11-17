<template>
  <section>
    <p
      >{{ states[value.state] || value.state }}；HTTP码 {{ value.http_status || '—' }}；尝试
      {{ value.attempts_used }}/4</p
    >
    <p>后续建单及通知请到接收平台查看。</p><p>{{ risk }}</p>
    <p v-if="value.had_unknown">曾有结果不明尝试，接收方可能已经处理。</p>
    <p v-if="value.source_changed">来源曾变化，请回MSP核对。</p>
    <p>错误分类：{{ value.reason || '—' }}；接收配置版本：{{ value.url_revision }}</p>
    <p>下次时间：{{ value.next_at ? new Date(value.next_at).toLocaleString() : '—' }}</p>
    <p class="break-all">event：{{ value.event_id }}；payload hash：{{ value.payload_hash || '尚未冻结' }}</p>
    <Inspection :value="value.inspection" />
    <h4>操作者与暂停/恢复审计</h4>
    <a-table :data="value.audits" :pagination="false" row-key="id">
      <template #columns
        ><a-table-column title="操作者" data-index="actor" /><a-table-column
          title="动作"
          data-index="action"
        /><a-table-column title="作用范围"
          ><template #cell="{ record }">{{
            record.scope === 'PROJECT' ? '项目配置/启停' : '本投递'
          }}</template></a-table-column
        ><a-table-column title="修订" data-index="revision" /><a-table-column title="时间"
          ><template #cell="{ record }">{{ new Date(record.time).toLocaleString() }}</template></a-table-column
        ></template
      >
      <template #empty>暂无操作审计记录</template>
    </a-table>
    <a-table :data="value.attempts" :pagination="false" row-key="sequence_no">
      <template #columns
        ><a-table-column title="次数" data-index="sequence_no" /><a-table-column
          title="开始"
          data-index="started_at" /><a-table-column title="结束" data-index="ended_at" /><a-table-column
          title="HTTP"
          data-index="http_status" /><a-table-column title="分类" data-index="error_code"
      /></template>
    </a-table>
    <details v-if="value.message"
      ><summary>固定消息快照</summary
      ><pre class="whitespace-pre-wrap break-all">{{ JSON.stringify(value.message, null, 2) }}</pre>
    </details>
  </section>
</template>

<script setup lang="ts">
  import Inspection from './inspection.vue';

  import type { Delivery } from '@/api/modules/hifox-handoff';
  import { risk, states } from '@/api/modules/hifox-handoff';

  defineProps<{ value: Delivery }>();
</script>
