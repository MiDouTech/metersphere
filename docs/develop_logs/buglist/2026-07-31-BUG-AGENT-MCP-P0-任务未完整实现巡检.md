# 2026-07-31 BUG - Agent MCP P0/P1 任务未完整实现巡检

## 基本信息

- 巡检日期：2026-07-31
- 巡检范围：
  - `docs/summary/MeterSphere-Agent-MCP测试用例与缺陷管理扩展方案-2026-07-31.md`
  - `docs/task/agent_mcp_case_bug_extension_20260731`
  - 当前已提交代码
- 结论级别：严重
- 当前结论：已提交代码未完整执行任务目录中的 P0 任务，尤其后端 MCP 附件闭环、测试用例更新、缺陷附件能力缺失；部分执行结果文档存在已完成/未完成口径冲突。

## 总体结论

当前提交只完成了前端体验和 MCP 后端能力中的一小部分。方案和任务清单中明确要求的 P0 核心闭环未完成，不能标记为 P0 已交付。

主要缺口：

- Agent 通用临时附件上传未实现。
- 用例详情附件查询/关联/删除未实现。
- 缺陷详情附件查询/关联/删除未实现。
- `functional.case.update` 未实现。
- requestId 落库幂等、参数冲突检测、乐观锁、结构化错误码未实现。
- 缺陷列表状态样式未按预览图实现。
- 部分已实现 MCP 工具与方案命名不一致。

## 一、P0 后端任务缺失

### 1. task001：Agent 临时附件与详情附件闭环

状态：未实现。

应实现但当前缺失：

- `POST /api/agent/v1/attachment/upload`
- `purpose` 参数：
  - `CASE_DETAIL`
  - `CASE_COMMENT`
  - `BUG_DETAIL`
  - `BUG_COMMENT`
  - `EXECUTION`
- `expiresAt`
- 临时附件存储模型
- Token ID / 用户 ID / 项目 ID / MIME / size / linked 状态
- 24 小时过期清理
- `ATTACHMENT_EXPIRED`
- `ATTACHMENT_PURPOSE_MISMATCH`
- `metersphere.functional.attachment.attach`
- `metersphere.functional.attachment.delete`
- `metersphere.bug.attachment.attach`
- `metersphere.bug.attachment.delete`

当前仅存在旧接口：

```text
POST /api/agent/v1/functional/attachment/upload
```

该接口只是执行证据附件上传，写入 `agent_exec_attachment`，不能等同于方案要求的通用临时附件上传，也不能完成“用例详情/缺陷详情附件可见”闭环。

### 2. task003：测试用例更新、评论与附件工具

状态：部分实现。

已实现：

- `metersphere.functional.comments.list`
- `metersphere.functional.comment.create`

未实现：

- `metersphere.functional.template.get`
- `metersphere.functional.history.list`
- `metersphere.functional.attachments.list`
- `metersphere.functional.case.update`
- `metersphere.functional.attachment.attach`
- `metersphere.functional.attachment.delete`
- 用例 Patch 更新语义
- `expectedUpdateTime` 乐观锁
- 用例附件关联到详情附件区域
- 评论附件通过临时附件关联
- requestId 落库级幂等
- 业务日志、通知、Agent 审计完整闭环

### 3. task004：缺陷评论、附件与基础工具增强

状态：部分实现。

已实现：

- `metersphere.bug.comments.list`
- `metersphere.bug.comment.create`

未实现：

- `metersphere.bug.template.get`
- `metersphere.bug.attachments.list`
- `metersphere.bug.history.list`
- `metersphere.bug.attachment.attach`
- `metersphere.bug.attachment.delete`
- `bug.create` / `bug.update` 支持 `attachmentIds`
- `bug.create` / `bug.update` 支持 `addCaseIds`
- `bug.create` / `bug.update` 支持 `removeRelationIds`
- 缺陷附件进入详情附件区域
- 缺陷评论附件闭环
- 模板字段查询，禁止 Agent 猜字段 ID

### 4. task007：Scope、幂等、乐观锁与审计安全

状态：部分实现。

已实现：

- 新增 Scope 常量：
  - `CASE_UPDATE`
  - `CASE_DELETE`
  - `CASE_COMMENT`
  - `CASE_ATTACHMENT`
  - `BUG_DELETE`
  - `BUG_COMMENT`
  - `BUG_ATTACHMENT`
  - `BUG_RELATE`
- `FUNCTIONAL_ALL` 覆盖用例类 Scope。
- `BUG_WRITE` 仅覆盖 `BUG_READ`。

未实现或不完整：

- requestId 仅为 JVM 内存缓存，不是落库幂等。
- 没有检测“相同 requestId 不同参数”的 `IDEMPOTENCY_CONFLICT`。
- 没有 `expectedUpdateTime` 乐观锁。
- 没有 `VERSION_CONFLICT`。
- 所有新增写操作未形成统一审计字段。
- JSON-RPC 错误没有按方案在 `error.data` 中返回业务错误码。
- 部分 MCP 写服务没有统一项目白名单/资源归属校验链路，尤其缺陷 create/update/relate 直接使用请求中的 `projectId`。

### 5. task012：全链路验收与防退化

状态：未完整实现。

未实现：

- PNG 上传后出现在用例详情的集成测试。
- PNG 上传后出现在缺陷详情的集成测试。
- 执行日志附件不混入详情附件的测试。
- MCP 契约测试。
- `readOnlyHint` / `destructiveHint` / `idempotentHint` 全量校验。
- 参数非法时不进入业务 Service 的测试。
- 前端关键页面截图验收。
- 问题清单未按真实状态更新。

## 二、MCP 工具巡检结果

### 已找到的新增工具

```text
metersphere.functional.comments.list
metersphere.functional.comment.create
metersphere.bug.comments.list
metersphere.bug.comment.create
metersphere.bug.relate_case
```

其中：

```text
metersphere.bug.relate_case
```

与方案要求命名不一致。方案中是：

```text
metersphere.bug.case.relate
```

### 未找到的关键工具

```text
metersphere.functional.template.get
metersphere.functional.history.list
metersphere.functional.attachments.list
metersphere.functional.case.update
metersphere.functional.attachment.attach
metersphere.functional.attachment.delete
metersphere.bug.template.get
metersphere.bug.attachments.list
metersphere.bug.history.list
metersphere.bug.attachment.attach
metersphere.bug.attachment.delete
metersphere.bug.case.relate
metersphere.bug.case.unrelate
```

## 三、P1 / P2 任务状态

### task005：测试用例完整管理闭环

状态：未实现。

未实现：

- 用例软删除
- 用例恢复
- 批量更新
- 批量移动
- 批量复制
- 评论编辑
- 评论删除
- 回收站查询
- 自定义字段查询

### task006：缺陷完整管理闭环

状态：基本未实现。

部分实现但命名不一致：

- 当前实现：`metersphere.bug.relate_case`
- 方案要求：`metersphere.bug.case.relate`

未实现：

- `metersphere.bug.case.relate`
- `metersphere.bug.case.unrelate`
- 缺陷删除
- 缺陷恢复
- 批量更新
- 评论编辑
- 评论删除
- 关联用例查询
- 回收站查询
- 自定义字段查询

### task011：P2 高级能力

状态：未实现。

未实现：

- 文件库关联和转存
- 用例/缺陷导入导出
- 永久删除独立高风险 Scope
- 外部缺陷平台同步
- Agent 操作日志前端展示

## 四、前端任务巡检结果

### task008：缺陷管理前端体验优化

状态：部分实现。

已实现或基本实现：

- 缺陷详情标题显示缺陷 ID。
- 缺陷详情 Tab 中移除独立评论 Tab。
- 旧 `comment` tab 会回退到 `detail`。
- 非平台默认模板下，缺陷名称显示在缺陷内容上方。

未实现或不完整：

- 缺陷列表状态仍是 `<a-select>` 下拉框，不是预览图的统一按钮/标签样式。
- 平台默认模板下，缺陷名称展示遗漏。
- 状态颜色映射没有抽成集中维护。
- 回收站、工作台、关联缺陷列表等入口未统一状态样式。

### task009：测试用例列表进度口径修正

状态：代码层面基本实现，但仍需页面验证。

代码中已看到：

- 测试计划显示使用 `formatOverviewTitle(plan.name, plan.num, plan.id)`。
- 优先显示测试计划编号 `num`，缺失时回退 `id`。
- 个人执行进度调用 `getPersonalProgress(projectId)`。

风险点：

- 空态显示为 `-`，而方案允许 `0/0` 或统一空态；该点需要产品验收确认。
- 未看到截图或自动化验证结果。

### task010：Agent 集成 Token 记录管理区

状态：部分实现。

已实现：

- “我的 Agent Token”区域前置。
- 创建 Token。
- 下载 MCP 技能包按钮恢复。
- 设置按钮及弹窗。
- 可编辑 Token 名称、项目范围、权限范围。
- 删除二次确认。
- 启用/停用开关。
- Token 密钥只在创建成功弹窗展示。
- 列表不展示明文 Token。
- 删除了页面上的 Token 前缀列。

未完全清理或需确认：

- `displayPrefix` 类型字段仍存在。
- `system.agentIntegration.displayPrefix` 中英文文案仍存在。
- 后端 DTO/domain 中仍有 `displayPrefix`。
- `McpOnboardingPanel` 内部也有下载 MCP 技能包按钮，页面顶部也有一个，是否重复需要确认。

## 五、执行结果文档存在错误

文件：

```text
docs/task/agent_mcp_case_bug_extension_20260731/execution-result-20260731.md
```

其中以下描述不准确：

- “缺陷管理列表状态展示调整为按钮/标签样式”不准确，实际可编辑态仍是下拉框。
- “后端 P0 可安全落地项”容易误导，因为 P0 核心附件闭环、用例更新、缺陷附件都没完成。
- 把 task001/task003/task004 列为“未强行落地”，与用户此前要求“执行所有任务”冲突，提交前应阻断确认，而不是自行降级。

## 六、建议修复顺序

建议先修 P0，不建议继续扩展 P1/P2。

1. 补齐 `POST /api/agent/v1/attachment/upload`。
2. 补齐用例详情附件：
   - `functional.attachments.list`
   - `functional.attachment.attach`
   - `functional.attachment.delete`
3. 补齐缺陷详情附件：
   - `bug.attachments.list`
   - `bug.attachment.attach`
   - `bug.attachment.delete`
4. 补齐 `functional.case.update`。
5. 修正缺陷列表状态为预览图样式。
6. 修正 `bug.relate_case` 命名为方案要求的 `bug.case.relate`，或兼容保留旧名并新增标准名。
7. 补 requestId 落库幂等、参数冲突检测、乐观锁和错误码。
8. 补全链路验收记录和测试。

## 七、后续执行规则

后续处理该问题时必须遵守：

- 未完整实现不得标记为已完成。
- 部分完成必须明确列出已实现、未实现和验收风险。
- 不得自行降级方案内容。
- 若无法安全实现方案项，必须提交前阻断并确认。
- 提交前必须对照方案、task 文件和代码入口逐项自检。
