# 两份方案实施与验证记录

基线：2026-10-22，当前工作区已有未提交改动。本轮修改前的文件备份与触及清单位于 `.codex-tmp/implementation-20261022-baseline/`。不操作生产，不覆盖既有业务数据。

## 需求追踪

| 编号 | 入口/前端 | 后端/数据配置 | 实施与验收状态 |
| --- | --- | --- | --- |
| DEF-01 | 计划详情及缓存导航 | 既有详情/统计接口，无预期迁移 | 路由监听、重置、响应隔离及离开保护已实现；构建通过，页面冒烟待执行 |
| DEF-02 | 测试平台新增配置 | 环境服务、错误映射、配置表 | 完整 URL/Origin 校验及错误码已实现；服务定向测试通过，真实新增待验收 |
| DEF-03 | 登录方式新增配置 | 登录服务、错误映射、配置表 | JSON/地址/超时校验及错误码已实现；服务定向测试通过，真实新增待验收 |
| REQ-01 | MCP，无人工录入 | URL 下载、安全校验、附件/证据、幂等及存储补偿 | 三类工具及转存已实现；安全、幂等与工具契约测试通过，实际转存待验收 |
| REQ-02 | 计划详情按钮 | 报告后端保留 | 入口及专属处理已移除；构建通过，页面冒烟待执行 |
| REQ-03 | 项目级同步操作 | 计划用例及缺陷关联，补齐不删除人工关系 | 已接入既有同步事务及计划锁；单元测试与真实 MySQL 幂等、过滤测试通过；页面链路待验收 |
| REQ-04 | 生成用例导航及旧路由 | 后端生成能力和历史数据保留 | 导航移除、旧路由兼容已实现；路由检查和构建通过，真实页面待验收 |
| REQ-05 | 业务文档生成入口 | 上传解析保留 | 顶部及行内生成入口已移除；构建通过，真实页面待验收 |
| REQ-06 | 用例资产四项控件 | 取消本页专属请求，数据保留 | 四项控件及专属请求已移除；构建通过，真实页面待验收 |
| INC-R01 | 执行状态/回写 | 排序规则与恢复查询 | 迁移 118 已实现，保留原 BINARY 精确比较；混合排序规则 MySQL 测试通过，完整启动迁移待验证 |
| INC-R02 | 执行观测/日志 | 调度扫描错误边界及退避 | 共享租约、退避、恢复状态及告警已实现；定向测试通过，运行演练待执行 |
| INC-R03 | 传统 Excel 导入 | 任务状态、持久化、异常提示 | 已接入独立持久任务及历史查询；回滚后留痕、用户隔离和中断测试通过，真实文件导入待验收 |
| INC-R04 | 导入限制与进度 | 资源/事务及限流 | 文件/解压/行数上限、全局并发与项目互斥、进度及心跳已实现；边界测试通过，代表性负载待隔离测量 |
| INC-R05 | 详情和批量读取 | 鉴权及重复查询 | 批量鉴权查询合并且保留权限、项目及删除校验，定向测试通过；实际 SQL/延迟收益待测量 |
| INC-R06 | 列表和统计 | 慢日志、执行计划及索引 | 待离线证据，不盲目改索引 |
| INC-R07 | 请求失败恢复 | 两套连接池及数据库故障恢复 | 待隔离演练 |
| INC-R08 | 运维观测 | 请求/连接池/JVM/数据库现场留存 | 请求关联、运行指标接口及现场脚本已实现；Trace 测试通过，诊断页面和采集待运行 |
| INC-R09 | 运维日志 | 日志容量、级别和慢日志轮转 | 应用日志 gzip、容量上限、SQL 日志默认 INFO 及脱敏已实现；生产慢日志轮转/容量策略仍需现场配置和验证 |
| INC-R10 | 运维恢复 | 数据库有序停止 | 隔离 compose 的 MySQL 停止宽限 120 秒已配置；演练待执行 |
| INC-R11 | 登录/退出/过期 | 会话销毁警告诊断 | 会话日志脱敏、事件计数及指纹已实现；销毁警告根因待复现，不能标记已修复 |

## 实施约定

- URL 首期按方案默认值：PNG/JPEG、5MiB、公网 HTTP/HTTPS 80/443；执行证据沿用提交门禁发布。
- 同步缺陷先补齐缺失关联，保留人工关联及历史记录，不默认同步解除关系。
- 生成页采用入口移除及旧路由兼容，保留共享服务和历史数据。
- 所有状态以实际测试/运行证据更新；未经验证不得标为完成。

## 验证记录

### 已执行的检查

证据保存在 `outputs/implementation-20261022/`，包含实际命令输出，未包含隔离环境密码文件。

- 前端 `npm run type:check`：通过；`npm run build`：通过，构建用时约 4 分 31 秒。
- 前端变更文件 ESLint：0 错误、12 警告，保留完整输出；尚不能声明无警告。
- 前端 `npm run test:api-contracts`、`npm run test:route-tabs`：通过。
- MCP `npm test`：24 项通过。
- 后端定向测试及 Maven 打包：此前一轮通过。该轮 system-setting 4 项、传统导入校验 2 项、agent-integration 31 项（30 通过、外网下载 1 项按条件跳过）；包含真实 MySQL 的排序规则迁移、恢复扫描、导入任务和缺陷同步测试。
- 后续增加计划关联锁及打包一致性保护，正在重新执行相关测试和打包。上一次重跑因隔离测试数据库停止而连接失败，数据库已恢复；该次失败不计为通过。

### 正在完成的运行验证

- 应用 JAR 曾发现嵌入依赖未随模块更新：已加入强制生成应用 JAR，并在镜像脚本中比较本地模块与嵌入依赖字节，等待新包验证。
- Docker 首次构建遇到 BuildKit 会话失败；使用传统构建器后遇到 Alpine CDN TLS 连接失败。脚本已支持 HTTPS 备用源，尚未形成成功镜像证据。
- 隔离验收项目为 `msp-implementation20261022`，数据库、Redis、MinIO、Kafka 已启动；应用启动、健康检查、核心页面端到端流程及数据库断连演练待执行。
- 公网图片真实下载、历史生产慢日志分析、代表性大批量性能和会话告警根因仍未验证。不得将安全单元测试或诊断设施视为这些验收项已完成。

恢复执行后，定向重跑 Maven 打包成功：`TestPlanFunctionalCaseSyncServiceTests` 3 项、`AgentPlanDefectSyncMySqlTests` 1 项、`AgentExternalImageServiceTests` 1 项均通过。镜像脚本的逐模块一致性检查通过，备用 HTTPS Alpine 源依赖下载成功；镜像后续步骤仍在执行。

### 本次恢复执行的新增证据与环境问题

- `AgentExternalImageNetworkTests` 已显式开启并通过（1 项），真实访问公网 PNG，包含正常 TLS 校验、DNS 固定连接及图片解码；该结果只证明下载器链路，不替代业务附件和执行证据端到端验收。
- 镜像构建成功：`msp-implementation20261022:verify`，镜像 ID `d98d639eefc6`，应用 JAR SHA256 `bc94d69515322c33e5052ebd4287de91e343ecba68d535faec1c58a49dae5b5a`。
- 应用容器已创建并启动，四项依赖健康；尚未获得应用 readiness 成功证据。随后 Docker API 的查询、日志和停止操作均返回 HTTP 500，切换 API 1.45 后仍失败。
- 实测主机可用内存约 700 MiB；Docker 虚拟机 7855 MiB 内存中使用 7488 MiB、available 170 MiB，2048 MiB swap 全部用满。WSL 后续请求也失败。不能据此断言应用存在内存泄漏或启动代码缺陷。
- 独立单测数据库 `msp-implementation-mysql-20261022` 已成功停止以释放资源。本次应用停止请求因 Docker API 故障失败，实际运行状态尚不确定。未重启 Docker、未停止既有项目。
- 本次隔离 compose 的应用限额已由 3 GiB / JVM 65% 改为 2 GiB / JVM 55%，待引擎恢复后重新创建；配置尚不等于实际生效。
- 变更范围 `git diff --check` 返回 0。此检查不替代新文件的编译和业务验收。

### 前后端对应关系及主要文件

| 功能 | 前端或消费者 | 后端及数据 |
| --- | --- | --- |
| 计划详情隔离与报告入口 | `frontend/src/views/test-plan/testPlan/detail/index.vue` | 复用 `/test-plan` 详情和统计；响应序号及路由/项目标识校验避免旧响应覆盖 |
| 配置创建校验 | `frontend/src/views/agent/environment-profile.vue`、`login-profile.vue`、`utils/platformConfiguration.ts` | `/ai/environment-profiles`、`/ai/login-profiles`；对应 Service 与 `AgentSafeErrorMapper` |
| 外部图片 | `metersphere-mcp/src/tools/caseLibrary.ts`，无人工录入入口 | `AgentExternalImageTools` → `AgentExternalImageService` → 安全下载器 → 既有附件/证据存储及幂等记录 |
| 同步缺陷 | 用例列表既有同步入口与返回数量 | `TestPlanFunctionalCaseService` → `TestPlanDefectSyncService` → `bug_relation_case`，沿用事务和计划锁 |
| 隐藏入口 | caseManagement 路由/pathMap、业务文档和用例资产页面 | 保留共享服务与既有数据，旧生成页路由兼容跳转 |
| 传统导入追踪 | `LegacyImportHistory.vue`、既有导入抽屉 | `/functional/case/import/jobs`、旧 Excel 导入包装；迁移 117 |
| 恢复诊断 | 执行中心 diagnostics、`recovery-health.vue` | `/ai/recovery/status`、`/runtime`；迁移 116，共享租约/退避 |
| 排序规则整改 | 执行状态与恢复流程 | 迁移 118，限定标识列转换与转换冲突预检 |
| 请求、会话及日志 | 统一错误 traceId、诊断页 | `RequestTraceFilter`、异常处理、会话诊断、logback；现场采集及慢日志离线分析脚本 |

变更文件清单位于 `outputs/implementation-20261022/changed-files.txt`；本次修改前备份保留在基线目录。工作区还存在原有改动，不能将完整 `git diff` 都归为本轮成果。

### 可复核命令及未完成检查

本次确实执行并通过：

```powershell
.\mvnw.cmd -pl backend/app -am package '-Dtest=TestPlanFunctionalCaseSyncServiceTests,AgentPlanDefectSyncMySqlTests,AgentExternalImageServiceTests' '-Dquality.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.compiler.useIncrementalCompilation=false'
.\mvnw.cmd -pl backend/services/agent-integration -am test '-Dtest=AgentExternalImageNetworkTests' '-Dexternal.image.network=true' '-Dsurefire.failIfNoSpecifiedTests=false'
$env:DOCKER_BUILDKIT='0'
python scripts/build-isolated-implementation.py --alpine-mirror https://dl-2.alpinelinux.org/alpine
```

引擎恢复并有足够资源后，继续以下命令。环境文件只在本机保存，不随证据发布：

```powershell
docker compose --env-file .codex-tmp/implementation.env -p msp-implementation20261022 -f .codex-tmp/implementation.compose.yml up -d --wait app
Invoke-RestMethod http://127.0.0.1:17079/actuator/health/readiness
playwright-cli.cmd -s=implementation-20261022 goto http://127.0.0.1:15179/#/login/admin
playwright-cli.cmd -s=implementation-20261022 snapshot
```

尚未通过的验收：完整应用迁移/健康检查，计划切换真实页面，配置真实新增及刷新，隐藏入口的页面与键盘检查，MCP 三类图片实际转存/回放/读取及证据提交门禁，真实 Excel 导入，数据库断连/有序停止演练及性能观测。原因是本次 Docker/WSL 资源故障；风险是编译和定向测试未覆盖运行时装配、权限、持久存储及页面行为。

生产慢日志原文件、代表性大规模导入负载、会话销毁告警复现条件仍缺失；离线分析工具和诊断信息不代表这些根因已经解决。全量单元/集成测试未执行，仅执行了前述定向测试及回归集。

变更文件扫描发现两处原有 TODO：传统导入控制器的多模板功能、用例表格的详情抽屉编辑说明，均存在于本轮修改前备份中。本轮未将这些注释所指能力计为新增实现或完成项。

### 再次恢复后的资源保护与验证

用户明确要求避免再次耗尽本机资源。本次使用已有镜像，未重新运行构建，也未启动额外浏览器。

- Docker 启动时自动恢复五个 `ms-dev-*` 容器，可用内存降至约 530 MiB；已停止刚启动的 Docker Desktop。用户随后明确授权临时停止这五个旧容器并保留数据卷，已执行成功。
- 新增 `scripts/guard-isolated-resources.py`：Windows 内存/提交率每 5 秒采样；可用内存低于 1536 MiB 或提交率达到 92% 时，停止本次项目的应用及依赖。严格限制项目名称，不删除卷，不操作其他项目。监控到期也会停止本次环境。
- 新增 `deploy/implementation-verify.compose.yml`：沿用已有镜像，应用 1536 MiB / 1.5 CPU、JVM 50%，Kafka 堆 128～256 MiB；依赖也受内存限额保护。`docker compose ... config --quiet` 通过。
- `--sample-only` 实测成功；非法项目名检查拒绝 `shared-project`。触线停止已实测两次，均成功停止本次五个容器。应用 `OOMKilled=false`，退出 137 来自保护过程的停止，不能作为 OOM 或应用内存泄漏证据。
- 第二次启动 Flyway 验证 155 项迁移成功，实际执行到 `3.7.2.35` 附近时保护触发。完整迁移、应用 readiness 及业务端到端仍未通过。不要将迁移文件校验成功等同于迁移全部执行成功。
- 原 2560 MiB 启动门槛不足，现提升为 Docker 已就绪时至少空闲 3584 MiB；仍保留运行期间 1536 MiB 停止线。最新预检测得约 1779 MiB，返回 `startupAllowed=false`，保持容器停止，等待更多资源。

具体资源约束、命令和停止步骤见[资源验收操作记录](implementation-resource-runbook-20261022.md)。本轮新增证据：`implementation-resource-guard*.log`、`implementation-startup-low-resource*.log`、`resource-preflight.log`，保存在同一输出目录。

用户询问 IDEA 与虚拟内存差异后，已核查 IDEA 的 `Application` 是本机 Spring Boot 配置，主类 `io.metersphere.Application`，无显式堆参数；本地脚本采用本机 Java 加 Docker 依赖。本次整套容器验收的资源门槛不能作为项目或 IDEA 的最低配置结论。已保持全部容器停止，后续优先评估单实例本机运行及依赖复用，不继续盲目启动或要求增大虚拟内存。

### 无 Docker 替代验证

用户选择暂不继续释放内存，要求尝试其他测试方式。已保持 Docker Desktop 关闭，采用串行 Node/Java 小进程执行，未重跑业务生产构建、原测试集或镜像构建。

- 前端真实配置校验函数新增 36 项输入测试全部通过，新增测试脚本 lint 最终退出 0；覆盖完整地址、Origin、凭据、协议/端口和 JSON 对象校验。新增 `npm run test:platform-configuration` 命令。
- 已打包下载器的 9 项真实网络/安全冒烟全部通过，包含 JPEG、重定向至内网、SVG/HTML 拒绝、404、元数据地址、凭据 URL、禁止端口及超时。不连接数据库、不 Mock HTTP，不将此结果等同于 MCP 业务验收。
- 新增独立迁移入口，最大堆 128 MiB；从同一产物加载 Flyway 和迁移，固定本次隔离库、历史表与 baseline 配置。新增 migration profile，仅依赖隔离 MySQL，迁移进程限 384 MiB / 0.5 CPU。工具编译、产物检查和 compose 配置检查通过，实际迁移尚未执行。
- 下载器、权限、附件正式目录及执行门禁做了代码链路核查；实际存储读取、事务与端到端仍待验证。
- 本轮仅新增测试与验收工具、脚本入口和文档，没有修改业务实现；原构建产物继续有效。具体文件、命令、日志、失败修正和验收边界见[无 Docker 补充验证记录](implementation-lightweight-verification-20261022.md)。

当前整体状态：**部分完成；新增 45 项补充检查通过，完整迁移、健康检查、真实页面及在线 MCP 仍未验收**。
