<template>
  <div class="role-editor-page p-4">
    <a-page-header :title="pageTitle" subtitle="系统设置 / 权限控制 / 角色设置" @back="goBack">
      <template #extra>
        <a-space>
          <a-button @click="() => goBack()">{{ readonly ? '返回' : '取消' }}</a-button>
          <a-button v-if="!readonly" type="primary" :loading="saving" @click="save">保存</a-button>
        </a-space>
      </template>
    </a-page-header>

    <a-result v-if="notFound" status="404" title="角色不存在或已被删除">
      <template #extra><a-button type="primary" @click="() => goBack(true)">返回角色列表</a-button></template>
    </a-result>

    <a-spin v-else :loading="loading" class="block w-full">
      <a-alert v-if="readonly" class="mb-4" type="info">
        管理员角色受保护，或当前账号只有查看权限，本页面不可修改。
      </a-alert>
      <a-card title="基础信息" :bordered="false">
        <a-form :model="form" layout="vertical">
          <div class="grid grid-cols-2 gap-4">
            <a-form-item field="name" label="角色名称" required>
              <a-input v-model="form.name" :disabled="readonly" placeholder="请输入角色名称" />
            </a-form-item>
            <a-form-item field="type" label="权限范围" required>
              <a-select v-model="form.type" :disabled="readonly || isEdit" @change="changeType">
                <a-option value="SYSTEM">系统</a-option>
                <a-option value="ORGANIZATION">组织</a-option>
                <a-option value="PROJECT">项目</a-option>
              </a-select>
            </a-form-item>
          </div>
          <a-form-item field="description" label="描述">
            <a-textarea v-model="form.description" :disabled="readonly" :max-length="1000" />
          </a-form-item>
          <a-form-item field="enabled" label="启用状态">
            <a-switch v-model="form.enabled" :disabled="readonly" />
          </a-form-item>
        </a-form>
      </a-card>

      <a-card class="mt-4" title="页面、页签与按钮权限" :bordered="false">
        <a-table :data="flatResources" :pagination="false" size="small" row-key="code" :scroll="{ y: 420 }">
          <template #columns>
            <a-table-column title="权限资源" :width="280">
              <template #cell="{ record }">
                <div :style="{ paddingLeft: `${record.depth * 16}px` }">{{ getResourceNameText(record.name) }}</div>
              </template>
            </a-table-column>
            <a-table-column title="类型" :width="100">
              <template #cell="{ record }">{{ getResourceTypeText(record.type) }}</template>
            </a-table-column>
            <a-table-column title="关联接口权限" :width="300">
              <template #cell="{ record }">{{ resourcePermissionText(record) }}</template>
            </a-table-column>
            <a-table-column title="可见" :width="80">
              <template #cell="{ record }">
                <a-checkbox
                  v-if="resourceMap[record.code]"
                  v-model="resourceMap[record.code].visible"
                  :disabled="readonly"
                  @change="syncResource(record.code)"
                />
              </template>
            </a-table-column>
            <a-table-column title="可操作" :width="90">
              <template #cell="{ record }">
                <a-checkbox
                  v-if="resourceMap[record.code] && ['BUTTON', 'API'].includes(record.type)"
                  v-model="resourceMap[record.code].operable"
                  :disabled="readonly"
                  @change="syncResource(record.code, true)"
                />
                <span v-else>-</span>
              </template>
            </a-table-column>
          </template>
        </a-table>
      </a-card>

      <a-card class="mt-4" title="数据操作权限" :bordered="false">
        <a-table :data="flatPermissions" :pagination="false" size="small" row-key="id" :scroll="{ y: 360 }">
          <template #columns>
            <a-table-column title="业务模块" data-index="groupName" :width="260" />
            <a-table-column title="操作权限">
              <template #cell="{ record }">{{ getPermissionText(record.id, record.groupName) }}</template>
            </a-table-column>
            <a-table-column title="授权" :width="90">
              <template #cell="{ record }">
                <a-switch v-model="permissionMap[record.id]" :disabled="readonly" @change="syncPermission(record.id)" />
              </template>
            </a-table-column>
          </template>
        </a-table>
      </a-card>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue';
  import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
  import { Message, Modal } from '@arco-design/web-vue';

  import {
    getPermissionControlPermissionDefinition,
    getPermissionControlResourceTree,
    getPermissionControlRole,
    getPermissionControlRolePermissions,
    getPermissionControlRoleUiPermissions,
    savePermissionControlRole,
  } from '@/api/modules/setting/permissionControl';
  import {
    getPermissionText,
    getResourceNameText,
    getResourceTypeText,
    isWritePermission,
  } from '@/config/permissionLocale';
  import { hasAnyPermission } from '@/utils/permission';

  import type {
    PermissionControlRole,
    PermissionResourceNode,
    RolePermissionItem,
  } from '@/models/setting/permissionControl';

  type FlatResource = PermissionResourceNode & { depth: number };
  type FlatPermission = RolePermissionItem & { groupName: string };

  const route = useRoute();
  const router = useRouter();
  const loading = ref(true);
  const saving = ref(false);
  const notFound = ref(false);
  const snapshot = ref('');
  const loadedType = ref<PermissionControlRole['type']>('SYSTEM');
  const form = reactive<Partial<PermissionControlRole>>({ name: '', description: '', type: 'SYSTEM', enabled: true });
  const resources = ref<PermissionResourceNode[]>([]);
  const permissions = ref<RolePermissionItem[]>([]);
  const resourceMap = reactive<Record<string, { visible: boolean; operable: boolean }>>({});
  const permissionMap = reactive<Record<string, boolean>>({});
  const roleId = computed(() => String(route.params.roleId || ''));
  const isEdit = computed(() => Boolean(roleId.value));
  const canAdd = computed(() => hasAnyPermission(['SYSTEM_PERMISSION_CONTROL:READ+ADD'], ['SYSTEM']));
  const canUpdate = computed(() => hasAnyPermission(['SYSTEM_PERMISSION_CONTROL:READ+UPDATE'], ['SYSTEM']));
  const readonly = computed(() => (isEdit.value ? roleId.value === 'admin' || !canUpdate.value : !canAdd.value));
  const pageTitle = computed(() => {
    if (readonly.value) return '查看角色';
    return isEdit.value ? '编辑角色' : '新增角色';
  });
  const serialize = () => JSON.stringify({ form, resourceMap, permissionMap });

  const flatResources = computed<FlatResource[]>(() => {
    const result: FlatResource[] = [];
    const walk = (nodes: PermissionResourceNode[], depth = 0) =>
      nodes.forEach((node) => {
        result.push({ ...node, depth });
        walk(node.children || [], depth + 1);
      });
    walk(resources.value);
    return result;
  });
  const flatPermissions = computed<FlatPermission[]>(() => {
    const result: FlatPermission[] = [];
    permissions.value.forEach((first) =>
      (first.children || []).forEach((second) =>
        (second.permissions || []).forEach((item) => result.push({ ...item, groupName: second.name }))
      )
    );
    return result;
  });

  function clearMaps() {
    Object.keys(resourceMap).forEach((key) => delete resourceMap[key]);
    Object.keys(permissionMap).forEach((key) => delete permissionMap[key]);
  }

  async function loadPermissions(reset = true) {
    if (!form.type) return;
    const [resourceTree, dataPermissions, uiPermissions] = await Promise.all([
      getPermissionControlResourceTree(form.type),
      form.id ? getPermissionControlRolePermissions(form.id) : getPermissionControlPermissionDefinition(form.type),
      form.id ? getPermissionControlRoleUiPermissions(form.id) : Promise.resolve([]),
    ]);
    resources.value = resourceTree || [];
    permissions.value = dataPermissions || [];
    clearMaps();
    const uiMap = new Map(uiPermissions.map((item) => [item.resourceCode, item]));
    flatResources.value.forEach((resource) => {
      const current = uiMap.get(resource.code);
      resourceMap[resource.code] = {
        visible: Boolean(current?.visible),
        operable: Boolean(current?.operable),
      };
    });
    flatPermissions.value.forEach((item) => {
      permissionMap[item.id] = Boolean(item.enable);
    });
    if (reset) {
      loadedType.value = form.type;
      snapshot.value = serialize();
    }
  }

  async function initialize() {
    loading.value = true;
    try {
      if (isEdit.value) {
        const role = await getPermissionControlRole(roleId.value);
        if (!role) {
          notFound.value = true;
          return;
        }
        Object.assign(form, role);
      }
      await loadPermissions();
    } catch (error: any) {
      if (Number(error?.code) === 100404) {
        notFound.value = true;
        return;
      }
      throw error;
    } finally {
      loading.value = false;
    }
  }

  function findResource(code?: string) {
    return code ? flatResources.value.find((item) => item.code === code) : undefined;
  }
  function isDescendant(item: FlatResource, ancestor: string) {
    let current: FlatResource | undefined = item;
    const visited = new Set<string>();
    while (current?.parentCode && !visited.has(current.code)) {
      if (current.parentCode === ancestor) return true;
      visited.add(current.code);
      current = findResource(current.parentCode);
    }
    return false;
  }
  function showAncestors(code: string) {
    let current = findResource(code);
    while (current?.parentCode) {
      if (resourceMap[current.parentCode]) resourceMap[current.parentCode].visible = true;
      current = findResource(current.parentCode);
    }
  }
  function syncPermission(id: string) {
    const subject = id.split(':')[0];
    if (isWritePermission(id) && permissionMap[id]) {
      if (`${subject}:READ` in permissionMap) permissionMap[`${subject}:READ`] = true;
    } else if (id.endsWith(':READ') && !permissionMap[id]) {
      flatPermissions.value
        .filter((item) => item.id.startsWith(`${subject}:`) && isWritePermission(item.id))
        .forEach((item) => {
          permissionMap[item.id] = false;
        });
    }
  }
  function syncResource(code: string, operableChanged = false) {
    const value = resourceMap[code];
    if (!value) return;
    if (operableChanged && value.operable) {
      value.visible = true;
      const permissionId = findResource(code)?.permissionId;
      if (permissionId && permissionId in permissionMap) {
        permissionMap[permissionId] = true;
        syncPermission(permissionId);
      }
    }
    if (value.visible) showAncestors(code);
    if (!value.visible) {
      value.operable = false;
      flatResources.value
        .filter((item) => isDescendant(item, code))
        .forEach((item) => {
          resourceMap[item.code].visible = false;
          resourceMap[item.code].operable = false;
        });
    }
  }
  function resourcePermissionText(resource: FlatResource) {
    return resource.permissionId
      ? getPermissionText(resource.permissionId, getResourceNameText(resource.name), resource.code)
      : '-';
  }

  async function changeType() {
    if (
      snapshot.value &&
      serialize() !== snapshot.value &&
      !window.confirm('切换权限范围会清空当前未保存配置，是否继续？')
    ) {
      form.type = loadedType.value;
      return;
    }
    await loadPermissions(false);
    loadedType.value = form.type || 'SYSTEM';
  }

  function hasChanges() {
    return !readonly.value && Boolean(snapshot.value) && serialize() !== snapshot.value;
  }
  async function goBack(force = false) {
    if (!force && hasChanges() && !window.confirm('存在未保存修改，离开后本次修改将丢失，是否继续？')) return;
    snapshot.value = '';
    await router.push({ name: 'settingSystemPermissionControl', query: { tab: String(route.query.tab || 'role') } });
  }

  async function save() {
    if (!form.name?.trim() || !form.type) {
      Message.error('角色名称和权限范围不能为空');
      return;
    }
    const confirmed = await new Promise<boolean>((resolve) => {
      Modal.confirm({
        title: '保存角色权限',
        content: '保存后角色基础信息及全部权限立即生效，是否继续？',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
    if (!confirmed) return;
    saving.value = true;
    try {
      const saved = await savePermissionControlRole({
        id: form.id,
        name: form.name.trim(),
        description: form.description,
        type: form.type,
        enabled: form.enabled !== false,
        permissions: flatPermissions.value.map((item) => ({ id: item.id, enable: Boolean(permissionMap[item.id]) })),
        uiPermissions: flatResources.value.map((item) => ({ resourceCode: item.code, ...resourceMap[item.code] })),
      });
      Object.assign(form, saved);
      snapshot.value = serialize();
      Message.success('角色及权限已保存');
      await goBack(true);
    } finally {
      saving.value = false;
    }
  }

  onBeforeRouteLeave(() => !hasChanges() || window.confirm('存在未保存修改，离开后本次修改将丢失，是否继续？'));
  onMounted(initialize);
</script>

<style scoped lang="less">
  .role-editor-page {
    overflow: auto;
    height: 100%;
    background: var(--color-fill-1);
  }
</style>
