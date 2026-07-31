# task003 - P0 原生远程 MCP 服务与 Tool Registry

## 目标

提供标准远程 Streamable HTTP MCP 服务，供 Codex、ChatGPT、Cursor、WorkBuddy 和其他 MCP 客户端直接接入，替代本地 Node.js stdio 适配器。

## 范围

- `/api/mcp`
- MCP 初始化、心跳、工具列表、工具调用
- Tool Registry
- MCP 鉴权和权限交集校验
- 只读/写入 Tool annotations

## 实现要点

1. 新增标准端点：

```text
POST /api/mcp
GET /api/mcp
DELETE /api/mcp
```

2. 至少支持：
   - `initialize`
   - `notifications/initialized`
   - `ping`
   - `tools/list`
   - `tools/call`
3. 建立 `McpToolRegistry`，统一注册 Tool 名称、描述、入参 schema、权限、scope 和 annotations。
4. Tool 调用必须经过以下交集校验：
   - 当前用户状态
   - 用户 RBAC
   - 项目成员关系
   - Token 项目白名单
   - Token scopes
   - MCP Tool 安全策略
5. 写操作复用原业务 Service，不绕过原权限、工作流和审计。
6. 支持 `Authorization: Bearer msat_...` 和 `X-API-Key: msat_...`。
7. 认证失败返回统一 401；无权限返回 403；限流返回 429。

## 验收标准

- Codex/Cursor/ChatGPT/WorkBuddy 可完成 `tools/list`。
- 至少一个只读 Tool 可调用成功。
- 无权限项目不会出现在 `search_projects` 结果中。
- 写 Tool 无法绕过 RBAC、scope、项目范围。
- Tool annotations 正确标识只读和破坏性操作。
- 错误响应可被 Agent 理解，不出现 HTML 错误页。
