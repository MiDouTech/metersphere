# task008 - P0 - Agent 聊天 SSE 接口、执行查询、停止与重试

## 状态

进行中。已实现聊天 SSE、持久化有序事件、执行查询、真实取消、失败/取消重试和 afterSequence 恢复；待补跨节点实时事件总线、心跳和代理环境验收。

## 目标

提供【生成用例】专用 Agent HTTP API，将编排器的消息、工具、草稿、usage 和错误以统一 SSE 协议推送给前端，并支持执行查询、取消、重试和断线恢复。

## 依赖

- task004 Provider 流。
- task005 Agent 编排器。
- task006、task007 Agent 工具。

## 实现范围

### 1. 接口

```http
POST /functional/case/ai/agent/chat
POST /functional/case/ai/agent/chat/cancel
POST /functional/case/ai/agent/chat/retry
GET  /functional/case/ai/agent/execution/{requestId}
GET  /functional/case/ai/agent/execution/{requestId}/events?afterSequence={n}
```

`/chat` 返回 `text/event-stream`。如果现有网关不支持 POST SSE，可采用创建执行后 GET 订阅的两步接口，但协议必须统一。

### 2. SSE 事件

- execution-start。
- message-start。
- content-delta。
- reasoning-status，仅展示简短工作状态，不暴露内部推理。
- tool-call、tool-result。
- drafts-changed。
- usage、warning、error。
- message-completed、execution-completed。

所有事件包含 requestId、sequence、timestamp；相关事件包含 messageId/toolCallId。

### 3. 断线恢复

- 客户端可通过 Last-Event-ID 或 afterSequence 恢复。
- 服务端从事件存储恢复，不能只依赖单节点 emitter。
- 文本 delta 可合并持久化，保证恢复后得到完整 assistant message。

### 4. 取消

- 校验 requestId 属于当前项目和用户。
- 设置 cancelRequested，并调用 Provider cancel handle。
- 阻止新工具执行，运行中工具按可取消能力停止。
- 重复取消幂等。

### 5. 重试

- 只能重试 FAILED/CANCELED 的执行。
- retry 创建新 requestId 并关联 retryOfRequestId。
- 用户可选择沿用原模型或当前会话模型。
- 写工具依赖 toolCallId 幂等，不能重复创建草稿。

## 验收标准

- 文本、工具状态、草稿变化和 usage 按顺序推送。
- 页面断网后可从最后 sequence 继续恢复。
- 多节点部署不因连接落到不同节点而丢失最终状态。
- 无权用户不能订阅、取消或重试他人执行。
- 取消后不再产生新文本和草稿。
- 重试不会重复执行已成功写工具。

## 测试要求

- SSE 正常、工具调用、错误和完成事件顺序测试。
- 断流和 afterSequence 恢复测试。
- 取消竞态和重复取消测试。
- 重试和写工具幂等测试。
- 跨用户事件订阅越权测试。
- 反向代理下 SSE 超时和心跳测试。
