# @midoo/metersphere-mcp

MeterSphere Agent API 的 MCP 薄封装，供 Cursor、Claude Desktop 等 MCP 客户端调用。

**不含业务逻辑**，仅将 MCP Tool 请求转发到 MeterSphere REST API（`/api/agent/v1`）。

## 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `MS_BASE_URL` | 是 | MeterSphere 地址，如 `https://msp.ebcone.cn` |
| `MS_AGENT_TOKEN` | 是 | Agent Token，格式 `msat_xxx` |
| `MS_PROJECT_ID` | 是 | 默认项目 ID（创建新项目后建议在调用中显式传 projectId） |
| `MS_TEST_PLAN_ID` | 否 | 执行类操作的默认计划；用例库 search/get 不自动带入，查询计划上下文须显式传 testPlanId |

## MCP Tools

### 维护与执行历史（需要部署含这些工具的后端）

下列工具通过 `/api/mcp` 转发到原生工具，复用业务权限和幂等校验。
写工具必须传非空 `requestId`；同一次请求重试使用相同 ID 和完全相同的参数。

| Tool | 主要参数 | Scope |
| --- | --- | --- |
| `metersphere.functional.submit.batch` | projectId、results、requestId；可选 failFast、testPlanId、executionTaskId | FUNCTIONAL_SUBMIT |
| `metersphere.execution.search` | projectId；可选 keyword、status、createdAfter、createdBefore、current、pageSize | AI_EXECUTION_READ |
| `metersphere.execution.preflight` | projectId、environmentProfileId、runnerType、requestId；取得恢复所需 preflightId | TASK_CLAIM |
| `metersphere.execution.checkpoint.resume` | taskId、checkpointId、resumeToken、preflightId、requestId | AI_EXECUTION_RUN |
| `metersphere.bug.transitions` | projectId、bugId | BUG_READ |
| `metersphere.bug.transition` | projectId、bugId、transition、requestId | BUG_WRITE |
| `metersphere.test_plan.get` | testPlanId；响应包含 updateTime | PLAN_READ |
| `metersphere.test_plan.update` | projectId、testPlanId、expectedUpdateTime、patch、requestId | PLAN_WRITE |
| `metersphere.test_plan.disassociate_cases` | projectId、testPlanId、associationIds、requestId | PLAN_WRITE |

- 批量回写最多 100 条。身份、项目/计划/用例归属在写入前校验；业务失败逐条返回
  `errors[{caseId,code,message,traceId}]`。响应包含 total/success/failed/skipped，
  failFast=true 时未尝试的后续项计入 skipped。成功项独立提交并持久化幂等记录。
  修正失败项后应以新 requestId 只提交需重试的项；幂等保留期沿用平台配置。
- 历史查询只返回 PERSONAL_MCP / EXTERNAL_MCP_AGENT，包含终态，按创建时间、ID 倒序。
  时间条件为包含边界的 Unix 毫秒；页码从 1 开始，每页 1–100 条。平台托管任务不在此接口中。
- 断点恢复必须使用原断点恢复令牌及新的 PASSED 预检记录；不是 login-ready，不能跳过快照和状态校验。
- 缺陷 transition 对象必须有 transitionId、targetStatusId、expectedUpdateTime；可选 comment、override、overrideReason。
  流转仍受平台工作流角色和并发版本约束。
- 计划 patch 支持 name、description、tags、plannedStartTime、plannedEndTime、automaticStatusUpdate、repeatCase、passThreshold。
  未传字段保留；不接受 null，清空描述用空字符串、清空标签用空数组。
  已归档或存在执行中的计划不能维护。associationIds 是 `testPlanCaseId`，不是用例库 caseId。
  删除计划、移动分组/模块和修改计划状态不属于本组工具的编辑范围。

### 读 / 执行回写

用例查询结果包含只读字段 `lastExecuteUser`（最后执行人用户 ID）和
`lastExecuteUserName`（姓名）。适用于 `search_functional_cases`、
`get_functional_case`、`metersphere.test_plan.cases`，以及原生远程 MCP 的
`metersphere.functional.search/get`。不指定测试计划时读取用例库的最后执行人；
指定计划并匹配到关联用例时读取计划记录。`includeSteps: false` 仍返回这些字段。
未执行时值可为空；用户姓名不可解析时保留 ID，姓名可为空。
这些字段不是分配执行人 `executeUser`，也不是回写备注中的 `executedBy`。

| Tool | 说明 |
|------|------|
| `search_functional_cases` | 检索功能用例 |
| `get_functional_case` | 单条用例详情 |
| `submit_functional_result` | 回写计划内执行结果 |
| `submit_functional_results_batch` | 批量回写 |
| `metersphere.functional.execution.evidence.upload` | 暂存与用例及步骤绑定的截图，门禁通过后发布 |
| `list_modules` | 模块列表 |
| `get_exec_log` | 执行审计日志详情 |
| `metersphere.test_plan.search` | 检索测试计划 |
| `metersphere.test_plan.cases` | 获取测试计划功能用例 |
| `metersphere.execution.resolve` | 解析 AI 执行范围 |
| `metersphere.execution.create` | 创建 AI 执行任务 |
| `metersphere.execution.get` | 查询 AI 执行任务 |
| `metersphere.execution.events` | 读取 AI 执行事件 |
| `metersphere.execution.cancel` | 取消 AI 执行任务 |
| `metersphere.execution.resume` | 登录恢复后继续 AI 执行任务 |

### 写闭环（一期）

| Tool | Scope | 说明 |
|------|-------|------|
| `create_project` | PROJECT_WRITE | 创建项目并加初始成员 |
| `add_project_members` | PROJECT_WRITE | 追加成员 |
| `create_functional_module` | CASE_WRITE | 创建模块 |
| `create_functional_case` | CASE_WRITE | 单条创建 |
| `batch_create_functional_cases` | CASE_WRITE | 批量导入 Agent 生成用例 |
| `create_test_plan` | PLAN_WRITE | 创建测试计划（可带 caseIds） |
| `associate_test_plan_cases` | PLAN_WRITE | 关联用例到计划 |
| `create_case_review` | REVIEW_WRITE | 创建评审 |
| `associate_case_review_cases` | REVIEW_WRITE | 关联用例到评审 |
| `search_bugs` | BUG_READ | 检索缺陷（关键词/状态/处理人） |
| `get_bug` | BUG_READ | 缺陷详情（含 customFields） |
| `create_bug` | BUG_WRITE | 创建缺陷（可关联用例） |
| `update_bug` | BUG_WRITE | 更新标题/描述/标签/自定义字段 |
| `relate_bug_case` | BUG_WRITE | 缺陷补关联用例 |

> `BUG_WRITE` 隐含 `BUG_READ`。缺陷读写详细说明见 [MCP 缺陷接口使用文档](../docs/task/metersphere_agent/mcp-bug-api-usage.md)。

## 本地开发

```bash
cd metersphere-mcp
npm install
npm run build
npm start
```

## Cursor 配置

**推荐**：平台「系统设置 → Agent 集成」下载 MCP 包并复制 `mcp.json`。

也可手写：

```json
{
  "mcpServers": {
    "metersphere": {
      "command": "node",
      "args": ["metersphere-mcp/dist/index.js"],
      "env": {
        "MS_BASE_URL": "https://msp.ebcone.cn",
        "MS_AGENT_TOKEN": "msat_xxxx",
        "MS_PROJECT_ID": "your-project-id",
        "MS_TEST_PLAN_ID": "optional-plan-id"
      }
    }
  }
}
```

重新打包进平台（管理员）：

```bash
cd metersphere-mcp && npm run build
# 仓库根目录
powershell -ExecutionPolicy Bypass -File scripts/pack-metersphere-mcp.ps1
```

## 对话闭环示例

1. `create_project` → 得到 projectId  
2. 对话生成用例 → `batch_create_functional_cases`  
3. `create_test_plan` + caseIds；`create_case_review` + caseIds  
4. `search_functional_cases` → `metersphere.functional.execution.contract` → 外部执行 → `metersphere.functional.execution.evidence.upload` → `metersphere.functional.execution.submit`
5. 失败则 `create_bug`（带 caseId）；后续可用 `search_bugs` / `get_bug` / `update_bug` 跟踪与改状态

## 常见问题

| 现象 | 处理 |
|------|------|
| 401 Unauthorized | 检查 `MS_AGENT_TOKEN` |
| 403 Scope 不足 | Token 需 AGENT_ALL 或对应 WRITE/SUBMIT scope |
| 429 Too Many Requests | Token 限流：全局 120/分钟；检索 30/分钟且间隔≥300ms；pageSize≤100 |
| 缺少 testPlanCaseId | 先关联测试计划再 search |
| 缺陷必填字段 | 传 `customFields` |

## 关联文档

- [缺陷 MCP 使用文档（AI 自配置）](../docs/task/metersphere_agent/mcp-bug-api-usage.md)
- [Cursor 接入指南](../docs/task/metersphere_agent/cursor-onboarding.md)
- [扩展方案](../docs/summary/MeterSphere-Agent对话闭环-扩展方案-2026-07-23.md)


### 全局质量门禁升级兼容性

启用平台统一门禁后，旧 `submit_functional_result` / `submit_functional_results_batch` 直写接口不再接受执行结果，返回 `QUALITY_LEGACY_SUBMIT_FORBIDDEN`。不能用旧工具提交“通过”来绕过门禁。执行器须转入平台任务认领、尝试绑定、证据上传、步骤结果及最终回写流程；仅上报事件或附件不构成验收通过。系统管理员通过 `/setting/system/quality-policy` 发布全平台统一规则；该管理能力不向 Agent Token 开放。


## 0.4.0 兼容变化

- 评审创建、关联必须提供稳定的 `requestId`；超时重试使用原键和原参数。
- 新增 `get_case_review` 及受控任务领取、租约续期、步骤事实、证据与终态工具。
- 不再注册 `submit_functional_result`、`submit_functional_results_batch`、`metersphere.functional.submit.batch`。旧直写由服务端拒绝；执行结果由质量门禁评估后回写。
- 领取响应丢失时先查询任务状态，不盲目重复领取。租约和上传凭证不得写入业务笔记或共享日志。
- 技能包契约版本为 1.1.0；该版本不承诺尚未开放的评审结论提交和 Excel 导入能力。实际能力以服务器 `tools/list` 为准。


## 0.5.1 用例库能力

- search_functional_cases 支持 projectId、identityType、标签 ANY/ALL、子目录范围、评审状态及版本筛选；默认读取用例库，分页响应保留 cases，并返回 current/pageSize/total/hasMore。
- 补充原生名称适配：functional.template.get、history.list、comments.list、attachments.list、case.update、comment.create、versions.list、overview.get、module.get/update/move（均带 metersphere. 前缀）。
- case.update 先读取 updateTime；customFields 使用字段 ID 到值的对象映射，不能提交执行结果字段。
- module.update/move 需要明确授予 CASE_MODULE_WRITE_V2，旧 AGENT_ALL/FUNCTIONAL_ALL 不包含此权限。module.get 返回 expectedUpdateTime 的来源；parentId=NONE 表示项目根目录。
- 技能包版本 1.2.1；实时分页不是完整快照导出。运行中删除确认、异步导入导出、普通附件上传下载、评论删除等仍需后续实现，以实时 tools/list 为准，不应猜测工具名调用。

- functional.comment.update 编辑本人评论正文，要求 CASE_COMMENT_EDIT_V2、expectedUpdateTime 和 requestId；不重新通知，不修改回复关系和附件。网页编辑使用相同版本校验。
# 个人 Agent 直接执行用例（0.6.0）

平台个人 Token 选择“个人 Agent 测试闭环”，包括 `FUNCTIONAL_READ;FUNCTIONAL_SUBMIT;CASE_ATTACHMENT`。用户本人仍需该项目的用例读取和更新权限。原生远程 MCP 和 Node 客户端均支持以下流程，无需任务、租约或 Runner：

1. `metersphere.functional.execution.contract({projectId, caseId})`：取得完整步骤、`contentHash`、`policyHash` 和全局门禁要求。`stepNum` 使用返回步骤数组从 1 开始的位置。
2. 执行测试，逐步骤保存观察事实和门禁要求的执行前/后截图。
3. `metersphere.functional.execution.evidence.upload`：传 `projectId, caseId, contentHash, stepNum, purpose, fileName, contentType, requestId`，以及 `base64`。Node 客户端可用本机 `filePath` 替代 base64。支持可解码的 PNG/JPEG，单文件不超过 5 MiB，并受当前全局策略更小的大小/MIME 限制。暂存附件 24 小时内提交，单 Token 暂存总量 50 MiB。
4. `metersphere.functional.execution.submit`：传 `projectId, caseId, contentHash, policyHash, requestId, steps`。每个步骤为 `{stepNum, actual, result, evidenceIds}`，不可漏步骤或跨步骤复用证据。审核通过后，附件与用例执行结果在一个事务内发布；拒绝时正式结果不变。
5. `metersphere.functional.execution.get({projectId, caseId, submissionId})` 查询步骤、附件对应关系及当次策略。用例详情也可查看、下载已发布附件。

当前 Q-ASSERT-01 使用服务端用例文字预期进行精确 EQUALS 比较：观察值相等时提交 `SUCCESS`，不等时提交 `ERROR`；不得直接复制未实际观察到的预期。它不是截图语义识别。全局策略若不允许 EQUALS，此通道拒绝提交，不会绕过策略。用例内容或策略变化后重新读取基准并核查执行事实。

相同操作重试沿用相同 requestId 和参数。已被采用的附件不能提交第二次；需重新执行时上传新的证据。已批准证据存于数据库并保留审计，删除用例附件关联不删除历史事实。数据库备份需包含新证据表。
