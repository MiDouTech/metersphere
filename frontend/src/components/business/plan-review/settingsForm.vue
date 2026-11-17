<template>
  <section aria-label="关联评审设置" class="mb-6">
    <a-divider orientation="left">关联评审设置</a-divider>
    <a-alert>保存真实测试计划时自动创建一份关联评审，仅同步有效功能用例。评审人不会自动代选。</a-alert>
    <a-alert v-if="failed" type="error" class="mt-2"
      >目录或评审人加载失败，暂不能创建。<a-button @click="load">重试</a-button></a-alert
    >
    <a-form ref="formRef" :model="settings" :disabled="props.disabled" layout="vertical" class="mt-3">
      <a-form-item field="name" label="评审名称" :rules="[{ required: true, message: '请填写评审名称' }]">
        <a-input v-model="settings.name" :max-length="255" :disabled="props.disabled || settings.followPlanName" />
        <a-checkbox v-model="settings.followPlanName" class="ml-2">随计划名称生成</a-checkbox>
      </a-form-item>
      <a-form-item field="moduleId" label="评审目录" :rules="[{ required: true, message: '请选择评审目录' }]">
        <a-tree-select
          v-model="settings.moduleId"
          :data="modules"
          :loading="loading"
          :field-names="{ key: 'id', title: 'name', children: 'children' }"
        />
      </a-form-item>
      <a-form-item
        field="reviewers"
        label="评审人"
        :rules="[{ required: true, type: 'array', minLength: 1, message: '请至少选择一名评审人' }]"
      >
        <a-select
          v-model="settings.reviewers"
          :options="users"
          :loading="loading"
          multiple
          allow-search
          placeholder="请选择评审人"
        />
      </a-form-item>
      <a-form-item field="reviewPassRule" label="通过规则">
        <a-radio-group v-model="settings.reviewPassRule"
          ><a-radio value="SINGLE">任一评审人通过</a-radio
          ><a-radio value="MULTIPLE">所有评审人通过</a-radio></a-radio-group
        >
      </a-form-item>
    </a-form>
  </section>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';

  import { getReviewModules, getReviewUsers } from '@/api/modules/case-management/caseReview';
  import type { PlanReviewSettings } from '@/api/modules/plan-review';

  import type { ReviewModuleItem } from '@/models/caseManagement/caseReview';

  import type { FormInstance, SelectOptionData } from '@arco-design/web-vue';

  const props = defineProps<{ projectId: string; planName: string; disabled?: boolean }>();
  const settings = ref<PlanReviewSettings>({
    name: '-评审',
    moduleId: 'root',
    reviewers: [],
    reviewPassRule: 'SINGLE',
    followPlanName: true,
  });
  const formRef = ref<FormInstance>();
  const modules = ref<ReviewModuleItem[]>([]);
  const users = ref<SelectOptionData[]>([]);
  const loading = ref(false);
  const failed = ref(false);
  let sequence = 0;
  const defaultName = computed(() => {
    const base = props.planName.trim();
    let end = Math.min(base.length, 252);
    if (end < base.length && /[\uD800-\uDBFF]/.test(base.charAt(end - 1))) end--;
    return `${base.slice(0, end)}-评审`;
  });
  watch(
    () => [defaultName.value, settings.value.followPlanName],
    () => {
      if (settings.value.followPlanName) settings.value.name = defaultName.value;
    },
    { immediate: true }
  );
  async function load() {
    const current = ++sequence;
    loading.value = true;
    failed.value = false;
    try {
      const [folders, reviewers] = await Promise.all([
        getReviewModules(props.projectId),
        getReviewUsers(props.projectId, ''),
      ]);
      if (current !== sequence) return;
      modules.value = folders;
      users.value = reviewers.map((user) => ({ label: user.name, value: user.id }));
    } catch {
      if (current === sequence) failed.value = true;
    } finally {
      if (current === sequence) loading.value = false;
    }
  }
  watch(
    () => props.projectId,
    () => {
      settings.value.moduleId = 'root';
      settings.value.reviewers = [];
      load();
    },
    { immediate: true }
  );
  async function validate() {
    if (loading.value || failed.value || !props.planName.trim()) return undefined;
    if (await formRef.value?.validate()) return undefined;
    return { ...settings.value, reviewers: [...settings.value.reviewers] };
  }
  defineExpose({ validate });
</script>
