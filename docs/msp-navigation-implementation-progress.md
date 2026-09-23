# 导航精简实施记录

分支：v3.2。范围按方案分批推进；保留已有 API、业务表、资源码和角色自定义授权。

| 需求 | 入口与实现 | 后端/数据 | 验收计划 | 状态 |
|---|---|---|---|---|
| N01/N02 统一任务与详情 | /execution/tasks，旧用例/Agent URL 白名单映射 | 复用 /ai/execution；项目及对象鉴权不变 | 列表、创建限制、筛选、详情与返回、旧链接 | 已实现；空态/路由/权限验证通过，正向任务操作待验收 |
| N03/N04 接入与配置分责 | 设置个人接入、项目执行设置、运行维护、扩展能力 | 原 Token/运行资源/治理 API；敏感配置保持管理员边界 | Token 单入口、管理员/普通账号直达权限 | 入口验证通过；Token 全生命周期未重测 |
| N05 评价下沉 | 执行任务内展开真实评价组件 | 原评价查询，历史不迁移 | 旧评价链接、真实数据读取 | 路由验证通过；有历史评价的数据场景未验收 |
| N06/N07 资产导航 | 三类主对象；历史缺陷/环境目录保留上下文访问 | 原 catalog/version/relation API | 主标签数量、版本与关联链接、历史 URL | 主标签/高级检索验证通过；具体环境历史快照未验收 |
| S2/S3 后续配置嵌入与页面减负 | 环境详情嵌入、消费者状态、详情业务标签 | 需进一步核对环境 ID 与消费者契约 | 不以迁菜单代替完整验收 | 待实施 |
| 综合方案依赖 | 无模型网页创建、审批 | external-task/审批后端尚未交付 | 明确限制；无假审批按钮 | 不在本导航批次实现 |
| S4 观察期 | 保留旧链接至少一个发布周期 | 不删除数据或 MCP/API | 需发布后的使用记录 | 待观察 |

失败边界：不复制 Token 明文到 URL；未知回跳参数不传播；项目参数不触发跨项目兜底查询；旧环境版本继续访问原历史能力，不重定向到最新版。权限资源仅调整组织元数据，不覆盖自定义 visible/operable，不增加角色授权。

## 首批实际实现

- `/execution/tasks` 成为主要任务入口，`/execution/tasks/:id` 复用真实详情。任务路由名为 `executionTasks`，避免原 `caseManagement` 模块开关误拦截；原路由名、旧 URL 保留兼容跳转。白名单保留项目、任务、用例、资产版本及现有筛选，剔除 Token 和任意回跳 URL。
- `/agent/queue` 默认到任务列表；`tab=leases/triggers` 到设置里的租约/调度。运维组件已移除重复任务列表实现。取消、重试、创建仍由原真实组件和接口提供；平台未配置可用模型时不显示网页模型创建按钮，明确说明外部任务只能通过既有 MCP 创建。
- 个人接入复用唯一 Token 编辑组件；个人中心提供上下文链接。原能力页拆为能力状态、运行告警、项目策略三个视图，不再混读个人 Token。配置迁入设置的四个分组，敏感配置保留 `adminOnly` 与原具体权限；项目门禁策略仍按其项目权限访问。
- 评价迁入任务页折叠区域，保留原评价 API、历史及支持的筛选。旧接口不支持日期/指定执行者过滤时明确提示，不能把保留 URL 参数当作实现了过滤。
- 资产路由生成的主标签降到文档、用例、数据三项，默认入口考虑权限与自定义 UI 可见性。其余目录、版本和追溯保留原组件及地址；提供按版本检索及按类型进入目录的上下文路径，缺陷和项目环境页面可回看资产历史；资产详情增加版本/关系快捷入口。
- 默认任务列表把来源、通道和尝试次数放入可展开的高级筛选/技术列，普通用户不读取模型配置和运行治理信息。详情沿用真实实现，本批没有完成“四个业务标签”的全面重排。
- V3.7.2_94 仅调整权限资源名称、父级、任务路由名及详情/评价的项目作用域，不写业务表、不增加角色权限、不覆盖自定义 UI 授权，保留既有默认可见性。

## 文件与前后端对应

| 文件/目录 | 对应链路 |
|---|---|
| `frontend/src/router/routes/modules/{execution,agent,caseManagement,setting,testAsset}.ts`、`router/routes/execution-settings.ts`、`enums/routeEnum.ts`、`config/pathMap.ts` | 主菜单、旧地址、设置分组、权限资源映射 |
| `frontend/src/utils/execution-navigation.ts`、`views/execution/tasks.vue`、`views/execution/locale/*` | 参数白名单、任务/评价入口 |
| `frontend/src/views/bug-management/automationExecution/index.vue` | 原 `/ai/execution/task/search`、详情、取消、重试、创建等接口；未新增后端执行协议 |
| `frontend/src/views/agent/{capability,queue,evaluation}.vue` | 原能力、告警、治理、租约、调度、评价 API；按新职责加载 |
| `frontend/src/components/business/{ms-personal-drawer,ms-top-menu}/index.vue` | Token 单维护入口、详情菜单高亮 |
| `frontend/src/views/test-asset/{cases,catalog,versions}.vue`、`components/TestAssetPage.vue` | 原资产版本、目录、关系 API；高级目录有上下文入口 |
| `frontend/src/views/bug-management/index.vue`、`views/project-management/environmental/index.vue` | 缺陷/环境主入口到原资产历史目录 |
| `backend/framework/domain/src/main/resources/migration/3.7.2/dml/V3.7.2_94__execution_navigation.sql` | 资源元数据迁移，角色与业务数据不迁移 |
| `frontend/scripts/test-execution-navigation.mjs`、`verify-route-tabs.mjs` | 真实路由模块/参数函数测试及架构断言 |

## 已执行验证

| 命令 | 结果 |
|---|---|
| `node --test frontend/scripts/test-execution-navigation.mjs` | 9 项通过，包含参数保留/剔除、旧队列分流、评价、配置权限边界及资产历史路径 |
| `pnpm.cmd -C frontend run test:route-tabs`、`test:permission-resources`、`test:api-contracts`、`test:layout-overflow` | 四项通过；93 精确资源绑定、49 兼容绑定、0 未绑定 |
| `pnpm.cmd -C frontend run type:check` | 通过；最终补充资产上下文入口后再次通过，日志 `.codex-tmp/navigation-type-final.log` |
| `pnpm.cmd -C frontend exec eslint <本批变更的 TS/Vue 文件> --fix` | 分批定向检查通过，0 错误；原执行页/环境页共 3 条未使用代码警告，日志 `navigation-lint-final.log`、`navigation-context-lint.log` |
| `pnpm.cmd -C frontend run build` | 通过；补充上下文入口后另行执行同一生产配置 `pnpm.cmd -C frontend exec vite build --config ./config/vite.config.prod.ts`，退出 0，日志 `navigation-bundle-final.log` |
| `.\mvnw.cmd -pl backend/services/agent-integration -am test '-Dtest=QualityPolicy*Tests,AgentExecutionExceptionHandlerTests,AgentExecutionChannelPolicyTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dquality.mysql=true'` | 27 项，0 失败/错误/跳过，BUILD SUCCESS；日志 `navigation-backend-tests.log` |
| `.\mvnw.cmd -pl backend/app -am package '-DskipTests'` | 首次打包 BUILD SUCCESS；最后静态资源更新后的打包结果待下方补记；此命令不作为测试通过证据 |
| `npx.cmd --yes --package @playwright/cli playwright-cli -s=mspnavigation run-code --filename .codex-tmp/navigation-browser-smoke.js` | 真实登录/后端：任务兼容与筛选、敏感参数剔除、模型限制、个人接入、运维队列、能力分流、资产三标签、门禁旧 URL 已走通；最终镜像后复验结果待补记 |

隔离迁移前基线：任务 0、附件 0、评价 0、资产版本 13、自定义 UI 授权 4。空表计数不能替代真实任务数据回归。

## 未完成与未验证

- S2 环境详情内嵌执行配置、登录方式、凭据引用的上下文迁移未完成；本次只是先完成设置归类并保留旧配置能力，不能视为 N08～N10 已验收。
- S3 完整详情业务标签、计划/执行者聚合筛选、证据筛选、未启用消费者的完整能力矩阵尚未完成。现有 API 不支持的字段未伪造返回。
- 综合方案的无模型网页创建、审批/正式门禁不在本导航批次实现，未开放占位按钮。
- 隔离库没有历史执行任务，尚未验收真实任务的取消/重试、正向详情及其旧链接返回；需准备由真实 API 创建的代表性任务再走查。本批未执行全仓后端回归（命令：`.\mvnw.cmd -pl backend/app -am test`）及生产克隆库升级对账。
- S4 必须经过至少一个发布周期的访问记录和错误观察，当前不能证明旧地址可物理删除。没有清理历史数据或旧 MCP/API。
- 本批仅本地实施与隔离验证，未提交、推送或部署生产。保留用户原有 mapper、测试模板、周报和其他未跟踪文档。

## 最终容器与浏览器验收（2026-09-23）

1. 最终前端源码执行 `pnpm.cmd -C frontend run type:check` 与 `pnpm.cmd -C frontend exec vite build --config ./config/vite.config.prod.ts` 均退出 0；随后 `.\mvnw.cmd -pl backend/app -am package '-DskipTests'` BUILD SUCCESS，日志 `.codex-tmp/navigation-package-final.log`。解包后的静态 `index.html` SHA256 与最终 `frontend/dist/index.html` 相同。
2. 新建 `backend/app/target/navigation-dependency-final`，在其中 `jar -xf ../app-3.x.jar`，再运行 `docker build -f Dockerfile.backend --build-arg DEPENDENCY=backend/app/target/navigation-dependency-final -t msp-quality:verify .` 成功。运行镜像与构建镜像均为 `sha256:7eb5d4962dc4835fb83f93a1bfc935f58fe25f16b6e59e2d7c3fbeefd616bd0a`。
3. 复用隔离实例既有口令环境变量，执行 `docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml up -d --wait --wait-timeout 180 app` 退出 0，应用及四项依赖均 healthy；`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-quality-environment.ps1` 通过。
4. Flyway `3.7.2.94` 的 `success=1`、`checksum=1937482263`。在隔离库事务内重复执行该迁移两次并回滚，任务资源路由匹配 1 条、详情/评价项目作用域匹配 2 条。业务计数与迁移前一致：任务/附件/评价均 0，版本 13，自定义 UI 授权 4。空表不能证明历史任务完整性。
5. 最终镜像启动后，Playwright CLI 再次执行 `.codex-tmp/navigation-browser-smoke.js` 退出 0，任务兼容、筛选、Token 参数剔除、模型限制、单接入页、租约页、能力分流、三项资产主导航、门禁兼容地址均走通。日志 `navigation-browser-final.log`。
6. `.codex-tmp/navigation-browser-permissions.js` 使用正式登录创建的普通测试账号：任务列表 HTTP 200，合法参数的执行器注册 HTTP 403；运行维护、模型、凭据及旧 Agent 地址共 4 条直达均进入无权限页。日志 `navigation-browser-permissions-final.log`。临时 `navigation-verify-read` 授权在 finally 中删除，最终查询余量 0。
7. `$env:QUALITY_E2E='true'; $env:PW_BASE_URL='http://127.0.0.1:15173'; pnpm.cmd -C frontend exec playwright test e2e/quality-policy.spec.ts --timeout=90000`：6 passed（31.5 秒），使用真实后端；策略移入设置未破坏保存、发布、禁用项目、字段校验和角色权限。
8. 同一基础地址下，`pnpm.cmd -C frontend exec playwright test e2e/information-architecture.spec.ts --grep '个人接入' --timeout=60000`：1 passed。该既有用例使用 Mock API，仅计为组件/布局验证，不作为真实 Token 后端验收；没有执行其全套旧架构用例。
9. 额外通过真实页面点击“按版本检索资产与引用”到 `/test-assets/versions`，读取 13 条既有版本；选“执行证据”后点击“查看此类资产目录”到 `/test-assets/evidence`。快照 `.playwright-cli/page-2026-09-23T10-28-35-299Z.yml`。
10. 最终变更范围 ESLint 0 错误；新测试断言的格式警告已修复，原执行页/环境页 3 条未使用代码警告保留。`git diff --check` 通过，仅有未纳入本次改造的测试模板行尾提示。浏览器快速切页过程存在控制台 AppError 记录，未逐条归因，不宣称控制台零错误。

完整 lint 范围的实际 PowerShell 命令（在本次未提交工作区执行）：

```powershell
$navigationLintFiles = @(git diff --name-only -- frontend/src frontend/e2e/information-architecture.spec.ts |
  Where-Object { $_ -match '\.(ts|vue)$' } | ForEach-Object { $_.Substring(9) }) +
  @('src/router/routes/execution-settings.ts','src/utils/execution-navigation.ts','src/views/execution/tasks.vue')
pnpm.cmd -C frontend exec eslint @navigationLintFiles
pnpm.cmd -C frontend exec eslint e2e/information-architecture.spec.ts --fix
```

本地 `.codex-tmp` 保存实际测试操作及输出，不作为业务代码提交，也不包含需要推广到生产的测试授权。

**整体状态：部分完成。S0～S1 首批入口改造已有上述证据；N08～N10、完整 S3、真实任务/Token 全生命周期和生产观察仍未完成，不可将本记录视为整个精简方案验收通过。**
