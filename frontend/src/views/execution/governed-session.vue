<template>
  <div class="p-4">
    <a-page-header title="受控执行与独立复核" subtitle="提交、复核和正式同步分别记录；待复核不表示通过" />
    <a-alert class="mb-4">发生未知外部结果时，先查询原业务对象并补充证据。页面不会重放外部业务动作。</a-alert>
    <a-space class="mb-4"
      ><a-button :loading="loading" @click="loadList">刷新实例</a-button
      ><a-button @click="showStart = !showStart">发起执行</a-button
      ><a-button @click="showAuthorization = !showAuthorization">来源授权</a-button></a-space
    >
    <a-card v-if="showAuthorization" title="来源授权" class="mb-4">
      <a-form :model="authorization" layout="vertical">
        <a-form-item label="项目"><a-input v-model="projectId" /></a-form-item
        ><a-form-item label="来源类型"
          ><a-select v-model="authorization.type"
            ><a-option>PERSONAL</a-option><a-option>TASK</a-option></a-select
          ></a-form-item
        >
        <a-form-item label="用例或任务标识"><a-input v-model="authorization.sourceId" /></a-form-item
        ><a-form-item label="环境"><a-input v-model="authorization.environmentId" /></a-form-item>
        <a-form-item label="允许的动作"><a-input-tag v-model="authorization.allowedActions" /></a-form-item
        ><a-form-item label="到期时间"><input v-model="authorization.expires" type="datetime-local" /></a-form-item>
        <a-button @click="readAuthorization">读取当前授权版本</a-button
        ><a-form-item label="当前授权版本"
          ><a-input-number v-model="authorization.expectedVersion" :min="0" /></a-form-item
        ><a-form-item label="授权依据"><a-textarea v-model="authorization.reason" /></a-form-item>
        <a-checkbox v-model="authorization.mutationsOwnDataOnly">仅允许修改本次准备和明确授权的数据</a-checkbox
        ><a-button :loading="saving" @click="authorize">保存来源授权（首次移交会暂停旧通道）</a-button>
      </a-form>
    </a-card>
    <a-card v-if="showStart" title="发起新的受控执行" class="mb-4">
      <a-form :model="start" layout="vertical">
        <a-form-item label="项目"><a-input v-model="projectId" /></a-form-item
        ><a-form-item label="来源类型"
          ><a-select v-model="start.sourceType"><a-option>PERSONAL</a-option></a-select></a-form-item
        ><a-alert>真实任务执行由已领取新任务租约、尚未开展旧业务动作的 MCP 客户端发起；页面可进行治理与复核。</a-alert>
        <a-form-item label="用例或真实执行任务标识"><a-input v-model="start.sourceId" /></a-form-item
        ><a-form-item v-if="start.sourceType === 'PERSONAL'" label="已发布结构化合约版本"
          ><a-input-number v-model="start.contractVersion" :min="1"
        /></a-form-item>
        <a-form-item label="环境配置"><a-input v-model="start.environmentProfileId" /></a-form-item
        ><a-form-item label="治理责任人"><a-input v-model="start.governanceUserId" /></a-form-item>
        <a-button type="primary" :loading="saving" @click="create">校验授权并创建</a-button>
      </a-form>
    </a-card>
    <a-table :data="sessions" :pagination="false" @row-click="openSession"
      ><template #columns
        ><a-table-column title="实例会话" data-index="id" /><a-table-column
          title="来源类型"
          data-index="source_type" /><a-table-column title="来源" data-index="source_id" /><a-table-column
          title="状态"
          data-index="state" /></template
    ></a-table>
    <template v-if="session">
      <a-divider>执行上下文</a-divider
      ><a-space
        ><a-tag>{{ session.state }}</a-tag
        ><a-button @click="refresh">重新读取状态</a-button
        ><a-button
          v-if="can('GOVERN')"
          @click="execute('finish', { expectedSessionVersion: session.sessionVersion }, false)"
          >检查并结束会话</a-button
        ></a-space
      >
      <a-descriptions :column="2"
        ><a-descriptions-item label="治理责任人">{{ session.context.responsibleUserId }}</a-descriptions-item
        ><a-descriptions-item label="执行变量版本">{{
          session.executionContextVersion
        }}</a-descriptions-item></a-descriptions
      ><a-collapse
        ><a-collapse-item header="执行预算与来源依据"
          ><ContentReader :value="session.context.budget" /><ContentReader
            :value="session.context.budgetConsumption" /><ContentReader
            :value="session.context.authorization" /></a-collapse-item
      ></a-collapse>
      <a-tabs v-model:active-key="instanceId"
        ><a-tab-pane v-for="item in session.instances" :key="item.caseExecutionId" :title="item.caseId"
      /></a-tabs>
      <template v-if="instance">
        <a-descriptions :column="4" bordered
          ><a-descriptions-item label="执行阶段">{{ instance.phase }}</a-descriptions-item
          ><a-descriptions-item label="核实状态">{{ instance.verification }}</a-descriptions-item
          ><a-descriptions-item label="提交结果">{{ instance.result || '未提交' }}</a-descriptions-item
          ><a-descriptions-item label="同步状态">{{
            instance.syncStatus || '未同步'
          }}</a-descriptions-item></a-descriptions
        >
        <a-alert
          v-if="instance.knowledgeUnavailable || instance.upstreamDisputed || instance.disputed"
          type="warning"
          class="my-4"
          >知识已不可用或结果存在争议，继续执行前须完成治理与重新校验。</a-alert
        >
        <a-tabs>
          <a-tab-pane v-if="can('EXECUTE') || can('EVIDENCE_WRITE')" key="work" title="执行与提交"
            ><GovernedWorkForm
              :instance="instance"
              :loading="saving"
              :can-execute="!!can('EXECUTE')"
              :can-supplement="!!can('EVIDENCE_WRITE')"
              @execute="execute"
          /></a-tab-pane>
          <a-tab-pane key="contract" title="冻结合约与数据"
            ><ContentReader :value="instance.contract" /><ContentReader :value="instance.bindings" /><ContentReader
              :value="instance.dependencies"
          /></a-tab-pane>
          <a-tab-pane key="preflight" title="恢复与前置校验">
            <a-form :model="preflight" layout="vertical"
              ><a-form-item label="实际登录账号"><a-input v-model="preflight.accountId" /></a-form-item
              ><a-form-item label="实际环境"><a-input v-model="preflight.environmentId" /></a-form-item
              ><a-form-item label="恢复凭据（仅内存保存）"
                ><a-input-password v-model="preflight.resumeToken"
              /></a-form-item>
              <a-form-item
                v-for="dependency in instance.contract.dependencies"
                :key="String(dependency.alias)"
                :label="`依赖 ${dependency.alias} 的已接受执行实例`"
                ><a-input
                  v-model="dependencies[String(dependency.alias)].instanceId"
                  placeholder="明确的生产者执行实例" /><a-input-number
                  v-model="dependencies[String(dependency.alias)].resultVersion"
                  :min="1"
                  placeholder="已接受结果版本"
              /></a-form-item>
              <div v-for="(binding, key) in bindingForms" :key="key" class="my-3"
                ><strong>重新核实数据 {{ key }}</strong
                ><a-input v-model="binding.objectId" placeholder="原业务对象标识，不能重新创建" /><ProtocolValueFields
                  v-model="binding.conditions"
                  :declarations="binding.declarations" /><a-input
                  v-model="binding.evidenceIds"
                  placeholder="当前核实证据，逗号分隔" /></div
              ><a-space
                ><a-button v-if="can('EXECUTE')" @click="execute('lease.takeover', {})">取得新租约并重新校验</a-button
                ><a-button
                  v-if="can('EXECUTE')"
                  @click="
                    execute('case.prepare', {
                      preflight: {
                        ...preflight,
                        evidence: evidenceRefs(),
                        bindings: Object.fromEntries(
                          Object.entries(bindingForms).map(([key, value]) => [
                            key,
                            {
                              objectId: value.objectId,
                              conditions: value.conditions,
                              evidence: evidenceRefs(value.evidenceIds),
                            },
                          ])
                        ),
                      },
                      dependencyInstances: dependencies,
                    })
                  "
                  >校验前置条件</a-button
                ><a-button
                  v-if="human && can('RESULT_REVIEW') && instance.channel === 'CLOSED'"
                  @click="
                    execute('result.revalidate', {
                      preflight: {
                        ...preflight,
                        evidence: evidenceRefs(),
                        bindings: Object.fromEntries(
                          Object.entries(bindingForms).map(([key, value]) => [
                            key,
                            {
                              objectId: value.objectId,
                              conditions: value.conditions,
                              evidence: evidenceRefs(value.evidenceIds),
                            },
                          ])
                        ),
                      },
                      dependencyInstances: dependencies,
                    })
                  "
                  >仅重新核实待复核上下文</a-button
                ></a-space
              >
            </a-form>
          </a-tab-pane>
          <a-tab-pane key="shared" title="会话变量"
            ><a-alert>只写入合约声明的共享变量；保存时校验独立的会话变量版本，不覆盖其他人的新版本。</a-alert
            ><ProtocolValueFields v-model="executionVariables" :declarations="executionDeclarations" /><a-button
              v-if="can('EXECUTE')"
              @click="
                execute(
                  'context.save',
                  { executionContextVersion: session.executionContextVersion, variables: executionVariables },
                  false
                )
              "
              >保存共享变量</a-button
            ><ContentReader :value="session.context.variables" /></a-tab-pane
          ><a-tab-pane key="actions" title="外部动作与未知结果">
            <ContentReader :value="instance.actions" /><a-checkbox v-model="cleanupMode">查询独立清理动作</a-checkbox
            ><ContentReader :value="instance.cleanup" />
            <a-form :model="action" layout="vertical"
              ><a-form-item label="待核实动作"
                ><a-select v-model="action.actionId"
                  ><a-option v-for="item in visibleActions" :key="String(item.actionId)" :value="String(item.actionId)"
                    >{{ item.actionType }} · {{ item.state }} · {{ item.target }}</a-option
                  ></a-select
                ></a-form-item
              ><a-form-item label="核实结论"
                ><a-select v-model="action.resolution"
                  ><a-option>UNKNOWN</a-option><a-option>CONFIRMED_EFFECT</a-option
                  ><a-option>CONFIRMED_NO_EFFECT</a-option></a-select
                ></a-form-item
              ><a-form-item label="权威查询事实"><a-textarea v-model="action.queryEvidence" /></a-form-item
              ><a-form-item v-if="action.resolution === 'CONFIRMED_EFFECT'" label="原业务对象标识"
                ><a-input v-model="action.objectId" /></a-form-item
              ><template v-if="action.resolution === 'CONFIRMED_NO_EFFECT'"
                ><a-form-item label="明确终态证明"
                  ><a-select v-model="terminalType"
                    ><a-option>CANCELLED_TERMINAL</a-option><a-option>FAILED_TERMINAL</a-option
                    ><a-option>UNIQUE_REQUEST_TERMINAL_NO_EFFECT</a-option></a-select
                  ></a-form-item
                ><a-form-item label="终态证据定位"><a-input v-model="terminalReference" /></a-form-item></template
              ><a-button v-if="can('VERIFY_EXTERNAL')" @click="resolveAction">记录核实，保留历史</a-button></a-form
            >
          </a-tab-pane>
          <a-tab-pane key="evidence" title="证据与补充">
            <a-alert>仅上传已脱敏 PNG/JPEG。原始账号凭据、Token 和私密地址不得进入图片或文字。</a-alert
            ><a-space class="my-4"
              ><input type="file" accept="image/png,image/jpeg" @change="selectFile" /><a-checkbox v-model="redacted"
                >已脱敏</a-checkbox
              ><a-select v-model="uploadStep" placeholder="关联步骤"
                ><a-option v-for="step in instance.contract.steps" :key="step.id" :value="step.id">{{
                  step.action
                }}</a-option></a-select
              ><a-select v-model="uploadPurpose"
                ><a-option>BEFORE_STEP</a-option><a-option>AFTER_STEP</a-option><a-option>FAILURE</a-option
                ><a-option>HEALING_BEFORE</a-option><a-option>HEALING_AFTER</a-option></a-select
              ><a-button v-if="can('EVIDENCE_WRITE')" :loading="saving" @click="upload"
                >上传并绑定当前实例</a-button
              ></a-space
            >
            <a-space
              ><a-input v-model="previewEvidenceId" placeholder="本实例图片证据标识" /><a-button
                v-if="can('EVIDENCE_READ')"
                @click="previewEvidence"
                >按当前权限读取图片</a-button
              ></a-space
            ><img
              v-if="evidencePreview"
              :src="evidencePreview"
              class="my-3 max-w-full"
              alt="当前实例脱敏证据"
            /><a-form-item label="本次使用的证据标识（逗号分隔）"><a-input v-model="evidenceIds" /></a-form-item>
            <div v-for="assertion in assertionForms" :key="assertion.assertionId" class="my-4 border p-3"
              ><strong>{{ assertion.assertionId }} · {{ assertion.expected }}</strong
              ><a-textarea v-model="assertion.observation" placeholder="可验证的实际观察" /><a-input
                v-model="assertion.evidenceIds"
                placeholder="该断言证据标识，逗号分隔"
            /></div>
            <a-button v-if="can('EVIDENCE_WRITE') && instance.phase !== 'ENDED'" @click="appendEvidence"
              >补充当前修订证据</a-button
            >
          </a-tab-pane>
          <a-tab-pane key="review" title="独立逐断言复核">
            <a-alert
              >复核者必须为具备权限的独立自然人。执行者、证据贡献者、合约作者及原结论复核者的回避由服务端校验。</a-alert
            ><ContentReader :value="instance.submittedAssertions" />
            <div v-for="assertion in assertionForms" :key="assertion.assertionId" class="my-4 border p-3"
              ><strong>{{ assertion.assertionId }} · {{ assertion.expected }}</strong
              ><a-select v-model="assertion.fact"
                ><a-option>SATISFIED</a-option><a-option>VIOLATED</a-option><a-option>UNKNOWN</a-option
                ><a-option>NOT_REACHED</a-option></a-select
              ><a-checkbox v-model="assertion.sufficient">证据充分</a-checkbox
              ><a-textarea v-model="assertion.reason" placeholder="判断依据" /><a-textarea
                v-model="assertion.authorityQuery"
                placeholder="权威来源查询及核对对象" /><input
                v-model="assertion.queryTime"
                type="datetime-local" /><a-input v-model="assertion.evidenceIds" placeholder="证据标识，逗号分隔"
            /></div>
            <a-card
              v-if="instance.result === 'PASS' && Object.keys((instance.proposedOutputs || {}) as ProtocolRecord).length"
              title="输出值独立核实"
              ><ContentReader :value="instance.proposedOutputs" /><a-textarea
                v-model="extraReview.reason"
                placeholder="逐项输出核对依据" /><a-textarea
                v-model="extraReview.authorityQuery"
                placeholder="权威查询及对象" /><input v-model="extraReview.queryTime" type="datetime-local" /><a-input
                v-model="extraReview.evidenceIds"
                placeholder="其他人员采集的独立核实证据" /></a-card
            ><a-card v-if="instance.result && !['PASS', 'FAIL'].includes(instance.result)" title="执行分类核实"
              ><a-select v-model="extraReview.decision"
                ><a-option>SUPPORTED</a-option><a-option>REJECTED</a-option><a-option>UNVERIFIABLE</a-option></a-select
              ><a-textarea v-model="extraReview.reason" placeholder="为何符合该执行分类" /><a-textarea
                v-model="extraReview.authorityQuery"
                placeholder="权威查询及对象" /><input v-model="extraReview.queryTime" type="datetime-local" /><a-input
                v-model="extraReview.evidenceIds"
                placeholder="独立核实证据" /></a-card
            ><a-space
              ><a-button v-if="human && can('RESULT_REVIEW')" type="primary" :loading="saving" @click="verify"
                >提交独立复核</a-button
              ><a-button
                v-if="human && can('RESULT_REVIEW') && instance.verification === 'ACCEPTED'"
                @click="execute('result.sync', {})"
                >仅重试正式同步</a-button
              ></a-space
            ><ContentReader :value="instance.reviewedAssertions" />
          </a-tab-pane>
          <a-tab-pane key="substitution" title="知识方法替代"
            ><KnowledgeSubstitutionForm
              :key="instance.caseExecutionId"
              :instance="instance"
              :can-confirm="!!(human && can('CONFIRM_SUBSTITUTION'))"
              :loading="saving"
              @execute="execute" /></a-tab-pane
          ><a-tab-pane key="governance" title="人工请求与治理">
            <a-alert v-if="session.context.responsibilityState === 'REASSIGN_REQUIRED'" type="warning"
              >原治理责任人资格已失效，需要有权人员重新指派。</a-alert
            ><a-input v-model="targetResponsible" placeholder="新的合格治理责任人账号" /><a-button
              v-if="human && can('GOVERN')"
              @click="
                execute(
                  'governance.transfer',
                  { expectedSessionVersion: session.sessionVersion, targetUserId: targetResponsible, reason },
                  false
                )
              "
              >转交治理责任</a-button
            ><ContentReader :value="instance.humanRequests" /><a-form-item label="治理理由"
              ><a-textarea v-model="reason"
            /></a-form-item>
            <a-space
              ><a-button v-if="can('GOVERN')" @click="execute('cancel', { reason })">关闭执行通道并保留事实</a-button
              ><a-button v-if="human && can('RESULT_REVIEW')" @click="execute('result.correction.request', { reason })"
                >发起新修订纠正</a-button
              ><a-select v-model="unverifiableReason" placeholder="无法核实的实际原因"
                ><a-option>NO_QUALIFIED_REVIEWER</a-option><a-option>NO_AUTHORITY_QUERY</a-option
                ><a-option>EVIDENCE_UNAVAILABLE</a-option></a-select
              ><a-checkbox v-model="retainUnknown">显式保留未知动作等待核实</a-checkbox
              ><a-button
                v-if="human && can('GOVERN')"
                @click="
                  execute('result.close-unaccepted', {
                    reason,
                    submissionRevision: instance.submissionRevision,
                    retainUnknown,
                    ...(unverifiableReason ? { unverifiableReason } : {}),
                  })
                "
                >关闭未接受结果</a-button
              ></a-space
            >
            <a-space
              v-for="request in instance.humanRequests.filter((item) => item.state === 'OPEN')"
              :key="String(request.id)"
              class="my-4"
              ><span>{{ request.reason }}</span
              ><a-button
                v-if="human && can('GOVERN')"
                @click="execute('human.resolve', { requestId: request.id, resolution: 'RECHECK_REQUESTED', reason })"
                >要求重新校验</a-button
              ><a-button
                v-if="human && can('GOVERN')"
                @click="execute('human.resolve', { requestId: request.id, resolution: 'CANCELLED', reason })"
                >取消此请求</a-button
              ></a-space
            >
          </a-tab-pane>
          <a-tab-pane key="history" title="步骤与事件"
            ><a-space
              v-for="item in (instance.resultHistory as ProtocolRecord[] || [])"
              :key="String(item.result_version)"
              ><span>结果版本 {{ item.result_version }} · {{ item.verification }}</span
              ><a-button @click="readHistory(Number(item.result_version))">只读查看</a-button></a-space
            ><ContentReader v-if="historical" :value="historical" /><ContentReader
              :value="instance.steps" /><ContentReader
              :value="session.events.filter((item) => !item.instance_id || item.instance_id === instanceId)"
          /></a-tab-pane>
        </a-tabs>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import { Message } from '@arco-design/web-vue';

  import GovernedWorkForm from './components/GovernedWorkForm.vue';
  import KnowledgeSubstitutionForm from './components/KnowledgeSubstitutionForm.vue';
  import ProtocolValueFields from './components/ProtocolValueFields.vue';
  import ContentReader from '@/views/test-asset/knowledge/ContentReader.vue';

  import {
    authorizeProtocolSource,
    downloadProtocolEvidence,
    type ExecutionSession,
    listSessions,
    type ProtocolRecord,
    readProtocolSourceAuthorization,
    readSession,
    sessionOperation,
    stableRequest,
    startSession,
    uploadProtocolEvidence,
  } from '@/api/modules/governed-execution';
  import useAppStore from '@/store/modules/app';
  import { useProtocolMemoryClear } from '@/utils/protocol-memory';

  const route = useRoute();
  const app = useAppStore();
  const loading = ref(false);
  const saving = ref(false);
  const projectId = ref(app.currentProjectId || '');
  const sessions = ref<ProtocolRecord[]>([]);
  const session = ref<ExecutionSession>();
  const instanceId = ref('');
  const instance = computed(() => session.value?.instances.find((item) => item.caseExecutionId === instanceId.value));
  const human = computed(() => session.value?.actorType === 'HUMAN');
  const executionVariables = ref<ProtocolRecord>({});
  const targetResponsible = ref('');
  const evidencePreview = ref('');
  const previewEvidenceId = ref('');
  const executionDeclarations = computed(() =>
    Object.assign(
      {},
      ...(session.value?.instances || []).map((item) =>
        Object.fromEntries(
          Object.entries(item.contract.variables || {}).filter(
            ([, value]) => (value as ProtocolRecord).scope === 'EXECUTION'
          )
        )
      )
    )
  );
  function clearPreview() {
    if (evidencePreview.value) URL.revokeObjectURL(evidencePreview.value);
    evidencePreview.value = '';
  }
  async function previewEvidence() {
    if (!session.value || !instance.value) return;
    const org = app.currentOrgId;
    const sid = session.value.sessionId;
    const iid = instance.value.caseExecutionId;
    clearPreview();
    const blob = await downloadProtocolEvidence(sid, iid, previewEvidenceId.value);
    if (org === app.currentOrgId && sid === session.value?.sessionId && iid === instance.value?.caseExecutionId)
      evidencePreview.value = URL.createObjectURL(new Blob([blob]));
  }
  const showStart = ref(false);
  const showAuthorization = ref(false);
  const start = reactive({
    sourceType: 'PERSONAL',
    sourceId: '',
    contractVersion: 1,
    environmentProfileId: '',
    governanceUserId: '',
  });
  const authorization = reactive({
    type: 'PERSONAL',
    sourceId: '',
    environmentId: '',
    allowedActions: [] as string[],
    expires: '',
    expectedVersion: 0,
    reason: '',
    mutationsOwnDataOnly: false,
    handoffExecutionId: '',
    handoffLeaseId: '',
    handoffAuthorityQuery: '',
    handoffEvidenceRef: '',
  });
  const preflight = reactive({ accountId: '', environmentId: '', resumeToken: '' });
  const dependencies = reactive<Record<string, { instanceId: string; resultVersion: number }>>({});
  const bindingForms = reactive<
    Record<string, { objectId: string; conditions: ProtocolRecord; declarations: ProtocolRecord; evidenceIds: string }>
  >({});
  const extraReview = reactive({
    decision: 'UNVERIFIABLE',
    reason: '',
    authorityQuery: '',
    queryTime: '',
    evidenceIds: '',
  });
  const unverifiableReason = ref('');
  const historical = ref<ProtocolRecord>();
  const reason = ref('');
  const retainUnknown = ref(false);
  const cleanupMode = ref(false);
  const visibleActions = computed(() =>
    cleanupMode.value
      ? (((instance.value?.cleanup as ProtocolRecord)?.actions || []) as ProtocolRecord[])
      : instance.value?.actions || []
  );
  const action = reactive({ actionId: '', resolution: 'UNKNOWN', queryEvidence: '', objectId: '' });
  const terminalType = ref('');
  const terminalReference = ref('');
  const evidenceIds = ref('');
  const uploadPurpose = ref('AFTER_STEP');
  const uploadStep = ref('');
  const redacted = ref(false);
  let file: File | undefined;
  const assertionForms = ref<
    Array<{
      assertionId: string;
      expected: string;
      observation: string;
      evidenceIds: string;
      fact: string;
      sufficient: boolean;
      reason: string;
      authorityQuery: string;
      queryTime: string;
    }>
  >([]);
  const clientCapabilities = [
    'start',
    'resume',
    'lease.takeover',
    'cancel',
    'action.prepare',
    'action.resolve',
    'action.status',
    'case.prepare',
    'step.save',
    'checkpoint.get',
    'case.finish',
    'finish',
    'result.evidence.append',
    'result.status.get',
    'result.close-unaccepted',
    'knowledge.substitution.propose',
    'knowledge.substitution.confirm',
    'result.correction.request',
    'observation.append',
    'context.save',
    'data.record',
    'recovery.record',
    'human.request',
    'human.resolve',
    'result.sync',
    'governance.transfer',
    'knowledge.adopt',
    'result.history.get',
    'result.revalidate',
    'cleanup.lease.takeover',
    'cleanup.prepare',
    'cleanup.resolve',
    'cleanup.recovery.record',
  ];
  function can(capability: string) {
    return session.value?.capabilities.includes(capability);
  }
  function evidenceRefs(value = evidenceIds.value) {
    return value
      .split(',')
      .map((id) => id.trim())
      .filter(Boolean)
      .map((id) => ({ kind: id.startsWith('TASK:') ? 'TASK' : 'PERSONAL', id: id.replace(/^(TASK|PERSONAL):/, '') }));
  }
  async function loadList() {
    const org = app.currentOrgId;
    loading.value = true;
    try {
      const result = await listSessions(
        org,
        projectId.value || undefined,
        typeof route.query.taskId === 'string' ? route.query.taskId : undefined
      );
      if (org === app.currentOrgId) sessions.value = result;
    } finally {
      loading.value = false;
    }
  }
  async function openSession(row: ProtocolRecord) {
    const org = app.currentOrgId;
    const next = await readSession(String(row.id));
    if (org !== app.currentOrgId) return;
    session.value = next;
    instanceId.value = next.instances[0]?.caseExecutionId || '';
  }
  async function refresh() {
    if (!session.value) return;
    const id = session.value.sessionId;
    const org = app.currentOrgId;
    try {
      const next = await readSession(id);
      if (org === app.currentOrgId && session.value?.sessionId === id) session.value = next;
    } catch (error) {
      if (session.value?.sessionId === id) {
        session.value = undefined;
        historical.value = undefined;
        preflight.resumeToken = '';
      }
      throw error;
    }
  }
  async function execute(operation: string, values: ProtocolRecord, withInstance = true) {
    if (!session.value) return;
    saving.value = true;
    const org = app.currentOrgId;
    try {
      const current = instance.value;
      const data: ProtocolRecord = { sessionId: session.value.sessionId, ...values };
      if (withInstance && current) {
        data.caseExecutionId = current.caseExecutionId;
        if (
          operation.startsWith('result.') ||
          operation.startsWith('knowledge.substitution.') ||
          operation === 'cancel'
        )
          data.expectedResultVersion = current.resultVersion;
        else if (!['action.resolve', 'cleanup.resolve', 'human.resolve'].includes(operation))
          Object.assign(data, {
            expectedContextVersion: current.contextVersion,
            leaseGeneration: current.leaseGeneration,
          });
      }
      const result = await sessionOperation(
        operation,
        stableRequest(`${org}:${data.sessionId}:${data.caseExecutionId}:${operation}`, data)
      );
      if (org !== app.currentOrgId) return;
      if (typeof result.resumeToken === 'string') preflight.resumeToken = result.resumeToken;
      Message.success(
        result.syncStatus === 'FAILED' ? '复核已保留，正式同步失败，请查看状态' : '已记录，请以刷新后的状态为准'
      );
      await refresh();
    } finally {
      saving.value = false;
    }
  }
  async function create() {
    saving.value = true;
    const org = app.currentOrgId;
    try {
      const result = await startSession(
        stableRequest(`${org}:start`, {
          projectId: projectId.value,
          sourceType: start.sourceType,
          ...(start.sourceType === 'PERSONAL'
            ? { caseId: start.sourceId, contractVersion: start.contractVersion }
            : { taskId: start.sourceId }),
          environmentProfileId: start.environmentProfileId,
          governanceUserId: start.governanceUserId,
          memoryOnlyCache: true,
          clientCapabilities,
        })
      );
      if (org !== app.currentOrgId) return;
      await loadList();
      await openSession({ id: result.sessionId });
    } finally {
      saving.value = false;
    }
  }
  async function readAuthorization() {
    const current = await readProtocolSourceAuthorization(
      projectId.value,
      authorization.type,
      authorization.sourceId,
      authorization.environmentId
    );
    authorization.expectedVersion = Number(current.version);
    Message.success('已读取当前授权版本，可核对后继续');
  }
  async function authorize() {
    const time = new Date(authorization.expires).getTime();
    if (!Number.isFinite(time)) {
      Message.warning('请选择到期时间');
      return;
    }
    saving.value = true;
    try {
      const result = await authorizeProtocolSource(projectId.value, authorization.type, authorization.sourceId, {
        environmentId: authorization.environmentId,
        allowedActions: authorization.allowedActions,
        expiresAt: time,
        expectedVersion: authorization.expectedVersion,
        reason: authorization.reason,
        mutationsOwnDataOnly: authorization.mutationsOwnDataOnly,
        ...(authorization.type === 'TASK'
          ? {
              handoffExecutionId: authorization.handoffExecutionId,
              handoffLeaseId: authorization.handoffLeaseId,
              handoffAuthorityQuery: authorization.handoffAuthorityQuery,
              handoffEvidenceRef: authorization.handoffEvidenceRef,
            }
          : {}),
      });
      authorization.expectedVersion = Number(result.version);
      Message.success(result.handoffPaused ? '移交授权已保存，旧通道已暂停，请按新协议接续' : '来源授权已保存');
    } finally {
      saving.value = false;
    }
  }
  function resolveAction() {
    execute(cleanupMode.value ? 'cleanup.resolve' : 'action.resolve', {
      ...action,
      evidence: evidenceRefs(),
      ...(action.resolution === 'CONFIRMED_NO_EFFECT'
        ? { terminalProof: { type: terminalType.value, reference: terminalReference.value } }
        : {}),
    });
  }
  function appendEvidence() {
    execute('result.evidence.append', {
      submissionRevision: instance.value?.submissionRevision,
      assertions: assertionForms.value
        .filter((item) => item.observation.trim())
        .map((item) => ({
          assertionId: item.assertionId,
          observation: item.observation,
          evidence: evidenceRefs(item.evidenceIds),
        })),
    });
  }
  function verify() {
    const assertions = assertionForms.value.map((item) => ({
      assertionId: item.assertionId,
      fact: item.fact,
      sufficient: item.sufficient,
      reason: item.reason,
      authorityQuery: item.authorityQuery,
      queryTime: new Date(item.queryTime).getTime(),
      evidence: evidenceRefs(item.evidenceIds),
    }));
    if (assertions.some((item) => item.sufficient && !Number.isFinite(item.queryTime))) {
      Message.warning('证据充分的断言须填写实际查询时间');
      return;
    }
    execute('result.verify', {
      submissionRevision: instance.value?.submissionRevision,
      verificationAttemptId: `review-${instance.value?.resultVersion}`,
      assertions: assertions.map((item) => (item.sufficient ? item : { ...item, queryTime: 0 })),
      reason: reason.value,
      ...(instance.value?.result === 'PASS' &&
      Object.keys((instance.value.proposedOutputs || {}) as ProtocolRecord).length
        ? {
            outputsReview: {
              outputsHash: instance.value.proposedOutputsHash,
              reason: extraReview.reason,
              authorityQuery: extraReview.authorityQuery,
              queryTime: new Date(extraReview.queryTime).getTime(),
              evidence: evidenceRefs(extraReview.evidenceIds),
            },
          }
        : {}),
      ...(instance.value?.result && !['PASS', 'FAIL'].includes(instance.value.result)
        ? {
            classificationReview: {
              result: instance.value.result,
              decision: extraReview.decision,
              reason: extraReview.reason,
              authorityQuery: extraReview.authorityQuery,
              queryTime: extraReview.decision === 'SUPPORTED' ? new Date(extraReview.queryTime).getTime() : 0,
              evidence: evidenceRefs(extraReview.evidenceIds),
            },
          }
        : {}),
    });
  }
  async function readHistory(version: number) {
    const current = instance.value;
    const sid = session.value?.sessionId;
    const org = app.currentOrgId;
    if (!current || !sid) return;
    const result = await sessionOperation('result.history.get', {
      sessionId: sid,
      caseExecutionId: current.caseExecutionId,
      resultVersion: version,
    });
    if (org === app.currentOrgId && sid === session.value?.sessionId) historical.value = result;
  }
  function selectFile(event: Event) {
    file = (event.target as HTMLInputElement).files?.[0];
    redacted.value = false;
  }
  async function upload() {
    const current = instance.value;
    if (!file || !current || !session.value || !redacted.value) {
      Message.warning('请选择图片并确认脱敏');
      return;
    }
    const selected = file;
    const org = app.currentOrgId;
    saving.value = true;
    try {
      const base64 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result).split(',')[1]);
        reader.onerror = reject;
        reader.readAsDataURL(selected);
      });
      const result = await uploadProtocolEvidence(
        stableRequest(`${org}:${current.caseExecutionId}:image`, {
          sessionId: session.value.sessionId,
          caseExecutionId: current.caseExecutionId,
          fileName: selected.name,
          contentType: selected.type,
          base64,
          redacted: true,
          ...(uploadStep.value ? { stepId: uploadStep.value } : {}),
          purpose: uploadPurpose.value,
        })
      );
      if (org !== app.currentOrgId) return;
      evidenceIds.value = [evidenceIds.value, result.evidenceId].filter(Boolean).join(',');
      Message.success('证据已绑定当前实例');
    } finally {
      saving.value = false;
    }
  }
  watch(
    () => instance.value?.bindings,
    (value) => {
      Object.entries((value || {}) as ProtocolRecord).forEach(([key, raw]) => {
        if (bindingForms[key]) return;
        const binding = raw as ProtocolRecord;
        bindingForms[key] = {
          objectId: String(binding.objectId || ''),
          evidenceIds: '',
          conditions: {},
          declarations: Object.fromEntries(
            Object.entries((binding.conditions || {}) as ProtocolRecord).map(([name, entry]) => [
              name,
              { type: Array.isArray(entry) ? 'array' : typeof entry },
            ])
          ),
        };
      });
    }
  );
  watch(instanceId, () => {
    clearPreview();
    historical.value = undefined;
    Object.keys(dependencies).forEach((key) => delete dependencies[key]);
    (instance.value?.contract.dependencies || []).forEach((item) => {
      dependencies[String(item.alias)] = { instanceId: '', resultVersion: 1 };
    });
    Object.keys(bindingForms).forEach((key) => delete bindingForms[key]);
    Object.entries((instance.value?.bindings || {}) as ProtocolRecord).forEach(([key, raw]) => {
      const binding = raw as ProtocolRecord;
      const conditions = (binding.conditions || {}) as ProtocolRecord;
      bindingForms[key] = {
        objectId: String(binding.objectId || ''),
        conditions: {},
        declarations: Object.fromEntries(
          Object.entries(conditions).map(([name, value]) => [
            name,
            { type: Array.isArray(value) ? 'array' : typeof value },
          ])
        ),
        evidenceIds: '',
      };
    });
    preflight.resumeToken = '';
    evidenceIds.value = '';
    assertionForms.value = (instance.value?.contract.assertions || []).map((item) => ({
      assertionId: item.id,
      expected: item.expected,
      observation: '',
      evidenceIds: '',
      fact: 'UNKNOWN',
      sufficient: false,
      reason: '',
      authorityQuery: '',
      queryTime: '',
    }));
  });
  watch(
    () => app.currentOrgId,
    () => {
      sessions.value = [];
      session.value = undefined;
      instanceId.value = '';
      preflight.resumeToken = '';
      assertionForms.value = [];
      file = undefined;
      clearPreview();
      executionVariables.value = {};
      loadList();
    }
  );
  onMounted(() => {
    if (typeof route.query.sourceProject === 'string') projectId.value = route.query.sourceProject;
    if (typeof route.query.caseId === 'string') {
      start.sourceId = route.query.caseId;
      showStart.value = true;
    }
    if (typeof route.query.taskId === 'string') {
      authorization.type = 'TASK';
      authorization.sourceId = route.query.taskId;
    }
    loadList();
  });
  useProtocolMemoryClear(() => {
    sessions.value = [];
    session.value = undefined;
    instanceId.value = '';
    preflight.resumeToken = '';
    assertionForms.value = [];
    file = undefined;
    clearPreview();
    executionVariables.value = {};
    evidenceIds.value = '';
  });
</script>
