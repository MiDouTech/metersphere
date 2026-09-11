# MCP 接口覆盖排查（2026-09-11）

状态：**部分完成（已完成静态覆盖排查，未完成真实平台联调；缺口尚未修复）**。

本次按 task008 的项目检索、执行、事件、证据、回写要求，扩展检查平台测试业务入口。检查当前工作区源代码，不将 2026-08-06 的旧分析直接当作现状，也不把每个管理类 REST 接口都视为必须提供 MCP。优先级是本次排查建议，不是原需求新增的验收承诺。

## 1. 工具覆盖与统计口径

原生远程 MCP 入口为 `/mcp`、`/api/mcp`；工具来自 Spring Handler Bean，经 Registry 交给 Streamable Service。stdio 入口是 `metersphere-mcp/src/index.ts` 中独立维护的固定注册列表。

| 项目 | 数量 | 含义 |
| --- | ---: | --- |
| 原生源码注册 | 84 | 66 个内置注册 + 18 个独立 Handler |
| 明确禁止个人 MCP 使用的触发器工具 | 4 | create/update/list/fire，不能计为遗漏注册 |
| 原生潜在可见工具 | 80 | 尚未扣除当前 Token Scope 过滤；不是实际账号 tools/list 数量 |
| stdio 注册 | 30 | 固定注册列表 |
| 两端同义操作交集 | 27 | 显式归一化 snake_case 和 metersphere.* 名称，未证明参数契约完全等价 |
| 原生有、stdio 无 | 53 | 完整名称见机器清单 |
| stdio 有、原生无直接等价工具 | 3 | get_exec_log、submit_functional_results_batch、upload_execution_attachment |

证据：[原生注册中心](../../../backend/services/agent-integration/src/main/java/io/metersphere/agent/tool/AgentMcpToolRegistry.java)、[内置工具](../../../backend/services/agent-integration/src/main/java/io/metersphere/agent/tool/BuiltinAgentMcpToolConfig.java)、[远程工具过滤与调用](../../../backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentMcpStreamableService.java)、[stdio 注册](../../../metersphere-mcp/src/index.ts)。每个工具的文件和行号见 [机器清单](mcp-coverage-inventory-20260911.json)。

## 2. 应补齐的业务链路（需求追踪表）

以下“缺失”指当前两套注册清单均没有对应业务操作，另有标注的除外。建议工具名尚未实现，不可直接调用。

| ID / 优先级 | 需求与前端入口 | 已有后端 / 服务 | MCP 缺口与影响 | 建议与验收 |
| --- | --- | --- | --- | --- |
| MCP-G01 / P1 | 用例评审列表、评审详情、提交结果 | `/case/review/page`；`/case/review/detail/page`；`/review/functional/case/save`；`/review/functional/case/get/list/{reviewId}/{caseId}` | 原生仅 create/associate_cases/get；两端均缺评审检索、关联用例分页、评审结果提交及历史。Agent 能建评审，不能完成评审闭环 | 新增 `case_review.search/cases/result.submit/history.list`；验证评审人身份、评审状态、跨项目拒绝和幂等重复提交 |
| MCP-G02 / P1 | 测试计划报告列表、报告详情与用例步骤 | `/test-plan/report/page`、`/get/{reportId}`、`/detail/functional/case/page`、`/detail/functional/case/step/{reportId}` | 两端均无正式报告查询工具。execution.result.get 仅返回任务状态/verdict 等字段，不能替代正式报告、步骤详情和聚合统计 | 新增 `test_plan.report.search/get/cases/steps`；验证报告归属、分页、计划内/外回写与报告对账 |
| MCP-G03 / P1 | 缺陷详情中的状态流转 | Agent REST 已有 `/api/agent/v1/bug/{projectId}/{bugId}/transitions` 与 `/transition`，复用 BugWorkflowRuntimeService | 两端均未注册流转列表和按 transitionId 执行流转。普通 bug.update 不等价于发现允许的流转及执行完整工作流校验 | 新增 `bug.transitions/transition`；复用现有状态机、权限、必填字段及审计；验证非法状态、过期状态与重放 |
| MCP-G04 / P1 | 自动化执行列表、历史任务检索 | `/api/ai/execution/task/search` → AgentExecutionService.searchTasks | 原生 `task.search` 实际调用 selectQueuedTasksForAgent，限定 QUEUED、PERSONAL_MCP、EXTERNAL_MCP_AGENT，最多 100 条且无完整分页。已完成/失败历史无法按条件找回；stdio 无任务搜索 | 新增个人任务历史检索 `execution.search`，支持状态、时间、分页；保留平台托管任务隔离，验证跨项目及跨渠道不可见 |
| MCP-G05 / P1 | task008 批量结果回写 | `/api/agent/v1/functional/submit/batch` → submitBatch | stdio 有 submit_functional_results_batch；原生只有单条 functional.submit，缺批量直接入口。逐条调用不是 failFast、批量返回及部分失败契约的等价实现 | 原生新增 `functional.submit.batch`；验证逐条幂等、部分失败、计划归属、证据关联 |
| MCP-G06 / P1 | task008 执行审计可追踪 | `/api/agent/v1/functional/exec-log/page`、`/exec-log/{id}` | 原生两者均缺；stdio 只有按 ID 的 get_exec_log，没有日志检索。execution.events、functional.history 和 bug.history 不是同一日志模型 | 新增 `exec_log.search/get`；验证 Token/项目隔离、筛选分页、敏感信息脱敏 |
| MCP-G07 / P1 | 断点恢复 | `/api/ai/execution/tasks/{taskId}/checkpoints/{id}/resume` → AgentExecutionCheckpointService.resume | 原生有 execution.checkpoint.create，无 checkpoint.resume；execution.resume 调用 loginReady，不能替代携带 resumeToken、新 preflightId 的断点恢复 | 新增 `execution.checkpoint.resume`；验证快照哈希、一次性恢复令牌、前置状态、预检及 actor 传递。不能只加注册后绕过现有检查 |
| MCP-G08 / P2 | 接口测试管理：接口用例/场景检索与执行 | `/api/case/page`、`/get-detail/{id}`、`/run`；`/api/scenario/page`、`/run` | 两端无接口用例和场景的业务工具；资产目录已有 API_DEFINITION 发布快照查询，不能误称“所有接口信息不可读”，但不覆盖接口用例/场景运行 | 建议扩展 `api_case.search/get/run`、`api_scenario.search/get/run` 及报告读取；需补 Agent Scope/适配服务并验证环境与执行权限。属于平台覆盖扩展，超出 task008 核心要求 |
| MCP-G09 / P2 | 测试计划编辑及关联用例维护 | `/test-plan/update`，现有计划详情/关联管理 Controller | 原生仅 create/get/search/cases/associate_cases，缺计划更新、解除关联等维护操作；stdio 还缺 get 的工具注册，虽 client.ts 已有 getTestPlan 方法 | 优先补计划更新与解除关联；验证计划状态和执行中禁止修改规则；删除/批量管理另行限定授权 |

本次不修改数据库、前端或后端。上述多数缺口可复用现有服务，但需补 MCP Scope、参数 schema、对象权限、错误协议、幂等和测试；不能据此推断仅注册工具就能完成交付。

前后端与服务证据：

- G01：[前端评审 API](../../../frontend/src/api/requrls/case-management/caseReview.ts)；[CaseReviewController](../../../backend/services/case-management/src/main/java/io/metersphere/functional/controller/CaseReviewController.java)；[CaseReviewFunctionalCaseController](../../../backend/services/case-management/src/main/java/io/metersphere/functional/controller/CaseReviewFunctionalCaseController.java)；[ReviewFunctionalCaseController](../../../backend/services/case-management/src/main/java/io/metersphere/functional/controller/ReviewFunctionalCaseController.java)。
- G02：[前端报告 API](../../../frontend/src/api/requrls/test-plan/report.ts)；[TestPlanReportController](../../../backend/services/test-plan/src/main/java/io/metersphere/plan/controller/TestPlanReportController.java)，page 位于 55 行、get 位于 121 行、用例/步骤位于 184/204 行；内置工具 result.get 位于 538 行附近。
- G03：[AgentBugController](../../../backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentBugController.java)，60/67 行；[AgentBugWriteService](../../../backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentBugWriteService.java)，131/136 行。
- G04：[前端执行 API](../../../frontend/src/api/requrls/ai-execution.ts)；[AgentExecutionController](../../../backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentExecutionController.java)；[AgentTaskClaimService](../../../backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentTaskClaimService.java)，145 行起；[Mapper](../../../backend/services/agent-integration/src/main/java/io/metersphere/agent/mapper/AgentExecutionMapper.xml)，428 行起。
- G05/G06：[AgentFunctionalCaseController](../../../backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentFunctionalCaseController.java)，89/96/103 行；[stdio 批量提交](../../../metersphere-mcp/src/tools/submitFunctionalResultsBatch.ts)；[stdio 执行日志](../../../metersphere-mcp/src/tools/bugWrite.ts)。
- G07：[Checkpoint Controller](../../../backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentExecutionCheckpointController.java)；[Checkpoint Service](../../../backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentExecutionCheckpointService.java)；该 Controller 入口已存在，本次未验证前端有可操作的对应入口。
- G08：[前端接口 API](../../../frontend/src/api/requrls/api-test/management.ts)、[场景 API](../../../frontend/src/api/requrls/api-test/scenario.ts)；[ApiTestCaseController](../../../backend/services/api-test/src/main/java/io/metersphere/api/controller/definition/ApiTestCaseController.java)；[ApiScenarioController](../../../backend/services/api-test/src/main/java/io/metersphere/api/controller/scenario/ApiScenarioController.java)。
- G09：[计划前端 API](../../../frontend/src/api/requrls/test-plan/testPlan.ts)；[TestPlanController](../../../backend/services/test-plan/src/main/java/io/metersphere/plan/controller/TestPlanController.java)。

## 3. 部分覆盖、客户端差异及设计限制

1. **stdio 明显落后。** 缺少预检、任务领取/租约、步骤提交、完成/失败、人工请求、资产目录、环境/凭据元数据、产物准备/提交/列表、断点、暂停/重试、用例更新、模板/评论/历史等。client.ts 已实现 getProject/getTestPlan/getCaseReview，但未接入 stdio tools 数组。53 项名称见 JSON；4 个禁止的触发器不在其中。即使暂不维护 stdio，也应明确支持范围或迁移到原生端，避免用户误认为两端等价。
2. **附件“挂载”不等于“上传/下载”。** 原生 functional/bug.attachment.attach 关联已有文件；stdio upload_execution_attachment 把本地文件上传到旧功能结果附件接口。原生 artifact.prepare/commit 是任务租约证据链，不能直接视作旧附件上传的等价替代。需选择并说明统一上传路径。
3. **产物内容仅部分覆盖。** 原生 artifact.list 返回 downloadPath，HTTP 下载服务存在，源码会校验任务归属、AVAILABLE 和 redacted。MCP 当前没有资源读取或专用内容读取工具（仅 tools/ping/initialize 等方法）；纯 MCP 客户端不能直接取截图/HAR/附件字节，需要额外 HTTP 能力。建议提供有权限的受控下载契约或 MCP 内容工具；不是新增一个不鉴权的 URL。
4. **任务诊断只有部分数据。** HTTP `/task/{id}/observability` 有专用诊断服务，MCP get/events/result/writeback 只覆盖其部分信息；可考虑 execution.observability.get 支持失败排查，不能把此项写成完全没有诊断数据。
5. **确认入口需明确设计。** HTTP `/task/{id}/confirm` 存在，MCP 没有 task confirm；已有 human_request.respond，不能默认其与 task confirm 相同。新增前应明确是否必须由 Web UI 人工确认，不能把人工治理自动化绕过。列为设计待确认，不列为确定缺陷。
6. **不要误报刻意隔离。** StreamableService 103/114 行附近显式隐藏并拒绝 execution.trigger.*；getPersonalTask 只允许 PERSONAL_MCP + EXTERNAL_MCP_AGENT。平台托管任务、Runner 内部协议、管理员配置和凭据明文不应为了“覆盖率”暴露给个人 MCP。
7. **实际可见数量受 Scope 影响。** 注册存在但 tools/list 不显示，首先排查 Token Scope、当前部署版本、使用原生还是 stdio。本次 80 是源码潜在上限，不是登录账号的可见数量。

## 4. task008 旧结论需要更新的部分

- 项目 search/list/get 已在原生注册，搜索不是当前“未实现 MCP”项；stdio 有 search_projects，但缺 list/get 工具注册。
- 现已有 Runner 内部租约/心跳入口和 ai-browser-runner 代码；原生已有 PERSONAL_MCP 任务执行闭环工具，不能沿用“只有会话表”的旧说法。
- 原生已有凭据元数据查询、预检、人工请求、artifact.prepare/commit/list、结果和回写状态读取。它们的存在不等于凭据注入、Browser/Desktop 真实执行及证据保留策略已验收通过。
- 已存在 AgentExecution*、MCP 契约等测试文件；旧文档“未发现专项测试”不能作为今天的判断。项目检索测试仍应按中文/大小写/权限/特殊字符/归档/分页逐项验收，本次没有完成该全量验收。
- 本次搜索执行模块和工作台未发现 SSE/WebSocket 主事件订阅实现；MCP GET 明确返回 405、使用 POST。MCP 传输选择与 task008 业务事件实时推送是两个问题，不能因没有 GET SSE 就认定 MCP 协议缺实现。

## 5. 验证与复现

已执行：

- `python scripts/audit-mcp-coverage.py`：通过，生成注册/同义映射/显式禁用/REST Mapping 清单。扫描全部 backend 主代码中的 Handler 声明，并断言 stdio 注册均可解析、工具名不重复。
- `node metersphere-mcp/node_modules/typescript/bin/tsc -p metersphere-mcp/tsconfig.json --noEmit`：通过。
- `.\mvnw.cmd -version`：Maven 3.8.3、Java 21.0.11。
- MCP 定向单测：4 个类合计 **9 项通过，0 失败、0 错误、0 跳过**。Registry 1 项、Contract 2 项、StreamableService 4 项、Trigger 2 项；Maven 日志显示 `BUILD SUCCESS`，耗时 2 分 15 秒。PowerShell 将 Mockito 的 stderr 警告记录为 NativeCommandError，外层工具退出码为 1；因此没有把外层命令报告为 exit 0，而是交叉核对本次日志和 `backend/services/agent-integration/target/surefire-reports/TEST-*.xml` 确认测试结果。命令为：

```powershell
.\mvnw.cmd -pl backend/services/agent-integration -am '-Dtest=AgentMcpToolRegistryTests,AgentMcpToolContractTests,AgentMcpStreamableServiceTests,BuiltinAgentMcpTriggerToolTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

未执行的验证与风险：

| 检查 | 原因与剩余风险 | 后续命令/动作 |
| --- | --- | --- |
| 在线 MCP tools/list、真实调用、RBAC/Scope/白名单 | 本次为工作区静态审计，未连接目标平台或使用测试账号；无法确认部署版本和真实账号可见性 | 在隔离测试环境向 `/api/mcp` 发 initialize、tools/list，使用全授权和受限 Token 分别对照 JSON；只做注册检查不能代替工具调用验收 |
| 全量单测/后端集成测试 | 本次仅尝试 MCP 定向测试；无全量运行结论 | `.\mvnw.cmd -pl backend/services/agent-integration -am test`，先按项目测试配置准备数据库和中间件 |
| 前端类型、lint、生产构建 | 未改前端；页面运行质量不在本次静态证据中 | `npm --prefix frontend run type:check`；? frontend ???? `npx eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts`；`npm --prefix frontend run build`（lint 从 frontend 目录执行更稳妥） |
| 后端打包 | 未改生产代码；不能断言可部署 | `.\mvnw.cmd -pl backend/app -am package -DskipTests` |
| 数据库迁移、Docker 构建/启动/健康 | 未改迁移/容器配置，未启动应用；新库及部署兼容性未验证 | 按项目配置准备打包产物后执行 `docker build -f Dockerfile.backend -t metersphere:mcp-audit .`；迁移和健康检查需要使用实际部署配置，不能将仅含基础服务的 dev compose 冒充完整应用验收 |
| Browser/Desktop E2E | 本次未执行真实页面/桌面操作及回写；不能声称 task008 通过 | 在受控环境各运行一条真实用例，用 runId 核对事件、步骤、产物和正式报告；仓库现有 `scripts/verify-agent-conversation-loop.ps1 -BaseUrl $env:MS_BASE_URL -Token $env:MS_AGENT_TOKEN -ProjectId $env:MS_PROJECT_ID -SkipProjectCreate` 仅作相关链路补充，不能替代 Runner 全链路验收 |

## 6. 修改文件与后续顺序

本次只新增此报告、`mcp-coverage-inventory-20260911.json`、`scripts/audit-mcp-coverage.py`；不改生产代码及原 task008 状态，不覆盖工作区已有其他修改。Maven 原始日志保存在同目录 `mcp-audit-tests-20260911.log`（受仓库日志忽略规则影响）。

后续实现：用户指定的 G03/G04/G05/G07/G09 已补入代码及定向测试，证据与未完成验收见 [五项缺口实现记录](mcp-gaps-implementation-20260911.md)。本文保留修复前审计基线；不代表 G01/G02/G06/G08 已补齐，也不代表部署验收完成。

已知限制：源码正则清点不等于 Spring 运行时加载；未读取已部署工具清单或验证内嵌 zip 与源码是否一致。当前结论是**MCP 业务覆盖部分完成，存在明确缺口**，不是修复完成报告。
