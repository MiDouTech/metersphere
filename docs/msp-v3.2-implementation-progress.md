# MSP v3.2 实施追踪

基线：89f0d2f0c8；分支：v3.2。需求依据：msp-consolidated-transformation-plan.md v1.1。
状态：部分完成，开发中。未合入 upstream/main 的 31 个提交。原有 ExtTestPlanBugMapper.xml、模板 README 及未跟踪文档不计为本次实现。

## 开发前需求追踪

### 配置模块修复追踪（2026-09-23，开始修复）

| 修复需求 | 前端 | 后端/数据 | 验收 |
|---|---|---|---|
| 编辑会话隔离 | quality-policy.vue 固定保存目标、失效旧回调、提交时禁关 | 不变 | 延迟校验、切换草稿/项目不串写 |
| 全量历史访问 | 服务端分页、独立当前版本入口 | policies 分页/detail | 101+ 版本可读、跨项目不可读 |
| 发布审计 | 版本详情显示前后版本摘要 | 发布事务追加事件、增量迁移 | CAS 失败无事件、发布指针与事件原子提交 |
| 精确校验 | 字段路径与中文提示 | 严格 schema 分支诊断 | 缺字段、非法枚举、大小边界路径 |
| 停用项目只读 | 展示服务端拒绝写入原因 | Access 检查项目启用状态 | READ 允许、MANAGE/PUBLISH 拒绝 |

外部任务、试算执行器、审批和正式结果守卫属于后续整体改造，本节不将其计为配置缺陷修复完成。

修复实现：编辑弹窗忙碌期间禁用关闭与 Esc，操作固定草稿目标，旧编辑会话回调失效；列表增加 page/pageSize/total 与按 ID 详情，页面独立展示当前发布版本；V3.7.2_93 新增追加式发布事件，记录前后策略 ID/hash、操作者、时间、原因并与指针更新同事务；详情显示审计，历史无事件明确标示；schema 按 ruleId 选择分支返回精确字段路径和稳定错误码；停用项目允许读取、拒绝策略管理/发布。

修复测试：27 项相关后端测试在真实 MySQL 专用库通过，含 102 版本分页、跨项目详情、发布审计与失败回滚、字段路径、停用项目边界。前端新增 `frontend/e2e/quality-policy.spec.ts`，隔离环境角色 fixture 位于 `deploy/quality-verify/rbac-fixture.sql`。

### 修复验收结果（2026-09-23）

| 命令/验证 | 实际结果 |
|---|---|
| `.\mvnw.cmd -pl backend/app -am package '-Dtest=QualityPolicy*Tests,AgentExecutionExceptionHandlerTests,AgentExecutionChannelPolicyTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dquality.mysql=true'` | 27 项、0 失败、0 错误、0 跳过，BUILD SUCCESS；日志 `.codex-tmp/quality-repair-package.log` |
| `pnpm.cmd -C frontend run type:check`、`pnpm.cmd -C frontend run build` | 均退出 0；构建日志 `.codex-tmp/quality-repair-build.log`，仍有依赖 eval/体积警告 |
| `pnpm.cmd -C frontend exec eslint src/api/modules/execution-quality.ts src/views/execution/quality-policy.vue e2e/quality-policy.spec.ts` | 修复测试脚本的循环 lint 错误后，定向检查通过 |
| `pnpm.cmd -C frontend run test:permission-resources`、`test:route-tabs`、`test:api-contracts`、`test:layout-overflow` | 四项通过；其中原 API 静态脚本不替代新增接口的 MockMvc 与真实 E2E 验收 |
| `QUALITY_E2E=true; PW_BASE_URL=http://127.0.0.1:15173; pnpm.cmd -C frontend exec playwright test e2e/quality-policy.spec.ts --timeout=90000`（PowerShell 环境变量写法见 README） | 6 项真实浏览器测试通过；`.codex-tmp/quality-repair-e2e-final.log` |
| `.\mvnw.cmd -pl backend/app -am package '-DskipTests'` | 前端构建结束后重新打包以纳入最新静态资源；退出 0，此命令不算测试证据，测试证据来自前面的 27 项 |
| `docker build -f Dockerfile.backend --build-arg DEPENDENCY=backend/app/target/quality-dependency-repair-final -t msp-quality:verify .` | 标准 Dockerfile 构建成功；运行镜像 ID 与该镜像一致：`sha256:04c9b03e23836f80ffcaa1eecf0d68749aea618d8e35a6c1306135d4a0545252` |
| compose 更新 app；`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-quality-environment.ps1`；`git diff --check` | 通过；应用 healthy；91/92/93 迁移均成功，含本轮基于已有策略数据的 93 升级；日志 `.codex-tmp/quality-repair-final-environment.log` |

浏览器覆盖：延迟真实 validate 响应时无法关闭/切换保存目标，草稿保存后可读取；发布后显示前后摘要及操作者；reader 拒绝创建/校验/发布，editor 可创建/校验但不能发布，publisher 可发布但不能创建/校验，跨项目请求拒绝；停用项目 READ 成功、创建返回 QUALITY_POLICY_PROJECT_DISABLED 且测试 finally 恢复原启用状态；超限字段返回 `/rules/0/parameters/maxArtifactBytes` 并保留 JSON 原文。

首轮浏览器失败是定位器匹配了隐藏弹窗、默认等待时间不足和直接 SQL 创建测试账号触发平台开户校验；现已改为通过正式开户接口创建账号，保留真实身份/权限链路，不关闭开户校验。首次 Docker 构建遇软件源 TLS 失败，重试及最终标准构建成功；临时离线备用镜像不作为最终构建证据。

本轮新增/修改范围：质量策略页面与 API client；QualityPolicyService/Controller/Access/Validator、AgentSafeErrorMapper；V3.7.2_93 发布审计迁移；四组 QualityPolicy 测试；quality-policy.spec.ts；隔离 RBAC fixture、环境验证脚本和说明文档。未提交或推送，保留用户原有改动。

尚未执行：全仓后端回归、生产存量克隆升级、权限配置页面完整操作、真实 Agent Token HTTP 全矩阵、对象上传下载与在线 MCP。本轮使用现有隔离库升级，不代表生产升级演练；Agent Token 拒绝仅有既有单元证据。完整执行门禁、样本试算、字段级版本差异和扩展规则仍未实现。配置缺陷已有上述验证，综合改造功能状态仍为**部分完成**。

| 需求 | 前端入口/实现 | 后端接口/实现 | 数据配置 | 验收 | 当前状态 |
|---|---|---|---|---|---|
| R1/R7 策略配置 | 执行质量→门禁策略；表单/JSON 草稿、版本发布 | quality/policies；项目权限、严格校验、CAS | execution_quality_policy、项目当前版本、权限资源 | 单元/真实 MySQL 集成/浏览器基础链路通过；U20/U21 未全部覆盖 | 部分完成 |
| R2/R7 无模型任务 | 计划/执行中心创建 | external-task、preflight、claim | task/contract/activation | U01/U16/U19 | 未实施 |
| R3/R7 提交审批 | 执行详情、待审批 | seal/check/approve/revoke/writer | submission/decision/approval/result/outbox | U02～U06/U13～U18 | 未实施 |
| R2/R3/R4 结果投影 | 用例历史、报告、缺陷验证 | 统一查询及写入守卫 | projection/dependency/report refs | U07/U08/U17/U19 | 未实施 |
| R5/R6 保留通道 | API 历史/能力状态 | 现有服务、能力查询 | 通道配置 | U11、传统回归 | 待验证 |
| R8 资产 | 文档/用例引用 | 既有目录/版本服务 | 版本引用 | U12、迁移对账 | 待验证 |

## T0 静态调用路径

- 创建：AgentExecutionController.create → AgentExecutionService.createInternal；普通页面固定模型通道，个人 MCP 单独分支。不能简单更名或伪造 Token 上下文。
- 结束：AgentRunnerService.completeLease → AgentExecutionWritebackService.writeback → 计划/用例写入；需要替换为批准后 outbox 路径。
- 正式写：TestPlanFunctionalCaseService.run/batchRun、AgentFunctionalCaseSubmitService、FunctionalCaseService 编辑/导入/关联计划同步，均需纳入守卫。
- 暂停恢复：AgentExecutionCheckpointService 清空 current_execution_id；领取服务重新创建尝试，需按方案统一生命周期。
- 权限：Shiro PermissionConstants + permission.json + permission_resource 迁移；服务层必须再次校验项目上下文与权限，不能只依赖按钮。
- JSON：现有 SDK JSON 接受未知字段/注释，不适合作为门禁严格校验器；新增局部严格解析不改变全局协议。

## 边界与失败行为

策略编辑限定登录用户和当前项目；Agent Token 不得管理/发布。只接受已实现的声明式规则，拒绝未知字段、重复键、非法类型、超限和任何脚本。草稿与发布采用版本 CAS，发布记录不可修改。并发发布必须校验项目当前版本，防止旧草稿覆盖刚发布的新版本。策略配置交付不等于门禁执行已启用；执行消费接入前页面明确说明。

## 环境与验证记录

- git branch --show-current：v3.2。
- .\mvnw.cmd -version：Maven 3.8.3、Java 21.0.11 可用；PATH 无 mvn，使用仓库 wrapper。
- Docker 首次查询 Engine 不可用；启动已有 Docker Desktop 后 docker version 已返回 Linux Engine 29.5.3。
- frontend/node_modules 存在。迁移当前最大序号为 V3.7.2_90；新增使用后续序号，不修改已发布脚本。
- 生产启动状态、在线 MCP、全量旧业务数据库基线尚未验收。T0 尚未退出。

## 2026-09-23 首批实现与验证

功能状态：**部分完成**。本批完成策略配置的首条前后端链路，并建立隔离环境；没有实现外部任务、冻结合同消费、材料试算、证据封存、独立审批、outbox 回写或正式结果守卫，T0～T4 整体未完成。当前发布的是配置版本，不会使已有执行自动经过门禁。

### 文件与接口对应

| 文件/目录（仓库相对路径） | 本次内容 |
|---|---|
| frontend/src/router/routes/modules/execution.ts；frontend/src/views/execution/quality-policy.vue；其 locale/*.ts | 项目入口、空态/错误态、表单与 JSON 同一草稿、校验、保存、对比发布、复制版本 |
| frontend/src/api/modules/execution-quality.ts | 对接 GET /quality/policy-schema、GET /quality/policies、POST /quality/policies/validate、POST /quality/policies、PUT /quality/policies/{id}/draft、POST /quality/policies/{id}/publish |
| backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/QualityPolicyController.java | 上述真实接口及 /api/quality 别名、请求校验、权限声明 |
| 同模块 quality/QualityPolicyAccess.java、QualityPolicyService.java、QualityPolicyValidator.java、QualityPolicyValidationException.java | 显式项目角色授权、拒绝 Agent Token、事务版本分配、发布 CAS、不可变已发布版本、严格 schema、规范化 hash、安全字段错误 |
| 同模块 resources/quality/quality-policy.v1.json、resources/permission.json | 首批声明式规则与权限目录；仅 Q-EVIDENCE-01、Q-ASSERT-01 配置，尚无执行检查器 |
| backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_91__execution_quality_policy.sql；dml/V3.7.2_92__execution_quality_policy_permissions.sql | 两张表、索引与权限资源；不修改历史迁移，不自动给旧角色发布权 |
| SDK PermissionConstants.java；AgentExecutionExceptionHandler.java；AgentSafeErrorMapper.java；frontend/src/config/permissionLocale.ts | 权限常量、400/403/409 等安全错误处理和操作名称 |
| backend/services/agent-integration/src/test/java/io/metersphere/agent/quality/*Tests.java | 19 项新增测试，含 5 项真实 MySQL 事务/并发测试 |
| backend/app/pom.xml；deploy/quality-verify.compose.yml；deploy/quality-verify/；scripts/verify-quality-environment.ps1；scripts/quality-browser-proxy.mjs | 补齐 Actuator 依赖（端点默认仍关闭），独立容器/卷/回环端口，迁移及健康验证、真实 API 浏览器代理 |
| docs/msp-consolidated-transformation-plan.md；本文 | 开工状态、需求追踪及验收记录 |

### 已执行命令及结果

从仓库根目录执行，2026-09-23；本地日志保存在 `.codex-tmp/`。隔离环境完整复现命令见 [验收环境说明](../deploy/quality-verify/README.md)。

```powershell
.\mvnw.cmd -pl backend/app -am package '-Dtest=QualityPolicy*Tests,AgentExecutionExceptionHandlerTests,AgentExecutionChannelPolicyTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dquality.mysql=true'
pnpm.cmd -C frontend run type:check
pnpm.cmd -C frontend exec eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts
pnpm.cmd -C frontend run test:api-contracts
pnpm.cmd -C frontend run test:route-tabs
pnpm.cmd -C frontend run test:permission-resources
pnpm.cmd -C frontend run test:layout-overflow
pnpm.cmd -C frontend run build
docker build -f Dockerfile.backend --build-arg DEPENDENCY=backend/app/target/quality-dependency-health -t msp-quality:verify .
docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml up -d app
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-quality-environment.ps1
git diff --check
```

- Maven：BUILD SUCCESS，进程退出 0。22 项测试，0 失败、0 错误、0 跳过；包括新增 19 项与既有异常处理/通道策略 3 项。日志 `quality-health-package.log`。真实 MySQL 测试专用库 `127.0.0.1:13326/quality_verify`，不连接共享开发或生产。
- 类型检查及四项静态契约检查通过；权限资源覆盖 93 个精确匹配、49 个兼容、0 个未绑定。全量 ESLint 0 错误、265 警告；针对变更文件检查 0 错误、3 个 CRLF 格式警告。没有将警告视为清零。
- 前端生产构建生成 dist；最终显式捕获进程退出码为 `FRONTEND_EXIT=0`，日志 `quality-build-verified.log`。存在依赖 eval/大块构建警告。
- Docker 镜像构建成功；最终镜像 manifest `sha256:a1f3c41f320c74f2998176317346cf9b62a2a83eb6e8158b584671dac6dc9250`。应用及四个依赖容器均 healthy。
- 健康脚本退出 0：readiness UP；Flyway 无失败记录，91/92 两条新迁移 checksum 非空且成功；事务插入并回滚、Redis PING、MinIO readiness、Kafka metadata 通过。重新创建应用后迁移校验仍通过。
- `git diff --check` 通过，仅提示 Windows 行尾转换。未覆盖或提交用户原有改动。

### 浏览器取证（真实后端）

Playwright CLI 命名会话 `mspquality`，本地静态构建代理 `http://127.0.0.1:15173` 转发至隔离容器 `18081`，没有 Mock 接口。仅在隔离新库开启管理员登录入口，并给测试角色显式增加三项策略权限；不是生产授权变更，也不是默认迁移授予。

1. 无显式项目授权时，策略 schema/list 均返回 403，页面跳转无资源权限。
2. 授权后显示空列表；新建 Quality-Smoke-v1，表单→JSON→表单后保存版本 1。
3. 发布前显示当前/待发布 JSON 对比；填写原因 Isolated-acceptance 后发布。刷新页面后版本 1 仍为当前发布版本，编辑/发布按钮消失。
4. 复制已发布版本，粘贴 `{}`，切回表单时显示安全字段错误，保留原 JSON，不丢弃输入。
5. 再次复制并保存得到版本 2 草稿，版本 1 保持当前发布版本。该操作在最终镜像重启后成功。

对应本地快照：`.playwright-cli/page-2026-09-23T06-35-39-259Z.yml`（发布）、`page-2026-09-23T06-36-44-130Z.yml`（错误保留）、`page-2026-09-23T06-38-04-117Z.yml`（复制保存）。初期登录/图标加载及预期 403 产生控制台错误；最终刷新策略页无新增控制台错误，不能据此声称整个应用没有前端问题。

### 未验证与风险

| 项目 | 原因/剩余风险 | 后续操作 |
|---|---|---|
| 全仓后端测试及旧功能全面回归 | 本轮只执行相关 22 项；其他业务可能有既有问题 | `.\mvnw.cmd -pl backend/app -am test` |
| 真实存量升级/迁移对账 | 当前只在隔离空库执行全量迁移和重启校验，没有获给存量克隆数据 | 向独立验收库恢复脱敏克隆后，执行 README 的 compose 启动和健康脚本，再比对关键表行数/业务状态；不能在生产直接试跑 |
| 普通项目角色 UI 授权配置及多角色全矩阵 | 当前真实浏览器使用管理员的显式项目角色授权；服务鉴权另有单元验证 | 在隔离环境用普通项目成员逐项验收 read/manage/publish 权限和切项目；现有 `pnpm.cmd -C frontend run test:e2e:information-architecture` 仅为基础回归，不能替代该矩阵 |
| 对象上传下载、在线 MCP、外部任务/审批端到端 | 这一批没有实现整条执行消费链；健康探针不证明对象往返和 MCP 可执行 | 后续 R7 实施时补真实材料与 Agent Token 的专项 E2E；当前没有可声称已通过的测试命令 |
| 材料试算、完整门禁规则、版本分页/历史检索 | 本批仅两条规则配置及最近 100 条列表；页面明确功能边界 | 按方案后续任务补齐，不把保存/发布配置当作执行验收 |

浏览器截图：`output/playwright/quality-policy-published-and-draft.png`；健康验证日志：`.codex-tmp/quality-environment-result.log`。代理脚本通过 `node --check scripts/quality-browser-proxy.mjs`。

实施中修复了真实 MySQL 并发建草稿的锁升级死锁（改为冲突 UPDATE 获取排他锁）、策略服务 ID 生成器静态初始化耦合（改构造注入）、容器日志路径权限和缺少健康依赖的问题。以上均有重跑测试或容器启动证据。
