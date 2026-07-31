# task005 - P0 治理、限流、幂等与安全验收

## 目标

补齐管理员治理、限流、幂等、安全测试和多客户端验收，确保远程 MCP 能安全上线。

## 范围

- 管理员 Agent Token 治理页面/API
- 全局 Agent 策略
- 限流
- 幂等
- 安全测试
- 多客户端端到端验收

## 实现要点

1. 管理员治理能力：
   - 查看 Token 元数据
   - 按用户、状态、客户端、最近使用时间筛选
   - 强制禁用/吊销
   - 配置是否允许个人 Token
   - 配置最大 Token 数、最长有效期、可用 scopes、允许 Tool、限流策略
2. 管理员不能查看完整 Token 明文。
3. 限流维度：
   - Token + Tool
   - Token + IP
   - 用户 + 项目
   - 系统全局
4. 写操作支持：

```http
Idempotency-Key: <client-generated-id>
```

5. 幂等键按 `userId + toolName + idempotencyKey` 去重。
6. 安全测试覆盖：
   - 越权
   - IDOR
   - 项目越界
   - scope 绕过
   - 重放
   - SQL 注入
   - 日志脱敏
   - Token 泄露扫描

## 验收标准

- 管理员可治理 Token，但不能看到明文 Token。
- 429 响应包含可理解的限流信息。
- 写 Tool 重试不会重复创建或重复回写。
- Token 越权、项目越界、scope 绕过测试全部失败并返回正确错误码。
- 多客户端完成至少 `tools/list` 和一个只读 Tool 验收。
- 技能包、日志、数据库、前端列表均无完整 Token。
