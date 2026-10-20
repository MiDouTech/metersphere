# 个人 Agent 核心闭环实施与验收

范围以用户最新要求为准：个人 Token 访问 MCP；直接读取用例、执行、提交步骤及对应附件，门禁通过后发布附件和执行结果。无需任务、租约、Runner 或环境资产。

|需求|入口与前端|后端实现|数据|验收|
|---|---|---|---|---|
|个人授权|现有个人 Agent 配置、创建/撤销 Token|个人 Token API、MCP 鉴权及实时项目权限|现有 agent_token|现有测试回归及真实 Token 冒烟|
|取得执行基准|MCP 消费端，不增加人工页面|functional.execution.contract 返回用例内容摘要和当前全局策略|读取用例和统一策略|内容变更后旧提交拒绝|
|提交步骤附件|原生 MCP base64，Node 客户端适配|functional.execution.evidence.upload|有界临时证据，绑定 Token、用例、步骤、内容摘要|类型、大小、归属、缺失、重复校验|
|门禁后发布结果|用例详情现有附件、最近执行结果|functional.execution.submit|同一数据库事务发布附件、审计事实和结果|失败无副作用、重放不重复、附件可下载|

异常行为：Token 撤销或项目权限变化立即拒绝；用例内容或全局策略变化要求重新读取；门禁未通过不发布附件、不修改正式结果；重复 requestId 同参数返回原结果、不同参数拒绝。

全局 Q-EVIDENCE-01 与 Q-ASSERT-01 均继续执行。普通用例的文字预期作为服务端基准，采用 EQUALS 与提交的实际观察值比较；不会把 Agent 自报“通过”当作断言证据。文字比较是精确比较，不是语义或截图内容理解。所有步骤必须齐全，失败步骤也必须提供有效证据。

## 实现文件与接口

- `backend/services/agent-integration/.../service/AgentDirectExecutionService.java`：用例基准、截图校验、归属/版本检查、提交事务与执行报告。
- `backend/services/agent-integration/.../tool/functional/AgentDirectExecutionTools.java`：四个原生 MCP 工具。
- `backend/services/agent-integration/.../quality/GlobalQualityGate.java`：复用全局发布策略及 Q-EVIDENCE-01、Q-ASSERT-01，无项目降级策略。
- `AgentMcpStreamableService.java`：新写操作由事务幂等执行器管理，重试仍校验当前权限。
- `AgentMcpBundleService.java`：下载技能包 1.3.0，个人通道说明不再要求租约。
- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_102__personal_execution_evidence.sql`：暂存/批准证据、执行事实表。
- `FunctionalCaseEvidenceStore.java`、`FunctionalCaseAttachmentService.java`：普通用例附件列表、预览、下载及保留审计；`FunctionalCaseService.java`、`DefaultHubCaseImportService.java`：复制证据时生成普通对象存储附件，兼容既有消费者。
- `frontend/src/views/setting/system/agentIntegration/index.vue`：个人 Token 的“个人 Agent 测试闭环”授权组合；复用现有用例详情附件和最近执行结果展示。
- `metersphere-mcp/src/tools/caseLibrary.ts`、`src/index.ts`：Node 0.6.0 的四个工具，本地文件转换为有界 base64；移除不可进入门禁的旧上传工具入口。
- `AgentDirectExecutionTests.java`、`AgentDirectExecutionMySqlTests.java`、`AgentMcpBundleServiceTests.java`、`metersphere-mcp/test/directExecution.test.mjs`、`test/maintenance.test.mjs`、`frontend/e2e/personal-agent-execution.spec.ts`：测试与验收。

前后端对应：个人 Token 页面沿用 `/api/personal/agent-tokens`；AI 的全部操作经 `/api/mcp` 调用新工具；审核通过写入现有 `functional_case_attachment` 和 `functional_case.last_execute_*`，用例页面沿用 `/attachment/preview`、`/attachment/download`。新增执行事实由 `functional.execution.get` 提供给 MCP 消费者，不要求人工操作新页面。

## 已执行检查

在仓库根目录执行，Windows 使用 `pnpm.cmd` / `npm.cmd`，Maven 的 `-D` 参数需引号：

```powershell
.\mvnw.cmd -pl backend/app -am test '-Dtest=Agent*Tests' '-Dquality.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false'
.\mvnw.cmd -pl backend/app -am package '-Dtest=AgentDirectExecution*Tests,AgentMcpBundleServiceTests' '-Dquality.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false'
pnpm.cmd -C frontend run type:check
pnpm.cmd -C frontend run build
pnpm.cmd -C frontend exec eslint src/views/setting/system/agentIntegration/index.vue e2e/personal-agent-execution.spec.ts
npm.cmd --prefix metersphere-mcp test
```

- Agent 回归 242 项通过、0 失败、0 跳过；最终打包复测 5 项通过，包括新加的复制附件测试。Maven 日志 `BUILD SUCCESS`。
- MySQL 测试直接执行新增迁移 DDL；验证缺证据/错误归属/版本冲突拒绝，数据库中途失败完整回滚，成功发布、重放去重、下载原字节、跨项目拒绝和过期临时数据清理。
- 前端类型检查完成；生产构建日志 `built in 4m 24s`，存在既有 eval/分块提示。PowerShell 将原生命令 stderr 警告包装为 `NativeCommandError`，因此同时保存原始构建日志和产物校验，并另用 cmd 包装复核 Vite 的退出码。
- 本轮修改页面及 E2E 的 ESLint 无错误；Node 源码及独立安装后的 0.6.0 包均 16 项通过。
- 验证日志存于 `.codex-tmp/direct-core-*.log`。最终 Node 包 SHA-256：`d251b9ccb69326cf621563f1db3ec774f4ce01304ef965741be30ee6bdd64201`。
- 已打包 JAR 内 `static/index.html` 与前端构建产物 SHA-256 相同：`5bed1e75576c586a5d6d9f460fce901f9d0775b4e9d4c5f6953f8e9d23ece847`。

## 运维与边界

1. 单张截图上限 5 MiB、20M 像素，同时服从全局策略更小的大小/MIME 限制；暂存 24 小时、单 Token 暂存总量 50 MiB；每次最多 100 个步骤、100 个证据文件。证据过期后需重新上传并使用新的 requestId。
2. 执行结果支持 `SUCCESS` / `ERROR`，由服务端根据逐步骤实际观察值与用例文字预期精确比较，并核对申报结果。截图归属和有效格式检查不等于视觉语义识别或证明 Agent 确实执行过操作；不得把复制预期文本当作真实观察。
3. 当前直接通道使用 EQUALS；如果全局策略只允许 IN_RANGE，明确拒绝，不绕过全局策略。复杂类型断言继续由已有高级执行通道处理。
4. 已批准证据以数据库 BLOB 保留，备份须包含新表；删除普通附件关联不删除审计事实。复制用例生成独立普通附件，不将历史执行结果冒充新用例的执行记录。
5. 未部署或修改远程测试/生产环境，未提交 GitHub。未执行真实第三方 AI 对用户业务系统的自动化测试；本轮验收针对 MSP 接入、证据审核和结果发布链路。生产环境需部署本次后端迁移/前端，并更新本地 Node 客户端；原生远程 MCP 随后端生效。

## 容器和浏览器验收结果

```powershell
# 镜像复用已经构建过的标准运行环境，替换全部应用库和静态资源；没有新增运行时依赖。
docker build -f .codex-tmp/Dockerfile.direct-core -t msp-quality:direct-core .
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .codex-tmp/start-direct-core.ps1
# 独立复核 Vite 退出码，避免 PowerShell 将 stderr 警告当作命令失败：
cmd.exe /d /c "pnpm.cmd -C frontend exec vite build --config ./config/vite.config.prod.ts"
$env:MCP_CASE_LIBRARY_E2E='true'
$env:PW_BASE_URL='http://127.0.0.1:15173'
pnpm.cmd -C frontend exec playwright test e2e/personal-agent-execution.spec.ts --workers=1
```

- Docker 构建退出码 0，镜像 `sha256:adb118b80379d3bf5c7316805b7993a9481e69a0f25497e2cf07d89fc5a5905b`。
- 隔离应用 `msp-quality-verify-app-1` 为 healthy，readiness 为 UP；数据库迁移 `3.7.2.102` success=1；Redis、MinIO、Kafka 及事务写检查通过。
- 容器 Agent JAR SHA-256 与本地打包一致：`d5a15c2f89b2a5c720d27b3659d8b54335280ed5bbca1870603b55b50ace4f52`。容器首页、JAR 首页和最终前端构建首页摘要一致。
- Vite 独立复核退出码 0，`built in 4m 7s`；仅有已有构建警告。
- 浏览器 E2E **1 passed (17.3s)**：创建个人 Token，收窄为核心权限组合，读取用例和统一门禁，上传真实 PNG 字节；错误证据和虚报成功均拒绝且正式状态/附件不变；成功提交后结果回写、报告包含步骤与附件对应关系、重试无重复；普通用例页面显示成功及文件，下载字节一致；停用 Token 后重试拒绝。
- 首次浏览器测试失败于点中了“所属模块”同名文本；改为点击目标行的“详情”后通过，没有修改业务实现或放宽断言。
- 测试用例已清理，测试 Token 已撤销；未留下可用测试凭据。原有其他开发容器未调整。

|核心需求|最终状态|
|---|---|
|个人在平台配置 Token，AI 经 MCP 读取/操作其授权项目|已验证|
|无需任务或租约，逐步骤上传证据并经全局门禁审核|已验证|
|审核拒绝不改变正式结果，审核通过原子发布附件和结果|已验证|
|平台查看/下载附件，MCP 查询执行事实；重复提交及撤权处理|已验证|

功能状态：**完成（上述核心范围，本地隔离环境验收）**。完整用例库扩展方案仍为部分完成；远程环境部署、真实第三方 AI 业务测试和全项目复制/导入 UI 回归未执行。复制附件的本次适配已通过单元测试，跨系统大规模历史数据迁移未纳入本轮。复测核心路径使用上面的 Playwright 命令；业务系统实际测试需连接目标 MSP 的 MCP，按 README 中四步操作执行，不应将本地夹具结果作为真实业务测试证据。
