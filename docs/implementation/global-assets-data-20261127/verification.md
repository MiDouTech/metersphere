# 全局资产与测试数据管理：实施验证记录

2026-11-27，开发文档-草稿。分支codex/global-assets-data-20261127，基线188d47397c1d15c89ebb8268e38c342a2f77babc。用户要求开始执行修复；产品及技术草稿保留原状态，不冒充文档整版批准。

## 需求追踪及链路

|要求|前端入口|后端实现|数据/权限|验证重点|
|全局业务理解|business-assets.vue、BusinessVersions.vue|BusinessAssetController/Service、GlobalAssetAccess|迁移162组织归属，164组织角色，旧来源保留|跨项目同组织读取、跨组织拒绝、证据权限、幂等、并发|
|全局公共配置|shared-configuration.vue、SharedConfigurationVersions.vue|SharedConfigurationController/Service|保留原projectId加密AAD，组织查询和版本管理|旧密文可解密、角色实时撤销、执行器固定版本、批量原子|
|兼容资产目录/版本全局视图|catalog.vue、versions.vue|GlobalAssetCatalogController/Service，MCP相同索引|组织内聚合有权来源，保留源权限|项目提示不筛掉其他项目、分页合并及预算、证据权限OR|
|追溯保持项目级|relations.vue，详情显式项目入口|原TestAssetCatalogService.relations|项目关系及任务数据不迁为全局|明确来源项目、未授权项目拒绝|
|测试数据管理|datasets.vue、data-detail.vue、DataContent/DataTableEditor|DataAssetController/Service/Parser|迁移160独立资产及不可变版本，163/164权限|可读内容、真实上传及替换、CAS、类型/预算、逻辑删除|
|异步与批量|上传解析状态、失败重试、批量修改/删除及编辑队列|DataAssetParseJobService、DataAssetBatchService|持久任务、代次隔离、逐项事务和结果|旧worker不能覆盖重试结果；批次中断不重复成功项|
|左分类树右列表|数据分类及管理抽屉|原TestAssetGovernanceService复用，数据专用计数|分类组织共享、数据归类及非空迁移|循环/外组织拒绝、非空保护、迁移不删内容|
|历史项目文件|历史全局索引、显式导入文件族|DataAssetLegacyService|旧文件族/版本→全局数据映射；原文件不改|50版本/20MB预算、版本原文件保留、原子失败补偿|
|执行器/MCP|发布用于任务显式选项目|DataAssetPublicationController、预检/固定快照、AgentTestDataLeaseService、DataAssetMcpTools|项目发布引用与全局内容分离，无新Token Scope|发布旧版本、哈希校验、读取冻结值、运行租约删除保护|

## 执行与证据边界

迭代日志位于.build/global-assets-*。v4单测98项中95实际通过、3条件跳过；v6单测119项中116实际通过、3条件跳过。它们均早于最后一轮类型/替换文件等调整，不能代替最终源码验证。最终包检查和浏览器复验正在进行，结果另行追加。

原生MySQL首轮：`python C:/Users/midoo/AppData/Local/Temp/msp38-regression-native-20261126.py --native-only --native-test-filter GlobalAssetMigrationMySqlTests,DataAssetMigrationMySqlTests,SharedConfigurationAuditMySqlTests`，3项实际通过，0失败/错误/跳过；日志.build/mysql-selftest/f8d347b3/native-suite.log。隔离数据目录，准确进程已停止、临时连接已删除，未操作既有数据库。后续改动及新增分类测试还需最终重跑。

前端组件/API测试已扩展到16项通过，含真实Arco Textarea readonly→编辑切换、组织切换过期响应、批次结果、固定版本发布及PUT上传。此前生产构建成功；最后一轮生产构建仍在运行，不能把旧构建作为新差异通过证据。

浏览器使用Ego Lite与生产包，HTTP夹具运行真实控制器、H2、数据服务、解析、批次及本地原文件存储。仅身份权限边界为测试替身，公共布局初始化为空测试响应；数据CRUD不返回固定成功或内存Mock列表。这是开发集成冒烟，不替代真实部署环境的登录、权限、MinIO及容器验收。

首轮浏览器发现新建JSON正文不可编辑：Arco textarea在setup缓存原生attrs，旧`:readonly`绑定不随编辑状态更新。已改用响应式textareaAttrs并增加真实组件回归，旧失败证据保留于docs/testing/global-assets-data-20261127/readonly-before-fix.json；待最终生产包复验。

## 迁移、部署和风险控制

执行迁移162前在旧库运行同目录migration-preflight.sql，孤儿资产结果必须为空，重复身份/配置名保留供治理。新增迁移160/162/163/164，不修改历史迁移。MySQL DDL非整体事务；失败后须先核对已执行DDL与Flyway失败记录，恢复备份或按实际状态修复，禁止盲目重复执行或自动clean。

历史项目文件通过全局来源索引继续可见；内容迁移采用显式授权的文件族导入，不在启动时无差别读取所有项目文件。导入保留旧版本、原始文件和映射，人工已编辑的当前版本不会被历史补录覆盖。上线前按只读inventory盘点未迁移文件族；不能把尚未导入的文件报告为已迁移。

Docker镜像构建按用户要求不执行。部署后容器启动/health、真实MinIO、SSO和线上/测试环境完整端到端需要用户部署通知后验证，本轮不操作生产。未接外部日志采集/告警；内部安全阶段日志不等同外部告警。

删除保留原文件和不可变历史版本，尚未添加物理存储回收任务，不声称已完成存储容量治理。文件/完整编辑2MB、总行数10000、工作簿列数受控，复杂大文件应明确拒绝或保留原项目文件，不静默截断保存。

所有代码仍需指定左殇对精确差异独立审查，通过后才能提交、正常合并推送自有分支及创建上游PR；不自行合并上游PR。

## 后续验证与修正记录（覆盖前述迭代状态）

- 原生MySQL最终数据库轮次：`.build/mysql-selftest/d89a831c/native-suite.log`，6项实际执行、0失败/错误/跳过。覆盖160/162/163/164/165实际迁移、重复权限迁移幂等、原密文AAD、混合排序规则、分类非空保护、真实Mapper发布与冻结租约、运行引用删除保护。首次执行暴露的排序规则问题修复后重跑通过。临时数据库进程已停止，未连接生产数据库。
- 后端v8：`mvn -o -DskipAntRunForJenkins=true '-Dtest=DataAsset*,GlobalAsset*,GlobalData*,BusinessAssetServiceTests,SharedConfiguration*Tests,ExternalExecutorServiceTests,TestAsset*Tests,AgentMcpToolContractTests,AgentExecutionPreflight*Tests,AgentTestDataLeaseServiceTests,PermissionUiServiceTests,PermissionManifestCoverageTests' -Dsurefire.failIfNoSpecifiedTests=false -pl backend/app -am package`，BUILD SUCCESS。agent-integration共134项，128实际通过、6数据库条件跳过；system-setting权限投影6项及app权限清单2项通过。数据库条件由上述独立原生轮次实际执行；最后补充的来源版本页投影另以v9检查。
- Node工具包：`npm test --prefix metersphere-mcp`，52/52通过，含stdio真实启动、工具发现及HTTP契约，不把它等同真实线上MCP授权验收。
- 前端最新行为/API测试：`node --test scripts/test-data-assets.mjs scripts/test-data-assets-components.mjs`，22/22通过；包含真实Arco编辑状态、批量逐字段保留/设置/清空、表格跨页编辑、组织/条目切换、历史固定链接、版本分页搜索清理旧响应。完整类型/生产构建仍以最终轮次为准。
- `npm run test:api-contracts`、`npm run test:route-tabs`通过。改动范围ESLint通过；详情和版本快照组件stylelint通过。`git diff --check`通过，仅既有换行格式提示。

浏览器开发冒烟已实际完成：CSV上传和异步解析、含逗号字段/空行展示、替换生成新版本、选择旧版本仍读原始内容、修改单元格并由真实接口读回99.90。证据在`docs/testing/global-assets-data-20261127/`。上传夹具最初未代理事务，已按真实Spring事务语义修正；不将夹具事务缺失写成产品故障。最终生产包的批量及统一版本入口复验另行追加。

新增165将真正全局页面改为组织权限投影；兼容来源索引仍逐项目校验。仅来源项目READ可投影版本页及父菜单壳，不提升业务资产/配置/测试数据全局写权限；关联追溯继续项目级。历史版本的name/description/categoryId/format保存不可变快照，旧来源无分类记录时明确未知，不以当前分类冒充。

页面表格依modern-web-guidance引导采用“Use standard `<table>` elements”与“Add a `tabindex="0"` attribute”，保留表头、区域标签、键盘横向滚动及分页；未引入新的UI依赖。

最终后端v9 `.build/global-assets-package-v9.log` BUILD SUCCESS：agent-integration 134项（128通过、6条件跳过），system-setting权限投影7项通过，app权限清单2项通过。合计143项中137实际通过，6项原生MySQL已另行全部执行通过。v9包含来源上下文与全局授权分离、仅来源READ的版本页投影及历史元信息冻结等最终后端差异。该命令跳过前端复制步骤，验证对象是后端包；前端生产资源另行构建验证，不宣称此后端JAR已内含最终前端资源。

浏览器后续轮次已换为隔离MySQL（13326、独立工作区数据目录），使用真实分类Controller/Service与事务、新数据Controller/Service及本地原文件存储。已从页面创建“订单样本”分类、上传CSV自动归类、人工新建JSON、批量设置分类及说明、逐项批量编辑CSV与JSON。接口实际读回CSV金额44.40、JSON金额55.5；不可变v1仍保留原用途与原内容。选中历史版本的固定链接实际打开v1，未读成当前v2；原始CSV下载字节与最初上传的74字节完全相同。证据`mysql-*.json/txt/png`均为合成测试数据，不包含生产数据。截图已实际打开检查。

前端完整v6生产构建通过，最终全量类型检查通过；最后补充组织切换销毁分类抽屉、关闭批量/导入等弹层后，行为测试仍22/22通过，正在以增量类型检查及相同生产Vite配置再次构建到独立输出目录，供最终复验。原构建存在既有eval、混合动态导入和大chunk警告，本轮未新增eval或调整依赖来规避警告。

后端包完整性额外检查：`python .build/verify-global-package.py`通过，嵌入的12个关键编译类及160/162/163/164/165迁移均与当前target/源码逐字节一致。后端JAR SHA256 `ada99213c83c241ce5ac01a5d9c925746afba840f342645fd965fe074f9b3f64`。这只证明包内容，不替代部署启动、SSO、真实MinIO和容器health验证。

## 最终固化记录（覆盖前述迭代状态）

最终后端V13：168项实际通过、6项条件跳过；随后9857f1e0隔离MySQL6/6通过、无跳过。Runner按PASSED预检中的actor授权，无网页登录态可工作；删除保护使用FOR UPDATE当前读，重复读旧快照并发新租约测试通过。最终前端V10全量类型及生产构建通过；25项组件/API测试、Node52项通过。最终真实包CAS弹层可见且不被覆盖、JSON完整字段、两条批删刷新无错误均已复验。精确命令/摘要在validation-summary.json；JAR与资源哈希见delivery-evidence.md。旧v9/11与旧哈希仅作迭代历史，不作为最终交付包。

## 左殇初审修正与V11复审固化

初审518f4fe2报告文档8e305a4d-eba4-49c5-b882-d5508d64c0e8要求R1/R2修复。新增时序回归先红后绿，32/32通过、修复文件eslint通过。V11生产构建16分45秒成功；类型并行检查因本机内存不足主动中止，单独重跑退出0，中止轮次未计通过。V11真实MySQL页面验证固定versionId、并发66.6保护/409草稿保留及延迟关闭后新建保存刷新通过；生产截图实际打开检查。后端未改，沿用V13与原生6项真实证据。
