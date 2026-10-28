# 本周复测整改实施记录

基线：msp-weekly-regression-remediation-review-20261028.md；本次补充：计划选择器按当前名称实际长度自适应，仅在容器不足时省略，完整计划分组不拆行。

## 需求追踪（代码整改已落地，整体部分完成）

|需求|入口/前端|后端|数据配置|验证与状态|
|---|---|---|---|---|
|R1 敏感事实|MCP submit/get|AgentSensitiveDataSanitizer、AgentFactCipher|复用 facts-key；不生成/覆盖环境密钥|嵌套字符串/缺密钥/加解密/深度限制；实际结果见下表|
|R2 导入重试|importJob / import 请求|AgentCaseImportController、AgentCaseImportService、FollowupCoordinator|提交阶段持久化迁移|停止前未提交零写入、已提交恢复；实际结果见下表|
|R3 资产同步|资产 readiness、列表|DefaultHubCaseSyncService、outbox、源变更入口|复用来源映射/更新版本|查状态/附件/事实/缺陷漏同步根因；实际结果见下表|
|R4 导出等级|资产导出→通用导出 API|FunctionalCaseFileService、ExportRowMapper|模板字段 ID 对应|真实映射回归；实际结果见下表|
|R5 工具契约|原生/Node tools/list、下载技能|工具注册与 schema|实际绑定版本属外部客户端配置|对比源码及实测产物；实际结果见下表|
|R6 旧详情|case detail、MCP get/search|FunctionalCaseService、模板服务|先只读定位，不盲改历史数据|解析/模板/数据边界；实际结果见下表|
|R7 布局|Token 表、case/plan/review|保留原接口/权限|无业务迁移|局部滚动、固定列、Demo 样式；实际结果见下表|
|UX 全选切换|batchAction→base-table→useTable|复用原 selectAll 协议|无|ALL/CANCEL_ALL 状态、排除项；实际结果见下表|
|UX 计划自适应|caseTable 概览|复用计划统计|无|名称测量、分组不拆行、宽度上限；实际结果见下表|

原有未提交文档/测试证据不覆盖；不提交、不部署。部分完成/阻塞的验收项不会因代码修改自动改为通过。

## 实际整改与根因定位结果

|主题|本轮结果|修改文件及链路|实际证据/限制|
|---|---|---|---|
|R1|统一敏感识别/脱敏，递归识别字符串中的 JSON，保留字符串类型；深度、大小和解析器限制失败关闭|AgentSensitiveDataSanitizer → AgentFactCipher → 原执行写入和读取路径；对应 JUnit|嵌套字符串缺密钥拒绝、有密钥原值加解密、输出脱敏、过深拒绝均通过。未修改测试服务器密钥，历史存储扫描未执行。|
|R2|预检停止后重试回 PRECHECKED，不入队；已提交只重置失败项；worker 也检查提交标记；Web 决策和提交同事务|AgentCaseImportController → AgentCaseImportService → functional_import_job / item / followup；迁移 135|3 项阶段测试通过。旧任务有 SUCCESS 不能证明人工提交，迁移只信任明确 FILE 关联决策；含糊的旧活动任务暂停，已有成功项保留。已复用本机既有 MySQL，在随机临时库验证迁移 135/136 和生产补偿 SQL，2 项通过；未验证完整历史库升级。|
|R3|镜像不再主动重置执行/评审状态；hash 纳入执行字段；附件上传漏事件补齐；执行及附件变更提升源更新时间；镜像事实读取规范来源引用|FunctionalCaseService、FunctionalCaseAttachmentService → FunctionalImportEffectOutbox → DefaultHubCaseSyncService；AgentDirectExecutionViewProvider；迁移 136|执行变化使 hash 失效、资产读取来源当前事实的单测通过。旧关系标为过期，新增每 30 秒最多 10 条的补偿排队；实际全字段、解绑/恢复及失败收敛仍需部署验收，不能认定“全部状态同步”已完整通过。|
|R4|找到源字段 ID 与资产默认模板字段 ID 不一致的路径，导出按 functional_priority 语义映射；按批次查询避免逐条 N+1|资产导出原入口 → FunctionalCaseFileService → 原 ExportRowMapper / XLSX|P0/P1/P2/P3、未设置、冲突映射定向测试通过。权限范围仍由 CaseAssetExportAccess 校验；真实下载文件待部署复测。|
|R5|仓库 Node 契约正确，发现服务声明版本硬编码 0.8.0 与 package 0.8.1 不一致；改为读取 package，准备版本 0.8.2|metersphere-mcp/src/index.ts、package.json、package-lock.json、stdioContract.test.mjs|实际启动构建后的 stdio 服务，声明版本及工具 schema 检查通过；62 文件打包预检通过。未发布 npm、未修改外部连接器绑定，旧连接器缓存原因尚不能确证。|
|R6|再次请求旧详情仍 500；源码检查并增加缺正文/非法步骤保护及安全提示；公共详情页显示失败，停止只显示加载占位|FunctionalCaseService 共享步骤解析；ms-prev-next-button 的 failed 事件 → ms-detail-drawer 失败展示|非法步骤/空步骤/补旧步骤 ID 测试通过；原故障记录根因仍待后端日志，**未做历史数据修复，不计已解决**。用户已索取查询命令，见下文。|
|R7|Token 根因是 MsCard 按全窗口宽度计算，没有扣个人中心菜单；改 auto-width，保留局部横滚及右侧列；试点表头及批量按钮应用 Demo 样式|agentIntegration/index.vue → MsCard；caseTable / planTable / reviewTable → base-table|相关 SFC、lint、stylelint 通过；窄于 640px 的试点表格将右固定列改为随表格滚动，按钮仍渲染，避免覆盖名称。Token 全链路及所有视口需部署后验收。|
|UX 全选切换|文案随 ALL 切换；点击 ALL/CANCEL_ALL，顶部和底部共用处理器，旧箭头保留|batchAction → base-table → 原 useTable.selectAllChange|真实生产选择逻辑单测：跨页、排除项、取消、重新全选通过；ego 隔离真实组件点击显示 6 → 0 且按钮文案恢复。|
|UX 计划自适应|由隐藏的同字体文本测量自然宽度，不设置固定 260/360px 上限；容器不足再压缩、省略；select/轨道/百分比同一不拆行分组|caseTable.vue 模板和局部 Less；原 useSinglePlanOverview/API 不变|隔离真实模板与 Arco 运行时：短名称约 251px、长名称约 419px，窄容器收缩到 200px；971/1366/1908 三种视口中心线误差 0px、无越界。完整名称 title 保留；非零 25%→75% 切换通过。|

### 额外定位结论

- 发布来源：检查了 `write-build-manifest.mjs` 及 `.github/workflows/cnb-build.yml`。当前流程普通 checkout，manifest 使用 GITHUB_SHA/本地 HEAD；没有发现足以证明测试服务器发错包的代码证据。仍须比对部署制品和流水线运行记录，不能直接修一个假定根因。
- Excel 目录：`FunctionalCaseService.saveImportData` 构建模块路径映射，记录的文件内模块路径参与最终定位。这是现有路径优先行为；本轮没有未经确认改变导入目录语义。
- 其余 16 条部分完成和 2 条阻塞的完整验收矩阵没有在本轮重跑，不改写历史结果；修改代码不等于解除权限、人工删除确认、密钥和长期留存等验收阻塞。

## 配置、迁移和发布要求

新增迁移：

- `V3.7.2_135__import_commit_stage.sql`：committed_at / stopped_from；只按明确 FILE 决策回填提交标记；含糊的旧 QUEUED/RUNNING 转 STOPPED，保留成功用例与条目。
- `V3.7.2_136__asset_execution_projection_reconcile.sql`：增加过期扫描索引，将已有关系标为过期，分批补偿更新原镜像，不改源用例、不创建重复来源。

发布前在隔离 MySQL 验证两条迁移和混合历史状态。迁移会更新来源关系并暂停部分旧任务，应先备份，避免新旧后端 worker 混跑；全部写实例更新后再开放导入。回退需暂停相关写路径并保留新增字段/任务审计，不能只删除列恢复旧代码。facts-key 仍由部署 Secret 提供，本轮没有生成或覆盖密钥；旧密文必须保留兼容密钥。Node 0.8.2 仅准备代码，尚未发布。

## 实际验证命令与结果

证据目录：[本轮验证](../testing/msp-weekly-fixes-20261028/)。完整生产构建、迁移、健康检查及部署后端到端均未完成，以下是最强已执行子集。

```powershell
python .codex-tmp/weekly-fixes-20261028/prepare.py
javac -J-Xmx256m '@.codex-tmp/weekly-fixes-20261028/compile.args'
java '@.codex-tmp/weekly-fixes-20261028/unit.args'
# 19 个当前源/测试文件定向编译通过；最终 24 项通过，0 跳过（含 2 项真实 MySQL 测试）

node --max-old-space-size=192 --test frontend/scripts/test-selection-toggle.mjs
# 6 项通过，包括生产选择 hook 与原计划选择 composable 的严格语义检查
node --max-old-space-size=192 frontend/scripts/verify-weekly-remediation-sfc.mjs
# 8 个 SFC 语法、脚本转译、模板编译通过；不是全项目 vue-tsc
```

对 8 个修改 SFC 和 `test-all-pages-toggle.mjs` 执行目标 ESLint，最终 0 错误、12 个既有警告；8 个 SFC 的 stylelint 通过。新 SFC 检查脚本的最后格式检查见验证日志，不将格式检查当类型检查。

Node 包内实际执行：

```powershell
node --max-old-space-size=256 node_modules/typescript/bin/tsc
node --max-old-space-size=128 --test test/caseEvidenceProtocol.test.mjs test/caseWriteIdentity.test.mjs test/caseLibrary.test.mjs test/directExecution.test.mjs
node --max-old-space-size=128 --test test/stdioContract.test.mjs
npm.cmd pack --dry-run --ignore-scripts --json
```

构建通过；首组 13 项、stdio 1 项通过；0.8.2 打包预检为 62 文件、40,852 字节。CI 中在完整前端生产构建前新增选择与 SFC 检查。CI 使用 Node 20.8.1，采用单测试入口串行注册两组用例，未使用该版本缺少的 `--test-concurrency` 参数（[版本对应 CLI 文档](https://nodejs.org/download/release/v20.8.1/docs/api/cli.html)）。本地运行版本为 Node 24，未声称已实跑 Node 20 CI。

ego 使用单任务 13，读取测试环境后复现旧用例 500，之后在本地加载真实组件模板/脚本和合成数据验证样式、选择交互。此证据是**隔离组件证据**，不是部署后的真实业务验收。未新增线上 Token、未写测试环境业务数据。结束时任务空间 13 已不存在，清理调用返回 task space not found；未重开新任务、未清 Cookie 或重置用户状态。

### 未执行项及准确补验方式

本机空闲内存从约 1 GiB 降到约 0.6 GiB，没有启动 Docker、完整应用或提高堆上限强跑。Maven 不在当前 PATH，项目可使用已有 mvnw.cmd。未执行：全项目 vue-tsc/生产构建、Maven 全模块打包/集成测试、完整历史库升级、镜像构建、容器启动/健康、部署后完整 E2E、多角色及长期留存。风险包括未发现的跨模块类型/链接错误、SQL/数据兼容、异步收敛和权限组合问题。

资源及 Maven 就绪后执行（仓库根目录）：

```powershell
pnpm --dir frontend run build
.\mvnw.cmd -pl backend/services/agent-integration -am -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AgentCaseImportStageTests,AgentSensitiveDataSanitizerTests,AgentFactCipherTests,AgentAssetExecutionViewTests,DefaultHubExecutionProjectionTests,FunctionalCaseExportRowMapperTests,FunctionalCaseDefinitionStepsTests test
.\mvnw.cmd -pl backend/app -am -DskipTests=false package
# 仅当本机专用测试 MySQL 已在 127.0.0.1:13326 就绪时运行，使用既有 quality-test-only 测试约定：
.\mvnw.cmd -pl backend/services/agent-integration -am -DskipTests=false -Dquality.mysql=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=WeeklyRemediationMigrationMySqlTests test
```

必须确认 Maven 成功后再从新 JAR 解包构建，不能使用旧 dependency 残留。以下复用仓库既有隔离环境与同一个 Compose project，需先从本机测试 Secret 配置 QUALITY_DB_PASSWORD、QUALITY_STORAGE_PASSWORD、QUALITY_FACTS_KEY，并有足够资源；**本轮没有执行**：

```powershell
$unpackDir = 'backend/app/target/weekly-fix-' + (Get-Date -Format 'yyyyMMddHHmmss')
New-Item -ItemType Directory $unpackDir
Push-Location $unpackDir
jar -xf ../app-3.x.jar
Pop-Location
docker build -f Dockerfile.backend --build-arg "DEPENDENCY=$unpackDir" -t msp-weekly-fix:20261028 .
docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml -f deploy/weekly-fix-verify.override.yml config --quiet
docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml -f deploy/weekly-fix-verify.override.yml up -d --wait
Invoke-RestMethod 'http://127.0.0.1:17071/actuator/health/readiness'
docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml -f deploy/weekly-fix-verify.override.yml ps
```

readiness 必须 UP，并单独核对 135/136 迁移成功、导入任务恢复、MCP/UI 真请求。既有 quality compose app 上限 3 GiB，不能在当前内存条件下运行；没有降低保护阈值。部署后按原 40+6 追踪逐项验收，不能以本轮单测替代。

## 历史详情 500：服务器日志查询命令

请在页面重新打开用例 100025（ID：1712038715113627712），报错后在测试服务器执行：

```bash
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
MSP_CONTAINER='实际后端容器名'
docker logs --since 15m --timestamps "$MSP_CONTAINER" > msp-case-detail-error.log 2>&1
grep -n -C 80 -E '1712038715113627712|functional/case/detail|Exception|Caused by:' \
  msp-case-detail-error.log > msp-case-detail-context.log
```

优先提供 context 文件；无匹配则保留完整 error 文件。日志不在 stdout 时：

```bash
docker exec "$MSP_CONTAINER" sh -lc 'printf "MS_LOG_PATH=%s\n" "$MS_LOG_PATH"; pwd; ls -lah ./local-runtime/logs/metersphere 2>/dev/null'
docker inspect --format '{{range .Mounts}}{{println .Source " -> " .Destination}}{{end}}' "$MSP_CONTAINER"
```

只遮蔽实际凭据，保留 traceId、caseId、异常类、完整调用栈及 Caused by；不要输出整份容器环境变量。历史故障待日志后继续精确修复，目前整体状态仍为**部分完成**。

### MySQL 补验记录

复用既有 127.0.0.1:13326 服务，没有启动新服务。迁移及补偿查询在随机临时库运行后清理。补偿查询首次复现混合排序规则错误，已将身份关联比较改为 BINARY，并补到来源事实查询；复验 2 项通过，最终后端定向测试共 24 项通过、0 跳过。此验证不替代完整应用迁移与健康检查。详见 unit-final.log 和 migration.log。
