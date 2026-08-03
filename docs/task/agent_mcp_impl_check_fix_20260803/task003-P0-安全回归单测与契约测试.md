# task003 - P0 安全回归单测与契约测试

> 问题：MCP-001/002/003 验收配套  
> 依赖：task001、task002  
> 状态：待开始

## 目标

为第一批安全修复提供可重复的自动化回归，防止空 scope 放行、子串误授权、误注销 Web 登录再次引入。

## 范围

- `AgentScopeAssertTests` 扩充
- `AgentTokenFilterTests` 扩充
- 建议：MCP `tools/call` 缺 scope 契约测试（Controller 或 Service 层）
- 写 Tool 抽样：至少覆盖 1 个用例写、1 个缺陷写

## 必测用例

### Scope

| 场景 | 期望 |
|---|---|
| Token null | SCOPE_DENIED |
| scopes `""` / 空白 | SCOPE_DENIED |
| 未知 scope | 创建拒绝或调用拒绝（与实现策略一致） |
| `XAGENT_ALL` | 不得放行 |
| `BUG_WRITE_EXT` | 不得获得 `BUG_WRITE` |
| `AGENT_ALL` | 任意 required 放行 |
| `FUNCTIONAL_ALL` | functional 族放行，不放行 `PROJECT_WRITE` |
| `BUG_WRITE` | 覆盖 `BUG_READ`，不覆盖评论/附件/关联/删除 |
| 多 scope 精确组合 | 仅命中已授权项 |

### Filter

| 场景 | 期望 |
|---|---|
| 本次建立登录 | postHandle 后 logout |
| 进入前已认证且同用户 | postHandle 后仍认证 |
| 进入前已认证且用户不一致 | 403（若实现一致性校验） |
| GET `/api/mcp` 无 SSE | 405 + Allow POST |

### 契约

- 未携带有效 Token 的 `tools/call` 不进入业务 Service（或进入前被拒）。
- 缺所需 scope 的写 Tool 返回明确错误码/文案。

## 验收标准

- `.\mvnw.cmd -f backend\pom.xml -pl services\agent-integration -am test` 相关用例通过（依赖就绪后）。
- 测试报告可归档到本目录或 `docs/summary`。

## 非目标

- 不替代真实客户端联调（见 task009）。
- 不覆盖项目检索全部分页边界（见 task008）。
