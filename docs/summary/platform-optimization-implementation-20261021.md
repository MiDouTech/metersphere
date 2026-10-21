# 平台优化实施记录（2026-10-21）

依据完整整改计划第 10 节及平台优化方案第 19 节。工作区存在其他改动，本记录仅归属本次新增变更；原 U01～U16、R01～R08/R03A 未经重新验证不改为完成。

## 实施前需求追踪

|需求|入口/前端|后端接口/实现|数据配置|测试与状态|
|---|---|---|---|---|
|R04/U13 权限、任务身份、幂等|用例底部导出、停止|创建/查询/停止/下载，新增共享任务服务|export_task 增量字段、唯一键、租约|E01～E04，已实现核心代码及 MySQL 测试；完整角色/双副本验收未完成|
|R04 异步恢复|caseTable.vue，HTTP 创建后轮询|任务详情、本人任务恢复|保留旧 API 返回，状态兼容|E05/E08，网页无 WS 创建已实测；订阅票据/真实重启恢复未验收|
|R04 文件与范围|Excel/XMind 字段选择和下载|两个格式服务、对象权限|文件元数据、过期、输入约束|E06/E07，真实两格式文件及空任务已实测；完整字段往返/容量仍待验收|
|其他 R/U 需求|按方案页面/客户端|各原业务服务|按方案|尚未实现或重新验收，不由导出进度推导完成|

异常边界：跨项目/他人任务拒绝；空集不留中间态；同键异参冲突；创建响应丢失保留原请求键；取消/完成竞争只允许一个发布结果；线程池拒绝及过期租约可查询失败；通知不阻塞创建；清理失败独立记录。前端加载、成功、失败及恢复使用真实接口，不使用 Mock 作为交付证据。

## 验证记录

实施进行中；以下区分代码落地与运行验收，未通过完整验收的需求仍为部分完成。

### 已落地链路

- 网页用例表格 `caseTable.vue` → `featureCase.ts` → `FunctionalCaseController` → `FunctionalCaseExportTaskService` → `export_task`。创建先走 HTTP，返回任务 ID 后轮询；请求不再等待 WebSocket 建连。状态查询、停止及下载校验任务所有者和实时项目权限。
- Excel/XMind 服务共用持久化任务、请求幂等键、容量准入锁、条件状态更新和有界线程池。工作线程以一致性读物化内容，生成文件不占用该事务；取消或超时后的迟到结果不能发布成功。下载使用流。
- 原生 MCP 的 `AgentCaseExportTools` 和 Node 的 `caseExport.ts` 提供创建、查询、停止、分块下载，调用相同业务服务；Node 本地测试通过不等于真实 MCP 链路验收通过。
- 迁移 `V3.7.2_104__case_export_recovery.sql` 扩展现有任务表并增加容量锁行；没有另建重复业务任务表。过期文件清理失败保留待重试状态。
- 旧 WebSocket 禁止客户端指定任意文件 ID 转发执行结果；新网页导出使用认证轮询。一次性订阅票据及多连接协议仍未交付。

### 交付文件索引

|层次|本次文件|
|---|---|
|后端任务|`backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseExportTaskService.java`、`FunctionalCaseFileService.java`、`FunctionalCaseXmindService.java`|
|接口契约|同模块 `controller/FunctionalCaseController.java`、`dto/ExportTaskDTO.java`、`request/FunctionalCaseExportRequest.java`|
|权限/通知|`backend/services/system-setting/src/main/java/io/metersphere/system/utils/SessionUtils.java`、`socket/ExportWebSocketHandler.java`|
|数据|`backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_104__case_export_recovery.sql`|
|网页|`frontend/src/api/modules/case-management/featureCase.ts`、`frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue`|
|MCP|`backend/services/agent-integration/src/main/java/io/metersphere/agent/tool/functional/AgentCaseExportTools.java`、`metersphere-mcp/src/tools/caseExport.ts`、`metersphere-mcp/src/index.ts` 中工具注册|
|测试|`FunctionalCaseExportMySqlTests.java`、`FunctionalCaseExportValidationTests.java`、`ExportWebSocketHandlerTests.java`、`metersphere-mcp/test/caseExport.test.mjs`、`frontend/e2e/case-export-recovery.spec.ts`|
|复现环境|`deploy/case-export-verify.compose.yml`、`deploy/quality-verify/case-export-fixture.sql`、`scripts/verify-case-export.ps1`、`scripts/case-export-browser-proxy.mjs`、`scripts/verify-case-export-stdio.mjs`|

工作区其他 Agent/缺陷/证据门禁及前端修改不归入本次导出交付，不能据此声称这些整改已完成。

### 固定边界与未完成项

默认每实例 2 个工作线程、16 个排队名额；数据库全局在途上限 18、每用户 2，超时 30 分钟，文件保留 24 小时。支持配置 `case.export.workers`、`queue-size`、`per-user-inflight`、`global-inflight`、`timeout-ms`、`retention-ms`。记录集合最多 100,000 条，序列化内容快照最多 8 MiB，生成文件最多 64 MiB；这些是保护阈值，尚不是容量验收结论。

快照时点为工作线程开始冻结数据时，尚未实现请求接收瞬间的版本快照。最近任务接口仅返回 20 条。旧浏览器依赖客户端文件 ID 的 WebSocket 通知与新服务端文件 ID 不兼容，升级需同步更新网页并刷新旧页签；不能宣称旧页面无感兼容。临时文件删除失败当前记录日志，尚缺独立重试扫描；对象存储清理已支持重试。双副本实际部署、完整字段往返/大文件、项目角色撤权、存量升级和回滚仍需证据；本轮已实测 Token 撤销后 MCP 拒绝下载，不能据此推导全部权限矩阵通过。

R01～R03/R03A、R05～R08，以及原 U/RA 中未映射到本轮导出的项目，仍按原计划逐项实施和验收。本轮不更改审核门禁，不将导出进展推导为整份方案完成。

### 实际验证（持续更新）

|命令|结果与证据|
|---|---|
|`pnpm.cmd -C frontend run type:check`|退出 0；前端类型检查通过|
|`$env:NODE_OPTIONS='--max-old-space-size=6144'; pnpm.cmd -C frontend run build`|完整 vue-tsc 后 Vite 输出 `built in 3m 31s`，生成 dist；`.codex-tmp/case-export-frontend-build2.log`。PowerShell 重定向将 stderr 警告包装为 NativeCommandError，外层会话退出 1，因此不记录为干净退出 0；存在既有 eval/大包警告|
|`pnpm.cmd -C frontend exec eslint src/api/modules/case-management/featureCase.ts src/views/case-management/caseManagementFeature/components/caseTable.vue`|0 错误、14 警告；`.codex-tmp/case-export-lint-final.log`|
|`npm.cmd --prefix metersphere-mcp test`|最终 22 通过，0 失败/跳过；`.codex-tmp/case-export-node-final2.log`|
|`.\mvnw.cmd -pl backend/app -am package '-Dtest=FunctionalCaseExport*Tests,ExportWebSocketHandlerTests,Agent*Tests' '-Dquality.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false'`|修复 MCP 停止工具幂等键后重新执行，退出 0、BUILD SUCCESS；89 个测试类、285 测试，0 失败/错误/跳过；`.codex-tmp/case-export-package-mcp.log`|
|其中导出与通知新增测试|真实 MySQL 7 项、输入/项目校验 3 项、WebSocket 2 项全部通过；真实 MySQL 在专用本机测试实例创建独立随机数据库，执行新迁移后测试并清理该测试库|
|`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-case-export.ps1 -Stage Image`|首次因 Alpine 软件包索引 TLS 中断失败，重试成功；MCP 修正后最终再次构建退出 0，`.codex-tmp/case-export-image-final.log`。最终镜像 manifest `sha256:5c84a4bef62a1d74c5249bcb001e87260cf9d948c0c072ef4a722f75b78cd297`；JAR SHA256 `f461e4c9679336e4df3a38963fba95971c7ec916c74bfa406e4905960725e46b`|
|`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-case-export.ps1 -Stage Start`|最终退出 0，独立 Compose 容器健康、readiness UP；`.codex-tmp/case-export-start-final.log`。此前 141 条空库迁移成功至 3.7.2.104；`.codex-tmp/case-export-migration-evidence.log`。最终重启查询 `metersphere_version` 确认 3.7.2.104 success=1，JAR 内首页与本次 dist 首页摘要相同|
|`npm.cmd pack ./metersphere-mcp --pack-destination .codex-tmp`、`npm.cmd install --prefix .codex-tmp/case-export-installed --no-audit --no-fund ./.codex-tmp/midoo-metersphere-mcp-0.7.0.tgz`|退出 0；`.codex-tmp/case-export-pack.log`、`.codex-tmp/case-export-install.log`。可用脚本 McpPackage 阶段复现|
|`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-case-export.ps1 -Stage Smoke`|最终退出 0，1 个综合场景通过（34.1 秒）；`.codex-tmp/case-export-e2e-final.log`。真实网页登录/选择/HTTP 创建且无 WS；两格式文件中文内容与数量；空集明确失败；成功后停止不覆盖；原生 MCP 幂等重放、状态、分块下载与停止；实际安装 npm 包 stdio 客户端下载字节与网页一致；撤销 Token 后 MCP 下载被拒绝|
|`git -c core.safecrlf=false diff --check -- <本轮已跟踪改动路径>`|退出 0，无空白错误|

早期默认堆内存的前端构建和一次并行后端打包因内存紧张被主动停止，不算通过；后续改为串行验证。测试依赖的服务授权部分使用 mock，单元/生命周期测试不代替真实撤权验收。此次 Maven 使用指定测试集，未执行未匹配的全部仓库测试。

首次浏览器测试因空库默认关闭管理员入口失败；只在独立验收库用 `case-export-fixture.sql` 开启。随后修正隐藏复选框与抽屉的测试定位，网页 Excel/XMind 下载、中文内容、数量、成功后停止与空任务测试通过（`.codex-tmp/case-export-e2e4.log`）。扩展原生 MCP 验收发现停止工具缺少统一写门禁要求的 `requestId`；已同时修复原生 schema 和 Node schema，并增加测试，不将修复前的 MCP 失败计为通过。

干净安装运行期另观察到 `AgentExecutionFinalizationService.recoverableTasks` 查询报 `utf8mb4_general_ci` 与 `utf8mb4_0900_ai_ci` 混用；健康检查仍为 UP，因此不能将 readiness 等同于全平台功能正常。本轮未修改该现有恢复查询，需作为后续数据库兼容性问题处理。

复现分阶段脚本：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-case-export.ps1 -Stage Frontend`，依次运行 Backend、McpPackage、Image、Start、Smoke 阶段。此参数仅作用于该 PowerShell 进程，不修改机器执行策略。Start 需要私有环境文件包含 `QUALITY_DB_PASSWORD`、`QUALITY_STORAGE_PASSWORD`、`QUALITY_FACTS_KEY`，不得提交秘密；Smoke 前运行 `node scripts/case-export-browser-proxy.mjs`，仅监听本机 15175。容器项目固定 `msp-export-20261021`，不替换已有验收/开发环境。

验收生成的文件保留于 `outputs/case-export-20261021/export.xlsx` 和 `export.xmind`。验收后为释放内存，仅停止本轮独立 Compose 容器，未删除数据卷；运行 Start 可恢复。未执行全仓库无筛选测试，可用 `.\mvnw.cmd -pl backend/app -am package` 在具备全部测试依赖的环境补跑；未执行全前端 lint，可用 `pnpm.cmd -C frontend exec eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts` 只读检查。双副本、容量、备份恢复及剩余 R/U 对应验收尚无本轮完整实现/脚本，需先按原计划补齐，不能用上述单实例 Smoke 代替。

**功能状态：部分完成。** R04/U13 的网页、原生 MCP、Node 导出主链路已落地并有真实运行证据；完整 R04/U13 及整份整改方案仍存在上述未完成实现与验收，不标记为完成。
