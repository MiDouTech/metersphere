<template>
  <section class="plan-review-link" aria-label="计划与评审关联">
    <a-spin :loading="loading" class="w-full">
      <div class="flex flex-wrap items-center gap-2">
        <strong>{{ objectName || title }}</strong>
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
          <span>{{ stateLabel(summary.target.status) }}</span>
          <a-tag>{{ syncLabel }}</a-tag>
          <a-button
            ref="recoverButton"
            size="small"
            :disabled="busy || !canRecover"
            title="需要当前同步恢复权限、有效服务授权且双方可编辑"
            @click="beginPreview(true)"
            >重新预览并确认同步</a-button
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
            ref="pairButton"
            size="small"
            :disabled="busy || !canPair || !summary.canPair"
            title="需要双方读取与更新权限；确认时还需项目服务授权"
            @click="choose"
            >{{ title }}</a-button
          >
        </template>
        <a-button
          ref="manageButton"
          size="small"
          :disabled="busy || loading || error || !summary || summary.restricted"
          @click="openManagement"
          >关联管理</a-button
        >
        <span v-if="actionReason" class="w-full text-xs" role="status">{{ actionReason }}</span>
        <a-alert v-if="actionError" type="error">{{ actionError }}</a-alert>
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
      v-model:visible="managementVisible"
      :title="`关联管理 · ${objectName || objectId}`"
      :footer="false"
      @close="returnFocus"
    >
      <p
        >当前{{ side === 'plan' ? '测试计划' : '评审计划' }}：#{{ objectNum || '—' }} {{ objectName }}（{{
          objectId
        }}）</p
      >
      <p v-if="summary?.legacyBlocked"
        >历史待交接，尚无已核实统一关系代次。请在同项目测试计划列表的“历史关联迁移”核查。</p
      >
      <p v-else-if="!summary?.generation">未关联，暂无当前关系记录。</p>
      <p v-else>当前关系代次：{{ summary.generation }}；{{ syncLabel }}。{{ summary.sync?.safeError }}</p>
      <div class="mt-3 flex flex-wrap gap-2">
        <a-button :disabled="busy || !summary?.generation" @click="showReadOnlyDifference">进度 / 只读差异</a-button>
        <a-button v-if="canManageGrant" size="small" :disabled="busy" @click="showGrant">同步服务授权</a-button>
        <a-button size="small" :disabled="busy || !summary?.generation" @click="showOperations">操作记录</a-button>
        <a-button size="small" :disabled="busy || !summary?.generation" @click="openTakeovers">接管审批</a-button>
        <a-button size="small" :disabled="busy || !summary?.generation" @click="openNotices">通知记录</a-button>
        <a-button v-if="side === 'review'" size="small" :disabled="busy || !summary?.generation" @click="showHistory"
          >退出成员历史</a-button
        >
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
    </a-modal>
    <a-modal
      v-if="readOnlyVisible"
      v-model:visible="readOnlyVisible"
      title="当前差异（只读，不开启人工恢复）"
      :footer="false"
      :width="760"
      @cancel="returnFocus"
    >
      <p>{{ objectName }}（{{ objectId }}）；{{ syncLabel }}；{{ summary?.sync?.safeError }}</p>
      <a-alert>只读查看，不重置当前预算或下次重试时间。移出保留历史，重新加入须重新评审。</a-alert>
      <a-radio-group v-model="readOnlyKind" class="my-3" @change="() => loadReadOnlyDifference(1)">
        <a-radio value="added">新增</a-radio><a-radio value="removed">移出</a-radio
        ><a-radio value="retained">保留</a-radio>
      </a-radio-group>
      <a-alert v-if="readOnlyError" type="error"
        >差异读取失败，请重新读取，不能确认同步。<a-button @click="loadReadOnlyDifference(readOnlyPage)"
          >重试</a-button
        ></a-alert
      >
      <a-list :data="readOnlyItems" :loading="readOnlyLoading"
        ><template #item="{ item }"
          ><a-list-item
            >#{{ item.num || '—' }} {{ item.name }} <span v-if="item.reentered">重新加入，须重新评审</span></a-list-item
          ></template
        ></a-list
      >
      <a-pagination :current="readOnlyPage" :total="readOnlyTotal" :page-size="20" @change="loadReadOnlyDifference" />
    </a-modal>
    <a-modal
      v-model:visible="selectVisible"
      :title="title"
      :width="760"
      :ok-loading="busy"
      :ok-button-props="{ disabled: !selectedId || candidatesLoading || candidatesError }"
      ok-text="下一步：预览差异"
      @close="closeCandidate"
      @before-ok="() => beginPreview(false)"
    >
      <p class="mb-2"
        >当前{{ side === 'plan' ? '测试计划' : '评审计划' }}：#{{ objectNum || '—' }} {{ objectName }}（{{
          objectId
        }}）；项目 {{ appStore.currentProjectId }}</p
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
      <a-alert v-if="previewError" type="error" class="my-2">{{ previewError }}</a-alert>
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
          <a-table-column title="状态" :width="110"
            ><template #cell="{ record }">{{ stateLabel(record.status) }}</template></a-table-column
          >
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
      :ok-button-props="{
        disabled:
          !acknowledged ||
          !preview ||
          expired ||
          busy ||
          (reconfirming && !canRecover) ||
          differenceLoading ||
          differenceError,
      }"
      :ok-text="reconfirming ? '确认继续同步' : '确认关联并同步'"
      @close="closePreview"
      @before-ok="confirm"
    >
      <p class="mb-2"
        >测试计划：{{ side === 'plan' ? objectName : previewTarget?.name }}（{{
          side === 'plan' ? objectId : previewTarget?.id
        }}） → 评审：{{ side === 'review' ? objectName : previewTarget?.name }}（{{
          side === 'review' ? objectId : previewTarget?.id
        }}）；项目 {{ appStore.currentProjectId }}</p
      >
      <a-alert type="warning">{{ preview?.message }}</a-alert>
      <p v-if="preview" class="my-2 text-xs"
        >源版本 {{ preview.sourceVersion ?? '待核实' }}；关系代次 {{ preview.generation }}；预览有效至
        {{ new Date(preview.expiresAt).toLocaleString() }}</p
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
      <a-button v-if="!reconfirming" size="small" @click="previousStep">上一步</a-button>
      <a-checkbox v-model="acknowledged" class="mt-3">已知晓评审范围和历史影响</a-checkbox>
      <a-alert v-if="expired" type="warning" class="mt-2">预览已过期，请重新预览；已有关系和成功步骤保留。</a-alert>
      <a-alert v-if="operationUnknown" type="warning" class="mt-2"
        >结果待确认，请勿重新创建。<a-button size="small" @click="resolveUnknownConfirmation"
          >查询原操作</a-button
        ></a-alert
      >
    </a-modal>
    <a-modal
      v-model:visible="grantVisible"
      title="项目成员同步服务授权"
      :ok-loading="busy"
      @close="returnFocus"
      @before-ok="saveGrant"
    >
      <a-alert type="warning"
        >这是项目级授权。撤销将停止目标成员写入；恢复授权后仍需有权人员重新预览确认，不会自动追平。</a-alert
      >
      <a-switch v-model="grantEnabled" class="mt-4" checked-text="启用" unchecked-text="停用" />
    </a-modal>
    <a-modal
      v-model:visible="operationsVisible"
      title="当前用户操作记录"
      :footer="false"
      :width="760"
      @close="returnFocus"
    >
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
    <a-modal
      v-model:visible="frozenVisible"
      title="完成 / 归档时快照（只读）"
      :footer="false"
      :width="800"
      @close="returnFocus"
    >
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
      @close="returnFocus"
      @before-ok="confirmLifecycle"
    >
      <a-alert type="warning">{{ lifecyclePreview?.message }}</a-alert>
      <p class="my-3"
        >源版本 {{ lifecyclePreview?.sourceVersion }} / 已同步 {{ lifecyclePreview?.appliedVersion }}；当前状态
        {{ lifecyclePreview?.objectState }}</p
      >
      <a-checkbox v-model="lifecycleAcknowledged">已知晓只修改本侧状态；恢复编辑后仍需另行预览确认成员同步</a-checkbox>
    </a-modal>
    <a-modal
      v-model:visible="historyVisible"
      title="退出成员历史（只读，不计入当前统计）"
      :footer="false"
      :width="800"
      @close="returnFocus"
    >
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
    <HandoffCard :side="side" :object-id="objectId" />
    <NoticePanel
      v-if="summary && !summary.restricted"
      :key="childContext"
      ref="noticeRef"
      :before-action="validateContext"
      :project-id="appStore.currentProjectId"
    />
    <NewReviewDialog
      v-if="summary && !summary.restricted"
      :key="childContext"
      ref="newReviewRecoveryRef"
      :before-action="validateContext"
    />
    <TakeoverPanel
      v-if="summary && !summary.restricted"
      :key="childContext"
      ref="takeoverRef"
      :before-action="validateContext"
      :relation-id="summary?.relationId"
      :generation="summary?.generation"
      :project-id="appStore.currentProjectId"
      @change="operationsVisible && showOperations()"
    />
  </section>
</template>

<script setup lang="ts">
  import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';

  import NewReviewDialog from './newReviewDialog.vue';
  import NoticePanel from './noticePanel.vue';
  import TakeoverPanel from './takeoverPanel.vue';
  import HandoffCard from '@/components/business/hifox-handoff/card.vue';

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
    findPlanReviewConfirmation,
    getPlanReview,
    getPlanReviewCandidates,
    getPlanReviewFrozen,
    getPlanReviewGrant,
    getPlanReviewHistory,
    getPlanReviewOperations,
    getPlanReviewPreviewItems,
    getPlanReviewReadOnlyDifference,
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

  const props = defineProps<{
    side: RelationSide;
    objectId: string;
    objectStatus?: string;
    objectName?: string;
    objectNum?: number;
  }>();
  const emit = defineEmits<{
    (e: 'change', summary: RelationSummary): void;
    (e: 'refresh', reason?: 'paired' | 'unlinked'): void;
  }>();
  const router = useRouter();
  const appStore = useAppStore();
  const title = computed(() => (props.side === 'plan' ? '关联评审计划' : '关联测试计划'));
  const summary = ref<RelationSummary>();
  const loading = ref(false);
  const error = ref(false);
  const actionError = ref('');
  const childEpoch = ref(0);
  const childContext = computed(
    () =>
      `${props.side}:${props.objectId}:${appStore.currentProjectId}:${summary.value?.relationId || ''}:${
        summary.value?.generation || ''
      }:${childEpoch.value}`
  );
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
  const selectVisible = ref(false);
  const previewVisible = ref(false);
  const operationsVisible = ref(false);
  const grantVisible = ref(false);
  const historyVisible = ref(false);
  const lifecycleVisible = ref(false);
  const frozenVisible = ref(false);
  const preview = ref<RelationPreview>();
  const previewTarget = ref<RelationCandidate>();
  const differenceItems = ref<RelationDifferenceItem[]>([]);
  const operations = ref<RelationOperation[]>([]);
  const historyItems = ref<RelationHistoryItem[]>([]);
  const lifecyclePreview = ref<import('@/api/modules/plan-review').RelationLifecyclePreview>();
  const frozen = ref<import('@/api/modules/plan-review').RelationFrozenFrame>();
  const acknowledged = ref(false);
  let sequence = 0;
  const managementVisible = ref(false);
  const readOnlyVisible = ref(false);
  const readOnlyItems = ref<RelationDifferenceItem[]>([]);
  const readOnlyKind = ref('added');
  const readOnlyPage = ref(1);
  const readOnlyTotal = ref(0);
  const readOnlyLoading = ref(false);
  const readOnlyError = ref(false);
  let readOnlySequence = 0;
  const pairButton = ref();
  const recoverButton = ref();
  const manageButton = ref();
  let entry: HTMLElement | undefined;
  let successfulExit = false;
  let scrollPosition = { left: 0, top: 0 };
  let alive = true;
  const canRecover = computed(
    () =>
      !!summary.value?.canReconfirm &&
      ['FAILED_MANUAL', 'RECONFIRM_REQUIRED'].includes(summary.value?.sync?.state || '')
  );
  const actionReason = computed(() => {
    if (loading.value) return '正在读取关联信息';
    if (error.value) return '关联读取失败，请重试，不会按未关联处理';
    if (summary.value?.restricted) return '无权查看关联对象，不能进行关联操作';
    if (summary.value?.legacyBlocked) return '历史关联待交接，不可重复新增；请从关联管理查看指引';
    if (summary.value?.target)
      return '已有有效关系，不能重复关联。人工恢复仅适用于耗尽或待重新确认，且需双方可编辑、双读、计划更新、评审成员关联和有效服务授权；解除独立校验双方更新权限，不要求服务授权。';
    if (!canPair.value || !summary.value?.canPair)
      return '建立关联需要真实可编辑未占用对象、双读双更新及有效服务授权；请核实状态和权限';
    return '';
  });
  async function returnFocus() {
    await nextTick();
    if (!alive) return;
    if (
      successfulExit ||
      managementVisible.value ||
      readOnlyVisible.value ||
      selectVisible.value ||
      previewVisible.value ||
      operationsVisible.value ||
      grantVisible.value ||
      historyVisible.value ||
      lifecycleVisible.value ||
      frozenVisible.value
    )
      return;
    const node = entry;
    if (node?.isConnected && !node.matches(':disabled') && node.getClientRects().length)
      node.focus({ preventScroll: true });
    else document.querySelector<HTMLElement>('[data-relation-list-title]')?.focus({ preventScroll: true });
    window.scrollTo(scrollPosition);
  }
  function rememberEntry() {
    successfulExit = false;
    entry = document.activeElement as HTMLElement;
    scrollPosition = { left: window.scrollX, top: window.scrollY };
  }
  async function validateContext(requireRelation = true) {
    if (!alive) return false;
    const owner = sequence;
    const old = summary.value;
    try {
      const value = await getPlanReview(props.side, props.objectId);
      if (!alive || owner !== sequence) return false;
      if (!old || value.restricted || old.generation !== value.generation || old.relationId !== value.relationId) {
        sequence++;
        summary.value = undefined;
        preview.value = undefined;
        previewTarget.value = undefined;
        differenceItems.value = [];
        operations.value = [];
        historyItems.value = [];
        lifecyclePreview.value = undefined;
        frozen.value = undefined;
        managementVisible.value = false;
        readOnlyVisible.value = false;
        previewVisible.value = false;
        Message.warning('管理上下文已失效，请返回列表重新选择');
        emit('refresh');
        return false;
      }
      summary.value = value;
      return !requireRelation || !!value.generation;
    } catch {
      if (owner === sequence) {
        summary.value = undefined;
        error.value = true;
        previewVisible.value = false;
        managementVisible.value = false;
      }
      return false;
    }
  }
  async function loadReadOnlyDifference(page: number) {
    const request = ++readOnlySequence;
    readOnlyItems.value = [];
    readOnlyError.value = false;
    readOnlyLoading.value = true;
    try {
      if (!(await validateContext()) || request !== readOnlySequence) return;
      readOnlyPage.value = page;
      const owner = sequence;
      const value = await getPlanReviewReadOnlyDifference(
        props.side,
        props.objectId,
        summary.value!.generation!,
        readOnlyKind.value,
        page
      );
      if (!alive || owner !== sequence || request !== readOnlySequence) return;
      readOnlyItems.value = value.items;
      readOnlyTotal.value = value.total;
    } catch {
      if (request === readOnlySequence) readOnlyError.value = true;
    } finally {
      if (request === readOnlySequence) readOnlyLoading.value = false;
    }
  }
  async function showReadOnlyDifference() {
    managementVisible.value = false;
    readOnlyVisible.value = true;
    await loadReadOnlyDifference(1);
  }

  async function openManagement() {
    rememberEntry();
    if (await validateContext(false)) managementVisible.value = true;
  }
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
  const stateLabel = (value: string) =>
    (({ PREPARED: '未开始', UNDERWAY: '进行中', COMPLETED: '已完成', ARCHIVED: '已归档' } as Record<string, string>)[
      value
    ] || '状态待核实');
  const blockerLabel = (value: string) => blockerNames[value] || '存在待处理阻塞';
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
  const selectedId = ref('');
  const keyword = ref('');
  const candidates = ref<RelationCandidate[]>([]);
  const candidatesLoading = ref(false);
  const candidatesError = ref(false);
  const previewError = ref('');
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
    rememberEntry();
    selectVisible.value = true;
    searchFirstPage();
  }
  function changeCandidatePage(page: number) {
    candidatePage.value = page;
    search();
  }
  let movingToPreview = false;
  let movingBack = false;
  function closeCandidate() {
    if (!movingToPreview) {
      previewVisible.value = false;
      preview.value = undefined;
      returnFocus();
    }
    movingToPreview = false;
  }
  function closePreview() {
    acknowledged.value = false;
    if (!movingBack) {
      selectVisible.value = false;
      returnFocus();
    }
    movingBack = false;
  }
  function previousStep() {
    movingBack = true;
    previewVisible.value = false;
    selectVisible.value = true;
    acknowledged.value = false;
  }
  const reconfirming = ref(false);
  const differenceTab = ref('added');
  const differencePage = ref(1);
  const operationUnknown = ref(false);
  let confirmKey = '';
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
    alive = false;
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
    previewError.value = '';
    if (
      busy.value ||
      (!reconfirm &&
        (candidatesLoading.value ||
          candidatesError.value ||
          !candidates.value.some((item) => item.id === selectedId.value)))
    )
      return false;
    if (reconfirm) rememberEntry();
    const other = reconfirm ? summary.value?.target?.id : selectedId.value;
    if (!other) return false;
    const request = ++previewRequestSequence;
    previewBusyOwner = request;
    busy.value = true;
    const current = sequence;
    try {
      if (
        !(await validateContext(reconfirm)) ||
        current !== sequence ||
        request !== previewRequestSequence ||
        (reconfirm ? !canRecover.value : !summary.value?.canPair)
      )
        return false;
      const value = await previewPlanReview({
        planId: props.side === 'plan' ? props.objectId : other,
        reviewId: props.side === 'review' ? props.objectId : other,
        purpose: reconfirm ? 'RECONFIRM' : 'PAIR_EXISTING',
      });
      if (current !== sequence || request !== previewRequestSequence) return false;
      previewTarget.value = reconfirm ? summary.value?.target : candidates.value.find((item) => item.id === other);
      movingToPreview = !reconfirm;
      preview.value = value;
      acknowledged.value = false;
      reconfirming.value = reconfirm;
      previewVisible.value = true;
      operationUnknown.value = false;
      confirmKey = crypto.randomUUID();
      differencePage.value = 1;
      return true;
    } catch {
      if (current === sequence && request === previewRequestSequence)
        previewError.value =
          '预览未成功。请核实权限、服务授权及对象状态后重试；具体缺项待管理员核实。可取消退出，未提交关联确认。';
      return false;
    } finally {
      if (previewBusyOwner === request) {
        previewBusyOwner = undefined;
        busy.value = false;
      }
    }
  }
  async function resolveUnknownConfirmation() {
    if (!alive || !confirmKey || !previewTarget.value) return false;
    const owner = sequence;
    try {
      const result = await findPlanReviewConfirmation(appStore.currentProjectId, confirmKey);
      if (!alive || owner !== sequence) return false;
      const operation = result.items.find(
        (item) =>
          item.generation &&
          item.relationId &&
          item.planId === (props.side === 'plan' ? props.objectId : previewTarget.value?.id) &&
          item.reviewId === (props.side === 'review' ? props.objectId : previewTarget.value?.id)
      );
      if (!operation) return false;
      await load();
      successfulExit = true;
      previewVisible.value = false;
      selectVisible.value = false;
      emit('refresh', 'paired');
      return true;
    } catch {
      return false;
    }
  }
  async function confirm() {
    if (
      busy.value ||
      !preview.value ||
      !acknowledged.value ||
      expired.value ||
      differenceLoading.value ||
      differenceError.value
    )
      return false;
    busy.value = true;
    const current = sequence;
    try {
      if (
        !(await validateContext(reconfirming.value)) ||
        current !== sequence ||
        !preview.value ||
        (reconfirming.value && !canRecover.value)
      ) {
        acknowledged.value = false;
        return false;
      }
      await confirmPlanReview({ previewId: preview.value.previewId, requestKey: confirmKey, acknowledged: true });
      if (current !== sequence) return false;
      Message.success('已关联 · 待同步；请以列表实际同步状态为准');
      await load();
      successfulExit = true;
      emit('refresh', 'paired');
      return true;
    } catch {
      if (current === sequence) {
        operationUnknown.value = true;
        acknowledged.value = false;
        if (await resolveUnknownConfirmation()) return true;
      }
      return false;
    } finally {
      if (current === sequence || !loading.value) busy.value = false;
    }
  }
  async function unlink() {
    if (
      busy.value ||
      !(await validateContext()) ||
      !summary.value?.canUnlink ||
      !summary.value?.relationId ||
      !summary.value.generation
    )
      return;
    busy.value = true;
    try {
      await unlinkPlanReview({
        relationId: summary.value.relationId,
        expectedGeneration: summary.value.generation,
        requestKey: crypto.randomUUID(),
      });
      await load();
      successfulExit = true;
      emit('refresh', 'unlinked');
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
  async function openNotices() {
    managementVisible.value = false;
    if (await validateContext()) noticeRef.value?.open(props.objectId, props.side);
  }
  async function openTakeovers() {
    managementVisible.value = false;
    if (await validateContext()) takeoverRef.value?.open();
  }
  const grantEnabled = ref(false);
  const grantRevision = ref(0);
  const grantProject = ref('');
  async function showGrant() {
    managementVisible.value = false;
    if (!(await validateContext(false))) return;
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
    } catch {
      actionError.value = '授权信息读取失败，请核实当前项目管理权限后重试；没有修改授权。';
    } finally {
      busy.value = false;
    }
  }
  async function saveGrant() {
    if (!(await validateContext(false))) return false;
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
  const operationsError = ref(false);
  const operationActionError = ref('');
  const operationRequests = new Map<string, { projectId: string; expectedRevision: number; requestKey: string }>();
  async function showOperations() {
    managementVisible.value = false;
    if (!(await validateContext(true))) return;
    const current = sequence;
    operationsVisible.value = true;
    operations.value = [];
    operationActionError.value = '';
    operationsError.value = false;
    try {
      const result = await getPlanReviewOperations(appStore.currentProjectId, false, {
        relationId: summary.value?.relationId,
        generation: summary.value?.generation,
      });
      if (current === sequence)
        operations.value = result.items.filter(
          (item) => item.generation === summary.value?.generation && item.relationId === summary.value?.relationId
        );
    } catch {
      if (current === sequence) {
        operationsError.value = true;
        operations.value = [];
      }
    }
  }
  async function controlOperation(item: RelationOperation, action: 'resume' | 'terminate') {
    if (!(await validateContext(true))) return;
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
  const frozenLoading = ref(false);
  const frozenError = ref(false);
  const frozenPage = ref(1);
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
  async function showFrozen() {
    managementVisible.value = false;
    if (!(await validateContext(true))) return;
    frozenVisible.value = true;
    loadFrozen(1);
  }
  const lifecycleAcknowledged = ref(false);
  async function beginLifecycle(action: string) {
    managementVisible.value = false;
    if (!(await validateContext(false))) return;
    const current = sequence;
    busy.value = true;
    try {
      const result = await previewPlanReviewLifecycle({ side: props.side, id: props.objectId, action });
      if (current !== sequence) return;
      lifecyclePreview.value = result;
      lifecycleAcknowledged.value = false;
      lifecycleVisible.value = true;
    } catch {
      actionError.value = '状态预览未成功，请核实当前权限和对象状态后重试；没有确认状态变更。';
    } finally {
      busy.value = false;
    }
  }
  async function confirmLifecycle() {
    if (!(await validateContext(false))) return false;
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
  const historyPage = ref(1);
  const historyTotal = ref(0);
  const historyError = ref(false);
  const historyLoading = ref(false);
  let historySequence = 0;
  async function loadHistory() {
    if (!(await validateContext(true))) return;
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
    managementVisible.value = false;
    historyPage.value = 1;
    historyVisible.value = true;
    loadHistory();
  }
  // Losing read access must also discard hidden dialog data, not merely hide the summary.
  watch(
    summary,
    (value, previous) => {
      const changed =
        previous && value && (previous.relationId !== value.relationId || previous.generation !== value.generation);
      if (value && !value.restricted && !changed) return;
      noticeRef.value?.invalidate?.();
      takeoverRef.value?.invalidate?.();
      newReviewRecoveryRef.value?.invalidate?.();
      childEpoch.value++;
      candidateSequence++;
      differenceSequence++;
      historySequence++;
      frozenSequence++;
      readOnlySequence++;
      candidates.value = [];
      selectedId.value = '';
      preview.value = undefined;
      previewTarget.value = undefined;
      differenceItems.value = [];
      operations.value = [];
      historyItems.value = [];
      lifecyclePreview.value = undefined;
      frozen.value = undefined;
      readOnlyItems.value = [];
      acknowledged.value = false;
      selectVisible.value = false;
      previewVisible.value = false;
      managementVisible.value = false;
      readOnlyVisible.value = false;
      operationsVisible.value = false;
      grantVisible.value = false;
      historyVisible.value = false;
      lifecycleVisible.value = false;
      frozenVisible.value = false;
    },
    { flush: 'sync' }
  );
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
