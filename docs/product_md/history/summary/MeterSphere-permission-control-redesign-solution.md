# MeterSphere 权限控制重构方案设计

## 1. 目标

在“系统设置 - 系统”下新增“权限控制”Tab，统一管理系统级权限配置能力，覆盖：

- 角色设置：角色增删改、启用/禁用、成员分配、页面可见性、页面内 Tab 可见性、按钮可见/可用性。
- 流程控制：主要面向缺陷管理，支持新建流程、维护流程状态流转、添加流程角色并配置流转授权。
- 兼容旧权限：保留现有 `user_role_permission`、`v-permission`、`@RequiresPermissions` 等代码，不直接删除，迁移期间用注释和适配层隔离新旧体系。

核心原则：

- 管理员默认存在，不可删除、不可禁用，拥有全部权限。
- 前端负责体验控制：页面隐藏、按钮隐藏、按钮禁用、路由拦截。
- 页面内 Tab 也作为独立权限资源管理，避免用户进入页面后看到无权访问的业务分区。
- 后端负责最终安全边界：接口权限、流程流转权限必须服务端校验。
- 新权限体系不破坏现有角色、成员、接口权限。
- 支持逐步迁移，未迁移页面继续走旧权限体系。

## 2. 当前代码可复用基础

### 2.1 旧权限体系

当前已有：

- `user_role`：角色/用户组。
- `user_role_relation`：用户与角色关系。
- `user_role_permission`：角色与权限位关系。
- 前端 `hasAnyPermission()`、`hasAllPermission()`、`v-permission`。
- 路由 `meta.roles`。
- 后端 `@RequiresPermissions`。

这些继续保留，作为接口安全与旧页面兼容基础。

### 2.2 新 UI 权限雏形

当前已有：

- `permission_resource`：页面、Tab、按钮、接口资源目录。
- `user_role_ui_permission`：角色 UI 权限配置。
- `/permission/resource/tree`
- `/permission/role-ui/{roleId}`
- 登录态 `uiPermissions`
- 前端 `hasPageVisible()`、`hasButtonVisible()`、`hasButtonOperable()`
- `v-visible-permission`
- `v-operable-permission`

但当前只覆盖缺陷详情样板，需要扩展为全系统资源目录。

### 2.3 状态流转基础

当前已有：

- `status_item`：状态项。
- `status_flow`：状态流转关系，当前只有 `from_id -> to_id`。
- 缺陷管理可根据当前状态查询可流转目标。

缺口：当前 `status_flow` 不支持角色、权限、岗位条件，无法实现“某角色才能执行某条流转”。

补充缺口：当前缺陷状态流转依附组织/项目模板配置，没有独立“流程定义”概念，也没有“流程角色”概念。若需要在权限控制中自行新建流程，需要新增流程定义层，将状态项、状态流、流程角色、角色授权绑定到同一个流程下。

## 3. 信息架构设计

“系统设置 - 系统”新增 Tab：

```text
系统设置
  系统
    用户
    用户组
    组织与项目
    ...
    权限控制
      角色设置
      流程控制
```

权限控制 Tab 内使用二级页面：

1. 角色设置
2. 流程控制

## 4. 角色设置设计

### 4.1 页面布局

角色设置页面分为三栏：

```text
左侧：角色列表
中间：角色基础信息 / 成员分配
右侧：权限配置
```

### 4.2 角色列表

支持：

- 新增角色
- 修改角色
- 删除角色
- 启用/禁用角色
- 搜索角色
- 查看角色来源：系统内置 / 自定义
- 查看成员数量

管理员角色规则：

- 默认存在。
- 不可删除。
- 不可禁用。
- 不可取消权限。
- 页面展示为“全部权限”。

建议字段：

| 字段 | 说明 |
|---|---|
| id | 角色 ID |
| name | 角色名称 |
| code | 角色编码 |
| description | 说明 |
| enabled | 是否启用 |
| internal | 是否内置 |
| scope_type | SYSTEM / ORGANIZATION / PROJECT |
| create_time | 创建时间 |
| update_time | 更新时间 |

当前可复用 `user_role`，如需区分新权限控制入口创建的角色，可增加 `source` 或 `permission_mode` 字段，也可以先不扩表。

### 4.3 成员按岗位分配角色

成员分配支持两种方式：

1. 手动分配成员。
2. 按组织架构和岗位批量分配。

页面能力：

- 左侧组织树。
- 中间岗位筛选。
- 右侧成员列表。
- 支持“将该岗位下成员加入当前角色”。
- 支持移除成员。
- 支持查看成员当前已有角色。

建议新增表：

### `role_assignment_rule`

用于记录“按岗位自动分配角色”的规则。

| 字段 | 说明 |
|---|---|
| id | 主键 |
| role_id | 角色 ID |
| organization_id | 组织 ID |
| department_id | 部门 ID，可为空 |
| position_id | 岗位 ID |
| enabled | 是否启用 |
| sync_mode | MANUAL / AUTO |
| create_time | 创建时间 |
| update_time | 更新时间 |

如果当前组织架构和岗位来自外部系统或企业微信同步，则该表只保存映射规则，不直接复制组织架构数据。

### 4.4 权限配置

权限配置分三层：

```text
模块 / 菜单
  页面
    Tab
      按钮 / 操作
```

权限项包括：

- 页面可见
- Tab 可见
- 按钮可见
- 按钮可用

Tab 权限用于控制页面内部的业务分区。例如：

```text
缺陷详情页
  基础信息 Tab
  关联用例 Tab
  变更历史 Tab
  评论 Tab

测试用例页面
  用例 Tab
    执行用例 Tab
    Xmind 用例 Tab
    用例列表 Tab
    用例详情 Tab
  生成用例 Tab
  自动化执行 Tab
  评审 Tab
  测试报告 Tab
```

Tab 只控制可见性，不建议第一阶段增加“Tab 可用性”。原因是 Tab 本身通常是导航容器，不直接代表业务操作；Tab 内按钮和字段编辑能力仍由按钮可见/可用及接口权限控制。

Tab 支持多级嵌套。顶部业务 Tab、页面内二级 Tab 都统一作为 `TAB` 资源处理，通过 `parent_code` 表达父子关系。

### 4.4.1 权限资源层级

权限资源建议统一为五类：

| 类型 | 说明 | 示例 |
|---|---|---|
| MENU | 一级菜单或模块入口 | 缺陷管理 |
| PAGE | 可路由页面或业务页面 | 缺陷详情页 |
| TAB | 页面内业务分区 | 关联用例 Tab |
| BUTTON | 页面或 Tab 内操作按钮 | 删除缺陷按钮 |
| API | 后端接口资源映射 | `/bug/update` |

资源树示例：

```text
BUG_MANAGEMENT_MENU
  BUG_MANAGEMENT_PAGE
    BUG_LIST_TABLE_TAB
      BUG_CREATE_BUTTON
      BUG_BATCH_DELETE_BUTTON
    BUG_DETAIL_PAGE
      BUG_DETAIL_BASE_INFO_TAB
        BUG_DETAIL_EDIT_BUTTON
      BUG_DETAIL_CASE_TAB
        BUG_DETAIL_LINK_CASE_BUTTON
      BUG_DETAIL_HISTORY_TAB
      BUG_DETAIL_COMMENT_TAB
        BUG_DETAIL_COMMENT_BUTTON

CASE_MANAGEMENT_MENU
  FUNCTIONAL_CASE_PAGE
    FUNCTIONAL_CASE_CASE_TAB
      FUNCTIONAL_CASE_EXECUTE_CASE_TAB
      FUNCTIONAL_CASE_XMIND_CASE_TAB
      FUNCTIONAL_CASE_LIST_TAB
      FUNCTIONAL_CASE_DETAIL_TAB
        FUNCTIONAL_CASE_EDIT_BUTTON
    FUNCTIONAL_CASE_AI_GENERATE_TAB
      FUNCTIONAL_CASE_AI_GENERATE_BUTTON
      FUNCTIONAL_CASE_REQUIREMENT_IMPORT_BUTTON
    FUNCTIONAL_CASE_AUTOMATION_EXECUTION_TAB
    FUNCTIONAL_CASE_REVIEW_TAB
    FUNCTIONAL_CASE_REPORT_TAB
```

### 4.4.2 Tab 权限交互规则

- 页面不可见时，该页面下所有 Tab、按钮全部不可见且不可用。
- Tab 不可见时，该 Tab 下所有按钮全部不可见且不可用。
- 父级 Tab 不可见时，子级 Tab 和子级按钮全部不可见且不可用。
- Tab 可见时，父页面自动可见。
- 子级 Tab 可见时，父级 Tab 和父页面自动可见。
- Tab 下任一按钮可见时，Tab 和父页面自动可见。
- Tab 权限不替代接口权限；用户绕过前端直接调用接口时，仍由后端返回 403。
- 没有显式配置 Tab 权限的旧页面，继续沿用旧页面权限和按钮权限，不强制拦截。

交互规则：

- 勾选“按钮可用”自动勾选“按钮可见”。
- 取消“按钮可见”自动取消“按钮可用”。
- 子按钮可见时，父页面自动可见。
- 父页面不可见时，子按钮全部不可见且不可用。
- Tab 被取消可见时，Tab 下按钮全部不可见且不可用。
- 未迁移页面显示“旧权限控制”，不允许在新 UI 权限中配置或只读展示。

权限计算规则：

```text
页面可见 = 管理员 || UI 页面可见 || 旧 READ 权限兜底
Tab 可见 = 管理员 || UI Tab 可见 || 未配置 Tab 权限时跟随父页面
按钮可见 = 管理员 || UI 按钮可见 || 未迁移时旧权限兜底
按钮可用 = 管理员 || (UI 按钮可用 && 拥有对应接口权限)
接口允许 = @RequiresPermissions 校验通过
```

建议前端新增：

```ts
hasTabVisible(resourceCode, typeList?)
```

Tab 组件使用方式：

```vue
<a-tab-pane
  v-if="hasTabVisible('BUG_DETAIL_CASE_TAB')"
  key="case"
  :title="t('bugManagement.detail.case')"
/>
```

需要修正当前 `hasButtonOperable()`：

```ts
function hasButtonOperable(resourceCode, permissions, typeList) {
  if (userStore.isAdmin) return true;
  if (userStore.uiPermissions) {
    return hasUiOperable(resourceCode, typeList) && hasAnyPermission(permissions || [], typeList);
  }
  return hasAnyPermission(permissions || [], typeList);
}
```

## 5. 流程控制设计

### 5.1 范围

第一阶段主要针对缺陷管理：

- 支持在权限控制中自行新建流程。
- 缺陷状态项可复用现有状态模板能力，也可在新流程中维护独立状态集合。
- 流程控制页重点配置“状态 A 到状态 B 是否允许流转”。
- 支持为每个流程添加流程角色。
- 新增角色维度授权：哪些角色可以看见该流转、哪些角色可以执行该流转。

### 5.2 页面布局

流程控制页面分为：

```text
顶部：模块选择、项目/组织范围选择、新建流程
左侧：流程列表 / 状态列表
中间：流程画布 / 流转矩阵
右侧：流转规则抽屉
```

流程列表支持：

- 新建流程
- 修改流程名称、编码、说明
- 启用/禁用流程
- 复制流程
- 删除未使用流程
- 设置默认流程
- 添加流程角色

建议第一版优先做“流转矩阵”，比画布更容易落地：

| 当前状态 \ 目标状态 | 新建 | 处理中 | 挂起 | 已解决 | 已关闭 | 已拒绝 |
|---|---|---|---|---|---|---|
| 新建 | - | 允许 | 允许 | 禁止 | 禁止 | 允许 |
| 处理中 | 禁止 | - | 允许 | 允许 | 允许 | 允许 |

点击单元格打开右侧规则抽屉。

### 5.3 流转规则

每条流转规则支持：

- 启用/禁用。
- 可见角色。
- 可执行角色。
- 可选接口权限位。
- 可选条件：处理人、创建人、关注人、管理员、字段条件。

第一阶段建议只实现：

- 启用/禁用
- 可执行角色
- 管理员默认全部可执行

后续再扩展字段条件。

### 5.4 流程定义

新增“流程”作为状态流转的上层容器。一个流程包含：

- 流程基础信息。
- 状态集合。
- 状态流转关系。
- 流程角色。
- 流程角色与状态流转授权。

流程基础字段建议：

| 字段 | 说明 |
|---|---|
| id | 主键 |
| code | 流程编码，例如 `BUG_DEFAULT_FLOW` |
| name | 流程名称，例如“默认缺陷流程” |
| scene | 业务场景，例如 `BUG` |
| scope_type | SYSTEM / ORGANIZATION / PROJECT |
| scope_id | 作用范围 ID |
| default_flow | 是否默认流程 |
| enabled | 是否启用 |
| description | 说明 |
| create_time | 创建时间 |
| update_time | 更新时间 |

流程使用规则：

- 缺陷管理第一阶段一个项目默认只启用一个缺陷流程。
- 管理员可以复制已有流程创建新流程。
- 流程已被缺陷引用后，不允许硬删除；只能禁用或归档。
- 流程禁用后，不允许新缺陷使用，但历史缺陷仍按原流程展示。
- 默认流程不可禁用，除非先切换默认流程。

### 5.5 流程角色

流程角色是流程内部的授权对象，用于表达“谁能执行某条流转”。它可以映射到系统角色，也可以映射到缺陷字段中的人员身份。

第一阶段建议支持两类流程角色：

| 类型 | 说明 | 示例 |
|---|---|---|
| SYSTEM_ROLE | 关联权限控制中的角色 | 缺陷负责人、测试经理 |
| FIELD_USER | 关联业务字段中的用户 | 创建人、处理人 |

流程角色字段建议：

| 字段 | 说明 |
|---|---|
| id | 主键 |
| flow_id | 流程 ID |
| code | 流程角色编码 |
| name | 流程角色名称 |
| role_type | SYSTEM_ROLE / FIELD_USER |
| role_id | 当 `role_type=SYSTEM_ROLE` 时关联角色 ID |
| field_key | 当 `role_type=FIELD_USER` 时关联业务字段，例如 `handle_user` |
| enabled | 是否启用 |

流程角色示例：

```text
缺陷负责人 -> SYSTEM_ROLE -> project_bug_owner
测试经理 -> SYSTEM_ROLE -> test_manager
当前处理人 -> FIELD_USER -> handle_user
创建人 -> FIELD_USER -> create_user
```

流转授权时使用流程角色，而不是直接散落绑定用户。这样可以避免每条流转重复配置成员。

### 5.6 数据模型

保留现有：

- `status_item`
- `status_flow`

新增：

### `workflow_definition`

| 字段 | 说明 |
|---|---|
| id | 主键 |
| code | 流程编码 |
| name | 流程名称 |
| scene | 业务场景 |
| scope_type | SYSTEM / ORGANIZATION / PROJECT |
| scope_id | 作用范围 ID |
| default_flow | 是否默认流程 |
| enabled | 是否启用 |
| description | 说明 |

### `workflow_role`

| 字段 | 说明 |
|---|---|
| id | 主键 |
| flow_id | 流程 ID |
| code | 流程角色编码 |
| name | 流程角色名称 |
| role_type | SYSTEM_ROLE / FIELD_USER |
| role_id | 系统角色 ID，可为空 |
| field_key | 业务字段 key，可为空 |
| enabled | 是否启用 |

建议扩展 `status_item`：

| 字段 | 说明 |
|---|---|
| flow_id | 所属流程 ID |

建议扩展 `status_flow`：

| 字段 | 说明 |
|---|---|
| flow_id | 所属流程 ID |

### `status_flow_role_permission`

| 字段 | 说明 |
|---|---|
| id | 主键 |
| flow_id | 流程 ID |
| status_flow_id | 状态流 ID |
| workflow_role_id | 流程角色 ID |
| visible | 是否可见 |
| operable | 是否可执行 |
| enabled | 是否启用 |

如果后续要支持权限位或直接绑定系统角色，可扩展：

| 字段 | 说明 |
|---|---|
| permission_id | 可选，关联旧权限位 |
| role_id | 可选，直接关联系统角色 ID |

### 5.7 服务端校验

缺陷状态变更必须后端校验：

```text
1. 查询当前缺陷当前状态。
2. 查询缺陷所属流程。
3. 查询 from -> to 是否存在有效 status_flow。
4. 查询该 status_flow 绑定的 workflow_role。
5. 判断当前用户是否命中任一可执行 workflow_role。
   - SYSTEM_ROLE：当前用户拥有对应系统角色。
   - FIELD_USER：当前用户等于缺陷对应字段用户。
6. 管理员直接通过。
7. 不通过返回 403。
```

不能只在前端过滤下拉选项。

### 5.8 前端展示

获取可流转状态时，需要后端按当前用户过滤：

```http
GET /bug/status/transitions?projectId=xxx&fromStatusId=xxx
```

返回：

```json
[
  {
    "label": "处理中",
    "value": "status_in_progress",
    "visible": true,
    "operable": true
  }
]
```

不可操作但可见时，前端置灰并提示“当前角色无流转权限”。

## 6. 新旧代码隔离策略

需求要求“原有权限控制代码不作删除，注释处理，避免新旧代码权限互相影响”。

建议不要大面积注释旧代码，否则容易造成安全漏洞。更稳妥的处理方式：

1. 保留旧代码。
2. 新增统一适配层。
3. 已迁移页面走新适配层。
4. 未迁移页面继续走旧逻辑。
5. 对旧 `v-permission` 使用注释标记迁移状态。

示例：

```vue
<!-- legacy-permission: 未迁移页面保留旧权限控制 -->
<MsButton v-permission="['PROJECT_BUG:READ+ADD']" />

<!-- ui-permission: 已迁移，页面/按钮可见性走资源目录，接口权限仍保留 -->
<MsButton
  v-visible-permission="{ code: 'BUG_DETAIL_EDIT_BUTTON', permissions: ['PROJECT_BUG:READ+UPDATE'] }"
  v-operable-permission="{ code: 'BUG_DETAIL_EDIT_BUTTON', permissions: ['PROJECT_BUG:READ+UPDATE'] }"
/>
```

适配层建议：

```ts
hasResourceVisible(resourceCode, fallbackPermissions)
hasResourceOperable(resourceCode, fallbackPermissions)
```

这样新旧权限不会在组件里互相交织。

## 7. 后端接口设计

### 7.1 角色管理

```http
GET    /permission-control/role/list
POST   /permission-control/role/add
POST   /permission-control/role/update
POST   /permission-control/role/enable
POST   /permission-control/role/delete
```

### 7.2 角色成员

```http
GET  /permission-control/role/member/list?roleId=xxx
POST /permission-control/role/member/add
POST /permission-control/role/member/remove
POST /permission-control/role/member/assign-by-position
```

### 7.3 角色权限

```http
GET  /permission-control/resource/tree?scopeType=SYSTEM
GET  /permission-control/role/permission?roleId=xxx
POST /permission-control/role/permission/save
```

保存请求：

```json
{
  "roleId": "project_bug_owner",
  "permissionIds": ["PROJECT_BUG:READ", "PROJECT_BUG:READ+UPDATE"],
  "uiPermissions": [
    {
      "resourceCode": "BUG_MANAGEMENT_PAGE",
      "visible": true,
      "operable": false
    },
    {
      "resourceCode": "BUG_DETAIL_CASE_TAB",
      "visible": true,
      "operable": false
    },
    {
      "resourceCode": "BUG_DETAIL_EDIT_BUTTON",
      "visible": true,
      "operable": true
    }
  ]
}
```

### 7.4 流程控制

```http
GET  /permission-control/flow/list?scene=BUG&scopeId=xxx
POST /permission-control/flow/add
POST /permission-control/flow/update
POST /permission-control/flow/enable
POST /permission-control/flow/delete
GET  /permission-control/flow/status/list?scene=BUG&scopeId=xxx
GET  /permission-control/flow/matrix?scene=BUG&scopeId=xxx
POST /permission-control/flow/status/add
POST /permission-control/flow/status/update
POST /permission-control/flow/transition/update
GET  /permission-control/flow/role/list?flowId=xxx
POST /permission-control/flow/role/add
POST /permission-control/flow/role/update
POST /permission-control/flow/role/delete
GET  /permission-control/flow/role-permission?flowId=xxx
POST /permission-control/flow/role-permission/save
```

新建流程请求：

```json
{
  "scene": "BUG",
  "scopeType": "PROJECT",
  "scopeId": "project_id",
  "code": "BUG_TRIAGE_FLOW",
  "name": "缺陷分诊流程",
  "copyFromFlowId": "default_bug_flow",
  "defaultFlow": false
}
```

新增流程角色请求：

```json
{
  "flowId": "BUG_TRIAGE_FLOW",
  "code": "BUG_HANDLER",
  "name": "当前处理人",
  "roleType": "FIELD_USER",
  "fieldKey": "handle_user"
}
```

## 8. 前端路由设计

新增系统设置子路由：

```ts
{
  path: 'permission-control',
  name: 'systemPermissionControl',
  component: () => import('@/views/setting/system/permissionControl/index.vue'),
  meta: {
    locale: 'system.permissionControl',
    roles: ['SYSTEM_PERMISSION_CONTROL:READ'],
    resourceCode: 'SYSTEM_PERMISSION_CONTROL_PAGE'
  }
}
```

新增权限位建议：

| 权限位 | 说明 |
|---|---|
| SYSTEM_PERMISSION_CONTROL:READ | 查看权限控制 |
| SYSTEM_PERMISSION_CONTROL:READ+ADD | 新增角色/规则 |
| SYSTEM_PERMISSION_CONTROL:READ+UPDATE | 修改角色/权限/流程 |
| SYSTEM_PERMISSION_CONTROL:READ+DELETE | 删除角色/规则 |

## 9. 资源编码规范

页面：

```text
SYSTEM_PERMISSION_CONTROL_PAGE
PERMISSION_ROLE_SETTING_PAGE
PERMISSION_FLOW_CONTROL_PAGE
BUG_MANAGEMENT_PAGE
BUG_DETAIL_PAGE
```

Tab：

```text
BUG_DETAIL_BASE_INFO_TAB
BUG_DETAIL_CASE_TAB
BUG_DETAIL_HISTORY_TAB
BUG_DETAIL_COMMENT_TAB
CASE_DETAIL_BASIC_INFO_TAB
CASE_DETAIL_STEP_TAB
CASE_DETAIL_ATTACHMENT_TAB
TEST_PLAN_DETAIL_PLAN_TAB
TEST_PLAN_DETAIL_CASE_TAB
```

按钮：

```text
PERMISSION_ROLE_ADD_BUTTON
PERMISSION_ROLE_DELETE_BUTTON
PERMISSION_ROLE_ENABLE_BUTTON
PERMISSION_ROLE_SAVE_PERMISSION_BUTTON
PERMISSION_ROLE_ASSIGN_MEMBER_BUTTON
PERMISSION_FLOW_SAVE_BUTTON
PERMISSION_FLOW_ROLE_AUTH_BUTTON
PERMISSION_FLOW_ADD_BUTTON
PERMISSION_FLOW_ROLE_ADD_BUTTON
BUG_STATUS_TRANSITION_BUTTON
```

流程：

```text
BUG_DEFAULT_FLOW
BUG_TRIAGE_FLOW
BUG_FLOW_NEW_TO_IN_PROGRESS
BUG_FLOW_IN_PROGRESS_TO_RESOLVED
BUG_FLOW_RESOLVED_TO_CLOSED
```

## 10. 实施阶段

### 阶段 1：基础框架

- 新增系统设置 Tab：权限控制。
- 新增角色设置页面框架。
- 新增流程控制页面框架。
- 补充权限控制自身的资源编码。
- 将资源类型扩展到 `TAB`，补充 Tab 权限工具函数设计。

### 阶段 2：角色设置

- 复用/扩展 `user_role`。
- 支持角色增删改启停。
- 支持成员手动分配。
- 支持权限树保存。
- 支持页面、Tab、按钮三级 UI 权限保存。
- 修正 `hasButtonOperable()`。

### 阶段 3：岗位分配

- 接入组织架构。
- 支持按岗位批量分配。
- 增加岗位角色规则表。

### 阶段 4：缺陷流程控制

- 新增 `workflow_definition`。
- 新增 `workflow_role`。
- 新增 `status_flow_role_permission`。
- 支持新建、复制、启用/禁用流程。
- 支持添加流程角色。
- 流程矩阵配置。
- 缺陷状态查询按角色过滤。
- 缺陷状态变更后端强校验。

### 阶段 5：全模块迁移

- 系统设置页面迁移。
- 缺陷管理完整迁移。
- 用例、接口、测试计划逐步迁移。
- 旧 `v-permission` 标注迁移状态。

## 11. 验收标准

- 管理员角色不可删除、不可禁用、不可取消权限。
- 普通角色可配置页面可见性。
- 普通角色可配置页面内每个 Tab 的可见性。
- 普通角色可配置按钮可见性。
- 普通角色可配置按钮可用性。
- Tab 不可见时，Tab 下按钮不可见且不可用。
- 按钮可见但不可用时，按钮置灰并提示原因。
- 用户加入角色后，登录态权限立即或重新登录后生效。
- 可按岗位批量分配角色。
- 可在流程控制中新建、复制、启用/禁用缺陷流程。
- 可在流程中添加流程角色，并将流程角色映射到系统角色或业务字段用户。
- 缺陷状态流转可以配置角色执行权限。
- 无流程权限的用户不能在前端看到或执行对应流转。
- 用户绕过前端直接请求状态变更接口时，后端返回 403。
- 未迁移页面仍按旧权限体系工作。

## 12. 需要产品确认的问题

1. “角色”是否等同当前“用户组”，还是需要独立新概念？
2. 是否需要单用户特例权限，覆盖角色权限？
3. 岗位数据来源是否稳定存在，字段来源在哪里？
4. 流程控制第一期是否只做缺陷管理？
5. 流程流转授权是按系统角色、流程角色、权限位，还是三者都支持？建议第一期使用流程角色，流程角色可映射系统角色或业务字段用户。
6. 禁用角色后，成员是否保留关系但不生效？
7. 多角色冲突时是否按“权限并集”处理？建议使用并集。
8. Tab 权限是否只控制可见性？建议第一期只控制可见性。
9. 一个项目是否允许同时启用多个缺陷流程？建议第一期只允许一个默认启用流程。
