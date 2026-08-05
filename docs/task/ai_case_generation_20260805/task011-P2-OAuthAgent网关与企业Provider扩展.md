# task011 - P2 - OAuth、Agent 网关与企业 Provider 扩展

## 状态

部分完成

## 目标

在基础 Provider Adapter 之上扩展 OAuth、企业 Agent 网关、MCP 或自定义协议接入能力。

## 实现范围

- OAuth Provider 接入：
  - 授权跳转。
  - 回调处理。
  - access token 加密存储。
  - refresh token 加密存储。
  - 过期检测。
  - 刷新。
  - 撤销。
- Agent 网关接入：
  - MCP 或自定义协议适配。
  - 企业 Agent 能力声明。
  - 任务上下文传递。
  - 错误码映射。
- 额度统计：
  - 项目维度。
  - 用户维度。
  - Provider 维度。
- 企业连接授权：
  - 组织授权。
  - 项目授权。
  - 个人授权。

## 边界说明

Cursor、Codex、WorkBuddy 等产品不一定向第三方系统提供统一模型 API。接入前必须确认官方能力或企业网关能力，不得凭产品名称假设可直接调用。

## 验收标准

- OAuth 授权、刷新、撤销流程可用。
- Agent 网关能返回明确能力声明。
- 未提供开放 API 的外部产品不得在系统中标记为已接入。
- 额度统计准确记录。

## 验证要求

- OAuth 回调测试。
- Token 过期刷新测试。
- 撤销授权测试。
- Agent 网关协议测试。
- 额度统计测试。

## 执行记录

- 已新增企业 Agent 网关能力声明 DTO：`AiAgentGatewayCapabilityDTO`。
- 已新增能力声明服务：`AiAgentGatewayService`。
- 已新增能力声明接口：`GET /ai/agent-gateway/capability/{gatewayId}`。
- 对 `cursor`、`codex`、`workbuddy` 等产品名称做了安全边界提示：不能仅凭产品名称认定已提供开放模型 API，必须接入官方开放能力或企业 Agent 网关后才可启用。
- 当前接口会明确返回 `configured=false`，避免将未配置网关误标为已接入。

## 未完成 / 未验证

- 未实现 OAuth 授权跳转。
- 未实现 OAuth 回调处理、access token / refresh token 加密存储、过期检测、刷新、撤销。
- 未实现 MCP 或自定义协议真实适配。
- 未实现企业 Agent 任务上下文传递、错误码映射。
- 未实现项目/用户/Provider 维度额度统计。
- 未实现组织授权、项目授权、个人授权。
- OAuth 回调、Token 刷新、撤销授权、Agent 网关协议、额度统计测试均未完成。
