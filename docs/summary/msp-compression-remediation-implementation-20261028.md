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
