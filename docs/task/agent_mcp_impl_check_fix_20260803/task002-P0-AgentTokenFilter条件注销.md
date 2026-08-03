# task002 - P0 AgentTokenFilter 条件注销

> 问题：MCP-003  
> 依赖：无（可与 task001 并行）  
> 状态：待开始

## 目标

避免 Agent Token 请求在 `postHandle` 中注销请求进入前已存在的 Web/JWT 登录身份；仅清理由本次 Filter 建立的临时 Subject。

## 范围

- `AgentTokenFilter.java`
- `AgentTokenFilterTests.java`
- 必要时补充与 `AgentTokenContext` / `SessionUtils` 清理相关的说明

## 改造要点

1. 定义 request attribute，例如 `AGENT_TOKEN_LOGIN_ESTABLISHED`。
2. `onPreHandle`：
   - 记录进入前 `subject.isAuthenticated()`。
   - 仅当未认证时执行 `login`，并标记 attribute=`true`。
   - 已有 Web 会话时不 `login`，attribute=`false`；仍注入 `AgentTokenContext` 与项目线程上下文。
3. `postHandle`：
   - 仅当 attribute=`true` 时 `logout()`。
   - 始终 `AgentTokenContext.clear()` 与 `SessionUtils.clearCurrentProjectId()`。
4. 安全增强（建议一并做）：
   - 若已有 Web 身份且 `subject` 用户与 Token `userId` 不一致 → 直接 403，避免身份串用。

## 验收标准

- 「已 Web 登录 + Agent Bearer」请求结束后，Subject 仍保持认证。
- 「仅 Agent 登录」请求结束后，Subject 已 logout，上下文已清理。
- MCP GET 405 行为不受影响（既有逻辑保留）。

## 非目标

- 不重构 `ApiKeyFilter`（同类问题可另开任务）。
- 不改变 Token 校验、限流、项目白名单解析主路径（除非为一致性校验所需）。
