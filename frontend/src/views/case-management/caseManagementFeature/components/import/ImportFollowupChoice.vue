<template>
  <a-spin :loading="loading" class="mb-3 block">
    <div v-if="options" class="rounded border border-solid border-[var(--color-border-2)] p-3">
      <template v-if="options.groups.length && !policy">
        <p>检测到再次导入，是否更新已有测试计划与评审计划？</p>
        <a-space v-if="!declined"
          ><a-button @click="policy = 'APPEND_EXISTING'">是，更新已有</a-button
          ><a-button @click="declined = true">否</a-button></a-space
        >
        <a-space v-else
          ><span>是否新建并关联？</span
          ><a-button :disabled="!options.canCreate" @click="policy = 'CREATE_NEW_PAIR'">是</a-button
          ><a-button @click="policy = 'IMPORT_ONLY'">否，仅导入</a-button></a-space
        >
      </template>
      <a-select v-if="policy === 'APPEND_EXISTING'" v-model="groupId" placeholder="选择当前项目的关联组" class="mb-2"
        ><a-option v-for="group in options.groups" :key="group.id" :value="group.id"
          >{{ group.planName }} / {{ group.reviewName }}</a-option
        ></a-select
      >
      <template v-if="policy && policy !== 'IMPORT_ONLY'">
        <p>{{
          policy === 'CREATE_NEW_PAIR'
            ? '本次导入成功后新建测试计划和评审计划'
            : '将本次成功导入的用例关联到所选计划与评审'
        }}</p>
        <a-select v-model="reviewers" multiple placeholder="选择评审人" :options="reviewerOptions" class="mb-2" />
        <a-radio-group v-model="rule"
          ><a-radio value="SINGLE">单人通过</a-radio><a-radio value="MULTIPLE">全部通过</a-radio></a-radio-group
        >
      </template>
      <a-alert v-if="!options.canCreate" type="warning" class="my-2"
        >当前账号可能缺少创建权限；更新已有目标仍由服务端检查关联权限。</a-alert
      >
      <a-button
        v-if="policy"
        type="text"
        @click="
          policy = '';
          declined = false;
        "
        >重新选择</a-button
      >
      <a-button type="text" @click="policy = 'IMPORT_ONLY'">仅导入，不关联</a-button>
    </div>
    <a-alert v-else-if="!loading" type="warning">关联选项加载失败，请关闭后重试。</a-alert>
  </a-spin>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import MSR from '@/api/http';
  import { getReviewUsers } from '@/api/modules/case-management/caseReview';

  const props = defineProps<{ projectId: string }>();
  const loading = ref(true);
  const options = ref<{
    canCreate: boolean;
    groups: { id: string; revision: number; planName: string; reviewName: string }[];
  }>();
  const policy = ref('');
  const declined = ref(false);
  const groupId = ref('');
  const reviewers = ref<string[]>([]);
  const reviewerOptions = ref<{ label: string; value: string }[]>([]);
  const rule = ref('SINGLE');
  function decision() {
    const group = options.value?.groups.find((item) => item.id === groupId.value);
    if (
      !options.value ||
      !policy.value ||
      (policy.value === 'APPEND_EXISTING' && !group) ||
      (policy.value !== 'IMPORT_ONLY' && !reviewers.value.length)
    ) {
      Message.warning('请选择导入关联策略、目标与评审人');
      return undefined;
    }
    return {
      policy: policy.value,
      targetGroupId: group?.id,
      expectedGroupRevision: group?.revision,
      reviewers: reviewers.value,
      reviewPassRule: rule.value,
    };
  }
  defineExpose({ decision });
  onMounted(async () => {
    try {
      options.value = await MSR.get({
        url: '/ai/case-imports/followup-options',
        params: { projectId: props.projectId },
      });
      if (!options.value?.groups.length && options.value?.canCreate) policy.value = 'CREATE_NEW_PAIR';
      if (options.value?.groups.length === 1) groupId.value = options.value.groups[0].id;
      const users = await getReviewUsers(props.projectId, '');
      reviewerOptions.value = users.map((user) => ({ label: user.name, value: user.id }));
    } finally {
      loading.value = false;
    }
  });
</script>
