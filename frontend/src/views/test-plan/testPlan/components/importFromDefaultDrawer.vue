<template>
  <a-drawer
    v-model:visible="dialogVisible"
    :title="t('testPlan.testPlanIndex.importFromDefaultProject')"
    :width="520"
    unmount-on-close
    @cancel="handleCancel"
  >
    <a-alert type="info" class="mb-4">{{ t('testPlan.testPlanIndex.importFromDefaultTip') }}</a-alert>
    <a-form :model="form" layout="vertical">
      <a-form-item :label="t('caseManagement.featureCase.importHubConflict')" required>
        <a-radio-group v-model="form.conflictStrategy">
          <a-radio value="SKIP">{{ t('caseManagement.featureCase.importHubSkip') }}</a-radio>
          <a-radio value="OVERWRITE">{{ t('caseManagement.featureCase.importHubOverwrite') }}</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item :label="t('testPlan.testPlanIndex.plan')" required>
        <a-select
          v-model="form.sourcePlanId"
          :loading="loading"
          allow-search
          :placeholder="t('common.pleaseSelect')"
          :options="planOptions"
        />
      </a-form-item>
    </a-form>
    <template #footer>
      <div class="flex justify-end gap-2">
        <a-button @click="handleCancel">{{ t('common.cancel') }}</a-button>
        <a-button type="primary" :loading="submitting" :disabled="!form.sourcePlanId" @click="handleImport">
          {{ t('common.import') }}
        </a-button>
      </div>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';

  import { getDefaultHubProjectId } from '@/api/modules/case-management/defaultHub';
  import { getDefaultHubJob } from '@/api/modules/case-management/featureCase';
  import { getTestPlanList, importPlanFromDefaultProject } from '@/api/modules/test-plan/testPlan';
  import { useI18n } from '@/hooks/useI18n';
  import useAppStore from '@/store/modules/app';

  import Message from '@arco-design/web-vue/es/message';

  const props = defineProps<{ visible: boolean }>();
  const emit = defineEmits<{
    (e: 'update:visible', val: boolean): void;
    (e: 'success'): void;
  }>();

  const { t } = useI18n();
  const appStore = useAppStore();

  const dialogVisible = computed({
    get: () => props.visible,
    set: (val) => emit('update:visible', val),
  });

  const form = ref({ sourcePlanId: '', conflictStrategy: 'SKIP' });
  const planOptions = ref<{ label: string; value: string }[]>([]);
  const loading = ref(false);
  const submitting = ref(false);
  let pollTimer: ReturnType<typeof setInterval> | null = null;

  function stopPoll() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }

  async function loadPlans() {
    try {
      loading.value = true;
      const hubId = await getDefaultHubProjectId();
      if (!hubId) {
        Message.warning('未配置默认项目');
        return;
      }
      const res = await getTestPlanList({
        projectId: hubId,
        current: 1,
        pageSize: 200,
        type: 'TEST_PLAN',
      });
      planOptions.value = (res.list || []).map((item: { name: string; id: string }) => ({
        label: item.name,
        value: item.id,
      }));
    } catch {
      Message.error('加载默认项目计划列表失败');
    } finally {
      loading.value = false;
    }
  }

  watch(
    () => props.visible,
    (val) => {
      if (val) {
        form.value = { sourcePlanId: '', conflictStrategy: 'SKIP' };
        loadPlans();
      } else {
        stopPoll();
      }
    }
  );

  function pollJob(jobId: string): Promise<boolean> {
    return new Promise((resolve) => {
      stopPoll();
      let ticks = 0;
      pollTimer = setInterval(async () => {
        ticks += 1;
        if (ticks > 60) {
          stopPoll();
          Message.warning('导入超时，请稍后刷新列表查看');
          resolve(false);
          return;
        }
        try {
          const job = await getDefaultHubJob(jobId);
          if (job.status === 'SUCCESS') {
            stopPoll();
            resolve(true);
          } else if (job.status === 'FAILED') {
            stopPoll();
            Message.error(job.errorMessage || '导入失败');
            resolve(false);
          }
        } catch {
          stopPoll();
          resolve(false);
        }
      }, 1000);
    });
  }

  async function handleImport() {
    if (!form.value.sourcePlanId) return;
    try {
      submitting.value = true;
      const result = await importPlanFromDefaultProject({
        sourcePlanId: form.value.sourcePlanId,
        targetProjectId: appStore.currentProjectId,
        conflictStrategy: form.value.conflictStrategy,
      });
      const ok = await pollJob(result.jobId);
      if (ok) {
        Message.success(t('testPlan.testPlanIndex.importFromDefaultSuccess'));
        emit('success');
        dialogVisible.value = false;
      }
    } catch {
      Message.error('导入失败');
    } finally {
      submitting.value = false;
    }
  }

  function handleCancel() {
    stopPoll();
    dialogVisible.value = false;
  }
</script>
