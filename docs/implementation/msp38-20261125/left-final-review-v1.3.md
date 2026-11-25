# MSP-38 v1.3复审结论

审查者质量-左殇0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0，2026-11-25。
绑定patch SHA256：2902b9e69e1bc8011449687f821aee2a1e57f4792b188baf7fe123f7ab83cb06，实测一致。结论：需修改，暂不可进入提交合并。

## 前轮问题状态

- 系统统计CR-07：实现层静态复审关闭。SQL按系统/模块GROUP BY返回计数，模块当前组织一次读取再分组，保留Hub、目录和删除过滤，不再逐系统查模块。
- 作者姓名N+1：实现层静态复审关闭。snapshotExcel内创建局部assetAuthors映射并传给批次，computeIfAbsent缓存实际引用ID及缺失用户空串；不跨任务缓存，不扩展为全员读取。
- HTML/TEXT和合并STEP两项：沿用前轮已核对的v1.2修订结论，本轮未发现对应回退。
- 日志P1：部分修复，但仍未关闭，见下项。

## P1 Agent技术边界仍可穿透至原始堆栈日志

位置：backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentCaseImportService.java:52–61，safeOperation。

该边界只catch DataAccessException及MSException。operation.get()抛出其他运行时技术异常（例如IllegalStateException、NullPointerException）时没有安全转换。precheck/get/action的技术链发生此类异常仍可能进入基线RestControllerExceptionHandler.handleException→recordInternalError（第230至238行），后者把原始Throwable交给LOGGER.error。已读LogUtils同样没有统一脱敏。

触发验证建议：在get或action的非授权数据库读取/结果处理依赖注入IllegalStateException，其message/cause含合成secret sentinel；实际MVC同时注册原global handler。预期安全500/错误协议、日志不含sentinel和ThrowableProxy，仍有阶段和关联编号；当前safeOperation不能拦截此异常，global handler会记录原始堆栈。此为静态确定的未捕获路径，未实际运行该故障注入，不声称线上已泄漏。

修复建议：对本任务三个边界补齐其他技术RuntimeException的安全转换，同时明确保留原有权限/治理拒绝及正常业务验证，不能把所有拒绝转500。以真实异常类型列白名单保留授权错误，而非为方便吞掉授权检查。不要修改全局日志策略或扩成无关功能。

## 已核对的日志修订

新CaseAssetOperationDiagnostics不输出message/Throwable/stack，phase/ref/rootType/SQLState有格式限制，SQL错误码白名单字段保留；因果链循环有identity去重。新advice限定两个资产controller的DataAccessException，最高优先级，返回500及既有structuredError；未改变global handler。

已核对预检解析catch、worker扫描/逐项/补偿/过期及导出快照/生成/清理注册/通知/下载相关改动，均改用安全辅助记录。size失败保留EXPORT_TOO_LARGE。不能由这些分支安全推断未捕获的技术异常同样安全；也不宣称已接入告警。

## 验证证据和限制

读取作者verification-summary.json：应用113项、80执行通过、33跳过；隔离套件49项无失败错误跳过。以上为作者提供证据，非本审查者重跑。前轮本审查实际执行附件草稿回归1项通过；未重跑Java/MySQL/MVC或浏览器。

本轮为新增两文件及性能/日志受影响路径复审，结合前轮分组报告；不把未读的大文件无关路径宣称全部逐行批准。真实部署、健康、端到端验收仍未完成；正式.cn映射待用户确认。没有提交、合并、推送、部署或将任务设完成。
