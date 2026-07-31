# Agent MCP 测试用例与缺陷管理扩展任务执行结果

执行日期：2026-07-31

## 已完成代码落地

### 前端 P0 体验优化

- 缺陷详情抽屉标题改为仅显示缺陷 ID。
- 缺陷名称移动到详情 Tab 内，展示在缺陷内容上方，区块标题为“缺陷名称”。
- 缺陷详情移除独立“评论”Tab，评论保留在详情内容内联区域。
- 缺陷管理列表状态展示调整为按钮/标签样式；可编辑态下的下拉框同步应用状态色。
- 测试用例列表测试计划展示顺序调整为：`测试计划名称（ID）`、进度条、百分比。
- 测试用例列表个人执行进度改为调用详情页同源的 `getPersonalProgress(projectId)` 接口，不再按当前页列表行累加。
- Agent 集成页将“我的 Agent Token”记录区域前置，说明面板移动到 Token 记录下方。
- Agent Token 表格补充创建时间、最后使用时间展示。
- Agent Token 授权范围补充测试用例维护/删除、缺陷评论附件关联/删除等细粒度 Scope 选项。

### 后端 P0 可安全落地项

- 新增细粒度 Agent Token Scope：
  - `CASE_UPDATE`
  - `CASE_DELETE`
  - `CASE_COMMENT`
  - `CASE_ATTACHMENT`
  - `BUG_DELETE`
  - `BUG_COMMENT`
  - `BUG_ATTACHMENT`
  - `BUG_RELATE`
- `FUNCTIONAL_ALL` 覆盖新增用例类 Scope。
- `BUG_WRITE` 仅保留对 `BUG_READ` 的兼容授权，不自动拥有评论、附件、关联、删除权限。
- Agent 缺陷关联用例 REST 接口权限从 `BUG_WRITE` 收窄为 `BUG_RELATE`。
- 新增 MCP Tool Handler 注册骨架，新增工具不再继续堆叠到 `AgentMcpStreamableService` 的大 switch。
- `tools/list` 自动合并 Handler 工具定义。
- `tools/call` 优先分发到 Handler。
- 写工具幂等支持 HTTP 幂等头，也支持工具参数 `requestId`。
- 新增 MCP 工具：
  - `metersphere.functional.comments.list`
  - `metersphere.functional.comment.create`
  - `metersphere.bug.comments.list`
  - `metersphere.bug.comment.create`
  - `metersphere.bug.relate_case`
- 新增 Scope 单元测试，覆盖新增授权继承和越权拦截。

## 已验证

- `pnpm.cmd --dir frontend type:check`
- `.\mvnw.cmd -pl backend/services/agent-integration -am -DskipTests compile`
- `.\mvnw.cmd -pl backend/services/agent-integration -Dtest=AgentScopeAssertTests test`
- `git diff --check`

以上校验均通过。

## 本轮未强行落地的任务

以下内容牵涉数据库表、正式附件关联模型、文件清理任务、软删除/恢复语义或跨服务完整审计链路；当前仓库已有服务不能只通过 MCP 工具“空接”完成，否则会形成不可验收的半成品接口。

- `task001`：通用 Agent 临时附件模型、`purpose` 持久化、过期清理、详情附件 attach/delete 完整闭环。
- `task003`：测试用例 update、历史查询、详情附件 attach/delete 完整闭环。
- `task004`：缺陷附件 attach/delete、缺陷模板查询、历史查询、create/update 内联附件与关联用例增强。
- `task005`：测试用例完整管理闭环，包括删除/恢复/复制/移动/批量操作等 P1 能力。
- `task006`：缺陷完整管理闭环，包括删除/恢复、取消关联、评论更新/删除等 P1 能力。
- `task007`：完整乐观锁、落库级 requestId 幂等、Agent 审计安全全链路。
- `task011`：高级能力与操作日志 P2 项。

## 后续建议

下一轮应先设计并合入数据库迁移：

- Agent 临时附件表或扩展现有 `agent_exec_attachment` 表，补充 token、project、purpose、expiresAt、linked 状态。
- Agent 工具 requestId 幂等表，避免 JVM 内存缓存重启失效。
- Agent 审计表补充 toolName、scope、requestId、targetType、targetId、result、errorCode。

数据库模型稳定后，再补附件 attach/delete、用例 update/delete、缺陷 delete/restore 等写操作工具。
