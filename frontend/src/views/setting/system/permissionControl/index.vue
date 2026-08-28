<template>
  <div class="permission-control-page">
    <a-tabs v-model:active-key="activeTab" lazy-load>
      <a-tab-pane v-if="hasTabVisible('PERMISSION_ROLE_SETTING_TAB', ['SYSTEM'])" key="role" title="角色设置">
        <a-card :bordered="false">
          <template #title>
            <div class="card-title">
              <div>
                <div>角色列表</div>
                <div class="muted mt-1">角色负责权限，成员关系仅负责用户归属；管理员角色为只读保护角色。</div>
              </div>
              <a-button
                v-if="canAddRole"
                v-visible-permission="{
                  code: 'PERMISSION_ROLE_ADD_BUTTON',
                  permissions: ['SYSTEM_PERMISSION_CONTROL:READ+ADD'],
                  typeList: ['SYSTEM'],
                }"
                type="primary"
                @click="openRoleEditor()"
              >
                新增角色
              </a-button>
            </div>
          </template>

          <div class="mb-4 flex justify-end">
            <a-input-search v-model="roleKeyword" class="w-[260px]" allow-clear placeholder="搜索角色名称" />
          </div>
          <a-table :data="filteredRoles" :pagination="false" row-key="id">
            <template #columns>
              <a-table-column title="角色名称" data-index="name" />
              <a-table-column title="权限范围" :width="160">
                <template #cell="{ record }">{{ roleScopeText(record.type) }}</template>
              </a-table-column>
              <a-table-column title="启用状态" :width="130">
                <template #cell="{ record }">
                  <a-switch
                    v-operable-permission="{
                      code: 'PERMISSION_ROLE_ENABLE_BUTTON',
                      permissions: ['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'],
                      typeList: ['SYSTEM'],
                    }"
                    :model-value="record.enabled !== false"
                    :disabled="isProtectedAdmin(record)"
                    @change="(value) => handleRoleEnable(record.id, Boolean(value))"
                  />
                </template>
              </a-table-column>
              <a-table-column title="编辑" :width="270" fixed="right">
                <template #cell="{ record }">
                  <a-space>
                    <a-link @click="openRoleEditor(record)">{{
                      isProtectedAdmin(record) || !canUpdateRole ? '查看' : '编辑'
                    }}</a-link>
                    <a-link @click="openRoleMembers(record)">成员</a-link>
                    <a-link
                      v-if="!isProtectedAdmin(record)"
                      v-visible-permission="{
                        code: 'PERMISSION_ROLE_DELETE_BUTTON',
                        permissions: ['SYSTEM_PERMISSION_CONTROL:READ+DELETE'],
                        typeList: ['SYSTEM'],
                      }"
                      status="danger"
                      @click="confirmDeleteRole(record)"
                    >
                      删除
                    </a-link>
                  </a-space>
                </template>
              </a-table-column>
            </template>
          </a-table>
        </a-card>
      </a-tab-pane>

      <a-tab-pane v-if="hasTabVisible('PERMISSION_FLOW_CONTROL_TAB', ['SYSTEM'])" key="flow" title="流程控制">
        <div class="flow-layout">
          <a-card class="flow-card flow-list-card" :bordered="false">
            <template #title>
              <div class="card-title">
                <span>缺陷流程</span>
                <a-button
                  v-visible-permission="{
                    code: 'PERMISSION_FLOW_ADD_BUTTON',
                    permissions: ['SYSTEM_PERMISSION_CONTROL:READ+ADD'],
                    typeList: ['SYSTEM'],
                  }"
                  type="primary"
                  @click="openFlowModal"
                >
                  新建流程
                </a-button>
              </div>
            </template>

            <div class="flow-list-scroll">
              <a-list :bordered="false">
                <a-list-item
                  v-for="flow in flows"
                  :key="flow.id"
                  class="flow-list-item"
                  :class="{ selected: selectedFlow?.id === flow.id }"
                  @click="selectFlow(flow)"
                >
                  <div class="flow-list-item-content">
                    <div class="flow-list-item-info">
                      <div class="flow-list-item-name" :title="flow.name">{{ flow.name }}</div>
                      <div class="flow-list-item-description" :title="flow.description || flow.code">
                        {{ flow.description || flow.code }}
                      </div>
                    </div>
                    <div class="flow-list-item-actions">
                      <a-tag v-if="flow.activeForNew" color="blue">使用中</a-tag>
                      <a-tag v-if="flow.enabled === false" color="red">已禁用</a-tag>
                      <a-tag
                        :color="
                          flow.lifecycle === 'PUBLISHED' ? 'green' : flow.lifecycle === 'ARCHIVED' ? 'gray' : 'orange'
                        "
                      >
                        {{
                          flow.lifecycle === 'PUBLISHED' ? '已发布' : flow.lifecycle === 'ARCHIVED' ? '已归档' : '草稿'
                        }}
                        v{{ flow.version || 1 }}
                      </a-tag>
                      <a-switch
                        v-if="flow.lifecycle !== 'ARCHIVED' && canUpdateRole"
                        v-operable-permission="{
                          code: 'PERMISSION_FLOW_SAVE_BUTTON',
                          permissions: ['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'],
                          typeList: ['SYSTEM'],
                        }"
                        :model-value="flow.enabled !== false"
                        size="small"
                        @click.stop
                        @change="(value) => handleFlowEnable(flow, Boolean(value))"
                      />
                      <a-link
                        v-if="isFlowDeleteCandidate(flow)"
                        v-visible-permission="{
                          code: 'PERMISSION_FLOW_DELETE_BUTTON',
                          permissions: ['SYSTEM_PERMISSION_CONTROL:READ+DELETE'],
                          typeList: ['SYSTEM'],
                        }"
                        status="danger"
                        @click.stop="confirmDeleteFlow(flow)"
                        >删除</a-link
                      >
                    </div>
                  </div>
                </a-list-item>
              </a-list>
            </div>
          </a-card>

          <a-card class="flow-card matrix-card" :bordered="false">
            <template #title>
              <div class="card-title">
                <div>
                  <div>{{ selectedFlow?.name || '流转矩阵' }}</div>
                  <div class="muted">系统全局生效；草稿和已发布流程均可修改，已归档流程只读。</div>
                </div>
                <a-space wrap>
                  <a-button v-if="canUpdateRole" :disabled="!isEditableFlow" @click="openStatusModal()"
                    >添加状态</a-button
                  >
                  <a-button v-if="canUpdateRole" :disabled="!isEditableFlow || !designerDirty" @click="saveDesigner"
                    >保存流程</a-button
                  >
                  <a-button :disabled="!selectedFlow?.id" @click="validateDesigner">校验</a-button>
                  <a-button v-if="isDraftFlow && canUpdateRole" type="primary" @click="publishDesigner">发布</a-button>
                  <a-button
                    v-if="
                      canUpdateRole &&
                      selectedFlow?.lifecycle === 'PUBLISHED' &&
                      selectedFlow.enabled !== false &&
                      !selectedFlow.activeForNew
                    "
                    type="primary"
                    @click="activateDesigner"
                    >开启使用</a-button
                  >
                  <a-button v-if="canAddRole && selectedFlow?.id && !isDraftFlow" @click="copyDesigner"
                    >复制新版本</a-button
                  >
                  <a-button
                    v-if="canUpdateRole && selectedFlow?.lifecycle === 'PUBLISHED' && !selectedFlow.activeForNew"
                    status="warning"
                    @click="archiveDesigner"
                    >归档</a-button
                  >
                  <a-button v-if="canUpdateRole && selectedFlow?.activeForNew" @click="openMigrationPreview"
                    >关联历史缺陷</a-button
                  >
                  <a-button v-if="selectedFlow?.lifecycle === 'ARCHIVED'" disabled>已归档</a-button>
                </a-space>
              </div>
            </template>

            <a-alert class="mb-4" type="info">
              点击“禁止”可新增流转；选择“允许”后在右侧配置角色。每条启用流转必须配置可执行角色才能发布。
            </a-alert>

            <div class="mb-3 flex flex-wrap gap-2">
              <a-tag
                v-for="status in flowMatrix.statuses"
                :key="status.id"
                :color="status.initial ? 'blue' : status.terminal ? 'green' : undefined"
                :checkable="isEditableFlow && canUpdateRole"
                @check="() => isEditableFlow && canUpdateRole && openStatusModal(status)"
              >
                {{ status.name }}{{ status.initial ? '（初始）' : '' }}{{ status.terminal ? '（结束）' : '' }}
              </a-tag>
            </div>

            <div class="matrix-wrap">
              <table class="flow-matrix">
                <thead>
                  <tr>
                    <th>当前 \ 目标</th>
                    <th v-for="status in flowMatrix.statuses" :key="status.id">{{ status.name }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="fromStatus in flowMatrix.statuses" :key="fromStatus.id">
                    <th>{{ fromStatus.name }}</th>
                    <td v-for="toStatus in flowMatrix.statuses" :key="toStatus.id">
                      <template v-if="fromStatus.id === toStatus.id">-</template>
                      <button
                        v-else-if="findTransition(fromStatus.id, toStatus.id)"
                        class="matrix-cell cell-allow"
                        :class="{ active: selectedTransition?.id === findTransition(fromStatus.id, toStatus.id)?.id }"
                        type="button"
                        @click="selectTransition(fromStatus.id, toStatus.id)"
                      >
                        <span>允许</span>
                        <small>{{ getTransitionAuthSummary(findTransition(fromStatus.id, toStatus.id)?.id) }}</small>
                      </button>
                      <button
                        v-else-if="isEditableFlow && canUpdateRole"
                        class="matrix-cell cell-deny"
                        type="button"
                        @click="addTransition(fromStatus.id, toStatus.id)"
                      >
                        禁止
                      </button>
                      <span v-else class="cell-deny">禁止</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </a-card>

          <a-card class="flow-card" :bordered="false">
            <template #title>
              <div class="card-title">
                <span>流转规则</span>
                <a-space wrap>
                  <a-tooltip :content="isArchivedFlow ? '归档流程保留历史配置，不再同步岗位' : '同步平台当前企微岗位'">
                    <a-button
                      v-if="canUpdateRole && selectedFlow?.id"
                      :disabled="isArchivedFlow"
                      @click="openWecomPositionPreview"
                      >同步企微岗位</a-button
                    >
                  </a-tooltip>
                  <a-button v-if="canAddRole" :disabled="!isEditableFlow" @click="() => openFlowRoleModal()"
                    >添加流程角色</a-button
                  >
                  <a-button
                    v-operable-permission="{
                      code: 'PERMISSION_FLOW_SAVE_BUTTON',
                      permissions: ['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'],
                      typeList: ['SYSTEM'],
                    }"
                    type="primary"
                    :disabled="!selectedFlow?.id || !isEditableFlow"
                    @click="saveTransitionPermissions"
                  >
                    保存授权
                  </a-button>
                </a-space>
              </div>
            </template>

            <a-empty v-if="!selectedTransition" description="请选择矩阵中的允许流转" />
            <template v-else>
              <a-descriptions :column="1" bordered size="small">
                <a-descriptions-item label="当前状态">{{
                  getStatusName(selectedTransition.fromId)
                }}</a-descriptions-item>
                <a-descriptions-item label="目标状态">{{ getStatusName(selectedTransition.toId) }}</a-descriptions-item>
              </a-descriptions>

              <a-button
                v-if="isEditableFlow && canUpdateRole"
                class="mt-3"
                status="danger"
                @click="removeSelectedTransition"
                >删除此流转</a-button
              >

              <div class="flow-role-table-wrap">
                <a-table :data="flowRoles" :pagination="false" size="small" row-key="id" sticky-header>
                  <template #columns>
                    <a-table-column title="流程角色">
                      <template #cell="{ record }">
                        <div>{{ record.name }}</div>
                        <div class="muted">{{ getWorkflowRoleMapping(record) }}</div>
                      </template>
                    </a-table-column>
                    <a-table-column :width="76" title="可见">
                      <template #cell="{ record }">
                        <a-checkbox
                          :model-value="getRolePermissionValue(record.id, 'visible')"
                          :disabled="!isEditableFlow || !canUpdateRole"
                          @change="(value) => updateRolePermission(record.id, 'visible', Boolean(value))"
                        />
                      </template>
                    </a-table-column>
                    <a-table-column :width="76" title="可执行">
                      <template #cell="{ record }">
                        <a-checkbox
                          :model-value="getRolePermissionValue(record.id, 'operable')"
                          :disabled="!isEditableFlow || !canUpdateRole"
                          @change="(value) => updateRolePermission(record.id, 'operable', Boolean(value))"
                        />
                      </template>
                    </a-table-column>
                    <a-table-column :width="110" title="操作">
                      <template #cell="{ record }">
                        <a-space>
                          <a-link
                            v-if="isEditableFlow && canUpdateRole"
                            v-visible-permission="{
                              code: 'PERMISSION_FLOW_ROLE_UPDATE_BUTTON',
                              permissions: ['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'],
                              typeList: ['SYSTEM'],
                            }"
                            @click="openFlowRoleModal(record)"
                            >编辑</a-link
                          >
                          <a-link
                            v-if="isEditableFlow && canDeleteRole"
                            v-visible-permission="{
                              code: 'PERMISSION_FLOW_ROLE_DELETE_BUTTON',
                              permissions: ['SYSTEM_PERMISSION_CONTROL:READ+DELETE'],
                              typeList: ['SYSTEM'],
                            }"
                            status="danger"
                            @click="confirmDeleteFlowRole(record)"
                            >删除</a-link
                          >
                        </a-space>
                      </template>
                    </a-table-column>
                  </template>
                </a-table>
              </div>
            </template>
          </a-card>
        </div>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:visible="flowModalVisible" title="新建缺陷流程" @ok="handleCreateFlow">
      <a-form :model="flowForm" layout="vertical">
        <a-form-item field="name" label="流程名称" required>
          <a-input v-model="flowForm.name" placeholder="例如：缺陷分诊流程" />
        </a-form-item>
        <a-form-item field="code" label="流程编码" required>
          <a-input v-model="flowForm.code" placeholder="例如：BUG_TRIAGE_FLOW" />
        </a-form-item>
        <a-form-item field="copyFromFlowId" label="从已有流程复制">
          <a-select v-model="flowForm.copyFromFlowId" allow-clear placeholder="不复制，仅创建空流程">
            <a-option v-for="flow in flows" :key="flow.id" :value="flow.id">{{ flow.name }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item field="description" label="说明">
          <a-textarea v-model="flowForm.description" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:visible="wecomPreviewVisible"
      title="同步企微岗位"
      :width="760"
      :ok-loading="wecomSyncLoading"
      ok-text="确认同步"
      @ok="syncWecomPositions"
    >
      <a-alert class="mb-3" type="info"
        >仅同步平台已保存的真实企微岗位；同步后仍需在流转矩阵中配置可见和可操作权限。</a-alert
      >
      <a-table
        :data="wecomPositions"
        :loading="wecomPreviewLoading"
        :pagination="false"
        row-key="sourceKey"
        size="small"
      >
        <template #columns>
          <a-table-column title="岗位" data-index="position" />
          <a-table-column title="人数" data-index="memberCount" :width="100" />
          <a-table-column title="同步状态" :width="120">
            <template #cell="{ record }"
              ><a-tag :color="record.existing ? 'green' : 'blue'">{{
                record.existing ? '已存在' : '待新增'
              }}</a-tag></template
            >
          </a-table-column>
        </template>
      </a-table>
      <a-divider v-if="wecomSyncResults.length">最近同步结果</a-divider>
      <a-table
        v-if="wecomSyncResults.length"
        :data="wecomSyncResults"
        :pagination="false"
        row-key="batchId"
        size="small"
      >
        <template #columns>
          <a-table-column title="时间" :width="180"
            ><template #cell="{ record }">{{ new Date(record.createTime).toLocaleString() }}</template></a-table-column
          >
          <a-table-column title="总数" data-index="total" :width="80" />
          <a-table-column title="新增" data-index="created" :width="80" />
          <a-table-column title="更新" data-index="updated" :width="80" />
          <a-table-column title="停用" data-index="disabled" :width="80" />
        </template>
      </a-table>
    </a-modal>

    <a-modal
      v-model:visible="statusModalVisible"
      :title="statusForm.id ? '编辑状态' : '添加状态'"
      :ok-button-props="{ disabled: !isEditableFlow || !canUpdateRole }"
      @ok="saveStatusDraft"
    >
      <a-form :model="statusForm" layout="vertical">
        <a-form-item label="状态名称" required><a-input v-model="statusForm.name" /></a-form-item>
        <a-form-item label="稳定编码" required
          ><a-input v-model="statusForm.code" :disabled="Boolean(statusForm.id)"
        /></a-form-item>
        <a-form-item label="说明"><a-textarea v-model="statusForm.remark" /></a-form-item>
        <a-space>
          <a-checkbox v-model="statusForm.initial">初始状态（唯一）</a-checkbox>
          <a-checkbox v-model="statusForm.terminal">结束状态</a-checkbox>
          <a-checkbox v-model="statusForm.enabled">启用</a-checkbox>
        </a-space>
      </a-form>
      <template #footer>
        <a-button v-if="statusForm.id && isEditableFlow && canUpdateRole" status="danger" @click="deleteStatusDraft"
          >删除状态</a-button
        >
        <a-button @click="statusModalVisible = false">取消</a-button>
        <a-button type="primary" :disabled="!isEditableFlow || !canUpdateRole" @click="saveStatusDraft">确定</a-button>
      </template>
    </a-modal>

    <a-modal
      v-model:visible="migrationVisible"
      title="批量关联历史缺陷"
      :width="1080"
      :ok-loading="migrationLoading"
      :ok-button-props="{ disabled: !migrationBugIds.length || !migrationMappingsComplete }"
      ok-text="确认执行迁移"
      @ok="executeMigration"
    >
      <a-alert class="mb-4" type="warning">
        此操作只关联尚未绑定流程版本的历史缺陷到当前使用中的流程；请选择需要处理的项目并确认状态映射。
      </a-alert>
      <a-descriptions v-if="migrationPreview" :column="2" bordered>
        <a-descriptions-item label="目标版本">v{{ migrationPreview.targetVersion }}</a-descriptions-item>
        <a-descriptions-item label="影响缺陷">{{ migrationPreview.affectedBugCount }}</a-descriptions-item>
        <a-descriptions-item label="自动映射">{{
          Object.keys(migrationPreview.suggestedMappings).length
        }}</a-descriptions-item>
        <a-descriptions-item label="未映射状态">{{ migrationUnresolvedStatuses.length }}</a-descriptions-item>
      </a-descriptions>
      <div v-if="migrationUnresolvedStatuses.length" class="mt-4">
        <a-alert class="mb-2" type="warning">请为以下历史状态选择目标状态，全部映射后方可执行。</a-alert>
        <a-form :model="migrationMappings" layout="vertical">
          <a-form-item
            v-for="status in migrationUnresolvedStatuses"
            :key="status.id"
            :label="`${status.name}（${status.bugCount} 个缺陷，${status.projectCount} 个项目）`"
          >
            <a-select v-model="migrationMappings[status.id]" placeholder="选择目标状态" allow-clear>
              <a-option
                v-for="targetStatus in migrationPreview?.targetStatuses || []"
                :key="targetStatus.id"
                :value="targetStatus.id"
                >{{ targetStatus.name }}</a-option
              >
            </a-select>
          </a-form-item>
        </a-form>
      </div>
      <div class="mt-4 grid grid-cols-4 gap-3">
        <a-input-search
          v-model="migrationCandidateQuery.keyword"
          allow-clear
          placeholder="缺陷 ID、编号或标题"
          @search="searchMigrationCandidates"
          @clear="searchMigrationCandidates"
        />
        <a-select
          v-model="migrationCandidateQuery.projectIds"
          multiple
          allow-clear
          placeholder="筛选项目"
          @change="searchMigrationCandidates"
        >
          <a-option
            v-for="project in migrationPreview?.projects || []"
            :key="project.projectId"
            :value="project.projectId"
          >
            {{ project.projectName }}
          </a-option>
        </a-select>
        <a-select
          v-model="migrationCandidateQuery.sourceStatusIds"
          multiple
          allow-clear
          placeholder="筛选原状态"
          @change="searchMigrationCandidates"
        >
          <a-option v-for="status in migrationSourceStatuses" :key="status.id" :value="status.id">
            {{ status.name }}（{{ status.bugCount }}）
          </a-option>
        </a-select>
        <a-range-picker v-model="migrationCreateTimeRange" show-time @change="searchMigrationCandidates" />
      </div>
      <div class="mt-3 flex items-center justify-between">
        <span class="muted">共 {{ migrationCandidateTotal }} 条，已跨页选择 {{ migrationBugIds.length }} 条</span>
        <a-space>
          <a-button @click="selectAllMigrationCandidates">选择当前筛选全部</a-button>
          <a-button @click="migrationBugIds = []">清空选择</a-button>
        </a-space>
      </div>
      <a-alert v-if="migrationBatch" class="mt-4" :type="migrationBatch.failedCount ? 'warning' : 'success'">
        批次 {{ migrationBatch.id }}：{{ migrationBatch.status }}；成功 {{ migrationBatch.successCount }}，失败
        {{ migrationBatch.failedCount }}，跳过 {{ migrationBatch.skippedCount }}
      </a-alert>
      <a-space
        v-if="migrationBatch && ['COMPLETED', 'FAILED', 'PARTIAL_SUCCESS'].includes(migrationBatch.status)"
        class="mt-2"
      >
        <a-button v-if="['FAILED', 'PARTIAL_SUCCESS'].includes(migrationBatch.status)" @click="resumeMigrationBatch"
          >重试失败项</a-button
        ><a-button status="danger" @click="rollbackMigrationBatch">回滚已迁移项</a-button>
      </a-space>
      <a-table
        v-if="migrationBatches.length"
        class="mt-3"
        :data="migrationBatches"
        :pagination="false"
        row-key="id"
        size="mini"
      >
        <template #columns>
          <a-table-column title="最近迁移批次" data-index="id" ellipsis tooltip />
          <a-table-column title="状态" data-index="status" :width="140" />
          <a-table-column title="成功/失败" :width="120">
            <template #cell="{ record }">{{ record.successCount || 0 }}/{{ record.failedCount || 0 }}</template>
          </a-table-column>
          <a-table-column title="操作" :width="80">
            <template #cell="{ record }"><a-link @click="viewMigrationBatch(record.id)">查看</a-link></template>
          </a-table-column>
        </template>
      </a-table>
      <a-table
        v-if="migrationPreview"
        v-model:selected-keys="migrationBugIds"
        class="mt-4"
        :data="migrationCandidates"
        :loading="migrationCandidateLoading"
        :pagination="migrationCandidatePagination"
        :row-selection="{ type: 'checkbox', showCheckedAll: true }"
        row-key="id"
        size="small"
        @page-change="changeMigrationCandidatePage"
        @page-size-change="changeMigrationCandidatePageSize"
      >
        <template #columns>
          <a-table-column title="缺陷编号" data-index="num" :width="110" />
          <a-table-column title="标题" data-index="title" ellipsis tooltip />
          <a-table-column title="项目" data-index="projectName" :width="180" ellipsis tooltip />
          <a-table-column title="原状态" data-index="sourceStatusName" :width="140" />
          <a-table-column title="创建时间" :width="180">
            <template #cell="{ record }">{{ new Date(record.createTime).toLocaleString() }}</template>
          </a-table-column>
        </template>
      </a-table>
    </a-modal>

    <a-modal
      v-model:visible="flowRoleModalVisible"
      :title="flowRoleForm.id ? '编辑流程角色' : '添加流程角色'"
      @ok="handleSaveFlowRole"
    >
      <a-form :model="flowRoleForm" layout="vertical">
        <a-form-item field="name" label="角色名称" required>
          <a-input v-model="flowRoleForm.name" placeholder="例如：当前处理人" />
        </a-form-item>
        <a-form-item field="code" label="角色编码" required>
          <a-input v-model="flowRoleForm.code" placeholder="例如：BUG_HANDLER" />
        </a-form-item>
        <a-form-item field="roleType" label="角色类型">
          <a-radio-group v-model="flowRoleForm.roleType">
            <a-radio value="FIELD_USER">业务字段用户</a-radio>
            <a-radio value="SYSTEM_ROLE">系统角色</a-radio>
            <a-radio value="POSITION">企微职位</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="flowRoleForm.roleType === 'FIELD_USER'" field="fieldKey" label="业务字段">
          <a-select v-model="flowRoleForm.fieldKey">
            <a-option value="handle_user">当前处理人</a-option>
            <a-option value="create_user">创建人</a-option>
          </a-select>
        </a-form-item>
        <a-form-item v-else-if="flowRoleForm.roleType === 'SYSTEM_ROLE'" field="roleId" label="系统角色">
          <a-select v-model="flowRoleForm.roleId" allow-search>
            <a-option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item v-else field="fieldKey" label="职位关键词">
          <a-input v-model="flowRoleForm.fieldKey" placeholder="多个关键词用 | 分隔，例如：测试|质量|QA" />
          <template #extra>匹配企微同步的职位名称；填写 * 表示所有非空职位。</template>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:visible="roleMemberVisible"
      :width="820"
      :title="`角色成员 - ${currentMemberRole?.name || ''}`"
      :footer="false"
      unmount-on-close
    >
      <a-alert v-if="memberReadonly" class="mb-4" type="info">当前角色成员仅可查看，不能添加或移除。</a-alert>
      <a-form-item
        v-if="currentMemberRole?.type !== 'SYSTEM'"
        :label="currentMemberRole?.type === 'ORGANIZATION' ? '组织' : '项目'"
      >
        <a-select
          v-model="memberSourceId"
          allow-clear
          allow-search
          :filter-option="false"
          :loading="memberScopeLoading"
          :placeholder="currentMemberRole?.type === 'ORGANIZATION' ? '请选择组织' : '请选择项目'"
          @search="loadRoleMemberScopeOptions"
          @change="reloadRoleMemberScope"
        >
          <a-option v-for="item in memberScopeOptions" :key="item.id" :value="item.id"
            >{{ item.name }}（{{ item.id }}）</a-option
          >
        </a-select>
      </a-form-item>
      <div class="mb-4 flex items-center justify-between gap-3">
        <a-input-search
          v-model="memberQuery.keyword"
          class="w-[300px]"
          allow-clear
          placeholder="搜索成员名称、邮箱、手机号或 ID"
          @search="searchRoleMembers"
          @clear="searchRoleMembers"
        />
        <a-space v-if="!memberReadonly">
          <a-button v-if="currentMemberRole?.type !== 'PROJECT'" @click="openPositionRules">岗位规则</a-button>
          <a-button
            v-visible-permission="{
              code: 'PERMISSION_ROLE_ASSIGN_MEMBER_BUTTON',
              permissions: ['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'],
              typeList: ['SYSTEM'],
            }"
            type="primary"
            @click="openAddRoleMembers"
          >
            添加成员
          </a-button>
        </a-space>
      </div>
      <a-table
        :data="roleMembers"
        :loading="roleMemberLoading"
        :pagination="memberPagination"
        row-key="id"
        @page-change="changeMemberPage"
        @page-size-change="changeMemberPageSize"
      >
        <template #columns>
          <a-table-column title="成员名称" data-index="name" :width="170" ellipsis tooltip />
          <a-table-column title="用户 ID" data-index="userId" :width="190" ellipsis tooltip />
          <a-table-column title="邮箱" data-index="email" :width="220" ellipsis tooltip />
          <a-table-column title="手机号" data-index="phone" :width="140" ellipsis tooltip />
          <a-table-column v-if="!memberReadonly" title="操作" :width="80" fixed="right">
            <template #cell="{ record }">
              <a-link
                v-operable-permission="{
                  code: 'PERMISSION_ROLE_ASSIGN_MEMBER_BUTTON',
                  permissions: ['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'],
                  typeList: ['SYSTEM'],
                }"
                status="danger"
                @click="confirmRemoveRoleMember(record)"
              >
                移除
              </a-link>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-modal>

    <a-modal
      v-model:visible="addRoleMemberVisible"
      title="添加角色成员"
      :ok-button-props="{ disabled: selectedMemberIds.length === 0 }"
      unmount-on-close
      @ok="handleAddRoleMembers"
      @cancel="selectedMemberIds = []"
    >
      <a-form :model="{ selectedMemberIds }" layout="vertical">
        <a-form-item label="成员" required>
          <a-select
            v-model="selectedMemberIds"
            multiple
            allow-search
            :filter-option="false"
            :loading="memberOptionLoading"
            placeholder="输入名称或邮箱搜索成员"
            @search="loadRoleMemberOptions"
          >
            <a-option v-for="option in roleMemberOptions" :key="option.id" :value="option.id">
              <div>{{ option.name }}</div>
              <div class="muted text-xs">{{ option.email || option.id }}</div>
            </a-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:visible="positionRuleVisible"
      title="按组织岗位分配成员"
      :ok-loading="positionRuleLoading"
      unmount-on-close
      @before-ok="savePositionRule"
    >
      <a-form :model="positionRuleForm" layout="vertical">
        <a-form-item field="organizationId" label="组织" required>
          <a-select v-model="positionRuleForm.organizationId" allow-search placeholder="请选择组织">
            <a-option v-for="item in memberScopeOptions" :key="item.id" :value="item.id">{{ item.name }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item field="departmentId" label="部门 ID">
          <a-input v-model="positionRuleForm.departmentId" allow-clear placeholder="可选；限定到组织内部门" />
        </a-form-item>
        <a-form-item field="positionId" label="岗位 ID" required>
          <a-input v-model="positionRuleForm.positionId" allow-clear placeholder="请输入岗位 ID" />
        </a-form-item>
        <a-form-item field="syncMode" label="同步方式">
          <a-radio-group v-model="positionRuleForm.syncMode">
            <a-radio value="MANUAL">保存时同步</a-radio>
            <a-radio value="AUTO">自动同步</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
      <a-divider>现有规则</a-divider>
      <a-table :data="positionRules" :pagination="false" size="small" row-key="id">
        <template #columns>
          <a-table-column title="组织" data-index="organizationId" ellipsis tooltip />
          <a-table-column title="部门" data-index="departmentId" ellipsis tooltip />
          <a-table-column title="岗位" data-index="positionId" ellipsis tooltip />
          <a-table-column title="同步" data-index="syncMode" :width="90" />
        </template>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message, Modal } from '@arco-design/web-vue';

  import {
    activatePermissionControlFlow,
    addPermissionControlFlow,
    addPermissionControlFlowRole,
    addPermissionControlRoleMembers,
    archivePermissionControlFlow,
    assignPermissionControlRoleByPosition,
    copyPermissionControlFlow,
    deletePermissionControlFlow,
    deletePermissionControlFlowRole,
    deletePermissionControlRole,
    enablePermissionControlFlow,
    enablePermissionControlRole,
    getPermissionControlFlowDesigner,
    getPermissionControlFlowImpact,
    getPermissionControlFlowMigrationBatch,
    getPermissionControlFlowMigrationCandidateIds,
    getPermissionControlFlowRolePermissions,
    getPermissionControlFlowRoles,
    getPermissionControlFlows,
    getPermissionControlRoleAssignmentRules,
    getPermissionControlRoleDeleteImpact,
    getPermissionControlRoleMemberOptions,
    getPermissionControlRoleMemberScopeOptions,
    getPermissionControlRoles,
    getPermissionControlWecomPositionSyncResults,
    listPermissionControlFlowMigrationBatches,
    migratePermissionControlFlow,
    pagePermissionControlFlowMigrationCandidates,
    pagePermissionControlRoleMembers,
    previewPermissionControlFlowMigration,
    previewPermissionControlWecomPositions,
    publishPermissionControlFlow,
    removePermissionControlRoleMembers,
    reportUnknownPermissionDiagnostic,
    resumePermissionControlFlowMigration,
    rollbackPermissionControlFlowMigration,
    savePermissionControlFlowDesigner,
    savePermissionControlFlowRolePermissions,
    syncPermissionControlWecomPositions,
    updatePermissionControlFlowRole,
    validatePermissionControlFlow,
    type WorkflowMigrationBatch,
    type WorkflowMigrationCandidate,
    type WorkflowMigrationPreview,
  } from '@/api/modules/setting/permissionControl';
  import { getRoleScopeText, setUnknownPermissionReporter } from '@/config/permissionLocale';
  import { useAppStore } from '@/store';
  import { hasAnyPermission, hasTabVisible } from '@/utils/permission';

  import {
    PermissionControlFlowMatrix,
    PermissionControlRole,
    PermissionControlRoleMember,
    PermissionControlRoleMemberOption,
    PermissionControlRoleMemberScopeOption,
    PermissionControlStatus,
    PermissionControlStatusFlow,
    RoleAssignmentRule,
    StatusFlowRolePermission,
    WorkflowDefinition,
    WorkflowRole,
  } from '@/models/setting/permissionControl';

  const flowScope = {
    scene: 'BUG',
    scopeType: 'SYSTEM' as const,
    scopeId: 'system',
  };
  const isProtectedAdmin = (role: PermissionControlRole) => role.id === 'admin';
  const appStore = useAppStore();
  const router = useRouter();
  const canAddRole = computed(() => hasAnyPermission(['SYSTEM_PERMISSION_CONTROL:READ+ADD'], ['SYSTEM']));
  const canUpdateRole = computed(() => hasAnyPermission(['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'], ['SYSTEM']));
  const canDeleteRole = computed(() => hasAnyPermission(['SYSTEM_PERMISSION_CONTROL:READ+DELETE'], ['SYSTEM']));

  const activeTab = ref('role');
  const roles = ref<PermissionControlRole[]>([]);
  const roleKeyword = ref('');
  const filteredRoles = computed(() => {
    const keyword = roleKeyword.value.trim().toLowerCase();
    return keyword ? roles.value.filter((role) => role.name.toLowerCase().includes(keyword)) : roles.value;
  });
  const roleMemberVisible = ref(false);
  const addRoleMemberVisible = ref(false);
  const roleMemberLoading = ref(false);
  const memberOptionLoading = ref(false);
  const memberScopeLoading = ref(false);
  const currentMemberRole = ref<PermissionControlRole>();
  const roleMembers = ref<PermissionControlRoleMember[]>([]);
  const roleMemberOptions = ref<PermissionControlRoleMemberOption[]>([]);
  const memberScopeOptions = ref<PermissionControlRoleMemberScopeOption[]>([]);
  const selectedMemberIds = ref<string[]>([]);
  const positionRuleVisible = ref(false);
  const positionRuleLoading = ref(false);
  const positionRules = ref<RoleAssignmentRule[]>([]);
  const positionRuleForm = reactive({
    organizationId: '',
    departmentId: '',
    positionId: '',
    enabled: true,
    syncMode: 'MANUAL' as RoleAssignmentRule['syncMode'],
  });
  const memberTotal = ref(0);
  const memberQuery = reactive({ current: 1, pageSize: 10, keyword: '' });
  const memberSourceId = ref('system');
  // 系统管理员的权限定义仍受保护，但其成员关系允许有更新权限的管理员维护。
  const memberReadonly = computed(() => !canUpdateRole.value);
  const memberPagination = computed(() => ({
    current: memberQuery.current,
    pageSize: memberQuery.pageSize,
    total: memberTotal.value,
    showTotal: true,
    showPageSize: true,
  }));

  const flows = ref<WorkflowDefinition[]>([]);
  const flowRoles = ref<WorkflowRole[]>([]);
  const flowPermissions = ref<StatusFlowRolePermission[]>([]);
  const flowMatrix = ref<PermissionControlFlowMatrix>({ statuses: [], transitions: [] });
  const selectedFlow = ref<WorkflowDefinition>();
  const selectedTransition = ref<PermissionControlStatusFlow>();
  const flowModalVisible = ref(false);
  const statusModalVisible = ref(false);
  const designerDirty = ref(false);
  const migrationVisible = ref(false);
  const migrationLoading = ref(false);
  const migrationPreview = ref<WorkflowMigrationPreview>();
  const migrationMappings = ref<Record<string, string>>({});
  const migrationBatch = ref<WorkflowMigrationBatch>();
  const migrationBatches = ref<WorkflowMigrationBatch[]>([]);
  const migrationBugIds = ref<string[]>([]);
  const migrationCandidates = ref<WorkflowMigrationCandidate[]>([]);
  const migrationCandidateTotal = ref(0);
  const migrationCandidateLoading = ref(false);
  const migrationCreateTimeRange = ref<string[]>([]);
  const migrationCandidateQuery = reactive({
    current: 1,
    pageSize: 20,
    keyword: '',
    projectIds: [] as string[],
    sourceStatusIds: [] as string[],
  });
  const migrationSourceStatuses = computed(() => {
    const statuses = migrationPreview.value?.sourceStatuses;
    if (statuses?.length) return statuses;
    const sourceStatusIds = [
      ...new Set((migrationPreview.value?.projects || []).flatMap((item) => item.sourceStatusIds)),
    ];
    return sourceStatusIds.map((id) => ({
      id,
      code: '',
      name: '未知历史状态',
      bugCount: 0,
      projectCount: 0,
      nameMissing: true,
      suggestedTargetStatusId: migrationPreview.value?.suggestedMappings[id],
      autoMapped: Boolean(migrationPreview.value?.suggestedMappings[id]),
    }));
  });
  const migrationUnresolvedStatuses = computed(() =>
    migrationSourceStatuses.value.filter((status) => !status.autoMapped)
  );
  const migrationMappingsComplete = computed(() =>
    migrationUnresolvedStatuses.value.every((status) => Boolean(migrationMappings.value[status.id]))
  );
  const migrationCandidatePagination = computed(() => ({
    current: migrationCandidateQuery.current,
    pageSize: migrationCandidateQuery.pageSize,
    total: migrationCandidateTotal.value,
    showTotal: true,
    showPageSize: true,
  }));
  const wecomPreviewVisible = ref(false);
  const wecomPreviewLoading = ref(false);
  const wecomSyncLoading = ref(false);
  const wecomPositions = ref<Array<{ position: string; sourceKey: string; memberCount: number; existing: boolean }>>(
    []
  );
  const wecomSyncResults = ref<any[]>([]);
  const statusForm = reactive({
    id: '',
    code: '',
    name: '',
    remark: '',
    initial: false,
    terminal: false,
    enabled: true,
    pos: 0,
  });
  const isDraftFlow = computed(() => selectedFlow.value?.lifecycle === 'DRAFT');
  const isArchivedFlow = computed(() => selectedFlow.value?.lifecycle === 'ARCHIVED');
  const isEditableFlow = computed(() => Boolean(selectedFlow.value?.id) && !isArchivedFlow.value);
  const flowRoleModalVisible = ref(false);

  const flowForm = reactive({
    code: '',
    name: '',
    copyFromFlowId: undefined as string | undefined,
    description: '',
  });

  const flowRoleForm = reactive({
    id: undefined as string | undefined,
    code: '',
    name: '',
    roleType: 'FIELD_USER' as WorkflowRole['roleType'],
    roleId: undefined as string | undefined,
    fieldKey: 'handle_user',
  });

  const fetchRoles = async () => {
    roles.value = await getPermissionControlRoles();
  };

  const roleScopeText = getRoleScopeText;

  async function loadRoleMembers() {
    if (!currentMemberRole.value?.id) return;
    if (
      currentMemberRole.value.type !== 'SYSTEM' &&
      (!memberSourceId.value || memberSourceId.value === 'no_such_project')
    ) {
      roleMembers.value = [];
      memberTotal.value = 0;
      return;
    }
    roleMemberLoading.value = true;
    try {
      const result = await pagePermissionControlRoleMembers({
        roleId: currentMemberRole.value.id,
        sourceId: memberSourceId.value,
        ...memberQuery,
        keyword: memberQuery.keyword.trim() || undefined,
      });
      roleMembers.value = result.list || [];
      memberTotal.value = result.total || 0;
    } finally {
      roleMemberLoading.value = false;
    }
  }

  function reloadRoleMemberScope() {
    if (!memberSourceId.value || !currentMemberRole.value) return;
    memberQuery.current = 1;
    loadRoleMembers();
  }

  async function loadRoleMemberScopeOptions(keyword = '') {
    if (!currentMemberRole.value?.id || currentMemberRole.value.type === 'SYSTEM') return;
    memberScopeLoading.value = true;
    try {
      memberScopeOptions.value = await getPermissionControlRoleMemberScopeOptions(
        currentMemberRole.value.id,
        keyword.trim() || undefined
      );
    } finally {
      memberScopeLoading.value = false;
    }
  }

  async function openRoleMembers(role: PermissionControlRole) {
    currentMemberRole.value = role;
    if (role.type === 'SYSTEM') {
      memberSourceId.value = 'system';
    } else if (role.type === 'ORGANIZATION') {
      memberSourceId.value = appStore.currentOrgId;
    } else {
      memberSourceId.value = appStore.currentProjectId;
    }
    if (role.type !== 'SYSTEM') {
      await loadRoleMemberScopeOptions('');
      if (!memberScopeOptions.value.some((item) => item.id === memberSourceId.value)) {
        memberSourceId.value = memberScopeOptions.value[0]?.id || '';
      }
    }
    if (!memberSourceId.value || memberSourceId.value === 'no_such_project') {
      Message.warning(`当前没有可管理的${role.type === 'ORGANIZATION' ? '组织' : '项目'}作用域`);
    }
    memberQuery.current = 1;
    memberQuery.keyword = '';
    roleMemberVisible.value = true;
    if (role.type === 'SYSTEM' || (memberSourceId.value && memberSourceId.value !== 'no_such_project')) {
      await loadRoleMembers();
    } else {
      roleMembers.value = [];
      memberTotal.value = 0;
    }
  }

  function searchRoleMembers() {
    memberQuery.current = 1;
    loadRoleMembers();
  }

  function changeMemberPage(current: number) {
    memberQuery.current = current;
    loadRoleMembers();
  }

  function changeMemberPageSize(pageSize: number) {
    memberQuery.current = 1;
    memberQuery.pageSize = pageSize;
    loadRoleMembers();
  }

  async function loadRoleMemberOptions(keyword = '') {
    if (!currentMemberRole.value?.id || memberReadonly.value) return;
    memberOptionLoading.value = true;
    try {
      const options = await getPermissionControlRoleMemberOptions(
        currentMemberRole.value.id,
        memberSourceId.value,
        keyword.trim() || undefined
      );
      roleMemberOptions.value = (options || []).filter((item) => !item.exclude);
    } finally {
      memberOptionLoading.value = false;
    }
  }

  async function openAddRoleMembers() {
    if (currentMemberRole.value?.type !== 'SYSTEM' && !memberSourceId.value) {
      Message.warning('请先填写成员关系作用域');
      return;
    }
    selectedMemberIds.value = [];
    addRoleMemberVisible.value = true;
    await loadRoleMemberOptions();
  }

  async function openPositionRules() {
    if (!currentMemberRole.value?.id) return;
    positionRuleForm.organizationId = currentMemberRole.value.type === 'ORGANIZATION' ? memberSourceId.value : '';
    positionRuleForm.departmentId = '';
    positionRuleForm.positionId = '';
    positionRuleForm.syncMode = 'MANUAL';
    if (!memberScopeOptions.value.length) await loadRoleMemberScopeOptions();
    positionRules.value = await getPermissionControlRoleAssignmentRules(currentMemberRole.value.id);
    positionRuleVisible.value = true;
  }

  async function savePositionRule() {
    if (!currentMemberRole.value?.id || !positionRuleForm.organizationId || !positionRuleForm.positionId) {
      Message.warning('请选择组织并填写岗位 ID');
      return false;
    }
    positionRuleLoading.value = true;
    try {
      await assignPermissionControlRoleByPosition({
        roleId: currentMemberRole.value.id,
        ...positionRuleForm,
        departmentId: positionRuleForm.departmentId || undefined,
      });
      Message.success('岗位分配规则已保存');
      positionRules.value = await getPermissionControlRoleAssignmentRules(currentMemberRole.value.id);
      await loadRoleMembers();
      return true;
    } finally {
      positionRuleLoading.value = false;
    }
  }

  async function handleAddRoleMembers() {
    if (!currentMemberRole.value?.id || selectedMemberIds.value.length === 0) return false;
    await addPermissionControlRoleMembers({
      roleId: currentMemberRole.value.id,
      sourceId: memberSourceId.value,
      userIds: selectedMemberIds.value,
    });
    Message.success('成员已添加');
    selectedMemberIds.value = [];
    addRoleMemberVisible.value = false;
    await loadRoleMembers();
    return true;
  }

  function confirmRemoveRoleMember(member: PermissionControlRoleMember) {
    if (!currentMemberRole.value?.id || memberReadonly.value) return;
    Modal.warning({
      title: `移除成员“${member.name}”`,
      content: '移除后该用户将立即失去此角色授予的权限，是否继续？',
      hideCancel: false,
      onBeforeOk: async () => {
        await removePermissionControlRoleMembers({
          roleId: currentMemberRole.value!.id,
          sourceId: memberSourceId.value,
          userIds: [member.userId],
        });
        Message.success('成员已移除');
        if (roleMembers.value.length === 1 && memberQuery.current > 1) memberQuery.current -= 1;
        await loadRoleMembers();
      },
    });
  }

  async function openRoleEditor(role?: PermissionControlRole) {
    await router.push(
      role
        ? { name: 'settingSystemPermissionControlRoleDetail', params: { roleId: role.id }, query: { tab: 'role' } }
        : { name: 'settingSystemPermissionControlRoleCreate', query: { tab: 'role' } }
    );
  }

  async function confirmDeleteRole(role: PermissionControlRole) {
    const impact = await getPermissionControlRoleDeleteImpact(role.id);
    Modal.warning({
      title: `删除角色“${role.name}”`,
      content: `该角色关联 ${impact.memberCount} 名成员，其中 ${impact.usersWithoutOtherBusinessRoleCount} 名成员删除后将没有业务权限。删除会解除全部成员关系，是否继续？`,
      hideCancel: false,
      onBeforeOk: async () => {
        await deletePermissionControlRole(role.id);
        Message.success('角色已删除');
        await fetchRoles();
      },
    });
  }

  const fetchFlowConfig = async (flowId: string) => {
    const [rolesResult, matrixResult, permissionsResult] = await Promise.all([
      getPermissionControlFlowRoles(flowId),
      getPermissionControlFlowDesigner(flowId),
      getPermissionControlFlowRolePermissions(flowId),
    ]);
    flowRoles.value = rolesResult;
    flowMatrix.value = matrixResult || { statuses: [], transitions: [] };
    flowPermissions.value = permissionsResult || [];
    [selectedTransition.value] = flowMatrix.value.transitions;
    designerDirty.value = false;
  };

  const selectFlow = async (flow: WorkflowDefinition) => {
    selectedFlow.value = flow;
    if (flow.id) {
      await fetchFlowConfig(flow.id);
    }
  };

  const fetchFlows = async () => {
    flows.value = await getPermissionControlFlows(flowScope);
    if (!selectedFlow.value && flows.value.length > 0) {
      await selectFlow(flows.value[0]);
    }
  };

  const handleRoleEnable = async (roleId: string, enabled: boolean) => {
    await enablePermissionControlRole({ roleId, enabled });
    Message.success(enabled ? '角色已启用' : '角色已禁用');
    await fetchRoles();
  };

  const handleFlowEnable = async (flow: WorkflowDefinition, enabled: boolean) => {
    if (!flow.id || !canUpdateRole.value) return;
    const apply = async () => {
      const updated = await enablePermissionControlFlow({ id: flow.id, enabled });
      Message.success(enabled ? '流程已启用；如需用于新建缺陷，请点击“开启使用”' : '流程已禁用');
      await fetchFlows();
      if (selectedFlow.value?.id === flow.id) await selectFlow(updated);
      return true;
    };
    if (!enabled && flow.activeForNew) {
      Modal.warning({
        title: '禁用当前使用中的流程',
        content: '禁用后该流程将停止用于新建缺陷，请随后开启其他已发布流程。已有缺陷仍保留原流程。',
        hideCancel: false,
        onBeforeOk: apply,
      });
      return;
    }
    await apply();
  };

  const openFlowModal = () => {
    const index = flows.value.length + 1;
    flowForm.code = `BUG_CUSTOM_FLOW_${Date.now()}`;
    flowForm.name = `自定义缺陷流程 ${index}`;
    flowForm.copyFromFlowId = selectedFlow.value?.id;
    flowForm.description = '';
    flowModalVisible.value = true;
  };

  const handleCreateFlow = async () => {
    if (!flowForm.name || !flowForm.code) {
      Message.error('流程名称和编码不能为空');
      return false;
    }
    const flow = await addPermissionControlFlow({
      code: flowForm.code,
      name: flowForm.name,
      scene: flowScope.scene,
      scopeType: flowScope.scopeType,
      scopeId: flowScope.scopeId,
      enabled: true,
      defaultFlow: false,
      description: flowForm.description,
      copyFromFlowId: flowForm.copyFromFlowId,
    } as WorkflowDefinition & { copyFromFlowId?: string });
    Message.success('流程已创建');
    flowModalVisible.value = false;
    await fetchFlows();
    await selectFlow(flow);
    return true;
  };

  const openStatusModal = (status?: PermissionControlStatus) => {
    if (!isEditableFlow.value || !canUpdateRole.value) return;
    statusForm.id = status?.id || '';
    statusForm.code = status?.code || `BUG_STATUS_${Date.now()}`;
    statusForm.name = status?.name || '';
    statusForm.remark = status?.remark || '';
    statusForm.initial = Boolean(status?.initial);
    statusForm.terminal = Boolean(status?.terminal);
    statusForm.enabled = status?.enabled !== false;
    statusForm.pos = status?.pos ?? flowMatrix.value.statuses.length;
    statusModalVisible.value = true;
  };

  const saveStatusDraft = () => {
    if (!isEditableFlow.value || !canUpdateRole.value) return false;
    if (!statusForm.name.trim() || !statusForm.code.trim()) {
      Message.error('状态名称和稳定编码不能为空');
      return false;
    }
    if (statusForm.initial) {
      flowMatrix.value.statuses.forEach((item) => {
        item.initial = false;
      });
    }
    const existing = flowMatrix.value.statuses.find((item) => item.id === statusForm.id);
    const value: PermissionControlStatus = {
      ...statusForm,
      id: statusForm.id || `draft-status-${Date.now()}`,
      code: statusForm.code.trim(),
      name: statusForm.name.trim(),
    };
    if (existing) Object.assign(existing, value);
    else flowMatrix.value.statuses.push(value);
    designerDirty.value = true;
    statusModalVisible.value = false;
    return true;
  };

  const deleteStatusDraft = () => {
    if (!statusForm.id || !isEditableFlow.value || !canUpdateRole.value) return;
    flowMatrix.value.statuses = flowMatrix.value.statuses.filter((item) => item.id !== statusForm.id);
    const removedTransitionIds = flowMatrix.value.transitions
      .filter((item) => item.fromId === statusForm.id || item.toId === statusForm.id)
      .map((item) => item.id);
    flowMatrix.value.transitions = flowMatrix.value.transitions.filter(
      (item) => item.fromId !== statusForm.id && item.toId !== statusForm.id
    );
    flowPermissions.value = flowPermissions.value.filter((item) => !removedTransitionIds.includes(item.statusFlowId));
    selectedTransition.value = undefined;
    designerDirty.value = true;
    statusModalVisible.value = false;
  };

  const findTransition = (fromId: string, toId: string) => {
    return flowMatrix.value.transitions.find((transition) => transition.fromId === fromId && transition.toId === toId);
  };

  const addTransition = (fromId: string, toId: string) => {
    if (!isEditableFlow.value || !canUpdateRole.value || findTransition(fromId, toId)) return;
    const transition: PermissionControlStatusFlow = {
      id: `draft-transition-${Date.now()}`,
      fromId,
      toId,
      enabled: true,
    };
    flowMatrix.value.transitions.push(transition);
    selectedTransition.value = transition;
    designerDirty.value = true;
  };

  const removeSelectedTransition = () => {
    if (!selectedTransition.value || !isEditableFlow.value || !canUpdateRole.value) return;
    const { id } = selectedTransition.value;
    flowMatrix.value.transitions = flowMatrix.value.transitions.filter((item) => item.id !== id);
    flowPermissions.value = flowPermissions.value.filter((item) => item.statusFlowId !== id);
    selectedTransition.value = undefined;
    designerDirty.value = true;
  };

  const saveDesigner = async () => {
    if (!selectedFlow.value?.id || !isEditableFlow.value || !canUpdateRole.value) return;
    flowMatrix.value = await savePermissionControlFlowDesigner(selectedFlow.value.id, flowMatrix.value);
    designerDirty.value = false;
    Message.success('流程已保存');
  };

  const persistTransitionPermissions = async () => {
    if (!selectedFlow.value?.id || !isEditableFlow.value || !canUpdateRole.value) return;
    await savePermissionControlFlowRolePermissions({
      flowId: selectedFlow.value.id,
      permissions: flowPermissions.value
        .filter((permission) => permission.statusFlowId && permission.workflowRoleId)
        .map((permission) => ({
          ...permission,
          flowId: selectedFlow.value?.id || '',
          visible: Boolean(permission.visible || permission.operable),
          operable: Boolean(permission.visible && permission.operable),
          enabled: permission.enabled !== false,
        })),
    });
  };

  const validateDesigner = async () => {
    if (!selectedFlow.value?.id) return;
    if (designerDirty.value) await saveDesigner();
    await persistTransitionPermissions();
    const result = await validatePermissionControlFlow(selectedFlow.value.id);
    if (result.valid) Message.success('流程完整性校验通过');
    else Modal.error({ title: '流程校验未通过', content: result.errors.join('；') });
  };

  const publishDesigner = async () => {
    if (!selectedFlow.value?.id || selectedFlow.value.version == null || !isDraftFlow.value) return;
    if (designerDirty.value) await saveDesigner();
    await persistTransitionPermissions();
    const published = await publishPermissionControlFlow(selectedFlow.value.id, selectedFlow.value.version);
    Message.success('流程已发布；请手动点击“开启使用”后再用于新建缺陷');
    await fetchFlows();
    await selectFlow(published);
  };

  const activateDesigner = async () => {
    if (!selectedFlow.value?.id) return;
    Modal.warning({
      title: '开启使用缺陷流程',
      content: '开启后，新建缺陷将使用此流程。已有缺陷不会自动切换，原使用中流程不会归档，仅停止接收新缺陷。',
      hideCancel: false,
      onBeforeOk: async () => {
        const active = await activatePermissionControlFlow(selectedFlow.value!.id!);
        Message.success('流程已开启使用');
        await fetchFlows();
        await selectFlow(active);
        return true;
      },
    });
  };

  const openWecomPositionPreview = async () => {
    if (!selectedFlow.value?.id || isArchivedFlow.value) return;
    wecomPreviewVisible.value = true;
    wecomPreviewLoading.value = true;
    try {
      [wecomPositions.value, wecomSyncResults.value] = await Promise.all([
        previewPermissionControlWecomPositions(selectedFlow.value.id),
        getPermissionControlWecomPositionSyncResults(selectedFlow.value.id),
      ]);
    } finally {
      wecomPreviewLoading.value = false;
    }
  };

  const syncWecomPositions = async () => {
    if (!selectedFlow.value?.id || isArchivedFlow.value) return false;
    wecomSyncLoading.value = true;
    try {
      const result = await syncPermissionControlWecomPositions(selectedFlow.value.id);
      Message.success(`企微岗位同步完成：新增 ${result.created}，更新 ${result.updated}，停用 ${result.disabled}`);
      await fetchFlowConfig(selectedFlow.value.id);
      wecomPositions.value = await previewPermissionControlWecomPositions(selectedFlow.value.id);
      wecomSyncResults.value = await getPermissionControlWecomPositionSyncResults(selectedFlow.value.id);
      return true;
    } finally {
      wecomSyncLoading.value = false;
    }
  };

  const copyDesigner = async () => {
    if (!selectedFlow.value?.id) return;
    const draft = await copyPermissionControlFlow(selectedFlow.value.id);
    Message.success('已复制为新版本草稿');
    await fetchFlows();
    await selectFlow(draft);
  };

  const archiveDesigner = async () => {
    if (!selectedFlow.value?.id) return;
    Modal.warning({
      title: '归档缺陷流程',
      content: `归档后“${selectedFlow.value.name}”将不可继续编辑，确认归档？`,
      hideCancel: false,
      onBeforeOk: async () => {
        const archived = await archivePermissionControlFlow(selectedFlow.value!.id!);
        Message.success('流程已归档');
        await fetchFlows();
        await selectFlow(archived);
        return true;
      },
    });
  };

  const migrationCandidatePayload = () => ({
    ...migrationCandidateQuery,
    keyword: migrationCandidateQuery.keyword.trim() || undefined,
    createTimeStart: migrationCreateTimeRange.value[0]
      ? new Date(migrationCreateTimeRange.value[0]).getTime()
      : undefined,
    createTimeEnd: migrationCreateTimeRange.value[1]
      ? new Date(migrationCreateTimeRange.value[1]).getTime()
      : undefined,
  });

  async function loadMigrationCandidates() {
    if (!migrationPreview.value) return;
    migrationCandidateLoading.value = true;
    try {
      const result = await pagePermissionControlFlowMigrationCandidates(
        migrationPreview.value.targetFlowId,
        migrationCandidatePayload()
      );
      migrationCandidates.value = result.list || [];
      migrationCandidateTotal.value = result.total || 0;
    } finally {
      migrationCandidateLoading.value = false;
    }
  }

  const openMigrationPreview = async () => {
    if (!selectedFlow.value?.id) return;
    migrationLoading.value = true;
    migrationVisible.value = true;
    try {
      migrationPreview.value = await previewPermissionControlFlowMigration(selectedFlow.value.id);
      migrationMappings.value = { ...migrationPreview.value.suggestedMappings };
      migrationBugIds.value = [];
      migrationCandidateQuery.current = 1;
      migrationCandidateQuery.keyword = '';
      migrationCandidateQuery.projectIds = [];
      migrationCandidateQuery.sourceStatusIds = [];
      migrationCreateTimeRange.value = [];
      migrationBatch.value = undefined;
      migrationBatches.value = await listPermissionControlFlowMigrationBatches(selectedFlow.value.id);
      await loadMigrationCandidates();
    } finally {
      migrationLoading.value = false;
    }
  };

  function searchMigrationCandidates() {
    migrationCandidateQuery.current = 1;
    loadMigrationCandidates();
  }

  function changeMigrationCandidatePage(current: number) {
    migrationCandidateQuery.current = current;
    loadMigrationCandidates();
  }

  function changeMigrationCandidatePageSize(pageSize: number) {
    migrationCandidateQuery.current = 1;
    migrationCandidateQuery.pageSize = pageSize;
    loadMigrationCandidates();
  }

  async function selectAllMigrationCandidates() {
    if (!migrationPreview.value) return;
    const result = await getPermissionControlFlowMigrationCandidateIds(
      migrationPreview.value.targetFlowId,
      migrationCandidatePayload()
    );
    migrationBugIds.value = result.ids;
    Message.success(`已选择当前筛选结果 ${result.total} 条`);
  }

  const waitForMigration = async (batchId: string, deadline: number): Promise<void> => {
    migrationBatch.value = await getPermissionControlFlowMigrationBatch(batchId);
    if (['COMPLETED', 'FAILED', 'PARTIAL_SUCCESS'].includes(migrationBatch.value.status) || Date.now() >= deadline) {
      return;
    }
    await new Promise<void>((resolve) => {
      window.setTimeout(resolve, 1000);
    });
    await waitForMigration(batchId, deadline);
  };

  const executeMigration = async () => {
    if (!migrationPreview.value || !migrationBugIds.value.length) {
      Message.warning('请选择需要关联的缺陷');
      return false;
    }
    if (!migrationMappingsComplete.value) {
      Message.warning('请先为全部历史状态选择目标状态');
      return false;
    }
    migrationLoading.value = true;
    try {
      await migratePermissionControlFlow({
        targetFlowId: migrationPreview.value.targetFlowId,
        dryRun: true,
        statusMappings: migrationMappings.value,
        bugIds: migrationBugIds.value,
      });
      const result = await migratePermissionControlFlow({
        targetFlowId: migrationPreview.value.targetFlowId,
        dryRun: false,
        statusMappings: migrationMappings.value,
        bugIds: migrationBugIds.value,
      });
      const deadline = Date.now() + 5 * 60 * 1000;
      await waitForMigration(result.batchId, deadline);
      migrationBatches.value = await listPermissionControlFlowMigrationBatches(migrationPreview.value.targetFlowId);
      if (migrationBatch.value?.status === 'COMPLETED')
        Message.success(`关联完成：成功 ${migrationBatch.value.successCount} 条`);
      else Message.warning('迁移未全部成功，可在当前窗口重试失败项或回滚');
      return false;
    } finally {
      migrationLoading.value = false;
    }
  };

  const resumeMigrationBatch = async () => {
    if (!migrationBatch.value) return;
    await resumePermissionControlFlowMigration(migrationBatch.value.id);
    Message.success('已提交失败项重试，请稍后刷新批次状态');
    migrationBatch.value = await getPermissionControlFlowMigrationBatch(migrationBatch.value.id);
    if (migrationPreview.value)
      migrationBatches.value = await listPermissionControlFlowMigrationBatches(migrationPreview.value.targetFlowId);
  };
  const rollbackMigrationBatch = async () => {
    if (!migrationBatch.value) return;
    const batchId = migrationBatch.value.id;
    Modal.warning({
      title: '确认回滚历史缺陷关联',
      content: '仅仍保持迁移后流程和状态的缺陷会被回滚；已被其他人员修改的缺陷将记录为冲突，不会覆盖。是否继续？',
      hideCancel: false,
      onBeforeOk: async () => {
        migrationBatch.value = await rollbackPermissionControlFlowMigration(batchId);
        if (migrationPreview.value)
          migrationBatches.value = await listPermissionControlFlowMigrationBatches(migrationPreview.value.targetFlowId);
        Message.success('回滚已执行，请核对冲突数');
        return true;
      },
    });
  };

  const viewMigrationBatch = async (batchId: string) => {
    migrationBatch.value = await getPermissionControlFlowMigrationBatch(batchId);
  };

  const isFlowDeleteCandidate = (flow: WorkflowDefinition) =>
    !flow.activeForNew && (flow.enabled === false || flow.lifecycle !== 'PUBLISHED');

  const confirmDeleteFlow = async (flow: WorkflowDefinition) => {
    if (!flow.id || !isFlowDeleteCandidate(flow)) return;
    const impact = await getPermissionControlFlowImpact(flow.id);
    if (!impact.deletable) {
      Message.warning(impact.reason || '该流程不可删除');
      return;
    }
    Modal.warning({
      title: `删除流程“${flow.name}”`,
      content: `该流程未关联缺陷和流转历史。流程及其角色、流转授权将被删除，是否继续？`,
      hideCancel: false,
      onBeforeOk: async () => {
        await deletePermissionControlFlow(flow.id!);
        if (selectedFlow.value?.id === flow.id) selectedFlow.value = undefined;
        Message.success('流程已删除');
        await fetchFlows();
      },
    });
  };

  const openFlowRoleModal = (role?: WorkflowRole) => {
    if (!isEditableFlow.value || (role ? !canUpdateRole.value : !canAddRole.value)) return;
    flowRoleForm.id = role?.id;
    flowRoleForm.code = role?.code || `BUG_HANDLER_${Date.now()}`;
    flowRoleForm.name = role?.name || '当前处理人';
    flowRoleForm.roleType = role?.roleType || 'FIELD_USER';
    flowRoleForm.roleId = role?.roleId;
    flowRoleForm.fieldKey = role?.fieldKey || 'handle_user';
    flowRoleModalVisible.value = true;
  };

  const handleSaveFlowRole = async () => {
    if (!isEditableFlow.value || (flowRoleForm.id ? !canUpdateRole.value : !canAddRole.value)) return false;
    if (!selectedFlow.value?.id || !flowRoleForm.name || !flowRoleForm.code) {
      Message.error('流程角色名称和编码不能为空');
      return false;
    }
    const payload: WorkflowRole = {
      id: flowRoleForm.id,
      flowId: selectedFlow.value.id,
      code: flowRoleForm.code,
      name: flowRoleForm.name,
      roleType: flowRoleForm.roleType,
      roleId: flowRoleForm.roleType === 'SYSTEM_ROLE' ? flowRoleForm.roleId : undefined,
      fieldKey:
        flowRoleForm.roleType === 'FIELD_USER' || flowRoleForm.roleType === 'POSITION'
          ? flowRoleForm.fieldKey
          : undefined,
      enabled: true,
    };
    if (flowRoleForm.id) await updatePermissionControlFlowRole(payload);
    else await addPermissionControlFlowRole(payload);
    Message.success(flowRoleForm.id ? '流程角色已更新' : '流程角色已添加');
    flowRoleModalVisible.value = false;
    await fetchFlowConfig(selectedFlow.value.id);
    return true;
  };

  const confirmDeleteFlowRole = (role: WorkflowRole) => {
    if (!role.id || !selectedFlow.value?.id || !isEditableFlow.value || !canDeleteRole.value) return;
    Modal.warning({
      title: `删除流程角色“${role.name}”`,
      content: '该角色在流转矩阵中的授权将同时删除，是否继续？',
      hideCancel: false,
      onBeforeOk: async () => {
        await deletePermissionControlFlowRole(role.id!);
        Message.success('流程角色已删除');
        await fetchFlowConfig(selectedFlow.value!.id!);
      },
    });
  };

  const selectTransition = (fromId: string, toId: string) => {
    selectedTransition.value = findTransition(fromId, toId);
  };

  const getStatusName = (statusId: string) => {
    return flowMatrix.value.statuses.find((status) => status.id === statusId)?.name || statusId;
  };

  const getWorkflowRoleMapping = (role: WorkflowRole) => {
    if (role.roleType === 'FIELD_USER') {
      return role.fieldKey === 'create_user' ? '业务字段：创建人' : '业务字段：当前处理人';
    }
    if (role.roleType === 'POSITION') {
      return `企微职位：${role.fieldKey || '-'}`;
    }
    return `系统角色：${roles.value.find((item) => item.id === role.roleId)?.name || role.roleId || '-'}`;
  };

  const findRolePermission = (workflowRoleId: string) => {
    if (!selectedTransition.value) return undefined;
    return flowPermissions.value.find(
      (permission) =>
        permission.statusFlowId === selectedTransition.value?.id && permission.workflowRoleId === workflowRoleId
    );
  };

  const getRolePermissionValue = (workflowRoleId: string, field: 'visible' | 'operable') => {
    return Boolean(findRolePermission(workflowRoleId)?.[field]);
  };

  const ensureRolePermission = (workflowRoleId: string) => {
    const current = findRolePermission(workflowRoleId);
    if (current) return current;
    const permission: StatusFlowRolePermission = {
      flowId: selectedFlow.value?.id || '',
      statusFlowId: selectedTransition.value?.id || '',
      workflowRoleId,
      visible: false,
      operable: false,
      enabled: true,
    };
    flowPermissions.value.push(permission);
    return permission;
  };

  const updateRolePermission = (workflowRoleId: string, field: 'visible' | 'operable', value: boolean) => {
    if (!isEditableFlow.value || !canUpdateRole.value) return;
    const permission = ensureRolePermission(workflowRoleId);
    if (field === 'operable' && value) {
      permission.visible = true;
    }
    if (field === 'visible' && !value) {
      permission.operable = false;
    }
    permission[field] = value;
  };

  const getTransitionAuthSummary = (statusFlowId?: string) => {
    if (!statusFlowId) return '未配置';
    const operableCount = flowPermissions.value.filter(
      (permission) => permission.statusFlowId === statusFlowId && permission.enabled !== false && permission.operable
    ).length;
    const visibleCount = flowPermissions.value.filter(
      (permission) => permission.statusFlowId === statusFlowId && permission.enabled !== false && permission.visible
    ).length;
    if (operableCount > 0) return `${operableCount} 可执行`;
    if (visibleCount > 0) return `${visibleCount} 可见`;
    return '未授权';
  };

  const saveTransitionPermissions = async () => {
    await persistTransitionPermissions();
    Message.success('流转授权已保存');
    if (!selectedFlow.value?.id) return;
    await fetchFlowConfig(selectedFlow.value.id);
  };

  onMounted(() => {
    setUnknownPermissionReporter((diagnostic) => {
      reportUnknownPermissionDiagnostic(diagnostic).catch(() => undefined);
    });
    fetchRoles();
    fetchFlows();
  });

  onUnmounted(() => setUnknownPermissionReporter());
</script>

<style scoped lang="less">
  .permission-control-page {
    overflow: auto;
    max-width: 100%;
    height: 100%;
    min-height: 0;
  }
  .role-layout {
    display: grid;
    grid-template-columns: 360px minmax(0, 1fr);
    gap: 16px;
    height: 100%;
  }
  .role-auth {
    min-width: 0;
  }
  .flow-layout {
    display: grid;
    align-items: start;
    min-width: 0;
    min-height: 0;
    grid-template-columns: 300px minmax(520px, 1fr) 360px;
    gap: 16px;
  }
  .flow-card {
    min-width: 0;
    min-height: 560px;
  }
  .card-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    min-width: 0;
  }
  .flow-list-item {
    cursor: pointer;
    border-radius: 6px;
  }
  .flow-list-item-content {
    width: 100%;
    min-width: 0;
  }
  .flow-list-item-info {
    min-width: 0;
  }
  .flow-list-item-name {
    overflow-wrap: anywhere;
    font-weight: 500;
    line-height: 22px;
    color: var(--color-text-1);
  }
  .flow-list-item-description {
    overflow-wrap: anywhere;
    margin-top: 2px;
    font-size: 12px;
    line-height: 20px;
    color: var(--color-text-3);
  }
  .flow-list-item-actions {
    display: flex;
    align-items: center;
    margin-top: 10px;
    width: 100%;
    flex-wrap: wrap;
    gap: 6px 8px;
  }
  .flow-list-scroll {
    overflow-y: auto;
    padding-right: 4px;
    min-height: 480px;
    max-height: calc(100vh - 250px);
    overscroll-behavior: contain;
    scrollbar-gutter: stable;
  }
  .selected {
    background: var(--color-fill-2);
  }
  .matrix-card {
    min-width: 0;
  }
  .matrix-wrap {
    overflow: auto;
  }
  .flow-role-table-wrap {
    overflow: auto;
    margin-top: 16px;
    min-height: 220px;
    max-height: calc(100vh - 430px);
    overscroll-behavior: contain;
    scrollbar-gutter: stable;
  }
  .flow-matrix {
    width: 100%;
    min-width: 680px;
    border-collapse: collapse;
    th,
    td {
      padding: 8px;
      border: 1px solid var(--color-border-2);
      text-align: center;
      vertical-align: middle;
    }
    th {
      font-weight: 600;
      background: var(--color-fill-1);
    }
  }
  .matrix-cell {
    width: 100%;
    min-height: 46px;
    border: 1px solid transparent;
    border-radius: 6px;
    cursor: pointer;
    span,
    small {
      display: block;
    }
    small {
      margin-top: 2px;
      color: var(--color-text-3);
    }
  }
  .cell-allow {
    color: rgb(var(--green-7));
    background: rgb(var(--green-1));
  }
  .cell-allow.active {
    border-color: rgb(var(--primary-6));
    color: rgb(var(--primary-6));
    background: rgb(var(--primary-1));
  }
  .cell-deny {
    color: var(--color-text-4);
  }
  .muted {
    font-size: 12px;
    color: var(--color-text-3);
  }

  @media (max-width: 1320px) {
    .flow-layout,
    .role-layout {
      grid-template-columns: 1fr;
    }
  }
</style>
