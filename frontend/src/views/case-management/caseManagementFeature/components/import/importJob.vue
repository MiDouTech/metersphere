<template>
  <a-drawer
    :visible="visible"
    title="导入预检与任务"
    :width="760"
    :footer="false"
    unmount-on-close
    @cancel="emit('update:visible', false)"
  >
    <a-alert class="mb-3"
      >预检已固定文件、模板和覆盖目标。提交后在后台逐条导入；关闭窗口不会停止任务，停止会保留已成功写入的用例。</a-alert
    >
    <a-alert v-if="error" type="error" class="mb-3">{{ error }}</a-alert>
    <template v-if="job">
      <p>{{ job.fileName }} · {{ labels[job.state] || job.state }} · 共 {{ job.total }} 条</p>
      <div
        v-if="job.state === 'PRECHECKED' && followupOptions"
        class="mb-4 rounded border border-solid border-[var(--color-border-2)] p-4"
      >
        <template v-if="followupOptions.groups.length && !policy">
          <p>检测到再次导入测试用例，是否更新已有测试计划和评审计划？</p>
          <a-space v-if="!declinedUpdate"
            ><a-button @click="policy = 'APPEND_EXISTING'">是，更新已有</a-button
            ><a-button @click="declinedUpdate = true">否</a-button></a-space
          >
          <template v-else
            ><p>是否新建测试计划和评审计划，并关联本次成功导入的用例？</p
            ><a-space
              ><a-button :disabled="!followupOptions.canCreate" @click="policy = 'CREATE_NEW_PAIR'"
                >是，新建并关联</a-button
              ><a-button @click="policy = 'IMPORT_ONLY'">否，仅导入</a-button></a-space
            ></template
          >
        </template>
        <a-alert v-if="!followupOptions.canCreate" type="warning" class="mb-2"
          >当前账号缺少创建或关联权限；可选择仅导入，已有任务结果不会因此回滚。</a-alert
        >
        <a-select
          v-if="policy === 'APPEND_EXISTING'"
          v-model="targetGroup"
          placeholder="选择要更新的计划与评审"
          class="mb-3"
        >
          <a-option v-for="group in followupOptions.groups" :key="group.id" :value="group.id"
            >{{ group.planName }} / {{ group.reviewName }}</a-option
          >
        </a-select>
        <template v-if="policy === 'CREATE_NEW_PAIR'">
          <p>{{
            policy === 'CREATE_NEW_PAIR'
              ? '成功导入后自动创建测试计划和评审计划'
              : '增量关联到选定目标，保留原执行和评审记录'
          }}</p>
          <a-select
            v-model="reviewers"
            :options="reviewerOptions"
            multiple
            placeholder="选择评审人（必填）"
            allow-search
            class="mb-3"
          />
          <a-select
            v-if="policy === 'CREATE_NEW_PAIR'"
            v-model="reviewPassRule"
            :options="[
              { value: 'SINGLE', label: '单人通过' },
              { value: 'MULTIPLE', label: '多人通过' },
            ]"
          />
        </template>
        <a-space class="mt-3"
          ><a-button
            v-if="policy"
            type="text"
            @click="
              policy = '';
              declinedUpdate = false;
            "
            >重新选择</a-button
          ><a-button v-if="policy !== 'IMPORT_ONLY'" type="text" @click="policy = 'IMPORT_ONLY'"
            >仅导入，不创建或关联计划/评审</a-button
          ><span v-else>仅导入</span></a-space
        >
      </div>
      <a-alert
        v-if="job.followup && !['LEGACY', 'SKIPPED'].includes(job.followup.state)"
        class="mb-3"
        :type="job.followup.state === 'FAILED' ? 'warning' : 'info'"
      >
        计划/评审关联：{{ followupLabels[job.followup.state] || job.followup.state }}
        <div v-if="job.followup.message">{{ job.followup.message }}</div>
        <a-button v-if="job.followup.planId" type="text" @click="openTarget(false)">{{
          job.followup.planName || '查看测试计划'
        }}</a-button>
        <a-button v-if="job.followup.reviewId" type="text" @click="openTarget(true)">{{
          job.followup.reviewName || '查看评审'
        }}</a-button>
        <a-button v-if="job.followup.state === 'FAILED'" :disabled="saving" @click="retryFollowup">只重试关联</a-button>
      </a-alert>
      <a-space class="my-3" wrap>
        <span v-for="count in job.counts" :key="count.state"
          >{{ labels[count.state] || count.state }}：{{ count.total }}</span
        >
      </a-space>
      <a-space class="mb-3" wrap>
        <a-button
          v-if="job.state === 'PRECHECKED'"
          type="primary"
          :loading="saving"
          :disabled="
            !policy ||
            (policy === 'CREATE_NEW_PAIR' && !reviewers.length) ||
            (policy === 'APPEND_EXISTING' && !targetGroup)
          "
          @click="action('commit')"
          >提交有效用例</a-button
        >
        <a-button
          v-if="['PRECHECKED', 'QUEUED', 'RUNNING'].includes(job.state)"
          status="danger"
          :loading="saving"
          @click="action('stop')"
          >停止导入</a-button
        >
        <a-button v-if="['STOPPED', 'PARTIAL', 'FAILED'].includes(job.state)" :loading="saving" @click="action('retry')"
          >重试未成功条目</a-button
        >
        <a-button :loading="loading" :disabled="saving" @click="load">刷新</a-button>
      </a-space>
      <a-table :data="job.items" row-key="position" :pagination="false">
        <template #columns>
          <a-table-column title="条目" data-index="position" :width="70" />
          <a-table-column title="处理"
            ><template #cell="{ record }">{{ labels[record.action] || record.action }}</template></a-table-column
          >
          <a-table-column title="状态"
            ><template #cell="{ record }">{{ labels[record.state] || record.state }}</template></a-table-column
          >
          <a-table-column title="说明" data-index="message" />
        </template>
      </a-table>
      <a-pagination
        class="mt-4"
        v-bind="paging.pagination"
        @change="paging.onPageChange"
        @page-size-change="paging.onPageSizeChange"
      />
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
  import { onBeforeUnmount, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';

  import MSR from '@/api/http';
  import { getReviewUsers } from '@/api/modules/case-management/caseReview';
  import useClientTablePaging from '@/hooks/useClientTablePaging';
  import { ensureAppError, formatAppErrorMessage } from '@/utils/appError';

  import { TestPlanRouteEnum } from '@/enums/routeEnum';

  const props = defineProps<{ jobId: string; visible: boolean }>();
  const emit = defineEmits<{ (event: 'update:visible', value: boolean): void; (event: 'changed'): void }>();
  interface Job {
    jobId: string;
    state: string;
    fileName: string;
    total: number;
    counts: { state: string; total: number }[];
    items: { position: number; action: string; state: string; message?: string }[];
    followup?: {
      state: string;
      message?: string;
      planId?: string;
      reviewId?: string;
      planName?: string;
      reviewName?: string;
    };
  }
  interface FollowupOptions {
    projectId: string;
    canCreate: boolean;
    groups: { id: string; revision: number; planName: string; reviewName: string }[];
  }
  const followupOptions = ref<FollowupOptions>();
  const policy = ref('');
  const targetGroup = ref('');
  const declinedUpdate = ref(false);
  const reviewers = ref<string[]>([]);
  const reviewerOptions = ref<{ label: string; value: string }[]>([]);
  const reviewPassRule = ref('SINGLE');
  const router = useRouter();
  const followupLabels: Record<string, string> = {
    PENDING: '等待或正在关联',
    SUCCEEDED: '已完成',
    FAILED: '部分关联未完成',
  };

  const job = ref<Job>();
  function openTarget(review: boolean) {
    const id = review ? job.value?.followup?.reviewId : job.value?.followup?.planId;
    if (id)
      router.push({
        name: review ? TestPlanRouteEnum.TEST_PLAN_REVIEW_DETAIL : TestPlanRouteEnum.TEST_PLAN_INDEX_DETAIL,
        query: { id },
      });
  }
  const paging = useClientTablePaging(
    'case-import-items',
    () => job.value?.total || 0,
    () => props.jobId
  );
  const loading = ref(false);
  const saving = ref(false);
  const error = ref('');
  const labels: Record<string, string> = {
    PRECHECKED: '等待提交',
    QUEUED: '排队中',
    RUNNING: '导入中',
    COMPLETED: '全部处理完成',
    PARTIAL: '部分未成功',
    STOPPED: '已停止',
    EXPIRED: '已过期',
    FAILED: '失败',
    PENDING: '待处理',
    SUCCESS: '成功',
    INVALID: '校验不通过',
    SKIPPED: '未覆盖，已跳过',
    CREATE: '新增',
    UPDATE: '覆盖',
  };
  let sequence = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let previous = '';
  function clearTimer() {
    if (timer) clearTimeout(timer);
    timer = undefined;
  }
  let generation = 0;
  let optionsPromise: Promise<void> | undefined;
  let pendingLoad: { key: string; promise: Promise<void> } | undefined;
  let reviewersLoaded = false;
  let reviewersPromise: Promise<void> | undefined;
  function loadReviewers(context: number): Promise<void> {
    if (!followupOptions.value || reviewersLoaded || policy.value !== 'CREATE_NEW_PAIR') return Promise.resolve();
    if (reviewersPromise) return reviewersPromise;
    const promise = (async () => {
      const { projectId } = followupOptions.value;
      try {
        const users = await getReviewUsers(projectId, '');
        if (context !== generation) return;
        reviewerOptions.value = users.map((user) => ({ label: user.name, value: user.id }));
        reviewersLoaded = true;
      } catch {
        if (context === generation) error.value = '无法加载评审人，请刷新重试或选择仅导入';
      }
    })();
    reviewersPromise = promise;
    return promise.finally(() => {
      if (reviewersPromise === promise) reviewersPromise = undefined;
    });
  }
  function loadOptions(id: string, context: number): Promise<void> {
    if (followupOptions.value) return loadReviewers(context);
    if (optionsPromise) return optionsPromise;
    const promise = (async () => {
      const options = await MSR.get<FollowupOptions>({
        url: `/ai/case-imports/${encodeURIComponent(id)}/followup-options`,
      });
      if (context !== generation) return;
      followupOptions.value = options;
      if (!options.groups.length && options.canCreate) policy.value = 'CREATE_NEW_PAIR';
      if (options.groups.length === 1) targetGroup.value = options.groups[0].id;
      await loadReviewers(context);
    })();
    optionsPromise = promise;
    return promise.finally(() => {
      if (optionsPromise === promise) optionsPromise = undefined;
    });
  }
  function load(): Promise<void> {
    if (!props.visible || !props.jobId) return Promise.resolve();
    clearTimer();
    const id = props.jobId;
    const context = generation;
    const { current } = paging.value.pagination;
    const { pageSize } = paging.value.pagination;
    const key = `${context}:${id}:${current}:${pageSize}`;
    if (pendingLoad?.key === key) return pendingLoad.promise;
    const request = ++sequence;
    loading.value = true;
    error.value = '';
    const promise = (async () => {
      try {
        const result = await MSR.get<Job>({
          url: `/ai/case-imports/${encodeURIComponent(id)}`,
          params: { current, pageSize },
        });
        if (request !== sequence || context !== generation) return;
        job.value = result;
        if (result.state === 'PRECHECKED') await loadOptions(id, context);
        if (request !== sequence || context !== generation) return;
        if (previous && previous !== result.state && ['COMPLETED', 'PARTIAL', 'STOPPED'].includes(result.state))
          emit('changed');
        previous = result.state;
        if (
          ['QUEUED', 'RUNNING'].includes(result.state) ||
          (result.state !== 'PRECHECKED' && result.followup?.state === 'PENDING')
        )
          timer = setTimeout(load, 3000);
      } catch (failure) {
        if (request === sequence && context === generation)
          error.value = formatAppErrorMessage(ensureAppError(failure, '无法查询导入状态，请重试'));
      } finally {
        if (request === sequence && context === generation) loading.value = false;
        if (pendingLoad?.key === key) pendingLoad = undefined;
      }
    })();
    pendingLoad = { key, promise };
    return promise;
  }
  watch(policy, () => loadReviewers(generation));
  async function action(value: string) {
    if (saving.value) return;
    const id = props.jobId;
    const captured = sequence;
    saving.value = true;
    clearTimer();
    try {
      const group = followupOptions.value?.groups.find((item) => item.id === targetGroup.value);
      if (value === 'commit' && (!policy.value || (policy.value === 'APPEND_EXISTING' && !group))) {
        error.value = '请选择导入关联策略与目标';
        return;
      }
      await MSR.post({
        url: `/ai/case-imports/${encodeURIComponent(id)}/${value}`,
        data:
          value === 'commit'
            ? {
                policy: policy.value,
                targetGroupId: group?.id,
                expectedGroupRevision: group?.revision,
                reviewers: policy.value === 'CREATE_NEW_PAIR' ? reviewers.value : undefined,
                reviewPassRule: policy.value === 'CREATE_NEW_PAIR' ? reviewPassRule.value : undefined,
              }
            : undefined,
      });
      if (captured === sequence) await load();
    } catch (failure) {
      if (captured === sequence)
        error.value = formatAppErrorMessage(ensureAppError(failure, '导入操作失败，请刷新重试'));
    } finally {
      saving.value = false;
    }
  }
  async function retryFollowup() {
    if (saving.value) return;
    saving.value = true;
    try {
      await MSR.post({ url: `/ai/case-imports/${encodeURIComponent(props.jobId)}/followup/retry` });
      await load();
    } catch (failure) {
      error.value = formatAppErrorMessage(ensureAppError(failure, '关联重试失败'));
    } finally {
      saving.value = false;
    }
  }
  watch([() => paging.value.pagination.current, () => paging.value.pagination.pageSize], () => {
    if (props.visible) load();
  });
  watch(
    [() => props.jobId, () => props.visible],
    () => {
      ++generation;
      ++sequence;
      optionsPromise = undefined;
      pendingLoad = undefined;
      reviewersLoaded = false;
      reviewersPromise = undefined;
      reviewerOptions.value = [];
      clearTimer();
      job.value = undefined;
      followupOptions.value = undefined;
      policy.value = '';
      targetGroup.value = '';
      reviewers.value = [];
      declinedUpdate.value = false;
      previous = '';
      paging.value.onPageChange(1);
      if (props.visible) load();
    },
    { immediate: true }
  );
  onBeforeUnmount(() => {
    ++generation;
    ++sequence;
    clearTimer();
  });
</script>
