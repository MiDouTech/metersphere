# 2026-10-28 压缩与剩余整改实施记录

基线：[详细方案](msp-compression-and-outstanding-remediation-plan-20261028.md)。当前状态：部分完成，未达到全部测试通过；GitHub完整校验待执行。

## 实现追踪

|需求|本次代码与链路|状态|
|---|---|---|
|正文压缩|新增SDK FunctionalCaseBlobCodec，功能用例Blob与Mind DTO五正文列配置pack/unpack；保持单层ZIP、有界兼容三层、严格UTF-8/CRC/条目校验|6项codec测试、真实MyBatis/MySQL往返通过；实际损坏内容仍需可靠来源恢复|
|写入校验|FunctionalCaseService新增/编辑/导入新增与更新，STEP JSON数组结构/字段类型/尾随内容/大小校验|写入校验回归通过；AI等直接mapper路径仍需全入口审计|
|异常协议|RestControllerExceptionHandler仅识别明确BodyFormatException原因链，返回安全422与traceId，避免通用500|真实损坏样本拒绝通过；HTTP部署验收待执行|
|历史诊断|scripts/maintenance/diagnose-functional-case-body.py，只读HEX、输出摘要/层数/分类，不输出正文|真实370字节分类NEEDS_SOURCE；没有清空或改写数据库；自动修复/CAS恢复工具尚未实现|
|前轮R1/R2/R3/R4/R5/R7/UX|保留并纳入本次提交的敏感事实、导入门禁、执行/附件同步、导出等级、Node契约、Token/表格/全选/自适应概览整改|定向测试通过；方案中全字段同步、实际客户端、历史扫描与真实部署E2E仍未闭环|

## 实际执行的验证

证据：[本次日志](../testing/msp-compression-fixes-20261028/)。

- Maven真实reactor定向测试：`mvnw.cmd -pl backend/services/agent-integration -am -Dquality.mysql=true -Dtest=FunctionalCaseBlobCodecTests,FunctionalCaseBlobRoundTripTests,FunctionalCaseDefinitionStepsTests,WeeklyRemediationMigrationMySqlTests,AgentSensitiveDataSanitizerTests,AgentFactCipherTests,AgentCaseImportStageTests,AgentAssetExecutionViewTests,DefaultHubExecutionProjectionTests,FunctionalCaseExportRowMapperTests,AgentDirectExecutionTests -Dsurefire.failIfNoSpecifiedTests=false test`，32项通过、0失败、0跳过。包括3项真实MySQL测试（mapper往返及2项迁移/补偿），随机临时库已清理，没有启动新MySQL。
- `pnpm --dir frontend run test:selection-toggle`：6项通过。
- `pnpm --dir frontend run test:weekly-remediation-sfc`：8个SFC语法/模板检查通过，不等同全项目类型检查。
- `npm --prefix metersphere-mcp run build`及14项Node协议/stdio测试通过。
- `python scripts/maintenance/diagnose-functional-case-body.py backend/framework/sdk/src/test/resources/functional-case-corrupt-steps.hex`：NEEDS_SOURCE，退出2是诊断发现需恢复，并非恢复成功。
- 本机完整前端build在768MiB与1536MiB堆上限均OOM，未到生产打包成功；不继续提高本机占用。
- `mvnw.cmd -pl backend/app -am -Dquality.mysql=true -DskipTests=false package`失败，system-setting集525项中374项上下文错误，根因为DockerNotPresentException，其余模块未全量跑完。没有跳过这些错误或将定向测试冒充全量。

## 未完成门槛与后续

新增GitHub独立校验workflow，在修复分支验证完整前端构建及有Docker runner的后端全测试打包；不会修改测试使其绕过失败。结果需在合并前核对。

尚未执行镜像构建/容器健康、部署后真实Web/MCP E2E、全历史库升级、密钥及留存矩阵。测试服务器损坏步骤未恢复；必须从原文件、版本或备份找可靠内容，禁止猜测。详案第8、9节其余项目仍按逐项证据验收，不能称全部修复。

当前修改文件见git提交清单；前端API/权限保持原入口，正文变更不新增业务API或数据库格式迁移。迁移135/136属于前轮未提交整改，本次一并交付。未提交本机临时产物/凭据；日志只作验证证据。

## 后续补验与改动

- 新发现历史缺ID步骤每次读取随机生成ID，会影响执行契约/幂等比较。现改为由caseId、位置和内容派生稳定UUID；重复读取一致、不同用例隔离，步骤回归3项通过。
- 正文格式异常直接抛出和MyBatis包装异常使用同一安全422协议；SDK codec 6项及错误处理器15项Maven测试通过。
- MCP Node完整31项测试通过；只读诊断工具4项单元测试通过。
- GitHub运行40671265144及40672471196的前端完整类型检查/Vite构建通过。后续通过frontend Git tree身份校验复用该产物，不重跑无关前端构建。
- 后端远端全测先遇缺frontend/dist，已串行传递真实产物；后遇Docker客户端API1.32与服务端最低1.40冲突，已指定1.44。运行40674983495进一步确认旧MinIO镜像404导致上下文失败；官方原二进制地址410、Quay匿名读取未成功。
- 新增deploy/quality-verify/Dockerfile.minio-test-source，固定官方2023版本源码commit c7f7e67a100ce35af559e3f49a2ed0b67deaa919构建真实测试依赖，尚待远端验证。生产MinIO配置不变，不换Mock。
- GitHub已提交修复分支；PR #4面向chenqifen、#5面向main均保持草稿，未合并。代码/环境验收尚未全部通过，不能关闭所有需求。
- 本次分支提交因本机资源保护禁用本地Husky完整检查，未宣称钩子通过；完整前端已在远端补验通过。新后端修改继续进入真实全测。

## MCP批量读取隔离与公共配置补修

- 新增AgentCaseDTO.contentError：异常行steps为null，不是零步骤成功；warnings列出错误码及caseId。CASE_BODY_INVALID为正文异常，CASE_CONTENT_READ_LIMIT为隔离预算不足，应缩小范围重试。单条get仍严格失败。
- AgentFunctionalCaseExportService保持正常批量路径，只有明确BodyFormatException才按二分隔离；最多64次读取，10秒软预算（在查询之间检查，单次数据库查询仍受原事务超时限制），累计正文估算32MiB。权限/数据库/消失异常不吞掉。
- FunctionalCaseService.getAgentCaseDetailsForSearch使用独立只读事务，避免一次坏记录读取将外层事务标为回滚；其他严格调用保留原事务语义。
- 3项坏正文隔离测试、原500条3批读取测试、SDK/错误协议/配置与真实mapper往返定向reactor共32项通过，0跳过；与前面的32项集合存在重叠，不累加成64项。
- 下载技能包升级1.4.1并加入contentError不可执行说明；Node README同步，运行时目录和校验和测试通过。
- 远端完整测试越过MinIO依赖后暴露独立模块缺LocalLoginProperties，新增SystemLoginConfiguration统一注册Local/Miduo属性，绑定测试通过，不禁用安全组件。
- 测试MinIO源码镜像引入按Dockerfile/OS/架构身份缓存，成功构建后立即保存，后端测试失败不会丢失缓存；后续复验不重复构建未改动依赖。
- 原始日志保留本机忽略目录，仓库提交verification-progress.json记录摘要/命令与GitHub链接；未将本机未提交的原始恢复候选内容公开。全量CI与部署E2E尚未通过，PR继续为草稿。

## 历史恢复工具及最终本机定向检查

新增FunctionalCaseBodyRepairTool维护入口（无公开HTTP）：默认只读DRY_RUN，apply要求项目/原BLOB摘要/版本/来源内容摘要确认，校验活跃运维操作人；只允许五正文列，先锁定并检查长度，避免无界加载。Linux私有备份目录700/文件600，文件及目录fsync后才改数据库。正文、单调版本和CASE_SYNC事件同事务，失败回滚；ZIP时间戳规范化，重复请求不重复事件。rollback仅接受目标/摘要匹配的私有原始备份，同样CAS和备份，不覆盖新编辑。

[维护命令与限制](../../scripts/maintenance/README-case-body-repair.md)。本次没有对MSP业务库执行恢复，仍缺可信原始内容。工具不将Windows不安全备份方式作为回退，不自动填空步骤；恢复及回滚都需要先预览。

其他补修：历史用例创建人已删除时保留原ID、名称为空，不再空指针导致详情500；ResourceEditAdapterRegistry的可选适配器列表不再阻止独立模块启动，实际编辑仍拒绝未安装的类型。

当前源码23个文件定向javac编译通过；17个测试类共63项，62成功、0失败、1项因本机非POSIX中止（恢复重试Linux分支）。不是全量Maven/容器/部署E2E通过。Linux CI须实际完成恢复/回滚/失败重试。前端完整构建已有远端通过证据，Node31项、前端逻辑6项及Python4项保持已通过记录；未受后续Java修改影响，不重跑这些构建。

新一轮全量CI将继续核对模块启动修复及新维护工具。仍未满足全部验收，chenqifen/main合并保持草稿，不宣称完成。

## 全量 CI 业务断言阶段的前置条件整改

运行40687296843实际进入529项system-setting测试，349项失败主要发生于BaseTest真实/login前置步骤（管理员入口默认关闭），另有11项测试SQL错误。仅在测试基类的隔离数据库显式开启入口；生产默认值、密码验证、权限判断不变。测试SQL按稳定组织编号100001定位，通知夹具使用独立角色避免污染内置project_admin权限，重复组织夹具仅重置其明确的合成身份。未修改业务断言或忽略测试。

加入全量Maven成功后的远端Docker镜像构建、独立依赖启动、readiness及前端首页HTTP检查，记录镜像身份并清理本次CI项目卷；不会在本机启动环境。该阶段当前未执行，不等同于浏览器端到端验收。
