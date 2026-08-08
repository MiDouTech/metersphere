# 权限控制改造方案

## 背景

当前系统已有基于角色的权限控制能力，主要由以下部分组成：

- 后端数据表：`user_role`、`user_role_relation`、`user_role_permission`
- 前端权限判断：`hasAnyPermission()`、`hasAllPermission()`、`v-permission`
- 页面权限：路由 `meta.roles`
- 后端接口权限：Shiro `@RequiresPermissions`

现有能力可以控制部分页面和按钮，但不足以满足以下目标：

- 可自行配置用户哪些页面可见
- 可配置用户具有哪些按钮的可见性与操作性
- 存在身份角色配置，可对身份角色配置可见页面与按钮的可见与操作
- 管理员默认具有所有权限，当前已配置的管理员成员不变

## 当前问题

以 `frontend/src/views/bug-management/components/bug-detail-drawer.vue` 为例：

- 编辑按钮使用 `PROJECT_BUG:READ+UPDATE` 控制。
- 更多按钮使用 `PROJECT_BUG:READ+ADD`、`PROJECT_BUG:READ+DELETE` 控制。
- 分享、关注按钮目前没有独立权限控制。
- `v-permission` 当前只支持“不满足权限则移除 DOM”，无法表达“按钮可见但不可操作”。
- 页面权限和按钮权限散落在路由和组件中，没有统一资源目录。
- 前端按钮权限与后端接口权限没有统一映射，容易出现前端与后端权限不一致。

## 总体方案

建议在现有 RBAC 基础上增强，不推翻重建。

权限拆为三层：

| 层级 | 控制内容 | 示例 |
|---|---|---|
| 页面权限 | 用户能否看到、进入某页面 | 缺陷管理、系统设置、项目成员 |
| 按钮可见性 | 用户是否能看到按钮 | 编辑、删除、复制、分享、关注 |
| 操作权限 | 用户点击后是否允许执行 | 接口请求成功或后端返回 403 |

核心原则：

- 管理员默认拥有所有权限。
- 当前已配置的管理员成员不变。
- 角色可配置页面、按钮可见性、按钮可操作性。
- 用户通过角色获得权限。
- 后端接口权限作为最终安全边界。
- 前端只负责体验控制：隐藏、禁用、路由拦截。

## 数据模型改造

保留现有表：

- `user_role`
- `user_role_relation`
- `user_role_permission`

新增权限资源元数据表：

### `permission_resource`

用于统一描述页面、按钮、接口资源。

| 字段 | 说明 |
|---|---|
| id | 主键 |
| code | 资源编码，例如 `BUG_DETAIL_EDIT_BUTTON` |
| name | 展示名称，例如“编辑缺陷” |
| type | `MENU` / `PAGE` / `BUTTON` / `API` |
| scope_type | `SYSTEM` / `ORGANIZATION` / `PROJECT` |
| parent_code | 父级资源编码 |
| route_name | 页面路由名，页面资源使用 |
| permission_id | 关联现有操作权限，例如 `PROJECT_BUG:READ+UPDATE` |
| visible_default | 默认是否可见 |
| operable_default | 默认是否可操作 |
| sort | 排序 |
| enabled | 是否启用 |
| description | 描述 |

新增角色 UI 权限表：

### `user_role_ui_permission`

| 字段 | 说明 |
|---|---|
| id | 主键 |
| role_id | 角色 ID |
| resource_code | 资源编码 |
| visible | 是否可见 |
| operable | 是否可操作 |

权限语义：

- `visible=false`：前端不展示页面或按钮。
- `visible=true, operable=false`：前端展示但禁用。
- `visible=true, operable=true`：前端展示且可点击。
- 真正接口是否允许，仍以 `user_role_permission.permission_id` 和后端 `@RequiresPermissions` 为准。

## 权限计算规则

管理员：

- 所有页面可见。
- 所有按钮可见。
- 所有按钮可操作。
- 所有接口允许。

普通用户：

- 页面可见 = 角色 UI 权限 `visible=true`，或兼容旧 `READ` 权限。
- 按钮可见 = 角色 UI 权限 `visible=true`。
- 按钮可操作 = 角色 UI 权限 `operable=true` 且拥有对应 `permission_id`。
- 接口允许 = 后端 `@RequiresPermissions` 校验通过。

兼容策略：

- 如果没有配置 `user_role_ui_permission`，页面可见性沿用现有 `meta.roles`。
- 如果没有配置按钮 UI 权限，按钮可操作性沿用现有 `v-permission`。
- 这样可以平滑升级，避免现有用户权限突然变化。

## 后端改造

### 1. 新增权限资源接口

用于权限配置页面渲染资源树：

```http
GET /permission/resource/tree?scopeType=PROJECT
GET /permission/resource/tree?scopeType=ORGANIZATION
GET /permission/resource/tree?scopeType=SYSTEM
```

返回示例：

```json
[
  {
    "code": "BUG_MANAGEMENT",
    "name": "缺陷管理",
    "type": "PAGE",
    "children": [
      {
        "code": "BUG_DETAIL_EDIT_BUTTON",
        "name": "编辑",
        "type": "BUTTON",
        "permissionId": "PROJECT_BUG:READ+UPDATE"
      }
    ]
  }
]
```

### 2. 扩展角色权限保存接口

现有接口：

```http
/user/role/project/permission/update
/user/role/organization/permission/update
/user/role/global/permission/update
```

建议扩展请求体：

```json
{
  "roleId": "project_member",
  "permissionIds": ["PROJECT_BUG:READ", "PROJECT_BUG:READ+UPDATE"],
  "uiPermissions": [
    {
      "resourceCode": "BUG_DETAIL_EDIT_BUTTON",
      "visible": true,
      "operable": true
    },
    {
      "resourceCode": "BUG_DETAIL_DELETE_BUTTON",
      "visible": true,
      "operable": false
    }
  ]
}
```

### 3. 登录态返回有效 UI 权限

`/is-login` 返回用户信息时增加 UI 权限：

```json
{
  "uiPermissions": {
    "visible": ["BUG_MANAGEMENT", "BUG_DETAIL_EDIT_BUTTON"],
    "operable": ["BUG_DETAIL_EDIT_BUTTON"]
  }
}
```

也可以按 scope 分组：

```json
{
  "projectUiPermissions": {
    "visible": [],
    "operable": []
  },
  "orgUiPermissions": {
    "visible": [],
    "operable": []
  },
  "systemUiPermissions": {
    "visible": [],
    "operable": []
  }
}
```

### 4. 后端接口权限保持强校验

继续使用：

```java
@RequiresPermissions(PermissionConstants.PROJECT_BUG_UPDATE)
```

前端按钮“可操作”只是体验控制，不能替代后端权限。

## 前端改造

### 1. 扩展权限工具

当前已有：

```ts
hasAnyPermission()
hasAllPermission()
```

建议新增：

```ts
hasPageVisible(resourceCode)
hasButtonVisible(resourceCode)
hasButtonOperable(resourceCode, permissionIds?)
```

示例逻辑：

```ts
function hasButtonOperable(resourceCode, permissionIds) {
  if (userStore.isAdmin) return true;
  return hasUiOperable(resourceCode) && hasAnyPermission(permissionIds);
}
```

### 2. 新增权限指令

当前 `v-permission` 不满足时直接删除 DOM。

建议新增：

```vue
v-visible-permission="'BUG_DETAIL_EDIT_BUTTON'"
v-operable-permission="{ code: 'BUG_DETAIL_EDIT_BUTTON', permissions: ['PROJECT_BUG:READ+UPDATE'] }"
```

行为：

- `v-visible-permission`：不可见则移除 DOM。
- `v-operable-permission`：不可操作则禁用按钮，并加 tooltip：`无操作权限`。

### 3. 路由权限改造

路由增加资源编码：

```ts
meta: {
  resourceCode: 'BUG_MANAGEMENT',
  roles: ['PROJECT_BUG:READ']
}
```

判断逻辑：

```ts
页面可见 = hasPageVisible(resourceCode) && hasAnyPermission(meta.roles)
```

管理员直接通过。

### 4. 缺陷详情按钮资源示例

建议为 `bug-detail-drawer.vue` 按钮建立如下资源：

| 按钮 | resourceCode | 操作权限 |
|---|---|---|
| 编辑 | `BUG_DETAIL_EDIT_BUTTON` | `PROJECT_BUG:READ+UPDATE` |
| 分享 | `BUG_DETAIL_SHARE_BUTTON` | 可新增 `PROJECT_BUG:READ+SHARE`，或只做可见性 |
| 关注 | `BUG_DETAIL_FOLLOW_BUTTON` | 可新增 `PROJECT_BUG:READ+FOLLOW` |
| 复制 | `BUG_DETAIL_COPY_BUTTON` | `PROJECT_BUG:READ+ADD` |
| 删除 | `BUG_DETAIL_DELETE_BUTTON` | `PROJECT_BUG:READ+DELETE` |
| 评论 | `BUG_DETAIL_COMMENT_BUTTON` | `PROJECT_BUG:READ+COMMENT` |

## 管理端页面设计

在“系统设置 / 组织设置 / 项目设置”的用户组权限配置中，使用树形配置：

```text
缺陷管理
  [ ] 页面可见
  缺陷详情
    [ ] 页面可见
    编辑按钮
      [ ] 可见
      [ ] 可操作
    删除按钮
      [ ] 可见
      [ ] 可操作
    分享按钮
      [ ] 可见
      [ ] 可操作
```

交互规则：

- 勾选“可操作”自动勾选“可见”。
- 取消“可见”自动取消“可操作”。
- 子按钮可见时，父页面自动可见。
- 管理员角色展示为“全部权限”，不可取消。
- 内置角色可以限制是否允许编辑，避免误删系统角色。

## 迁移方案

### 阶段 1：权限资源目录落库

- 初始化 `permission_resource`。
- 把现有路由、按钮、接口权限整理成资源树。
- 不改变当前权限行为。

### 阶段 2：兼容生成 UI 权限

根据现有 `user_role_permission` 自动生成默认 UI 权限：

- 有 `xxx:READ` → 页面可见。
- 有 `xxx:READ+UPDATE` → 编辑按钮可见且可操作。
- 有 `xxx:READ+DELETE` → 删除按钮可见且可操作。

### 阶段 3：前端切换到新判断

- 路由使用 `resourceCode`。
- 按钮使用 `v-visible-permission` / `v-operable-permission`。
- 保留 `v-permission` 兼容旧页面。

### 阶段 4：后端接口保持强校验

- 不降低任何 `@RequiresPermissions`。
- 新增按钮权限时同步补充 `PermissionConstants`。
- 对重要操作补充权限测试。

## 实施优先级

建议按以下顺序实施：

1. 权限资源目录表与初始化数据。
2. 登录态返回 UI 权限。
3. 前端权限工具和新指令。
4. 角色权限配置页改造。
5. 先改造缺陷管理页面作为样板。
6. 再逐步覆盖用例、接口、测试计划、系统设置。

## 验收标准

- 管理员用户不受新权限配置影响，默认拥有所有页面和按钮操作能力。
- 现有管理员成员保持不变。
- 普通角色可以配置页面是否可见。
- 普通角色可以配置按钮是否可见。
- 普通角色可以配置按钮是否可操作。
- 按钮可见但不可操作时，按钮置灰并提示无操作权限。
- 用户直接调用无权限接口时，后端仍返回 403。
- 未迁移页面仍按旧权限体系正常工作。

## 关键结论

这次改造应采用“增强型 RBAC + UI 权限资源目录”的方式实现：

- 不重建现有角色权限体系。
- 不破坏已有管理员和角色关系。
- 前端负责页面和按钮体验控制。
- 后端负责最终操作安全。
- 通过资源目录统一管理页面、按钮和接口权限映射。
