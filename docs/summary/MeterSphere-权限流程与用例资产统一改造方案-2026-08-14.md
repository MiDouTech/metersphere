# MeterSphere 权限流程与用例资产统一改造方案

> 日期：2026-08-14  
> 状态：确认版，可进入任务实施  
> 适用版本：当前 `main` 分支  
> 关联页面：权限控制、缺陷管理、Agent 集成、测试资产、测试用例、Agent 调度队列

## 1. 改造结论

本次需求不是八个互不相关的页面微调，而是三条需要统一收口的产品链路：

1. **权限与流程唯一化**：角色编辑改为独立页面；缺陷流程从项目模板迁移到系统级权限控制；列表、详情和后端更新统一使用同一套流程判定。
2. **用例资产中心化**：将当前“跨项目只读视图”升级为可维护的组织级用例资产库，资产目录不是业务项目；项目用例通过“从用例资产导入”获得独立副本并保留来源关系。
3. **Agent 入口和资产选择统一化**：Agent 集成仅保留两个 Tab；调度规则通过共用的用例资产选择器选择或导入用例，不再要求用户手工粘贴 ID。

改造完成后的唯一性原则如下：

- 系统角色只在“系统设置 / 系统 / 权限控制 / 角色设置”维护。
- 本地缺陷流程只在“系统设置 / 系统 / 权限控制 / 流程控制”设计和发布。
- 项目模板中的“缺陷模板 / 工作流设置”不再提供入口。
- 可选缺陷状态只由服务端流程运行时计算，列表与详情不得各自维护状态集合。
- 可复用测试用例只在“测试资产 / 用例资产”维护，业务项目通过导入获得项目用例。

## 2. 需求理解与目标结果

| 编号 | 需求理解 | 目标结果 |
|---|---|---|
| 1 | 角色编辑需要完整、可刷新、可收藏的页面 | 点击新增、编辑、查看进入独立路由，不再打开抽屉 |
| 2 | 缺陷列表与详情必须执行相同的状态流和角色授权 | 只展示当前状态允许到达且当前用户有权执行的下一步状态；后端再次强校验 |
| 3 | 权限控制流程设计器参考缺陷模板工作流，并成为唯一流程来源 | 支持状态、初始/结束状态、流转、流程角色和授权的系统级设计、发布与版本管理 |
| 4 | Agent 集成减少模块层级 | “我的 Agent Token”和“我的 AI Agent”作为两个 Tab；MCP 说明并入 Token Tab |
| 5 | 测试资产导航重新排序 | 业务文档后依次为用例资产、测试数据、测试环境，再展示其他资产入口 |
| 6 | 调度规则无需手工填写用例 ID | 提供“从用例资产选择”入口，选择结果回填并由后端校验或导入到目标项目 |
| 7 | 项目用例的资产来源统一 | “从默认项目导入”改为“从用例资产导入”，数据源改为用例资产目录 |
| 8 | 用例资产从只读聚合页升级为资产库 | 支持目录、新建/导入/编辑/删除用例、引用项目展示及项目创建时自动建立同名目录 |

## 3. 当前实现与主要偏差

### 3.1 权限控制

- 当前角色列表、新增、编辑、权限树和权限矩阵都位于 `frontend/src/views/setting/system/permissionControl/index.vue`。
- 角色编辑使用 `a-drawer`，页面状态不能通过独立 URL 恢复。
- 流程控制已有 `workflow_definition`、`workflow_role`、`status_flow_role_permission` 数据结构，也已为 `status_item`、`status_flow`增加 `flow_id`。
- 当前新建流程只复制流程角色和授权，没有完整复制/维护该流程自己的状态集合与状态流。
- 当前流程页面使用系统范围，但缺陷状态仍主要来自项目模板，状态 ID 与系统级流程授权可能不一致。

### 3.2 缺陷状态

- 详情模板通过 `BugStatusService.getToStatusItemOption(...)` 获取当前状态的下一步选项。
- 列表页使用表头的全量 `statusOption`，导致所有记录共享同一组选项，没有按每条缺陷当前状态和当前用户计算。
- 更新时已有 `PermissionControlService.assertBugTransitionOperable(...)`，但存在两个问题：
  - 没有将“目标状态必须是当前状态的合法下一步”作为独立强校验。
  - 找不到流程角色授权时会兼容旧逻辑直接放行。
- 列表和详情都通过通用缺陷更新接口修改状态，容易绕开专门的流转语义、并发控制和审计。

### 3.3 缺陷模板工作流

- 项目模板复用了 `workflowTable.vue`，已具备状态矩阵、添加状态、初始状态、结束状态和状态流编辑交互。
- 数据仍按组织/项目的 `status_item`、`status_flow` 保存，与权限控制中的系统级流程定义并存，形成两个配置源。

### 3.4 Agent 集成

- `Agent / Agent 集成` 当前将 Token 管理和“我的 AI Agent”上下排列。
- MCP 引导组件已经出现在 Token 管理组件中，但位于搜索和操作区之后；页面仍没有按需求拆成两个 Tab。
- 技能包下载能力应保留，但不再作为独立业务模块存在。

### 3.5 测试资产与用例导入

- 当前用例资产页按用户可访问的真实项目分页，只提供跨项目只读列表。
- `FunctionalCaseController /functional/case/asset/page` 仍以真实 `projectId` 查询项目用例。
- “从默认项目导入”依赖隐藏默认 Hub 项目、Hub 文件夹和异步导入任务，具备可复用基础，但产品概念仍是“默认项目”。
- 现有导入明确不写 Hub 映射，因此导入后的项目用例与资产源用例之间缺少稳定血缘，无法可靠统计“已引用项目”。

## 4. 总体架构

```text
权限控制
├─ 角色设置
│  ├─ 角色列表
│  └─ 角色新增/查看/编辑独立页
└─ 流程控制
   ├─ 流程列表与版本
   ├─ 状态与流转设计器
   └─ 流程角色与流转授权

缺陷管理
├─ 查询可执行流转（统一运行时服务）
├─ 列表状态菜单
├─ 详情状态菜单
└─ 专用状态流转接口

测试资产 / 用例资产
├─ 资产目录（非业务项目）
├─ 资产用例
├─ 资产用例血缘
└─ 共用选择器
   ├─ 项目用例“从用例资产导入”
   └─ Agent 调度规则“从用例资产选择”
```

## 5. 详细改造设计

### 5.1 角色编辑改为独立页面

#### 5.1.1 路由

新增路由：

```text
/setting/system/permission-control/roles/new
/setting/system/permission-control/roles/:roleId
```

建议路由名称：

```text
SETTING_SYSTEM_PERMISSION_CONTROL_ROLE_CREATE
SETTING_SYSTEM_PERMISSION_CONTROL_ROLE_DETAIL
```

角色列表的“新增、查看、编辑”统一使用 `router.push`；成员维护可继续使用弹窗，因为它是列表上下文内的轻量关系操作。

#### 5.1.2 页面结构

将现有抽屉内容提取为：

```text
frontend/src/views/setting/system/permissionControl/role/editor.vue
frontend/src/views/setting/system/permissionControl/role/components/RoleBasicForm.vue
frontend/src/views/setting/system/permissionControl/role/components/RolePermissionTree.vue
frontend/src/views/setting/system/permissionControl/role/components/RolePermissionMatrix.vue
```

页面包含：

- 面包屑和返回角色列表。
- 基础信息。
- 页面/Tab/按钮可见与可操作权限。
- 数据操作权限。
- 保存、取消。
- 管理员角色只读提示。
- 离开未保存页面确认。

#### 5.1.3 兼容规则

- 原权限控制路由仍默认展示角色列表。
- 管理员角色保护规则不变，并继续由后端强制执行。
- 刷新编辑页时重新读取角色、资源树和授权，不依赖列表页内存状态。

#### 5.1.4 验收标准

- 点击编辑后 URL 改变，刷新后仍停留在相同角色。
- 浏览器前进、后退行为正确。
- 未保存离开有提示。
- 只读角色不能通过直接调用接口修改。

### 5.2 缺陷状态统一执行流程控制

#### 5.2.1 统一运行时服务

新增 `BugWorkflowRuntimeService`，作为缺陷状态的唯一判定入口，职责包括：

1. 解析缺陷绑定的流程及流程版本。
2. 读取当前状态的启用流转边。
3. 匹配当前用户的流程角色：系统角色、创建人、当前处理人。
4. 计算“可见”和“可执行”状态。
5. 校验目标状态确实是当前状态的直接下一步。
6. 执行乐观锁更新、写入流转历史和审计日志。

返回模型建议：

```json
{
  "bugId": "bug-id",
  "workflowId": "flow-id",
  "workflowVersion": 3,
  "currentStatus": { "id": "status-new", "name": "新建" },
  "transitions": [
    {
      "transitionId": "new-to-processing",
      "targetStatus": { "id": "status-processing", "name": "处理中" },
      "visible": true,
      "operable": true,
      "matchedRoles": ["开发岗位"],
      "disabledReason": null
    }
  ]
}
```

前端正常状态菜单只展示 `visible=true && operable=true` 的目标状态。没有可执行流转时展示普通状态标签，不显示下拉箭头。

“开发岗位、测试岗位”不在缺陷代码中写死。推荐沿用权限控制的角色体系：先通过岗位分配规则将组织岗位映射到稳定的系统角色，再由流程角色 `SYSTEM_ROLE` 关联这些角色 ID。用户命中任一被授权流程角色即可执行该流转；岗位名称变化不会直接破坏流程。创建人、当前处理人等动态身份继续使用 `FIELD_USER` 表达。

系统管理员可绕过流程角色授权，但仅能选择当前状态真实存在且启用的下一步流转，不能绕过流程版本、状态图、数据权限或第三方平台返回的可达状态。管理员因绕过角色而获得操作权时，前端必须先弹框要求填写原因；后端以管理员身份、`override=true` 和非空 `overrideReason` 三项共同校验，并写入流转历史与审计日志。管理员本身已经命中普通流程角色时按普通流转处理，无需填写绕过原因。

#### 5.2.2 接口

新增：

```text
GET  /bug/{bugId}/transitions
POST /bug/transitions/batch
POST /bug/{bugId}/transition
```

流转请求：

```json
{
  "transitionId": "new-to-processing",
  "targetStatusId": "status-processing",
  "expectedUpdateTime": 1720000000000,
  "comment": "开始处理",
  "override": false,
  "overrideReason": null
}
```

后端执行顺序必须固定：

```text
缺陷存在且可访问
→ 当前状态与 expected 状态一致
→ 流程已发布且可用于该缺陷
→ from → to 流转存在且启用
→ 当前用户匹配至少一个可执行流程角色，或系统管理员按规则发起角色绕过
→ 角色绕过时 overrideReason 非空
→ 原子更新缺陷状态
→ 写历史、通知和审计
```

任一步失败均不更新状态。

#### 5.2.3 列表页

- 列表数据不再共用全量 `statusOption` 作为编辑选项。
- 用户点击某行状态时按需查询 `/bug/{id}/transitions`，或使用当前页批量接口预取，避免 N+1。
- 菜单只显示当前缺陷的合法下一步。
- 状态变更成功后局部刷新该行及统计项；失败恢复原状态。

#### 5.2.4 详情页

- 状态字段使用与列表相同的运行时接口和共用 `BugStatusTransitionSelect` 组件。
- 状态不再作为普通自定义字段随通用表单任意提交。
- 详情保存如果同时修改基础信息和状态，先保存基础信息，再调用专用流转接口；任一步失败需要给出明确结果，不制造部分成功的假象。更推荐交互上将状态变更独立即时提交。

#### 5.2.5 后端封堵

- 通用 `updateBug` 接口发现状态发生变化时，必须转调统一运行时校验，或第一阶段直接拒绝并提示使用状态流转接口。
- 批量编辑、Agent/MCP 缺陷更新、导入、同步等所有可修改状态的入口均调用同一服务。
- 删除“没有角色授权就兼容放行”的生产行为。兼容期仅允许通过显式开关启用，并输出告警审计。
- 非法跨级流转即使目标状态属于流程，也返回 409/业务冲突错误。

#### 5.2.6 第三方缺陷

Jira 等第三方缺陷的远端状态流仍由第三方平台返回；第一期固定采用：

- 第三方平台决定“有哪些下一步”。
- MeterSphere 流程角色决定“当前用户能否操作这些下一步”。
- 状态更新先调用第三方，成功后回写本地；失败不得只改本地。
- 系统管理员只能绕过 MeterSphere 流程角色校验，并强制填写原因，不能绕过第三方平台的可达状态。

### 5.3 全局缺陷流程设计器

#### 5.3.1 产品入口

入口唯一保留在：

```text
系统设置 / 系统 / 权限控制 / 流程控制
```

隐藏：

```text
项目管理 / 模板管理 / 缺陷模板 / 工作流设置
```

隐藏范围只针对缺陷场景 `BUG`；其他仍依赖模板工作流的场景不随本次需求删除。

#### 5.3.2 交互设计

参考当前 `workflowTable.vue`，但将流程列表、设计器和授权整合为完整页面：

```text
左侧：流程列表、版本、草稿/已发布/已归档状态
中部：状态流转矩阵
右侧：选中状态或流转的属性与角色授权
顶部：新建、复制、保存草稿、发布、归档
```

支持：

- 新建流程或复制现有流程。
- 新增、编辑、排序、停用状态。
- 设置唯一初始状态和一个或多个结束状态。
- 点击矩阵单元格新增/删除流转。
- 为流转配置可见角色、可执行角色。
- 流程角色映射到系统角色或缺陷字段用户。
- 发布前执行完整性检查。

发布校验至少包括：

- 恰好一个初始状态。
- 至少一个结束状态。
- 除结束状态外，每个启用状态至少存在一条出边。
- 从初始状态可以到达所有启用状态。
- 不允许流转指向已停用状态。
- 每条启用流转至少有一个可执行角色；除非显式配置为“所有有更新权限者”。
- 状态编码、流程编码在作用域内唯一。

#### 5.3.3 全局生效与版本

建议将“全局生效”定义为：

- `scene=BUG`、`scope_type=SYSTEM`、`scope_id=system`。
- 同一时刻只有一个“默认且已发布”的本地缺陷流程用于新建缺陷。
- 已发布流程不可原地破坏性修改；修改时产生新版本。
- 新缺陷绑定发布时的 `workflow_id + workflow_version`。
- 历史缺陷默认继续使用原流程版本；管理员可发起显式迁移并预览状态映射结果。

这样既保证配置入口唯一，又避免全局流程修改后历史缺陷突然进入无合法状态的情况。

#### 5.3.4 数据模型调整

复用已有表并补充：

`workflow_definition`：

| 新增字段 | 说明 |
|---|---|
| version | 流程版本号 |
| lifecycle | DRAFT / PUBLISHED / ARCHIVED |
| published_time | 发布时间 |
| published_by | 发布人 |
| source_flow_id | 复制或升级来源 |

`status_item`、`status_flow`：

- 强制写入 `flow_id`。
- 状态增加流程内稳定 `code`，名称只用于展示。
- 状态流只能连接同一 `flow_id` 下的状态。

`bug`：

| 新增字段 | 说明 |
|---|---|
| workflow_id | 缺陷绑定流程 |
| workflow_version | 缺陷绑定流程版本 |

`bug_status_transition_history`：

| 字段 | 说明 |
|---|---|
| id | 主键 |
| bug_id | 缺陷 ID |
| workflow_id/version | 执行时流程版本 |
| transition_id | 流转 ID |
| from_status_id/to_status_id | 前后状态 |
| operator | 操作人 |
| matched_role_ids | 命中的流程角色 |
| comment | 操作说明 |
| create_time | 时间 |

#### 5.3.5 项目模板入口迁移

迁移分两步：

1. **双读只写新入口**：项目模板的 BUG 工作流只读并显示“已迁移至权限控制”，提供跳转链接。
2. **隐藏旧入口**：数据迁移和验收通过后，移除 BUG 卡片的“工作流设置”操作及对应菜单入口；旧接口保留一个版本的只读兼容期。

迁移脚本按状态稳定编码合并项目流程；存在差异的项目生成迁移报告，不能静默覆盖。

### 5.4 Agent 集成改为两个 Tab

目标结构：

```text
Agent / Agent 集成
├─ 我的 Agent Token
└─ 我的 AI Agent
```

“我的 Agent Token”Tab 顺序：

1. Token 标题和说明。
2. MCP 技能包说明区，即截图中的平台地址、Scope、连接说明和不可用提示。
3. 创建 Token、下载技能包、搜索框。
4. Token 安全提示。
5. Token 列表。

具体调整：

- `McpOnboardingPanel` 移到搜索/操作区上方。
- 删除独立“MCP 技能包”模块或 Tab，但保留下载技能包按钮和生成配置能力。
- “我的 AI Agent”继续复用 `userAgent.vue`。
- Tab 使用路由 query 或子路由保存，例如 `?tab=token`、`?tab=user-agent`。
- 无“我的 AI Agent”读取权限时只展示 Token Tab，不展示空 Tab。

### 5.5 测试资产导航顺序

目标顺序：

```text
业务文档
用例资产
测试数据
测试环境
资产版本
关联追溯
公共步骤
接口资产
执行证据
缺陷资产
```

需要同步修改：

- `frontend/src/config/pathMap.ts`
- `frontend/src/router/routes/modules/testAsset.ts`
- `TestAssetTabs.vue` 如果其顺序不是直接来源于路由
- 权限资源 `pos` 数据迁移，避免不同账号因数据库排序看到旧顺序
- 菜单/路由自动化测试

历史 URL、路由名称和权限编码保持不变，只调整显示顺序。

### 5.6 调度规则支持从用例资产选择

#### 5.6.1 前端

在“用例 ID”输入区增加：

```text
[从用例资产选择] [清空]
```

点击后打开共用 `CaseAssetSelectorDrawer`：

- 左侧：资产目录，可搜索名称或 ID。
- 右侧：用例列表，可按 ID、名称、标签搜索。
- 支持跨页保留选择、批量选择和已选清单。
- 确认后显示用例标签/表格，而不是只展示不可读的纯文本 ID。
- 保留手工输入作为兼容高级方式，但失焦后解析、去重并查询名称。

#### 5.6.2 与目标项目的关系

调度规则已有当前项目上下文，而资产用例位于资产库。建议采用显式动作：

```text
选择资产用例
→ 确认“导入到当前项目并加入调度规则”
→ 后端复制到当前项目并返回项目用例 ID
→ 调度规则保存项目用例 ID
```

已导入且来源相同的资产用例，根据冲突策略选择复用现有副本或创建新副本。不能把资产库用例 ID 直接塞给仅接受项目用例的执行服务。

#### 5.6.3 后端

- 保存调度规则时仍校验所有 `caseIds` 属于规则项目且当前用户有执行权限。
- 前端选择器不能替代后端范围校验。
- 资产导入和规则保存建议使用“导入任务完成后再保存规则”的两阶段交互；异步导入失败时不得保存半有效规则。

### 5.7 “从默认项目导入”改为“从用例资产导入”

#### 5.7.1 前端

- 文案统一改为“从用例资产导入”。
- 原 `importFromDefaultModal.vue` 改造为共用资产选择器。
- 选择树层级改为“资产目录 / 模块 / 用例”。
- 支持选择目标模块、冲突策略和是否携带附件/自定义字段。

#### 5.7.2 后端

新增语义化接口：

```text
GET  /functional/case/asset/import/tree
POST /functional/case/asset/import
GET  /functional/case/asset/import/job/{jobId}
```

请求示例：

```json
{
  "targetProjectId": "project-id",
  "targetModuleId": "module-id",
  "assetCaseIds": ["asset-case-1", "asset-case-2"],
  "conflictStrategy": "SKIP",
  "copyAttachments": false
}
```

复用现有异步导入、进度和冲突处理能力，但将来源从“唯一默认项目”改为“当前组织用例资产库”。旧接口保留一版兼容并内部转发，前端不再调用。

每次复制必须写入资产血缘关系，不能继续采用“不写 Hub map”的行为。

### 5.8 用例资产升级为可维护资产库

#### 5.8.1 资产目录定义

页面中的“项目”实质为**资产目录**：

- 不写入业务 `project` 表。
- 不出现在项目管理、项目切换或项目权限列表。
- 仅用于组织、检索和维护可复用用例。
- 建议作用域为组织，避免不同租户同名目录合并。

为降低改造风险，底层继续使用隐藏 Hub 项目保存 `functional_case` 正文和步骤，但通过专用资产服务隔离，前端和公开接口不暴露隐藏项目概念。

#### 5.8.2 目录数据模型

新增 `case_asset_catalog`：

| 字段 | 说明 |
|---|---|
| id | 资产目录 ID |
| organization_id | 所属组织 |
| name | 展示名称 |
| normalized_name | 去空格、统一大小写后的合并键 |
| hub_project_id | 内部存储项目，不对产品暴露 |
| hub_module_id | 对应 Hub 根文件夹 |
| source_type | MANUAL / PROJECT / MERGED |
| manually_renamed | 是否人工改名 |
| enabled/deleted | 状态 |
| create/update 信息 | 审计字段 |

唯一约束：

```text
(organization_id, normalized_name, deleted)
```

新增 `case_asset_catalog_project_rel`：

| 字段 | 说明 |
|---|---|
| catalog_id | 资产目录 |
| project_id | 真实业务项目 |
| relation_type | AUTO_PROJECT / MANUAL_LINK |
| create_time | 建立时间 |

一个目录允许关联多个同名真实项目，从而满足“已有同名目录则合并处理”，同时不以名称作为运行时外键。

#### 5.8.3 项目自动建目录

利用现有 `CreateProjectResourceService` 扩展点增加 `CreateCaseAssetCatalogResourceService`：

```text
真实项目创建成功
→ 在同一组织按 normalized_name 查目录
→ 不存在则创建 PROJECT 来源目录
→ 已存在则复用并改为 MERGED
→ 写 catalog_project_rel
```

处理规则建议：

- 项目创建失败时不留下资产目录孤儿。
- 目录创建失败是否回滚项目创建需按事务边界决定；推荐写可靠事件并重试，不阻断核心项目创建。
- 项目删除只解除关系，不删除目录及资产用例。
- 项目改名时，仅当目录只关联该项目且从未人工改名时自动同步名称；否则保持目录名称并提示管理员处理。
- 只要项目已关联资产目录，提交改名前必须弹出二次确认：`该项目已关联测试资产：{目录名称}，是否确认改名`。用户取消则项目及目录均不改名；确认后再按上一条同步规则处理目录名称。

#### 5.8.4 页面交互

左侧目录区：

- 新建目录按钮，产品文案可继续叫“新建用例项目”，帮助提示明确“仅创建用例资产目录，不创建业务项目”。
- 目录名称加粗、紫色。
- 目录 ID 比名称小一号、灰色。
- 支持名称/ID 搜索、分页或虚拟滚动。

右侧用例区：

- 新建单条用例。
- 导入用例，能力与项目功能用例导入一致，目标改为资产目录。
- 搜索、分页、删除。
- 点击用例 ID 或用例名称进入资产用例详情页。
- 详情支持编辑、保存和返回资产目录。
- 删除“状态”列。
- 新增“已引用项目”列，项目名称去重展示，数量较多时折叠并提供明细。

建议列表字段：

```text
用例 ID、用例名称、所属模块、用例等级、标签、已引用项目、创建人、更新人、更新时间、操作
```

#### 5.8.5 资产用例详情

新增独立路由：

```text
/test-asset/cases/catalog/:catalogId/case/:caseId
```

不能通过先切换到隐藏 Hub 项目再复用普通项目路由。可以复用功能用例表单组件，但数据加载、权限和保存接口必须是资产专用接口。

#### 5.8.6 导入血缘与“已引用项目”

新增 `case_asset_case_lineage`：

| 字段 | 说明 |
|---|---|
| id | 主键 |
| asset_case_id | 资产源用例 |
| target_case_id | 导入后的项目用例 |
| target_project_id | 目标项目 |
| import_job_id | 导入任务 |
| relation_type | COPY / SYNC |
| source_version | 导入时资产版本 |
| create_time/create_user | 审计 |

“已引用项目”计算口径：

```text
资产用例
→ case_asset_case_lineage 找到项目用例
→ test_plan_functional_case 找到测试计划
→ test_plan 取得所属项目
→ 按项目 ID DISTINCT
```

因此，同一项目中多个测试计划引用同一资产来源用例，只展示一次项目。

建议提供聚合接口或在列表 SQL 中一次性聚合，禁止前端逐行查询：

```text
GET /functional/case/asset/{caseId}/referenced-projects
```

列表接口直接返回前若干项目和总数：

```json
{
  "referencedProjects": [
    { "id": "project-1", "name": "物流项目" }
  ],
  "referencedProjectCount": 1
}
```

#### 5.8.7 删除规则

- 资产用例删除采用软删除。
- 已导入到项目的副本不随资产用例删除。
- 有引用项目时删除前展示影响范围，但允许具备删除权限的用户确认删除。
- 删除后历史血缘和测试计划数据保留，资产列表不再提供该用例给新的导入/调度规则。

## 6. 共用前端组件

建议新增：

```text
frontend/src/components/business/case-asset-selector/
├─ index.vue
├─ CatalogTree.vue
├─ AssetCaseTable.vue
├─ SelectedCasePanel.vue
└─ types.ts
```

复用入口：

- 项目功能用例“从用例资产导入”。
- Agent 调度规则用例选择。
- 后续测试计划添加资产用例。

组件只负责选择，不直接信任或拼接业务权限；调用方明确传入 `targetProjectId`、选择模式和确认行为。

## 7. 后端接口清单

### 7.1 权限角色

现有角色 API 可复用，主要改前端路由和组件拆分，无需因抽屉改页面而新增接口。

### 7.2 流程设计

```text
GET  /permission-control/flow/list
POST /permission-control/flow/add
POST /permission-control/flow/update
POST /permission-control/flow/{id}/publish
POST /permission-control/flow/{id}/archive
POST /permission-control/flow/{id}/copy
GET  /permission-control/flow/{id}/designer
POST /permission-control/flow/{id}/status
POST /permission-control/flow/{id}/transition
POST /permission-control/flow/{id}/role-permission/save
POST /permission-control/flow/{id}/validate
```

### 7.3 缺陷运行时

```text
GET  /bug/{bugId}/transitions
POST /bug/transitions/batch
POST /bug/{bugId}/transition
GET  /bug/{bugId}/transition-history
```

### 7.4 用例资产

```text
POST /functional/case/asset/catalog/page
POST /functional/case/asset/catalog
PUT  /functional/case/asset/catalog/{id}
DELETE /functional/case/asset/catalog/{id}
POST /functional/case/asset/page
POST /functional/case/asset/case
GET  /functional/case/asset/case/{id}
PUT  /functional/case/asset/case/{id}
DELETE /functional/case/asset/case/{id}
POST /functional/case/asset/import-file
GET  /functional/case/asset/import/tree
POST /functional/case/asset/import
GET  /functional/case/asset/import/job/{jobId}
GET  /functional/case/asset/{caseId}/referenced-projects
```

## 8. 权限设计

建议新增或确认以下权限：

| 权限 | 用途 |
|---|---|
| SYSTEM_PERMISSION_CONTROL:READ+ADD/UPDATE/DELETE | 角色与全局流程维护 |
| SYSTEM_PERMISSION_CONTROL:FLOW_PUBLISH | 发布全局流程 |
| PROJECT_BUG:READ+UPDATE | 缺陷基础更新前置权限 |
| PROJECT_BUG:TRANSITION | 缺陷状态流转接口权限 |
| TEST_ASSET_CASE:READ | 查看资产目录和资产用例 |
| TEST_ASSET_CASE:READ+ADD | 新建目录、用例、导入文件 |
| TEST_ASSET_CASE:READ+UPDATE | 编辑资产目录和资产用例 |
| TEST_ASSET_CASE:READ+DELETE | 删除资产目录和资产用例 |
| TEST_ASSET_CASE:READ+IMPORT | 将资产用例导入项目 |

缺陷状态流转必须同时满足：

```text
接口权限 PROJECT_BUG:TRANSITION
AND 对缺陷所在项目有访问权
AND 目标是合法下一步
AND 命中该流转的可执行流程角色
```

前端隐藏按钮不是授权依据。

## 9. 数据迁移与兼容

### 9.1 缺陷流程迁移

1. 为当前有效项目 BUG 工作流生成差异报告。
2. 按稳定状态编码归并状态，不按数据库 ID 直接合并。
3. 选择一个基线生成系统级默认流程草稿。
4. 管理员确认差异后发布。
5. 为存量本地缺陷回填 `workflow_id/workflow_version` 并映射状态 ID。
6. 映射失败的缺陷写入迁移异常表，不允许自动赋任意状态。
7. 观察期只读旧项目工作流，最终隐藏入口。

### 9.2 用例资产迁移

1. 读取当前默认 Hub 项目的 FOLDER 模块，生成 `case_asset_catalog`。
2. `ref_project_id` 转为 `case_asset_catalog_project_rel`。
3. Hub 中已有用例继续作为资产用例，不复制正文。
4. `default_hub_case_map` 可迁移到新血缘表。
5. 历史“从默认项目导入”由于原实现不写映射，无法百分之百反推来源；仅对可通过现有 map、唯一内容哈希和审计任务确定的记录回填，其他标记为“历史来源未知”。

### 9.3 接口兼容

- 旧默认 Hub 导入接口保留一个版本，内部转发到资产导入服务。
- 旧项目工作流写接口在双读阶段返回“已迁移，请前往权限控制”，不再接受新配置。
- 原用例资产项目只读接口在新页面切换后保留，供旧客户端兼容，不作为新资产库写入口。

## 10. 实施阶段

### 阶段 0：决策冻结与数据预检

- 已确认决策以第 13 节为准，实施中不得由各任务自行改变口径。
- 统计各项目 BUG 状态/流转差异。
- 统计默认 Hub、目录、用例、导入任务和可回填血缘数量。
- 输出迁移预览，不修改生产数据。

### 阶段 1：低风险页面结构

- 角色编辑独立路由。
- Agent 集成两个 Tab 和 MCP 说明位置调整。
- 测试资产导航排序。

### 阶段 2：全局流程设计器

- 补齐流程版本、状态归属、发布校验和审计。
- 迁移项目 BUG 工作流为系统流程草稿。
- 先保留项目模板旧入口只读。

### 阶段 3：缺陷运行时闭环

- 统一流转查询与执行服务。
- 列表、详情改用共用组件。
- 通用更新、批量、Agent/MCP 入口后端封堵。
- 完成角色、非法跨级和并发测试后发布流程。

### 阶段 4：用例资产中心

- 资产目录和真实项目关系。
- 新建、导入、编辑、删除资产用例。
- 资产专用详情路由和权限。
- 导入血缘及“已引用项目”。

### 阶段 5：导入和调度选择器

- 项目用例改为从资产导入。
- 调度规则接入共用选择器。
- 完成导入任务与规则保存的两阶段闭环。
- 隐藏旧默认项目文案和入口。

### 阶段 6：旧入口下线

- 隐藏项目缺陷模板工作流设置。
- 停止旧工作流写接口。
- 移除独立 MCP 技能包模块。
- 观察期后清理不再使用的前端代码，数据库历史表按归档策略处理。

## 11. 测试与验收

### 11.1 权限与流程

- 开发岗位在“新建”状态只看到“处理中、已拒绝”。
- 测试岗位按配置看到相同或不同的合法下一步。
- 非流程人员看不到状态下拉且调用接口返回 403/业务拒绝。
- 有更新权限但没有流程角色的人不能流转。
- 直接提交非下一步状态被后端拒绝。
- 列表和详情对同一用户、同一缺陷返回相同选项。
- 两人同时流转时仅一个成功，另一个收到状态已变化提示。
- 管理员未命中流程角色时仍只能选择合法下一步，且必须填写绕过原因；缺少原因或尝试跨级流转时后端拒绝，成功后历史和审计均可追溯。

### 11.2 流程设计

- 不完整流程不能发布。
- 每个新流程的状态和流转互相隔离。
- 全局只能有一个默认已发布 BUG 流程。
- 发布新版本后历史缺陷仍绑定创建时流程版本，未执行显式迁移时行为不变。
- 项目模板 BUG 工作流入口不可继续写入第二套配置。

### 11.3 用例资产

- 手工新建目录不会创建业务项目。
- 新建真实项目会自动创建或合并同名目录。
- 同组织同名目录不重复，不同组织互不合并。
- 已关联资产目录的项目改名前出现包含目录名称的二次确认；取消不产生任何改名，确认后按自动同步规则处理。
- 资产用例可新增、导入、编辑、软删除。
- ID 和名称均可进入详情。
- 列表无状态列，有去重后的已引用项目。
- 同项目多个测试计划引用只统计一个项目。
- 删除资产用例不删除已导入项目副本。

### 11.4 Agent 与导航

- Agent 集成只有两个 Tab。
- MCP 说明位于 Token 搜索区上方，下载能力仍可用。
- 测试资产顺序在不同角色和刷新后保持一致。
- 调度规则可通过资产选择器完成选择、导入和保存。
- 无权限资产、跨组织资产和已删除资产不能写入规则。

## 12. 风险与控制

| 风险 | 控制措施 |
|---|---|
| 项目流程差异被全局流程覆盖 | 先生成差异报告，人工确认后发布，不静默合并 |
| 状态 ID 从项目级迁移到流程级导致历史数据失配 | 使用稳定编码映射，失败项进入异常表并阻断自动迁移 |
| 前端限制被接口绕过 | 所有状态写入口统一调用运行时服务 |
| 用例资产继续伪装成业务项目 | 隐藏 Hub 仅作为存储实现，产品和公开接口使用资产目录 ID |
| 历史导入用例无法计算来源 | 只回填可证明关系，未知来源不猜测 |
| 调度规则保存资产 ID 后执行失败 | 规则只保存导入后的目标项目用例 ID |
| 项目创建与资产目录同步失败 | 使用幂等 upsert、可靠事件和补偿任务 |
| 引用项目聚合查询性能下降 | 建索引、服务端聚合、分页返回，禁止前端 N+1 |

## 13. 人工确认结论（已冻结）

以下结论是方案、任务实现和验收的统一口径；如需改变，必须先变更本方案并重新评估数据迁移和接口兼容影响：

1. **历史缺陷流程版本**：新发布流程只用于新缺陷；历史缺陷继续绑定创建时的流程版本。历史缺陷切换版本必须由管理员显式发起，先展示状态映射预览，存在未映射状态时禁止自动迁移。
2. **管理员角色绕过**：系统管理员可绕过流程角色授权，但不能绕过合法流转、流程版本、数据权限或第三方平台可达状态；绕过时前端强制弹框填写原因，后端强制校验并记录历史和审计。
3. **第三方缺陷**：第三方平台决定可达状态，MeterSphere 流程角色决定谁可操作，不使用本地全局状态图替换第三方状态图。
4. **全局范围**：流程在系统级对平台全部组织生效，同一时刻只有一个默认已发布的本地缺陷流程。
5. **资产目录同名合并**：仅在同一组织内按规范化名称合并，不同组织严格隔离。
6. **项目改名**：目录仅关联一个项目且从未人工改名时才自动同步；已合并或人工命名目录不随项目改名。项目已关联目录时必须二次确认，提示 `该项目已关联测试资产：{目录名称}，是否确认改名`。
7. **调度规则选择资产用例**：执行“导入到当前项目并加入规则”，规则最终保存导入后的项目用例 ID，不直接保存或执行资产源用例 ID。
8. **已引用项目口径**：只有资产导入形成的项目用例副本实际被该项目测试计划关联时才计入；仅导入但未进入任何测试计划不计入，同一项目多个计划引用只计一次。
9. **资产用例删除**：采用软删除；已有项目副本和历史计划不受影响，已删除资产禁止再次导入，不因存在引用而禁止删除。

## 14. 任务拆分建议

对应实施任务已细化至 [`docs/task/permission_flow_case_asset_unification_20260814`](../task/permission_flow_case_asset_unification_20260814/task000-实施总览与依赖关系.md)，任务执行以第 13 节冻结结论为前提。

| 任务 | 优先级 | 依赖 |
|---|---|---|
| task001 角色编辑独立路由与组件拆分 | P1 | 无 |
| task002 Agent 集成双 Tab 与 MCP 说明合并 | P1 | 无 |
| task003 测试资产导航顺序与权限资源排序 | P1 | 无 |
| task004 全局流程数据模型、版本和发布校验 | P0 | 无（按冻结结论 1、4 实施） |
| task005 项目缺陷工作流迁移预检和迁移工具 | P0 | task004 |
| task006 缺陷流程运行时查询与专用流转接口 | P0 | task004 |
| task007 缺陷列表/详情共用状态流转组件 | P0 | task006 |
| task008 全部缺陷状态写入口后端封堵 | P0 | task006 |
| task009 用例资产目录与项目自动关联 | P0 | 无（按冻结结论 5、6 实施） |
| task010 资产用例 CRUD、导入和详情 | P0 | task009 |
| task011 资产导入血缘与引用项目聚合 | P0 | task010 |
| task012 共用用例资产选择器 | P1 | task010 |
| task013 项目用例从资产导入 | P0 | task011、task012 |
| task014 调度规则资产选择与导入闭环 | P1 | task012、task013 |
| task015 旧流程/默认项目/MCP 独立入口下线 | P1 | 前述任务验收完成 |
