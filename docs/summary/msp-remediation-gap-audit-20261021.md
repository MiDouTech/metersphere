# 当前实现与整改方案缺口复核

日期：2026-10-21。源码基线：`19037ddb30a982cad64c9dd506c8a8eda5c279b1`。

依据：[完整整改方案](msp-remediation-complete-plan-20261020.md)（第 11 节优先）、[平台优化方案](../../doc/platform-optimization-implementation-plan-2026-10-21.md)、[原 AC01–AC28](../metersphere-mcp-case-library-improvement-plan-2026-10-20.md)。用户截图作为页面现象证据，不用于推断线上部署 commit。

结论：整体部分完成。上次完成的是代码提交与分支合并，不能作为整份方案完成或线上部署完成的证明。本次只进行源码、截图与既有实施记录复核；未修改业务实现，未执行线上交互或重新运行应用测试。

## 1. 平台优化需求逐项结果

路径除特别说明外相对仓库根目录。代码证据表示静态事实，不表示运行验收通过。

|需求|状态|当前实现与明确缺口|前后端、配置证据|
|---|---|---|---|
|R01 每页条数|未按方案实现|截图有“共 20 条”和页码，但无 10/20/50/100 选择器。已有分页与偏好基础设施，不等于本需求已交付；用户隔离偏好、四档值、选择清理及边界验收未闭环|`frontend/src/config/settings.json:17` 的 showPageSize=false；`components/pure/ms-table/useTable.ts:99` 读取全局设置；`ms-pagination/pagination.tsx:404` 条件渲染。真实查询仍走 `/functional/case/page`|
|R02 720 次/分钟|未按方案实现|总额度仍为 120；计数仍在进程内。720 阈值、共享 Redis 准入及双实例验证未交付|`AgentConstants.java:15`；`security/AgentTokenRateLimiter.java:17` 起的 ConcurrentHashMap|
|R03 14 天及多端隔离|未按方案实现|默认 Session 12 小时，前端按 30 天判断；SSO 凭证仍按 userId 保存/删除，未按 instanceId+sessionId 隔离。不能据此断言线上实际 TTL 或已复现互踢|`backend/app/src/main/resources/commons.properties:81`；`frontend/src/utils/auth.ts`；`system/sso/miduo/MiduoSsoSessionStore.java:37`；`SessionConfig.java:44` 固定 namespace 扫描|
|R03A 本机受信登录复用|未找到完整新链路|既有同源存储复用不等于设备信任；代理/扩展、签名 challenge、独立 Session 兑换、设备撤销及分发验收没有完整交付记录|当前 auth.ts 仍使用 sessionId/csrfToken；方案 6.5 的设备生命周期仍需专门实现及逐端验收|
|R04 用例导出|部分完成|HTTP 创建→任务查询→下载，网页/原生/Node 共用服务已有实现与历史验收。一次性 WS 票据、多连接协议、临时文件清理重试仍缺；完整字段往返、双副本及容量未验收；旧网页通知兼容有已披露限制|`caseTable.vue`→`featureCase.ts`→`FunctionalCaseController`→`FunctionalCaseExportTaskService`；迁移 104；详见导出实施记录|
|R05 批量按钮常驻|未按方案实现|当前仍需选中记录才出现操作区，未完成“有权限常驻、零选择禁用”。公共组件已有 alwaysShowSelectedCount 例外不等于所有页面已接入|`frontend/src/components/pure/ms-table/base-table.vue:669`：selectedCount>0 才 showBatchAction；273 行控制渲染。既有批量 API 不缺，但缺新的展示/禁用契约及逐页验证|
|R06 隐藏模型入口|未完整实现|截图仍显示“生成用例”；源码 caseGenerate 路由仅 adminOnly，没有该入口的部署默认关闭判断。管理员可见不符合“平台禁用模型入口”的目标|`frontend/src/router/routes/modules/caseManagement.ts:38`；生成页面及原模型服务仍需统一能力开关、直达路由/接口拒绝与 MCP 回归|
|R07 凭证失效提示|部分基础能力，目标未完成|有并发 401 处理保护，但仍直接静默登出；不是方案要求的默认单例确认弹框、分类恢复及集成凭证提示闭环|`frontend/src/api/http/checkStatus.ts:35` 起；调用 logout，默认按 message 提示。特定 errorMessageMode=modal 不等于统一到期流程|
|R08 自动刷新|部分完成|用例页面首次挂载、模块变化及部分操作会刷新；onActivated 只按路由 ID 打开详情，未刷新列表。持续停留外部变化通知/补偿刷新、平台逐页失效矩阵未闭环|`caseTable.vue:2616`、2630；父页 `caseManagementFeature/index.vue` 无对应激活刷新。不能说所有页面都不刷新，也不能说平台刷新已完成|

R01/R05 的源码与截图现象一致，不能把它们仅归因于浏览器缓存或生产未更新。R06 也存在明确的源码入口残留。

## 2. 原 U01–U16 复核

“已有实现/待本版本复验”不等于本轮重新验收通过。此前台账中的“缺失”也不能不看代码直接照抄。

|需求及验收|本轮判定|实现、缺口或证据边界|
|---|---|---|
|U01 / RA01 导航迁移|已有实现，待本版本全角色复验|execution-settings、旧路由及 retired/diagnostics 已有迁移处理；历史 navigation E2E 不能证明当前生产所有书签、搜索、页签及角色矩阵|
|U02 / RA02 个人配置|已有实现，待本版本复验|`ms-personal-drawer/index.vue:33` 实际内嵌 AgentIntegration，调用个人 Token API，不再只是跳转按钮|
|U03 / RA03 全局门禁迁移|已有实现，待本版本复验|`router/routes/modules/execution.ts` 实际挂载 quality-policy，使用 SYSTEM_QUALITY 权限；不能继续标“没有页面迁移”|
|U04 / RA04 平台配置|部分完成|`views/execution/platforms.vue` 仍嵌入 EnvironmentProfile、LoginProfile、CredentialReference 三个旧组件；统一平台/角色模型、迁移映射预览、expectedVersion、真实登录验证未形成方案要求的闭环|
|U05 / RA05 任务列表失败|部分处理，现场根因仍需核实|已有任务页错误及请求竞争处理；未获得用户目标部署的失败请求、traceId、部署摘要。导出验收记录另披露 recoverableTasks 的数据库排序规则冲突，不等同于已定位截图任务故障|
|U06 / RA06 Token/MCP|已有核心实现和历史验收，完整验收仍部分|个人 Token、原生 MCP、安装 Node 包调用已有证据；真实第三方 AI 客户端业务全过程和当前目标版本矩阵不能从接口测试推导|
|U07 / RA07 证据结构|按最新基线已有实现|DirectExecutionService 明确 STRUCTURE_ONLY_V1；保留结构、归属及版本约束。独立 OCR/图片语义审核不列本期缺口|
|U08 / RA08–09 结果保存|按最新基线已有实现|AGENT_REPORTED、额外/重复步骤保留、任一 ERROR 汇总 ERROR 已有代码及专项测试记录。v2 合同/issue/revision 后续决定，不列本期强制缺口|
|U09 / RA10 附件生命周期|部分完成|有上传、关联、下载及删除能力和归属校验；共享引用、失败补偿、过期/配额、跨用途全部矩阵未完整验收，不能说附件功能完全不存在|
|U10 / RA11 查询目录|部分完成|AgentFunctionalCaseSearchService 已真实分页、total、hasMore 和项目解析；目录改名/移动工具存在。查询接口未形成跨页固定快照协议，不能用单次查询或导出快照替代并发完整提取验收|
|U11 / RA12 评论|部分完成，明确接口缺口|Node caseLibrary.ts 与原生 FunctionalCommentsListToolHandler 仅 caseId，无分页输入；存在 create/update，未找到 functional.comment.delete 工具。删除墓碑、管理员权限、可靠通知去重仍需完整实现/验证|
|U12 / RA13 删除恢复|方案新增闭环未交付|原网页删除/回收站不能替代 MCP 预检确认、执行中取消/等待/停止后删除、确认失效、迟到提交拦截及逐项恢复。工具注册未发现对应完整删除恢复协议|
|U13 / RA14 异步导入导出|部分完成|本轮主要落地导出；缺陷导入工具不是功能用例导入。用例异步导入预检、逐逻辑用例事务、逐行结果恢复及往返验收仍未闭环|
|U14 / RA15 定时任务|既有引擎可复用，新流程交付不完整|不能把现有任务/调度接口或表单视为可安装适配器、离线阻塞、错过触发、重叠、时区和重启恢复均已验收|
|U15 / RA15 归档|未形成方案要求的完整交付证据|既有执行产物服务不等于业务理解/测试数据资产的来源、脱敏、去重、独立失败重试闭环；仍按待交付验收处理，不声称仓库完全无归档代码|
|U16 / RA16 客户端/部署|部分完成|有打包、隔离镜像、健康检查和特定 MCP 实测；缺当前同版本全功能/多客户端、存量升级回滚、备份恢复及目标部署摘要闭环。GitHub 合并不等于部署|

原 AC01–AC28 仍适用；其中 AC07/08/22/23 对应 U13，AC10/27 对应 U11，AC12/13/19–21 对应 U12，AC24 对应 U10，AC25/28 对应 U16，不能因导出专项通过而统一标通过。AC11 的个人直提判定以最新方案第 11 节为准。

## 3. 证据与文档问题

- 原完整方案第 2 节仍有 U01–U03“缺失”早期描述，代码已发生迁移；平台方案第 3 节仍将 R04 列为“未实施”，实际已部分实现。两个台账都需要按本次复核同步状态，不能将旧状态或提交数量当作实时完成率。
- `msp-remediation-implementation-20261020.md` 中 NEEDS_REVIEW、精确文本比较等是历史行为，已被新方案第 11 节及步骤校验调整记录覆盖；不是当前个人直提的实际口径。
- 已有 285 项后端测试和 22 项 Node 测试记录，只覆盖发现的指定测试集。没有 R01/R05 等要求对应的断言，测试通过不会自动暴露这些缺失的 UI 功能。
- 上次提交钩子通过类型和 lint 检查，只能支持可检查代码的静态质量，不能证明按钮、分页、登录及全平台交互符合方案。

## 4. 本轮验证与未执行项

实际执行：`git rev-parse HEAD` 确认基线；`git status --short --untracked-files=no` 在审计开始时为空；使用 `rg -n`、`rg --files`、UTF-8 `Get-Content` 搜索上述路由、组件、API、服务、配置和工具注册；读取用户截图；对新增文档执行差异空白检查。

本轮没有改业务代码，仅新增本审计文档。未重跑单元/集成测试、类型检查、lint、前后端构建、迁移、Docker、健康检查和 E2E：本轮任务为缺口审计，明确不存在的 UI/协议不能靠重跑旧测试验收，且没有当前目标部署版本证据。因此本报告不声称线上全功能已实测，也不把未验证项直接判成代码故障。

已有检查可复跑：

```powershell
.\mvnw.cmd -pl backend/app -am package '-Dtest=FunctionalCaseExport*Tests,ExportWebSocketHandlerTests,Agent*Tests' '-Dquality.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false'
npm.cmd --prefix metersphere-mcp test
pnpm.cmd -C frontend run type:check
pnpm.cmd -C frontend run build
pnpm.cmd -C frontend exec eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-case-export.ps1 -Stage Image
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-case-export.ps1 -Stage Start
node scripts/case-export-browser-proxy.mjs
# 代理保持运行，在另一终端执行：
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-case-export.ps1 -Stage Smoke
```

Start/Smoke 需要导出实施记录中约定的隔离依赖和私有配置，不连接生产。上述命令仍不能替代尚未编写的 R01/R05/R08、双副本、设备和全量 U/RA 测试；这些项目必须先补实现及测试，不能编造可执行验收命令。

建议实施顺序：先补 R01/R05（用例页后扩展逐页清单）和 R06 可见入口，再补 R07/R08；R02 与 R03/R03A 独立验证；原 U04、U09–U15 按完整生命周期逐项交付。保留 U07/U08 当前基线，不擅自扩大证据审核范围。

**状态：缺口复核记录已形成；功能整体部分完成。**
