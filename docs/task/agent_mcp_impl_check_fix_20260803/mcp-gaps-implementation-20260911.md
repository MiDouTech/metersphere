# 五项 MCP 缺口实现与验收记录

日期：2026-09-11。范围：MCP-G03/G04/G05/G07/G09。状态：**部分完成：代码、定向测试和应用打包通过，真实环境验收未完成**。保留此前最终执行人读取及用户工作区的其他修改；未部署。

## 需求追踪与前后端对应

开发前依据 [缺口审计需求追踪表](mcp-coverage-audit-20260911.md) 检查已有实现。新入口均为 `/api/mcp` 的 tools/call，stdio 转发到同一入口，复用真实业务服务，不新增页面或数据库迁移。

| 需求 | 现有用户入口 / 前端实现 | 新 MCP 与后端链路 | 权限、数据及边界 | 测试 |
| --- | --- | --- | --- | --- |
| G05 批量回写 | task008 客户端、测试用例结果展示 | functional.submit.batch → MaintenanceService → BatchSubmitService → 单项事务 → FunctionalCaseSubmitService；沿用 functional/submit/batch 业务 | FUNCTIONAL_SUBMIT + 用例更新 RBAC；全批预校验项目/计划关联/任务快照/附件拥有者；1–100 项，failFast、逐项幂等、安全错误 | MaintenanceServiceTests、BatchTests、MaintenanceToolTests、stdio schema |
| G07 断点恢复 | Agent 执行任务及检查点 | execution.checkpoint.resume → CheckpointService.resume；stdio 补入既有 execution.preflight 前置入口 | AI_EXECUTION_RUN；个人 MCP 任务、Token 用户上下文；沿用一次性 token、hash、预检、任务状态检查 | CheckpointResumeTests、MaintenanceServiceTests |
| G09 计划编辑及关联维护 | 计划详情；frontend/src/api/requrls/test-plan/testPlan.ts | test_plan.update / disassociate_cases → TestPlanService.update / TestPlanFunctionalCaseService.disassociate；对应 /test-plan/update、/test-plan/functional/case/batch/disassociate | PLAN_WRITE + 更新/关联 RBAC、项目模块开关、归档/执行中禁止；编辑版本校验；解关联用 testPlanCaseId；详情增加 updateTime | MaintenanceServiceTests、MaintenanceToolTests |
| G04 执行历史检索 | Agent 执行任务列表 / MCP 客户端 | execution.search → AgentExecutionMapper count/page SQL | AI_EXECUTION_READ；固定授权项目与 PERSONAL_MCP / EXTERNAL_MCP_AGENT 通道；关键词、状态、闭区间毫秒时间、真实总数、稳定分页 | PersonalHistorySqlTests、MaintenanceServiceTests |
| G03 缺陷状态流转 | 缺陷详情；frontend/src/api/modules/bug-management/index.ts | bug.transitions / bug.transition → AgentBugWriteService → 既有工作流；对应 /bug/{bugId}/transitions、/bug/{bugId}/transition | BUG_READ / BUG_WRITE + 缺陷 RBAC；沿用合法流转、目标状态、expectedUpdateTime 和覆盖原因校验 | MaintenanceServiceTests、工具 Scope 测试 |

表中工具均以 `metersphere.` 开头。用户 RBAC、Token Scope、项目白名单共同约束调用；返回幂等缓存前重新检查 Scope。运行错误返回安全业务码和 traceId，stdio 不透传 HTTP 失败原始响应体。

## 调用与边界

- 批量传 projectId、results、稳定 requestId；计划和任务上下文可顶层继承。归属/授权/参数错误在写入前拒绝整个请求，执行阶段业务失败按项返回 success/failed/skipped。每项独立事务，锁定已有 Token 行后检查幂等记录。同 requestId 重试返回原结果；修正失败项后用新 requestId 仅重试失败项。幂等有效期沿用配置。
- 恢复先 execution.preflight（TASK_CLAIM），再用有效 preflightId、taskId、checkpointId、resumeToken 调用 checkpoint.resume。失败预检、过期/重复 token、hash 不符、不可恢复任务均拒绝。后续真实执行仍依赖 Runner。
- 编辑先 test_plan.get 获取 updateTime，再传 expectedUpdateTime 与 patch。支持 name、description、tags、plannedStartTime、plannedEndTime、automaticStatusUpdate、repeatCase、passThreshold；未传字段保留，不接受 null；空描述用空字符串、空标签用空数组。未扩展计划移动、删除、任意状态修改或 API 用例关联。
- 新增关联继续用已有 associate_cases；取消关联使用 disassociate_cases 的 associationIds，值为计划内 testPlanCaseId，不是用例库 caseId。
- 缺陷先 bug.transitions 获取可用流转和版本，再通过 transition 对象调用 bug.transition，不绕过工作流直接改状态。

完整参数见 [MCP README](../../../metersphere-mcp/README.md)。原生新增 7 个工具，stdio 增加这 7 个及已有 plan.get、execution.preflight，共 9 个注册。静态盘点原生 91、stdio 39；仍有 51 个原生工具未在 stdio 同名/语义映射中覆盖，本次不宣称全平台缺口清零。

## 修改文件

以下路径相对仓库根目录。Java 基础路径为 `backend/services/agent-integration/src/main/java/io/metersphere/agent/`。

| 目录 | 新增 / 修改 |
| --- | --- |
| dto/ | AgentExecutionHistoryRequest.java、AgentBatchSubmitResponse.java、AgentTestPlanDTO.java |
| mapper/ | AgentExecutionMapper.java、AgentExecutionMapper.xml |
| service/ | AgentMcpMaintenanceService.java、AgentMcpBatchItemService.java、AgentBatchSubmitService.java、AgentMcpStreamableService.java、AgentSafeErrorMapper.java、AgentTestPlanWriteService.java |
| tool/ | BuiltinAgentMcpToolConfig.java |
| 同模块 src/test/java/io/metersphere/agent/ | service/AgentMcpMaintenanceServiceTests.java、AgentMcpBatchTests.java、AgentCheckpointResumeTests.java；tool/AgentMcpMaintenanceToolTests.java；mapper/AgentPersonalHistorySqlTests.java |
| metersphere-mcp/ | src/client.ts、src/index.ts、src/tools/maintenance.ts、test/maintenance.test.mjs、package.json、README.md |
| scripts/ | audit-mcp-coverage.py：输出路径参数和批量工具语义映射 |
| 本文目录 | 本报告、mcp-gaps-inventory-20260911.json、mcp-gaps-test-results-20260911.json、审计后续链接 |

## 实际验证命令及结果

从仓库根目录执行：

```powershell
.\mvnw.cmd -pl backend/services/agent-integration -am -DskipTests compile
$mcpTests = 'AgentMcpMaintenanceServiceTests,AgentMcpBatchTests,AgentCheckpointResumeTests,AgentMcpMaintenanceToolTests,AgentPersonalHistorySqlTests,AgentMcpToolContractTests,AgentMcpToolRegistryTests,AgentMcpStreamableServiceTests,AgentCaseSchemaMapperTests,AgentFunctionalCaseSearchServiceTests,AgentFunctionalCaseSubmitServiceTests'
.\mvnw.cmd -pl backend/services/agent-integration -am "-Dtest=$mcpTests" '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -pl backend/app -am "-Dtest=$mcpTests" '-Dsurefire.failIfNoSpecifiedTests=false' package
npm --prefix metersphere-mcp test
python scripts/audit-mcp-coverage.py --output docs/task/agent_mcp_impl_check_fix_20260803/mcp-gaps-inventory-20260911.json
git diff --check
docker version --format '{{.Server.Version}}'
npm --prefix frontend run build
# 以下从 frontend 目录执行，不使用 --fix：
node node_modules/eslint/bin/eslint.js . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts
```

- Maven 编译、定向测试、应用打包均为 BUILD SUCCESS。新增 24 项加既有回归共 **52 项，失败/错误/跳过均为 0**。产物 backend/app/target/app-3.x.jar、target/dependency 已生成。这是单元、SQL 绑定及传输契约测试，不是 MySQL 集成。
- PowerShell 把 Mockito stderr 警告包装成 NativeCommandError，外层返回 exit 1；已核对 Maven 最终 BUILD SUCCESS 与 Surefire XML。计数见 [测试结果 JSON](mcp-gaps-test-results-20260911.json)。mcp-gaps-tests-20260911.log 和 mcp-gaps-package-20260911.log 受日志忽略规则影响，仅保存在本地。
- stdio TypeScript 编译与 **4 项测试通过**，包括真实启动 stdio 子进程 tools/list、参数上下文、结构化错误与 HTTP 失败脱敏；HTTP 测试服务是本地 fixture，不是平台写入验收。
- 清点脚本、git diff --check 通过（有 CRLF 提示）。初次测试发现历史查询 LIKE 转义与 SQL ESCAPE 不一致，已修复并回归。
- Docker version 失败：dockerDesktopLinuxEngine 管道不存在。
- 前端只读 lint：**9 errors、379 warnings，未通过**。本轮未改前端，未批量修复现存规则问题；明细见本地 mcp-gaps-frontend-lint-20260911.log。
- 前端 `npm --prefix frontend run build` 已走完 vue-tsc 类型检查、Vite 生产构建及压缩产物生成。存在依赖 eval、包体积等警告；PowerShell 同样将 stderr 包装成 NativeCommandError，外层 exit 1，不能作为零警告构建。原始日志为 mcp-gaps-frontend-build-20260911.log。

## 未完成验证与风险

没有新增迁移；真实 MySQL 的 SQL、行锁、逐项事务和并发幂等尚未取证。附件旧表没有 projectId，目前校验存在性与 Token 用户拥有关系。计划行锁不能作为所有既有 UI/执行启动路径都已通过并发测试的证明。

| 未完成项 | 原因 / 风险 | 后续准确命令与条件 |
| --- | --- | --- |
| 全量单测与数据库集成 | 仅执行相关集合；无运行中的数据库环境，真实持久化未取证 | 准备测试依赖后 `.\mvnw.cmd -pl backend/services/agent-integration -am test`；仍需真实 MCP 调用并核对数据，不能以 Mock 测试替代 |
| Docker 构建、启动、健康、迁移 | Engine 不可用，未获得启动与新库升级证据 | 启动 Docker Desktop 后 `docker build -f Dockerfile.backend -t metersphere:mcp-gaps .`；准备本地配置后 `docker compose -f cds-compose.yml up -d app`、`docker compose -f cds-compose.yml ps`、`docker compose -f cds-compose.yml logs --tail 100 app`；compose 为仓库开发启动方式，不是前一镜像部署验收 |
| 平台 E2E | 未部署、无目标账号和 Runner 的真实取证 | 设置 MS_BASE_URL、MS_AGENT_TOKEN、MS_PROJECT_ID 后 `powershell -ExecutionPolicy Bypass -File scripts/verify-agent-conversation-loop.ps1 -BaseUrl $env:MS_BASE_URL -Token $env:MS_AGENT_TOKEN -ProjectId $env:MS_PROJECT_ID -SkipProjectCreate`；此脚本仅补充既有链路，还需逐项调用新增工具核对页面/数据库，覆盖越权、版本冲突、重复回写、token 重放 |
| 平台内嵌 stdio 包 | 源码/dist 已构建，未重打包发布平台内嵌包 | `powershell -ExecutionPolicy Bypass -File scripts/pack-metersphere-mcp.ps1`，再在受控环境部署并重新下载验证 |

功能保持**部分完成**，本记录不替代 task008 的真实执行验收。
