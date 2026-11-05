<template>
  <a-modal
    v-model:visible="visible"
    title="历史关联迁移"
    width="940px"
    :footer="false"
    :mask-closable="false"
    :on-before-cancel="canClose"
  >
    <a-alert
      >只处理明确选择的原记录。不会补建历史未关联计划，不会替换原目标或清除成员、意见。请先停止旧入口和旧消费者，再进行负责人批准。</a-alert
    >
    <a-space class="my-3">
      <a-button :disabled="busy" @click="scan">重新盘点</a-button>
      <a-select
        v-if="recent.length"
        v-model="lookupId"
        :disabled="busy"
        allow-search
        placeholder="选择已有迁移"
        :options="recent.map((item) => ({ value: item.id, label: `${stateLabel(item.state)} · ${item.id}` }))"
      />
      <a-input v-model="lookupId" :disabled="busy" placeholder="继续已有迁移：输入原迁移编号" />
      <a-button :disabled="busy || !lookupId.trim()" @click="loadExisting">读取原迁移</a-button>
    </a-space>
    <a-spin :loading="busy" class="w-full">
      <template v-if="inventory && !change">
        <p>清单版本：{{ inventory.manifestHash }}</p>
        <a-table :data="inventory.groups" :pagination="false" row-key="legacyGroupId" :scroll="{ y: 230 }">
          <template #columns>
            <a-table-column title="原关系" data-index="legacyGroupId" />
            <a-table-column title="分类"
              ><template #cell="{ record }">{{ classificationLabel(record.classification) }}</template></a-table-column
            >
            <a-table-column title="原计划 / 原评审"
              ><template #cell="{ record }"
                >{{ record.planId || '不可访问' }} / {{ record.reviewId || '不可访问' }}</template
              ></a-table-column
            >
            <a-table-column title="成员差异"
              ><template #cell="{ record }"
                >新增 {{ record.addCount ?? '-' }}，退出 {{ record.removeCount ?? '-' }}</template
              ></a-table-column
            >
            <a-table-column title="明确选择"
              ><template #cell="{ record }"
                ><a-select
                  v-model="groupChoices[record.legacyGroupId]"
                  :disabled="busy"
                  allow-clear
                  placeholder="暂不选择"
                  :options="groupOptions" /></template
            ></a-table-column>
          </template>
        </a-table>
        <div v-for="job in jobs" :key="job.jobId" class="my-3 rounded border p-3">
          <p>原任务 {{ job.jobId }} · {{ job.policy }} · {{ job.state }} · {{ job.stage || '原任务记录' }}</p>
          <a-select
            v-model="jobChoices[job.jobId].action"
            :disabled="busy"
            placeholder="明确处理方式"
            :options="jobOptions"
          />
          <template v-if="jobChoices[job.jobId].action === 'ADOPT'">
            <a-input
              v-model="jobChoices[job.jobId].actor"
              :disabled="busy"
              placeholder="当前执行人账号ID（必填）"
              class="mt-2"
            />
            <a-select v-model="jobChoices[job.jobId].mode" :disabled="busy" class="mt-2" :options="modeOptions" />
            <p>仅在旧记录缺少评审设置时补齐；不能改写此前已确认的设置。原评审已存在时保持当前人员。</p>
            <a-input v-model="jobChoices[job.jobId].name" :disabled="busy" placeholder="缺失的评审名称" />
            <a-input
              v-model="jobChoices[job.jobId].module"
              :disabled="busy"
              placeholder="明确的评审目录ID（根目录填写root）"
            />
            <a-input
              v-model="jobChoices[job.jobId].reviewers"
              :disabled="busy"
              placeholder="评审人员账号ID，以英文逗号分隔"
            />
            <a-select
              v-model="jobChoices[job.jobId].rule"
              :disabled="busy"
              allow-clear
              placeholder="评审通过规则"
              :options="ruleOptions"
            />
          </template>
        </div>
        <a-button v-if="canMigrate" type="primary" :disabled="busy || !hasSelection" @click="preview"
          >预览所选清单及影响</a-button
        >
      </template>
      <template v-if="change">
        <p>迁移编号：{{ change.id }}</p
        ><p>阶段：{{ stateLabel(change.state) }}；原备份版本：{{ change.snapshot_hash }}</p>
        <p v-for="effect in change.effects" :key="effect">{{ effect }}</p>
        <pre class="max-h-48 overflow-auto whitespace-pre-wrap">{{ JSON.stringify(change.selection, null, 2) }}</pre>
        <a-alert v-if="change.rehearsal_id" type="success">此清单已有真实隔离演练凭证。</a-alert>
        <a-checkbox v-model="acknowledged" :disabled="busy">我确认本次显示的准确清单和影响</a-checkbox>
        <template v-if="change.state === 'REHEARSED' && change.canApprove">
          <a-input
            v-model="stopEvidence"
            :disabled="busy"
            placeholder="停入口、停旧消费者和核实水位的证据SHA256（不填写任何密钥）"
          />
          <a-input v-model="releaseSha" :disabled="busy" placeholder="已停止旧写入的兼容程序提交SHA（40位）" />
        </template>
        <a-space class="mt-3" wrap>
          <a-button
            v-if="change.canExecute && change.state === 'PREVIEW'"
            :disabled="busy || !acknowledged"
            @click="step('backup')"
            >保存准确备份</a-button
          >
          <a-button
            v-if="change.canExecute && change.state === 'BACKED_UP'"
            :disabled="busy || !acknowledged"
            @click="step('rehearse')"
            >在独立MySQL中演练</a-button
          >
          <a-button
            v-if="change.canApprove && change.state === 'REHEARSED'"
            :disabled="busy || !acknowledged || !validEvidence"
            @click="step('approve')"
            >负责人批准原清单</a-button
          >
          <a-button
            v-if="change.canExecute && change.state === 'APPROVED'"
            status="warning"
            :disabled="busy || !acknowledged"
            @click="step('execute')"
            >执行已批准迁移</a-button
          >
          <a-button
            v-if="change.canExecute && change.state === 'APPLIED'"
            :disabled="busy"
            @click="step('rollback-preview')"
            >仅预览回退影响</a-button
          >
        </a-space>
        <template v-if="rollbackHash">
          <a-alert class="mt-3" type="warning"
            >回退方案：{{ rollbackMode }}。旧任务不会复活；已有后续写入时只允许保留事实的补偿。</a-alert
          >
          <a-button v-if="change.canApprove" :disabled="busy || !acknowledged" @click="step('rollback-approve')"
            >负责人批准此回退</a-button
          >
          <a-button
            v-if="change.canExecute"
            :disabled="busy || !acknowledged"
            status="warning"
            @click="step('rollback')"
            >执行此回退</a-button
          >
        </template>
      </template>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';

  import type {
    LegacyAction,
    LegacyChange,
    LegacyDecision,
    LegacyInventory,
    LegacyJob,
  } from '@/api/modules/plan-review/legacy';
  import {
    getLegacyChange,
    getLegacyChanges,
    legacyMigrationStep,
    previewLegacyMigration,
    scanLegacyRelations,
  } from '@/api/modules/plan-review/legacy';
  import { useAppStore } from '@/store';
  import { hasAnyPermission } from '@/utils/permission';

  const app = useAppStore();
  const visible = ref(false);
  const busy = ref(false);
  let epoch = 0;
  const inventory = ref<LegacyInventory>();
  const change = ref<LegacyChange>();
  const lookupId = ref('');
  const recent = ref<LegacyChange[]>([]);
  const groupChoices = reactive<Record<string, string>>({});
  const jobChoices = reactive<
    Record<
      string,
      {
        action: string;
        actor: string;
        mode: 'ORIGINAL' | 'WEB';
        name: string;
        module: string;
        reviewers: string;
        rule: string;
      }
    >
  >({});
  const acknowledged = ref(false);
  const stopEvidence = ref('');
  const releaseSha = ref('');
  const rollbackHash = ref('');
  const rollbackMode = ref('');
  let requestKey = '';
  function stateLabel(state: string) {
    return (
      (
        {
          PREVIEW: '待备份',
          BACKED_UP: '待隔离演练',
          REHEARSED: '待负责人批准',
          APPROVED: '已批准，待执行',
          APPLIED: '迁移已执行',
          ROLLED_BACK: '已受控回退',
        } as Record<string, string>
      )[state] || state
    );
  }
  function classificationLabel(state: string) {
    return (
      (
        {
          LEGAL_UNIQUE: '合法唯一关系',
          DUPLICATE_PAIR: '同一对重复旧边',
          MULTIPLE_TARGETS: '多个目标冲突',
          CONTRADICTORY_FIELDS: '字段矛盾',
          ORPHAN: '原对象缺失',
          CROSS_PROJECT: '跨项目旧边',
          LOCKED: '原对象锁定',
          UNKNOWN_STATE: '状态待核实',
        } as Record<string, string>
      )[state] || state
    );
  }
  const groupOptions = [
    { value: 'KEEP', label: '保留原两对象' },
    { value: 'ISOLATE', label: '隔离此旧边' },
    { value: 'RELEASE', label: '明确解除旧边' },
  ];
  const jobOptions = [
    { value: 'ADOPT', label: '移交原任务，等待显式恢复' },
    { value: 'ISOLATE', label: '隔离并停止旧任务' },
    { value: 'TERMINATE', label: '明确终止后续步骤' },
  ];
  const modeOptions = [
    { value: 'ORIGINAL', label: '保留原执行认证' },
    { value: 'WEB', label: '显式申请负责人批准网页接手' },
  ];
  const ruleOptions = [
    { value: 'SINGLE', label: '任一人员通过' },
    { value: 'MULTIPLE', label: '全部人员通过' },
  ];
  const canMigrate = computed(() => hasAnyPermission(['PLAN_REVIEW:MIGRATE']));
  const validEvidence = computed(
    () => /^[a-fA-F0-9]{64}$/.test(stopEvidence.value.trim()) && /^[a-fA-F0-9]{40}$/.test(releaseSha.value.trim())
  );
  const hasSelection = computed(
    () => Object.values(groupChoices).some(Boolean) || Object.values(jobChoices).some((job) => !!job.action)
  );
  const jobs = computed(() => {
    const found = new Map<string, LegacyJob>();
    inventory.value?.groups.forEach((group) => group.jobs.forEach((job) => found.set(job.jobId, job)));
    inventory.value?.orphanJobs
      .filter((job) => job.policy !== 'IMPORT_ONLY')
      .forEach((job) => found.set(job.job_id, { jobId: job.job_id, policy: job.policy, state: job.state }));
    return [...found.values()];
  });
  function reset() {
    epoch += 1;
    inventory.value = undefined;
    change.value = undefined;
    lookupId.value = '';
    recent.value = [];
    busy.value = false;
    Object.keys(groupChoices).forEach((key) => delete groupChoices[key]);
    Object.keys(jobChoices).forEach((key) => delete jobChoices[key]);
    acknowledged.value = false;
    rollbackHash.value = '';
    rollbackMode.value = '';
    stopEvidence.value = '';
    releaseSha.value = '';
    requestKey = '';
  }
  async function guarded(action: (project: string, current: () => boolean) => Promise<void>) {
    if (busy.value || !app.currentProjectId) return;
    const project = app.currentProjectId;
    const token = ++epoch;
    busy.value = true;
    const current = () => visible.value && token === epoch && project === app.currentProjectId;
    try {
      await action(project, current);
    } finally {
      if (token === epoch) busy.value = false;
    }
  }
  async function scan() {
    await guarded(async (project, current) => {
      const [result, history] = await Promise.all([scanLegacyRelations(project), getLegacyChanges(project)]);
      if (!current() || result.projectId !== project || history.projectId !== project) return;
      recent.value = history.items;
      change.value = undefined;
      inventory.value = result;
      requestKey = crypto.randomUUID();
      rollbackHash.value = '';
      acknowledged.value = false;
      Object.keys(groupChoices).forEach((key) => delete groupChoices[key]);
      Object.keys(jobChoices).forEach((key) => delete jobChoices[key]);
      jobs.value.forEach((job) => {
        jobChoices[job.jobId] = {
          action: '',
          actor: '',
          mode: 'ORIGINAL',
          name: '',
          module: '',
          reviewers: '',
          rule: '',
        };
      });
    });
  }
  async function loadExisting() {
    const id = lookupId.value.trim();
    if (!id) return;
    await guarded(async (project, current) => {
      const result = await getLegacyChange(project, id);
      if (current() && result.project_id === project) {
        change.value = result;
        acknowledged.value = false;
        rollbackHash.value = '';
      }
    });
  }
  async function preview() {
    if (!inventory.value) return;
    const data: LegacyDecision = {
      projectId: app.currentProjectId,
      manifestHash: inventory.value.manifestHash,
      requestKey,
      groups: {},
      jobs: {},
    };
    Object.entries(groupChoices)
      .filter(([, action]) => !!action)
      .forEach(([id, action]) => {
        data.groups[id] = action;
      });
    Object.entries(jobChoices)
      .filter(([, job]) => !!job.action)
      .forEach(([id, job]) => {
        data.jobs[id] = { action: job.action, executingActor: job.actor.trim(), executionMode: job.mode };
        if (job.name.trim() && job.module.trim() && job.reviewers.trim() && job.rule)
          data.jobs[id].reviewSettings = {
            name: job.name.trim(),
            moduleId: job.module.trim(),
            reviewers: [
              ...new Set(
                job.reviewers
                  .split(',')
                  .map((value) => value.trim())
                  .filter(Boolean)
              ),
            ],
            reviewPassRule: job.rule,
          };
      });
    await guarded(async (project, current) => {
      const result = await previewLegacyMigration(data);
      if (!current() || result.project_id !== project) return;
      const details = await getLegacyChange(project, result.id);
      if (current() && details.project_id === project) {
        change.value = details;
        lookupId.value = details.id;
        acknowledged.value = false;
      }
    });
  }
  async function step(action: LegacyAction) {
    const selected = change.value;
    if (!selected || (action !== 'rollback-preview' && !acknowledged.value)) return;
    await guarded(async (project, current) => {
      const result = await legacyMigrationStep(action, {
        projectId: project,
        changeId: selected.id,
        snapshotHash: selected.snapshot_hash,
        acknowledged: acknowledged.value,
        stopEvidenceHash: stopEvidence.value.trim(),
        releaseSha: releaseSha.value.trim(),
        previewHash: rollbackHash.value,
      });
      if (!current() || (result.projectId && result.projectId !== project)) return;
      const details = await getLegacyChange(project, selected.id);
      if (!current() || details.project_id !== project) return;
      change.value = details;
      acknowledged.value = false;
      if (action === 'rollback-preview') {
        rollbackHash.value = result.previewHash || '';
        rollbackMode.value = result.mode || '';
      }
      if (action === 'rollback') rollbackHash.value = '';
    });
  }
  function canClose() {
    return !busy.value;
  }
  async function open() {
    reset();
    visible.value = true;
    await scan();
  }
  watch(
    () => app.currentProjectId,
    () => {
      visible.value = false;
      reset();
    }
  );
  watch(visible, (value) => {
    if (!value) reset();
  });
  onBeforeUnmount(reset);
  defineExpose({ open });
</script>
