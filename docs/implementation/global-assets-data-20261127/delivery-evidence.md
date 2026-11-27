# 测试资产全局化与测试数据管理：开发交付证据

2026-11-27，开发文档-草稿。依据本会话用户列明的需求及“开始执行修复修改”授权实施；产品、技术草案未冒称整版正式批准。本文件在独立审查前固化，审查结论、提交和上游 PR 由最终交付消息记录，避免修改被审查差异。

“全局”按当前组织隔离。关联追溯、执行会话、来源项目权限保留；全局资产不会因此跨组织共享，也不把项目管理员提升为组织资产管理员。

## 需求追踪与对应链路

|需求|前端入口与实现|后端接口、方法及持久化|验收证据|
|---|---|---|---|
|公共配置、业务资产不随当前项目变化|business-assets / shared-configuration / 对应版本组件；监听组织而非项目|BusinessAssetService、SharedConfigurationService；GlobalAssetAccess.context/authorize；162回填 organization_id，保留来源项目与密文AAD|GlobalAssetAccessTests、GlobalAssetMigrationMySqlTests、SharedConfigurationServiceTests、ExternalExecutorServiceTests|
|检查其他测试资产的项目限制|catalog、versions 使用全局索引；统一版本页新增测试数据页签|GlobalAssetCatalogController /test-assets/global/catalog、/versions；逐来源、逐类型权限校验；TestAssetCatalogService.sourceCatalog/sourceDetail/sourceVersions/sourceVersion|多项目、不同类型角色隔离测试；默认版本不使用当前项目权限读取其他来源|
|关联追溯仍项目级|relations；数据详情显式选择引用项目|原项目关系接口及权限保留；来源读写携真实 projectId|原 TestAssetControllerPermissionTests / 目录与版本回归；路由、API契约检查|
|名称/详情进入可读详情页|datasets 名称与常驻详情按钮 → /test-assets/datasets/:dataId|GET /test-data-assets/{id}、/{id}/content；DataAssetService.detail/content|真实浏览器详情、接口读回及截图；JSON/TXT完整原文、CSV/XLS/XLSX表格与分页组件测试|
|左上角上传、人工新增|上传测试数据、人工新建；解析状态及重试|POST /test-data-assets、/uploads；DataAssetParseJobService.upload/drain/retry；原文件、不可变版本、持久解析任务|CSV/JSON真实页面创建、原文件字节保持、解析状态与重试、过期任务恢复测试|
|修改与批量修改|常驻修改；分类/说明批量保留、设置、清空；逐项内容编辑队列|PUT /test-data-assets/{id}、POST /batches；DataAssetService.save、DataAssetBatchService.submit/retry|MySQL实际批量归类/说明更新，CSV金额44.40、JSON金额55.5读回；CAS冲突、失败重试不重放成功项测试|
|单项/批量删除|常驻删除、批量删除及确认|DELETE /{id}?expectedRevision=；批次逐项删除；活动租约/运行任务保护，逻辑删除保留历史|GlobalDataExecutionMySqlTests、DataAssetBatchServiceTests；最终V10浏览器两条数据批删、刷新为空，无解析恢复404；final-batch-delete-refresh.json|
|左树右条目分类|复用分类管理抽屉、组织切换销毁旧抽屉与弹层|GET /test-data-assets/categories；复用 /test-assets/categories CRUD；TestAssetGovernanceService 计入新数据|真实分类创建与归类、非空保护、迁移到未分类不删内容；无效/外组织分类拒绝|
|操作按钮常驻及收纳|详情/修改/删除常驻；其他操作位于“…”|对应详情、写入、下载、发布、来源及追溯真实接口|真实列表截图、名称和按钮跳转、固定版本链接|
|历史版本可追溯、不被覆盖|详情版本选择、统一版本页、替换文件生成新版本|GET /{id}/versions、PUT /{id}/uploads；metadata_json / metadata_source；历史名称/说明/分类/格式快照|原用途仍可在v1读取；旧CSV字节与上传一致；日期/空行/前导列、历史链接和晚到响应测试|
|兼容历史项目文件|历史来源索引、显式导入文件族|DataAssetLegacyService.inventory/importFile；source 映射、全文件族幂等迁移|旧版本/原文件保留、50版本/20MB预算、失败补偿、人工当前版本不被补录覆盖|
|供执行器使用且不漂移|发布/用于任务必须显式选择项目|DataAssetPublicationController.publish；项目 test_asset_version 桥接；预检读取固定版；AgentTestDataLeaseService.acquire/content 读取哈希校验快照|真实MyBatis+MySQL发布、编辑后仍读取旧值、运行删除保护、无网页登录态时按已通过预检的持久actor授权、MySQL重复读旧快照下仍拒绝新租约删除；不是线上执行器联测结论|
|原生MCP、Node及授权一致|Node工具同步注册并说明；有效Token无需追加Scope|metersphere.data_asset.manage 15种操作；外层CAS/幂等参数正确合入；用户当前角色、Token有效性/项目白名单保留|Node 52项；原生工具5项；内外参数冲突拒绝、同一安全错误码/trace验证|
|失败可理解、可定位|复用 AppError 安全提示与trace；错误在操作弹层内可见|DataAssetExceptionHandler、DataAssetErrors / DataAssetFailure、AgentSafeErrorMapper；安全事件/阶段/时间/根因/SQLState及错误码|真实MockMvc故障注入、秘密/堆栈不入响应日志、HTTP/native trace；浏览器CAS不覆盖及提示可见性复验|

## 文件与模块

完整文件清单见同目录 `changed-files.json`。主要修改集中在：

- `backend/services/agent-integration`：组织授权、业务资产/配置、全局来源索引、新测试数据服务/Controller/MCP工具、执行快照适配、统一安全错误及对应测试。
- `backend/services/system-setting`：PermissionUiService及测试，仅投影授权页面与父菜单壳，不授予兄弟页面或接口权限。
- `backend/framework/domain/.../migration/3.7.2`：160新数据表、162组织列/回填、163/164角色权限、165全局页面映射。未改历史迁移。
- `frontend/src/api`、`router`、`config/pathMap`、`views/test-asset`：真实API与路由、全局页面、新数据列表/详情/版本/表格编辑、批量治理、组织响应隔离及错误提示。
- `metersphere-mcp`：dataAssets工具注册、既有projectId别名兼容、说明与真实stdio/HTTP契约测试。未升级依赖或引入额外Token Scope。
- `docs/product_md`、`docs/implementation`、`docs/testing/global-assets-data-20261127`：草案、实施证据、只读迁移预检及合成数据测试记录。历史61MB验收材料与私人交接记录未混入。

## 实际验证

|检查|结果与边界|
|---|---|
|后端V13完整相关测试及打包|BUILD SUCCESS；agent-integration 165项（159实际通过、6条件跳过），权限投影7项、app权限清单2项通过，共168实际通过|
|最终隔离MySQL|9857f1e0轮次6/6实际通过、0跳过；使用刚经V13编译验证的生产类，插件明示 skipMain，仅避免重复主源码编译；临时实例准确停止、连接文件删除|
|Node工具包|52/52通过，包含真实stdio进程启动、工具发现及HTTP转发契约|
|前端行为/API组件|32/32通过；完整SFC模板测试，不以全页出现字符串冒充弹层内正确展示|
|前端类型检查/最终生产资源|V10全量类型检查通过；修复后V11生产构建成功（16分45秒），最终补验使用V11资源。V11并行类型检查因内存争用主动中止后，单独重跑退出0；未将中止轮次报告为通过。|
|lint / stylelint / diff|改动范围检查通过；生产代码无新增依赖、Mock、占位函数或未说明TODO|
|真实浏览器开发集成|真实Controller、事务、隔离MySQL、解析/批次服务和本地原文件；仅身份及FileCenter注册为测试边界。已执行上传、人工新增、分类、批量元信息与内容编辑、替换、历史查看、原文件字节核对和并发保护|

详细命令、输出摘要和最终浏览器结果放在 `docs/testing/global-assets-data-20261127/`；完整迭代说明见 `verification.md`。前端首轮曾因本机生成文件写入异常中断，未改变规则或依赖规避；独立重跑结果单独记录。首次CAS截图也暴露错误被遮挡，保留失败证据，以最后真实包的可见性结果为准。

后端包验证额外比较嵌入模块JAR与当前构建目标的完整字节，并比较关键编译类和5个迁移；它证明包内容，不证明部署启动。前端资源独立构建；后端命令使用skipAntRunForJenkins，未声称该后端JAR内含最终前端。

## 部署前后边界与操作

1. 迁移前运行同目录 `migration-preflight.sql`，孤儿/无组织来源必须为零；重复业务身份/配置名保留供明确治理，不自动合并。MySQL DDL并非整体事务，失败后先核对已执行DDL和Flyway记录，禁止盲目重跑或clean。
2. 默认组织/系统管理角色获新资产权限。仅项目管理员不会自动取得组织资产写权限；自定义角色须拥有组织级读取及维护权限。既有有效Token无需重建或追加Scope；Node用户需要同步构建更新工具包才能发现新工具。
3. 文件/完整编辑上限2MB、总行数10000；历史文件族最多50版、总量20MB。超预算明确拒绝，不静默截断。历史导入是显式操作，不把尚未导入的文件宣称已迁移。
4. 删除保留原文件、不可变历史及已存在的追溯记录；未增加物理存储清理任务。内部安全诊断日志不等于已接外部采集/告警。
5. Docker镜像构建按用户要求不执行。完整容器启动/health、真实SSO、MinIO及执行器宿主端到端须在用户通知部署后验证；本轮不操作生产。按当前测试容器名可执行 `docker inspect metersphere --format '{{json .State.Health}}'`、`docker logs --since 15m metersphere`，再从真实登录入口复验上述核心路径及上传替换。健康检查不能替代功能验收。
6. 历史同步两个失败项目、无可用低权限账号的实测按用户要求排除；不报告已修复或已通过。自动化角色隔离测试已做，但不冒充低权限真实账号验收。

独立审查使用指定左殇既有共享会话，绑定精确文档版本、文件哈希和工作区patch SHA256；审查通过前不提交。之后按约定正常合并推送自有chenqifen/main并提交上游PR，上游由用户审查合并及部署。

V10浏览器补验（首次送审前）：包含rows/totals/type=DATE/value/other的JSON全部原样可读；409冲突在弹层内可见且elementFromPoint未被遮挡，保留金额55.5，截图已打开检查；CSV上传解析成功清除恢复键，两条数据批删刷新为空、无错误。未使用临时CSS注入。证据 final-json-complete.json、final-cas-conflict-visible.json/png、final-batch-delete-refresh.json。

最终后端JAR SHA256：`86ba354d7c16d82781d2fad51506168824566e268f3dd47437f7cc66284aa99d`；前端index SHA256：`81badaa64b9576d3e798bc2b1399ab2c6d866d319afcc4a6a259fbf616185f13`。构建清单标识未提交工作区，最终审查patch对应本交付。

末尾空行清理后重新打包 BUILD SUCCESS（3分58秒），明确skipTests/skipMain；生产语义无变更。包完整性重新比对通过。最终eslint/stylelint退出0；API契约、路由页签、权限资源检查通过（83精确/60兼容/0未绑定）。

## 指定独立审查及修正

左殇初审绑定patch `518f4fe2d729861131c32242a5836e1b004e1d0cce5ba782a9bf83212e10d523`，结论需修改；报告归MSP协作文档库审查报告-草稿，文档ID `8e305a4d-eba4-49c5-b882-d5508d64c0e8`。确认R1/P1编辑元信息与正文并行读可跨版本拼接，R2/P2取消加载后新建继承loading导致保存禁用。历史补录元信息疑点经审阅未列缺陷。

R1修正：先读取资产元信息，再按currentVersionId固定读取正文，expectedRevision来自同份元信息；历史版本不能直接编辑覆盖当前。R2修正：取消/新建明确释放当前loading，旧请求成功/失败/finally仅作用于自身代际。32项前端测试通过，新增先红后绿时序覆盖元信息/正文延迟、close→create可保存、close→openB旧成功/失败不得释放B读取状态、并发后409保留草稿/trace、仅元信息修改与逐项队列固定版本。V11生产构建与两项真实页面补验通过；新patch提交指定左殇复审，旧patch不得提交。

V11最终补验：HTTP正文请求显式带固定versionId，延迟内容请求期间另一客户端将金额55.5改为66.6，仅改说明的旧草稿保存409并保留草稿/trace，服务端66.6未被覆盖；提示截图已打开检查。延迟metadata→关闭→新建→释放旧请求后保存成功，刷新可见且无错误。证据final-v11-fixed-edit-race.json/png、final-v11-cancel-create.json、final-v11-build-manifest.json。V11类型单独重跑退出0；最终复审结论由交付消息记录。

V11前端index SHA256：`a24b9ba757259230099a5464b7eb0d2be5735009778cd26bc482330f3d076464`。V10记录仅为此前验收证据，最后资源以V11为准。
