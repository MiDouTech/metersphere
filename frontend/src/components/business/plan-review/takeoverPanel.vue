<template>
  <a-modal v-model:visible="visible" title="原操作受控接管" :footer="false" :width="820">
    <a-alert type="warning"
      >接管不恢复已结束的关联，也不自动追平成员。须负责人明确批准，再由新执行者确认；每一步重新检查当前权限。</a-alert
    >
    <a-alert v-if="error" type="error" class="mt-2">请求未确认完成，请刷新原记录后重试；不会创建替代对象。</a-alert>
    <a-select
      v-if="managedOperations.length"
      :options="
        managedOperations.map((item) => ({ label: `${item.operationId} · ${item.stage}`, value: item.operationId }))
      "
      placeholder="选择需要接管的原操作"
      class="my-3"
      @change="selectOperation"
    />
    <a-space v-if="operation" class="my-3">
      <a-select
        v-model="newActor"
        :options="users"
        placeholder="选择接管人"
        :disabled="busy"
        :style="{ width: '240px' }"
        allow-search
      />
      <a-button :disabled="busy || !newActor" @click="requestPreview">生成接管预览</a-button>
    </a-space>
    <a-button class="my-3" :disabled="busy" @click="refresh">刷新审批记录</a-button>
    <a-list :data="items" :loading="busy">
      <template #item="{ item }"
        ><a-list-item>
          <div>{{ item.operationId }} · {{ userName(item.oldActor) }} → {{ userName(item.newActor) }}</div>
          <a-alert v-if="item.credentialToWeb"
            >本次改为网页登录身份执行，原Agent凭据引用仅保留追踪，后续不会借用。即使接手账号相同，也必须由负责人明确批准。</a-alert
          >
          <div v-if="item.blockedReason">{{ item.blockedReason }}</div>
          <div
            >有效至 {{ new Date(item.expiresAt).toLocaleString() }} ·
            {{
              item.consumed
                ? '已接管'
                : item.stale
                ? '已失效'
                : item.approved
                ? '已批准，待新执行者确认'
                : '待负责人批准'
            }}</div
          >
          <a-popconfirm
            v-if="item.canApprove"
            content="确认允许所选接管人继续此原操作的未完成步骤？成功结果和原发起者记录会保留。"
            @ok="approve(item)"
          >
            <a-button :disabled="busy || !knownUser(item.newActor)" size="small">明确批准接管</a-button>
          </a-popconfirm>
          <a-popconfirm
            v-if="item.canTakeover"
            content="确认以自己的当前权限接管？原身份不会再用于后续源写入。"
            @ok="confirm(item)"
          >
            <a-button :disabled="busy || !knownUser(item.newActor)" size="small" type="primary"
              >以我的身份确认接管</a-button
            >
          </a-popconfirm>
        </a-list-item></template
      >
    </a-list>
  </a-modal>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';

  import type { OperationTakeover, RelationOperation } from '@/api/modules/plan-review';
  import {
    approveOperationTakeover,
    confirmOperationTakeover,
    getOperationTakeovers,
    getPlanReviewOperations,
    previewOperationTakeover,
  } from '@/api/modules/plan-review';
  import { getProjectOptions } from '@/api/modules/project-management/projectMember';
  import { hasAnyPermission } from '@/utils/permission';

  const props = defineProps<{ projectId: string }>();
  const emit = defineEmits<{ (e: 'change'): void }>();
  const visible = ref(false);
  const busy = ref(false);
  const error = ref(false);
  const newActor = ref('');
  const operation = ref<RelationOperation>();
  const items = ref<OperationTakeover[]>([]);
  const users = ref<{ label: string; value: string }[]>([]);
  const managedOperations = ref<RelationOperation[]>([]);
  let context = 0;
  const keys = new Map<string, string>();
  const userName = (id: string) => users.value.find((user) => user.value === id)?.label || '项目账号';
  const knownUser = (id: string) => users.value.some((user) => user.value === id);
  function selectOperation(id: unknown) {
    operation.value = managedOperations.value.find((item) => item.operationId === id);
    newActor.value = '';
  }
  async function refresh() {
    const current = ++context;
    const project = props.projectId;
    busy.value = true;
    error.value = false;
    try {
      const records = await getOperationTakeovers(project);
      if (current !== context) return;
      items.value = records.items;
    } catch {
      if (current === context) error.value = true;
    } finally {
      if (current === context) busy.value = false;
    }
  }
  async function action(work: () => Promise<unknown>) {
    if (busy.value) return;
    const current = context;
    busy.value = true;
    error.value = false;
    try {
      await work();
      if (current !== context) return;
      emit('change');
      await refresh();
    } catch {
      if (current === context) error.value = true;
    } finally {
      if (current === context) busy.value = false;
    }
  }
  async function requestPreview() {
    const selected = operation.value;
    if (!selected || !newActor.value) return;
    await action(() =>
      previewOperationTakeover(selected.operationId, {
        projectId: props.projectId,
        expectedRevision: selected.revision,
        newActor: newActor.value,
      })
    );
  }
  async function approve(item: OperationTakeover) {
    await action(() =>
      approveOperationTakeover({ projectId: props.projectId, previewId: item.previewId, acknowledged: true })
    );
  }
  async function confirm(item: OperationTakeover) {
    const key = keys.get(item.previewId) || crypto.randomUUID();
    keys.set(item.previewId, key);
    await action(() =>
      confirmOperationTakeover({
        projectId: props.projectId,
        approvalId: item.previewId,
        expectedRevision: item.revision,
        requestKey: key,
      })
    );
  }
  async function open(value?: RelationOperation) {
    operation.value = value;
    newActor.value = '';
    items.value = [];
    users.value = [];
    managedOperations.value = [];
    visible.value = true;
    const project = props.projectId;
    const current = context + 1;
    await refresh();
    if (current !== context || project !== props.projectId || !visible.value) return;
    try {
      const result = await getProjectOptions(project);
      if (current === context)
        users.value = result.map((user: { id: string; name: string }) => ({ label: user.name, value: user.id }));
    } catch {
      if (current === context && value) error.value = true;
    }
    if (!value && current === context && hasAnyPermission(['PLAN_REVIEW:TAKEOVER_APPROVE'])) {
      try {
        const result = await getPlanReviewOperations(project, true);
        if (current === context) managedOperations.value = result.items.filter((item) => item.canRequestTakeover);
      } catch {
        if (current === context) error.value = true;
      }
    }
  }
  function invalidate() {
    context++;
    busy.value = false;
    visible.value = false;
    items.value = [];
    operation.value = undefined;
    keys.clear();
  }
  watch(() => props.projectId, invalidate);
  watch(visible, (value) => {
    if (!value) invalidate();
  });
  onBeforeUnmount(invalidate);
  defineExpose({ open });
</script>
