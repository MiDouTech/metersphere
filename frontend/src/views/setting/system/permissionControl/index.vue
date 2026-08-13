<template>
  <div class="permission-control-page">
    <a-tabs v-model:active-key="activeTab" lazy-load>
      <a-tab-pane v-if="hasTabVisible('PERMISSION_ROLE_SETTING_TAB', ['SYSTEM'])" key="role" title="角色设置">
        <div class="role-layout">
          <a-card title="角色状态" :bordered="false">
            <a-table :data="roles" :pagination="false" size="small" row-key="id">
              <template #columns>
                <a-table-column title="角色" data-index="name" />
                <a-table-column title="范围" data-index="type" />
                <a-table-column :width="90" title="启用">
                  <template #cell="{ record }">
                    <a-switch
                      v-operable-permission="{
                        code: 'PERMISSION_ROLE_ENABLE_BUTTON',
                        permissions: ['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'],
                        typeList: ['SYSTEM'],
                      }"
                      :model-value="record.enabled !== false"
                      :disabled="record.internal"
                      @change="(value) => handleRoleEnable(record.id, Boolean(value))"
                    />
                  </template>
                </a-table-column>
              </template>
            </a-table>
          </a-card>
          <!-- ui-permission: 权限控制入口自身已迁移，角色权限配置复用旧用户组能力并扩展 UI 资源权限。 -->
          <SystemUserGroup class="role-auth" />
        </div>
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
                  @click="openFlowRoleModal"
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
                <a-descriptions-item label="当前状态">{{ getStatusName(selectedTransition.fromId) }}</a-descriptions-item>
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
                </template>
              </a-table>
            </template>
          </a-card>
        </div>
      </a-tab-pane>

      <a-tab-pane v-if="hasTabVisible('PERMISSION_COMPAT_TAB', ['SYSTEM'])" key="compat" title="兼容策略">
        <a-card :bordered="false">
          <a-descriptions :column="1" bordered>
            <a-descriptions-item label="旧权限代码">
              保留 user_role_permission、v-permission、@RequiresPermissions，不删除。
            </a-descriptions-item>
            <a-descriptions-item label="新 UI 权限">
              页面 / Tab / 按钮由 permission_resource + user_role_ui_permission 控制。
            </a-descriptions-item>
            <a-descriptions-item label="按钮可用">
              新 UI 可用权限与旧接口权限同时满足才可操作。
            </a-descriptions-item>
            <a-descriptions-item label="流程流转">
              缺陷状态变化时服务端强校验流程角色授权；管理员默认通过。
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
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

    <a-modal v-model:visible="flowRoleModalVisible" title="添加流程角色" @ok="handleCreateFlowRole">
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
  </div>
</template>

<script setup lang="ts">
  import { Message } from '@arco-design/web-vue';

  import {
    addPermissionControlFlow,
    addPermissionControlFlowRole,
    enablePermissionControlRole,
    getPermissionControlFlowMatrix,
    getPermissionControlFlowRolePermissions,
    getPermissionControlFlowRoles,
    getPermissionControlFlows,
    getPermissionControlRoles,
    savePermissionControlFlowRolePermissions,
    updatePermissionControlFlow,
  } from '@/api/modules/setting/permissionControl';
  import {
    PermissionControlFlowMatrix,
    PermissionControlStatusFlow,
    StatusFlowRolePermission,
    WorkflowDefinition,
    WorkflowRole,
  } from '@/models/setting/permissionControl';
  import { UserGroupItem } from '@/models/setting/usergroup';
  import { hasTabVisible } from '@/utils/permission';
  import SystemUserGroup from '@/views/setting/system/usergroup/systemUserGroup.vue';

  const flowScope = {
    scene: 'BUG',
    scopeType: 'SYSTEM' as const,
    scopeId: 'system',
  };

  const activeTab = ref('role');
  const roles = ref<UserGroupItem[]>([]);
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
    code: '',
    name: '',
    roleType: 'FIELD_USER' as WorkflowRole['roleType'],
    roleId: undefined as string | undefined,
    fieldKey: 'handle_user',
  });

  const fetchRoles = async () => {
    roles.value = await getPermissionControlRoles();
  };

  const fetchFlows = async () => {
    flows.value = await getPermissionControlFlows(flowScope);
    if (!selectedFlow.value && flows.value.length > 0) {
      await selectFlow(flows.value[0]);
    }
  };

  const fetchFlowConfig = async (flowId: string) => {
    const [rolesResult, matrixResult, permissionsResult] = await Promise.all([
      getPermissionControlFlowRoles(flowId),
      getPermissionControlFlowMatrix({ scene: flowScope.scene, scopeId: flowScope.scopeId }),
      getPermissionControlFlowRolePermissions(flowId),
    ]);
    flowRoles.value = rolesResult;
    flowMatrix.value = matrixResult || { statuses: [], transitions: [] };
    flowPermissions.value = permissionsResult || [];
    selectedTransition.value = flowMatrix.value.transitions[0];
  };

  const handleRoleEnable = async (roleId: string, enabled: boolean) => {
    await enablePermissionControlRole({ roleId, enabled });
    Message.success(enabled ? '角色已启用' : '角色已禁用');
    await fetchRoles();
  };

  const selectFlow = async (flow: WorkflowDefinition) => {
    selectedFlow.value = flow;
    if (flow.id) {
      await fetchFlowConfig(flow.id);
    }
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
    await updatePermissionControlFlow({ id: flow.id, enabled });
    Message.success(enabled ? '流程已启用' : '流程已禁用');
    await fetchFlows();
  };

  const openFlowRoleModal = () => {
    flowRoleForm.code = `BUG_HANDLER_${Date.now()}`;
    flowRoleForm.name = '当前处理人';
    flowRoleForm.roleType = 'FIELD_USER';
    flowRoleForm.roleId = undefined;
    flowRoleForm.fieldKey = 'handle_user';
    flowRoleModalVisible.value = true;
  };

  const handleCreateFlowRole = async () => {
    if (!selectedFlow.value?.id || !flowRoleForm.name || !flowRoleForm.code) {
      Message.error('流程角色名称和编码不能为空');
      return false;
    }
    await addPermissionControlFlowRole({
      flowId: selectedFlow.value.id,
      code: flowRoleForm.code,
      name: flowRoleForm.name,
      roleType: flowRoleForm.roleType,
      roleId: flowRoleForm.roleType === 'SYSTEM_ROLE' ? flowRoleForm.roleId : undefined,
      fieldKey: flowRoleForm.roleType === 'FIELD_USER' ? flowRoleForm.fieldKey : undefined,
      enabled: true,
    });
    Message.success('流程角色已添加');
    flowRoleModalVisible.value = false;
    await fetchFlowConfig(selectedFlow.value.id);
    return true;
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
    fetchRoles();
    fetchFlows();
  });
</script>

<style scoped lang="less">
  .permission-control-page {
    height: 100%;
    overflow: hidden;
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
    align-items: center;
    justify-content: space-between;
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
      border: 1px solid var(--color-border-2);
      padding: 8px;
      text-align: center;
      vertical-align: middle;
    }

    th {
      background: var(--color-fill-1);
      font-weight: 600;
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
    background: rgb(var(--green-1));
    color: rgb(var(--green-7));
  }

  .cell-allow.active {
    border-color: rgb(var(--primary-6));
    background: rgb(var(--primary-1));
    color: rgb(var(--primary-6));
  }

  .cell-deny {
    color: var(--color-text-4);
  }

  .muted {
    color: var(--color-text-3);
    font-size: 12px;
  }

  @media (max-width: 1320px) {
    .flow-layout,
    .role-layout {
      grid-template-columns: 1fr;
    }
  }
</style>
