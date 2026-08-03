# task006 - P1 MCP Notification 与协议行为修正

> 问题：MCP-008  
> 依赖：可与 task004/005 并行  
> 状态：待开始

## 目标

对齐目标 MCP / JSON-RPC / Streamable HTTP 行为，消除 Notification 误回响应体等问题，并为真实客户端验收提供协议基线。

## 范围

- `AgentMcpStreamableService.handle`
- `AgentMcpStreamableController`（HTTP 状态码、Content-Type、空 body）
- 既有 GET 405 逻辑保持
- 协议相关单元/集成测试

## 改造要点

| 项 | 方案 |
|---|---|
| `notifications/initialized`（无 id） | HTTP 202 或 204，**空 body**；不生成带 `result` 的 JSON-RPC |
| 有 id 的请求 | 正常 JSON-RPC response / error |
| Content-Type / Accept | 按目标协议版本核对并文档化 |
| 批量请求 | 明确是否支持；不支持则返回标准错误 |
| Session | 继续无状态；文档声明不要求 `Mcp-Session-Id` |
| GET | 保持 405 + `Allow: POST` |

## 验收标准

- Notification 无响应体（或仅协议允许的空应答），客户端不因多余 body 失败。
- POST initialize / tools/list / tools/call 行为与当前 Codex 成功路径兼容。
- Cursor / WorkBuddy 在 TLS 可用域名上可完成连接探测（GET 405 可接受）。

## 非目标

- 不在本任务修复 `msp.ebcone.cn` Nginx TLS/schannel。
- 不替代四类客户端完整证据归档（见 task009）。
