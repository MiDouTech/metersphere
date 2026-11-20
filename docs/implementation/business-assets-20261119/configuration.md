# 业务资产开发配置（开发草稿）

产品基线为已批准 v1.0；本文件不代表已部署或正式验收通过。

- 新配置：`msp.shared-configuration.encryption-key`，环境变量 `MSP_SHARED_CONFIGURATION_ENCRYPTION_KEY`。值为安全生成的 32 字节随机密钥的 Base64，不写入仓库、命令行参数、报告和日志。须在整个历史版本及证据保留周期内安全备份；未实现在线换钥，不得直接替换现存密钥。
- 缺少密钥时列表元数据仍可读，配置及证据写入/解密明确失败；不得以空值或成功掩盖失败。
- 权限：`BUSINESS_ASSET:READ/WRITE`、`SHARED_CONFIGURATION:READ/WRITE/RESOLVE`。RESOLVE 仅允许已连接且仍运行的执行器按固定版本读取实际值，不自动授予普通读取者。
- Agent Token 必须显式授权新 scope：`BUSINESS_ASSET_READ/WRITE`、`SHARED_CONFIGURATION_READ/WRITE/RESOLVE`；旧通配 scope 不自动扩权。
- 文档原件不超过 2 MB，全部加密保存。Markdown/TXT/JSON 支持 UTF-8 解析状态；其他格式保留原件并明确显示不支持解析，不伪造成功。
- 执行器从 `/external-executors` 建立会话，`/events` 上报进度，`/commands` 拉取纠正，`/commands/{command}/response` 上报响应。任务上下文固定版本；不消费冻结动作、不使用旧 Runner 租约。
- 业务资产上传 `/business-assets`；`source.taskId` 是新执行器会话 ID，`source.executorId` 必须对应同一 Token 的已登记执行器。新建和更新均需要幂等标识；并发冲突保留版本，不覆盖当前指针。
- 历史迁移 148/149 保留原字节。153 将旧协议数据归档到 `retired_*`，有明确项目来源的历史知识按创建者权限迁入待归类版本，原始快照保留；没有项目来源的组织级知识保留于历史归档，不猜测项目或扩大可见范围。
- 当前没有新增外部监控平台或通知渠道；失败日志和审计供现有采集使用，不等同于已配置告警。

本地验证推荐：`./mvnw.cmd -pl backend/services/agent-integration -am "-Dtest=BusinessAssetServiceTests,SharedConfigurationServiceTests,ExternalExecutorServiceTests,BusinessGovernanceExceptionHandlerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`；前端在 `frontend` 运行 `npm run type:check` 和 `npm run build`；MCP 在 `metersphere-mcp` 运行 `npm test`。

历史证据兼容：156 将归档 hold 转入独立 historical_evidence_retention，个人与任务证据清理继续保留这些引用；不恢复旧协议入口。历史 facts 仍需安全提供原 agent.execution.facts-key（对应环境变量 AGENT_EXECUTION_FACTS_KEY）才能解密；新公共配置密钥不能替代原密钥。
