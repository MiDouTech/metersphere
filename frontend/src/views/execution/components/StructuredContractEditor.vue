<template>
  <a-spin :loading="loading" class="block">
    <a-alert class="mb-4"
      >结构化版本与原用例分别保存；转换仅供预览，缺项必须补齐后才能发布。原步骤与原件不被覆盖。</a-alert
    >
    <a-space class="mb-4"
      ><a-button @click="load">读取结构化版本</a-button><a-button @click="convert">预览文字转换</a-button
      ><a-button :loading="saving" @click="save(false)">保存草稿</a-button
      ><a-button type="primary" :loading="saving" @click="save(true)">校验并发布</a-button></a-space
    >
    <a-alert v-if="message" class="mb-4" :type="issues.length ? 'warning' : 'info'"
      >{{ message }} {{ issues.join('；') }}</a-alert
    >
    <a-form :model="form" layout="vertical">
      <a-form-item label="目标" required><a-textarea v-model="form.goal" /></a-form-item>
      <a-form-item label="前置条件" required
        ><a-textarea v-model="form.preconditions" placeholder="没有前置条件时明确填写“不需要”"
      /></a-form-item>
      <a-form-item label="目标环境" required><a-input v-model="form.environmentId" /></a-form-item>
      <a-form-item label="风险与不可逆操作边界" required><a-textarea v-model="form.riskBoundary" /></a-form-item>
      <a-form-item label="授权动作"
        ><a-input-tag v-model="form.allowedActions" placeholder="只声明任务可授权范围；此处不授予权限"
      /></a-form-item>
      <a-divider>业务步骤</a-divider>
      <a-card v-for="(step, index) in form.steps" :key="step.id" class="mb-3" :title="`步骤 ${index + 1}`">
        <a-space
          ><a-input v-model="step.id" placeholder="步骤标识" /><a-input-number
            v-model="step.sourcePosition"
            :min="1"
            placeholder="原步骤序号"
          /><a-button status="danger" @click="form.steps.splice(index, 1)">移除</a-button></a-space
        >
        <a-form-item label="操作"><a-textarea v-model="step.action" /></a-form-item
        ><a-form-item label="预期观察"><a-textarea v-model="step.expectedObservation" /></a-form-item>
        <a-space v-for="(binding, bindingIndex) in bindingRows[step.id] || []" :key="bindingIndex" class="mb-2"
          ><a-input v-model="binding.field" placeholder="输入字段" /><a-input
            v-model="binding.reference"
            placeholder="data.绑定.id / case.依赖.outputs.字段"
          /><a-button @click="bindingRows[step.id].splice(bindingIndex, 1)">移除绑定</a-button></a-space
        ><a-button @click="addBinding(step.id)">声明输入绑定</a-button>
      </a-card>
      <a-button @click="addStep">添加步骤</a-button>
      <a-divider>必要断言及证据要求</a-divider>
      <a-card v-for="(assertion, index) in form.assertions" :key="assertion.id" class="mb-3" :title="assertion.id">
        <a-space
          ><a-input v-model="assertion.id" placeholder="断言标识" /><a-checkbox v-model="assertion.required"
            >必要断言</a-checkbox
          ><a-button status="danger" @click="form.assertions.splice(index, 1)">移除</a-button></a-space
        >
        <a-form-item label="关联步骤"
          ><a-select v-model="assertion.stepId"
            ><a-option v-for="step in form.steps" :key="step.id" :value="step.id">{{
              step.action || step.id
            }}</a-option></a-select
          ></a-form-item
        >
        <a-form-item label="被测入口"><a-input v-model="assertion.entryPoint" /></a-form-item
        ><a-form-item label="预期"><a-textarea v-model="assertion.expected" /></a-form-item>
        <a-form-item label="失败判据"><a-textarea v-model="assertion.failureRule" /></a-form-item>
        <a-form-item label="证据类型"
          ><a-input-tag
            v-model="assertion.evidenceTypes"
            placeholder="页面截图、业务对象查询、拒绝响应等" /></a-form-item
        ><a-form-item label="核实对象与字段"><a-input-tag v-model="assertion.objectFields" /></a-form-item>
        <a-form-item label="核验方式"
          ><a-select v-model="assertion.verificationMethod"
            ><a-option value="INDEPENDENT_HUMAN">独立人员逐项核实</a-option
            ><a-option value="AUTHORIZED_QUERY">授权权威系统查询</a-option></a-select
          ></a-form-item
        >
      </a-card>
      <a-button @click="addAssertion">添加断言</a-button>
      <a-divider>输出与执行变量</a-divider
      ><a-alert>变量必须显式声明。共享变量使用独立上下文版本，依赖只读取指定实例已接受的输出。</a-alert>
      <a-card v-for="(row, index) in fieldRows" :key="index" class="my-3"
        ><a-space
          ><a-select v-model="row.kind"
            ><a-option value="output">用例输出</a-option><a-option value="CASE">实例变量</a-option
            ><a-option value="EXECUTION">会话变量</a-option></a-select
          ><a-input v-model="row.name" placeholder="字段名称" /><a-select v-model="row.type"
            ><a-option>string</a-option><a-option>number</a-option><a-option>integer</a-option
            ><a-option>boolean</a-option></a-select
          ><a-input v-model="row.unit" placeholder="单位（如适用）" /><a-checkbox v-model="row.nullable"
            >可空</a-checkbox
          ><a-checkbox v-model="row.required">必填</a-checkbox
          ><a-button @click="fieldRows.splice(index, 1)">移除</a-button></a-space
        ></a-card
      ><a-button
        @click="fieldRows.push({ kind: 'output', name: '', type: 'string', unit: '', nullable: false, required: true })"
        >添加输出或变量</a-button
      >
      <a-divider>数据准备与依赖</a-divider>
      <a-card v-for="(requirement, index) in dataRows" :key="index" class="mb-3" title="数据条件">
        <a-space
          ><a-input v-model="requirement.key" placeholder="绑定名称" /><a-input
            v-model="requirement.entity"
            placeholder="业务对象"
          /><a-input v-model="requirement.preparationMethodRef" placeholder="已发布准备方法及版本引用" /><a-button
            @click="dataRows.splice(index, 1)"
            >移除</a-button
          ></a-space
        >
        <a-checkbox-group v-model="requirement.strategies"
          ><a-checkbox value="SEARCH">查询既有数据</a-checkbox
          ><a-checkbox value="CREATE">授权创建数据</a-checkbox></a-checkbox-group
        ><a-space v-for="(condition, conditionIndex) in requirement.conditions" :key="conditionIndex" class="mt-2"
          ><a-input v-model="condition.field" placeholder="字段" /><a-select v-model="condition.type"
            ><a-option value="string">文本</a-option><a-option value="number">数值</a-option
            ><a-option value="boolean">布尔</a-option></a-select
          ><a-input v-model="condition.value" placeholder="所需值" /><a-button
            @click="requirement.conditions.splice(conditionIndex, 1)"
            >移除条件</a-button
          ></a-space
        ><a-button class="mt-2" @click="requirement.conditions.push({ field: '', type: 'string', value: '' })"
          >添加条件</a-button
        > </a-card
      ><a-button
        @click="dataRows.push({ key: '', entity: '', preparationMethodRef: '', conditions: [], strategies: [] })"
        >添加数据要求</a-button
      >
      <a-card v-for="(dependency, index) in dependencyRows" :key="index" class="my-3" title="指定生产用例依赖"
        ><a-space
          ><a-input v-model="dependency.alias" placeholder="依赖别名" /><a-input
            v-model="dependency.caseId"
            placeholder="生产用例"
          /><a-input-number v-model="dependency.contractVersion" :min="1" placeholder="固定结构版本" /><a-button
            @click="dependencyRows.splice(index, 1)"
            >移除</a-button
          ></a-space
        ></a-card
      ><a-button @click="dependencyRows.push({ alias: '', caseId: '', contractVersion: 1, bindings: {} })"
        >添加依赖</a-button
      >
    </a-form>
  </a-spin>
</template>

<script setup lang="ts">
  import { reactive, ref, watch } from 'vue';

  import {
    convertStructuredContract,
    getStructuredContract,
    saveStructuredContract,
    stableRequest,
    type StructuredContract,
  } from '@/api/modules/governed-execution';

  const props = defineProps<{ projectId: string; caseId: string }>();
  const empty = (): StructuredContract => ({
    schemaVersion: '1.0',
    goal: '',
    preconditions: '',
    environmentId: '',
    riskBoundary: '',
    allowedActions: [],
    steps: [],
    assertions: [],
    dataRequirements: [],
    dependencies: [],
    outputs: {},
    knowledgeRefs: [],
  });
  const form = reactive(empty());
  const version = ref(0);
  const loading = ref(false);
  const saving = ref(false);
  const issues = ref<string[]>([]);
  const message = ref('');
  const fieldRows = ref<
    Array<{ kind: string; name: string; type: string; unit: string; nullable: boolean; required: boolean }>
  >([]);
  const bindingRows = reactive<Record<string, Array<{ field: string; reference: string }>>>({});
  const dataRows = ref<
    Array<{
      key: string;
      entity: string;
      preparationMethodRef: string;
      strategies: string[];
      conditions: Array<{ field: string; type: string; value: string }>;
    }>
  >([]);
  const dependencyRows = ref<
    Array<{ alias: string; caseId: string; contractVersion: number; bindings: Record<string, string> }>
  >([]);
  function assign(value: StructuredContract) {
    Object.assign(form, empty(), value);
    version.value = value.contractVersion || 0;
    fieldRows.value = [
      ...Object.entries(value.outputs || {}).map(([name, field]) => ({
        name,
        ...(field as Record<string, unknown>),
        kind: 'output',
      })),
      ...Object.entries(value.variables || {}).map(([name, field]) => ({
        name,
        ...(field as Record<string, unknown>),
        kind: String((field as Record<string, unknown>).scope || 'CASE'),
      })),
    ].map((item) => {
      const field = item as Record<string, unknown>;
      return {
        name: String(field.name),
        kind: String(field.kind),
        type: String(field.type),
        unit: String(field.unit || ''),
        nullable: !!field.nullable,
        required: field.required !== false,
      };
    });
    Object.keys(bindingRows).forEach((key) => delete bindingRows[key]);
    value.steps.forEach((step) => {
      bindingRows[step.id] = Object.entries(step.inputBindings || {}).map(([field, reference]) => ({
        field,
        reference,
      }));
    });
    dependencyRows.value = (value.dependencies || []).map((item) => ({
      alias: String(item.alias),
      caseId: String(item.caseId),
      contractVersion: Number(item.contractVersion),
      bindings: (item.bindings || {}) as Record<string, string>,
    }));
    dataRows.value = (value.dataRequirements || []).map((item) => ({
      key: String(item.key),
      entity: String(item.entity),
      preparationMethodRef: String(item.preparationMethodRef),
      strategies: (item.strategies || []) as string[],
      conditions: Object.entries((item.conditions || {}) as Record<string, unknown>).map(([field, entry]) => ({
        field,
        type: typeof entry,
        value: String(entry),
      })),
    }));
  }
  function addBinding(stepId: string) {
    if (!bindingRows[stepId]) bindingRows[stepId] = [];
    bindingRows[stepId].push({ field: '', reference: '' });
  }
  async function load() {
    if (!props.caseId || !props.projectId) return;
    loading.value = true;
    message.value = '';
    const project = props.projectId;
    const id = props.caseId;
    try {
      const result = await getStructuredContract(project, id);
      if (project === props.projectId && id === props.caseId) assign(result);
    } catch {
      message.value = '尚无可读结构化版本，或当前账号无权限；原用例保留，可选择预览文字转换。';
    } finally {
      loading.value = false;
    }
  }
  async function convert() {
    loading.value = true;
    const project = props.projectId;
    const id = props.caseId;
    try {
      const result = await convertStructuredContract(project, id);
      if (project !== props.projectId || id !== props.caseId) return;
      assign(result);
      message.value = '转换预览未写入，请核对前置、断言、证据和风险边界。';
    } finally {
      loading.value = false;
    }
  }
  function addStep() {
    form.steps.push({
      id: `S${form.steps.length + 1}`,
      sourcePosition: form.steps.length + 1,
      action: '',
      expectedObservation: '',
      inputBindings: {},
    });
  }
  function addAssertion() {
    form.assertions.push({
      id: `A${form.assertions.length + 1}`,
      required: true,
      stepId: form.steps[0]?.id || '',
      entryPoint: '',
      expected: '',
      evidenceTypes: [],
      objectFields: [],
      verificationMethod: 'INDEPENDENT_HUMAN',
      failureRule: '',
    });
  }
  async function save(publish: boolean) {
    saving.value = true;
    try {
      const descriptor = (row: (typeof fieldRows.value)[number]) => ({
        ...(((row.kind === 'output' ? form.outputs : form.variables)?.[row.name] || {}) as Record<string, unknown>),
        type: row.type,
        unit: row.unit,
        nullable: row.nullable,
        required: row.required,
      });
      const content = {
        schemaVersion: '1.0',
        goal: form.goal,
        preconditions: form.preconditions,
        environmentId: form.environmentId,
        riskBoundary: form.riskBoundary,
        allowedActions: form.allowedActions,
        steps: form.steps.map((step) => ({
          ...step,
          inputBindings: Object.fromEntries(
            (bindingRows[step.id] || []).map((binding) => [binding.field, binding.reference])
          ),
        })),
        assertions: form.assertions,
        dependencies: dependencyRows.value,
        outputs: Object.fromEntries(
          fieldRows.value.filter((row) => row.kind === 'output').map((row) => [row.name, descriptor(row)])
        ),
        variables: Object.fromEntries(
          fieldRows.value
            .filter((row) => row.kind !== 'output')
            .map((row) => [row.name, { ...descriptor(row), scope: row.kind }])
        ),
        knowledgeRefs: form.knowledgeRefs,
        dataRequirements: dataRows.value.map((row) => ({
          key: row.key,
          entity: row.entity,
          preparationMethodRef: row.preparationMethodRef,
          strategies: row.strategies,
          conditions: Object.fromEntries(
            row.conditions.map((condition) => {
              if (condition.type === 'number' && !Number.isFinite(Number(condition.value)))
                throw new Error('请输入有效数值条件');
              if (condition.type === 'boolean' && !['true', 'false'].includes(condition.value))
                throw new Error('布尔条件仅允许true或false');
              let actual: string | number | boolean = condition.value;
              if (condition.type === 'number') actual = Number(condition.value);
              if (condition.type === 'boolean') actual = condition.value === 'true';
              return [condition.field, actual];
            })
          ),
          fields: Object.fromEntries(row.conditions.map((condition) => [condition.field, { type: condition.type }])),
        })),
      };
      const result = await saveStructuredContract(
        props.projectId,
        props.caseId,
        stableRequest(`contract:${props.caseId}`, { expectedVersion: version.value, content, publish })
      );
      version.value = result.version;
      issues.value = result.issues;
      message.value = publish ? '结构化版本已发布' : '草稿已保存';
    } finally {
      saving.value = false;
    }
  }
  watch(
    () => [props.projectId, props.caseId],
    () => {
      assign(empty());
      load();
    },
    { immediate: true }
  );
</script>
