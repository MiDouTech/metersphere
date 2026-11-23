# 资产 UI 与公共配置实现验证（开发草稿）
日期：2026-11-23。依据本会话六项优化及左操作右检索要求；需求和实现链路见 plan.md，接口兼容见 configuration-protocol.md。
当前代码通过 R3 独立 Agent 审查；这不是指定外部身份左殇签核，也不代表部署验收通过。正式验收按项目约定等待用户通知部署。

## 需求追踪结果
|需求|实际实现|验证证据|运行边界|
|---|---|---|---|
|账号密码分开、自定义行|公共配置 Arco 表单固定账号/密码及可增删字段；后端校验结构并加密|实际 SFC 保存/校验测试、真实服务加密测试、浏览器提交字段检查|部署后真实账号授权链路待验收|
|敏感配置分两类|SECRET + secretType；旧单值保留为兼容格式；编辑默认保留秘密|旧值保留、JSON严格校验、越项目拒绝、固定版本解析与脱敏测试|执行器真实联调待部署|
|资产版本 UI|按权限分业务/配置/其他目录，系统选择器、列表及提示；保留历史深链|深链默认标签、倒序响应、项目/权限切换回归|真实历史数据规模待验收|
|新增配置弹窗|居中 Arco Modal，单一保存入口，关闭清理，宽度随视口收缩|浏览器实际 Vue 组件，720px视口弹窗688px，无页面水平溢出|未在部署后的完整壳层验收|
|公共配置风格|左操作右检索，系统/环境/类型筛选、标签及空态|浏览器工具栏坐标左45右1092；原生输入框名称/自动填充属性检查|截图使用虚构数据|
|业务资产风格|分类树与列表卡片、文档/分支统计、系统按钮，行操作收进更多菜单|实际组件截图、原有业务及导航回归|真实上传/归类/合并留待部署|

## 实际执行
以下命令在仓库根目录执行，另有注明除外。原始构建输出保存在本机临时目录，报告不包含凭据。
- node --test frontend/scripts/test-business-assets.mjs frontend/scripts/test-shared-configuration-fields.mjs frontend/scripts/test-asset-version-loading.mjs frontend/scripts/test-execution-navigation.mjs：26项通过，零失败、零跳过。
- npm run test:api-contracts（frontend）：通过。
- npm test（metersphere-mcp）：含 TypeScript 编译，47项通过。
- 变更范围 npx eslint 和 npx stylelint（frontend）：通过。文件完整清单见 review-r3-manifest.json；没有执行会改写整个仓库的 lint --fix。
- npm run build（frontend）：R1和R2构建均 exit 0（包括 vue-tsc）。R3精确最终源代码的生产构建也已 exit 0；完整命令为 npm run build（含 vue-tsc --noEmit、vite build 及 build manifest）。
- python -X utf8 scripts/verify-shared-configuration-migration.py：真实隔离 MySQL 13306、新建专属数据库，4项通过；不连接业务数据库。见 mysql-verification.json。
- git diff --check：通过。
- ego-browser：挂载正式 Vue 组件和 Arco、系统实际主题函数，以隔离虚构响应检查三个页面、账号密码与自定义行、字段提交、默认保留秘密、视口适应和可访问名称。复现启动命令 node frontend/scripts/asset-ui-preview.mjs；默认仅监听127.0.0.1:5188。此测试夹具不参与应用生产入口，也不是真实后端端到端。
- 后端相关测试和应用 package 命令如下，exit 0，6套件41项全部通过，配置服务16项；报告哈希见 backend-verification.json：

    .\mvnw.cmd -pl backend/app -am '-Dtest=SharedConfigurationServiceTests,BusinessAssetServiceTests,ExternalExecutorServiceTests,BusinessGovernanceExceptionHandlerTests,BusinessAssetMcpTests,AgentHistoricalCompatibilityTests' '-Dsurefire.failIfNoSpecifiedTests=false' package -q

服务测试执行真实 H2 持久化、加密、事务和 MockMvc JSON 请求；授权入口使用测试替身，不能把这些结果视为实际登录与完整权限链路的验收。JSON畸形、重复键、尾随内容及加解密故障注入检查诊断分类和脱敏，无原始秘密或异常堆栈。
既有生产构建提示 eval、动态/静态混合导入和大包体警告；未将其当作本次已修复问题，也没有隐藏警告。

## 审查及回归
R1：5项P2、2项P3，均已修复。前端3个回归先失败后通过；后端尾随/重复JSON和重复当前版本审计2个回归先失败后通过。R2又复现旧资产目录竞态，对应2项测试先失败后通过。R3独立复审通过，审查Agent独立执行17项专项测试。精确差异及哈希见 review-r3-manifest.json，实际身份见 review-record.md。
没有把本机未连接的指定外部身份记作已审查，未提交、推送或部署。工作区其他任务的改动排除在本次17文件代码差异之外。

## 未运行及所需条件
- Docker镜像/容器构建：按用户要求不执行。
- 容器启动、健康检查：未运行。Get-Command docker未发现命令，Docker Desktop和标准docker.exe安装路径均不存在。当前没有可用容器运行环境；应用编译和服务测试不能证明容器能启动。
- 部署后的完整前端后端端到端、真实执行器解析与权限矩阵：未运行。没有收到本版本已部署通知；不拿旧环境或隔离响应冒充新版真实验收。
- 全仓库全部单元测试：未运行；实际范围为上述受影响套件，不宣称全仓通过。

具备现有隔离容器环境、预构建镜像和安全注入测试凭据后，原项目路径可执行以下命令（本轮均未执行，也没有操作CDS或Kafka）：

    docker compose -p msp-business-assets-review -f deploy/quality-verify.compose.yml ps
    Invoke-WebRequest http://127.0.0.1:17071/actuator/health/readiness

启动命令是同一 compose 的 up -d，但仅在用户准备好现成验证环境后执行；需要 QUALITY_DB_PASSWORD、QUALITY_STORAGE_PASSWORD、QUALITY_FACTS_KEY 及隔离 override 中的 MSP_SHARED_CONFIGURATION_ENCRYPTION_KEY，不能把实际值写入文档或命令日志。本轮没有搭建、启动或改动该基础设施。
现有导航端到端命令为 npm --prefix frontend run test:e2e:information-architecture；它也未运行，且不能替代本需求的真实配置保存与执行器联调。
最小协助：在既有协作会话取得指定审查签核；用户部署本次代码后通知验收地址及现有安全登录方式。随后 Agent 覆盖新建/刷新后持久化/保留或替换秘密/批量导入回滚/固定会话解析/历史切换及越权失败，不预先猜测验收地址。

## Modern Web Guidance
使用 css-layout 指南。我保留交互控件的DOM顺序，用 margin-inline-start:auto 将检索区推到右侧，配合 flex-wrap、min-inline-size:0 和 minmax(0,1fr) 防止溢出；没有用CSS order改变键盘焦点顺序。依据原文：“Don't use order or flex-direction: *-reverse to reorder interactive content.” 弹窗复用系统Arco，不将原型的原生dialog实现混入组件体系。
## 已知风险与交付边界
- 原截图中的“业务资产版本目录加载失败”未在原部署环境重现；本轮修复了历史归属查询遮蔽正常目录和并发覆盖问题，不宣称已查明原部署侧故障根因。
- 系统现有主题与页面壳层在正式部署中的组合仍待验收。浏览器截图是正式组件的隔离夹具，使用系统主题函数和虚构数据；不是线上页面。
- 外部监控/告警没有新增接入；保留现有安全日志和 traceId，不把日志分类等同于已经建立外部告警。
- 指定外部审查身份签核未取得。依 AGENTS.md 的“交0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0独立审查 → 修复及复审 → 审查通过后提交”约定，本轮独立子Agent结论不冒充该身份授权，提交、合并、推送步骤保持等待；所有可独立执行的代码实现、测试、迁移、打包及材料准备继续做到可复核。
## 最终构建绑定
- R3最终 npm run build exit 0，包含完整类型检查、生产构建及 build manifest。
- 前端构建后再次执行 .\mvnw.cmd -pl backend/app -am '-DskipTests' package -q，exit 0。此轮仅重新打包最新页面，不把跳过测试记为重新运行；相关后端41项测试证据保留在上文。
- 应用包5项检查全部通过：最新前端manifest、index、入口资源、严格结构校验类及157迁移均已入包。见 package-verification.json。
- 当前工作区另有其他任务的未提交代码，应用包仅作为该共享工作区的编译/打包证据，不作为仅包含本次17文件差异的隔离发布包。后续应在指定签核后以本次代码提交形成可发布构建。
- build-manifest中的revision仍是尚未提交的HEAD基线；本次实际源代码以R3补丁及逐文件SHA256为准。最后复核17文件未发生审后变化，git diff --check通过。
- 本轮启动的浏览器夹具与隔离MySQL进程已关闭，测试数据库及截图证据保留。没有部署或操作共享基础设施。

## 提交授权更新
用户在当前会话明确要求“提交代码至github，合并至chenqifen和main”。据此继续本次差异的提交和自有仓库两分支合并推送；R3实际独立审查身份及部署验证边界保留，不冒充指定外部人员签核。不合并上游PR，不部署。
