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
- functional.comments.list 返回 `{total,current,pageSize,list}`，根评论与回复独立分页（默认 10、最多 100）；传 `parentId` 获取指定根评论的回复页。根评论包含首 10 条回复及 `childTotal`。
- functional.comment.delete 要求显式 `CASE_COMMENT_DELETE_V2`、`projectId/caseId/commentId/expectedUpdateTime/requestId`。`AGENT_ALL` 不包含此权限；作者还须具有当前评论权限，管理删除还须具有当前用例删除权限。删除保留墓碑和回复，清空正文；旧版无版本删除请求会明确拒绝。
- 完整提取可用 `functional.snapshot.create({query})` 冻结用例库内容，再用 `snapshot.page` 续读，完成后 `snapshot.release`。有效期15分钟，最多10000条/50MiB，每Token同时5个；超限应缩小查询或使用文件导出。普通 search 仍实时查询；快照读取会重新授权，目标被删除后要求重建。
- 普通用例附件支持 `functional.attachment.upload.prepare`（项目、用途、文件名、类型、大小、SHA-256、requestId）与 `upload.complete`（uploadId及原文件）。Node complete 接受 filePath 或 base64；原生 MCP 接受 base64，HTTP complete 接受 multipart。单文件5MiB、准备有效期15分钟、每Token有效准备额度50MiB。完成只产生临时attachmentId，仍需 attach/comment.create 关联；个人执行证据继续走独立 evidence.upload。
### 持久化导入、删除确认与执行归档

- `functional.import.precheck` 接收项目、文件和稳定的 `requestId`，返回任务与逐项预检结果；检查后调用 `import.commit`，通过 `import.get` 分页读取实际进度。`import.stop` 保留已成功条目，`import.retry` 仅处理未成功条目。需要显式 `CASE_IMPORT_V2`，上限为 50MiB、10000 条逻辑用例，任务重试期限 24 小时。Node 支持本地 `filePath`；原生工具使用 `base64`。预检不等于导入成功。
- `functional.case.delete_preview` 返回网页确认地址，用户本人选择取消、等待、停止或删除后，调用 `case.delete` / `case.batch_delete` 继续同一 `previewId`。需要显式 `CASE_DELETE_V2`，最多100个目标版本，默认只处理 CURRENT；ALL 必须明确指定。不要代替用户确认。恢复使用 `case.restore` 和显式 `CASE_RESTORE_V2`，不会恢复已停止任务。
- `functional.archive.prepare` 接受已结束 TASK 或已接收 SUBMISSION 的来源 ID；`archive.get` 查看独立归档状态，失败后 `archive.retry` 不重新执行测试。需要 `ARTIFACT_WRITE` 和当前项目来源读取权限。归档仍明确保留 `AGENT_REPORTED / STRUCTURE_ONLY_V1`，不代表独立业务核验。
- 有真实可复用业务数据时调用 `functional.archive.data`，每条提供 `sourceObjectId`、`reuseConditions`、`cleanupStatus` 和 `values`，另需项目文件新增权限。没有实际数据时不创建空数据资产。相同内容重放不会重复生成文档或数据集；不同内容不能覆盖同一次运行的原始归档。

# 个人 Agent 直接执行用例（0.8.0）

个人直接提交采用 `STRUCTURE_ONLY_V1`：要求原用例步骤至少覆盖一次，允许额外和重复步骤并原样保留。服务端冻结定义版本，按步骤位置覆盖，不比较 actual 与 expected 文字；提交方声明 SUCCESS 或 ERROR，任一记录 ERROR 则汇总 ERROR。

门禁继续检查权限、内容/策略摘要、证据角色与归属、图片有效性及容量。单次最多1000条记录、序号1..1000；额外序号不计入原步骤覆盖。重复同一步可引用同一证据 ID，正式附件只发布一次；跨步骤证据仍拒绝。ACCEPTED/PUBLISHED 表示结构被接收并保存声明结果，不代表独立证明实际执行或图片语义。无需外部内容核验服务；既有 NEEDS_REVIEW 历史不会自动发布。

平台个人 Token 选择“个人 Agent 测试闭环”，包括 FUNCTIONAL_READ、FUNCTIONAL_SUBMIT 和 CASE_ATTACHMENT；仍受本人项目读取和更新权限约束。

1. contract 可选，用于获取步骤及支持范围；无需维护 contentHash/policyHash，stepNum 是原步骤数组从1开始的位置。
2. 执行后保存真实观察，使用 functional.execution.evidence.upload 或 import_url 上传至少一张本次用例图片，不要求逐步配图。Node 可用 filePath，原生使用 base64；PNG/JPEG 单文件最多5MiB、最低64乘64像素、最多2000万像素。暂存24小时，单Token有效暂存额度50MiB。
3. submit 顶层传 evidenceIds（1～10张），每步传 stepNum、actual、SUCCESS或ERROR；不用每步 evidenceIds。原步骤不可遗漏，允许额外和重复记录。旧步骤级证据兼容合并去重。附件与结果同事务发布。
4. execution.get 回读结果；attachments.list 查文件标识，functional.attachment.read 读取图片，历史图片可传 submissionId。响应丢失用 operation.get 按原 requestId 查询；NOT_FOUND 不证明未执行，不要换新 key 重做。

相同操作重试使用相同 requestId 和参数；同号不同内容被拒绝。已发布证据不能用于新提交。附件删除关联不改历史执行事实，数据库备份需包含证据与报告表。


### 缺陷创建、导入和导出

- 创建前读取 `metersphere.bug.template.get`，按 fieldId 填写 `customFields`，包含项目合法的 `handleUser`。`status` 在新建时留空，由已发布工作流决定。`create_bug` 必须带 `requestId`。
- `metersphere.bug.export.columns` 查询可导出列；`metersphere.bug.export` 接受 `projectId, requestId, bugIds`（1..100），默认导出 ID、标题、内容三列的 ZIP/XLSX，可传 `exportColumns: [{key, columnType}]` 选择列（以 export.columns 返回值为准），返回 `downloadPath, size, expiresAt, sha256`。下载仍需同一 Token；24 小时过期，不关联到缺陷附件。需要 BUG_READ 和当前用户的项目导出权限。
- `metersphere.bug.import.preview` 接受 `projectId`、可选 `templateId` 和互斥的 `rows` 或 `fileName + base64`。XLSX 只允许一个工作表，首行列名 `title`, `description`, `handleUser`, `custom:<fieldId>`；title 必须存在。枚举填内部值，处理人填项目成员 ID。文件不超过5MiB，最多100条，禁止公式。
- `metersphere.bug.import.commit` 传相同导入内容、预检返回的 `previewHash` 和 `requestId`。返回每行 CREATED/FAILED 和总状态 SUCCEEDED/PARTIAL/FAILED；每行独立事务，使用同一 requestId 重试不会重复创建成功行。模板变化需重新预检；需要 BUG_WRITE 和用户项目创建权限。重试幂等保留7天。
- 网页导出文件保留原有布局，不能假定可以直接原样回导；按上述稳定列头整理后再预检导入。第一版不批量导入附件、不更新同名既有缺陷、不同步第三方平台项目。第三方项目导入将在提交前拒绝，防止远端副作用与本地回滚不一致。

## 0.8.1 写入请求标识

创建目录、单条/批量创建用例必须提供调用方稳定的 `requestId`；同一业务请求重试复用原值，不自动生成新值。导出与这些创建工具采用 1–128 位 `A-Z a-z 0-9 _ . : -`。升级后重启 MCP 进程并在客户端刷新工具 schema；旧的外部连接器缓存不会随仓库提交自动刷新。

## 0.8.2 契约与版本核对

服务初始化声明的版本现在读取 package.json，修正此前包版本 0.8.1、服务仍声明 0.8.0 的不一致。构建后可运行 `node --test test/stdioContract.test.mjs`，实际启动 stdio 服务检查版本及工具列表。

客户端更新后应从实际 tools/list 核对：execution.submit 有用例级 evidenceIds，contentHash/policyHash 可选；evidence.upload 的 contentHash/stepNum/purpose 可选；创建用例和目录要求稳定 requestId。Node 包、远程服务和下载技能包有各自版本，不要求版本号相等，但参数语义必须一致。

本次代码准备 0.8.2 不代表已经发布 npm。发布前使用本地构建启动；发布后更新客户端绑定的实际包/端点，重新连接并刷新工具缓存。若刷新后仍看到旧 required 字段，应检查该连接器的工具定义来源，不能只靠重启后端认定已修复。
