# 缺陷创建修复与导入导出 MCP 实施方案

## 分项实施方案与验收追踪

|需求|调用入口与后端|数据及配置|验证|
|---|---|---|---|
|修复单条创建|原生 bug.template.get / bug.create → AgentBugWriteService → BugService|读取含内置字段的真实模板；新增 ID 由业务层生成；初始状态由已发布流程决定；处理人验证项目候选；复用原有表|真实创建、详情、必填/枚举/成员错误、幂等及事务回滚|
|错误协议|AgentSafeErrorMapper / AgentMcpStreamableService|稳定错误码、安全提示、服务日志 traceId|未知工具、参数错误、业务错误不冒充系统异常|
|导出|bug.export.columns / bug.export → AgentBugTransferService → BugService.export|复用已有 ZIP/XLSX；Token+用户导出权限；现有临时附件存储/24小时过期；限制最多100行、5MiB|列配置、实际 ZIP、下载字节、越权、大小与ID归属|
|导入|bug.import.preview / bug.import.commit → AgentBugTransferService → 同一创建服务|支持 XLSX base64 与结构化 rows；固定基础列和 custom:<fieldId>；最多100行/5MiB；禁止公式；previewHash 绑定文件及当前模板；逐行事务与持久幂等；IMPORT来源|有效文件、逐行校验报告、重复提交、模板变化、缺失字段、非法成员、公式/过量文件、失败不生成缺陷|
|客户端|原生 tools/list 和 Node MCP 工具转发；README|复用 BUG_READ/BUG_WRITE，导出额外检查网页 PROJECT_BUG_EXPORT 权限|schema、Node测试与构建|

1. 修复：先读取网页相同的注入模板，移除预分配 ID；本地创建允许状态空缺并由工作流赋初值，禁止请求任意指定初始状态。处理人及自定义枚举严格校验。单条创建使用现有 AgentOperationExecutor 保证业务与幂等同事务。
2. 导出接入：只接受显式 bugIds，逐条核对项目和读取权限，避免全选不受限；复用已有字段配置和生成；返回临时附件下载元数据及 sha256，不直接在 JSON 返回大型二进制。
3. 导入接入：读取 rows 或 XLSX（互斥），先逐行转换/校验；返回 previewHash 与行号/错误；commit 必须提交该 hash，重新校验当前模板和内容；每行独立事务并以请求+行号幂等，允许部分成功但明确给出失败行。复用操作执行器，重试不重复创建。
4. 验证：针对新增路径做单元/集成/权限检查，再 Maven、Node、前端生产构建、镜像构建、独立容器健康与端到端冒烟。保留其他未提交修改，不触碰测试计划映射及其他报告。

此功能的交互入口是 MCP 客户端，文件及缺陷结果可由现有网页查看；无需新增网页导入按钮。远端部署后才能认定测试环境已修复。

状态：部分完成。本地实现与验收已完成；测试环境尚未部署，等待发布入口。最终证据和边界见下文。


## 已实现的契约

- 创建：保留无 ID 的新增请求；本地模板包含处理人、状态及工作流版本，状态由服务端赋值；严格验证成员和枚举，公开稳定错误码；现有操作执行器让缺陷与幂等结果同事务提交。
- 导出：`metersphere.bug.export.columns` 和 `metersphere.bug.export`。显式选择1..100个缺陷；可选 `exportColumns: [{key,columnType}]`；默认 ID、标题、内容；复用 ZIP/XLSX 导出。使用 BUG_EXPORT 临时用途，下载重新验证当前用户导出权限、Token 项目及 scope，24小时过期；内容限5MiB，返回 sha256。
- 导入：`metersphere.bug.import.preview` 与 `metersphere.bug.import.commit`。XLSX 或 rows 二选一；稳定列头 title/description/handleUser/custom:<fieldId>；最多100条、5MiB；限制解压尺寸和条目数量，拒绝公式和重复表头；预检摘要包含模板和工作流版本；每行独立事务、以整份导入摘要和行号约束幂等，返回 CREATED/FAILED 及总状态。仅支持本地缺陷项目，不更新已有同名缺陷、不导入附件。
- MCP 是本功能用户入口；现有缺陷网页负责显示创建结果，现有 Agent HTTP 下载接口负责产物下载，不新增网页导入按钮。
- 不新增表或迁移；复用 agent_idempotency_record 与 agent_temp_attachment。Node 客户端同步新增4个工具，create_bug 的 requestId 改为必填。

## 当前已执行验证

- `.\mvnw.cmd -pl backend/app -am package -Dtest=Agent*Tests -Dquality.mysql=true -Dsurefire.failIfNoSpecifiedTests=false`：264项通过，零失败、零跳过，日志 `.codex-tmp/bug-verified-package.log`。
- `npm --prefix metersphere-mcp test`：构建及19项测试通过，日志 `.codex-tmp/bug-node-final.log`。
- `pnpm -C frontend exec eslint e2e/mcp-bug-transfer.spec.ts --fix`：零错误、零警告，日志 `.codex-tmp/bug-lint.log`。
- `pnpm -C frontend run build`：类型检查及生产构建通过；原有 eval / 大包告警保留，日志 `.codex-tmp/bug-frontend-build-retry.log`。第一次检查因内存压力主动停止，随后串行重跑通过。
- `git diff --check`：通过。
- 独立本地环境迁移记录：140条，失败0。本轮不修改任何远端配置。

## 本轮修改文件

后端：AgentAttachmentPurpose.java、AgentBugCreateRequest.java、AgentBugWriteService.java、AgentMcpStreamableService.java、AgentSafeErrorMapper.java、AgentTempAttachmentService.java；新增 AgentBugTransferService.java、tool/bug/AgentBugTransferTools.java。

测试：新增 AgentBugCreateContractTests.java、AgentBugTransferTests.java、frontend/e2e/mcp-bug-transfer.spec.ts；扩充 AgentSafeErrorMapperTests.java。

Node：src/client.ts、src/index.ts、src/tools/bugWrite.ts、README.md；新增 src/tools/bugTransfer.ts、test/bugTransfer.test.mjs。本文件记录方案、追踪和验收结果。此前工作区已有的其他改动予以保留，不计入本轮修改。


## 首轮容器验收发现及修复

实际 MCP 单条创建、详情查询、创建重放（搜索仅1条）、真实 XLSX 导入、导入重放（搜索仅1条）、预检摘要错误、部分成功逐行报告、实际 ZIP 下载及工作簿内容读取、缺陷网页列表可见性均通过。最后“撤销权限后下载”已被拒绝，但返回 HTTP400，而非 HTTP403，因此该轮端到端整体失败，未按通过计数。

原因：AgentExecutionExceptionHandler 只从异常 message 提取大写代码，丢弃 AgentErrorCode.SCOPE_DENIED；补充保留类型化错误码及403分类，并增加回归测试。补充模板读取的实时缺陷读取权限校验，及 XLSX 文件签名检查（拒绝旧二进制工作簿伪装成 xlsx）。新增修改文件 AgentExecutionExceptionHandler.java 和 AgentExecutionExceptionHandlerTests.java。将基于修复后的新镜像重新执行完整冒烟。


## 最终构建补充

最新 Maven 命令为 `.\mvnw.cmd -pl backend/app -am package -Dtest=Agent*Tests -Dquality.mysql=true -Dsurefire.failIfNoSpecifiedTests=false`，日志 `.codex-tmp/bug-acceptance-package.log`。跨模块合计273项通过（本模块266项、依赖模块7项），零失败、零错误、零跳过；此前264为较早轮次的本模块数量。

标准镜像首次构建成功。加入权限状态码修复后，标准构建两次遇到 Alpine CDN TLS EOF；未关闭证书校验、未使用 HTTP 镜像站。最终复用首次成功镜像的相同系统运行时，将最后一次打包解出的 BOOT-INF/lib、META-INF、BOOT-INF/classes 全量覆盖构建为独立候选镜像，无新系统依赖，也不包含运行时秘密环境变量。离线 Dockerfile 及日志保留在证据包中；标准构建在完全无缓存、需要访问 CDN 的机器上仍依赖该网络恢复。

离线构建命令：`docker build --network none --pull=false -f backend/app/target/remediation-image-27eeeabd49d52af4/Dockerfile.offline -t msp-quality:bug-transfer-candidate-20261021 backend/app/target/remediation-image-27eeeabd49d52af4`。基础标签 msp-quality:bug-runtime-base-20261021 为本轮已成功构建的镜像快照。发布时使用最终候选镜像自带的完整层，无需目标机器存在该本地基础标签。

Node 客户端已通过 `npm pack ./metersphere-mcp --pack-destination .codex-tmp/bug-client-package` 打包，检查包含 dist/tools/bugTransfer.js；未发布 npm registry、未修改远端服务。


## 最终验收结果

- 最终镜像 `msp-quality:bug-transfer-20261021`：`sha256:454c6449508ad7b3dcc2cd4dcef77e837ec43dfbb77ac548c3307d1752e3292c`。
- 最终应用 JAR SHA256：`27eeeabd49d52af4aa34399879a4dae5d0ee26581831a64aae6127f2eeb7723d`。
- 已从运行容器复制 MCP 业务 JAR 与打包内容比较，SHA256 一致：`6c4f313fb5d796a6952b0556d60a6af05bf669c36f829b833071bedaa7f558a2`。
- 容器 readiness=UP，health=healthy，restart=0，OOM=false。
- 最终实际命令：设置 `$env:PW_BASE_URL='http://127.0.0.1:15174'`、`$env:MCP_BUG_E2E='true'` 后执行 `pnpm -C frontend exec playwright test e2e/mcp-bug-transfer.spec.ts --workers=1`：1 passed，59.3秒。
- 端到端通过：未知工具稳定错误码、真实缺陷创建/详情、重放后查询仅1条、相同键参数变化拒绝、非法处理人拒绝、真实XLSX预检/提交/重放、摘要不匹配拒绝、部分成功和失败行明细、导出字段清单/非法字段拒绝、按列导出2条缺陷、实际ZIP下载及sha256一致、读取其中工作簿确认标题及内容、网页缺陷列表可见、撤销Token读取权限后HTTP403且业务码SCOPE_DENIED。
- 页面验收等待真实 `/api/bug/page` 响应，再校验标题可见；最终截图在证据包中。默认首页跳转曾超时；正常网页登录生成会话后，显式加载带项目参数的完整URL作为验收入口。本轮未修改默认首页跳转逻辑，不将该路径视为已修复。
- 测试缺陷通过正常页面API删除，临时Token撤销；独立环境的管理员入口恢复false，验收容器与代理停止，镜像及数据卷保留以便复核。

## 未执行项、风险与复核命令

- 尚未部署到 msp.ebcone.net，也未在该站点执行修复后回归。本会话没有明确的测试发布任务或部署入口；仓库只有通用发布说明，已向用户询问具体入口。风险：远端仍可能运行旧实现，当前不能宣称测试环境缺陷已解决。发布后需重新获取 tools/list，按README的模板/创建/预检/提交/导出契约执行同等真实验证。
- 标准Dockerfile最终版本在访问Alpine CDN时失败两次；最终使用已验证运行时基础离线封装并验收。无缓存环境重新运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-msp-remediation.ps1 -Stage Image` 仍需要CDN网络可用；本轮未改变HTTPS校验或源地址。
- 未进行100条最大批量的性能/压力测试，也未验证第三方缺陷平台导入。前者只做边界单元校验；后者明确在提交前拒绝。导入不处理附件、不更新同名既有缺陷，导出文件需按稳定导入列头整理再回导。
- 既有后台回写任务仍出现 ai_execution_task 与 ai_execution_case_sync 的排序规则不一致异常，本轮不修改该独立问题。它不阻止上述MCP链路验收，但整个平台不能据此声称全部正常。
- 本轮不新增迁移。独立数据库140条迁移均成功；未将本地容器启动当作远端部署证据。

最终状态：创建修复、导入和导出本地实现已验收；整体部分完成，剩余测试环境发布及在线回归。源代码未提交或发布到远端。所有本轮新增文件和真实命令、响应、截图、导出文件及哈希均随交付包保存。
