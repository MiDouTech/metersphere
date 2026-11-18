<template>
  <div class="p-4">
    <a-page-header title="受控执行授权配置" subtitle="仅可信权限管理员可维护；无默认授予" />
    <a-alert class="mb-4" type="warning"
      >人员关联必须有可核实依据，同一自然人的账号和Agent不能规避回避规则。配置记录不等于产品审批、容量证明或生产发布。</a-alert
    >
    <a-space class="mb-4"
      ><a-button :loading="loading" @click="load">刷新配置</a-button
      ><a-radio-group v-model="section" type="button"
        ><a-radio value="identity">人员关联</a-radio><a-radio value="subject">Agent主体</a-radio
        ><a-radio value="grant">能力授权</a-radio><a-radio value="policy">启用与策略</a-radio></a-radio-group
      ></a-space
    >
    <a-form :model="form" layout="vertical" class="max-w-[760px]">
      <a-form-item v-if="section !== 'policy'" label="目标账号" required><a-input v-model="form.userId" /></a-form-item>
      <template v-if="section === 'identity'"
        ><a-form-item label="经核实的自然人关联标识" required><a-input v-model="form.personRef" /></a-form-item
        ><a-form-item label="人员关联证据定位" required><a-input v-model="form.evidenceRef" /></a-form-item
      ></template>
      <template v-if="section === 'subject'"
        ><a-form-item label="个人Token记录标识（不是Token密钥）" required
          ><a-input v-model="form.tokenId" /></a-form-item
        ><a-form-item label="稳定Agent登记标识" required><a-input v-model="form.agentRef" /></a-form-item
        ><a-form-item label="启用绑定"><a-switch v-model="form.enabled" /></a-form-item
      ></template>
      <template v-if="section === 'grant' || section === 'policy'"
        ><a-form-item label="适用测试环境" required><a-input v-model="form.environmentId" /></a-form-item
      ></template>
      <template v-if="section === 'grant'"
        ><a-form-item label="能力" required
          ><a-select v-model="form.capability"
            ><a-option v-for="item in capabilities" :key="item.value" :value="item.value">{{
              item.label
            }}</a-option></a-select
          ></a-form-item
        ><a-form-item label="授权到期时间" required
          ><input v-model="expires" type="datetime-local" class="border p-2" /></a-form-item
        ><a-alert>填写已过期时间可撤销能力；不会删除既有事实或重置预算。</a-alert></template
      >
      <template v-if="section === 'policy'"
        ><a-form-item label="环境允许动作清单" required><a-input-tag v-model="allowedActions" /></a-form-item
        ><div v-for="name in allowedActions" :key="name" class="my-3"
          ><strong>{{ name }}</strong
          ><a-select v-model="actionRules[name].effect"
            ><a-option>READ</a-option><a-option>CREATE</a-option><a-option>MUTATE</a-option
            ><a-option>DELETE</a-option></a-select
          ><a-input v-model="actionRules[name].entity" placeholder="经批准的实体类型" /></div
        ><a-form-item label="精确批准记录" required><a-input v-model="form.approvalRecord" /></a-form-item
        ><a-form-item label="启用新协议"><a-switch v-model="form.enabled" /></a-form-item
        ><a-alert
          >长度、预算、保留及容量依据从服务端明确配置读取。缺项时拒绝启用，不使用默认生产值。关闭后治理和历史查询仍保留。</a-alert
        ></template
      >
      <a-form-item label="当前记录版本" required
        ><a-input-number v-model="form.expectedVersion" :min="0"
      /></a-form-item>
      <a-button type="primary" :loading="saving" @click="save">保存指定配置</a-button>
    </a-form>
    <a-divider>现有配置</a-divider>
    <a-tabs
      ><a-tab-pane key="identity" title="人员关联"
        ><a-table :data="state.identities" :pagination="false" @row-click="editIdentity"
          ><template #columns
            ><a-table-column title="账号" data-index="user_id" /><a-table-column
              title="自然人关联"
              data-index="person_ref" /><a-table-column
              title="版本"
              data-index="version" /></template></a-table></a-tab-pane
      ><a-tab-pane key="subject" title="主体绑定"
        ><a-table :data="state.subjects" :pagination="false"
          ><template #columns
            ><a-table-column title="账号" data-index="user_id" /><a-table-column
              title="Agent登记"
              data-index="agent_ref" /><a-table-column
              title="版本"
              data-index="version" /></template></a-table></a-tab-pane
      ><a-tab-pane key="grant" title="能力授权"
        ><a-table :data="state.grants" :pagination="false"
          ><template #columns
            ><a-table-column title="账号" data-index="user_id" /><a-table-column
              title="能力"
              data-index="capability" /><a-table-column title="环境" data-index="environment_id" /><a-table-column
              title="版本"
              data-index="version" /></template></a-table></a-tab-pane
    ></a-tabs>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import {
    configureProtocolAdministration,
    inspectProtocolAdministration,
    type ProtocolRecord,
  } from '@/api/modules/governed-execution';
  import useAppStore from '@/store/modules/app';

  const app = useAppStore();
  const loading = ref(false);
  const saving = ref(false);
  const section = ref('identity');
  const expires = ref('');
  const allowedActions = ref<string[]>([]);
  const actionRules = reactive<Record<string, { effect: string; entity: string }>>({});
  watch(
    allowedActions,
    (names) => {
      names.forEach((name) => {
        actionRules[name] ||= { effect: '', entity: '' };
      });
      Object.keys(actionRules)
        .filter((name) => !names.includes(name))
        .forEach((name) => delete actionRules[name]);
    },
    { deep: true }
  );
  const state = reactive<{ identities: ProtocolRecord[]; subjects: ProtocolRecord[]; grants: ProtocolRecord[] }>({
    identities: [],
    subjects: [],
    grants: [],
  });
  const form = reactive({
    userId: '',
    personRef: '',
    evidenceRef: '',
    tokenId: '',
    agentRef: '',
    enabled: false,
    environmentId: '',
    capability: '',
    approvalRecord: '',
    expectedVersion: 0,
  });
  const capabilities = [
    { value: 'EXECUTE', label: '执行' },
    { value: 'GOVERN', label: '实例治理' },
    { value: 'VERIFY_EXTERNAL', label: '外部结果核实' },
    { value: 'RESULT_REVIEW', label: '独立结果复核' },
    { value: 'CONFIRM_SUBSTITUTION', label: '确认方法替代' },
    { value: 'AUTHORIZE_TASK', label: '可信任务授权' },
    { value: 'EVIDENCE_READ', label: '证据读取' },
    { value: 'EVIDENCE_WRITE', label: '证据上传与补充' },
    { value: 'BUDGET_READ', label: '预算读取' },
    { value: 'KNOWLEDGE_EDIT', label: '知识编辑' },
    { value: 'KNOWLEDGE_PUBLISH', label: '知识发布与撤回' },
  ];
  async function load() {
    loading.value = true;
    const org = app.currentOrgId;
    try {
      const result = await inspectProtocolAdministration(org);
      if (org === app.currentOrgId) Object.assign(state, result);
    } finally {
      loading.value = false;
    }
  }
  function editIdentity(row: ProtocolRecord) {
    section.value = 'identity';
    Object.assign(form, {
      userId: row.user_id,
      personRef: row.person_ref,
      evidenceRef: row.evidence_ref,
      expectedVersion: row.version,
    });
  }
  async function save() {
    saving.value = true;
    try {
      let request: ProtocolRecord;
      if (section.value === 'identity')
        request = {
          userId: form.userId,
          personRef: form.personRef,
          evidenceRef: form.evidenceRef,
          expectedVersion: form.expectedVersion,
        };
      else if (section.value === 'subject')
        request = {
          userId: form.userId,
          tokenId: form.tokenId,
          agentRef: form.agentRef,
          enabled: form.enabled,
          expectedVersion: form.expectedVersion,
        };
      else if (section.value === 'grant') {
        const time = new Date(expires.value).getTime();
        if (!Number.isFinite(time)) {
          Message.warning('请选择明确到期时间');
          return;
        }
        request = {
          userId: form.userId,
          environmentId: form.environmentId,
          capability: form.capability,
          expiresAt: time,
          expectedVersion: form.expectedVersion,
        };
      } else
        request = {
          environmentId: form.environmentId,
          enabled: form.enabled,
          allowedActions: allowedActions.value,
          actionRules,
          approvalRecord: form.approvalRecord,
          expectedVersion: form.expectedVersion,
        };
      await configureProtocolAdministration(app.currentOrgId, section.value, request);
      Message.success('指定配置已保存');
      await load();
    } finally {
      saving.value = false;
    }
  }
  watch(
    () => app.currentOrgId,
    () => {
      Object.assign(state, { identities: [], subjects: [], grants: [] });
      load();
    }
  );
  onMounted(load);
</script>
