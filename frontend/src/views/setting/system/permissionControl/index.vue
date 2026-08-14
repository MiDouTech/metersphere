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
          <a-card class="flow-card" :bordered="false">
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

            <a-list :bordered="false">
              <a-list-item
                v-for="flow in flows"
                :key="flow.id"
                class="flow-list-item"
                :class="{ selected: selectedFlow?.id === flow.id }"
                @click="selectFlow(flow)"
              >
                <a-list-item-meta :title="flow.name" :description="flow.description || flow.code" />
                <template #actions>
                  <a-tag v-if="flow.defaultFlow" color="blue">默认</a-tag>
                  <a-switch
                    v-operable-permission="{
                      code: 'PERMISSION_FLOW_SAVE_BUTTON',
                      permissions: ['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'],
                      typeList: ['SYSTEM'],
                    }"
                    :model-value="flow.enabled !== false"
                    :disabled="flow.defaultFlow"
                    @click.stop
                    @change="(value) => handleFlowEnable(flow, Boolean(value))"
                  />
                  <a-link
                    v-if="!flow.defaultFlow"
                    v-visible-permission="{
                      code: 'PERMISSION_FLOW_DELETE_BUTTON',
                      permissions: ['SYSTEM_PERMISSION_CONTROL:READ+DELETE'],
                      typeList: ['SYSTEM'],
                    }"
                    status="danger"
                    @click.stop="confirmDeleteFlow(flow)"
                    >删除</a-link
                  >
                </template>
              </a-list-item>
            </a-list>
          </a-card>

          <a-card class="flow-card matrix-card" :bordered="false">
            <template #title>
              <div class="card-title">
                <span>{{ selectedFlow?.name || '流转矩阵' }}</span>
                <a-button
                  v-visible-permission="{
                    code: 'PERMISSION_FLOW_ROLE_ADD_BUTTON',
                    permissions: ['SYSTEM_PERMISSION_CONTROL:READ+ADD'],
                    typeList: ['SYSTEM'],
                  }"
                  :disabled="!selectedFlow?.id"
                  @click="() => openFlowRoleModal()"
                >
                  添加流程角色
                </a-button>
              </div>
            </template>

            <a-alert class="mb-4" type="info">
              点击允许流转的单元格，在右侧为该流转配置流程角色的可见/可执行权限；未配置授权时后端兼容旧状态流。
            </a-alert>

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
                <a-button
                  v-operable-permission="{
                    code: 'PERMISSION_FLOW_SAVE_BUTTON',
                    permissions: ['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'],
                    typeList: ['SYSTEM'],
                  }"
                  type="primary"
                  :disabled="!selectedFlow?.id"
                  @click="saveTransitionPermissions"
                >
                  保存授权
                </a-button>
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

              <a-table class="mt-4" :data="flowRoles" :pagination="false" size="small" row-key="id">
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
                        @change="(value) => updateRolePermission(record.id, 'visible', Boolean(value))"
                      />
                    </template>
                  </a-table-column>
                  <a-table-column :width="76" title="可执行">
                    <template #cell="{ record }">
                      <a-checkbox
                        :model-value="getRolePermissionValue(record.id, 'operable')"
                        @change="(value) => updateRolePermission(record.id, 'operable', Boolean(value))"
                      />
                    </template>
                  </a-table-column>
                  <a-table-column :width="110" title="操作">
                    <template #cell="{ record }">
                      <a-space>
                        <a-link
                          v-visible-permission="{
                            code: 'PERMISSION_FLOW_ROLE_UPDATE_BUTTON',
                            permissions: ['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'],
                            typeList: ['SYSTEM'],
                          }"
                          @click="openFlowRoleModal(record)"
                          >编辑</a-link
                        >
                        <a-link
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
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="flowRoleForm.roleType === 'FIELD_USER'" field="fieldKey" label="业务字段">
          <a-select v-model="flowRoleForm.fieldKey">
            <a-option value="handle_user">当前处理人</a-option>
            <a-option value="create_user">创建人</a-option>
          </a-select>
        </a-form-item>
        <a-form-item v-else field="roleId" label="系统角色">
          <a-select v-model="flowRoleForm.roleId" allow-search>
            <a-option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</a-option>
          </a-select>
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

    <a-drawer
      v-model:visible="roleEditorVisible"
      :width="920"
      :title="roleEditorReadonly ? '查看角色' : roleForm.id ? '编辑角色' : '新增角色'"
      :footer="!roleEditorReadonly"
      unmount-on-close
      :on-before-cancel="confirmDiscardRoleChanges"
      :on-before-ok="saveRole"
    >
      <a-alert v-if="roleEditorReadonly" class="mb-4" type="info"
        >管理员角色受保护；或当前账号只有查看权限，本页面不可修改。</a-alert
      >
      <a-form :model="roleForm" layout="vertical">
        <div class="grid grid-cols-2 gap-4">
          <a-form-item field="name" label="角色名称" required>
            <a-input v-model="roleForm.name" :disabled="roleEditorReadonly" placeholder="请输入角色名称" />
          </a-form-item>
          <a-form-item field="type" label="权限范围" required>
            <a-select
              v-model="roleForm.type"
              :disabled="roleEditorReadonly || Boolean(roleForm.id)"
              @change="handleRoleTypeChange"
            >
              <a-option value="SYSTEM">系统</a-option>
              <a-option value="ORGANIZATION">组织</a-option>
              <a-option value="PROJECT">项目</a-option>
            </a-select>
          </a-form-item>
        </div>
        <a-form-item field="description" label="描述">
          <a-textarea v-model="roleForm.description" :disabled="roleEditorReadonly" :max-length="1000" />
        </a-form-item>
        <a-form-item field="enabled" label="启用状态">
          <a-switch v-model="roleForm.enabled" :disabled="roleEditorReadonly" />
        </a-form-item>
      </a-form>

      <a-divider>页面、页签与按钮权限</a-divider>
      <a-table
        :data="flatResources"
        :loading="rolePermissionLoading"
        :pagination="false"
        size="small"
        row-key="code"
        :scroll="{ y: 330 }"
      >
        <template #columns>
          <a-table-column title="权限资源" :width="260">
            <template #cell="{ record }">
              <div :style="{ paddingLeft: `${record.depth * 16}px` }">{{ resourceNameText(record.name) }}</div>
            </template>
          </a-table-column>
          <a-table-column title="类型" :width="90">
            <template #cell="{ record }">{{ resourceTypeText(record.type) }}</template>
          </a-table-column>
          <a-table-column title="关联接口权限" :width="250">
            <template #cell="{ record }">{{ resourcePermissionText(record) }}</template>
          </a-table-column>
          <a-table-column title="可见" :width="80">
            <template #cell="{ record }">
              <a-checkbox
                v-model="resourcePermissionMap[record.code].visible"
                :disabled="roleEditorReadonly"
                @change="syncResourcePermission(record.code)"
              />
            </template>
          </a-table-column>
          <a-table-column title="可操作" :width="90">
            <template #cell="{ record }">
              <a-checkbox
                v-if="record.type === 'BUTTON' || record.type === 'API'"
                v-model="resourcePermissionMap[record.code].operable"
                :disabled="roleEditorReadonly"
                @change="syncResourcePermission(record.code, true)"
              />
              <span v-else>-</span>
            </template>
          </a-table-column>
        </template>
      </a-table>

      <a-divider>数据操作权限</a-divider>
      <a-table
        :data="flatDataPermissions"
        :loading="rolePermissionLoading"
        :pagination="false"
        size="small"
        row-key="id"
        :scroll="{ y: 280 }"
      >
        <template #columns>
          <a-table-column title="业务模块" data-index="groupName" :width="220" />
          <a-table-column title="操作权限">
            <template #cell="{ record }">{{ dataPermissionText(record) }}</template>
          </a-table-column>
          <a-table-column title="授权" :width="90">
            <template #cell="{ record }">
              <a-switch
                v-model="dataPermissionMap[record.id]"
                :disabled="roleEditorReadonly"
                @change="syncDataPermission(record.id)"
              />
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
  import { onBeforeRouteLeave } from 'vue-router';
  import { Message, Modal } from '@arco-design/web-vue';

  import {
    addPermissionControlFlow,
    addPermissionControlFlowRole,
    addPermissionControlRoleMembers,
    assignPermissionControlRoleByPosition,
    deletePermissionControlFlow,
    deletePermissionControlFlowRole,
    deletePermissionControlRole,
    enablePermissionControlFlow,
    enablePermissionControlRole,
    getPermissionControlFlowMatrix,
    getPermissionControlFlowRolePermissions,
    getPermissionControlFlowRoles,
    getPermissionControlFlows,
    getPermissionControlPermissionDefinition,
    getPermissionControlResourceTree,
    getPermissionControlRoleAssignmentRules,
    getPermissionControlRoleDeleteImpact,
    getPermissionControlRoleMemberOptions,
    getPermissionControlRoleMemberScopeOptions,
    getPermissionControlRolePermissions,
    getPermissionControlRoles,
    getPermissionControlRoleUiPermissions,
    pagePermissionControlRoleMembers,
    removePermissionControlRoleMembers,
    reportUnknownPermissionDiagnostic,
    savePermissionControlFlowRolePermissions,
    savePermissionControlRole,
    updatePermissionControlFlowRole,
  } from '@/api/modules/setting/permissionControl';
  import {
    getPermissionText,
    getResourceNameText,
    getResourceTypeText,
    getRoleScopeText,
    isWritePermission,
    setUnknownPermissionReporter,
  } from '@/config/permissionLocale';
  import { useAppStore } from '@/store';
  import { hasAnyPermission, hasTabVisible } from '@/utils/permission';

  import {
    PermissionControlFlowMatrix,
    PermissionControlRole,
    PermissionControlRoleMember,
    PermissionControlRoleMemberOption,
    PermissionControlRoleMemberScopeOption,
    PermissionControlStatusFlow,
    PermissionResourceNode,
    RoleAssignmentRule,
    RolePermissionItem,
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
  const canAddRole = computed(() => hasAnyPermission(['SYSTEM_PERMISSION_CONTROL:READ+ADD'], ['SYSTEM']));
  const canUpdateRole = computed(() => hasAnyPermission(['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'], ['SYSTEM']));

  const activeTab = ref('role');
  const roles = ref<PermissionControlRole[]>([]);
  const roleKeyword = ref('');
  const filteredRoles = computed(() => {
    const keyword = roleKeyword.value.trim().toLowerCase();
    return keyword ? roles.value.filter((role) => role.name.toLowerCase().includes(keyword)) : roles.value;
  });
  const roleEditorVisible = ref(false);
  const roleEditorReadonly = ref(false);
  const rolePermissionLoading = ref(false);
  const roleEditorSnapshot = ref('');
  const loadedRoleType = ref<PermissionControlRole['type']>('SYSTEM');
  const roleForm = reactive<Partial<PermissionControlRole>>({
    id: undefined,
    name: '',
    description: '',
    type: 'SYSTEM',
    enabled: true,
  });
  const permissionResources = ref<PermissionResourceNode[]>([]);
  const roleDataPermissions = ref<RolePermissionItem[]>([]);
  const resourcePermissionMap = reactive<Record<string, { visible: boolean; operable: boolean }>>({});
  const dataPermissionMap = reactive<Record<string, boolean>>({});
  const serializeRoleEditor = () =>
    JSON.stringify({ role: roleForm, data: dataPermissionMap, ui: resourcePermissionMap });
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
  const memberReadonly = computed(() =>
    Boolean(currentMemberRole.value && (isProtectedAdmin(currentMemberRole.value) || !canUpdateRole.value))
  );
  const memberPagination = computed(() => ({
    current: memberQuery.current,
    pageSize: memberQuery.pageSize,
    total: memberTotal.value,
    showTotal: true,
    showPageSize: true,
  }));

  type FlatResource = PermissionResourceNode & { depth: number };
  type FlatDataPermission = RolePermissionItem & { groupName: string };
  const flatResources = computed<FlatResource[]>(() => {
    const result: FlatResource[] = [];
    const walk = (nodes: PermissionResourceNode[], depth = 0) => {
      nodes.forEach((node) => {
        result.push({ ...node, depth });
        walk(node.children || [], depth + 1);
      });
    };
    walk(permissionResources.value);
    return result;
  });
  const flatDataPermissions = computed<FlatDataPermission[]>(() => {
    const result: FlatDataPermission[] = [];
    roleDataPermissions.value.forEach((first) => {
      (first.children || []).forEach((second) => {
        (second.permissions || []).forEach((permission) => result.push({ ...permission, groupName: second.name }));
      });
    });
    return result;
  });
  const permissionSubjectMap = computed(() => {
    const result = new Map<string, string>();
    flatDataPermissions.value.forEach((permission) => {
      const subjectCode = permission.id.split(':')[0];
      if (subjectCode && !result.has(subjectCode)) result.set(subjectCode, permission.groupName);
    });
    return result;
  });
  const flows = ref<WorkflowDefinition[]>([]);
  const flowRoles = ref<WorkflowRole[]>([]);
  const flowPermissions = ref<StatusFlowRolePermission[]>([]);
  const flowMatrix = ref<PermissionControlFlowMatrix>({ statuses: [], transitions: [] });
  const selectedFlow = ref<WorkflowDefinition>();
  const selectedTransition = ref<PermissionControlStatusFlow>();
  const flowModalVisible = ref(false);
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
  const resourceTypeText = getResourceTypeText;
  const resourceNameText = getResourceNameText;
  const permissionText = getPermissionText;
  const dataPermissionText = (permission: FlatDataPermission) => permissionText(permission.id, permission.groupName);
  const resourcePermissionText = (resource: FlatResource) => {
    const subjectCode = resource.permissionId?.split(':')[0] || '';
    const subjectName = permissionSubjectMap.value.get(subjectCode) || resourceNameText(resource.name);
    return permissionText(resource.permissionId, subjectName, resource.code);
  };

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

  function clearRolePermissionState() {
    Object.keys(resourcePermissionMap).forEach((key) => delete resourcePermissionMap[key]);
    Object.keys(dataPermissionMap).forEach((key) => delete dataPermissionMap[key]);
  }

  async function loadRolePermissions(resetSnapshot = true) {
    if (!roleForm.type) return;
    rolePermissionLoading.value = true;
    try {
      const [resources, dataPermissions, uiPermissions] = await Promise.all([
        getPermissionControlResourceTree(roleForm.type),
        roleForm.id
          ? getPermissionControlRolePermissions(roleForm.id)
          : getPermissionControlPermissionDefinition(roleForm.type),
        roleForm.id ? getPermissionControlRoleUiPermissions(roleForm.id) : Promise.resolve([]),
      ]);
      permissionResources.value = resources || [];
      roleDataPermissions.value = dataPermissions || [];
      clearRolePermissionState();
      const uiMap = new Map(uiPermissions.map((item) => [item.resourceCode, item]));
      flatResources.value.forEach((resource) => {
        const configured = uiMap.get(resource.code);
        resourcePermissionMap[resource.code] = {
          visible: roleEditorReadonly.value || Boolean(configured?.visible),
          operable: roleEditorReadonly.value || Boolean(configured?.operable),
        };
      });
      flatDataPermissions.value.forEach((permission) => {
        dataPermissionMap[permission.id] = roleEditorReadonly.value || permission.enable;
      });
      if (resetSnapshot) roleEditorSnapshot.value = serializeRoleEditor();
      if (resetSnapshot) loadedRoleType.value = roleForm.type || 'SYSTEM';
    } finally {
      rolePermissionLoading.value = false;
    }
  }

  async function openRoleEditor(role?: PermissionControlRole) {
    roleForm.id = role?.id;
    roleForm.name = role?.name || '';
    roleForm.description = role?.description || '';
    roleForm.type = role?.type || 'SYSTEM';
    roleForm.enabled = role?.enabled !== false;
    roleEditorReadonly.value = role ? isProtectedAdmin(role) || !canUpdateRole.value : !canAddRole.value;
    roleEditorVisible.value = true;
    await loadRolePermissions();
  }

  async function handleRoleTypeChange() {
    if (
      roleEditorSnapshot.value &&
      serializeRoleEditor() !== roleEditorSnapshot.value &&
      !window.confirm('切换权限范围会清空当前未保存的权限配置，是否继续？')
    ) {
      roleForm.type = loadedRoleType.value;
      return;
    }
    await loadRolePermissions(false);
    loadedRoleType.value = roleForm.type || 'SYSTEM';
  }

  function findResource(code?: string) {
    return code ? flatResources.value.find((item) => item.code === code) : undefined;
  }

  function isDescendant(item: FlatResource, ancestorCode: string) {
    let current: FlatResource | undefined = item;
    const visited = new Set<string>();
    while (current?.parentCode && !visited.has(current.code)) {
      if (current.parentCode === ancestorCode) return true;
      visited.add(current.code);
      current = findResource(current.parentCode);
    }
    return false;
  }

  function setAncestorsVisible(code: string) {
    let resource = findResource(code);
    while (resource?.parentCode) {
      const parent = resourcePermissionMap[resource.parentCode];
      if (parent) parent.visible = true;
      resource = findResource(resource.parentCode);
    }
  }

  function disableDescendants(code: string) {
    const descendants = flatResources.value.filter((item) => isDescendant(item, code));
    descendants.forEach((item) => {
      resourcePermissionMap[item.code].visible = false;
      resourcePermissionMap[item.code].operable = false;
    });
  }

  function syncDataPermission(permissionId: string) {
    const prefix = permissionId.split(':')[0];
    if (isWritePermission(permissionId) && dataPermissionMap[permissionId]) {
      const readId = `${prefix}:READ`;
      if (readId in dataPermissionMap) dataPermissionMap[readId] = true;
    } else if (permissionId.endsWith(':READ') && !dataPermissionMap[permissionId]) {
      flatDataPermissions.value
        .filter((item) => item.id.startsWith(`${prefix}:`) && isWritePermission(item.id))
        .forEach((item) => {
          dataPermissionMap[item.id] = false;
        });
    }
    if (!dataPermissionMap[permissionId]) {
      flatResources.value
        .filter((resource) => resource.permissionId === permissionId)
        .forEach((resource) => {
          resourcePermissionMap[resource.code].operable = false;
        });
    }
  }

  function syncResourcePermission(code: string, operableChanged = false) {
    const value = resourcePermissionMap[code];
    const changedResource = findResource(code);
    if (operableChanged && value.operable) {
      value.visible = true;
      const permissionId = findResource(code)?.permissionId;
      if (permissionId && permissionId in dataPermissionMap) {
        dataPermissionMap[permissionId] = true;
        syncDataPermission(permissionId);
      }
    }
    if (value.visible) setAncestorsVisible(code);
    if (!value.visible) value.operable = false;
    // 未显式接入资源码的旧按钮通过 permissionId 兼容收口；同一接口权限必须作为一个按钮能力组同步，
    // 避免配置页显示为可分别设置而运行时只能按关联权限统一判断。
    if (changedResource?.type === 'BUTTON' && changedResource.permissionId) {
      flatResources.value
        .filter(
          (item) => item.type === 'BUTTON' && item.permissionId === changedResource.permissionId && item.code !== code
        )
        .forEach((item) => {
          resourcePermissionMap[item.code].visible = value.visible;
          resourcePermissionMap[item.code].operable = value.operable;
          if (value.visible) setAncestorsVisible(item.code);
        });
    }
    if (!value.visible) {
      const affected = flatResources.value.filter(
        (item) => resourcePermissionMap[item.code]?.visible && isDescendant(item, code)
      );
      if (affected.length) {
        value.visible = true;
        Modal.warning({
          title: '关闭父资源权限',
          content: `将同时关闭 ${affected.length} 个子资源权限：${affected
            .slice(0, 5)
            .map((item) => item.name)
            .join('、')}${affected.length > 5 ? '等' : ''}。是否继续？`,
          hideCancel: false,
          onOk: () => {
            value.visible = false;
            value.operable = false;
            disableDescendants(code);
          },
        });
      }
    }
  }

  function getRoleChangeSummary() {
    if (!roleEditorSnapshot.value) return { added: 0, removed: 0, roleChanged: 0 };
    const original = JSON.parse(roleEditorSnapshot.value) as {
      role: Partial<PermissionControlRole>;
      data: Record<string, boolean>;
      ui: Record<string, { visible: boolean; operable: boolean }>;
    };
    let added = 0;
    let removed = 0;
    Object.keys(dataPermissionMap).forEach((key) => {
      if (!original.data[key] && dataPermissionMap[key]) added += 1;
      if (original.data[key] && !dataPermissionMap[key]) removed += 1;
    });
    Object.keys(resourcePermissionMap).forEach((key) => {
      const before = original.ui[key] || { visible: false, operable: false };
      const after = resourcePermissionMap[key];
      if (!before.visible && after.visible) added += 1;
      if (before.visible && !after.visible) removed += 1;
      if (!before.operable && after.operable) added += 1;
      if (before.operable && !after.operable) removed += 1;
    });
    const roleChanged = ['name', 'description', 'type', 'enabled'].filter(
      (key) => original.role[key as keyof PermissionControlRole] !== roleForm[key as keyof PermissionControlRole]
    ).length;
    return { added, removed, roleChanged };
  }

  function confirmDiscardRoleChanges() {
    if (roleEditorReadonly.value || serializeRoleEditor() === roleEditorSnapshot.value) return true;
    return window.confirm('存在未保存修改，关闭后本次角色和权限修改将丢失，是否继续？');
  }

  async function saveRole() {
    if (!roleForm.name?.trim() || !roleForm.type) {
      Message.error('角色名称和权限范围不能为空');
      return false;
    }
    const permissions = flatDataPermissions.value.map((permission) => ({
      id: permission.id,
      enable: Boolean(dataPermissionMap[permission.id]),
    }));
    const uiPermissions = flatResources.value.map((resource) => ({
      resourceCode: resource.code,
      ...resourcePermissionMap[resource.code],
    }));
    const changeSummary = getRoleChangeSummary();
    const confirmed = await new Promise<boolean>((resolve) => {
      Modal.confirm({
        title: '保存角色权限',
        content: `变更摘要：新增 ${changeSummary.added} 项权限，移除 ${changeSummary.removed} 项权限，修改 ${changeSummary.roleChanged} 项基础信息。保存后立即生效。`,
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
    if (!confirmed) return false;
    await savePermissionControlRole({
      id: roleForm.id,
      name: roleForm.name.trim(),
      description: roleForm.description,
      type: roleForm.type,
      enabled: roleForm.enabled !== false,
      permissions,
      uiPermissions,
    });
    Message.success('角色及权限已保存');
    roleEditorVisible.value = false;
    await fetchRoles();
    return true;
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
      getPermissionControlFlowMatrix({ scene: flowScope.scene, scopeId: flowScope.scopeId }),
      getPermissionControlFlowRolePermissions(flowId),
    ]);
    flowRoles.value = rolesResult;
    flowMatrix.value = matrixResult || { statuses: [], transitions: [] };
    flowPermissions.value = permissionsResult || [];
    [selectedTransition.value] = flowMatrix.value.transitions;
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

  const handleFlowEnable = async (flow: WorkflowDefinition, enabled: boolean) => {
    await enablePermissionControlFlow({ id: flow.id, enabled });
    Message.success(enabled ? '流程已启用' : '流程已禁用');
    await fetchFlows();
  };

  const confirmDeleteFlow = (flow: WorkflowDefinition) => {
    if (!flow.id || flow.defaultFlow) return;
    Modal.warning({
      title: `删除流程“${flow.name}”`,
      content: '流程及其角色、流转授权将被删除，是否继续？',
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
    flowRoleForm.id = role?.id;
    flowRoleForm.code = role?.code || `BUG_HANDLER_${Date.now()}`;
    flowRoleForm.name = role?.name || '当前处理人';
    flowRoleForm.roleType = role?.roleType || 'FIELD_USER';
    flowRoleForm.roleId = role?.roleId;
    flowRoleForm.fieldKey = role?.fieldKey || 'handle_user';
    flowRoleModalVisible.value = true;
  };

  const handleSaveFlowRole = async () => {
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
      fieldKey: flowRoleForm.roleType === 'FIELD_USER' ? flowRoleForm.fieldKey : undefined,
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
    if (!role.id || !selectedFlow.value?.id) return;
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

  const findTransition = (fromId: string, toId: string) => {
    return flowMatrix.value.transitions.find((transition) => transition.fromId === fromId && transition.toId === toId);
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
    if (!selectedFlow.value?.id) return;
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
    Message.success('流转授权已保存');
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

  onBeforeRouteLeave(async () => {
    if (!roleEditorVisible.value || roleEditorReadonly.value) return true;
    return confirmDiscardRoleChanges();
  });
</script>

<style scoped lang="less">
  .permission-control-page {
    overflow: hidden;
    height: 100%;
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
    grid-template-columns: 300px minmax(520px, 1fr) 360px;
    gap: 16px;
  }
  .flow-card {
    min-height: 560px;
  }
  .card-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }
  .flow-list-item {
    cursor: pointer;
    border-radius: 6px;
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
