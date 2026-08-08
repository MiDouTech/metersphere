# task016 - P2 - OAuth、企业 Gateway、MCP 与多 Provider 扩展

## 状态

未开始。

## 目标

在 P0 用例 Agent 闭环稳定后，为企业私有模型、外部 Agent 平台和多 Provider 提供可管理、可审计的扩展能力。

## 依赖

- task001–task013 已完成并稳定运行。

## 实现范围

### 1. 多 Provider 独立适配

- 按 providerType 注册 Adapter。
- 每个 Adapter 声明流式、工具、视觉、usage 和取消能力。
- Provider 级超时、重试、限流和错误映射策略。
- 统一契约测试。

### 2. OAuth

- 授权跳转、state、PKCE、回调。
- access token/refresh token 加密存储。
- 过期检测、并发安全刷新和撤销。
- 组织、项目和个人授权范围。
- 前端连接列表、状态、授权、刷新、撤销和脱敏展示。
- OAuth 连接与模型源建立明确关系。

### 3. 企业 Agent Gateway

- 配置、能力发现、健康检查和停用。
- 项目、组织和个人授权。
- 超时、重试、限流、usage 和审计。
- SSRF 防护始终阻止云元数据、环回和危险链路本地地址；私网允许策略不得绕过元数据保护。

### 4. MCP

确有外部 MCP 需求时支持：

- initialize。
- notifications/initialized。
- tools/list。
- tools/call。
- 协议版本和能力协商。
- 幂等写工具。

明确区分：

- MeterSphere 作为 MCP Server，供外部 Agent 调用。
- MeterSphere 用例 Agent 作为 MCP Client，调用企业工具。

两者不得混用权限和凭据。

### 5. 与用例 Agent 集成

- 企业模型出现在项目可用模型列表中。
- Gateway/MCP 工具进入 Agent 工具注册中心前经过管理员白名单。
- 外部工具不能绕过草稿和人工确认边界。
- usage 统一关联项目、用户、Provider、会话和 requestId。

## 验收标准

- 不同 Provider 的流式和工具行为符合统一契约。
- OAuth 刷新并发安全，凭据不明文返回。
- Gateway 无权访问和 SSRF 请求被拒绝。
- MCP 完成真实 initialize、tools/list、tools/call 联调。
- 企业扩展不能绕过项目模型白名单和正式保存确认。
- 前端能够管理连接并展示脱敏状态。

## 测试要求

- 每 Provider 契约测试。
- OAuth state、PKCE、刷新竞态、撤销和越权测试。
- Gateway SSRF、超时、限流和错误映射测试。
- MCP 协议真实客户端兼容测试。
- 企业模型到用例 Agent 的端到端测试。

## 非目标

- 不在 P0 聊天闭环完成前提前扩展新的企业 Provider。
