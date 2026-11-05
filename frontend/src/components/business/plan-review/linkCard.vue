<template>
  <section class="plan-review-link" aria-label="计划与评审关联">
    <a-spin :loading="loading" class="w-full">
      <div class="flex flex-wrap items-center gap-2">
        <strong>{{ title }}</strong>
        <template v-if="error">
          <a-alert type="error" class="flex-1">关联信息加载失败</a-alert>
          <a-button size="small" @click="load">重试</a-button>
        </template>
        <template v-else-if="summary?.restricted">无权查看关联对象</template>
        <template v-else-if="summary?.legacyBlocked">
          <span>历史关联待交接</span>
          <span class="text-xs text-[var(--color-text-3)]"
            >请由有权人员在测试计划列表的“历史关联迁移”中核查原记录，完成受控交接后再操作。</span
          >
        </template>
        <template v-else-if="summary?.target">
          <a-tooltip :content="summary.target.name">
            <a-link class="max-w-[300px] truncate" @click="openTarget"
              >#{{ summary.target.num }} {{ summary.target.name }}</a-link
            >
          </a-tooltip>
          <span>{{ summary.target.status }}</span>
          <a-tag>{{ syncLabel }}</a-tag>
          <a-button
            size="small"
            :disabled="busy || !summary.canReconfirm"
            title="需要当前同步恢复权限、有效服务授权且双方可编辑"
            @click="beginPreview(true)"
            >查看差异 / 恢复同步</a-button
          >
          <a-popconfirm content="停止同步，双方现有成员和历史保留，不会自动再建评审。是否解除？" @ok="unlink">
            <a-button size="small" :disabled="busy || !summary.canUnlink" title="需要双方读取和更新权限且双方可编辑"
              >解除关联</a-button
            >
          </a-popconfirm>
          <a-button size="small" :disabled="busy" @click="load">刷新</a-button>
        </template>
        <template v-else-if="summary">
          <span>未关联</span>
          <a-button
            size="small"
            :disabled="busy || !canPair"
            title="需要双方读取与更新权限；确认时还需项目服务授权"
            @click="choose"
            >{{ title }}</a-button
          >
        </template>
        <a-button v-if="canManageGrant" size="small" :disabled="busy" @click="showGrant">同步服务授权</a-button>
        <a-button size="small" :disabled="busy" @click="showOperations">操作记录</a-button>
        <a-button size="small" :disabled="busy" @click="takeoverRef?.open()">接管审批</a-button>
        <a-button size="small" :disabled="busy" @click="noticeRef?.open(objectId, side)">通知记录</a-button>
        <a-button v-if="side === 'review'" size="small" :disabled="busy" @click="showHistory">退出成员历史</a-button>
        <a-button
          v-if="['COMPLETED', 'ARCHIVED'].includes(objectStatus || '')"
          size="small"
          :disabled="busy"
          @click="showFrozen"
          >完成 / 归档时快照</a-button
        >
        <a-button
          v-if="objectStatus === 'ARCHIVED'"
          size="small"
          :disabled="busy || !canRestore"
          @click="beginLifecycle('RESTORE_ARCHIVE')"
          >仅将本侧恢复到完成</a-button
        >
        <a-button
          v-if="objectStatus === 'COMPLETED'"
          size="small"
          :disabled="busy || !canRestore"
          @click="beginLifecycle('RESTORE_EDIT')"
          >恢复本侧编辑并预览影响</a-button
        >
        <a-button
          v-if="['PREPARED', 'UNDERWAY', 'COMPLETED'].includes(objectStatus || '')"
          size="small"
          :disabled="busy || !canUpdate"
          @click="beginLifecycle('ARCHIVE')"
          >归档本侧并保存快照</a-button
        >
      </div>
      <div v-if="summary?.sync && !summary.restricted" class="mt-2 text-xs text-[var(--color-text-3)]">
        源版本 {{ summary.sync.sourceVersion }} / 已同步 {{ summary.sync.appliedVersion }}
        <span v-if="summary.sync.delayed"> · 同步延迟</span>
        <span v-if="summary.sync.blockers.length"> · {{ summary.sync.blockers.map(blockerLabel).join('；') }}</span>
        <div v-if="summary.sync.safeError">{{ summary.sync.safeError }}</div>
        <div>{{ summary.scopeMessage }}</div>
        <div v-if="side === 'review'">用例由关联测试计划维护，请前往计划新增或移除；当前成员意见仍按原权限操作。</div>
      </div>
    </a-spin>
    <a-modal
      v-model:visible="selectVisible"
      :title="title"
      :width="760"
      :ok-loading="busy"
      :ok-button-props="{ disabled: !selectedId || candidatesLoading || candidatesError }"
      ok-text="下一步：预览差异"
      @before-ok="() => beginPreview(false)"
    >
      <a-input-search
        v-model="keyword"
        placeholder="搜索名称或编号"
        :max-length="255"
        allow-clear
        @search="searchFirstPage"
      />
      <a-alert class="mt-2"
        >可选择待开始或进行中的未占用对象。历史关联或导入任务尚未交接的对象不会列入，请先在“历史关联迁移”核查。</a-alert
      >
      <a-alert v-if="candidatesError" type="error" class="my-2"
        >候选加载失败，请重试；不会按未关联处理。<a-button size="mini" @click="search">重试</a-button></a-alert
      >
      <a-table
        :data="candidates"
        :loading="candidatesLoading"
        :pagination="false"
        row-key="id"
        class="mt-3"
        @row-click="selectCandidate"
      >
        <template #columns>
          <a-table-column title="选择" :width="60"
            ><template #cell="{ record }"
              ><a-radio
                v-model="selectedId"
                :value="record.id"
                :disabled="candidatesLoading || candidatesError"
                uninject-group-context
                ><span class="sr-only">选择 {{ record.num }} {{ record.name }}</span></a-radio
              ></template
            ></a-table-column
          >
          <a-table-column title="编号" data-index="num" :width="90" />
          <a-table-column title="名称" data-index="name" ellipsis tooltip />
          <a-table-column title="状态" data-index="status" :width="110" />
        </template>
        <template #empty>暂无可关联对象；对象可能已关联、不可编辑或当前账号无权限。</template>
      </a-table>
      <a-pagination
        :current="candidatePage"
        :total="candidateTotal"
        :page-size="20"
        class="mt-3"
        @change="changeCandidatePage"
      />
    </a-modal>
    <a-modal
      v-model:visible="previewVisible"
      title="确认评审成员范围与历史影响"
      :width="800"
      :ok-loading="busy"
      :ok-button-props="{ disabled: !acknowledged || !preview || expired || differenceLoading || differenceError }"
      :ok-text="reconfirming ? '确认继续同步' : '确认关联并同步'"
      @before-ok="confirm"
    >
      <a-alert type="warning">{{ preview?.message }}</a-alert>
      <p v-if="preview" class="my-2 text-xs"
        >关系代次 {{ preview.generation }}；预览有效至 {{ new Date(preview.expiresAt).toLocaleString() }}</p
      >
      <a-tabs v-model:active-key="differenceTab">
        <a-tab-pane v-for="tab in differenceTabs" :key="tab.key" :title="`${tab.label} ${tab.ids.length}`">
          <a-alert v-if="differenceError" type="error">差异加载失败或预览已失效，请重新预览。</a-alert>
          <a-list :data="differenceItems" :loading="differenceLoading">
            <template #item="{ item }"
              ><a-list-item
                >#{{ item.num || '—' }} {{ item.name }}
                <a-tag v-if="item.reentered">重新加入，须重新评审</a-tag></a-list-item
              ></template
            >
          </a-list>
          <a-pagination v-model:current="differencePage" :total="tab.ids.length" :page-size="20" class="mt-2" />
        </a-tab-pane>
      </a-tabs>
      <a-checkbox v-model="acknowledged" class="mt-3">已知晓评审范围和历史影响</a-checkbox>
      <a-alert v-if="expired" type="warning" class="mt-2">预览已过期，请重新预览；已有关系和成功步骤保留。</a-alert>
      <a-alert v-if="operationUnknown" type="warning" class="mt-2"
        >结果待确认，请勿重新创建。<a-button size="small" @click="showOperations">查询原操作</a-button></a-alert
      >
    </a-modal>
    <a-modal v-model:visible="grantVisible" title="项目成员同步服务授权" :ok-loading="busy" @before-ok="saveGrant">
      <a-alert type="warning"
        >这是项目级授权。撤销将停止目标成员写入；恢复授权后仍需有权人员重新预览确认，不会自动追平。</a-alert
      >
      <a-switch v-model="grantEnabled" class="mt-4" checked-text="启用" unchecked-text="停用" />
    </a-modal>
    <a-modal v-model:visible="operationsVisible" title="当前用户操作记录" :footer="false" :width="760">
      <a-alert v-if="operationActionError" type="warning">{{ operationActionError }}</a-alert>
      <a-alert v-if="operationsError" type="error"
        >操作记录加载失败，不能认定操作失败或重新创建。<a-button size="small" @click="showOperations"
          >重试</a-button
        ></a-alert
      >
      <a-list :data="operations"
        ><template #item="{ item }"
          ><a-list-item
            >{{ item.operationId }} · {{ item.stage }} · {{ item.state }}
            <a-button v-if="item.canResume" :disabled="busy" size="small" @click="controlOperation(item, 'resume')"
              >继续原操作</a-button
            >
            <a-popconfirm
              v-if="item.canTerminate"
              content="仅停止此操作未执行的步骤，已成功对象、成员和历史会保留。确认终止？"
              @ok="controlOperation(item, 'terminate')"
            >
              <a-button :disabled="busy" size="small" status="danger">终止后续步骤</a-button>
            </a-popconfirm>
            <a-button v-if="item.canRequestTakeover" :disabled="busy" size="small" @click="takeoverRef?.open(item)"
              >申请接管</a-button
            >
          </a-list-item></template
        ></a-list
      >
    </a-modal>
    <a-modal v-model:visible="frozenVisible" title="完成 / 归档时快照（只读）" :footer="false" :width="800">
      <a-alert v-if="frozenError" type="error"
        >快照读取失败，请重试。<a-button @click="loadFrozen(1)">重试</a-button></a-alert
      >
      <a-spin :loading="frozenLoading" class="w-full">
        <template v-if="frozen?.available">
          <p>冻结于 {{ new Date(frozen.frozenAt || 0).toLocaleString() }}；当前源范围的变化不改写这份记录。</p>
          <p class="my-2"
            >冻结范围 {{ frozen.total }} 条；通过率
            {{ frozen.statistics?.passRate ?? frozen.statistics?.pass_rate ?? '—' }}%</p
          >
          <p v-if="frozen.sourceVersion !== undefined"
            >当时源版本 {{ frozen.sourceVersion }} / 已同步 {{ frozen.appliedVersion }}</p
          >
          <a-list :data="frozen.members"
            ><template #item="{ item }"
              ><a-list-item
                >#{{ item.case_num ?? '—' }} {{ item.case_name || item.case_id }}
                <span v-if="item.status"> · {{ item.status }}</span></a-list-item
              ></template
            ></a-list
          >
          <a-pagination :current="frozenPage" :total="frozen.total || 0" :page-size="20" @change="loadFrozen" />
        </template>
        <a-alert v-else-if="!frozenLoading && !frozenError">暂无可读取的冻结快照；历史对象需先完成盘点核实。</a-alert>
      </a-spin>
    </a-modal>
    <a-modal
      v-model:visible="lifecycleVisible"
      title="确认本侧状态与冻结影响"
      :ok-loading="busy"
      :ok-button-props="{ disabled: !lifecycleAcknowledged || !lifecyclePreview || now >= lifecyclePreview.expiresAt }"
      @before-ok="confirmLifecycle"
    >
      <a-alert type="warning">{{ lifecyclePreview?.message }}</a-alert>
      <p class="my-3"
        >源版本 {{ lifecyclePreview?.sourceVersion }} / 已同步 {{ lifecyclePreview?.appliedVersion }}；当前状态
        {{ lifecyclePreview?.objectState }}</p
      >
      <a-checkbox v-model="lifecycleAcknowledged">已知晓只修改本侧状态；恢复编辑后仍需另行预览确认成员同步</a-checkbox>
    </a-modal>
    <a-modal v-model:visible="historyVisible" title="退出成员历史（只读，不计入当前统计）" :footer="false" :width="800">
      <a-alert v-if="historyError" type="error"
        >历史加载失败，请重试。<a-button @click="showHistory">重试</a-button></a-alert
      >
      <a-list :data="historyItems" :loading="historyLoading"
        ><template #item="{ item }"
          ><a-list-item>
            <div>用例 {{ item.caseId }} · 第 {{ item.epoch }} 轮 · {{ new Date(item.exitedAt).toLocaleString() }}</div>
            <a-collapse
              ><a-collapse-item header="查看退出时快照">
                <pre class="whitespace-pre-wrap break-all">{{
                  JSON.stringify(
                    { case: item.case_snapshot, review: item.review_snapshot, opinions: item.history_snapshot },
                    null,
                    2
                  )
                }}</pre>
              </a-collapse-item></a-collapse
            >
          </a-list-item></template
        ></a-list
      >
      <a-pagination v-model:current="historyPage" :total="historyTotal" :page-size="20" @change="loadHistory" />
    </a-modal>
    <NoticePanel ref="noticeRef" :project-id="appStore.currentProjectId" />
    <NewReviewDialog ref="newReviewRecoveryRef" />
    <TakeoverPanel
      ref="takeoverRef"
      :project-id="appStore.currentProjectId"
      @change="operationsVisible && showOperations()"
    />
  </section>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';

  import NewReviewDialog from './newReviewDialog.vue';
  import NoticePanel from './noticePanel.vue';
  import TakeoverPanel from './takeoverPanel.vue';

  import type {
    RelationCandidate,
    RelationDifferenceItem,
    RelationHistoryItem,
    RelationOperation,
    RelationPreview,
    RelationSide,
    RelationSummary,
  } from '@/api/modules/plan-review';
  import {
    confirmPlanReview,
    confirmPlanReviewLifecycle,
    getPlanReview,
    getPlanReviewCandidates,
    getPlanReviewFrozen,
    getPlanReviewGrant,
    getPlanReviewHistory,
    getPlanReviewOperations,
    getPlanReviewPreviewItems,
    previewPlanReview,
    previewPlanReviewLifecycle,
    resumePlanReviewOperation,
    setPlanReviewGrant,
    terminatePlanReviewOperation,
    unlinkPlanReview,
  } from '@/api/modules/plan-review';
  import useAppStore from '@/store/modules/app';
  import { hasAnyPermission } from '@/utils/permission';

  import { TestPlanRouteEnum } from '@/enums/routeEnum';

  import type { TableData } from '@arco-design/web-vue';

  const props = defineProps<{ side: RelationSide; objectId: string; objectStatus?: string }>();
  const emit = defineEmits<{ (e: 'change', summary: RelationSummary): void; (e: 'refresh'): void }>();
  const router = useRouter();
  const appStore = useAppStore();
  const title = computed(() => (props.side === 'plan' ? '关联评审计划' : '关联测试计划'));
  const summary = ref<RelationSummary>();
  const loading = ref(false);
  const error = ref(false);
  const busy = ref(false);
  const takeoverRef = ref<InstanceType<typeof TakeoverPanel>>();
  const noticeRef = ref<InstanceType<typeof NoticePanel>>();
  const newReviewRecoveryRef = ref<InstanceType<typeof NewReviewDialog>>();
  const canPair = computed(
    () =>
      hasAnyPermission(['PROJECT_TEST_PLAN:READ']) &&
      hasAnyPermission(['PROJECT_TEST_PLAN:READ+UPDATE']) &&
      hasAnyPermission(['CASE_REVIEW:READ']) &&
      hasAnyPermission(['CASE_REVIEW:READ+UPDATE'])
  );
  const canManageGrant = computed(() => hasAnyPermission(['PLAN_REVIEW:GRANT_MANAGE']));
  const canUpdate = computed(() =>
    hasAnyPermission([props.side === 'plan' ? 'PROJECT_TEST_PLAN:READ+UPDATE' : 'CASE_REVIEW:READ+UPDATE'])
  );
  const canRestore = computed(() => canUpdate.value && hasAnyPermission(['PLAN_REVIEW:RESTORE']));
  const syncNames: Record<string, string> = {
    PENDING: '待同步',
    APPLYING: '同步中',
    SYNCED: '已同步',
    DELAYED: '同步延迟',
    WAITING_RETRY: '等待重试',
    FAILED_MANUAL: '失败待人工',
    RECONFIRM_REQUIRED: '待重新确认',
    ENDED: '已结束',
  };
  const blockerNames: Record<string, string> = {
    PLAN_LOCKED: '测试计划已锁定',
    REVIEW_LOCKED: '评审已锁定',
    UNKNOWN_STATE: '状态待核实',
    GRANT_REVOKED: '服务授权已停用',
    RECONFIRM_REQUIRED: '需要重新确认',
    RETRY_EXHAUSTED: '重试次数已耗尽',
  };
  const syncLabel = computed(() => syncNames[summary.value?.sync?.state || ''] || '状态待核实');
  const blockerLabel = (value: string) => blockerNames[value] || '存在待处理阻塞';
  let sequence = 0;
  async function load() {
    const current = ++sequence;
    if (!props.objectId) {
      summary.value = undefined;
      return;
    }
    loading.value = true;
    error.value = false;
    try {
      const value = await getPlanReview(props.side, props.objectId);
      if (current === sequence) {
        summary.value = value;
        emit('change', value);
      }
    } catch {
      if (current === sequence) {
        error.value = true;
        summary.value = undefined;
      }
    } finally {
      if (current === sequence) loading.value = false;
    }
  }
  const selectVisible = ref(false);
  const selectedId = ref('');
  const keyword = ref('');
  const candidates = ref<RelationCandidate[]>([]);
  const candidatesLoading = ref(false);
  const candidatesError = ref(false);
  const candidatePage = ref(1);
  const candidateTotal = ref(0);
  let candidateSequence = 0;
  let candidateSearchTimer: number | undefined;
  let previewRequestSequence = 0;
  let previewBusyOwner: number | undefined;
  function invalidatePreviewRequest() {
    previewRequestSequence++;
    if (previewBusyOwner !== undefined) {
      previewBusyOwner = undefined;
      busy.value = false;
    }
  }
  watch(selectedId, invalidatePreviewRequest, { flush: 'sync' });
  function selectCandidate(record: TableData) {
    const candidate = candidates.value.find((item) => item.id === record.id);
    if (!candidatesLoading.value && !candidatesError.value && candidate) selectedId.value = candidate.id;
  }
  async function search() {
    const current = ++candidateSequence;
    candidatesLoading.value = true;
    candidatesError.value = false;
    selectedId.value = '';
    try {
      const result = await getPlanReviewCandidates({
        side: props.side,
        id: props.objectId,
        keyword: keyword.value,
        page: candidatePage.value,
        size: 20,
      });
      if (current === candidateSequence) {
        candidates.value = result.items;
        candidateTotal.value = result.total;
      }
    } catch {
      if (current === candidateSequence) {
        candidatesError.value = true;
        candidates.value = [];
        candidateTotal.value = 0;
      }
    } finally {
      if (current === candidateSequence) candidatesLoading.value = false;
    }
  }
  function searchFirstPage() {
    window.clearTimeout(candidateSearchTimer);
    candidatePage.value = 1;
    return search();
  }
  watch(
    keyword,
    () => {
      invalidatePreviewRequest();
      window.clearTimeout(candidateSearchTimer);
      candidateSequence++;
      selectedId.value = '';
      candidates.value = [];
      candidateTotal.value = 0;
      if (!selectVisible.value) return;
      candidatesLoading.value = true;
      candidateSearchTimer = window.setTimeout(searchFirstPage, 250);
    },
    { flush: 'sync' }
  );
  watch(
    selectVisible,
    (visible) => {
      invalidatePreviewRequest();
      if (!visible) {
        window.clearTimeout(candidateSearchTimer);
        candidateSequence++;
        selectedId.value = '';
        candidatesLoading.value = false;
      }
    },
    { flush: 'sync' }
  );
  function choose() {
    selectVisible.value = true;
    searchFirstPage();
  }
  function changeCandidatePage(page: number) {
    candidatePage.value = page;
    search();
  }
  const preview = ref<RelationPreview>();
  const previewVisible = ref(false);
  const acknowledged = ref(false);
  const reconfirming = ref(false);
  const differenceTab = ref('added');
  const differencePage = ref(1);
  const operationUnknown = ref(false);
  let confirmKey = '';
  const differenceItems = ref<RelationDifferenceItem[]>([]);
  const differenceLoading = ref(false);
  const differenceError = ref(false);
  let differenceSequence = 0;
  watch(
    () => [preview.value?.previewId, differenceTab.value, differencePage.value],
    async () => {
      const current = ++differenceSequence;
      const context = sequence;
      differenceItems.value = [];
      differenceError.value = false;
      if (!preview.value) return;
      differenceLoading.value = true;
      try {
        const result = await getPlanReviewPreviewItems(
          preview.value.previewId,
          differenceTab.value,
          differencePage.value
        );
        if (current === differenceSequence && context === sequence) differenceItems.value = result.items;
      } catch {
        if (current === differenceSequence && context === sequence) differenceError.value = true;
      } finally {
        if (current === differenceSequence && context === sequence) differenceLoading.value = false;
      }
    }
  );
  const now = ref(Date.now());
  const timer = window.setInterval(() => {
    now.value = Date.now();
  }, 1000);
  onBeforeUnmount(() => {
    invalidatePreviewRequest();
    window.clearTimeout(candidateSearchTimer);
    window.clearInterval(timer);
    sequence++;
    candidateSequence++;
  });
  const expired = computed(() => !!preview.value && now.value >= preview.value.expiresAt);
  const differenceTabs = computed(() => {
    const diff = preview.value?.difference;
    return [
      { key: 'added', label: '新增', ids: [...(diff?.added || []), ...(diff?.reentered || [])] },
      { key: 'removed', label: '移出', ids: [...(diff?.removed || []), ...(diff?.reentered || [])] },
      { key: 'retained', label: '保留', ids: diff?.retained || [] },
    ];
  });
  watch(differenceTab, () => {
    differencePage.value = 1;
  });
  async function beginPreview(reconfirm: boolean) {
    if (
      busy.value ||
      (!reconfirm &&
        (candidatesLoading.value ||
          candidatesError.value ||
          !candidates.value.some((item) => item.id === selectedId.value)))
    )
      return false;
    const other = reconfirm ? summary.value?.target?.id : selectedId.value;
    if (!other) return false;
    const request = ++previewRequestSequence;
    previewBusyOwner = request;
    busy.value = true;
    const current = sequence;
    try {
      const value = await previewPlanReview({
        planId: props.side === 'plan' ? props.objectId : other,
        reviewId: props.side === 'review' ? props.objectId : other,
        purpose: reconfirm ? 'RECONFIRM' : 'PAIR_EXISTING',
      });
      if (current !== sequence || request !== previewRequestSequence) return false;
      preview.value = value;
      acknowledged.value = false;
      reconfirming.value = reconfirm;
      previewVisible.value = true;
      operationUnknown.value = false;
      confirmKey = crypto.randomUUID();
      differencePage.value = 1;
      return true;
    } catch {
      return false;
    } finally {
      if (previewBusyOwner === request) {
        previewBusyOwner = undefined;
        busy.value = false;
      }
    }
  }
  async function confirm() {
    if (!preview.value || !acknowledged.value || expired.value) return false;
    busy.value = true;
    const current = sequence;
    try {
      await confirmPlanReview({ previewId: preview.value.previewId, requestKey: confirmKey, acknowledged: true });
      if (current !== sequence) return false;
      Message.success('关联已确认，成员同步状态请以详情为准');
      await load();
      return true;
    } catch {
      if (current === sequence) operationUnknown.value = true;
      return false;
    } finally {
      if (current === sequence || !loading.value) busy.value = false;
    }
  }
  async function unlink() {
    if (!summary.value?.relationId || !summary.value.generation) return;
    busy.value = true;
    try {
      await unlinkPlanReview({
        relationId: summary.value.relationId,
        expectedGeneration: summary.value.generation,
        requestKey: crypto.randomUUID(),
      });
      await load();
    } finally {
      busy.value = false;
    }
  }
  function openTarget() {
    if (summary.value?.target)
      router.push({
        name:
          props.side === 'plan' ? TestPlanRouteEnum.TEST_PLAN_REVIEW_DETAIL : TestPlanRouteEnum.TEST_PLAN_INDEX_DETAIL,
        query: { id: summary.value.target.id },
      });
  }
  const grantVisible = ref(false);
  const grantEnabled = ref(false);
  const grantRevision = ref(0);
  const grantProject = ref('');
  async function showGrant() {
    const current = sequence;
    busy.value = true;
    try {
      const project = appStore.currentProjectId;
      const result = await getPlanReviewGrant(project);
      if (current !== sequence) return;
      grantRevision.value = result.revision;
      grantProject.value = project;
      grantEnabled.value = result.enabled;
      grantVisible.value = true;
    } finally {
      busy.value = false;
    }
  }
  async function saveGrant() {
    if (!grantProject.value || grantProject.value !== appStore.currentProjectId) return false;
    const current = sequence;
    busy.value = true;
    try {
      await setPlanReviewGrant({
        projectId: grantProject.value,
        expectedRevision: grantRevision.value,
        enabled: grantEnabled.value,
      });
      if (current !== sequence) return false;
      await load();
      return true;
    } catch {
      return false;
    } finally {
      busy.value = false;
    }
  }
  const operationsVisible = ref(false);
  const operationsError = ref(false);
  const operations = ref<RelationOperation[]>([]);
  const operationActionError = ref('');
  const operationRequests = new Map<string, { projectId: string; expectedRevision: number; requestKey: string }>();
  async function showOperations() {
    const current = sequence;
    operationsVisible.value = true;
    operations.value = [];
    operationActionError.value = '';
    operationsError.value = false;
    try {
      const result = await getPlanReviewOperations(appStore.currentProjectId);
      if (current === sequence) operations.value = result.items;
    } catch {
      if (current === sequence) {
        operationsError.value = true;
        operations.value = [];
      }
    }
  }
  async function controlOperation(item: RelationOperation, action: 'resume' | 'terminate') {
    if (busy.value) return;
    const current = sequence;
    const mapKey = `${appStore.currentProjectId}:${item.operationId}:${action}`;
    const request = operationRequests.get(mapKey) || {
      projectId: appStore.currentProjectId,
      expectedRevision: item.revision,
      requestKey: crypto.randomUUID(),
    };
    operationRequests.set(mapKey, request);
    busy.value = true;
    operationActionError.value = '';
    try {
      const result = await (action === 'resume' ? resumePlanReviewOperation : terminatePlanReviewOperation)(
        item.operationId,
        request
      );
      if (current !== sequence) return;
      operationRequests.delete(mapKey);
      if (action === 'resume' && result.newReviewPreview) {
        operationsVisible.value = false;
        newReviewRecoveryRef.value?.openRecovery(result.newReviewPreview, () => {
          emit('refresh');
          load();
        });
        return;
      }
      await showOperations();
      emit('refresh');
    } catch {
      if (current === sequence)
        operationActionError.value = '操作未确认完成，请查询原记录；重试会保留原请求，不重新创建对象。';
    } finally {
      if (current === sequence) busy.value = false;
    }
  }
  const lifecycleVisible = ref(false);
  const frozenVisible = ref(false);
  const frozenLoading = ref(false);
  const frozenError = ref(false);
  const frozenPage = ref(1);
  const frozen = ref<import('@/api/modules/plan-review').RelationFrozenFrame>();
  let frozenSequence = 0;
  async function loadFrozen(page: number) {
    const current = ++frozenSequence;
    const owner = sequence;
    frozenPage.value = page;
    frozenLoading.value = true;
    frozenError.value = false;
    try {
      const result = await getPlanReviewFrozen(props.side, props.objectId, page);
      if (current === frozenSequence && owner === sequence) frozen.value = result;
    } catch {
      if (current === frozenSequence && owner === sequence) {
        frozenError.value = true;
        frozen.value = undefined;
      }
    } finally {
      if (current === frozenSequence && owner === sequence) frozenLoading.value = false;
    }
  }
  function showFrozen() {
    frozenVisible.value = true;
    loadFrozen(1);
  }
  const lifecycleAcknowledged = ref(false);
  const lifecyclePreview = ref<import('@/api/modules/plan-review').RelationLifecyclePreview>();
  async function beginLifecycle(action: string) {
    const current = sequence;
    busy.value = true;
    try {
      const result = await previewPlanReviewLifecycle({ side: props.side, id: props.objectId, action });
      if (current !== sequence) return;
      lifecyclePreview.value = result;
      lifecycleAcknowledged.value = false;
      lifecycleVisible.value = true;
    } finally {
      busy.value = false;
    }
  }
  async function confirmLifecycle() {
    if (!lifecyclePreview.value || !lifecycleAcknowledged.value || Date.now() >= lifecyclePreview.value.expiresAt)
      return false;
    const current = sequence;
    busy.value = true;
    try {
      await confirmPlanReviewLifecycle({ previewId: lifecyclePreview.value.previewId, acknowledged: true });
      if (current !== sequence) return false;
      emit('refresh');
      await load();
      return true;
    } catch {
      return false;
    } finally {
      busy.value = false;
    }
  }
  const historyVisible = ref(false);
  const historyPage = ref(1);
  const historyTotal = ref(0);
  const historyItems = ref<RelationHistoryItem[]>([]);
  const historyError = ref(false);
  const historyLoading = ref(false);
  let historySequence = 0;
  async function loadHistory() {
    const current = ++historySequence;
    const context = sequence;
    historyLoading.value = true;
    historyError.value = false;
    try {
      const result = await getPlanReviewHistory(props.objectId, historyPage.value);
      if (current === historySequence && context === sequence) {
        historyItems.value = result.items;
        historyTotal.value = result.total;
      }
    } catch {
      if (current === historySequence && context === sequence) historyError.value = true;
    } finally {
      if (current === historySequence && context === sequence) historyLoading.value = false;
    }
  }
  function showHistory() {
    historyPage.value = 1;
    historyVisible.value = true;
    loadHistory();
  }
  watch(
    () => [props.side, props.objectId, appStore.currentProjectId],
    () => {
      invalidatePreviewRequest();
      selectVisible.value = false;
      previewVisible.value = false;
      preview.value = undefined;
      operationsVisible.value = false;
      grantVisible.value = false;
      grantProject.value = '';
      historyVisible.value = false;
      lifecycleVisible.value = false;
      frozenVisible.value = false;
      frozen.value = undefined;
      frozenSequence++;
      lifecyclePreview.value = undefined;
      candidateSequence++;
      candidates.value = [];
      candidateTotal.value = 0;
      selectedId.value = '';
      keyword.value = '';
      historySequence++;
      summary.value = undefined;
      load();
    },
    { immediate: true }
  );
</script>

<style scoped>
  .plan-review-link {
    margin-top: 12px;
    padding: 12px;
    border: 1px solid var(--color-border-2);
    border-radius: 4px;
    min-width: 0;
    max-width: 100%;
  }
</style>
