# task010 - P1 - AI Provider Adapter 与模型连接管理

## 状态

部分完成

## 目标

建设统一 AI Provider Adapter，复用现有 `ai_model_source`、系统模型配置和个人模型配置，统一处理模型能力声明、鉴权、请求转换、流式响应、重试、限流和使用量统计。

## 实现范围

- Provider Adapter 抽象：
  - 模型能力声明。
  - 鉴权。
  - 请求转换。
  - 流式响应。
  - 超时。
  - 重试。
  - 限流。
  - 错误码转换。
  - Token 使用量统计。
- 支持系统模型。
- 支持个人模型。
- 支持连接测试。
- 支持系统默认模型回退。
- API Key 加密存储。
- 前端凭据掩码展示。

## 首期建议支持

- OpenAI 兼容 API。
- Azure OpenAI。
- DeepSeek。
- 通义。
- Ollama。

实际支持范围以项目已有模型配置能力和可用 Provider 为准，未接入的 Provider 不得标记为已完成。

## 验收标准

- 可配置至少一种模型连接并完成测试。
- 生成用例功能能通过 Provider Adapter 调用模型。
- 模型超时、限流、认证失败能返回明确错误。
- 凭据不会明文回显或写入日志。

## 验证要求

- Provider 单元测试。
- 连接测试接口测试。
- 流式响应测试。
- 凭据脱敏测试。
- 默认模型回退测试。

## 执行记录

- 已新增 Provider Adapter 抽象：`AiProviderAdapter`。
- 已新增默认实现：`DefaultAiProviderAdapter`，复用现有 `SystemAIConfigService` 和 `AiChatBaseService`。
- 已新增能力声明接口：`GET /ai/provider/capability/{modelSourceId}`。
- 已新增连接测试接口：`POST /ai/provider/test-connect`。
- 能力声明首期返回：
  - Provider 名称。
  - 基础模型名。
  - 支持能力：`CHAT_COMPLETION`、`CASE_GENERATION`。
  - 流式/OAuth/Agent Gateway 支持状态。
- 连接测试失败信息做了基础脱敏，避免 API Key、Token、Secret、Authorization 明文进入响应。
- 生成用例链路仍复用现有模型配置和 `AiChatBaseService`，未绕过已有鉴权。

## 未完成 / 未验证

- 未按 Provider 分别实现 OpenAI/Azure/DeepSeek/通义/Ollama 的独立 Adapter。
- 未实现统一流式响应封装。
- 未实现 Adapter 层重试、限流、Token 使用量统计。
- API Key 加密存储沿用现有模型配置能力，本次未新增加密机制。
- 未实现系统默认模型自动回退。
- Provider 单元测试、连接测试接口测试、流式响应测试、凭据脱敏测试、默认模型回退测试尚未补齐。

## 2026-08-06 补充执行记录（替代上述过时缺口）

- 已新增统一同步调用与 SSE 流式调用；流式仅在首块输出前重试/回退，输出后失败不重试，避免内容重复。
- 已实现 Redis 原子分钟限流（Redis 不可用时本机降级）、瞬态错误指数退避重试、项目配置默认模型回退、Provider 错误分类与脱敏。
- 已记录输入/输出/总 Token、耗时、成功状态和错误码；Provider 返回 usage 时采用真实值，否则使用明确的估算值。
- 功能用例生成服务已改为通过 Adapter 调用，并修复远程调用长事务、取消不可见和失败状态回滚问题。
- Provider 流式/重试/回退/不重复输出/脱敏测试均通过；仍未按品牌拆分独立 Adapter，复用现有 Spring AI Provider 实现。
