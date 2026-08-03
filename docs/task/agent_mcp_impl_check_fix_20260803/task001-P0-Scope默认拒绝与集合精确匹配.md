# task001 - P0 Scope 默认拒绝与集合精确匹配

> 问题：MCP-001、MCP-002  
> 依赖：无  
> 状态：待开始

## 目标

修复 Token scope 为空时放行，以及使用 `StringUtils.contains` 子串匹配导致的误授权，使 MCP Tool 授权改为「解析集合 + 白名单精确匹配 + 显式覆盖关系」。

## 范围

- `AgentScopeAssert.java`
- `AgentTokenScope.java`（可新增解析/覆盖辅助方法）
- 建议新增 `AgentTokenScopeParser.java`（或等价工具类）
- Token 创建/更新校验：`AgentTokenManagementService`（scopes 非空且合法）
- 相关单测：`AgentScopeAssertTests`、`AgentTokenServiceTests`

## 改造要点

### 1. 默认拒绝（MCP-001）

- `assertScope` / `assertAnyScope`：`AgentTokenContext` 无 Token，或 `scopes` 空白 → 抛 `SCOPE_DENIED`，禁止 `return`。
- 不得以「Token 为空」作为跳过权限的条件。
- 若存在非 MCP 内部调用需求，另开显式 API，不复用当前默认路径。

### 2. 集合精确匹配（MCP-002）

- 统一解析历史分隔符（`,` `;` 空白等）为 `Set<String>`，去空、去重。
- 未知 scope：创建/更新阶段拒绝；运行时不得子串命中。
- `hasScope` 仅基于 Set 成员与显式 implies：
  - `AGENT_ALL` → 任意 required
  - `FUNCTIONAL_ALL` → functional 族
  - `BUG_WRITE` → `BUG_READ` + `BUG_WRITE`
  - 其余 `set.contains(required)`
- 禁止 `StringUtils.contains(scopes, AGENT_ALL)` 一类实现。

### 3. 存量与入口

- 创建/更新 Token：服务端校验 scopes 必填且白名单内。
- 存量空 scope Token：一律拒绝调用（更安全）；可选管理端提示补全。

## 验收标准

- 空 scope / null Token 调用受保护 Tool → 明确 scope 不足错误。
- `XAGENT_ALL`、`BUG_WRITE_EXT` 等不得获得权限。
- 合法多 scope、`AGENT_ALL`、精确 scope 仍可调用。
- 覆盖矩阵具备完整单元测试。

## 非目标

- 不在本任务改项目检索契约或 Filter 注销逻辑（见 task002/task005）。
- 不强制本迭代将 DB 存储改为 JSON 数组（可作为后续）。
