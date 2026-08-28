<template>
  <div class="automation-execution-page">
    <div class="automation-execution-header">
      <div>
        <div class="text-[18px] font-medium text-[var(--color-text-1)]">
          {{ t('menu.bugManagement.automationExecution') }}
        </div>
        <div class="mt-[4px] text-[12px] text-[var(--color-text-3)]">
          {{ t('bugManagement.automationExecution.subtitle') }}
        </div>
      </div>
      <div class="flex flex-wrap gap-[8px]">
        <a-button
          v-if="!executionTaskId && !isCreating"
          v-permission="['AI_EXECUTION:RUN']"
          type="primary"
          @click="startCreating"
        >
          创建执行任务
        </a-button>
        <a-button v-if="executionTaskId" @click="backToTaskList">返回任务列表</a-button>
        <a-button v-if="isCreating" @click="backToTaskList">返回任务列表</a-button>
        <a-button @click="refreshAll">{{ t('common.refresh') }}</a-button>
        <a-button
          v-if="task?.confirmRequired || task?.status === 'WAITING_CONFIRMATION'"
          v-permission="['AI_EXECUTION:RUN']"
          type="primary"
          :loading="actionLoading"
          @click="handleConfirm"
        >
          {{ t('bugManagement.automationExecution.confirm') }}
        </a-button>
        <a-button
          v-if="task?.status === 'WAITING_LOGIN' || task?.status === 'PAUSED'"
          v-permission="['AI_EXECUTION:RUN']"
          type="primary"
          :loading="actionLoading"
          @click="handleLoginReady"
        >
          {{ t('bugManagement.automationExecution.loginReady') }}
        </a-button>
        <a-button
          v-permission="['AI_EXECUTION:RUN']"
          :disabled="!canPause"
          :loading="actionLoading"
          @click="handlePause"
        >
          {{ t('bugManagement.automationExecution.pause') }}
        </a-button>
        <a-button
          v-permission="['AI_EXECUTION:CANCEL']"
          status="danger"
          :disabled="!canCancel"
          :loading="actionLoading"
          @click="handleCancel"
        >
          {{ t('bugManagement.automationExecution.stop') }}
        </a-button>
        <a-button
          v-permission="['AI_EXECUTION:RUN']"
          :disabled="!canRetry"
          :loading="actionLoading"
          @click="handleRetry"
        >
          {{ t('bugManagement.automationExecution.retryFailed') }}
        </a-button>
      </div>
    </div>

    <section v-if="!executionTaskId && !isCreating" class="task-center-panel">
      <div class="mb-[12px] flex flex-wrap items-center gap-[8px]">
        <a-input-search
          v-model:model-value="taskSearch.keyword"
          class="w-[260px]"
          allow-clear
          placeholder="搜索任务名称、目标或 ID"
          @search="reloadTaskList"
          @press-enter="reloadTaskList"
        />
        <a-select v-model:model-value="taskSearch.status" class="w-[160px]" allow-clear placeholder="运行状态">
          <a-option v-for="status in taskStatuses" :key="status" :value="status">{{ status }}</a-option>
        </a-select>
        <a-select v-model:model-value="taskSearch.verdict" class="w-[170px]" allow-clear placeholder="业务结论">
          <a-option v-for="verdict in taskVerdicts" :key="verdict" :value="verdict">{{ verdict }}</a-option>
        </a-select>
        <a-select v-model:model-value="taskSearch.executorChannel" class="w-[190px]" allow-clear placeholder="执行通道">
          <a-option value="MODEL_API_RUNNER">平台模型执行器</a-option>
          <a-option value="EXTERNAL_MCP_AGENT">个人 MCP Agent</a-option>
        </a-select>
        <a-button type="primary" :loading="taskListLoading" @click="reloadTaskList">查询</a-button>
      </div>
      <a-table :data="taskList" :loading="taskListLoading" :pagination="false" row-key="id" @row-click="openTask">
        <template #columns>
          <a-table-column title="任务" :width="280">
            <template #cell="{ record }">
              <div class="font-medium">{{ record.name || record.id }}</div>
              <div class="mt-[2px] truncate text-[12px] text-[var(--color-text-3)]">{{
                record.objective || record.id
              }}</div>
            </template>
          </a-table-column>
          <a-table-column title="运行状态" data-index="status" :width="150" />
          <a-table-column title="业务结论" :width="170">
            <template #cell="{ record }">
              <a-tag :color="verdictColor(record.verdict)">{{ record.verdict || '待判定' }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="执行来源 / 通道" :width="230">
            <template #cell="{ record }">
              {{ taskOriginLabel(record.taskOrigin) }} / {{ executorChannelLabel(record.executorChannel) }}
            </template>
          </a-table-column>
          <a-table-column title="进度" :width="130">
            <template #cell="{ record }"
              >{{ (record.successCount || 0) + (record.failedCount || 0) }}/{{ record.totalCount || 0 }}</template
            >
          </a-table-column>
          <a-table-column title="尝试" :width="90">
            <template #cell="{ record }">{{ record.attemptCount || 0 }}/{{ record.maxAttempts || 3 }}</template>
          </a-table-column>
          <a-table-column title="创建时间" :width="180">
            <template #cell="{ record }">{{ formatTime(record.createTime) }}</template>
          </a-table-column>
        </template>
      </a-table>
      <div class="mt-[12px] flex justify-end">
        <a-pagination
          v-model:current="taskSearch.current"
          v-model:page-size="taskSearch.pageSize"
          :total="taskListTotal"
          show-total
          show-page-size
          @change="reloadTaskList"
          @page-size-change="reloadTaskList"
        />
      </div>
    </section>

    <div v-else class="automation-execution-workbench">
      <section class="automation-panel">
        <div class="panel-title">{{ t('bugManagement.automationExecution.conversation') }}</div>
        <a-form :model="draftForm" layout="vertical" class="mb-[12px]">
          <a-alert class="mb-[12px]" type="info" show-icon>
            平台创建的任务固定由模型执行器运行；个人 Agent 任务仅能通过 MCP 创建。
          </a-alert>
          <a-form-item label="MAP Gateway 模型配置" required
            ><a-select v-model="draftForm.modelProfileId"
              ><a-option v-for="item in platformModelProfiles" :key="item.id" :value="item.id"
                >{{ item.name }} · {{ item.logicalModelPublicId }}</a-option
              ></a-select
            ></a-form-item
          >
          <a-form-item label="已发布 Prompt 模板" required
            ><a-select v-model="draftForm.promptTemplateId"
              ><a-option v-for="item in platformPromptTemplates" :key="item.id" :value="item.promptTemplateId"
                >{{ item.name }} · v{{ item.versionNo }}</a-option
              ></a-select
            ></a-form-item
          >
          <a-form-item label="环境执行配置" required
            ><a-select v-model="draftForm.environmentProfileId" @change="selectExecutionEnvironment"
              ><a-option v-for="item in platformEnvironmentProfiles" :key="item.id" :value="item.id"
                >{{ item.name }} · {{ item.baseUrl }}</a-option
              ></a-select
            ></a-form-item
          >
          <a-form-item label="凭据引用"
            ><a-select v-model="draftForm.credentialReferenceId" allow-clear
              ><a-option v-for="item in platformCredentialReferences" :key="item.id" :value="item.id"
                >{{ item.name }} · {{ item.businessRole }}</a-option
              ></a-select
            ></a-form-item
          >
          <a-form-item label="任务资产">
            <div class="w-full">
              <div class="mb-[8px] flex flex-wrap gap-[8px]">
                <a-tag
                  v-for="item in selectedAssetRefs"
                  :key="`${item.assetType}:${item.assetId}`"
                  closable
                  @close="removeAssetRef(item)"
                >
                  {{ item.assetName }}（{{ item.assetType }}）{{ item.versionId ? '·固定版本' : '·创建时固定' }}
                </a-tag>
                <span v-if="selectedAssetRefs.length === 0" class="text-[var(--color-text-3)]"
                  >尚未选择扩展测试资产</span
                >
              </div>
              <a-button v-permission="['AI_EXECUTION:RUN']" size="small" @click="openAssetPicker"
                >选择测试资产</a-button
              >
              <span class="ml-[8px] text-[12px] text-[var(--color-text-3)]">最多 50 项，创建任务时固定版本</span>
            </div>
          </a-form-item>
        </a-form>
        <div class="message-list">
          <div v-for="message in messages" :key="message.id" class="message-item" :class="message.role">
            <div class="message-role">{{ message.role === 'user' ? 'User' : 'AI' }}</div>
            <div class="message-content">{{ message.content }}</div>
          </div>
          <a-empty v-if="messages.length === 0" :description="t('bugManagement.automationExecution.noMessage')" />
        </div>
        <a-textarea
          v-model:model-value="prompt"
          :placeholder="t('bugManagement.automationExecution.inputPlaceholder')"
          :auto-size="{ minRows: 3, maxRows: 6 }"
          :max-length="2000"
          show-word-limit
        />
        <div class="mt-[12px] flex flex-wrap justify-end gap-[8px]">
          <a-button :loading="resolveLoading" :disabled="!prompt.trim()" @click="sendPrompt">
            {{ t('bugManagement.automationExecution.resolveScope') }}
          </a-button>
          <a-button
            v-permission="['AI_EXECUTION:RUN']"
            type="primary"
            :loading="createLoading"
            :disabled="
              !canCreateFromResolve ||
              !draftForm.modelProfileId ||
              !draftForm.promptTemplateId ||
              !draftForm.environmentProfileId
            "
            @click="createFromResolve"
          >
            {{ t('bugManagement.automationExecution.createTask') }}
          </a-button>
        </div>

        <div v-if="resolveResult" class="scope-preview mt-[12px]">
          <div class="panel-subtitle mb-[8px]">{{ t('bugManagement.automationExecution.scopePreview') }}</div>
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item :label="t('bugManagement.automationExecution.status')">
              {{ resolveResult.status || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.total')">
              {{ resolveResult.total ?? 0 }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.estimatedMinutes')">
              {{ resolveResult.estimatedMinutes ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.plan')">
              {{ resolveResult.testPlanId || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.confirmRequired')">
              {{ resolveResult.confirmationRequired ? resolveResult.confirmationReason : '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.parseConfidence')">
              {{ resolveResult.parseConfidence ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.snapshotHash')">
              {{ resolveResult.caseSnapshotHash || '-' }}
            </a-descriptions-item>
          </a-descriptions>

          <a-alert v-if="observabilityError" class="mt-[12px]" type="error" :content="observabilityError">
            <template #action><a-button size="mini" @click="loadObservability">重试</a-button></template>
          </a-alert>
          <a-collapse v-if="task" class="mt-[12px]" :bordered="false">
            <a-collapse-item key="governance" header="冻结上下文、模型调用与运行治理">
              <a-spin :loading="observabilityLoading" class="w-full">
                <a-descriptions :column="2" size="small" bordered>
                  <a-descriptions-item label="Preflight 状态">{{
                    observability?.preflight?.status || '-'
                  }}</a-descriptions-item>
                  <a-descriptions-item label="Preflight 快照 Hash">{{
                    observability?.preflight?.assetSnapshotHash || '-'
                  }}</a-descriptions-item>
                  <a-descriptions-item label="模型调用次数">{{
                    observability?.modelInvocations?.length || 0
                  }}</a-descriptions-item>
                  <a-descriptions-item label="Runner Lease 次数">{{
                    observability?.runnerLeases?.length || 0
                  }}</a-descriptions-item>
                  <a-descriptions-item label="数据租约数">{{
                    observability?.dataLeases?.length || 0
                  }}</a-descriptions-item>
                  <a-descriptions-item label="清理任务数">{{
                    observability?.cleanupJobs?.length || 0
                  }}</a-descriptions-item>
                </a-descriptions>
                <div class="panel-subtitle mt-[12px]">模型 Invocation</div>
                <a-table :data="observability?.modelInvocations || []" :pagination="false" size="small" row-key="id">
                  <template #columns>
                    <a-table-column title="Request ID" data-index="gatewayRequestId" :width="210" ellipsis tooltip />
                    <a-table-column title="状态" data-index="status" :width="100" />
                    <a-table-column title="TTFT(ms)" data-index="ttftMs" :width="105" />
                    <a-table-column title="总耗时(ms)" data-index="durationMs" :width="115" />
                    <a-table-column title="输入/输出 Token" :width="145"
                      ><template #cell="{ record }"
                        >{{ record.inputTokens || 0 }}/{{ record.outputTokens || 0 }}</template
                      ></a-table-column
                    >
                    <a-table-column title="重试" data-index="retryCount" :width="80" />
                    <a-table-column title="费用" :width="110"
                      ><template #cell="{ record }"
                        >{{ record.costAmount ?? 0 }} {{ record.currency || '' }}</template
                      ></a-table-column
                    >
                    <a-table-column title="错误" data-index="errorCode" ellipsis tooltip />
                  </template>
                </a-table>
                <div class="panel-subtitle mt-[12px]">数据租约与清理</div>
                <a-table :data="observability?.dataLeases || []" :pagination="false" size="small" row-key="id">
                  <template #columns>
                    <a-table-column title="数据集" data-index="datasetId" />
                    <a-table-column title="数据键" data-index="dataKey" />
                    <a-table-column title="命名空间" data-index="namespace" />
                    <a-table-column title="状态" data-index="status" :width="100" />
                    <a-table-column title="过期时间" :width="170"
                      ><template #cell="{ record }">{{ formatTime(record.expiresAt) }}</template></a-table-column
                    >
                  </template>
                </a-table>
                <a-table
                  class="mt-[8px]"
                  :data="observability?.cleanupJobs || []"
                  :pagination="false"
                  size="small"
                  row-key="id"
                >
                  <template #columns>
                    <a-table-column title="清理类型" data-index="cleanupType" />
                    <a-table-column title="状态" data-index="status" :width="100" />
                    <a-table-column title="尝试次数" data-index="attemptCount" :width="100" />
                    <a-table-column title="错误" data-index="errorMessage" ellipsis tooltip />
                  </template>
                </a-table>
              </a-spin>
            </a-collapse-item>
          </a-collapse>
          <div
            v-if="(resolveResult.matchedReasons || []).length"
            class="mt-[8px] text-[12px] text-[var(--color-text-3)]"
          >
            {{ (resolveResult.matchedReasons || []).join('；') }}
          </div>
          <a-alert
            v-if="resolveResult.highRisk"
            class="mt-[8px]"
            type="warning"
            :content="
              t('bugManagement.automationExecution.highRiskTip', {
                keywords: (resolveResult.highRiskSignals || []).join(', '),
              })
            "
          />
          <a-checkbox v-if="resolveResult.confirmationRequired" v-model:model-value="resolveConfirmed" class="mt-[8px]">
            {{ t('bugManagement.automationExecution.confirmScopeCheckbox') }}
          </a-checkbox>
          <a-table
            v-if="(resolveResult.cases || []).length"
            class="mt-[8px]"
            size="small"
            :pagination="false"
            :scroll="{ y: 160 }"
            :data="resolveResult.cases || []"
            row-key="caseId"
          >
            <template #columns>
              <a-table-column title="ID" data-index="num" :width="80" />
              <a-table-column :title="t('bugManagement.automationExecution.caseName')" data-index="name" />
            </template>
          </a-table>
          <a-table
            v-if="(resolveResult.candidatePlans || []).length"
            class="mt-[8px]"
            size="small"
            :pagination="false"
            :data="resolveResult.candidatePlans || []"
            row-key="id"
          >
            <template #columns>
              <a-table-column :title="t('bugManagement.automationExecution.plan')" data-index="name" />
              <a-table-column :title="t('bugManagement.automationExecution.status')" data-index="status" :width="120" />
              <a-table-column
                :title="t('bugManagement.automationExecution.total')"
                data-index="associatedCaseCount"
                :width="100"
              />
            </template>
          </a-table>
        </div>
        <a-alert class="mt-[12px]" type="info" show-icon>
          {{ t('bugManagement.automationExecution.scopeTip') }}
        </a-alert>
      </section>

      <section class="automation-panel">
        <div class="panel-title">{{ t('bugManagement.automationExecution.executionPanel') }}</div>
        <a-empty v-if="!executionTaskId" :description="t('bugManagement.automationExecution.empty')" />
        <a-spin v-else :loading="loading" class="w-full">
          <a-descriptions v-if="task" :column="2" size="small" bordered>
            <a-descriptions-item :label="t('bugManagement.automationExecution.taskId')">
              {{ task.id }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.status')">
              <a-tag>{{ task.status }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="业务结论">
              <a-tag :color="verdictColor(task.verdict)">{{ task.verdict || '待判定' }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="结论说明">
              {{ task.verdictReason || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.project')">
              {{ task.projectId }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.plan')">
              {{ task.testPlanId || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.model')">
              {{ task.providerId || chatModelId || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="任务来源">
              {{ taskOriginLabel(task.taskOrigin) }}
            </a-descriptions-item>
            <a-descriptions-item label="执行通道">
              {{ executorChannelLabel(task.executorChannel) }}
            </a-descriptions-item>
            <a-descriptions-item label="本次执行 ID">
              {{ task.currentExecutionId || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="Trace ID">{{ task.traceId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="Preflight ID">{{ task.preflightId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="环境 Profile / 版本">
              {{ task.environmentProfileId || '-' }} / {{ task.environmentProfileVersion ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="模型 / Prompt 版本">
              {{ task.modelProfileId || '-' }} / {{ task.promptTemplateVersionId || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="Runner / Lease">
              {{ task.runnerId || '-' }} / {{ task.runnerLeaseId || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="执行合同 Hash">{{ task.executionContractHash || '-' }}</a-descriptions-item>
            <a-descriptions-item label="范围 / 扩围">
              {{ task.originalScopeCount || 0 }} + {{ task.expandedScopeCount || 0 }}（{{
                ((task.scopeExpansionRate || 0) * 100).toFixed(2)
              }}%）
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.startTime')">
              {{ formatTime(task.createTime) }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.writebackStatus')">
              {{ task.writebackStatus || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.artifactStatus')">
              {{ task.artifactStatus || '-' }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.success')">
              {{ task.successCount || 0 }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.failed')">
              {{ task.failedCount || 0 }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.blocked')">
              {{ task.blockedCount || 0 }}
            </a-descriptions-item>
            <a-descriptions-item :label="t('bugManagement.automationExecution.unexecuted')">
              {{ task.unexecutedCount || 0 }}
            </a-descriptions-item>
          </a-descriptions>

          <a-alert
            v-if="task?.confirmRequired || task?.status === 'WAITING_CONFIRMATION'"
            class="mt-[12px]"
            type="warning"
            :content="task.confirmationReason || t('bugManagement.automationExecution.confirmRequired')"
          />
          <a-alert
            v-if="task?.status === 'WAITING_LOGIN'"
            class="mt-[12px]"
            type="warning"
            :content="t('bugManagement.automationExecution.waitingLogin')"
          />
          <a-alert
            v-if="task?.status === 'PARTIAL_SUCCESS'"
            class="mt-[12px]"
            type="warning"
            :content="t('bugManagement.automationExecution.partialSuccessTip')"
          />
          <a-alert class="mt-[12px]" type="info" :content="t('bugManagement.automationExecution.runnerPlaceholder')" />

          <div v-if="humanRequests.length" class="mt-[12px]">
            <div class="panel-subtitle mb-[8px]">人工介入请求</div>
            <a-card v-for="request in humanRequests" :key="request.id" class="mb-[8px]" :bordered="true">
              <div class="flex items-start justify-between gap-[12px]">
                <div
                  ><div class="font-medium">{{ request.title }}</div
                  ><div class="mt-[4px] text-[12px] text-[var(--color-text-3)]">{{ request.content || '-' }}</div></div
                >
                <a-tag :color="request.status === 'PENDING' ? 'orange' : 'gray'">{{ request.status }}</a-tag>
              </div>
              <div v-if="request.status === 'PENDING'" class="mt-[8px] flex justify-end gap-[8px]">
                <a-button v-permission="['AI_EXECUTION:RUN']" @click="respondHuman(request.id, 'CANCEL')"
                  >取消请求</a-button
                >
                <a-button
                  v-permission="['AI_EXECUTION:RUN']"
                  status="danger"
                  @click="respondHuman(request.id, 'REJECT')"
                  >拒绝</a-button
                >
                <a-button
                  v-if="request.requestType === 'INPUT'"
                  v-permission="['AI_EXECUTION:RUN']"
                  type="primary"
                  @click="openHumanAnswer(request)"
                  >输入并恢复</a-button
                >
                <a-button
                  v-else
                  v-permission="['AI_EXECUTION:RUN']"
                  type="primary"
                  @click="respondHuman(request.id, 'APPROVE')"
                  >批准并恢复</a-button
                >
              </div>
            </a-card>
          </div>

          <div class="mt-[12px] flex items-center justify-between">
            <div class="panel-subtitle">{{ t('bugManagement.automationExecution.caseProgress') }}</div>
          </div>
          <a-table
            v-if="task"
            class="mt-[8px]"
            :data="task.cases || []"
            :pagination="false"
            :scroll="{ y: 220 }"
            row-key="caseId"
            size="small"
          >
            <template #columns>
              <a-table-column title="ID" data-index="caseNum" :width="90" />
              <a-table-column :title="t('bugManagement.automationExecution.caseName')" data-index="caseName" />
              <a-table-column :title="t('bugManagement.automationExecution.status')" data-index="status" :width="120" />
              <a-table-column :title="t('bugManagement.automationExecution.result')" data-index="result" :width="100" />
              <a-table-column :title="t('bugManagement.automationExecution.error')" data-index="errorMessage" />
            </template>
          </a-table>

          <div class="panel-subtitle mt-[12px]">{{ t('bugManagement.automationExecution.evidence') }}</div>
          <div v-if="artifacts.length" class="evidence-list mt-[8px]">
            <div v-for="artifact in artifacts" :key="artifact.id" class="evidence-item">
              <a-image :src="artifact.downloadPath" width="140" height="88" fit="cover" />
              <div class="mt-[4px] truncate text-[12px]">{{ artifact.purpose }} · {{ artifact.fileName }}</div>
              <div class="truncate text-[11px] text-[var(--color-text-3)]">{{ artifact.sha256 }}</div>
            </div>
          </div>
          <a-empty v-else :description="t('bugManagement.automationExecution.noEvidence')" />

          <div class="mt-[12px] flex items-center justify-between gap-[8px]">
            <div class="panel-subtitle">{{ t('bugManagement.automationExecution.eventLog') }}</div>
            <div class="flex flex-wrap items-center justify-end gap-[8px]">
              <a-select v-model:model-value="eventLevel" size="mini" class="w-[120px]" @change="resetEvents">
                <a-option value="ALL">ALL</a-option>
                <a-option value="INFO">INFO</a-option>
                <a-option value="WARN">WARN</a-option>
                <a-option value="ERROR">ERROR</a-option>
              </a-select>
              <a-select
                v-model:model-value="eventTypeFilter"
                size="mini"
                allow-clear
                class="w-[160px]"
                placeholder="事件类型"
              >
                <a-option v-for="type in eventTypeOptions" :key="type" :value="type">{{ type }}</a-option>
              </a-select>
              <a-select
                v-model:model-value="eventActorFilter"
                size="mini"
                allow-clear
                class="w-[180px]"
                placeholder="执行者"
              >
                <a-option v-for="actor in eventActorOptions" :key="actor.value" :value="actor.value">
                  {{ actor.label }}
                </a-option>
              </a-select>
              <a-input
                v-model:model-value="eventStepFilter"
                size="mini"
                allow-clear
                class="w-[140px]"
                placeholder="步骤 ID"
              />
              <a-range-picker
                v-model:model-value="eventTimeRange"
                size="mini"
                show-time
                value-format="timestamp"
                class="w-[300px]"
              />
              <a-checkbox v-model:model-value="eventFailedOnly">仅失败</a-checkbox>
              <a-button size="mini" @click="downloadEvents">
                {{ t('bugManagement.automationExecution.downloadLog') }}
              </a-button>
            </div>
          </div>
          <div class="event-list">
            <div v-for="event in filteredEvents" :key="`${event.sequence}-${event.id}`" class="event-item">
              <a-tag size="small" :color="eventLevelColor(event.level)">{{ event.level }}</a-tag>
              <span class="event-time">{{ formatTime(event.eventTime) }}</span>
              <span class="event-type">{{ event.eventType }}</span>
              <span class="event-type">{{ event.actorType || '-' }} / {{ event.actorId || '-' }}</span>
              <span class="event-message">{{ event.message }}</span>
            </div>
            <a-empty v-if="filteredEvents.length === 0" :description="t('bugManagement.automationExecution.noEvent')" />
          </div>
        </a-spin>
      </section>
    </div>
    <a-modal v-model:visible="assetPickerVisible" title="选择测试资产" :width="860" :footer="false" unmount-on-close>
      <div class="mb-[12px] flex gap-[8px]">
        <a-select v-model:model-value="assetPicker.assetType" class="w-[180px]" @change="searchAssetPicker">
          <a-option v-for="item in assetTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</a-option>
        </a-select>
        <a-input-search
          v-model:model-value="assetPicker.keyword"
          allow-clear
          placeholder="搜索名称、ID 或摘要"
          @search="searchAssetPicker"
          @clear="searchAssetPicker"
        />
      </div>
      <a-table :data="assetPickerRecords" :loading="assetPickerLoading" row-key="id" :pagination="false">
        <a-table-column title="名称" data-index="name" />
        <a-table-column title="类型" data-index="category" :width="140" />
        <a-table-column title="状态" data-index="status" :width="120" />
        <a-table-column title="版本" :width="90">
          <template #cell="{ record }">v{{ record.assetVersionNo || 1 }}</template>
        </a-table-column>
        <a-table-column title="操作" :width="90">
          <template #cell="{ record }">
            <a-link v-if="!hasAssetRef(record.assetType, record.id)" @click="addAssetRecord(record)">添加</a-link>
            <span v-else class="text-[var(--color-text-3)]">已添加</span>
          </template>
        </a-table-column>
      </a-table>
      <div class="mt-[12px] flex justify-end">
        <a-pagination
          v-model:current="assetPicker.current"
          v-model:page-size="assetPicker.pageSize"
          :total="assetPickerTotal"
          show-total
          @change="loadAssetPicker"
        />
      </div>
    </a-modal>
    <a-modal v-model:visible="humanAnswerVisible" title="补充人工输入" @before-ok="submitHumanAnswer">
      <a-textarea v-model="humanAnswer" :max-length="4000" show-word-limit :auto-size="{ minRows: 4, maxRows: 8 }" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { Message, type TableData } from '@arco-design/web-vue';
  import dayjs from 'dayjs';

  import {
    type AiCredentialReference,
    type AiEnvironmentProfile,
    type AiExecutionArtifact,
    type AiExecutionEvent,
    type AiExecutionObservability,
    type AiExecutionResolveResult,
    type AiExecutionTask,
    type AiExecutorChannel,
    type AiHumanRequest,
    type AiModelProfile,
    type AiPromptTemplateVersion,
    cancelAiExecutionTask,
    confirmAiExecutionTask,
    createAiExecutionTask,
    getAiExecutionArtifacts,
    getAiExecutionEvents,
    getAiExecutionObservability,
    getAiExecutionTask,
    getAiHumanRequests,
    listAiCredentialReferences,
    listAiEnvironmentProfiles,
    listAiModelProfiles,
    listAiPromptTemplateVersions,
    loginReadyAiExecutionTask,
    pageTestAssetCatalog,
    pauseAiExecutionTask,
    preflightAiExecution,
    resolveAiExecutionScope,
    respondAiHumanRequest,
    retryAiExecutionTask,
    searchAiExecutionTasks,
    type TestAssetCatalogItem,
    type TestAssetCatalogType,
  } from '@/api/modules/ai-execution';
  import { useI18n } from '@/hooks/useI18n';
  import { useAppStore } from '@/store';
  import useAIStore from '@/store/modules/setting/ai';

  const route = useRoute();
  const router = useRouter();
  const { t } = useI18n();
  const appStore = useAppStore();
  const aiStore = useAIStore();

  const loading = ref(false);
  const actionLoading = ref(false);
  const resolveLoading = ref(false);
  const createLoading = ref(false);
  const task = ref<AiExecutionTask>();
  const observability = ref<AiExecutionObservability>();
  const observabilityLoading = ref(false);
  const observabilityError = ref('');
  const taskListLoading = ref(false);
  const taskList = ref<AiExecutionTask[]>([]);
  const taskListTotal = ref(0);
  const taskSearch = reactive({
    keyword: '',
    status: undefined as string | undefined,
    verdict: undefined as string | undefined,
    executorChannel: undefined as AiExecutorChannel | undefined,
    current: 1,
    pageSize: 20,
  });
  const taskStatuses = [
    'WAITING_CONFIRMATION',
    'QUEUED',
    'PREPARING_BROWSER',
    'WAITING_LOGIN',
    'WAITING_HUMAN',
    'RUNNING',
    'PAUSED',
    'WRITING_BACK',
    'SUCCESS',
    'PARTIAL_SUCCESS',
    'FAILED',
    'CANCELED',
  ];
  const taskVerdicts = [
    'PASSED',
    'PRODUCT_FAILED',
    'ENV_FAILED',
    'DATA_FAILED',
    'AGENT_FAILED',
    'BLOCKED',
    'INCONCLUSIVE',
  ];
  const events = ref<AiExecutionEvent[]>([]);
  const artifacts = ref<AiExecutionArtifact[]>([]);
  const humanRequests = ref<AiHumanRequest[]>([]);
  const humanAnswerVisible = ref(false);
  const humanAnswerRequestId = ref('');
  const humanAnswer = ref('');
  const eventCursor = ref(0);
  const eventLevel = ref('ALL');
  const eventTypeFilter = ref('');
  const eventActorFilter = ref('');
  const eventStepFilter = ref('');
  const eventTimeRange = ref<Array<number | string>>([]);
  const eventFailedOnly = ref(false);
  const prompt = ref('');
  const draftForm = reactive({
    environmentId: '',
    environmentProfileId: '',
    credentialReferenceId: '',
    modelProfileId: '',
    promptTemplateId: '',
  });
  const platformEnvironmentProfiles = ref<AiEnvironmentProfile[]>([]);
  const platformCredentialReferences = ref<AiCredentialReference[]>([]);
  const platformModelProfiles = ref<AiModelProfile[]>([]);
  const platformPromptTemplates = ref<AiPromptTemplateVersion[]>([]);
  async function loadExecutionProfiles() {
    if (!appStore.currentProjectId) return;
    const [e, c, m, p] = await Promise.all([
      listAiEnvironmentProfiles(appStore.currentProjectId),
      listAiCredentialReferences(appStore.currentProjectId),
      listAiModelProfiles(appStore.currentProjectId),
      listAiPromptTemplateVersions(appStore.currentProjectId),
    ]);
    platformEnvironmentProfiles.value = e.filter((x) => x.enabled);
    platformCredentialReferences.value = c.filter((x) => x.enabled && x.status === 'ACTIVE');
    platformModelProfiles.value = m.filter((x) => x.enabled && x.lastVerifyStatus === 'SUCCESS');
    platformPromptTemplates.value = p.filter((x) => x.status === 'PUBLISHED');
    if (!draftForm.modelProfileId) draftForm.modelProfileId = platformModelProfiles.value[0]?.id || '';
    if (!draftForm.promptTemplateId)
      draftForm.promptTemplateId = platformPromptTemplates.value[0]?.promptTemplateId || '';
  }
  function selectExecutionEnvironment() {
    const e = platformEnvironmentProfiles.value.find((x) => x.id === draftForm.environmentProfileId);
    if (e?.defaultCredentialReferenceId) draftForm.credentialReferenceId = e.defaultCredentialReferenceId;
  }
  type SelectedAssetRef = {
    assetType: TestAssetCatalogType;
    assetId: string;
    assetName: string;
    versionId?: string;
  };
  const selectedAssetRefs = ref<SelectedAssetRef[]>([]);
  const environmentOptions = ref<TestAssetCatalogItem[]>([]);
  const assetPickerVisible = ref(false);
  const assetPickerLoading = ref(false);
  const assetPickerRecords = ref<TestAssetCatalogItem[]>([]);
  const assetPickerTotal = ref(0);
  const assetPicker = reactive({ assetType: 'DATASET' as TestAssetCatalogType, keyword: '', current: 1, pageSize: 10 });
  const assetTypeOptions: Array<{ label: string; value: TestAssetCatalogType }> = [
    { label: '测试数据', value: 'DATASET' },
    { label: '测试环境', value: 'ENVIRONMENT' },
    { label: '公共步骤', value: 'COMMON_STEP' },
    { label: '接口定义', value: 'API_DEFINITION' },
    { label: '执行证据', value: 'EVIDENCE' },
    { label: '缺陷', value: 'BUG' },
  ];
  const resolveResult = ref<AiExecutionResolveResult>();
  const resolveConfirmed = ref(false);
  const chatModelId = ref(localStorage.getItem('aiChatModel') || '');
  const messages = ref<Array<{ id: string; role: 'user' | 'assistant'; content: string }>>([]);
  const lastResolvedPrompt = ref('');
  let pollTimer: number | undefined;

  const executionTaskId = computed(() => (route.query.executionTaskId as string | undefined) || undefined);
  const isCreating = computed(() => route.query.creating === '1');
  const selectedAssetRef = computed(() => {
    const assetType = String(route.query.assetType || '');
    const assetId = String(route.query.assetId || '');
    if (!assetType || !assetId) return undefined;
    return {
      assetType: assetType as import('@/api/modules/ai-execution').TestAssetCatalogType,
      assetId,
      assetName: String(route.query.assetName || assetId),
      versionId: String(route.query.assetVersionId || '') || undefined,
    };
  });

  function hasAssetRef(assetType: TestAssetCatalogType, assetId: string) {
    return selectedAssetRefs.value.some((item) => item.assetType === assetType && item.assetId === assetId);
  }

  function addAssetRef(item: SelectedAssetRef) {
    if (hasAssetRef(item.assetType, item.assetId)) return;
    if (selectedAssetRefs.value.length >= 50) {
      Message.warning('单个任务最多选择 50 个扩展测试资产');
      return;
    }
    if (item.assetType === 'ENVIRONMENT') {
      selectedAssetRefs.value = selectedAssetRefs.value.filter((existing) => existing.assetType !== 'ENVIRONMENT');
      draftForm.environmentId = item.assetId;
    }
    selectedAssetRefs.value.push(item);
  }

  function addAssetRecord(item: TestAssetCatalogItem) {
    addAssetRef({
      assetType: item.assetType,
      assetId: item.id,
      assetName: item.name,
      versionId: item.assetVersionId,
    });
  }

  function removeAssetRef(item: SelectedAssetRef) {
    selectedAssetRefs.value = selectedAssetRefs.value.filter(
      (existing) => existing.assetType !== item.assetType || existing.assetId !== item.assetId
    );
    if (item.assetType === 'ENVIRONMENT' && draftForm.environmentId === item.assetId) draftForm.environmentId = '';
  }

  async function loadEnvironmentOptions(keyword = '') {
    if (!appStore.currentProjectId) return;
    const result = await pageTestAssetCatalog({
      projectId: appStore.currentProjectId,
      assetType: 'ENVIRONMENT',
      status: 'PUBLISHED',
      keyword: keyword.trim() || undefined,
      current: 1,
      pageSize: 100,
    });
    environmentOptions.value = result.list || [];
  }

  function handleEnvironmentChange(value?: unknown) {
    const environmentId = typeof value === 'string' || typeof value === 'number' ? String(value) : '';
    selectedAssetRefs.value = selectedAssetRefs.value.filter((item) => item.assetType !== 'ENVIRONMENT');
    if (!environmentId) return;
    const environment = environmentOptions.value.find((item) => item.id === environmentId);
    if (environment) addAssetRecord(environment);
  }

  async function loadAssetPicker() {
    if (!appStore.currentProjectId) return;
    assetPickerLoading.value = true;
    try {
      const result = await pageTestAssetCatalog({
        projectId: appStore.currentProjectId,
        assetType: assetPicker.assetType,
        status: 'PUBLISHED',
        keyword: assetPicker.keyword.trim() || undefined,
        current: assetPicker.current,
        pageSize: assetPicker.pageSize,
      });
      assetPickerRecords.value = result.list || [];
      assetPickerTotal.value = result.total || 0;
    } finally {
      assetPickerLoading.value = false;
    }
  }

  function searchAssetPicker() {
    assetPicker.current = 1;
    loadAssetPicker();
  }

  function openAssetPicker() {
    assetPickerVisible.value = true;
    loadAssetPicker();
  }
  const modelOptions = computed(() =>
    (aiStore.aiSourceNameList || []).map((item) => ({
      label: item.name,
      value: item.id,
    }))
  );
  const eventTypeOptions = computed(() => [...new Set(events.value.map((item) => item.eventType))].sort());
  const eventActorOptions = computed(() => {
    const actors = new Map<string, string>();
    events.value.forEach((item) => {
      const value = `${item.actorType || ''}:${item.actorId || ''}`;
      if (value !== ':') actors.set(value, `${item.actorType || '-'} / ${item.actorId || '-'}`);
    });
    return [...actors.entries()].map(([value, label]) => ({ value, label }));
  });
  const filteredEvents = computed(() => {
    const stepKeyword = eventStepFilter.value.trim().toLowerCase();
    const [startTime, endTime] = eventTimeRange.value.map(Number);
    return events.value.filter((item) => {
      const actor = `${item.actorType || ''}:${item.actorId || ''}`;
      const failed = item.level === 'ERROR' || item.eventType.includes('FAILED');
      return (
        (eventLevel.value === 'ALL' || item.level === eventLevel.value) &&
        (!eventTypeFilter.value || item.eventType === eventTypeFilter.value) &&
        (!eventActorFilter.value || actor === eventActorFilter.value) &&
        (!stepKeyword || (item.stepId || '').toLowerCase().includes(stepKeyword)) &&
        (!startTime || Number(item.eventTime || 0) >= startTime) &&
        (!endTime || Number(item.eventTime || 0) <= endTime) &&
        (!eventFailedOnly.value || failed)
      );
    });
  });
  const canCancel = computed(
    () => !!task.value && !['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELED', 'EXPIRED'].includes(task.value.status)
  );
  const canPause = computed(
    () => !!task.value && ['RUNNING', 'WAITING_LOGIN', 'PREPARING_BROWSER'].includes(task.value.status)
  );
  const canRetry = computed(
    () => !!task.value && ((task.value.failedCount || 0) > 0 || (task.value.blockedCount || 0) > 0)
  );
  const canCreateFromResolve = computed(() => {
    if (!resolveResult.value?.executable) {
      return false;
    }
    if ((resolveResult.value.candidatePlans || []).length > 1 && !resolveResult.value.testPlanId) {
      return false;
    }
    if (resolveResult.value.confirmationRequired && !resolveConfirmed.value) {
      return false;
    }
    if (resolveResult.value.testPlanId) {
      return true;
    }
    if ((resolveResult.value.cases || []).length > 0) {
      return true;
    }
    return (resolveResult.value.total || 0) > 0 && !!resolveResult.value.confirmationRequired;
  });

  function formatTime(value?: number) {
    if (!value) {
      return '-';
    }
    return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
  }

  function eventLevelColor(level?: string) {
    if (level === 'ERROR') return 'red';
    if (level === 'WARN') return 'orange';
    return 'blue';
  }

  function verdictColor(verdict?: string) {
    if (verdict === 'PASSED') return 'green';
    if (verdict === 'PRODUCT_FAILED') return 'red';
    if (verdict === 'BLOCKED' || verdict === 'INCONCLUSIVE') return 'orange';
    if (verdict) return 'purple';
    return 'gray';
  }

  function taskOriginLabel(origin?: AiExecutionTask['taskOrigin']) {
    if (origin === 'PLATFORM_SCHEDULED') return '平台定时';
    if (origin === 'PLATFORM_MANUAL') return '平台手动';
    if (origin === 'PERSONAL_MCP') return '个人 MCP';
    return '-';
  }

  function executorChannelLabel(channel?: AiExecutionTask['executorChannel']) {
    if (channel === 'MODEL_API_RUNNER') return '模型执行器';
    if (channel === 'EXTERNAL_MCP_AGENT') return '外部 MCP Agent';
    return '-';
  }

  async function reloadTaskList() {
    if (!appStore.currentProjectId || executionTaskId.value) return;
    taskListLoading.value = true;
    try {
      const response = await searchAiExecutionTasks({
        projectId: appStore.currentProjectId,
        keyword: taskSearch.keyword.trim() || undefined,
        status: taskSearch.status,
        verdict: taskSearch.verdict,
        executorChannel: taskSearch.executorChannel,
        current: taskSearch.current,
        pageSize: taskSearch.pageSize,
      });
      taskList.value = response.items || [];
      taskListTotal.value = response.total || 0;
    } finally {
      taskListLoading.value = false;
    }
  }

  async function openTask(record: TableData) {
    await router.push({ query: { ...route.query, executionTaskId: String(record.id) } });
  }

  async function backToTaskList() {
    if (route.path === '/agent/execution/detail') {
      await router.push('/agent/queue');
      return;
    }
    const query = { ...route.query };
    delete query.executionTaskId;
    delete query.creating;
    delete query.caseIds;
    delete query.assetType;
    delete query.assetId;
    delete query.assetName;
    delete query.assetVersionId;
    await router.push({ query });
  }

  async function startCreating() {
    await router.push({ query: { creating: '1' } });
  }

  function hydrateExplicitCasesFromRoute() {
    if (!isCreating.value) return;
    const raw = Array.isArray(route.query.caseIds) ? route.query.caseIds.join(',') : String(route.query.caseIds || '');
    const caseIds = [
      ...new Set(
        raw
          .split(',')
          .map((item) => item.trim())
          .filter(Boolean)
      ),
    ];
    if (!caseIds.length) return;
    const confirmationRequired = caseIds.length > 20;
    resolveResult.value = {
      status: confirmationRequired ? 'WAITING_CONFIRMATION' : 'CREATED',
      executable: true,
      confirmationRequired,
      confirmationReason: confirmationRequired ? '执行范围超过 20 条，需要确认后继续' : undefined,
      projectId: appStore.currentProjectId,
      total: caseIds.length,
      selectionMode: 'MANUAL',
      message: `已接收 ${caseIds.length} 条已发布用例`,
      cases: caseIds.map((caseId) => ({ caseId, name: caseId })),
    };
    resolveConfirmed.value = false;
    lastResolvedPrompt.value = '执行刚发布的 AI 用例';
    messages.value = [
      {
        id: `${Date.now()}_a`,
        role: 'assistant',
        content: `已载入 ${caseIds.length} 条正式用例，请配置执行环境后创建任务。`,
      },
    ];
  }

  async function loadTask() {
    if (!executionTaskId.value) {
      task.value = undefined;
      return;
    }
    loading.value = true;
    try {
      task.value = await getAiExecutionTask(executionTaskId.value);
      if (task.value.providerId) {
        chatModelId.value = task.value.providerId;
      }
    } catch {
      Message.error(t('bugManagement.automationExecution.loadFailed'));
    } finally {
      loading.value = false;
    }
  }

  async function loadObservability() {
    if (!executionTaskId.value) {
      observability.value = undefined;
      observabilityError.value = '';
      return;
    }
    observabilityLoading.value = true;
    observabilityError.value = '';
    try {
      observability.value = await getAiExecutionObservability(executionTaskId.value);
    } catch (error: any) {
      observabilityError.value = error?.message || '加载执行治理信息失败，请稍后重试';
    } finally {
      observabilityLoading.value = false;
    }
  }

  async function loadEvents(reset = false) {
    if (!executionTaskId.value) {
      events.value = [];
      eventCursor.value = 0;
      return;
    }
    if (reset) {
      events.value = [];
      eventCursor.value = 0;
    }
    const response = await getAiExecutionEvents(executionTaskId.value, {
      cursor: eventCursor.value,
      limit: 100,
    });
    const nextEvents = response.events || [];
    if (nextEvents.length) {
      events.value = [...events.value, ...nextEvents];
      eventCursor.value = response.cursor || nextEvents[nextEvents.length - 1].sequence;
    }
  }

  async function loadArtifacts() {
    artifacts.value = executionTaskId.value ? await getAiExecutionArtifacts(executionTaskId.value) : [];
  }

  async function loadHumanRequests() {
    humanRequests.value = executionTaskId.value ? await getAiHumanRequests(executionTaskId.value) : [];
  }

  async function respondHuman(requestId: string, action: 'APPROVE' | 'REJECT' | 'CANCEL', response?: string) {
    if (!executionTaskId.value) return;
    await respondAiHumanRequest(executionTaskId.value, requestId, action, response);
    await Promise.all([loadTask(), loadHumanRequests(), loadEvents()]);
    const successMessage = {
      APPROVE: '已批准，任务恢复执行',
      REJECT: '已拒绝人工请求',
      CANCEL: '人工请求已取消',
    }[action];
    Message.success(successMessage);
  }
  function openHumanAnswer(request: AiHumanRequest) {
    humanAnswerRequestId.value = request.id;
    humanAnswer.value = '';
    humanAnswerVisible.value = true;
  }
  async function submitHumanAnswer(done: (closed: boolean) => void) {
    if (!humanAnswer.value.trim() || !executionTaskId.value) {
      Message.warning('补充输入不能为空');
      done(false);
      return;
    }
    try {
      await respondAiHumanRequest(
        executionTaskId.value,
        humanAnswerRequestId.value,
        'ANSWER',
        humanAnswer.value.trim()
      );
      await Promise.all([loadTask(), loadHumanRequests(), loadEvents()]);
      Message.success('已提交人工输入，任务恢复执行');
      done(true);
    } catch {
      done(false);
    }
  }

  async function refreshAll() {
    if (!executionTaskId.value) {
      await reloadTaskList();
      return;
    }
    await loadTask();
    await loadEvents(true);
    await loadArtifacts();
    await loadHumanRequests();
    await loadObservability();
  }

  function resetEvents() {
    // filter only; keep loaded events
  }

  async function sendPrompt() {
    const content = prompt.value.trim();
    if (!content) {
      return;
    }
    if (!appStore.currentProjectId) {
      Message.warning(t('bugManagement.automationExecution.projectRequired'));
      return;
    }
    messages.value.push({ id: `${Date.now()}_u`, role: 'user', content });
    lastResolvedPrompt.value = content;
    prompt.value = '';
    resolveLoading.value = true;
    try {
      const result = await resolveAiExecutionScope({
        projectId: appStore.currentProjectId,
        query: content,
      });
      resolveResult.value = result;
      resolveConfirmed.value = false;
      const summary = [
        result.message || t('bugManagement.automationExecution.resolveDone'),
        `total=${result.total ?? 0}`,
        result.testPlanId ? `plan=${result.testPlanId}` : 'plan=none',
        result.confirmationRequired ? `confirm=${result.confirmationReason}` : 'confirm=no',
        result.highRisk ? `highRisk=${(result.highRiskSignals || []).join('/')}` : '',
      ]
        .filter(Boolean)
        .join('；');
      messages.value.push({ id: `${Date.now()}_a`, role: 'assistant', content: summary });
      if (chatModelId.value) {
        localStorage.setItem('aiChatModel', chatModelId.value);
      }
    } catch (error: any) {
      messages.value.push({
        id: `${Date.now()}_a`,
        role: 'assistant',
        content: error?.message || t('bugManagement.automationExecution.resolveFailed'),
      });
    } finally {
      resolveLoading.value = false;
    }
  }

  async function createFromResolve() {
    if (!canCreateFromResolve.value || !resolveResult.value || !appStore.currentProjectId) {
      return;
    }
    createLoading.value = true;
    try {
      const hasPlan = !!resolveResult.value.testPlanId;
      const projectWide = !hasPlan && !!resolveResult.value.confirmationRequired;
      const caseIds =
        hasPlan || projectWide ? [] : (resolveResult.value.cases || []).map((item) => item.caseId).filter(Boolean);
      const preflight = await preflightAiExecution({
        projectId: appStore.currentProjectId,
        testPlanId: resolveResult.value.testPlanId,
        caseIds,
        environmentProfileId: draftForm.environmentProfileId,
        credentialReferenceId: draftForm.credentialReferenceId || undefined,
        modelProfileId: draftForm.modelProfileId,
        promptTemplateId: draftForm.promptTemplateId,
        runnerType: 'BROWSER',
        requiredCapabilities: ['BROWSER'],
        browserType: 'chromium',
        assetRefs: selectedAssetRefs.value.length
          ? selectedAssetRefs.value.map(({ assetType, assetId, versionId }) => ({ assetType, assetId, versionId }))
          : undefined,
        taskOrigin: 'PLATFORM_MANUAL',
      });
      if (preflight.status !== 'PASSED') {
        Message.error(`${preflight.blockedReason}: ${preflight.blockedDetail || ''} · ${preflight.traceId}`);
        return;
      }
      const taskCreated = await createAiExecutionTask({
        projectId: appStore.currentProjectId,
        testPlanId: resolveResult.value.testPlanId,
        caseIds,
        source: 'WORKBENCH',
        selectionMode: resolveResult.value.selectionMode || 'NATURAL_LANGUAGE',
        prompt: lastResolvedPrompt.value,
        resolvedFilter: resolveResult.value.resolvedFilter
          ? JSON.stringify(resolveResult.value.resolvedFilter)
          : undefined,
        policySnapshot: JSON.stringify({ screenshotMode: 'AFTER_STEP', fullPage: true }),
        confirmed: resolveResult.value.confirmationRequired ? resolveConfirmed.value : undefined,
        projectWide: projectWide || undefined,
        preflightId: preflight.id,
        environmentProfileId: draftForm.environmentProfileId,
        credentialReferenceId: draftForm.credentialReferenceId || undefined,
        modelProfileId: draftForm.modelProfileId,
        promptTemplateVersionId: preflight.promptTemplateVersionId,
        assetRefs: selectedAssetRefs.value.length
          ? selectedAssetRefs.value.map(({ assetType, assetId, versionId }) => ({ assetType, assetId, versionId }))
          : undefined,
        browserType: 'chromium',
        loginMode: 'MANUAL',
        idempotencyKey: `workbench-${appStore.currentProjectId}-${Date.now()}`,
      });
      Message.success(t('bugManagement.automationExecution.taskCreated'));
      await router.replace({
        query: { executionTaskId: taskCreated.id },
      });
    } finally {
      createLoading.value = false;
    }
  }

  async function handleConfirm() {
    if (!executionTaskId.value) return;
    actionLoading.value = true;
    try {
      task.value = await confirmAiExecutionTask(executionTaskId.value);
      await loadEvents();
      Message.success(t('common.success'));
    } finally {
      actionLoading.value = false;
    }
  }

  async function handleLoginReady() {
    if (!executionTaskId.value) return;
    actionLoading.value = true;
    try {
      task.value = await loginReadyAiExecutionTask(executionTaskId.value);
      await loadEvents();
      Message.success(t('common.success'));
    } finally {
      actionLoading.value = false;
    }
  }

  async function handlePause() {
    if (!executionTaskId.value) return;
    actionLoading.value = true;
    try {
      task.value = await pauseAiExecutionTask(executionTaskId.value);
      await loadEvents();
      Message.success(t('bugManagement.automationExecution.paused'));
    } finally {
      actionLoading.value = false;
    }
  }

  async function handleCancel() {
    if (!executionTaskId.value) return;
    actionLoading.value = true;
    try {
      task.value = await cancelAiExecutionTask(executionTaskId.value);
      await loadEvents();
      Message.success(t('bugManagement.automationExecution.canceled'));
    } finally {
      actionLoading.value = false;
    }
  }

  async function handleRetry() {
    if (!executionTaskId.value) return;
    actionLoading.value = true;
    try {
      task.value = await retryAiExecutionTask(executionTaskId.value);
      await loadEvents();
      Message.success(t('common.success'));
    } finally {
      actionLoading.value = false;
    }
  }

  function downloadEvents() {
    const lines = filteredEvents.value.map(
      (item) => `[${formatTime(item.eventTime)}] [${item.level}] [${item.eventType}] ${item.message || ''}`
    );
    const blob = new Blob([lines.join('\n')], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `ai-execution-${executionTaskId.value || 'log'}.txt`;
    link.click();
    URL.revokeObjectURL(url);
  }

  function startPolling() {
    window.clearInterval(pollTimer);
    if (!executionTaskId.value) {
      return;
    }
    pollTimer = window.setInterval(async () => {
      try {
        await loadTask();
        await loadEvents(false);
        await loadArtifacts();
      } catch {
        // ignore transient poll errors
      }
    }, 3000);
  }

  watch(
    executionTaskId,
    async () => {
      await refreshAll();
      startPolling();
    },
    { immediate: true }
  );

  watch(() => [route.query.creating, route.query.caseIds, appStore.currentProjectId], hydrateExplicitCasesFromRoute, {
    immediate: true,
  });

  watch(
    () => appStore.currentProjectId,
    () => {
      selectedAssetRefs.value = [];
      draftForm.environmentId = '';
      draftForm.environmentProfileId = '';
      draftForm.credentialReferenceId = '';
      draftForm.modelProfileId = '';
      draftForm.promptTemplateId = '';
      loadEnvironmentOptions();
      loadExecutionProfiles();
    },
    { immediate: true }
  );

  watch(
    selectedAssetRef,
    (value) => {
      if (value) addAssetRef(value);
    },
    { immediate: true }
  );

  watch(
    () => [taskSearch.status, taskSearch.verdict, taskSearch.executorChannel],
    () => {
      taskSearch.current = 1;
      reloadTaskList();
    }
  );

  watch(
    () => modelOptions.value,
    (vals) => {
      if (!vals.length) return;
      if (!vals.some((item) => item.value === chatModelId.value)) {
        chatModelId.value = vals[0].value;
      }
    },
    { immediate: true }
  );

  onMounted(() => {
    aiStore.getAISourceNameList();
    loadExecutionProfiles();
  });

  onBeforeUnmount(() => {
    window.clearInterval(pollTimer);
  });
</script>

<style scoped>
  .automation-execution-page {
    display: flex;
    padding: 16px;
    height: 100%;
    min-height: calc(100vh - 128px);
    background: var(--color-fill-2);
    flex-direction: column;
    gap: 12px;
  }
  .automation-execution-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 12px;
  }
  .automation-execution-workbench {
    display: grid;
    grid-template-columns: 35% 1fr;
    gap: 12px;
    min-height: 0;
    flex: 1;
  }
  .task-center-panel {
    overflow: auto;
    padding: 16px;
    min-height: 0;
    border-radius: 8px;
    background: var(--color-bg-1);
    flex: 1;
  }
  .automation-panel {
    display: flex;
    padding: 16px;
    min-height: 0;
    border-radius: 8px;
    background: var(--color-bg-1);
    flex-direction: column;
  }
  .panel-title {
    margin-bottom: 12px;
    font-size: 14px;
    font-weight: 600;
    color: var(--color-text-1);
  }
  .panel-subtitle {
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-2);
  }
  .message-list,
  .event-list {
    overflow: auto;
    margin-bottom: 12px;
    padding: 8px;
    min-height: 120px;
    max-height: 220px;
    border: 1px solid var(--color-border-2);
    border-radius: 6px;
    flex: 1;
  }
  .evidence-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 8px;
  }
  .evidence-item {
    padding: 8px;
    min-width: 0;
    border: 1px solid var(--color-border-2);
    border-radius: 4px;
  }
  .scope-preview {
    overflow: auto;
    max-height: 320px;
  }
  .message-item {
    margin-bottom: 8px;
    padding: 8px;
    border-radius: 6px;
    background: var(--color-fill-2);
  }
  .message-item.user {
    background: rgb(var(--primary-1));
  }
  .message-role {
    margin-bottom: 4px;
    font-size: 12px;
    color: var(--color-text-3);
  }
  .event-item {
    display: grid;
    align-items: start;
    padding: 6px 0;
    font-size: 12px;
    border-bottom: 1px solid var(--color-border-2);
    grid-template-columns: 56px 150px 140px 1fr;
    gap: 8px;
  }
  .event-message {
    word-break: break-word;
    color: var(--color-text-1);
  }
  @media (max-width: 1200px) {
    .automation-execution-workbench {
      grid-template-columns: 1fr;
    }
  }
</style>
