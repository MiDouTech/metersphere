# MSP SQL 稳定性优化实施记录（2026-10-23）

对应方案：[完整优化方案](msp-sql-stability-optimization-plan-20261023.md)。本次实施对象为当前本地源码，未部署生产、未执行生产 DDL、未扫描或下载生产慢日志。

## 1. 结论和范围

已经落实 SQL 关联、分页计数、批量详情、计划统计、分层超时、入口并发保护、导入事务边界、可靠后置处理及运行指标。功能状态仍为 **部分完成**：源码回归通过不代表生产高峰性能及历史事故根因已确认。下述验收和发布门槛均需要保留。

工作区同时存在其他功能改动；本文仅归属本次 SQL 优化修改，不能把同一工作区其他改动自动视为本次交付或已验收。

## 2. 需求追踪与前后端对应

| 需求 | 用户入口 / API | 实现及数据变化 | 已有验收证据 | 状态与缺口 |
| --- | --- | --- | --- | --- |
| O01 元数据唯一关联 | 用例库列表、Agent 库搜索 | ExtFunctionalCaseMapper.xml：从 project 读取可信 organization_id，补齐复合唯一键；来源及分类过滤同样限定组织 | 真实 MySQL：同项目同引用跨组织元数据不串用、不重复；来源过滤正确 | 实现完成；代表数据执行计划和峰值延迟待验收 |
| O02 精简 COUNT | 同上分页接口 | list / list_COUNT 共用完整 listWhere；COUNT 不连接展示表；默认排序增加 id | 真实 PageHelper 分页：总数、页边界、空集、标签、优先级、来源过滤；断言 COUNT 无展示 JOIN | 实现完成；完整组合筛选及高并发性能待验收 |
| O03 批量详情 | Agent/MCP 查询与单例详情 | 每批最多 200；基础行按已授权项目过滤；正文、自定义字段、执行人批量取；模板和成员选项批内复用；模板深复制；includeSteps=false 不取步骤大字段 | 100 条同模板仅调用一次各批量 Mapper；500 条分三批；缺失/越权在正文读取前失败；字段值不串用；真实 MySQL 验证轻量投影 | 实现完成；极端正文、真实端到端性能待验收 |
| O04 计划汇总 | Agent 用例关联计划概览 | CTE 分离关联、相关计划、计划聚合，共享计划仅聚合一次 | 真实 MySQL 对比旧单例统计：共享计划、重复关联、删除用例、归档、零可计数用例 | 实现完成；大计划基准待验收 |
| O05 预算与入口保护 | MCP、Agent 功能用例、列表、新旧导入 | MyBatis 25 秒；业务池获取 3000ms；Quartz 维持 30000ms；唯一池名；默认并发 8，鉴权前无等待拒绝；同步/异步完成释放许可；补偿使用独立单线程调度器，原有定时任务保持原池 | 过滤器测试；真实 JDBC SQL 超时、单连接池耗尽、中断连接后重新建连 | 实现完成；跨实例总预算、代理超时和生产演练待验收 |
| O06 导入短事务与可靠后置处理 | 新版预检提交页面、Excel 原同步入口 / FunctionalCaseFileService.importExcel | 解析及预处理不占写事务；旧入口写入统一 120 秒事务；通知、Hub 与资产发布写入同事务 outbox；迁移 119 | 真实 MySQL：回滚撤销队列、失败退避及恢复；浏览器新版导入 501 条成功，旧入口 1 条成功；新增 502 用例，1004 条补偿全部 SUCCESS | 部分完成；断进程/多实例/外部重复投递验收待执行 |
| O07 鉴权负载与撤销 | Agent/MCP | 高开销入口先受并发保护，包含新版预检；保留现有实时 Token、权限及审计语义，未降低凭据哈希强度 | 入口保护测试；真实 Agent 检索成功后撤销 Token，下一次详情请求 HTTP 401 | 部分完成；Token/JDBC/权限独立成本基准、角色变更及并发撤权待执行，不实施未经验证的跨请求授权缓存 |
| O08 恢复扫描 | AI 执行后台恢复 | 两个有界候选集 UNION 后统一全局排序 LIMIT 20；兼容迁移前混合排序规则，保留不透明 ID 大小写精确比较；复用既有迁移 118 | 真实 MySQL 混合排序规则恢复扫描与健康退避测试 | 实现完成；生产升级和大积压公平性待验收 |
| O09 可观测与回滚 | 后台日志、Micrometer、原有错误展示 | 慢/失败请求记录 traceId、Mapper 操作数与耗时；单次 Mapper >=1 秒记录固定 statement ID；线程名；并发/拒绝/慢操作/补偿计数指标；成功队列保留 7 天，逐小时最多清理 1000 条 | 请求追踪及入口测试、编译打包 | 部分完成；监控系统采集、告警阈值及高峰基线未配置 |

没有新增前端业务入口；原有列表、详情、导入、Agent/MCP 接口继续使用原响应数据结构。新增过载响应为 HTTP 503 + Retry-After: 3，JSON 包含 code/message/traceId，提示稍后重试、避免重复提交。前端保留现有统一网络错误处理；页面级重试体验仍需 E2E 验收。

## 3. 主要修改文件

- `backend/services/case-management/.../functional/mapper/ExtFunctionalCaseMapper.xml`、`.java`：唯一关联、共用 WHERE/COUNT、轻量正文批量 SQL、计划聚合。
- `backend/services/case-management/.../functional/service/FunctionalCaseService.java`：项目限定批量详情、导入副作用入队、资产发布入口。
- `backend/services/agent-integration/.../service/AgentFunctionalCaseExportService.java`、`AgentFunctionalCaseSearchService.java`：替换逐条详情、限制批大小、移除外层长事务。
- `backend/services/agent-integration/.../service/AgentExecutionFinalizationService.java`：有界恢复候选查询。
- `backend/services/case-management/.../functional/controller/FunctionalCaseController.java`：默认排序平局按 ID 排序。
- `backend/services/case-management/.../functional/service/FunctionalCaseFileService.java`、`excel/listener/FunctionalCaseImportEventListener.java`：先解析后统一写事务。
- `backend/services/case-management/.../functional/service/FunctionalImportEffectOutbox.java`：持久队列、事务领取、有限重试、保留期。
- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_119__functional_import_effect_outbox.sql`：新表与到期/保留期索引。
- `backend/services/system-setting/.../system/service/CommonNoticeSendService.java`：提供同步可传播异常的通知发送供队列确认；既有异步入口仍异步调用。
- `backend/services/system-setting/.../system/security/DatabaseWorkloadFilter.java`、`RequestTraceFilter.java`、`interceptor/DatabaseQueryTelemetry.java`；`backend/app/.../config/DatabaseWorkloadMetrics.java`：入口隔离及诊断。
- `backend/app/src/main/resources/commons.properties`、`logback-spring.xml`、`deploy/env.prod.example`：预算、池名称、日志与配置示例。
- `backend/services/agent-integration/src/test/.../AgentBatchReadTests.java`、`SqlStabilityMySqlTests.java`、`AgentLibrarySearchMySqlTests.java`、`AgentFunctionalCaseSearchServiceTests.java`；`backend/services/system-setting/src/test/.../DatabaseWorkloadFilterTests.java`：回归与故障测试。
- `scripts/verify-sql-stability.ps1`、`deploy/sql-stability-verify.compose.yml`、`scripts/sql-stability-browser-proxy.mjs`：可重复的独立验收环境和本机页面代理，不复用已有开发/生产卷。

上述 `...` 表示对应模块 `src/main/java/io/metersphere` 或测试 `java/io/metersphere` 包路径。

## 4. 配置与实际边界

| 配置 | 默认值 | 单位 / 作用 |
| --- | --- | --- |
| MS_SQL_TIMEOUT_SECONDS | 25 | 秒，JDBC statement 超时，纠正原 25000 秒配置 |
| MS_DB_ACQUIRE_TIMEOUT_MS | 3000 | 毫秒，仅业务连接池获取连接 |
| MS_DB_HEAVY_REQUEST_CONCURRENCY | 8 | 每实例受保护 HTTP 请求上限，非全局分布式限流 |
| MS_DB_INTERACTIVE_TRANSACTION_SECONDS | 25 | 秒，批量详情只读事务 |
| MS_IMPORT_WRITE_TIMEOUT_SECONDS | 120 | 秒，Excel 导入数据库写事务 |

未增加默认业务池 100 / Quartz 50 的容量。上线前必须汇总所有实例、其他共享项目及运维连接预算，不能只核对 MSP 单实例。

补偿队列使用 `functionalImportEffectsScheduler` 独立单线程，避免外部通知阻塞原有恢复/导入调度；显式保留 `taskScheduler`，池大小仍读 `spring.task.scheduling.pool.size`，默认 1，避免新增调度器被其他任务误用。队列每秒扫描，每轮最多 5 条，初次延迟 30 秒；多实例通过 `FOR UPDATE SKIP LOCKED` 领取。失败退避从 30 秒指数增长，上限 15 分钟，达到 8 次标记 FAILED；数据库不可用暂停扫描 30 秒。FAILED/PENDING 保留对账；只清理 7 天前 SUCCESS。新增 migration 119 必须先于新版写入功能可用；正常 Flyway 启动顺序执行。

## 5. 实际验证记录

证据统一在 `outputs/sql-stability-20261023/`，含失败尝试，不能把初次失败日志当作最终结果。

1. 后端编译：`.\mvnw.cmd -pl backend/services/agent-integration -am -DskipTests compile`，修正类型问题后通过。
2. 完整应用打包 + 相关测试：`.\scripts/verify-sql-stability.ps1 -Stage Package` 已实际执行，最终 `package-isolated-scheduler.log` 为 BUILD SUCCESS；system-setting 4 项、agent-integration 27 项，共 **31 项通过、0 失败、0 跳过**。该轮包含新版预检并发保护和独立补偿调度器的编译；实际线程绑定还通过容器启动验证。MySQL 真实运行在仅绑定本机 13326 的独立测试容器，绝非生产直连。每个新增 SQL 测试建立随机独立库，结束删除自身测试库。
3. 前端 `pnpm run type:check` 通过；`pnpm run build` 通过（`frontend-build.log`）。
4. 前端非修改式检查 `pnpm exec eslint 'src/**/*.{vue,js,jsx,cjs,mjs,ts,tsx,cts,mts}'`，0 errors / 262 warnings（`frontend-lint-source.log`）。方案原命令对 `.` 的 glob 在本机未匹配，已改显式源码 glob 执行。警告未批量自动修复，避免混入无关改动。
5. 前端 `pnpm run test:api-contracts` 通过（`frontend-api-contracts.log`）。
6. `git diff --check` 通过，无空白错误；存在 LF/CRLF 工作副本提示。
7. 新迁移 119 在真实 MySQL 中执行成功，回滚/重试覆盖新表、索引及事务一致性。隔离应用从空库由 Flyway **成功执行全部 157 个迁移，版本到 3.7.2.120**（其中 120 是工作区其他任务变更，不归属本次 SQL 优化），迁移 118/119/120 的 success 均为 1，记录在 `app-startup.log`。
8. `Stage Image -AlpineMirror https://mirrors.aliyun.com/alpine` 构建镜像通过；默认 Alpine 源 TLS 连接失败，保留初次失败日志，不把网络错误归为业务错误。`Stage Start` 启动独立五服务；`Stage Health` 所有容器 healthy，readiness UP（`container-health.log`）。本地名称前缀 `msp-sql-stability20261023`，应用端口 18091/17091。
9. 浏览器验收用 Playwright CLI：本机未安装 ego-browser，转用 CLI；实际登录 admin、打开 13 条种子用例列表、详情显示步骤和计划，下载真实模板、上传并提交 501 条 XLSX，最终显示全部完成、成功 501；另使用同一真实会话和 CSRF 请求旧同步 Excel 接口，返回 successCount=1/failCount=0。`browser-import-progress.log`、`legacy-import-api.log`、`import-db-evidence.txt` 记录页面和数据库证据。刷新页面最终显示 515 条，与初始 13 + 新增 502 一致；截图在 `output/playwright/sql-stability-20261023/list-after-import.png`。
10. 真实 Agent HTTP：初始批量检索 13 条；includeSteps=false 单例步骤为空；导入后 total=515、单页 100 条详情返回成功，一次耗时 602ms；撤销仅本地测试 Token 后下一次请求 401。见 `agent-smoke-summary.json`、`agent-post-import-smoke.json`。602ms 是本机单次请求（同时构建后端），**不是 p95 或优化前后性能对比**。测试 Token 已撤销；本次只在新建隔离库启用管理员入口，没有改动生产设置。
11. 中间构建应用 JAR SHA256：`bc05519a399b2525b7fa3fed4fab01e80be9692ba3dd6ad4080bb99730cd0fb2`；中间镜像 ID：`sha256:7b2e0428d1a86e21993009e7522eaf653def91b1807a7389e57472c7cafd73a9`。第一次镜像包含前述 SQL/导入改造，中间镜像额外纳入新版预检入口并发保护。两轮之间工作区其他任务修改了迁移 120，中间镜像对第一次测试库的校验因此按设计失败（`app-release-startup.log`，120 的 checksum 从 1119977916 变为 247533689）。没有 repair、关闭校验或修改历史表；保留 `metersphere_quality` 的 515 条验收数据，另建 `metersphere_sql_release` 验证冻结镜像：成功执行 **158 个迁移到 3.7.2.121**，readiness UP；旧同步导入再次成功、2 条补偿成功（`app-frozen-startup.log`、`container-frozen-health.log`、`legacy-frozen-api.log`、`frozen-db-evidence.txt`）。迁移 120/121 归属工作区其他任务。该并行迁移变化是集成发布门槛，不能将第一次库冒烟误称为中间镜像原库升级已通过。
12. 独立补偿线程改造后的最终 JAR SHA256：`250659a3491ef83e739b7b8e2522976b534b239868cfdcbb6a47ad243c658908`；最终镜像 ID：`sha256:74a97fd8a8e4b4a592aa312fe58dd13280c10ba8e6e09c93810f9e6597f920b1`（`image-isolated-scheduler.log`、`final-running-image.txt`）。最终容器与此 ID 一致；启动再次成功校验 158 个迁移，数据库 3.7.2.121 无新增迁移、失败迁移数 0；五个服务 healthy/readiness UP。最终镜像上再次调用旧 Excel 入口，HTTP 200、code 100200、成功 1 / 失败 0；新产生的 2 条任务由独立补偿调度处理成功，该库累计 4 条 SUCCESS，无 PENDING/FAILED。证据：`app-final-startup.log`、`container-final-health.log`、`legacy-final-api.log`、`final-db-evidence.txt`。该最终原库重启校验通过与第一阶段因 120 改写产生的校验失败分别保留，不混淆。

## 6. 已知风险及未验收内容

- 历史事故根因及责任容器仍未最终确认。此次修复可证明的源码风险，不以“晚间有任务”或“导入发生在附近”直接归因。
- 暂无相同数据/工作量的生产代表 p95/p99、SQL 数、CPU/I/O 基线对比，不能宣称提速百分比、已达性能目标或数据库不会再故障。
- 批量正文 32MiB 检查发生在读取后，不能视为解压前的严格内存防护；200 条批次仍需极端大正文压力验证。模板种类很多时查询数随不同模板数量增长。
- outbox 提供至少一次处理。外部消息已发送、数据库提交失败时可能重复通知，尚不能称端到端 exactly-once；应以 outbox ID 做外部幂等或业务对账。补偿副作用在独立事务中运行；事务超时不能硬中断所有远程网络调用，外部客户端仍需有限连接/读取超时。
- 隔离环境未配置真实外部通知供应商，队列 SUCCESS 证明本地领取/提交/Hub/资产发布链执行完毕，不代表真实邮件或企业微信接收已验收；本次未发送对外测试消息。
- Excel 原子写事务仍可能随批量增长，120 秒是保护预算，不能替代受控分批/显式幂等导入协议。此次保留原原子性，未偷偷改成部分提交。
- 新导入通知/Hub/资产发布由提交后队列完成，可能延迟；不能用通知到达时间判断导入是否已成功。回滚应用前必须排空或对账队列，不能删表丢待处理数据。
- Mapper 指标是 MyBatis 映射操作，包含获取连接、映射和可能缓存，不等于精确物理 SQL 次数；不覆盖 JdbcTemplate 或异步线程完整成本。请求日志不输出 SQL 参数、凭据或用例正文。
- 指标注册未自动开放公开 actuator 指标端点；生产采集、访问权限、告警及积压最老年龄监控需在受控监控环境落地。
- 并发上限目前按实例生效；调度任务及其他项目不受此 HTTP 保护直接约束。需要配合共享 MySQL 全局容量治理。
- 未执行生产发布、生产索引 DDL、生产代表结构升级/恢复备份演练、全部组合筛选、大附件及多模板复杂导入、并发撤权、多实例强制终止及高峰压测。因此整体状态 **部分完成，禁止按全量完成发布**。本机 501 条纯文本成功不代表 501 条大附件场景已通过。

## 7. 发布与后续验收命令

先在隔离环境执行（`.codex-tmp/sql-stability.env` 为本地测试配置，不含生产凭据，不能复制到生产）：

`Stage Package` 需要隔离单测 MySQL：容器名 `msp-sql-optimization-mysql`，镜像 `mysql:8.0.35`，仅 `127.0.0.1:13326:3306`，库 `quality_verify`、测试 root 密码 `quality-test-only`。本次已创建并执行；不要把该测试凭据用于生产。新机器运行需先准备此独立实例。Compose 使用 `.codex-tmp/sql-stability.env` 中的 QUALITY_DB_PASSWORD、QUALITY_STORAGE_PASSWORD、QUALITY_FACTS_KEY（32 字节 Base64），不可为空或覆盖已有事实加密密钥。

```powershell
.\scripts/verify-sql-stability.ps1 -Stage Package
.\scripts/verify-sql-stability.ps1 -Stage Image
# 默认 Alpine 源网络不可用时，可指定 HTTPS 镜像源：
.\scripts/verify-sql-stability.ps1 -Stage Image -AlpineMirror https://mirrors.aliyun.com/alpine
.\scripts/verify-sql-stability.ps1 -Stage Start
.\scripts/verify-sql-stability.ps1 -Stage Health
node scripts/sql-stability-browser-proxy.mjs
# 上一条作为单独终端服务运行；浏览器使用 http://127.0.0.1:15191/#/login/admin。
docker compose --env-file .codex-tmp/sql-stability.env -p msp-sql-stability20261023 -f deploy/sql-stability-verify.compose.yml logs --tail 200 app
```

发布门槛：迁移成功、登录/列表/详情/导入 E2E、权限/结果一致、断连降级和恢复、500+ 条导入与并发检索压测均通过；生产代表普通 EXPLAIN 与基线确认后再灰度。未经流量证据，不盲加索引、不放大连接池、不延长超时掩盖故障。

回滚触发：结果或权限不一致、错误率及 p95/p99 恶化、持续连接等待、队列积压增长。回滚旧应用和配置前先停止新导入，排空或对账 outbox；新表保留，不自动 DROP。生产需使用已验证旧镜像及明确版本，不能依赖可变 latest。
