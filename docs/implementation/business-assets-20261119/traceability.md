# 业务资产重建实施追踪（开发草稿）

基线：产品文档 v1.0，2026-11-19 当前会话用户整版批准；文件位于 `../../product_md/MSP-业务资产重建与受控执行清理-产品文档-v1.0-20261119.md`。
分支：`codex/business-assets-rebuild-20261119`。既有文档迁移工作保留，不覆盖。

| 需求 | 前端入口与实现 | 后端接口 | 数据与配置 | 必测边界 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 业务理解持续沉淀 | business-assets.vue 列表、编辑、上传、分支详情、归类树 | BusinessAssetController → BusinessAssetService，/business-assets | 150：资产、不可变版本、幂等记录、审计 | 项目隔离、幂等、冲突保留、合并关联、待归类 | 已编码；服务集成与 HTTP 合约测试，待端到端验收 |
| 版本切换 | BusinessVersions.vue 比较、下载和替换 | /business-assets/{id}/versions 与当前版本选择 | 当前指针、历史审计、归类 identity 同步 | 回切后新增版本不重复、重复身份拒绝、旧任务不漂移 | 已编码；回归测试通过，待真实运行验收 |
| 公共配置 | shared-configuration.vue、SharedConfigurationVersions.vue | SharedConfigurationController → SharedConfigurationService，/shared-configurations | 152：加密、配置版本、文档原件、任务固定引用 | 越权、脱敏、解密失败、解析重试、批量回滚、编辑竞态 | 已编码；复审发现的竞态及大小写解析修复已通过回归 |
| 执行器接入与纠偏 | external-executors.vue 监控、纠偏、响应、上下文替换 | ExternalExecutorController → ExternalExecutorService，/external-executors | 151/154：会话、事件、纠正、加密图片、固定上下文 | Token 所有权、重放、跨项目、纠偏失败、超千条历史不阻塞新指令 | 已编码；模拟执行器服务集成已覆盖，真实执行器联调待验证 |
| 删除受控执行 | 删除专属入口、页面及子导航 | 删除 AgentGovernedExecution/Protocol/Knowledge 专属接口与工具 | 148/149 保持原字节；153 归档旧表并保留历史知识；155 导航权限 | 新上报不依赖旧租约；无来源不猜项目；旧数据不扩权 | 已清理代码；真实 MySQL 迁移 8 项检查通过 |
| AI 协议 | 任务上下文、MCP/技能包和独立 Token scope | BuiltinAgentMcpToolConfig、businessAssets.ts → 真实服务 | JSON schema 1.0；原生技能包 1.5.0；Node MCP 0.10.0 | 必填来源、当前权限校验、幂等重放、秘密防泄露 | 已编码；MCP 契约及 Node 45 项测试通过，最终回归通过 |

入口→Vue→HTTP→Controller→Service→权限校验→事务写库→响应为本次验证链路；复用 JdbcTemplate、项目权限与 Agent Token 模型。新链路的异常通过 BusinessGovernanceExceptionHandler 转为用户可理解的错误及 traceId，安全诊断不记录原始消息或堆栈。

失败行为：不以空数据掩盖请求失败；并发冲突保留提交版本；重复幂等键不同内容拒绝；日志只记录标识、阶段、稳定错误分类和安全根因，禁止输出文档、密码或原始异常消息。

当前状态：代码开发、自测及 R4 独立代码审查阶段结束；容器与真实端到端运行验证阻塞。正式部署验收待用户通知；没有将编译、模拟执行器或服务测试计为真实部署验收。详细验证命令、结果和未运行检查见 verification.md；精确差异及审查结论按 review-rN 记录。

执行器交付范围：提供独立接入协议、HTTP/MCP 工具、监控和纠偏回执链路，不新建一个替用户操作浏览器的 AI 运行器。现有 AI 执行器须实际接入工具后才能监控其执行；本次尚无真实执行器联调证据。
