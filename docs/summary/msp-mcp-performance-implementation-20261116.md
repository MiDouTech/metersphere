# MSP MCP 接口修复实施与验证记录

日期：2026-11-16。总体状态：部分完成，未部署。方案见同目录 `msp-mcp-performance-remediation-plan-20261116.md`。

## 需求追踪与交付边界

| 需求 | 实际实现 | 状态/验收 |
|---|---|---|
| R1 编辑契约 | 原生 MCP 明确 8 个字段，运行时校验；Node 同步空值、嵌套步骤和字段约束 | 已实现，新增契约测试 |
| R2 安全错误 | 专用字段错误；安全合并 details 和 nextAction；Node 白名单保留修正提示 | Node 测试通过，后端 40 项相关测试通过，真实运行验收未完成 |
| R3 可观测性 | MCP 总耗时、traceId、MDC 恢复；图片解析/源读取/证据读取/事务/业务/计划发布分段日志 | 已实现，尚未取得线上慢请求样本；未新增指标采集、SQL 锁等待采集器 |
| R4 上传优化 | 图片解析移至写事务之前；完成请求优先重放；SHA-256 复用 | 已实现；Token 配额锁仍保留，active/physical 两次 SUM 合并为一次条件聚合，专用配额表未实施 |
| R5 二进制上传 | 保留既有 Base64 与 BLOB 协议 | 未实施，依赖文件存储及完整上传状态机验证 |
| R6 提交优化 | 有界批量查询并锁定证据；批量写入报告证据、消费状态和正式附件 | 已实现，保持同事务与原有所有未归档计划同步语义；未改成异步 |
| R7 恢复 | contract 增加 workflow/recovery；REQUEST_IN_PROGRESS 增加 retryAfterMs；Node 保留安全恢复提示 | 已实现；复用现有 operation.get，不增加自动写重试 |

## 入口、权限与前后端对应

入口是 `/api/mcp` 的 tools/list/tools/call，Node stdio 作为薄封装。case.update → AgentCaseManageService → 既有权限/版本/业务规则 → 持久化；execution.upload/submit/get → AgentDirectExecutionService → 原有权限和幂等事务 → 证据/报告/附件/用例及计划结果。

本次未新增前端路由或页面，不改变既有成功字段和同步状态。现有用例详情和计划详情继续消费原数据。实际 UI 冒烟尚未执行，不能据此宣称前端端到端通过。

自定义字段保留模板动态取值（包括数字与集合），不强制转换为 schema 字符串；仍由既有模板/业务逻辑处理。

步骤中的 actualResult/executeResult 原本可被编辑 DTO 接受，本次为兼容保留，并在 schema/文档明确不能替代正式执行提交。未擅自删除历史字段。

## 修改文件

后端生产目录：`backend/services/agent-integration/src/main/java/io/metersphere/agent/`。

- `service/FunctionalCasePatchContract.java`（新增）：共享字段 schema 与运行校验。
- `service/AgentFieldValidationException.java`（新增）：受控字段提示。
- `service/AgentSafeErrorMapper.java`：错误码与恢复详情合并。
- `tool/functional/FunctionalCaseUpdateToolHandler.java`：描述、schema。
- `service/AgentCaseManageService.java`：执行前字段校验。
- `service/AgentDirectExecutionService.java`：流程指引、上传预处理、单次配额聚合、定义 hash 复用、批量证据查询和写入。
- `service/AgentMcpStreamableService.java`：请求耗时、traceId 生命周期。
- `service/AgentMcpTiming.java`（新增）：阶段计时。
- `service/AgentOperationExecutor.java`：事务和业务阶段计时。
- `deploy/conf/metersphere.properties.example`：总耗时阈值配置。
- `metersphere-mcp/src/client.ts`、`src/tools/caseLibrary.ts`、`README.md`：桥接错误详情、契约及使用说明。
- `FunctionalCasePatchContractTests.java`（新增）、`AgentMcpStreamableServiceTests.java`、`metersphere-mcp/test/patch-recovery.test.mjs`（新增）：相关测试。

## 验证记录

已完成的验证如下；未完成项单独披露，不将待执行命令视为通过。

已执行：`npm.cmd --prefix metersphere-mcp test`，34 项全部通过，包含 TypeScript 编译、真实 stdio 发现、HTTP 桥接和新增字段/安全详情测试。

Docker：初始守护进程不可连接；尝试启动本机 Docker Desktop 后，daemon 返回 `Docker Desktop is unable to start`。因此无法启动隔离 MySQL 或运行镜像，真实 SQL 事务和锁等待仍未经验证。

后端 Maven wrapper 启动后长时间无输出，改用已缓存 Maven 3.8.3 二进制离线运行，避免 wrapper 初始化等待。复现方式：

```powershell
$mavenExecutable = 'C:\Users\midoo\.m2\wrapper\dists\apache-maven-3.8.3-bin\5a6n1u8or3307vo2u2jgmkhm0t\apache-maven-3.8.3\bin\mvn.cmd'
& $mavenExecutable -o -pl backend/services/agent-integration -am test '-Dtest=FunctionalCasePatchContractTests,AgentMcpStreamableServiceTests,AgentDirectExecutionTests' '-Dsurefire.failIfNoSpecifiedTests=false'
```

后端定向测试：24 项通过。扩展验证与应用打包：40 项通过，BUILD SUCCESS，产出 backend/app/target/app-3.x.jar。完整命令：

```powershell
& $mavenExecutable -o -pl backend/app -am package '-Dtest=FunctionalCasePatchContractTests,AgentMcpStreamableServiceTests,AgentMcpStreamableControllerTests,AgentDirectExecutionTests,AgentSafeErrorMapperTests,AgentExecutionExceptionHandlerTests,AgentMcpToolContractTests' '-Dsurefire.failIfNoSpecifiedTests=false'
```

- `docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml config --quiet`：通过，使用 README 中隔离测试变量。
- `git diff --check`：通过。
- frontend 目录执行 `.\node_modules\.bin\eslint.cmd src/api/modules/case-management/featureCase.ts src/router/routes/modules/caseManagement.ts`：0 错误、1 个既有未使用变量警告，未改动该无关代码。
- 全量前端 lint、独立 type:check 和首次 build：已尝试；因可用内存不足 400 MB 导致资源竞争，主动终止这些检查。没有计为通过。后端结束后重新串行执行生产构建，成功（退出码 0）。完整 vue-tsc 类型检查通过；Vite 构建阶段耗时 4m17s，存在大体积 chunk 提示。
- 证据日志：`docs/testing/msp-mcp-performance-20261116/backend-focused.log`、`backend-package.log`、`node-tests.log`、`frontend-build.log`、`frontend-lint-focused.log`。

## 未验证项及精确执行入口

1. 真实 MySQL：Docker 恢复后启动专用 13326 测试数据库，执行方案中的 `-Dquality.mysql=true` 命令；禁止使用生产库。风险：批量 SQL、回滚、锁顺序和并发尚未由真实数据库证明。
2. 镜像/运行：按 `deploy/quality-verify/README.md` 构建后端、解包 JAR、`docker build -f Dockerfile.backend --build-arg DEPENDENCY=backend/app/target/quality-dependency -t msp-quality:verify .`，随后 `docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml up -d --wait` 与 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-quality-environment.ps1`。需先设置隔离测试环境变量，见该 README。
3. UI 端到端：测试环境运行后，在 frontend 目录执行 `npx playwright test e2e/mcp-case-library.spec.ts`，按用例要求配置测试身份和环境；追加上传、提交及计划状态的真实断言。风险：当前未验证 UI 刷新与附件展示。
4. 压测：执行方案第 13 节的图片/并发/步骤/关联计划矩阵并保留 trace。未采集优化前后基线，不能声称达到性能目标。
5. 数据迁移：本次无数据库迁移，未新增索引；方案中的配额表、上传意图和异步事件迁移仍未实施。

### 隔离数据库复现命令

Docker 恢复后先确认 13326 端口与下列容器名称未被占用，再在仓库根执行。此容器仅用于本地测试，口令与测试类硬编码一致。

```powershell
docker run --name msp-mcp-mysql-20261116 -e MYSQL_ROOT_PASSWORD=quality-test-only -e MYSQL_DATABASE=quality_verify -p 127.0.0.1:13326:3306 -d mysql:8.0.35
docker exec msp-mcp-mysql-20261116 mysqladmin ping -uroot -pquality-test-only --silent
& $mavenExecutable -o -pl backend/services/agent-integration -am test '-Dtest=AgentDirectExecutionMySqlTests,AgentOperationMySqlTests,AgentPersonalOperationMySqlTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dquality.mysql=true'
```

mysqladmin 未就绪时等待并重试健康命令，健康成功再运行 Maven。测试需创建隔离 schema；不要切换生产库运行。上述命令本次未执行。

## 已知限制

- 专用配额表、二进制上传、显式计划上下文、异步计划传播仍为后续方案项，当前不能宣称全部方案已交付。
- 批量 JDBC 调用减少应用往返/调用开销，实际驱动是否合并网络批次依赖 JDBC 配置；没有压测不宣称确定加速比例。
- 总耗时阈值可配置，阶段日志采用固定 1000ms 警告阈值；低于阈值的阶段日志需 DEBUG。
- 原有同 Token 上传锁及全关联计划传播仍可能造成竞争；本次提供定位证据并缩小部分开销，没有根除所有潜在瓶颈。
- 不发布、不部署、不修改生产数据；原截图分钟级延迟根因仍需原任务 trace 对账。


## 最终验证汇总

| 命令/检查 | 结果 |
|---|---|
| 后端定向 Maven test（第一个命令） | 24 项通过 |
| 后端扩展 Maven package（第二个命令） | 40 项通过，应用 JAR 打包成功 |
| npm.cmd --prefix metersphere-mcp test | 34 项通过，包含 TypeScript 编译 |
| npm.cmd --prefix frontend run build | 成功，包含完整 vue-tsc 类型检查和 postbuild |
| 前端相关入口/API 定向 eslint | 0 错误、1 个既有警告 |
| Compose config --quiet | 通过 |
| git diff --check | 通过 |
| 真实 MySQL、Docker build/up/health、UI E2E、性能压测 | 未执行：Docker Desktop 无法启动；所需真实环境不存在 |

全量前端 lint 与独立 skipLibCheck 类型检查已尝试后中止，不记为通过；生产构建中的完整类型检查已通过。当前未修改前端业务文件，没有新增数据库迁移。原有同 Token 锁和全计划同步仍存在，不能承诺分钟级耗时已消失。

最终状态：部分完成。已实现并通过代码/构建检查的改动可供审查；运行验证与方案中可选架构升级未交付。未提交 Git commit，未发布客户端包，未部署任何环境。
