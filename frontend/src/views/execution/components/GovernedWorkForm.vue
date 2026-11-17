<template>
  <a-form :model="form" layout="vertical">
    <a-form-item label="操作"
      ><a-select v-model="operation"
        ><a-option v-for="item in visibleOperations" :key="item.value" :value="item.value">{{
          item.label
        }}</a-option></a-select
      ></a-form-item
    >
    <template v-if="['action.prepare', 'cleanup.prepare'].includes(operation)"
      ><a-alert>先保存动作意图并收到许可，再在业务系统中操作；不确定结果不能直接重试。</a-alert
      ><a-form-item label="动作类型"
        ><a-select v-model="form.actionType"
          ><a-option v-for="item in instance.contract.allowedActions" :key="item">{{ item }}</a-option></a-select
        ></a-form-item
      ><a-form-item v-for="field in actionFields" :key="field.key" :label="field.label"
        ><a-input v-model="form[field.key]" /></a-form-item
    ></template>
    <template v-if="['step.save', 'result.evidence.append'].includes(operation)"
      ><a-form-item label="合约步骤"
        ><a-select v-model="form.stepId"
          ><a-option v-for="step in instance.contract.steps" :key="step.id" :value="step.id">{{
            step.action
          }}</a-option></a-select
        ></a-form-item
      ><a-form-item label="恢复许可（失败步骤重试时填写）"><a-input v-model="form.recoveryPermitId" /></a-form-item
      ><ProtocolValueFields v-model="variables" :declarations="caseVariables" /><a-form-item label="实际观察"
        ><a-textarea v-model="form.observation" /></a-form-item
      ><a-form-item label="步骤事实"
        ><a-select v-model="form.outcome"
          ><a-option>SATISFIED</a-option><a-option>VIOLATED</a-option><a-option>UNKNOWN</a-option
          ><a-option>NOT_REACHED</a-option></a-select
        ></a-form-item
      ><a-alert>任务来源同时提交原合约的实际观测值，由原质量门禁判定；不会将预期自动填作实际值。</a-alert
      ><a-form-item
        v-for="(assertion, index) in legacyAssertions"
        :key="index"
        :label="String(assertion.assertionId || `原断言 ${index + 1}`)"
        ><a-select
          v-if="assertion.valueType === 'boolean' || typeof assertion.expected === 'boolean'"
          v-model="legacyActual[index]"
          ><a-option :value="true">true</a-option><a-option :value="false">false</a-option></a-select
        ><a-input-number
          v-else-if="
            ['number', 'integer'].includes(String(assertion.valueType)) || typeof assertion.expected === 'number'
          "
          v-model="legacyActual[index]" /><a-input
          v-else
          v-model="legacyActual[index]"
          placeholder="实际观测文本；不自动复制预期" /></a-form-item
    ></template>
    <template v-if="operation === 'data.record'"
      ><a-form-item label="数据要求"
        ><a-select v-model="form.key"
          ><a-option
            v-for="item in instance.contract.dataRequirements"
            :key="String(item.key)"
            :value="String(item.key)"
            >{{ item.key }} · {{ item.entity }}</a-option
          ></a-select
        ></a-form-item
      ><a-form-item label="已核实业务对象"><a-input v-model="form.objectId" /></a-form-item
      ><a-form-item label="对应已确认动作（如适用）"><a-input v-model="form.actionId" /></a-form-item
      ><a-alert>根据当前权威查询填写实际条件，不会将合约预期自动当成实测值。</a-alert
      ><ProtocolValueFields v-model="actualConditions" :declarations="conditionFields"
    /></template>
    <template v-if="['recovery.record', 'cleanup.recovery.record'].includes(operation)"
      ><a-form-item label="关联失败事件"><a-input v-model="form.failureEventId" /></a-form-item
      ><a-form-item v-for="field in recoveryFields" :key="field.key" :label="field.label"
        ><a-textarea v-model="form[field.key]" /></a-form-item
    ></template>
    <template v-if="operation === 'knowledge.adopt'"
      ><a-form-item label="知识标识"><a-input v-model="form.knowledgeId" /></a-form-item
      ><a-form-item label="固定版本"><a-input-number v-model="knowledgeVersion" :min="1" /></a-form-item
      ><a-form-item label="关联步骤"
        ><a-select v-model="form.stepId"
          ><a-option v-for="step in instance.contract.steps" :key="step.id" :value="step.id">{{
            step.action
          }}</a-option></a-select
        ></a-form-item
      ><a-form-item label="采用目的"><a-input v-model="form.purpose" /></a-form-item
    ></template>
    <template v-if="operation === 'human.request'"
      ><a-form-item label="阻塞原因"
        ><a-select v-model="form.condition"
          ><a-option>MISSING_CREDENTIAL</a-option><a-option>OUT_OF_SCOPE</a-option
          ><a-option>UNRESOLVED_REQUIREMENT_CONFLICT</a-option><a-option>UNKNOWN_ACTION</a-option
          ><a-option>INDEPENDENT_REVIEW_UNAVAILABLE</a-option><a-option>METHOD_NOT_KNOWN</a-option></a-select
        ></a-form-item
      ><a-form-item label="具体阻塞及已完成检查"><a-textarea v-model="form.reason" /></a-form-item
    ></template>
    <template v-if="operation === 'case.finish'"
      ><a-form-item label="提交结果"
        ><a-select v-model="form.result"
          ><a-option>PASS</a-option><a-option>FAIL</a-option><a-option>BLOCKED</a-option
          ><a-option>EXECUTION_ERROR</a-option><a-option>NOT_RUN</a-option></a-select
        ></a-form-item
      ><a-form-item label="说明"><a-textarea v-model="form.reason" /></a-form-item
      ><ProtocolValueFields v-model="outputs" :declarations="instance.contract.outputs" /><div
        v-for="assertion in assertions"
        :key="assertion.assertionId"
        class="my-3 border p-3"
        ><strong>{{ assertion.expected }}</strong
        ><a-textarea v-model="assertion.observation" placeholder="实际观察" /><a-input
          v-model="assertion.evidenceIds"
          placeholder="该断言的个人证据标识，逗号分隔" /></div
    ></template>
    <a-form-item label="本次证据标识（逗号分隔）"><a-input v-model="evidenceIds" /></a-form-item
    ><a-button
      type="primary"
      :disabled="!visibleOperations.some((item) => item.value === operation)"
      :loading="loading"
      @click="submit"
      >提交当前操作</a-button
    >
  </a-form>
</template>

<script setup lang="ts">
  import { computed, reactive, ref, watch } from 'vue';

  import ProtocolValueFields from './ProtocolValueFields.vue';

  import { type ExecutionInstance, type ProtocolRecord } from '@/api/modules/governed-execution';

  const props = defineProps<{
    instance: ExecutionInstance;
    loading: boolean;
    canExecute?: boolean;
    canSupplement?: boolean;
  }>();
  const emit = defineEmits<{ execute: [operation: string, values: ProtocolRecord] }>();
  const operations = [
    { value: 'action.prepare', label: '准备外部动作' },
    { value: 'cleanup.lease.takeover', label: '取得结果结束后的清理租约' },
    { value: 'cleanup.prepare', label: '清理本执行已创建的对象（仅删除）' },
    { value: 'cleanup.recovery.record', label: '清理失败后有新依据的恢复' },
    { value: 'data.record', label: '绑定已核实数据' },
    { value: 'step.save', label: '保存步骤事实' },
    { value: 'recovery.record', label: '有新依据的恢复' },
    { value: 'knowledge.adopt', label: '采用固定知识' },
    { value: 'human.request', label: '请求人工处理' },
    { value: 'case.finish', label: '提交待独立核验结果' },
    { value: 'result.evidence.append', label: '补证并更正已记录的步骤事实' },
  ];
  const visibleOperations = computed(() =>
    operations.filter((item) =>
      item.value === 'result.evidence.append'
        ? !!props.canSupplement && props.instance.verification !== 'ACCEPTED' && !!props.instance.result
        : props.canExecute !== false &&
          (item.value.startsWith('cleanup.') ? props.instance.phase === 'ENDED' : props.instance.channel === 'OPEN')
    )
  );
  const operation = ref('action.prepare');
  watch(
    visibleOperations,
    (values) => {
      if (!values.some((item) => item.value === operation.value) && values.length) operation.value = values[0].value;
    },
    { immediate: true }
  );
  const form = reactive<Record<string, string>>({
    actionType: '',
    target: '',
    expectedEffect: '',
    businessKey: '',
    queryMethod: '',
    targetEntity: '',
    objectId: '',
    retryOf: '',
    recoveryPermitId: '',
    stepId: '',
    observation: '',
    outcome: 'UNKNOWN',
    key: '',
    actionId: '',
    failureEventId: '',
    changedCondition: '',
    proposedAction: '',
    expectedObservation: '',
    knowledgeId: '',
    purpose: '',
    condition: '',
    reason: '',
    result: '',
  });
  const knowledgeVersion = ref(1);
  const evidenceIds = ref('');
  const conditions = reactive<Record<string, any>>({});
  const assertions = ref<Array<{ assertionId: string; expected: string; observation: string; evidenceIds: string }>>(
    []
  );
  const legacyActual = reactive<Record<number, any>>({});
  const legacyAssertions = computed(() => {
    const step = props.instance.contract.steps.find((item) => item.id === form.stepId);
    const byPosition = props.instance.legacyStepAssertions as Record<string, ProtocolRecord[]> | undefined;
    return byPosition?.[String(step?.sourcePosition)] || [];
  });
  const variables = ref<ProtocolRecord>({});
  const outputs = ref<ProtocolRecord>({});
  const caseVariables = computed(() =>
    Object.fromEntries(
      Object.entries(props.instance.contract.variables || {}).filter(
        ([, value]) => (value as ProtocolRecord).scope === 'CASE'
      )
    )
  );
  const actionFields = [
    { key: 'targetEntity', label: '授权实体类型' },
    { key: 'objectId', label: '已归属本次执行的对象（修改或删除时必填）' },
    { key: 'retryOf', label: '原已确认无副作用动作（重试时必填）' },
    { key: 'recoveryPermitId', label: '恢复许可（重试时必填）' },
    { key: 'target', label: '目标对象及范围' },
    { key: 'expectedEffect', label: '预期副作用' },
    { key: 'businessKey', label: '可追踪业务键' },
    { key: 'queryMethod', label: '事后权威查询方法' },
  ];
  const recoveryFields = [
    { key: 'observation', label: '新观察' },
    { key: 'changedCondition', label: '已发生变化的条件' },
    { key: 'proposedAction', label: '与前次不同的恢复动作' },
    { key: 'expectedObservation', label: '预计如何核实' },
  ];
  function refs(value: string) {
    return value
      .split(',')
      .map((id) => id.trim())
      .filter(Boolean)
      .map((id) => ({ kind: id.startsWith('TASK:') ? 'TASK' : 'PERSONAL', id: id.replace(/^(TASK|PERSONAL):/, '') }));
  }
  const actualConditions = computed({
    get: () => ({ ...conditions }),
    set: (value: ProtocolRecord) => {
      Object.keys(conditions).forEach((key) => delete conditions[key]);
      Object.assign(conditions, value);
    },
  });
  const conditionFields = computed(() => {
    const requirement = props.instance.contract.dataRequirements.find((item) => item.key === form.key);
    return (requirement?.fields ||
      Object.fromEntries(
        Object.entries((requirement?.conditions || {}) as ProtocolRecord).map(([name, value]) => [
          name,
          { type: Array.isArray(value) ? 'array' : typeof value },
        ])
      )) as ProtocolRecord;
  });
  function submit() {
    let values: ProtocolRecord = {};
    if (['action.prepare', 'cleanup.prepare'].includes(operation.value))
      values = {
        actionType: form.actionType,
        target: form.target,
        targetEntity: form.targetEntity,
        ...Object.fromEntries(
          ['objectId', 'retryOf', 'recoveryPermitId'].filter((key) => form[key]).map((key) => [key, form[key]])
        ),
        expectedEffect: form.expectedEffect,
        businessKey: form.businessKey,
        queryMethod: form.queryMethod,
        parameters: {},
        ...(operation.value === 'cleanup.prepare' ? { evidence: refs(evidenceIds.value) } : {}),
      };
    else if (operation.value === 'step.save')
      values = {
        stepId: form.stepId,
        observation: form.observation,
        outcome: form.outcome,
        evidence: refs(evidenceIds.value),
        variables: variables.value,
        ...(form.recoveryPermitId ? { recoveryPermitId: form.recoveryPermitId } : {}),
        ...(props.instance.legacyStepAssertions
          ? {
              legacyAssertions: legacyAssertions.value.map((item, index) => ({
                ...(item.contractVersion === 'v2' ? { assertionId: item.assertionId } : {}),
                actual: legacyActual[index],
              })),
            }
          : {}),
      };
    else if (operation.value === 'data.record')
      values = {
        key: form.key,
        objectId: form.objectId,
        conditions: { ...conditions },
        ...(form.actionId ? { actionId: form.actionId } : {}),
        evidence: refs(evidenceIds.value),
      };
    else if (['recovery.record', 'cleanup.recovery.record'].includes(operation.value))
      values = {
        failureEventId: form.failureEventId,
        ...Object.fromEntries(recoveryFields.map((field) => [field.key, form[field.key]])),
        evidence: refs(evidenceIds.value),
      };
    else if (operation.value === 'knowledge.adopt')
      values = {
        knowledgeId: form.knowledgeId,
        knowledgeVersion: knowledgeVersion.value,
        stepId: form.stepId,
        purpose: form.purpose,
      };
    else if (operation.value === 'human.request')
      values = { condition: form.condition, reason: form.reason, attempts: [] };
    else if (operation.value === 'result.evidence.append')
      values = {
        submissionRevision: props.instance.submissionRevision,
        assertions: [],
        correctedSteps: [
          {
            stepId: form.stepId,
            observation: form.observation,
            outcome: form.outcome,
            evidence: refs(evidenceIds.value),
            ...(props.instance.legacyStepAssertions
              ? {
                  legacyAssertions: legacyAssertions.value.map((item, index) => ({
                    ...(item.contractVersion === 'v2' ? { assertionId: item.assertionId } : {}),
                    actual: legacyActual[index],
                  })),
                }
              : {}),
          },
        ],
      };
    else if (operation.value === 'cleanup.lease.takeover') values = {};
    else
      values = {
        result: form.result,
        reason: form.reason,
        assertions: assertions.value
          .filter((item) => item.observation.trim())
          .map((item) => ({
            assertionId: item.assertionId,
            observation: item.observation,
            evidence: refs(item.evidenceIds),
          })),
        outputs: outputs.value,
      };
    emit('execute', operation.value, values);
  }
  watch(
    () => form.stepId,
    () => {
      Object.keys(legacyActual).forEach((key) => delete legacyActual[Number(key)]);
    }
  );
  watch(
    () => form.key,
    () => {
      Object.keys(conditions).forEach((item) => delete conditions[item]);
    }
  );
  watch(
    () => props.instance.caseExecutionId,
    () => {
      assertions.value = props.instance.contract.assertions.map((item) => ({
        assertionId: item.id,
        expected: item.expected,
        observation: '',
        evidenceIds: '',
      }));
      evidenceIds.value = '';
      variables.value = {};
      outputs.value = {};
    },
    { immediate: true }
  );
</script>
