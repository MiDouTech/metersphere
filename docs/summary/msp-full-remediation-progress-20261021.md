# 完整需求实施追踪

基线：[逐项自检 L01–L52](msp-two-day-requirement-selfcheck-20261021.md)、[完整方案](msp-remediation-complete-plan-20261020.md)。全部 U/RA、R/E、AC 及个人 Token 第 19 节继续有效，未在本表推进的项目保持自检状态，不自动完成。

|需求|前端/调用入口|后端/数据配置|实现和验收状态|
|---|---|---|---|
|R01/L01–03|用例主列表→公共分页/useTable→每用户偏好|原 functional/case/page，不新增表|进行中：四档选择、空表可见、页码/选择重置、过期响应隔离；待完整 UI 验收|
|R05/L04|公共表格批量操作→原各业务批量 API|原权限与业务接口保持|进行中：常驻、零选/加载禁用、子项权限；待逐页零/单/多选/权限回归|
|R07/L06|HTTP 401→单例确认→现有登录跳转|原认证协议|进行中：单例确认已加入；分类错误/Blob/返回地址待完整验收|
|R08/L07|公共表格加载→激活/焦点/可见性/低频刷新|原列表 API；请求序号隔离旧响应|进行中：共享刷新已加入；非公共表格页面及全平台负载验收待补|
|R02/L08–10|MCP/Agent HTTP→过滤器及工具准入|Redis 原子滑动窗口；默认 720/60000ms；保留检索子限额|进行中：实现及 720/721、时钟边界、并发测试已加入；实际 Redis 双实例及故障验收待补|
|其他 L11–52|以自检表逐项入口为准|以自检表逐项链路为准|继续实施，未标完成|

异常及边界：未选择/加载期间禁止批量操作；无权限操作不可见；分页变化保留筛选但取消旧选择；旧请求不能覆盖新结果；空结果仍能改条数；后台刷新不打断输入或轮询隐藏页面；并发 401 只显示一个确认；Redis 故障不无限放行。

当前状态：**实施中，整体部分完成**。本记录不代替真实运行证据；实际检查与剩余需求在实施期间持续补入。

## 本轮实现及证据更新

尚未提交。以下更新优先于上表早期进度，未列出的 L01–L52 行保持原缺口状态。

|需求|前后端对应|新增实现和剩余验证|
|---|---|---|
|R01/R05|用例列表、公共表格→原业务列表/批量 API|四档和用户偏好、选择重置、常驻禁用、子项权限；修复窄屏裁剪和底栏出屏。全列表/权限矩阵待验收|
|R06|能力 Store/生成路由→feature/capabilities→RequiresLlmFeature|默认关闭平台模型；对话、生成和平台创建受控，历史及 MCP 保留。AOP 单测通过；完整控制矩阵待验收|
|R07|HTTP/Blob→单例确认→退出/登录|Blob JSON 解码、显式集成归属、完整内部路径恢复；真实错误协议和并发失效矩阵待验收|
|R02|Agent HTTP/工具→Redis Lua|720/60000ms，真实 Redis 双实例并发通过；完整客户端重试矩阵待验收|
|R03|登录→SessionLifetime/SSO store|14 天绝对期限、namespace+用户+Session 隔离、退出当前端；修复 SSO 回调编码。单测通过，真实 SSO/会话轮换待验收|
|L25|环境编辑→EnvironmentProfileService.update|强制客户端 version，缺失/负数拒绝单测通过，真实并发待验收|
|L29|定时清理→对象/预览/元数据|先删字节，失败保留元数据重试；关联/共享引用保护和 SKIP LOCKED。单测与实际 SQL 查询通过，完整生命周期待验收|

实际命令：

1. `./mvnw.cmd -pl backend/services/agent-integration -am test -Dtest=AgentTokenRateLimiter*Tests,AgentTokenFilterTests,SessionLifetimeTests,MiduoSso*Test*,LlmFeatureTests -Dquality.redis=true -Dsurefire.failIfNoSpecifiedTests=false`：BUILD SUCCESS，33 项，0 失败/错误。日志 `.codex-tmp/remediation-session-rate-tests.log`。
2. `./mvnw.cmd -pl backend/services/agent-integration -am test -Dtest=AgentTempAttachmentCleanupTests,AgentEnvironmentProfileServiceTests -Dsurefire.failIfNoSpecifiedTests=false`：BUILD SUCCESS，4 项，0 失败/错误。日志 `.codex-tmp/remediation-attachment-environment-tests.log`。
3. 变更前端文件 `eslint --fix`：0 错误，17 条警告；最终只读检查待执行。日志 `.codex-tmp/remediation-lint-final.log`。
4. `npm.cmd run build`：日志存在 `built in 3m 48s`，但外层返回 1，且期间有后续修改，不能作为最终成功证据；需捕获原生退出码复核。
5. `git diff --check`：无空白错误，仅 CRLF 提醒。
6. 隔离 Compose MySQL 内事务执行实际过期附件查询后 ROLLBACK：退出 0，无 SQL 错误；不能替代并发清理集成测试。
7. Playwright 实测从第 2 页改为 20 条后回第 1 页，18 条数据；1280×720、1024×768 下分页和批量底栏可见，零选禁用。截图 `outputs/remediation-case-pagination-visible.png`、`outputs/remediation-case-1024.png`。这是源码前端连接旧隔离后端，不能证明新后端制品上线。

仍在进行：最新源码类型检查、最终生产构建返回码、后端打包、新镜像构建/启动/健康、核心 E2E、全部角色/列表矩阵。不得用旧镜像健康或开发服务器截图替代本轮制品验收。

设备授权、统一平台迁移、环境资产引用、搜索快照、评论分页删除、删除恢复、异步功能用例导入、调度/归档、Token 升级等仍按 52 项台账推进，不标记完成。个人直提继续遵循 STRUCTURE_ONLY_V1/AGENT_REPORTED，不引入延期的 OCR、语义核验或 v2 合同要求。

## 最终批次验证更新（优先于上述过程记录）

- 最终前端 `cmd.exe /d /c "npm.cmd run build ..."` 退出 0，包含最新源码类型检查；日志 `.codex-tmp/remediation-build-verified.log`。此前外层返回码异常的构建不作为最终依据。
- 只读 ESLint 退出 0，0 错误、20 条警告；日志 `.codex-tmp/remediation-lint-readonly.log`。
- `test:route-tabs`、`test:permission-resources` 退出 0。路由断言已按最新方案检查个人中心/执行中心及隐藏旧路由，不再要求旧配置页作为可见入口。
- 最新后端专项测试退出 0，共 40 项；日志 `.codex-tmp/remediation-latest-tests.log`。包含真实 Redis 双实例、会话、模型功能拦截、附件下载/清理和配置版本校验。
- `npm.cmd --prefix metersphere-mcp test` 退出 0，22 项通过；日志 `.codex-tmp/remediation-mcp-tests.log`。
- L28 在隔离 MySQL 实际复现 `Illegal mix of collations`；恢复查询改为不依赖表默认排序规则的标识符比较。混合排序规则数据库回归 `AgentQualityFinalizationMySqlTests` 3 项通过，并完成当时源码打包；日志 `.codex-tmp/remediation-recovery-package.log`。
- 权限刷新服务也改为使用配置的 Session namespace。附件关联增加行锁，清理使用 SKIP LOCKED，避免与普通附件关联并发。
- 第一轮镜像构建失败：Alpine 软件源 TLS 中断，无法安装运行库；日志 `.codex-tmp/remediation-image.log`。没有跳过依赖检查。
- `mvnw -pl backend/app clean` 成功，仅清理已核对路径的应用生成目录，消除旧静态资源累积。干净打包、镜像重试和新制品运行验证仍在进行。

下一轮需特别复核：能力开关从未加载变为开启后，顶部菜单缓存是否立即重建；当前只确认默认关闭路径。该边界未标为已通过。

R06 还需对已有平台模型任务的确认/重试/后台调度逐路核对开关，取消和历史读取仍应可用；不能只以创建接口拦截判定整个模型执行链路完成。

## 后续实施批次（继续进行，不代表全量交付）

上一批制品证据已经补齐：干净打包成功，原 Dockerfile 重试构建成功，隔离 Compose 启动且 readiness UP，校验 141 个迁移；生产前端 Excel/XMind 导出 E2E 1 项通过。日志分别为 `.codex-tmp/remediation-clean-package.log`、`remediation-image-retry.log`、`remediation-start.log`、`remediation-e2e.log`。该制品 JAR SHA256 为 `cced57fc26396383efc8941aaac4b2d361dba41d049601c69ee5e36b30af693a`，不包含以下新增修改。

|需求/缺口|本批实现链路|验证状态|
|---|---|---|
|R06 异步菜单与已有任务|顶部菜单监听能力开关；任务创建服务、确认/登录恢复/重试/人工批准、检查点恢复按通道校验；规划、Gateway、Runner 领取入口拦截；运行 Runner 控制返回 PAUSE，保留 CANCEL|新增服务及 AOP 边界测试；本批编译/测试进行中，开启开关、不同角色及真实 Runner 待验收|
|R06 错误契约|Agent 专用异常处理保留 FEATURE_DISABLED，返回 403 和安全提示|新增协议测试；不能只凭通用 Controller 拦截测试判断 Agent 协议正确|
|L32/U10 目录过期写入|树接口返回 updateTime；网页改名/拖拽、脑图携带 expectedUpdateTime，服务在项目树锁内校验；MCP 改名调用共享校验；脑图保存后重新拉取版本|新增缺失/过期版本测试，调整现有集成测试请求；真实网页/MCP 并发、删除/转换及全写入口仍待覆盖|
|登录链路缺接口|运行时发现 /authentication/get-list 无路由，被错误处理为 500；补充当前发行版支持的 LOCAL 方式接口，米多 SSO 继续使用独立 status/bridge API；不存在资源返回 404 和 requestId|新增 404 契约测试；新增路由尚待新制品 HTTP 验收|

生产浏览器已核对旧生成直链转到 `/feature-disabled` 并显示返回工作台按钮，记录 `.codex-tmp/remediation-disabled-snapshot.txt`。这只覆盖默认关闭场景。

本批源码尚未重新完成镜像与 E2E，不得把上一批制品结果当作新增修改的运行证据。整体状态仍为**部分完成**，其余 L01–L52 缺口继续有效。

本批已执行的验证：

追加需求追踪（实施前）：L25 配置过期写入覆盖环境、凭据、登录、模型、页面对象和业务流程。对应 `views/agent/*-profile.vue`、`credential-reference.vue`、`page-object.vue`、`business-flow.vue` 的编辑表单均已发送 version；服务 update 必须拒绝缺失/负数版本，条件更新继续防止并发覆盖。无需数据库迁移；补充实际服务拒绝请求、错误协议及现有创建能力回归。启停、归档及统一平台 API 仍单独追踪，不能用本批 update 校验代替。

R08 补充链路：公共表格 `useTable` → `useListRefresh` → 现有列表 API，增加离线暂停、online 恢复、焦点/可见事件合并以及弹窗/抽屉编辑保护。无后端/数据库变更。独立刷新控制器行为测试覆盖事件合并、未就绪暂停、慢请求不重叠、失败可重试和卸载停止；仍需真实浏览器联网状态和非公共表格列表验收。

R08 非公共表格接入：环境/登录/凭据/模型/页面对象/业务流程六个配置列表使用原 list API，补激活/联网/低频刷新，项目切换关闭编辑并清除旧项目数据，响应按请求代次和项目校验，旧失败不能覆盖新状态；卸载使旧响应失效。无需后端或数据变更。真实多项目、权限和后台变更验证仍需逐页登记。

- `mvnw -pl backend/services/agent-integration -am test -Dtest=AgentExecutionServiceTests,AgentRunner*Tests,AgentExecutionPlanning*Tests,LlmFeatureTests,RestControllerExceptionHandlerTests,AgentExecutionExceptionHandlerTests,FunctionalCaseModuleServiceTests -DskipAntRunForJenkins=true -Dsurefire.failIfNoSpecifiedTests=false`：31 项通过，日志 `.codex-tmp/remediation-directory-model-tests.log`。
- 同一 Maven reactor 补跑 `AgentModelFeatureBoundaryTests,AgentExecutionCheckpointServiceTests,AuthenticationControllerTests`：5 项通过，日志 `.codex-tmp/remediation-boundary-tests-json.log`。认证测试前两次失败分别暴露缺少 JSONAssert 测试依赖、默认内容协商返回 XML；使用现有 JSON 解析器并明确接口 produces JSON 后重跑通过，没有删除失败断言。
- `npm.cmd run type:check`：退出 0，日志 `.codex-tmp/remediation-directory-typecheck.log`；变更前端文件 ESLint 0 错误、1 条已有未使用函数警告，日志 `.codex-tmp/remediation-directory-lint.log`。
- `git diff --check`：退出 0；应用生成目录 `mvnw -pl backend/app clean`：退出 0。
- 生产构建和本批制品运行验收仍在执行。新的目录 HTTP 脚本包含版本缺失、过期、并发写竞争、移动与数据回读断言，不以单元测试替代它。

## 目录与模型制品实测，以及继续整改记录

L24 需求追踪：网页/MCP preflight → AgentExecutionPreflightService → TestAssetCatalogService.resolveContext → 发布资产版本读取 → BLOCKED 结果持久化。预期业务拒绝不得污染外层事务，技术异常仍回滚；验证采用 Spring 真实事务代理和隔离 MySQL 数据库，覆盖资产不存在、发布版本不存在以及技术错误。权限和发布版本要求保持原规则，不以取消校验修复事务。

登录回归发现：已有有效会话打开 `/login/admin` 会一直显示“登录中”，默认登录页存在同样分支。登录页需显式调用现有用户/项目权限恢复及跳转逻辑；登录页识别改为准确路由名，不匹配任意包含 login 的业务地址。无后端或迁移变化；使用已登录真实浏览器验证自动离开登录页，并保留未登录表单/SSO 原规则。

R08/U05 执行中心：任务列表和详情 → 既有 search/task/events/artifacts/human API。列表接入激活及补偿刷新；详情保留 3 秒频率但受可见性、联网、编辑与并发控制；同任务后台刷新不先清空数据，迟到响应不能覆盖新任务。临时列表失败保留并标注旧数据，401/403 清除；切项目清空。无需迁移。待类型检查、真实任务列表/详情和失败矩阵验收。

- 目录/模型批次生产构建、后端打包、镜像和隔离容器启动均成功，readiness UP，迁移版本 3.7.2.104；JAR SHA256 `60d1f90fd3da3352d4da0aacf8741031674e472a8e983d313a3e6b5df53741e9`。日志 `remediation-directory-build/package/image/start.log`。真实生产前端 Excel/XMind 下载及 MCP 导出 E2E 1 项通过，日志 `remediation-directory-e2e.log`。
- 真实 HTTP 目录改名、缺版本 400、过期版本 409、并发同版本更新仅一个成功、移动后旧版本拒绝及父目录回读均通过；测试创建的两个目录已清理。`remediation-directory-http.log`。
- 逐按钮复核发现旧截图仍有右侧裁切，因此旧“首按钮可见”不能作为全底栏通过证据。已取消用例页固定最小宽度，工具栏和底栏允许换行。新源码浏览器检查 1024/1280 宽度所有按钮、条数选择器及裁切祖先，均通过；四档分页、页码/选择重置、偏好持久化、空/单页状态通过。日志 `remediation-responsive-pagination.log`，截图 `outputs/remediation-responsive-all-actions-1024.png`、`1280.png`。该布局修改尚待最新生产制品复验。
- Runner 控制新增 WAIT_HUMAN 等待及非终态 NONE 暂停，保留 CANCEL；Node 启用本机 Chrome 的 13 项测试通过，无跳过，日志 `remediation-runner-controls.log`。Java Runner 专项通过，日志 `remediation-runner-pause-java.log`。
- 六类配置 update 拒绝缺失/负数版本，不再默认服务端最新版本。16 项 Java 回归通过，日志 `remediation-configuration-version-tests.log`。创建测试仍通过；启停/统一平台和真实竞争矩阵未据此标为完成。
- 刷新控制器及实际 hook 5 项测试通过；真实浏览器 focus/visibility 合并、offline 暂停、online 补刷、编辑弹窗暂停、隐藏配置页签暂停通过，日志 `remediation-list-refresh-tests.log`、`remediation-refresh-browser.log`。六类配置列表补项目切换及迟到响应隔离。
- `test:layout-overflow`、`test:api-contracts` 通过。后者首次失败因静态断言仍要求直接读取 app.currentProjectId；已改为校验请求使用捕获的 projectId，并追加过期项目响应丢弃断言，没有删除“必须从已有环境选择”的要求。
- 当前源码生产构建日志 `remediation-refresh-build.log` 仍在执行；本节新增源码不能借用上面的旧 JAR 验收结论。所有未覆盖 L01–L52 仍保留，整体状态为部分完成。

进一步验证与修复：

- `remediation-refresh-build.log` 已退出 0，但之后又修改登录恢复和执行中心刷新，不能作为最终源码制品。
- 有效会话访问 `/login/admin`、`/login`，真实浏览器均自动进入 `/workstation/home`，日志 `remediation-existing-session-login.log`。修复前管理员入口实际停在“登录中”。
- 预检事务修复前 MySQL 3 项测试中 2 项失败，均真实抛出 UnexpectedRollbackException；日志 `remediation-preflight-transaction-before.log`。对 resolveContext/getPublished/latestPublished 的只读业务拒绝设置 noRollbackFor，不取消任何权限/发布版本校验；修复后资产与预检 30 项通过，其中 MySQL 4 项无跳过，日志 `remediation-preflight-transaction-after.log`。其他预检阶段及完整受管任务仍需独立矩阵验收。
- 执行中心真实 search 请求 HTTP 200、业务 code 100200、total 0，日志 `remediation-task-list-browser.log`，仅证明真实空集路径。页面故障注入验证 500 保留并标注旧数据、403 清除旧数据通过，日志 `remediation-task-failure-browser.log`；明确不作为后端业务成功证据。
- 登录后的生产 Vite 构建 `remediation-login-production.log` 因随后新增执行中心修复而主动停止，不计为通过。最新类型检查 `remediation-task-typecheck.log` 和最终生产制品待完成。

- 执行中心类型检查首次发现 3 处可选任务 ID 未正确收窄；修正为检查捕获的 taskId 后，`remediation-task-typecheck-final.log` 对应命令退出 0。任务详情前端夹具验证可见轮询、离线暂停、联网恢复通过，`remediation-task-poll-browser.log`；真实空列表首次加载请求数为 1，消除两个 immediate watcher 的重复请求。
- 最终前端制品构建 `remediation-task-production.log` 正在执行。应用模块 clean 已成功。清理 `backend/services/system-setting/target/test-classes/static` 历史生成资源的操作被自动审批审核以“blocked by policy”拒绝；未重试删除，保留原目录继续构建，实际应用静态资源以本批干净打包及哈希检查为准。

## 当前制品复验与环境引用修复追踪

当前 JAR SHA256 为 `fcd183831932082a98b1fea38bab31da4c88195082a2cd90d35395598db40ab1`。最新类型检查、Vite 生产构建、Maven 打包、Docker 镜像构建、隔离 compose 启动均退出 0，readiness UP，141 项迁移校验通过（3.7.2.104）。日志分别为 `remediation-task-typecheck-final.log`、`remediation-task-production.log`、`remediation-current-package.log`、`remediation-current-image.log`、`remediation-current-start.log`。

扩展现有导出 E2E，按需求检查四档分页、零选择按钮常驻且禁用、选中后按钮集合不变、1024/1280 全部按钮及裁切祖先边界，再运行真实 Excel/XMind、原生 MCP 和 Node MCP 导出：1 项通过，52.6 秒，`remediation-current-e2e.log`。生产页面刷新合并、离线暂停、恢复、编辑保护、隐藏页签暂停通过，`remediation-current-refresh-browser.log`；目录版本与并发 HTTP 矩阵通过，`remediation-current-http.log`。这些证据仅覆盖对应需求，整体仍部分完成。

L23 实施前追踪：平台配置编辑 → environment-profiles create/update → 明确的 environmentAssetId → 同项目已发布 ENVIRONMENT 校验 → 受管预检冻结真实资产。environmentId 保留为凭据范围标识，不改动现有凭据或历史快照；个人直连仍不要求发布资产。新增可空绑定字段及存量映射迁移，仅映射确实存在且同项目的旧环境；悬空旧 ID 不伪造资产。界面允许搜索绑定，明确未绑定仅可个人直连；受管入口缺绑定提前返回可理解的阻塞原因。验证覆盖无绑定个人保存、有效/跨项目/未发布绑定、旧凭据标识稳定、存量迁移、预检冻结，以及版本冲突与权限。

L23 实现补充：预检与创建共用 `AgentEnvironmentAssetBinding`，禁止创建阶段重新把凭据范围 ID 当作资产；保留冻结版本，冲突或多个环境引用拒绝。预检后平台版本或绑定变化必须重新预检。迁移 105 保留旧 ID，只映射真实同项目环境，不自动发布旧环境；旧客户端省略 environmentAssetId 保留已有绑定，空字符串才解除绑定。普通配置不绑定即可保存；受管任务仍要求真实资产及原有权限、发布版本。

新增测试首轮因 Mockito ArgumentCaptor 泛型编译失败；第二轮 26 项中 25 项通过，1 项因独立单元测试未初始化 IDGenerator 出错。已在该测试中显式模拟 ID 生成器后重跑，未更改生产校验。MySQL 存量迁移和预检事务测试在第二轮均真实执行、无跳过。前端 ESLint 0 错误（保留已有 unused handler 警告），API 合同检查通过；真实源码页面已请求已发布环境目录 HTTP 200，`remediation-environment-binding-browser.log`，没有据此声称保存链路已验证。最新类型检查、测试复跑、迁移 105 制品及真实保存回读仍在进行。

- `remediation-environment-binding-tests-recheck.log`：26 项全部通过、0 跳过。复跑仅修改测试 IDGenerator 夹具，因此使用 `-Dmaven.main.skip=true` 复用上一轮已经成功编译且未变化的生产类；测试源码重新编译，未跳过测试。真实 MySQL 包括存量映射 1 项、预检事务 4 项。
- `npx.cmd vue-tsc --noEmit --skipLibCheck --incremental --tsBuildInfoFile ../.codex-tmp/remediation-front.tsbuildinfo` 退出 0，`remediation-environment-binding-typecheck.log`。当前机器内存不足时已停止自己创建的 15177 Vite 与 msp-export-20261021 的 app 容器，未停止其他环境或数据库；生产构建后重新启动验收。
- 旧版/新建任务入口共用预检安全提示，显示可操作原因及 traceId，避免直接向用户展示内部枚举。

L18 实施前追踪：网页/原生/Node 共享导出服务 → Excel/XMind 生成 → 本机任务专属临时目录 → finally 清理及后台重试。创建目录即带 taskId，不依赖进程内失败队列；重启后可扫描。仅清理明确属于本功能的目录，不跟随符号链接；PREPARED 任务保留，终态可重试，无任务记录的目录超过 24 小时才清理。删除失败不更改已经生成的导出结果。无需数据库迁移；测试实际目录/文件、活跃保护、终态回收、孤儿宽限、删除失败重试及陌生目录保护，再复跑真实 Excel/XMind 导出。

本批整包编译及测试 `remediation-environment-package.log` 退出 0：环境/预检 27 项、导出 15 项全部通过，0 跳过（真实 MySQL 共 12 项）。首次制品检查因 `skipAntRunForJenkins=true` 跳过前端复制而发现 JAR 缺失 static/index.html，未构建或启动该 JAR 的镜像。正在以原有资源复制流程重新打包，复用未变化且已完整编译/测试的 Java 类；不能把首次 Maven 退出 0 直接当作制品验收通过。

L18 新导出目录使用 `ms-case-export.<taskId>.<UUID>`，重试不依赖进程内列表。历史 `tmp_*` 与其他模块共用且无法可靠归属，不自动删除，仍列为历史清理遗留。只读 ESLint 已重新执行，退出 0，1 条已有 unused handler 警告，日志 `remediation-environment-lint-readonly.log`。

## 环境绑定与导出清理制品验收

JAR SHA256：`af839db0d325cf0d5324c56c50704d68a6aaff735203a2ca3a2d3da22b6a3506`。重新打包恢复原前端复制流程，`remediation-environment-package-static.log` 退出 0，JAR/index.html 与 frontend/dist 摘要一致。正式 Dockerfile 的默认 Alpine CDN 两次 TLS 下载失败；新增 HTTPS 软件源参数，将系统依赖安装前置以复用缓存，使用 dl-2 同版本软件源构建成功，`remediation-environment-image-mirror.log`。没有关闭 TLS 或 APK 签名校验。

隔离 compose 启动成功、readiness UP，142 项 Flyway 校验通过并升级至 3.7.2.105，`remediation-environment-start.log`、`remediation-environment-container.log`。真实配置 HTTP 全部断言通过：无资产保存、未发布拒绝、发布绑定、过期写 409、凭据范围不可替换、错误引用拒绝、旧请求保留绑定、显式解绑；`remediation-environment-http.log`。真实页面新增、提交、刷新回读、停用通过，`remediation-environment-editor.log`，截图 `outputs/remediation-environment-editor-saved.png`。可重跑脚本已保存至 `scripts/acceptance/`，不只依赖临时文件。

首次新镜像导出 E2E 失败：清理夹具由 root 预先创建了本应由 appuser 创建的临时目录，导致 AccessDeniedException；日志 `remediation-environment-e2e.log`、`remediation-environment-runtime-error.log`。仅修正自己创建的目录归属后，同一镜像 E2E 1 项通过（32.6 秒），`remediation-environment-e2e-recheck.log`，未修改业务代码或测试断言。

定时清理真实容器验收通过：过期孤儿移除，新目录和其他功能目录保留，测试夹具随后清理。首次 PowerShell 文本管道追加 CR 导致断言通过后退出 127，改为 cmd 原始文件重定向后退出 0，`remediation-environment-temp-cleanup-recheck.log`；仓库脚本强制 appuser 运行，并固定 shell 文件 LF 换行。完整受管任务、统一平台表单/登录验证和其他未覆盖需求仍未封板。

L17 实施前追踪：新增独立“导出记录”入口，不依赖勾选用例 → 分页历史 API → 本人/项目授权与完整总数 → 分页、刷新、重新下载/停止。旧 recent API 保持兼容并优先恢复活跃任务，避免一个长任务被 20 条后续完成记录挤出。无需迁移；覆盖超过 20 条、跨用户/项目隔离、页码边界、零选择入口、过期下载及状态竞争。重试来源关联和请求瞬间快照仍单独保留，不能用历史分页完成代替。

L17 代码及专项验证：`ExportHistoryDrawer.vue` 独立入口调用 `GET /functional/case/export/tasks/history`，后端按当前用户和项目授权后分页；支持 10/20/50/100、总数、越界回退、刷新、下载及停止。401/403/404 清除记录，其他失败标注旧数据，关闭/切项目丢弃迟到响应。刷新 hook 显式允许只读抽屉，不改变其他编辑页面的暂停规则。`remediation-export-history-refresh-tests.log` 5 项通过；`remediation-export-history-tests.log` 17 项通过、0 跳过，其中真实 MySQL 9 项，新增 26 条历史完整翻页及非法参数测试。`remediation-export-history-types.log` 类型检查退出 0。扩展现有导出 E2E 检查零选择入口及历史下载，生产构建及新制品运行验证尚在进行，不能借用前批镜像结论。

下一条 L17 关联重试实施前追踪：导出记录中的重试操作、原生 MCP 和 Node MCP → 共用重试服务 → 本人/当前项目导出权限 → 仅终态失败/停止/过期任务，使用原筛选及字段重新导出当前内容 → 新 taskId、稳定 requestId、持久化 sourceTaskId。保留原任务和审计；缺失历史参数明确拒绝，不能悄悄导出全部。UI 必须说明重新读取当前内容并在响应丢失后沿用同一 requestId。该项尚未实施，不代表已经满足关联重试或旧内容重放。

L17 首轮制品 `e45dc13aa0ab0b267b5e2aadd2e8c7a54a2986680d626399f61cf7bc312c86dd` 打包、静态资源哈希核对、镜像及容器健康均通过。真实历史 API 30 条完整跨页、无重复，`remediation-export-history-browser.log`。但截图复核发现 10 行内容将分页推到首屏外，不能仅以抽屉边界通过判 UI 完成；已改为固定 footer，并追加分页在视口内断言。首轮 E2E 因隐藏抽屉残留的分页与用例分页标题重复而严格定位失败，`remediation-export-history-e2e.log`；测试明确限定主表底栏，抽屉关闭卸载。前端修正需新制品复验，不能沿用首轮制品结论。

关联重试代码已接入独立记录抽屉、`POST /functional/case/export/tasks/{taskId}/retry`、原生和 Node `functional.export.retry`，共用 `FunctionalCaseExportRetryService`。迁移 106 增加 source_task_id；仅服务端设置关联，客户端不能伪造来源；普通请求不新增空字段，保留原幂等参数序列化。浏览器 sessionStorage 保存未确认的重试 requestId。`remediation-export-retry-tests.log`：20 项通过、0 跳过，其中 MySQL 12 项，覆盖迁移、原参数、关联、重复请求、他人任务、成功/活跃拒绝、旧参数缺失、停止/过期及只读来源字段。Node 22 项通过，`remediation-export-retry-node.log`；新包打包及隔离安装成功。最新前端类型、构建、新迁移制品与网页/两种 MCP 的真实重试验收仍在进行。

下一条 L16 内容冻结实施前追踪：网页及两种 MCP 现有导出入口 → 任务容量与幂等预留 → 请求返回前完成一致性读并保存实际内容快照 → 有界队列只生成文件，不再读取当前正文。已有异步工作者才冻结的时机不满足排队修改隔离。冻结失败仍落可查询安全终态，停止和过期不得被迟到冻结覆盖；幂等重放不再重复冻结。需验证排队期间修改、快照失败、停止竞争、保存及输出内容一致，不把保存筛选参数当作快照。本项尚待实施。

关联重试制品 `aa19f9231a41142fcecc919892ecf7db95dbf32edd6cb147029d0070b0ec4724`：类型检查、生产构建、打包、静态资源哈希核对、镜像、隔离启动与 readiness 均通过。打包复用本批 20 项测试对应且未再修改的已编译 Java 类，重新复制前端，未将跳过测试的打包计为测试证据。迁移从 105 升至 106。日志 `remediation-export-retry-{types,build,package,image,start,migration}.log`。

同制品 E2E 1 项通过（42.9 秒），覆盖原分页/批量按钮、历史下载、Excel/XMind、网页重试、原生及已安装 Node MCP 的关联幂等重试、撤销后拒绝下载/重试，`remediation-export-retry-e2e.log`。真实后端创建后中断响应，再点击返回同一 requestId/taskId，`remediation-export-retry-loss.log`。30 条历史完整分页、固定分页底栏在 1024/1280 可见；但截图发现最后一个行操作仍横向裁切，故 UI 未封板。已收紧列宽并增加逐按钮祖先裁切断言，需下一制品复验，不能沿用抽屉边界通过结论。

L16 代码已调整：容量及幂等预留后同步冻结，文件生成入队后只消费冻结对象；Excel/XMind 均保存按选中顺序排列的 caseId/versionId/updateTime。保留原四参数 start 兼容调用；冻结异常落安全终态，停止后迟到冻结不会重新启动工作。新增真实 MySQL 阻塞工作者后排队、创建返回时快照已保存、修改源内容不改变输出、重放不再冻结、冻结空集和停止竞争测试；当前测试与新制品验证进行中。

## 导出历史、重试与快照本批验收

当前已运行制品 JAR SHA256 `bfe1c844ee8831622f493fcfd81dadc566f2a557c176667d6a39bc77e251f6ce`，镜像 `sha256:5ba291f66b6f07e0190cac890eb0c04c579ebd0a4cdb8308a2b919abd96553b0`。应用生成目录经绝对路径核对后执行 Maven clean，重新编译/打包并核对 JAR/index.html 与 dist 一致，避免累积旧静态资源。未删除此前被阻止清理的 system-setting 测试资源目录。

- `mvnw.cmd -pl backend/services/case-management -am test -Dtest=FunctionalCaseExport*Tests -Dquality.mysql=true -DskipAntRunForJenkins=true -Dsurefire.failIfNoSpecifiedTests=false`：最终 23 项通过、0 跳过，其中 MySQL 14 项，`remediation-export-snapshot-tests-final.log`。加入已创建重试优先重放，不依赖旧参数或当前字段重新校验；仍复核授权并拒绝其他来源的请求键碰撞。
- 最新 vue-tsc、只读 ESLint、API 合同、Vite 生产构建、干净 Maven package、Docker 构建、Compose 健康和 readiness UP 均通过。日志 `remediation-export-snapshot-{types,lint-readonly,contracts,build,clean,package,image,start}.log`。打包使用 `-Dmaven.test.skip=true`，测试证据来自前述实际执行，不把打包当测试。
- 当前制品 E2E 1 项通过（41.1 秒），`remediation-export-snapshot-e2e.log`：四档分页和批量按钮、历史下载、Excel/XMind、网页/原生/已安装 Node MCP 关联重试及撤销拒绝；另在导出返回后通过真实 MCP 改名，解压两种文件验证旧名称存在、新名称不存在。确定性的排队竞争由 MySQL 测试补充，不声称文件测试单独证明排队时序。
- 独立页面脚本实际点击第 2 页及 20/50/100/10 条，核对请求和界面、重置第 1 页；49 条历史完整遍历、无重复，1024/1280 固定底栏及每个行操作均通过裁切祖先检查。`remediation-export-snapshot-history-controls.log`，截图 `outputs/remediation-export-history-1024.png`、`1280.png`，已人工查看。
- 真实创建后中断响应，浏览器沿用同一请求标识且返回同一 taskId，再次通过，`remediation-export-snapshot-loss.log`。数据库读取最近五个真实导出均有 revisions/versionId，`remediation-export-snapshot-revisions.log`。

上述仅覆盖列明的 L16/L17 子要求。R04 的 WS 一次性票据、多副本通知、旧 fileId 兼容、旧文件保留/回收及完整容量故障矩阵仍未完成；其他 L01–L52 未覆盖内容继续保留。整体状态仍为部分完成。

L19/E05 通知实施前追踪：当前网页导出 → 已认证且经过 CSRF 的票据接口 → Redis 60 秒一次性票据，绑定会话/用户/项目/任务/Origin/用途 → 专用 Shiro 握手过滤器原子消费 → 连接仅接收本人任务状态。票据放入 WebSocket 子协议请求头，URL 不含 Session/Agent Token 或票据；过滤后移除内部请求中的票据头值，不记录凭据。连接持续复核会话绝对期限及权限，按共享数据库读取状态，允许多连接和跨副本；WS 只促使网页重新查询 HTTP 状态，原轮询始终保留。默认不启用可选通知加速，启用时必须显式配置允许 Origin；隔离验收启用，并覆盖重放、过期、错误 Origin/任务、会话撤销、Redis 故障、双副本和 WS 失败下的 HTTP 导出。该项尚未实现，不以设计代替证据。

L19/E05 新增票据服务、专用握手过滤器、多连接处理器及网页通知监听，接入当前导出的独立 HTTP 轮询；部署代理补充升级头，隔离测试代理支持同路径升级。首次测试夹具误用私有 SessionUser 构造器，编译失败日志 `remediation-export-ws-tests.log` 保留；改用实际工厂后 10 项通过、0 跳过，其中 5 项真实 Redis，`remediation-export-ws-tests-recheck.log`。前端通知生命周期 4 项通过，`remediation-export-ws-frontend-tests.log`。以上尚不是新制品浏览器或双应用进程验收；完整类型/构建/容器及真实 WS 验证进行中。

L18 补偿边界实施前追踪：超时/停止任务的非中断上传可能晚于首次定时清理结束；旧逻辑 cleanup_done=1 后，迟到上传失败留下的对象不再扫描。本次让迟到工作者退出时重新登记失败/停止任务的清理责任，并用真实 MySQL＋可控存储故障验证下一轮重试。保留业务终态，不扩大删除路径，不触碰旧记录保留政策；进程在上传成功后突然退出及对象库存清理仍需独立故障验收。

本批合并回归 `remediation-export-ws-regression.log`：36 项通过、0 失败、0 跳过，其中真实 MySQL 16 项、真实 Redis 5 项；已包含迟到上传后异常/删除失败的清理重登记和专用过滤链约束。执行 `mvnw.cmd -pl backend/services/case-management -am test -Dtest=FunctionalCaseExport*Tests,CaseExportNotification*Tests,CaseExportTicketFilterTests -Dquality.mysql=true -Dquality.redis=true -DskipAntRunForJenkins=true -Dsurefire.failIfNoSpecifiedTests=false`。

本批运行 JAR SHA256 `8ccd0ada2a62255aeafafa4c7e205b7c3f2ead03288bae4ed6d47ea8389d6ed4`，镜像 `sha256:a986028e31c3a3bdc8fd90a632ffdb380ec5547167a85bec3304c8779a0190c9`。干净应用打包、包内前端哈希核对、镜像、Compose 健康及 readiness UP 通过；日志 `remediation-export-ws-{clean,package,image,start}.log`。本批未新增迁移。

HTTP 回退 E2E 最终 1 项通过（31.0 秒），`remediation-export-ws-e2e-fallback-confirmed.log`。首次综合导出通过并不足以证明阻断实际发生；增加连接尝试计数后失败，保留 `e2e-fallback/e2e-fallback-recheck/e2e-diagnostic/e2e-fallback-final/e2e-gated` 日志。诊断显示票据 200/启用正常，发现测试延迟规则漏匹配 GET 时间戳，以及 hash 导航不会执行新 addInitScript。最终在当前页面安装阻断，等待一次真实状态响应、确认连接构造实际被阻断且没有 WS 建立，再放行原始响应，完整导出回归通过。未 Mock 任务成功状态，也未修改业务代码以绕过断言。

网页增加切项目/卸载/迟到响应保护，通知独立有界重连与 HTTP 读取去重。最终类型检查、Vite 构建、4 项通知生命周期测试、API 合同通过；只读 lint 0 错误、13 条既有页面警告。日志 `remediation-export-ws-{types-complete,build-final,frontend-tests-final,contracts,lint-verified}.log`。Nginx 配置在真实容器 `nginx -t` 通过（仅语法验证），`remediation-export-ws-nginx.log`。通知使用独立调度线程，避免占用任务恢复/清理调度；新制品容器及跨副本运行验收仍待完成。

L19/E05 本批真实通知验收完成：同 JAR/版本启动独立 peer（18086/17076，readiness UP），仅共享数据库/Redis/对象存储。`remediation-export-ws-browser-concurrent.log` 的 `### Result` 全部通过：匿名/错误 Origin 拒绝、本人任务消息匹配、公开子协议、URL 无凭据、重放/跨任务拒绝、错误任务不消耗票据、A 签发 B 消费、跨副本同时消费恰一成功、独立连接均收到通知。`remediation-export-ws-browser-logout.log`：新 context 登录、签发后退出，peer 拒绝票据，原浏览器会话仍可查询。短任务终态通知由真实浏览器证明；持续 RUNNING 多连接/版本顺序由专项处理器测试补充，不宣称完成所有高负载/断网矩阵。最初 peer 尚未启动的诊断失败日志 `remediation-export-ws-browser-first.log` 保留。临时 peer 已停止并移除，未删除共享卷；为节省内存临时停下的专项 MySQL 已恢复。

本批已验证通知子链路及迟到工作者清理重登记，不代表 R04 或全方案完成。旧 fileId 网页兼容、旧记录保留回收、突然退出后的孤立对象库存对账、100,000 条容量和完整故障矩阵继续列为未完成；U04/U09–U15、设备授权、Token/OAuth 等其余缺口仍按原台账继续实施。

## U11 / AC10、AC27 评论生命周期实施前追踪

|需求|网页入口|共享服务/接口|数据与权限|验收计划|
|---|---|---|---|---|
|根评论和回复分页|用例详情内嵌评论、评论页签|新增授权分页接口；原生/Node comments.list 同步分页参数与返回元数据|稳定 createTime/id 排序，10/20/50/100，有界读取；旧列表超过兼容上限明确提示使用分页，不能静默截断|超过一页、根/回复独立分页、总数、跨用例/项目、迟到响应与错误状态|
|作者编辑/管理删除|共用评论操作菜单|共享作者/管理授权与版本校验，新增 POST 删除及 MCP comment.delete|编辑仅作者且有评论权限；删除为作者＋评论权限，或同时拥有现有 FUNCTIONAL_CASE:READ+COMMENT 和 FUNCTIONAL_CASE:READ+DELETE；不按角色名称放行|非作者编辑拒绝、管理删除允许、撤权/缺版本/过期版本拒绝，并发只一成功|
|墓碑与审计|删除提示说明回复保留，墓碑显示；移除编辑/删除操作|共享删除服务，不级联删除回复|增量迁移保留父子关系、删除元数据，正文清空；审计只留前后摘要/版本，避免泄露正文|父删除仍可读回复，回复删除不影响同级，不能编辑墓碑，未知用户头像安全回退|
|MCP 一致性|个人 Agent 配置显式授权|新危险 scope CASE_COMMENT_DELETE_V2，不被 AGENT_ALL 隐式包含；原生/Node/技能说明同步|沿用 AgentOperationExecutor 幂等和每次重放重新授权|两客户端相同请求不重复删除、错误项目/作者/范围拒绝|
|通知可靠性|沿用站内通知/项目通知配置|当前 @Async 直接发送会吞异常，需另补事务提交后的持久化发送与重试|按事件/通道/接收人去重，不能把异步方法返回当作送达；外部通道不确定结果单独处理|事务回滚不发送、发送失败恢复、重复消费不重复通知；本行尚未实施|

本批先落实评论读写生命周期及客户端链路，通知 outbox 继续后续同一任务实施，不据此标记整个 U11 完成。个人执行证据基线保持不变。

## 2026-10-22 恢复实施（主流程集中验收）

用户要求继续完成剩余工作；以需求完整覆盖为标准，完成开发后集中验证主流程，不追加穷举异常矩阵。OAuth 等来源未明确的扩展不新增；现有 STRUCTURE_ONLY_V1 / AGENT_REPORTED 不变。源码未提交。

|需求|本次实际修改|当前证据/剩余|
|---|---|---|
|U11 评论|三个入口共用根/回复分页和版本删除；墓碑保留回复；新增显式 MCP 删除范围；发布中禁用重复操作，切换用例丢弃旧响应|专项 Maven 19 项通过，含真实 MySQL 8 项；`remediation-comment-resume-tests.log`。网页、Node 及最终制品仍待集中验收|
|U11 通知|迁移108；同事务持久化加密的事件/通道/接收人通知，站内通知与出箱状态原子提交，失败重试；外部旧通道只能标 SUBMITTED，异常/中断标 UNCERTAIN，不声称已送达、不盲目重发|MySQL 验证事务回滚和重复消费不重复发送；外部失败诊断/确认重试入口仍待补|
|U04 平台操作|在平台列表进入限定该平台的登录与角色配置，复用现有服务，新增时自动填关联对象；关闭后刷新默认配置选项|仅源码，尚不等于统一平台 API、迁移/归档/真实登录验证全部完成|
|U10 完整提取快照|迁移109；快照创建/分页/释放工具，固定内容，15分钟到期，后续读取重新授权，范围删除失效；普通 search 仍实时|新增源码，未编译验证。默认10条、最大100；当前快照限制10000条/50MiB/每Token5个，明确超限拒绝；不冒充100000条导出容量验收|

本次不重复完整镜像构建。相关编译与专项测试用于发现实现错误，最终统一前端构建、打包、迁移、容器健康及主流程验证。

U12 实施前追踪：MCP delete_preview → 用户登录后的独立删除确认页 → 本人决策记录 → MCP delete/batch_delete → 共享软删除服务；默认CURRENT、ALL显式，目标版本和有效期绑定，取消/等待/停止决定不可由MCP伪造。恢复复用回收站服务，但增加明确目标版本及合法目录选择，不复活任务。新增显式危险scope，不纳入旧AGENT_ALL。计划验证预检→网页确认→删除→恢复这一主流程和旧确认拒绝；已有网页删除入口需同步核对，不能只补MCP。

U13 导入实施前追踪：现有 Excel/XMind 上传与预检入口 → 共享解析器冻结逻辑用例、模板摘要、覆盖目标版本 → 用户提交 → 持久化任务和逐项事务 → 状态/停止/失败项重试；网页和两种 MCP 共用服务。复用解析、字段校验、模块建立和用例写入服务，不另做简化格式。一次逻辑用例与其任务结果同事务，重启只处理未提交项；用户撤权、模板或覆盖目标变化明确失败。文件 50MiB、逻辑用例 10000、压缩包体积和解析时间有界。集中验收 Excel/XMind 预检→提交→结果、覆盖版本保护、停止/重试，不新增穷举异常矩阵。

### 2026-10-22 10:20 源码与专项结果

- 删除确认已接现有用例列表单条/批量入口、独立确认页、共享软删除和 MCP 恢复。`remediation-deletion-tests-recheck.log`：11 项通过，含 3 项真实 MySQL；首轮漏传新增响应字段的编译失败已修复，失败日志保留。后续补充共享删除服务的活跃任务阻断，防止旧入口绕过，待合并回归。
- 新增导入任务 112：复用 Excel/XMind 解析和写入，冻结文件/逻辑条目/模板摘要/覆盖版本，提交、分页、停止、重试；成功条目不重复执行。网页去除模拟进度，显示服务端逐项状态，可恢复最近任务。原生/Node 加入五个工具及显式 CASE_IMPORT_V2。`remediation-import-tests.log`：21 项通过，0 跳过，其中评论 MySQL 8、导入 MySQL 2、库生命周期 MySQL 3、权限 8。导入测试验证真实任务表事务与恢复，但业务写入使用共享服务的测试替身；真实解析和真实页面尚待最终制品验收，不能混称 E2E。
- `remediation-import-node.log`：23 项通过；`remediation-import-types.log` 类型检查退出 0；本批选定页面 ESLint 修正后退出 0。这些不代替最终生产构建、迁移、容器及主流程验收。
- 评论通知已新增状态/手动确认重试入口与作者/管理员权限、版本检查；外部无回执明确显示 SUBMITTED/UNCERTAIN。删除用例后不继续发送通知。
- 附件 113：存储写入前独立事务记录对象 ID，外层事务持锁；元数据回滚或进程退出后保留清理责任，清理失败继续保留。已有正式元数据的对象不删。该新增代码与测试尚待运行。
- 定时 114：默认 SKIP 与方案一致；领取计划时同事务登记持久化派发，重启后可继续，按定义串行并沿用任务幂等键。尚待专项验证和实际适配器主流程验收，不能标记 U14 完成。

继续未闭合项包括统一平台配置剩余整合、业务理解/数据资产归档、浏览器会话授权补接及原台账中未覆盖的兼容/查询写入点。OAuth 和证据内容增强仍不新增；最终完成判断以实际需求覆盖和主流程结果为准。

U15 实施前追踪：执行详情/原生与 Node 归档工具 → 当前用户和项目/来源执行授权 → 持久化来源运行或个人提交＋产物类型去重 → 现有来源文档、文件数据资产及版本/来源元数据。默认只整理已有执行事实，推断不冒充事实；可补充有来源对象 ID、复用条件与清理状态的业务数据，没有数据时明确记录。归档失败独立于执行结果，可单独重试；生成文件使用稳定服务端身份，事务回滚重试不累积随机对象。验收真实执行来源→文档/数据资产→重复请求不重复归档、字段脱敏与失败重试主流程。

### 2026-10-22 10:54 专项回归与后续修改

- `remediation-archive-tests.log`：28 项通过、0 失败、0 跳过，包含评论 MySQL 8、库生命周期 MySQL 5、导入 MySQL 2、附件 3、scope 8、调度服务 2。调度测试仍是已有校验测试，不能用来证明持久化派发或真实适配器已经验证。
- `remediation-archive-types.log` 类型检查通过；`remediation-archive-lint.log` 无错误，执行详情已有未使用方法警告 1 条。`remediation-archive-node.log` 23 项通过。
- `remediation-archive-import-final-tests.log`：归档/导入后续回归 7 项通过、0 跳过，完成于 10:53:11。覆盖数组字符串脱敏、独立业务数据提交人记录、归档重试不重复文档；业务资产写入仍使用测试替身，尚不能替代实际制品验收。
- XMind 移除按文本单词 `case` 计数的 800 次限制，改在实际用例解析处限制 10000 条；解压使用独占临时目录，失败时清理该目录。上述改动已随专项编译通过；真实文件导入仍待浏览器主流程验证。
- 此后补充导入并发同请求的锁内重查、导入结果每页 10/20/50/100 选择，更新 Node README 和服务端技能包说明。这几项后续修改待最终编译/验收。
- 当前仍未声称整体完成：浏览器独立 Session 设备兑换尚未实施，统一平台 API/归档及真实登录验证仍有缺口，调度适配器、旧导出兼容和实际前后端主流程仍需核对。未新增 OAuth 或个人证据内容核验。

### 集中制品验证进行中

- `remediation-final-frontend-build.log`：Vite 生成生产资源，记录 `built in 4m 15s`；PowerShell 包装层返回 1，日志只有 npm 环境配置、旧依赖 eval 等警告，尚不将包装命令计为退出成功。后续校验制品前端摘要并实际打开页面。
- `remediation-final-node.log`：23 项通过；`remediation-final-node-pack.log` 和 `remediation-final-node-install.log`：0.7.0 包生成并在隔离目录安装成功。
- 新迁移 107–115 的建表明确使用与旧库一致的 `utf8mb4_general_ci`，避免评论通知等联表在真实旧库触发排序规则冲突；真实 MySQL 测试库也明确使用该配置。
- 首轮 `remediation-final-package.log` 被新增调度恢复测试拦截：隔离测试未初始化静态 ID 生成器，导致预期恢复断言失败。已在该测试作用域内提供 ID，未修改生产生成器。`remediation-final-package-recheck.log` 为后续重跑，结果待补；不能把首轮视为成功。

### 2026-10-22 实际容器主流程与集成修复

- `remediation-final-package-recheck.log`：17 项通过、0 跳过，完整打包成功。后续真实运行发现并修复评论工具的 `@Resource` 按名称注入错误；迁移 107–115 已在隔离容器成功执行。
- U11：`remediation-final-comment-http.log` 验证创建、编辑、回复、删除父评论后回复保留、删除回复及通知查询。外部通知没有回执时仍只显示提交或不确定状态，不冒称送达。
- U13：实际后台导入暴露请求语言与工作线程语言不一致、后台线程误取网页登录 Session 两个问题。预检保存解析语言，工作线程恢复语言；组织从真实项目获取，消除对请求 Session 的依赖。`remediation-import-actor-package.log` 3 项 MySQL 测试通过并完整打包成功。`remediation-final-import-filled-http.log`：Excel 1 条、XMind 3 条通过真实解析和持久化，重复预检与提交不重复创建任务或条目。测试模板必填项已填写，未将模板占位值的拒绝误记为产品失败。
- U12：`remediation-deletion-ui-dev.log` 验证本人网页确认后软删除完成，完成后按钮保留且禁用。同一测试用例通过真实回收站接口恢复成功。浏览器开发资源验证不代替最终生产资源验收。
- U15：`remediation-final-archive-http.log` 验证真实 MinIO 文件和项目文档/数据资产写入、去重及敏感字段脱敏。来源明确为隔离环境合成历史记录，不声称执行过真实业务测试。文档 ID `fe287a8f-6238-44f3-a633-493ddf7e6fc3`，数据资产 ID `arc-6c87dc92-3d90-3175-b2ec-bc82ce220cac`。
- U04：平台角色抽屉的两个组件同时读取环境列表，被通用重复请求取消器互相取消，出现请求失败提示。共享环境列表请求改为允许并发，组件自身继续丢弃过期响应；`remediation-platform-ui-dev-recheck.log` 和截图验证无错误提示。凭据表增加横向滚动，避免窄抽屉表头挤压。
- U16：安装后的 Node 客户端调用原生 MCP 时发现上传完成被通用 `requestId` 校验拦截。上传、导入、快照、删除确认、归档等已有独立任务/资源协议的工具改为执行各自服务的幂等和当前授权检查；新增分发层回归测试。此修改尚待最终打包与原生客户端复验，先前失败日志保留。
- 评论通知、删除确认和导入报告改用统一分页偏好，默认 10 条，支持 10/20/50/100。最新前端仍待生产构建复验。浏览器导入首次因页面路由未加载到目标页面而超时，不能计为通过。
- 当前容器 JAR 摘要 `01b85ceb0a828b0cebbeccf4a86780438e3e969fdf0b8b181455f897fff6027c`，包含导入修复，不包含上述最新 MCP 分发及前端修复。整体状态仍为部分完成。

### 2026-10-22 12:26 最新制品构建

- `npx.cmd vue-tsc --noEmit --skipLibCheck --incremental --tsBuildInfoFile ../.codex-tmp/remediation-front.tsbuildinfo`：退出 0，日志 `remediation-latest-types.log`。
- `node node_modules/vite/bin/vite.js build --config ./config/vite.config.prod.ts`：退出 0，3 分 32 秒，日志 `remediation-latest-production-build.log`；仍有既有依赖 eval 等构建警告。
- `mvnw.cmd -pl backend/app -am package -Dtest=AgentMcpStreamableServiceTests,AgentCaseImportMySqlTests,AgentLibraryLifecycleMySqlTests -Dquality.mysql=true -Dsurefire.failIfNoSpecifiedTests=false`：完整打包成功，21 项通过，0 失败、0 跳过；日志 `remediation-latest-package.log`。其中导入 MySQL 3、库生命周期 MySQL 8、MCP 分发 10。
- 删除/恢复补充旧数据兼容：没有 ref_id 的记录在 ALL 范围下按自身 ID 处理；MySQL 参数化回归覆盖 CURRENT、ALL 空值/空字符串，验证不会误选其他无关联 ID 记录。
- 上述仅为相关回归和制品构建证据，不能替代未执行的全部历史模块集成回归或尚未闭合的需求。下一步运行镜像与生产页面、安装客户端主流程验证。

### 2026-10-22 12:35 最新镜像主流程结果

应用 JAR SHA256：`9703db9e05431d667971df9dc279686b719839e4c1414c7b1735efba1c68197a`。`remediation-latest-image.log` 镜像构建退出 0，打包静态首页与当前生产前端摘要一致；`remediation-latest-start.log` 容器启动健康检查通过。仅操作隔离项目 `msp-export-20261021`，未部署生产、未提交或推送代码。

|需求链路|本轮实际结果|证据|
|---|---|---|
|R01/R05 用例列表→统一表格分页/选择操作|40 条总数和每页 10 条可见；零选导出常驻禁用，单选和多选启用，清空后禁用|`remediation-latest-list-ui-verified.log`；`outputs/remediation-list-selection-production-20261022.png`|
|U04 平台列表→登录/角色抽屉→环境配置 API|生产资源实际进入平台抽屉，无请求错误和错误提示；保留平台限定和表格分页|`remediation-latest-platform-ui.log`；`outputs/remediation-platform-role-drawer-production-20261022.png`|
|U09 安装后的 Node 包→原生 MCP→上传意图/完成→授权下载|准备和完成重复调用返回相同资源，真实下载摘要一致；测试 Token 最后撤销|`remediation-latest-attachment-native.log`|
|U10 安装后的 Node 包→原生 MCP→查询快照服务|39 条用例真实快照创建、分页、释放通过；测试 Token 最后撤销|`remediation-latest-snapshot-native.log`|
|U13 真实网页上传→预检→提交→任务状态/数据库|生产页面 Excel 导入 1 条成功；报告显示总数、逐项结果、分页；列表数量从 39 更新为 40|`remediation-latest-import-ui-recheck.log`；`outputs/remediation-import-ui-success-20261022.png`|

网页导入首次脚本选到了页面中其他上传控件，列表检查首次选到了隐藏原生 checkbox；修正为导入弹窗内上传控件和实际可见 checkbox 标签后重测通过。失败日志保留，不能将这几次脚本定位错误描述为产品功能缺陷，也不能用 CLI 进程退出码代替其 `### Result`。

本批主要文件与对应关系：

- `frontend/src/api/modules/ai-execution.ts`、`views/agent/credential-reference.vue`：平台抽屉共用环境 API 并发请求修复及窄表横向滚动。
- `frontend/src/components/business/ms-comment/comment-deliveries.vue`、`views/execution/case-delete-confirm.vue`、`views/case-management/caseManagementFeature/components/import/importJob.vue`：通知、删除预览、导入结果共享分页偏好；对应评论通知、删除预览和导入查询接口。
- `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue`、`views/api-test/components/paramTable.vue`：运行时不存在的 interface 改为 type import。
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentMcpStreamableService.java` 与 `AgentMcpStreamableServiceTests.java`：资源协议交由各共享服务处理当前授权、状态及重放，不要求协议中不存在的通用 requestId。
- `AgentCaseDeletionService.java` 与 `AgentLibraryLifecycleMySqlTests.java`：无 ref_id 旧记录的 ALL 删除/恢复范围修复。
- 前述导入语言/后台组织修复、评论工具注入修复及迁移 107–115 均已包含在最新镜像；具体接口和追踪见本节之前记录。

实际验证补充命令：

```powershell
# 仓库根目录；测试 Token 由脚本创建并在 finally 撤销
node .codex-tmp/remediation-attachment-native-smoke.mjs
node .codex-tmp/remediation-snapshot-native-smoke.mjs
playwright-cli.cmd -s=remediation-full run-code --filename .codex-tmp/remediation-platform-ui-production.js
playwright-cli.cmd -s=remediation-full run-code --filename .codex-tmp/remediation-import-ui-production.js
playwright-cli.cmd -s=remediation-full run-code --filename .codex-tmp/remediation-list-ui-production.js
docker inspect --format '{{.State.Health.Status}}' msp-export-20261021-app-1
```

选定修改页面 ESLint 退出 0，日志 `remediation-latest-lint.log`。Node 包 23 项测试和打包安装沿用本轮此前通过证据，之后未修改 Node 包源码。

**整体仍为部分完成，不能把本轮通过扩大为全方案交付：**

1. U04 的统一平台 API/归档及真实登录验证尚未完整补齐；当前明确提供地址连接检查，不显示 LOGIN_VERIFIED。
2. R03A 独立浏览器 Session 设备授权/代理安装链路尚未实施。本次测试恢复的浏览器存储状态不构成其独立 Session 验收。
3. U14 持久化调度恢复有数据库证据，真实受管适配器完整运行尚无本版本证据；隔离环境没有配置模型，不能以合成历史归档冒充真实运行。具备真实配置后可按 `ai-browser-runner/README.md` 注册并配置 Runner，执行 `npm.cmd --prefix ai-browser-runner run build`、`npm.cmd --prefix ai-browser-runner start`，再从真实任务入口验收。
4. 旧导出 fileId 网页兼容、对象库存对账及查询关联写入点完整审计仍未闭合；U12 当前为最多 100 条的整批事务，不声称支持逐条部分成功。
5. 未运行全仓所有历史业务测试，也未穷举容量与故障矩阵；本轮按用户要求验证相关回归和主流程。需要全仓回归时命令为 `mvnw.cmd -pl backend/app -am test`，不能用本轮 21 项代替全仓通过。
6. OAuth、个人证据内容增强、额外 Token 认证治理不在本轮擅自新增；来源存在争议的扩展不计为已经获得明确需求确认。
