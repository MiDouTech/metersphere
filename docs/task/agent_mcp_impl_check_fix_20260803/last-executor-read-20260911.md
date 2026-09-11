# MCP 用例最后执行人读取

本次范围：补充现有 MCP 用例查询响应中的最后执行人 ID 和姓名。不新增写入接口，不改变执行回写行为。task008 全量自动化验收仍未完成。

## 需求追踪

| 需求 | 入口与实现 | 数据/配置 | 验收证据 |
| --- | --- | --- | --- |
| 读取用例库最后执行人 | 原生 `metersphere.functional.search/get` → AgentFunctionalCaseSearchService → AgentCaseSchemaMapper → AgentCaseDTO | 复用 lastExecuteUser/lastExecuteUserName，无迁移 | 列表映射、详情服务测试；字段与 executeUser 分配人区分 |
| stdio 同样可读 | `search_functional_cases/get_functional_case` → `/api/agent/v1/functional/search`、`/{caseId}` | 薄封装透传后端 JSON，无客户端参数变更 | TypeScript 检查，未做在线 stdio 联调 |
| 计划上下文一致 | `metersphere.test_plan.cases` 及带 testPlanId 的用例查询 | 读取计划关联记录的最后执行人 | 计划/用例库执行人不同时保留计划值；未执行计划不得回退到库执行人 |
| 不读取步骤也可读执行人 | includeSteps=false | 执行人映射不依赖步骤导出 | 详情服务测试、列表直接映射 |
| 空值边界 | 未执行、用户姓名不可解析 | 保留源数据空值，不虚构姓名 | 未执行为空、只有用户 ID 时保留 ID |

前端对应：`frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue` 已在 lastExecuteUserName 槽位显示姓名或 `-`；本次无前端变更。现有 FUNCTIONAL_READ、项目上下文及业务权限链路复用，不增加用户目录查询，也不返回额外用户资料。

新增只读返回字段：

```json
{
  "lastExecuteUser": "user-id",
  "lastExecuteUserName": "执行人姓名"
}
```

以上为字段示例，不是真实执行数据。未执行时为空；字段不代表 `executedBy` Agent 标识。现有 get 带计划参数但未匹配到关联记录的行为保持原有用例库回退语义；本次不修改关联查询规则。

## 修改文件

- `backend/services/agent-integration/src/main/java/io/metersphere/agent/dto/AgentCaseDTO.java`：增加两个响应字段。
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/mapper/AgentCaseSchemaMapper.java`：列表、计划及详情映射，保留计划执行上下文。
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentFunctionalCaseSearchService.java`：详情匹配计划关联记录后同步执行人。
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/mapper/AgentCaseSchemaMapperTests.java`：增加 3 个边界/上下文测试。
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/AgentFunctionalCaseSearchServiceTests.java`：增加 2 个详情查询服务测试。
- `metersphere-mcp/README.md`：说明字段、适用工具及空值语义。
- 本报告。

## 验证命令

```powershell
.\mvnw.cmd -pl backend/services/agent-integration -am '-Dtest=AgentCaseSchemaMapperTests,AgentFunctionalCaseSearchServiceTests,AgentFunctionalCaseControllerTests,AgentMcpToolContractTests,AgentMcpStreamableServiceTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
node metersphere-mcp/node_modules/typescript/bin/tsc -p metersphere-mcp/tsconfig.json --noEmit
git diff --check
docker version --format '{{.Server.Version}}'
```

TypeScript 检查通过；diff 检查通过，仅有现有换行转换提示。Docker Engine 命名管道不存在，无法连接 daemon。

Maven 日志显示 BUILD SUCCESS，编译关联后端模块并运行 **30 项测试，0 失败、0 错误、0 跳过**：Mapper 8 项、SearchService 9 项、Controller 7 项、MCP Contract 2 项、MCP StreamableService 4 项。本次新增 5 项测试包含执行上下文覆盖和空值分支。已核对 Surefire XML；这是单元/服务测试，不是真实数据库集成测试。外层 PowerShell 因 Mockito stderr 警告记录 NativeCommandError、工具退出码为 1，因此测试结论依据 Maven 日志与本次 XML 双重证据。原始日志为同目录 `last-executor-read-tests-20260911.log`。

## 未执行验证、原因与风险

- 真实数据库集成和真实 MCP E2E：本次单测模拟底层数据服务，没有连接测试平台；不能据此验证真实账号、数据库、部署版本。后续在配置好的测试环境调用现有 search/get，分别校验有执行记录、未执行、缺失用户、无权限项目、带计划及 includeSteps 两种值。
- 容器镜像/启动/健康检查：Docker daemon 不可用；恢复 Docker 后，按项目打包流程准备依赖，执行 `docker build -f Dockerfile.backend -t metersphere:last-executor-read .`，再使用实际部署配置启动并检查应用健康。未验证运行时兼容性。
- 前端类型、lint、生产构建：本次不修改前端或前端接口契约，仅扩展 Agent DTO，未重复运行前端检查。可执行 `npm --prefix frontend run type:check`、`npm --prefix frontend run build`；在 frontend 目录执行 `npx eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts`。页面运行状态不在本次验收证据内。
- 完整后端打包：定向测试会编译关联后端模块，但未执行应用打包；后续命令 `.\mvnw.cmd -pl backend/app -am package -DskipTests`。
- 数据库迁移验证：无表结构、SQL、迁移或配置变更，不适用；未对新环境初始化作结论。
- task008 的 Browser/Desktop 实际执行、凭据、事件、产物、全链路回写：超出本次字段读取增量，未执行，不修改 task008 完成状态。

已知遗留：旧计划外 functional.submit 不更新最后执行人的问题不在此次读取需求内，未修复；因此读到空值或旧值时应核对原始执行回写链路。

状态：部分完成（读取实现及 30 项定向测试通过，容器及真实平台验收尚未通过）。
