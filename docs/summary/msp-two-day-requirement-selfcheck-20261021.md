# 10 月 20–21 日方案与实现逐项自检

审计日期：2026-10-21。代码：`19037ddb30a982cad64c9dd506c8a8eda5c279b1`。纳入工作区尚未提交的完整方案“实施与自测强制标准”。本轮只列问题，不修改业务代码。

## 1. 提交及方案范围

|提交|时间|与本轮有关的内容|
|---|---|---|
|f1bd305c6a|10-20 13:33|用例库主方案、个人 Token/多 Agent 接入方案（文件名为 07-30，但第 19 节在本次提交中修订）、个人闭环及联合实施记录|
|2b67852f73|10-20 15:51|完整整改方案、用例库方案修订、导航与个人执行实施记录|
|19037ddb30|10-21 14:04|平台优化 R/E 方案、导出根因报告、范围盘点、个人直提基线修订、缺陷 MCP 方案及一批历史研究/产品文档|

不能按文件名日期或提交标题中的 complete 判断完成度。以下按实际内容分层：

1. **当前主验收依据**：`msp-remediation-complete-plan-20261020.md` 的 U/RA、`metersphere-mcp-case-library-improvement-plan-2026-10-20.md` 的 AC01–AC28、`platform-optimization-implementation-plan-2026-10-21.md` 的 R/E。
2. **本次补查的独立方案**：个人 Token/多 Agent 接入方案第 19 节、`mcp-bug-delivery-20261021.md`、`完整预检内部错误分析与恢复方案-20261020.md`。不能因为未全部展开在 U/R 表就遗漏。
3. **修订优先级**：用户最新确认及完整方案第 11 节优先。个人直提按 STRUCTURE_ONLY_V1 / AGENT_REPORTED，不将 OCR、v2 稳定步骤、审核 revision 等后续决定项计为本期缺陷。受管执行原约束仍有效。
4. **历史证据**：MCP 用例库实施记录、个人核心交付、重新审计、联合实施记录、导出实施记录、系统取证及生产审计 JSON，用于判断实际实现与运行证据，不能反向削减需求。
5. **研究/建议文档**：三份 ai-quality/ai-testing-quality 文档、产品范围讨论稿、板块处置/冗余审查、visual-ui-testing 技能升级建议属于背景或候选方案；已被主方案吸收的内容纳入下表，未确认的产品重构/外部技能升级不自动成为 MSP 必须新增功能。业务理解文档、测试模板/示例和操作指南不是应用交付完成证据。

状态定义：**未实现**=明确要求缺少对应链路；**部分**=有基础但不满足完整契约；**代码缺陷**=可定位的代码行为违背要求；**待复现**=有风险路径但未证实实际触发；**缺验收**=代码存在，证据不足。下表是独立整改检查项，不是互不重叠的需求数量。

## 2. 页面、平台、会话和导出

|编号|需求|状态及缺口|代码/验收依据|
|---|---|---|---|
|L01|R01 条数选择|未实现：用例列表未显示 10/20/50/100 选择器|`frontend/src/config/settings.json:17` showPageSize=false；`ms-table/useTable.ts:99` 读取全局配置|
|L02|R01 条数变更原子重置|代码缺陷：pageSizeChange 只改 pageSize/偏好并 loadList；没有在该路径 current=1、清空选中/排除/全选。不能仅打开选择器就算修完|`frontend/src/components/pure/ms-table/useTable.ts:507`；`ms-pagination/pagination.tsx:252` 也只发条数事件。需以高页码及旧选择回归|
|L03|R01 用户偏好与边界|部分/缺验收：已有 tableKey 缓存，但未证明四档校验、用户隔离、空/单页/窄屏与总数减少回退都符合方案|`ms-table/useTable.ts:122`；R01 整套 UI 验收未交付|
|L04|R05 批量按钮常驻|未实现：selectedCount>0 才显示；零选禁用和逐页权限矩阵未落地|`ms-table/base-table.vue:669`、273；截图与源码一致|
|L05|R06 隐藏模型入口|未完整实现：“生成用例”仍是 adminOnly 可见路由，未统一按部署能力关闭|`router/routes/modules/caseManagement.ts:38`；还需生成/对话/执行入口及直接接口统一开关检查|
|L06|R07 失效提示恢复|部分：并发保护存在，业务 401 仍直接静默登出；统一确认、返回地址、集成凭证分类与 Blob 失败未完整验收|`api/http/checkStatus.ts:35` 起；默认 message，不是统一确认弹框|
|L07|R08 激活与外部写入刷新|部分：mounted/部分操作会加载；onActivated 只处理详情 ID，不刷新列表；持续停留补偿及逐页竞态清单未闭环|`caseTable.vue:2616/2630`；父页有手动 refresh，不等于自动激活刷新|
|L08|R02 总额度|未实现：仍 120/min，不是 720/min|`AgentConstants.java:15`|
|L09|R02 分布式准入|未实现：计数在进程 ConcurrentHashMap；Redis 准入/故障策略、共享额度未交付|`security/AgentTokenRateLimiter.java:17`；E12 未完成|
|L10|R02 边界及客户端退避|缺验收：721 次拒绝、跨窗口、跨 IP、子额度/batch、Retry-After 与安全重试矩阵缺失|现有 Node 22 项不覆盖这些容量及多实例行为|
|L11|R03 14 天有效期|未实现：后端默认 43200s，前端按 30 天；14 天服务端绝对到期未闭环|`backend/app/src/main/resources/commons.properties:81`、`frontend/src/utils/auth.ts`；不等同线上 TTL 已确认|
|L12|R03 多端/实例隔离|代码设计缺口：SSO 凭证按 userId 单键读写删除，未会话隔离；Session 清理硬编码 namespace|`MiduoSsoSessionStore.java:37/106`；`SessionConfig.java:44`。实际互踢仍需双端复现|
|L13|R03A A 自动化状态复用|部分/缺验收：同源存储可复用，但没有完整的账号/环境绑定、启动有效性检查及独立自动化验收|不能用新标签页复用替代隔离 context/跨账号测试|
|L14|R03A B 设备授权|未找到完整实现：设备注册/签名 challenge/一次性兑换独立 Session、代理扩展、撤销轮换及安装生命周期|与 MCP Token 通道独立，不新增个人直提前置条件|
|L15|R04/U13 导出主体|部分：HTTP 创建、持久化任务、查询/停止/下载、两格式及原生/Node 已实现；不是“导出全部没做”|`FunctionalCaseExportTaskService`、`FunctionalCaseFileService`、`FunctionalCaseXmindService`、AgentCaseExportTools、caseExport.ts、迁移 104；有历史实测|
|L16|R04 通知/旧网页兼容|代码兼容缺口：新服务端分配 fileId，旧页订阅客户端 fileId；新轮询正常不代表旧页兼容。短票据、Origin 绑定、原子消费和多连接协议未实现|任务服务 start 清空客户端 fileId；历史导出记录已披露；E05/E07|
|L17|R04 快照时点与恢复列表|部分：worker 开始冻结而非请求瞬间；recent 固定 LIMIT 20，未提供完整任务翻页。重试来源关联待补|任务服务 readSnapshot/saveSnapshot/recent。冻结时点必须在产品契约中准确说明|
|L18|R04 清理补偿|未实现完整链路：Excel/XMind 本机临时文件删除失败只写日志，无独立重试扫描；对象存储重试已有|`FunctionalCaseXmindService.java:143`、FileService 对应 finally；不混淆两类清理|
|L19|R04/E01–08 全量验收|缺验收：角色撤权、双副本取消/租约、存储故障、字段往返、大文件及兼容矩阵未全覆盖|不能用一次综合导出 E2E 及 Token 撤销覆盖全部场景|

## 3. 原整改闭环及新发现的实现问题

|编号|需求|状态及缺口|代码/验收依据|
|---|---|---|---|
|L20|U01 导航隐藏|已有实现/缺当前全矩阵验收：旧路由桥接、retired/diagnostics 已做；搜索、缓存页签、书签、无项目系统管理员需本版本全量核对|execution-settings、execution/setting/agent 路由；历史 navigation 测试不等于线上验收|
|L21|U02/U03 个人配置/门禁|已有实现/缺当前全矩阵验收：抽屉内嵌 AgentIntegration，执行中心实际挂载门禁；不能沿用旧台账“缺失”|`ms-personal-drawer/index.vue:33`、`routes/modules/execution.ts`；纯系统管理员、撤销后调用等需统一版本证据|
|L22|U04 统一平台配置|部分：仍三个旧组件页签；统一角色/登录验证状态、配置迁移预览、旧 ID 映射和引用保护未完整落地|`views/execution/platforms.vue`→EnvironmentProfile/LoginProfile/CredentialReference→原服务|
|L23|U04/预检恢复 环境引用|代码缺陷：创建可生成没有对应资产的 environmentId；受管预检仍将它当 ENVIRONMENT 资产调用 resolveContext。新配置可能保存成功后预检无法通过|`AgentEnvironmentProfileService.java:70`、`AgentExecutionPreflightService.java:167`。个人直提无需资产不等于受管链路允许悬空引用|
|L24|预检失败事务处理|待复现：预检事务捕获参与事务的 TestAssetCatalogService 抛出的 MSException 后继续保存，仍有 rollback-only 导致 500 的风险；未取得本轮运行堆栈|preflight @Transactional、ADDITIONAL_ASSETS catch；catalog 类 @Transactional(readOnly=true)。不把推理写成已确认线上异常|
|L25|U04 平台版本冲突|部分：已有 version 条件更新，但 request.version 缺省时使用服务端当前值，未强制客户端版本，不能保证防止过期页面覆盖|`AgentEnvironmentProfileService.java:106`；需缺版本拒绝与并发更新测试|
|L26|U04 登录验证|部分：verify 检查域名/DNS/TLS/TCP/Runner，不等于真实登录；FORMAT_VALID/CREDENTIAL_AVAILABLE/LOGIN_VERIFIED/NEEDS_HUMAN 分层闭环不足|`AgentEnvironmentProfileService.verify`。不能把 reachable/valid 描述为已登录|
|L27|U05 任务列表故障|待现场复现：页面已有错误和请求竞争处理，但目标部署实际失败 URL/code/traceId/制品未完整采集|任务页面→searchAiExecutionTasks→任务搜索接口；不能凭截图认定根因|
|L28|U05/U14 恢复查询排序规则|历史运行缺陷待复验：导出实施记录披露 recoverableTasks collation 混用；未见该问题修复的专项证据|`platform-optimization-implementation-20261021.md` 运行边界；readiness UP 不能排除后台恢复失败|
|L29|U09 普通附件过期清理|代码缺口：cleanupExpired 仅删除 agent_temp_attachment 行，不调用实际文件删除及失败重试。上传保存到系统临时目录，元数据删除不能证明字节回收|`AgentTempAttachmentService.java:205` 与 `CommonFileService.java:45`。本轮未统计实际残留文件；仍需核对存储生命周期策略|
|L30|U09 附件完整生命周期|部分/缺验收：已有上传、归属及下载校验；prepare/complete 契约、共享引用保护、关联失败补偿、配额和清理矩阵未全交付|暂存/正式附件服务与 AC05/06/26；不是完全无附件能力|
|L31|U10 跨页读取快照|未实现完整契约：真实分页/total 存在，但各页独立查询，没有可续读固定快照协议|AgentFunctionalCaseSearchService、AgentCaseSearchRequest；导出快照不能代替 search snapshot|
|L32|U10 目录并发统一版本|代码缺口：MCP 要 expectedUpdateTime，网页 ModuleUpdateRequest 只有 id/name，编辑请求也无客户端版本；树锁不能识别过期页面意图|FunctionalCaseModuleUpdateRequest、FunctionalCaseModuleEditRequest；AC18/24|
|L33|U11 评论分页/删除|未实现：原生 comments.list 只有 caseId，Node 同步无分页；未找到 comment.delete 工具|FunctionalCommentsListToolHandler、metersphere-mcp/src/tools/caseLibrary.ts|
|L34|U11 墓碑、管理员与通知|部分/缺验收：本人编辑已有；删除父评论保留回复、作者/管理员完整权限、通知 outbox/重试去重未闭环|AC10/27、RA12；不以 create/update 通过代表生命周期完成|
|L35|U12 删除确认|未实现方案新增链路：delete_preview、用户确认页、取消/等待/停止后删除及确认过期/摘要绑定|现有网页批量删除不是 MCP 用户决定协议；AC19–21|
|L36|U12 删除恢复与迟到提交|未实现完整闭环：CURRENT/ALL、逐项结果、目录不存在恢复选择、预检后新运行、恢复不复活任务|trash/restore 等 MCP 协议未完整交付；个人直提取消影响必须按当前无 v2 合同的实际模型设计|
|L37|U13 功能用例导入|未实现完整异步协议：precheck/commit/get/stop/retry、模板/覆盖目标冻结、逐逻辑用例事务与重启恢复|缺陷 bug.import 不是 functional.import；AC07/08/22/23|
|L38|U14 调度适配器|部分：旧引擎/触发器存在；可安装适配器、在线领取、离线阻塞、错过触发、重叠、时区与重启恢复未完整交付验收|RA15；不能把任务表单/接口当作真实自动运行|
|L39|U15 业务理解/数据归档|部分/缺验收：存在产物及测试数据基础设施，但来源、事实/推断、脱敏、运行+类型去重、独立重试到项目资产完整链路未证明|RA15；不宣称仓库没有任何资产或清理代码|

## 4. 昨日个人 Token 方案中此前漏列的项目

此组依据个人 Token 方案第 19 节；不以早期标题替代当前主方案，冲突处按最新确认。已有 tokenVersion=2 和 BCrypt，不能误写成没有 v2 凭据或没有哈希存储。

|编号|需求|状态及缺口|证据|
|---|---|---|---|
|L40|凭据轮换|未实现：个人控制器无 /rotate；无 expectedRevision、代次递增、旧秘密立即失效与丢响应处理闭环|PersonalAgentTokenController；现有 webhook rotate-secret 不是个人 Token 轮换|
|L41|授权版本/项目范围|部分：有 tokenVersion、projectIds 与显式危险 scope 保护；缺 authorizationContractVersion、credentialGeneration、projectScopeMode/revision 的完整持久化/迁移契约|AgentTokenManagementService、AgentTokenScopeParser、迁移检索；不能泛称所有 AGENT_ALL 都已自动扩权|
|L42|旧 Token 截止迁移|缺完整实现/验收：30 天默认/60 天审计延期、明确旧格式截止、不安全归类处理和回退边界未交付|tokenVersion=2 新签发不能替代历史数据迁移|
|L43|管理员治理|部分：有列表、撤销、审计 CSV；仍复用 SYSTEM_USER_READ/UPDATE，未完整交付独立治理权限、持久化签发/调用策略及治理页面闭环|AdminAgentTokenController 明确 Partial governance M1；不是完全没有治理接口|
|L44|签发与运行配额|未完整实现：创建直接 insert，未见方案的并发签发配额锁/限制；多 Token 的用户/项目/全局并发约束未形成统一能力|AgentTokenManagementService.create；Token 级限流不能代替多维配额|
|L45|轮换后任务恢复|未实现完整契约：旧 generation 的 lease/上传凭据失效、暂停原因和新尝试恢复依赖 L40/L41|已有 lease/attempt 保护不能代替凭据代次隔离|
|L46|统计及可靠审计|部分/缺验收：有 invocationCount/执行日志；认证次数/调用/成功分开计量、有界异步统计、可靠审计保留及故障矩阵未完整证明|需按第 19.6 节逐项核对，不能用日志存在视为全部符合|
|L47|OAuth 接入|未找到完整 Agent MCP OAuth 授权服务器链路：发现/Code+PKCE/同意/刷新轮换/撤销/resource 等仍缺|模型供应商 ai_oauth_connection 不等于 MCP OAuth；ChatGPT 保持待支持，不以 Bearer/stdio 验收替代|
|L48|客户端/技能契约|部分/缺验收：原生、Node、技能包均有实现；真实下载技能包及各第三方客户端的版本/读写/文件/恢复矩阵未全覆盖|U16、AC25；schema 单测、只读 tools/list 不等于实际业务全过程|

## 5. 缺陷 MCP、工程交付与文档

|编号|需求|状态及缺口|依据|
|---|---|---|---|
|L49|缺陷创建/导入导出|已有本地实现和历史实测；目标部署与线上回归缺证据|mcp-bug-delivery 方案明确 100 行/5MiB、固定列、不导附件；这些既定边界不当作遗漏。源代码已提交，文中“未提交”属过时状态|
|L50|全量需求自测|不完整：已有测试集未覆盖全部需求；本轮 Node 22 通过也不覆盖页面、OAuth、设备及多实例|本轮实际执行 npm --prefix metersphere-mcp test；22/22，退出 0，无跳过|
|L51|同版本交付验证|不完整：个人、缺陷、导出 E2E 分属阶段制品，不能合并宣称最终同一版本全量验收通过|RA16/E14；正式镜像、干净迁移已有部分证据，存量升级、备份恢复、回滚、双副本和生产摘要仍缺|
|L52|方案/实施台账同步|文档缺陷：U01–03 仍写旧“缺失”、R04 仍写“未实施”、旧审核行为与未提交描述滞后；无统一当前状态|本轮清单明确区分历史事实/现代码/未来目标；不修改原历史证据冒充新验收|

## 6. 全部原验收编号的追踪（防止漏出自检范围）

下表“待复验”表示不得标已通过；不是已定位故障。

|原验收|本轮对应|判定|
|---|---|---|
|AC01/02/03/04|U10，L31|已有项目解析、查询及分页；组合/跨项目/多页完整矩阵待复验，快照缺口保留|
|AC05/06|L29/30|附件基础已做，完整生命周期/异常边界部分|
|AC07/08|L37|用例导入往返及逐行恢复未闭环|
|AC09|L32|目录能力存在，全写入口版本不完整|
|AC10|L33/34|编辑已有，删除与完整并发未完成|
|AC11|U07/U08，第 11 节|个人直提按声明结果和结构校验；受管任务原规则保留，不能混用审核结论|
|AC12/13|L35/36|删除恢复完整协议缺失|
|AC14|L20、R06|测试用例旧执行入口与平台模型隐藏是不同要求，须分别验收|
|AC15|L06/24/27|安全错误已有基础，预检/分类提示和全场景待补|
|AC16/17|L41/45/48|有权限、幂等测试；异步/代次/通知完整矩阵不足|
|AC18|L25/32/35|版本及预检变化协调不完整|
|AC19/20/21|L35/36|执行中用户决定与恢复未闭环|
|AC22|L37|功能用例多表多行、解析限制/事务验收未完成|
|AC23|L15–19/37|导出部分、导入及双副本恢复不足|
|AC24|L31/32|稳定跨页快照与全版本入口未完整|
|AC25|L48/51|安装包部分实测，技能包/真实客户端全链路不足|
|AC26|L29/30|共享引用与清理补偿不足|
|AC27|L33/34|墓碑/管理员/通知未闭环|
|AC28|L42/51|全写开关、旧客户端及回退缺完整验收|
|RA01/02/03|L20/21|已有核心实现，当前全角色全入口待复验|
|RA04/05|L22–28|配置及故障闭环不完整|
|RA06|L48|原生/Node 有历史实测，真实第三方业务链路待验收|
|RA07/08/09|U07/U08|按现结构基线已有专项测试；不扩大为语义可信。当前全授权/事务矩阵仍需按需求复核|
|RA10/11/12/13/14|L29–37、L15–19|对应生命周期缺口保留|
|RA15|L38/39|真实调度及归档未全验收|
|RA16|L48/51|同版本全量交付未完成|
|E01/02/03/04|L15/19|导出专项覆盖部分，双副本及全权限/竞争矩阵缺证据|
|E05/06/07/08|L16–19|通知/兼容/容量/恢复仍有缺口|
|E09/10|L11–14|会话及设备方案未交付完整链路|
|E11|L05、U07/U08|关闭模型入口仍缺；个人直提不适用旧“核验器故障阻止发布”要求，受管任务另验|
|E12/13/14|L08–10/L04–07/L51|分布式限流、逐页交互及工程验收不完整|

## 7. 本轮检查、限制与文件

- 实际执行 Git 日期范围日志和文档变更清单，确认三个提交；读取方案及相关实现，并沿组件→API/服务→数据/配置检查上述重点路径。
- 实际运行 `npm.cmd --prefix metersphere-mcp test`：TypeScript 编译和 22 项测试通过，失败/跳过均 0。此结果只说明现有 Node 用例通过，不是本报告全部需求通过。
- 新增本文件；保留先前工作区方案修订及审计记录。未修改业务代码、未提交或推送。
- 未在本轮重跑后端集成、前端构建、数据库迁移、容器启动或完整 UI/E2E：本轮目标是先列全差距；完整缺失能力尚无可执行验收，现有旧用例不能替代。没有目标部署摘要与完整账号/角色/多实例运行证据，线上表现仍待核实。
- 对既有代码可复跑：`.\mvnw.cmd -pl backend/app -am package '-Dtest=FunctionalCaseExport*Tests,ExportWebSocketHandlerTests,Agent*Tests' '-Dquality.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false'`；`pnpm.cmd -C frontend run type:check`；`pnpm.cmd -C frontend run build`；`pnpm.cmd -C frontend exec eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts`。这些不是缺失需求的新验收脚本。
- 导出专项复跑用 `scripts/verify-case-export.ps1` 的 Image/Start/Smoke 和 `node scripts/case-export-browser-proxy.mjs`，隔离依赖、私有配置与精确说明见 [导出实施记录](platform-optimization-implementation-20261021.md)。新增需求、预检事务、并发修改及多副本必须先补对应测试，禁止空匹配记通过。

总体：**部分完成**。本清单完成文档/源码层需求自检和一次 Node 回归，不宣称已完成所有运行验证。代码缺陷与待复现风险已分别列明；后续按每一行需求验收关闭，不按修改文件或测试数量关闭。
