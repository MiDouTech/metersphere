# 补充方案实施记录（持续更新）

依据：`msp-remediation-complete-plan-20261020.md` U01–U16。整体状态：部分实现，尚未满足完整方案验收；本文件不把后续工作写成已完成。

## 当前实现与追踪

|需求|实现文件/对应链路|数据影响|验收状态|
|---|---|---|---|
|U01 隐藏/旧链接|execution-settings.ts 改为兼容路由；旧高级页停用；按需 diagnostics.vue|不删历史数据|管理员导航/旧链接实测通过；全部角色及搜索/缓存组合仍需补齐|
|U02 个人配置|ms-personal-drawer 内嵌 AgentIntegration；旧 URL 打开同一抽屉；一次性 Token 弹窗实际 MCP tools/list 验证|复用个人 Token API|UI 创建、范围选择、接入检查和撤销通过；安装版客户端实测通过；第三方 AI 业务执行尚未验收|
|U03 全局门禁|SystemQualityPolicy 路由名/资源码保留，页面迁入 execution；父级系统权限聚合；旧 URL 单向跳转|复用全局策略和权限|9 项门禁权限及策略流程通过，包含纯系统管理员、无项目绑定、撤权和发布|
|U04 平台配置|platforms.vue 承接平台/登录/凭据；服务端生成新环境关联 ID，更新保留原 ID|不复制/重建旧档案及秘密|仅入口整合和 ID 简化；角色统一表单/真实登录验证等仍不完整|
|U05 任务列表|同页多个 watcher 的并发请求隔离，序号防止旧请求覆盖新结果；取消机制对该只读搜索关闭|无数据库变更|原页面失败但 HTTP/业务返回成功；修复后真实后端列表冒烟通过|
|U06 接入链路|Token 原授权保持；关闭 Agent Filter 的重复 Servlet 注册，保留 Shiro 鉴权及限流；增加界面配置验证|未扩权；修复安装客户端首次检索被 429 误拦|原生 MCP、安装版 Node 初始化/工具发现/用例检索/原生工具转发及撤销实测通过；尚非真实 AI 业务执行验收|
|U07 内容核验|EvidenceContentReviewer：受管理员部署配置控制的独立服务协议，拒绝未配置/无效/不完整响应；2×2 图片不再接受|迁移 103 增审核状态及提交证据引用|定向及全量 Agent 回归通过，包含真实 MySQL；外部核验器测试使用协议夹具，不是视觉准确率证明；实际引擎尚未实现/接入|
|U08 结果审计|NEEDS_REVIEW/NOT_PUBLISHED 不写附件/结果；审核通过仍原子发布；重放不重复调用核验器；历史标 LEGACY_STRUCTURE_ONLY|旧结果保留，不升级成已做内容审核|合同快照 v2、语义/类型断言、结构拒绝历史及完整补交仍待实现|
|U09–U13|附件下载补 Token/用户/用途/过期授权；其余完整生命周期、查询快照、评论删除、删除确认/恢复、异步导入导出仍缺|未改变原批量业务数据；兼容上传增加所属暂存记录|部分实现，未完成|
|U14–U15|旧调度规则迁到任务页内；完整适配器与自动归档仍待实现|保留原引擎|未完成|
|U16|Node 0.7.0、技能包 1.4.0；明确只有 ACCEPTED/PUBLISHED 才正式发布|客户端契约文案升级|构建、正式镜像、隔离升级/健康及 14 项浏览器回归通过；干净安装/备份恢复、完整下载技能包实测、全部角色组合及第三方业务验收仍待完成|

## 独立核验服务部署契约

待用户明确选择内网核验服务或已经授权的视觉服务；默认不向任何服务发送截图。以下是已实现的协议适配器配置，不是内置视觉模型，也不是声称已部署可用的审核服务。

```yaml
agent:
  evidence-review:
    endpoint: ${MSP_EVIDENCE_REVIEW_ENDPOINT:}
    token: ${MSP_EVIDENCE_REVIEW_TOKEN:}
```

可通过 Spring 对应配置注入；上面 YAML 为部署需显式加入的配置示例，不能只设置 MSP_* 环境变量却未绑定到应用属性。支持 HTTPS；HTTP 只允许 loopback 本地服务，不跟随重定向。地址/秘密不来自 MCP 参数，不在状态接口回显。`GET /system/quality/evidence-review/status` 仅系统策略读取权限可用。

请求 POST JSON，Authorization Bearer 为服务端配置；`X-Evidence-Request-Hash` 为完整请求字节的 SHA256。请求包含 protocolVersion、contentHash、steps；每步含 stepNum、description、expected、actual、evidenceHashes、有界图片证据及用途。核验服务必须独立读取图像/观察证据判断对应关系，严禁照抄请求直接响应 SUPPORTED。

响应 JSON 示例结构（占位值仅说明协议，不是测试证据）：

```json
{
  "requestHash": "<收到的请求字节摘要>",
  "reviewerVersion": "<核验器固定版本>",
  "steps": [{
    "stepNum": 1,
    "status": "SUPPORTED",
    "observedActual": "<从独立证据核实的实际文字>",
    "evidenceHashes": ["<逐项核验的文件摘要>"]
  }]
}
```

响应必须覆盖所有步骤和文件摘要；不支持/无法核实返回 INSUFFICIENT 或 CONTRADICTED。平台拒绝部分响应、错摘要、错实际值、超大响应和未知状态；服务不可用保留 NEEDS_REVIEW，不发布正式结果。请求超时 30 秒、响应 16 KiB；图片/总容量沿用现有上限。此协议仍需要受信核验器，不能证明任何服务提供者都不会误判。

当前断言仍使用精确文本 EQUALS，尚未完成方案中的自然语言/类型断言迁移。不得向用户声称措辞差异已经自动正确处理。MIN 64×64 只是明显无效图过滤，不是语义审核。

## 状态及保留

- 新提交审核未通过独立内容核验时：result=UNKNOWN、gateStatus=NEEDS_REVIEW、publishStatus=NOT_PUBLISHED，返回具体服务错误码；MCP 调用成功不是业务通过。
- 已发布：gateStatus=ACCEPTED、publishStatus=PUBLISHED。相同 requestId 同参数返回原提交；待核验后补交使用新 requestId。
- 迁移 103 将旧记录标为 LEGACY_STRUCTURE_ONLY，不改旧正式结果。
- 未发布提交的证据引用保留 30 天；24 小时上传可用期到期后不能再次提交旧证据，但审计保留期内不清理原始文件。正式证据继续长期保留。
- 结构错误仍使用现有错误协议，尚未实现方案要求的全部结构拒绝审计/修订关系。不可把这部分说成已经完整。

## 验证记录

已经执行：

```powershell
.\mvnw.cmd -pl backend/app -am test '-Dtest=AgentDirectExecution*Tests,AgentEvidenceContentReviewerTests,AgentEnvironmentProfileServiceTests' '-Dquality.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false'
```

8 项通过，0 失败、0 跳过，BUILD SUCCESS；日志 `.codex-tmp/remediation-backend-tests.log`。这些结果仅覆盖当时实现，后续修改需复测。

前端首次 type:check、build 退出 0；后续修改后的最终检查进行中。首次新导航测试访问了旧构建，不能当成新实现缺陷；重新构建后发现实际仍有环境 ID 字段，已修正，待复测。任务列表使用真实后端的测试已通过。

新增 `frontend/e2e/remediation-navigation.spec.ts`；原个人执行测试改为“无核验器时不发布”的负例，2×2 图显式拒绝。内容审核成功正例必须由实际核验器与真实业务步骤验收，不能以 mock 的 SUPPORTED 响应替代。

未执行/待完成：真实核验器准确率与业务端到端、完整 U09–U15、全部角色导航组合、完整 v2 合同、全新安装及备份恢复。最终构建/隔离升级/镜像/健康及浏览器结果见末节。风险与缺口按表持续跟踪；不得提交整体完成声明。

## 后续实测记录（2026-10-20）

- 前端生产构建（含 `vue-tsc --noEmit`）通过：`.codex-tmp/remediation-frontend-build-verified.log`。
- 后端执行 `.\mvnw.cmd -pl backend/app -am package -Dtest=Agent*Tests -Dquality.mysql=true -Dsurefire.failIfNoSpecifiedTests=false`：246 项通过、0 失败、0 跳过，BUILD SUCCESS；日志 `.codex-tmp/remediation-package-verified.log`。前一次新增附件依赖触发循环依赖测试失败，已修正调用关系后完整重跑；未放宽 Spring 循环依赖设置。
- Node `npm.cmd --prefix metersphere-mcp test`：16 项通过；0.7.0 包已打包并安装到隔离目录。MCP 握手版本同步为 0.7.0。安装包实际读取平台的检查由后续浏览器用例调用 `scripts/verify-installed-mcp.mjs`，不能将这 16 项协议测试当作第三方业务实测。
- 导航浏览器初次最终构建回归 3 项通过：旧入口迁移、真实任务列表、UI 创建 Token/原生 MCP 发现/撤销；随后补充平台生成 ID、普通附件字节下载及安装包实测，完整结果以下一轮日志为准。
- 附件授权收紧：通用和旧兼容下载入口均检查当前项目、Token、用户、用途 scope、未关联及未过期；旧兼容上传改为写入带归属的暂存记录。不能从不带归属的旧临时记录恢复授权；已关联正式附件应走正式用例附件读取路径。未补齐的物理清理、原生普通附件上传、共享引用生命周期仍属于 U09 缺口。
- 提交查询补充 `submissionId/gateStatus/publishStatus/issues/policyHash/contentHash/reviewerVersion`，保留原字段供旧客户端读取；旧历史 `LEGACY_STRUCTURE_ONLY` 不升级为内容已审核。

可复现脚本：`scripts/verify-msp-remediation.ps1`。在仓库根目录执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-msp-remediation.ps1 -Stage Validate
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-msp-remediation.ps1 -Stage Image
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-msp-remediation.ps1 -Stage Start
# 在另一终端运行代理；只监听本机并代理隔离端口。
node scripts/quality-browser-proxy.mjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-msp-remediation.ps1 -Stage Smoke
```

`Validate` 的 MySQL 测试连接本机专用 13326 端口；不连接生产。`Image` 使用原始 `Dockerfile.backend`、干净且有界的构建上下文，并对比打包前后的前端首页摘要。`Start` 仅更新 `msp-quality-verify` 隔离应用，保留原加密 key，不打印秘密；检查迁移 103。首次建立基础设施需要使用 `deploy/quality-verify.compose.yml` 及显式隔离环境变量；`Start` 的 `--no-deps` 不负责初始化外部服务。最终 Docker/Smoke 结果见末节，不能仅以脚本存在作为通过。

## 本轮文件与前后端对应

|用户路径|前端实现文件（相对 frontend/src）|后端及数据|
|---|---|---|
|个人中心 → Agent配置|`components/business/ms-personal-drawer/index.vue`、`views/setting/system/agentIntegration/index.vue`、`views/agent/access.vue`|复用 `/personal/agent-tokens`；连接检查实际请求 `/mcp`；未另造 Token 数据|
|执行中心 → 门禁策略|`router/routes/modules/execution.ts`、`views/execution/quality-policy.vue`、`api/modules/execution-quality.ts`|复用 `/system/quality/policies` 等全局接口；新增 `GlobalQualityPolicyController` 的 `/system/quality/evidence-review/status`，权限仍为系统级|
|执行中心 → 平台配置|`views/execution/platforms.vue` 及三个 `views/agent/*-profile.vue` / `credential-reference.vue`|复用 `/ai/environment-profiles`、登录及凭据 API；`AgentEnvironmentProfileRequest/Service` 允许新建时由服务端生成 ID、更新保持原 ID；尚未实现方案中的统一 `/agent/v1/platforms` 模型|
|执行中心 → 任务|`views/execution/tasks.vue`、`views/agent/queue.vue`、`views/bug-management/automationExecution/index.vue`、`api/modules/ai-execution.ts`|复用真实任务搜索及触发器接口；防止旧/取消请求覆盖成功结果；未增加新调度适配器|
|旧菜单及书签|`router/routes/execution-settings.ts`、`router/routes/modules/setting.ts`、`hooks/usePermission.ts`、`utils/permission.ts`、`views/execution/retired.vue` / `diagnostics.vue`|不删除历史业务记录；系统策略页不受项目菜单授权约束|
|MCP 上传证据、提交、查报告|原生工具与 Node 0.7.0 客户端；技能包 1.4.0|`AgentDirectExecutionService`、`AgentOperationExecutor`、`quality/EvidenceContentReviewer`、`AgentDirectExecutionTools`；数据库 `V3.7.2_103__personal_evidence_review.sql`|
|普通暂存附件及兼容接口|现有 Agent HTTP/MCP 消费方|`AgentAttachmentController`、`AgentFunctionalCaseController`、`AgentTempAttachmentService`、`AgentAttachmentService`；校验归属后才能读字节，实际存储也使用规范文件名|
|验收与状态说明|`frontend/e2e/remediation-navigation.spec.ts`、`personal-agent-execution.spec.ts`、`quality-policy.spec.ts`|新增 `AgentEvidenceContentReviewerTests`、`AgentAttachmentDownloadTests`，扩充 DirectExecution/Operation MySQL 测试；`scripts/verify-msp-remediation.ps1`、`verify-installed-mcp.mjs`|

上述实施并未修改生产环境；也没有自动提交、推送或合并 Git 分支。实施分支为 `v3.2`，现有与本任务无关的工作区文件保留。

## 最终验证结果

最终源码修订包括 `AgentShiroFilterChainExtender` 禁止 Servlet 自动重复注册 Agent Filter，以及 `AgentMcpStartupTests` 对 Spring 启动注册行为的回归。最初安装包实测暴露首次检索 HTTP 429；修复后同一验收在不增加重试、不降低限流阈值的条件下通过。

|实际执行|结果|日志|
|---|---|---|
|`pnpm.cmd -C frontend run build`（含类型检查）|通过|`.codex-tmp/remediation-frontend-build-verified.log`|
|`pnpm.cmd -C frontend exec eslint` 后接本轮实际变更的 Vue/TS 文件及新增页面/用例|0 错误，1 条原有 `handleEnvironmentChange` 未使用警告|`.codex-tmp/remediation-lint-final.log`|
|`.\mvnw.cmd -pl backend/app -am package -Dtest=Agent*Tests -Dquality.mysql=true -Dsurefire.failIfNoSpecifiedTests=false`|250 项 Agent 测试通过，0 失败、0 跳过；BUILD SUCCESS|`.codex-tmp/remediation-package-filter-fix.log`|
|`npm.cmd --prefix metersphere-mcp test`|16 项通过|`.codex-tmp/remediation-node-tests-final.log`|
|`npm.cmd pack ./metersphere-mcp --pack-destination .codex-tmp`，随后安装 tgz 到隔离目录|0.7.0 构建及安装通过；实际客户端调用包含在最后一项浏览器测试中|`.codex-tmp/remediation-npm-pack.log`、`remediation-npm-install.log`|
|验收脚本 `-Stage Image`|正式 Dockerfile 构建通过，JAR 内前端首页与 dist 摘要一致|`.codex-tmp/remediation-image-filter-fix.log`|
|验收脚本 `-Stage Start`|隔离应用 healthy，readiness UP，迁移 103 成功，数据库事务、Redis、MinIO、Kafka 检查通过|`.codex-tmp/remediation-start-filter-fix.log`|
|验收脚本 `-Stage Smoke`|14 项全部通过，包括待核验不发布、全局门禁权限、导航迁移、平台 ID、普通附件字节、原生 MCP、安装包首次检索/转发及撤销|`.codex-tmp/remediation-smoke-filter-fix.log`|
|`git -c core.safecrlf=false diff --check`、脚本语法检查|通过|终端检查|

制品与运行镜像：

- JAR SHA256：`f6db3edff5f9fdefdc781a427bf4cbc61c3bc2eba5fd32eb22bb9c6b986b08c8`。
- 前端首页 SHA256：`86bc09877987967cae24f86375244abb6122aa3c292678b7526b059f11ad735d`。
- npm tgz SHA256：`8a6251370706bd4d659ebee6558d10a7822e291968ce04685da6d19412ccdf2f`。
- 隔离运行镜像：`sha256:cac8c56b9e362cec1462eae1411dcee978f17526c8b9f541850763a49f2a0669`；容器 `/tmp/MS_VERSION` 为 `remediation-f6db3edff5f9fdef`。

测试边界及未执行项：

1. 独立审核协议适配器已实现，但没有实际 OCR/视觉核验引擎，也未配置获授权的外部服务。当前所有无法独立核验的个人提交保留 NEEDS_REVIEW，不发布正式附件/结果。不能声称已经完成真实内容审查或真实业务通过正例。需明确核验器服务、地址、鉴权和数据授权，再验证真实样本。
2. v2 不可变合同、稳定 stepId、类型/语义断言、完整拒绝审计/补交修订尚未实现；当前仍是 v1 内容 hash/当前策略校验与精确文本断言。现有测试通过不消除这些缺口。
3. 平台页仍是旧能力的入口整合，未完成统一角色/登录表单、配置迁移预览和真实登录验证。U09–U15 的完整生命周期、快照、评论删除、执行中删除决定、异步导入导出、真实调度适配器和归档仍未交付完整验收。
4. 本轮完成已有隔离环境的升级和迁移验证，未进行整库干净安装、备份恢复演练或生产部署。尚不能把本机结果等同于目标生产版本已生效；后续需在独立端口/卷执行安装与恢复检查，再按已固定的 Image/Start/Smoke 流程复验。
5. 下载技能包只进行了已有包生成测试，没有完成所有第三方 AI 客户端的实际业务测试。上述 14 项是明确列出的路径验收，不能替代方案 RA01–RA16 全量验收。

最终功能状态：**部分完成**。本记录保留未完成项；本次没有提交“完整方案已完成”的结论。
