<template>
  <a-alert
    >同一实例只允许方法等价替代；目标、断言、入口、权限、环境、数据范围、风险或不可逆副作用改变时，应新建实例。</a-alert
  >
  <a-form :model="form" layout="vertical">
    <a-form-item label="原已采用知识"
      ><a-select v-model="form.knowledgeId"
        ><a-option v-for="item in knowledge" :key="String(item.knowledgeId)" :value="String(item.knowledgeId)"
          >{{ item.knowledgeId }} v{{ item.version }}</a-option
        ></a-select
      ></a-form-item
    >
    <a-form-item label="替代知识（留空表示原知识的新版本）"><a-input v-model="form.newKnowledgeId" /></a-form-item
    ><a-form-item label="原版本"><a-input-number v-model="form.oldVersion" :min="1" /></a-form-item
    ><a-form-item label="替代发布版本"><a-input-number v-model="form.newVersion" :min="1" /></a-form-item>
    <a-checkbox v-for="key in boundaries" :key="key" v-model="unchanged[key]">{{ labels[key] }}保持不变</a-checkbox>
    <a-form-item label="兼容性与当前条件核实证据"
      ><a-input v-model="form.evidence" placeholder="逗号分隔，可使用 TASK: 标识任务证据"
    /></a-form-item>
    <a-form-item label="前置条件已用当前绑定对象核实"
      ><a-checkbox v-model="form.preconditions">兼容且有证据</a-checkbox></a-form-item
    ><a-form-item label="输出转换"><a-checkbox v-model="form.lossless">一对一、无损且有证据</a-checkbox></a-form-item>
    <a-form-item
      v-for="kind in ['inputMapping', 'outputMapping']"
      :key="kind"
      :label="kind === 'inputMapping' ? '输入别名映射' : '输出别名映射'"
      ><div v-for="(row, index) in mappings[kind]" :key="index"
        ><a-input v-model="row.old" placeholder="原字段" /><a-input v-model="row.next" placeholder="新字段" /></div
      ><a-button @click="mappings[kind].push({ old: '', next: '' })">添加映射</a-button></a-form-item
    >
    <a-checkbox v-for="assertion in completedAssertions" :key="assertion.id" v-model="applicable[assertion.id]"
      >已完成断言 {{ assertion.expected }} 仍适用，证据支持</a-checkbox
    >
    <a-form-item label="替代原因"><a-textarea v-model="form.reason" /></a-form-item
    ><a-button :loading="loading" @click="propose">提交差异方案</a-button>
    <ContentReader v-if="instance.substitution" :value="instance.substitution" /><a-checkbox
      v-if="canConfirm"
      v-model="confirmed"
      >已逐项核对当前差异方案和证据</a-checkbox
    ><a-button v-if="canConfirm && instance.substitution" :disabled="!confirmed" :loading="loading" @click="confirm"
      >以当前方案确认替代</a-button
    >
  </a-form>
</template>

<script setup lang="ts">
  import { computed, reactive, ref } from 'vue';

  import ContentReader from '@/views/test-asset/knowledge/ContentReader.vue';

  import type { ExecutionInstance, ProtocolRecord } from '@/api/modules/governed-execution';

  const props = defineProps<{ instance: ExecutionInstance; canConfirm: boolean; loading: boolean }>();
  const emit = defineEmits<{ execute: [operation: string, values: ProtocolRecord] }>();
  const boundaries = [
    'goal',
    'expected',
    'assertions',
    'entryPoint',
    'authorization',
    'environment',
    'dataScope',
    'risk',
    'irreversibleEffects',
  ];
  const labels: Record<string, string> = {
    goal: '目标',
    expected: '预期',
    assertions: '断言',
    entryPoint: '入口',
    authorization: '权限',
    environment: '环境',
    dataScope: '数据范围',
    risk: '风险',
    irreversibleEffects: '不可逆副作用',
  };
  const unchanged = reactive<Record<string, boolean>>({});
  const applicable = reactive<Record<string, boolean>>({});
  const mappings = reactive<Record<string, Array<{ old: string; next: string }>>>({
    inputMapping: [],
    outputMapping: [],
  });
  const confirmed = ref(false);
  const form = reactive({
    knowledgeId: '',
    newKnowledgeId: '',
    oldVersion: 1,
    newVersion: 1,
    evidence: '',
    preconditions: false,
    lossless: false,
    reason: '',
  });
  const knowledge = computed(() => (props.instance.knowledge || []) as ProtocolRecord[]);
  const completedAssertions = computed(() =>
    props.instance.contract.assertions.filter((a) => props.instance.steps.some((step) => step.stepId === a.stepId))
  );
  function propose() {
    const evidence = form.evidence
      .split(',')
      .map((id) => id.trim())
      .filter(Boolean)
      .map((id) => ({ kind: id.startsWith('TASK:') ? 'TASK' : 'PERSONAL', id: id.replace(/^(TASK|PERSONAL):/, '') }));
    const difference = {
      ...Object.fromEntries(boundaries.map((key) => [key, unchanged[key] ? 'UNCHANGED' : 'CHANGED'])),
      preconditions: form.preconditions ? 'COMPATIBLE_WITH_EVIDENCE' : 'UNPROVEN',
      outputs: form.lossless ? 'LOSSLESS_WITH_EVIDENCE' : 'UNPROVEN',
      evidence,
      ...Object.fromEntries(
        Object.entries(mappings).map(([key, rows]) => [key, Object.fromEntries(rows.map((row) => [row.old, row.next]))])
      ),
      assertionChecks: completedAssertions.value.map((a) => ({
        assertionId: a.id,
        assertionHash: (props.instance.assertionHashes as ProtocolRecord)[a.id],
        stillApplicable: !!applicable[a.id],
        evidence,
      })),
    };
    emit('execute', 'knowledge.substitution.propose', {
      knowledgeId: form.knowledgeId,
      oldVersion: form.oldVersion,
      newVersion: form.newVersion,
      ...(form.newKnowledgeId ? { newKnowledgeId: form.newKnowledgeId } : {}),
      difference,
      reason: form.reason,
    });
  }
  function confirm() {
    const proposal = props.instance.substitution as ProtocolRecord;
    emit('execute', 'knowledge.substitution.confirm', {
      knowledgeId: proposal.knowledgeId,
      newKnowledgeId: proposal.newKnowledgeId,
      oldVersion: proposal.oldVersion,
      newVersion: proposal.newVersion,
      proposalHash: props.instance.substitutionHash,
      reason: form.reason,
    });
  }
</script>
