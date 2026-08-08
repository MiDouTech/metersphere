# task004 - P0 - WSS 长连接、流式协议与多节点路由

## 状态

实现中：WSS、心跳/离线、序号校验、消息上限、有界队列、Redis 节点消息总线及接收/首内容/空闲/总时长超时已落地；断线 ACK/续传、持久化消息总线和完整集群故障测试未完成。

## 目标

建设 Agent Bridge Gateway，使用户设备通过出站 WSS 建立可认证、可恢复、可取消的长连接，并把 Agent 事件可靠转化为现有浏览器 SSE 事件。

## 依赖

- task002 数据模型。
- task003 配对与设备身份。

## 当前基础

- `AiAgentGatewayService` 当前支持远程 MCP/CUSTOM_HTTP 单次调用。
- 用例 Agent 已有执行事件持久化和浏览器 SSE。
- 当前没有设备长连接、心跳、WSS 路由和 Bridge 协议。

## 实现范围

### 1. WSS 入口

```http
WS /ai/agent-bridge/ws
```

握手必须校验设备令牌、签名挑战、协议版本、Bridge 版本和设备撤销状态。

### 2. 消息信封

统一字段：

```json
{
  "protocolVersion": "1.0",
  "type": "content.delta",
  "requestId": "request-id",
  "sequence": 12,
  "timestamp": 1786156800000,
  "nonce": "nonce",
  "payload": {}
}
```

平台下行至少包括 `connection.probe/execution.start/execution.cancel/tool.result/session.close`。

Bridge 上行至少包括 `connection.ready/connection.heartbeat/execution.accepted/message.start/content.delta/tool.call/usage.reported/execution.completed/execution.failed/execution.cancelled`。

### 3. 可靠性

- `requestId + sequence` 唯一去重。
- Bridge 断线重连携带最后确认序号。
- 平台只向浏览器推送已持久化事件。
- 高频 delta 可批量持久化，但完成、工具和错误事件必须立即持久化。
- 取消使用显式 ACK；超时后平台将执行标为取消并拒绝迟到工具调用。
- 单次执行设置接收、首事件、空闲和总时长四类超时。

### 4. 多节点

- Redis 保存 `deviceId -> gatewayNodeId`，短 TTL 随心跳续期。
- 使用 Redis Stream 或内部消息总线向设备节点投递任务。
- 节点下线时清除或等待路由 TTL 过期。
- Bridge 自动重连后刷新路由。
- 不依赖永久负载均衡粘性。

### 5. 背压和容量

- 限制单设备并发执行数、单连接待发送队列和最大消息大小。
- content delta 超过阈值时合并，禁止无限缓存。
- 慢消费者触发明确错误和执行中断。
- 心跳频率和离线判定可配置。

### 6. 与现有远程 Gateway 的关系

保留 `/ai/agent-gateway` 远程 MCP/CUSTOM_HTTP 能力。新增 Bridge Gateway 使用独立 Controller、表和协议，禁止通过向现有 Gateway 填写本机 URL 的方式替代。

## 验收标准

- Mock Bridge 能完成连接、流式、工具调用、取消、断线和恢复。
- 浏览器仍只消费现有 SSE，不直接连接用户设备。
- 重复事件不会产生重复消息、草稿或工具执行。
- 任一 Gateway 节点重启后设备可重连，状态可解释。
- 撤销设备、授权过期或协议不兼容时立即拒绝执行。

## 测试要求

- WebSocket 协议契约和版本兼容测试。
- 乱序、重复、丢失、迟到事件测试。
- 心跳、离线、重连和节点重启测试。
- Redis 路由 TTL 与多节点投递测试。
- 大消息、慢消费者、背压和队列上限测试。
- 取消 ACK、取消超时和取消后工具调用测试。

## 非目标

- 本任务使用 Mock Bridge，不声明真实 WorkBuddy/Codex/Cursor 已接入。
