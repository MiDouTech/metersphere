# task005 - P1 项目检索分页、契约冻结与 SQL 下沉

> 问题：MCP-004、MCP-006、MCP-007  
> 依赖：task004  
> 状态：待开始

## 目标

冻结项目模糊检索 Tool 契约，补齐分页响应，并将过滤、排序、count、分页下沉到数据库，消除内存全量过滤的扩展风险。

## 范围

- `AgentProjectSearchRequest` / 新增 `AgentProjectSearchResponse`
- `AgentProjectService.search`
- Project Mapper / 自定义 SQL（参数化 + LIKE 转义）
- `AgentMcpStreamableService`：`metersphere.project.search` / `list` schema 与执行
- 技能包 Tool 文档同步
- 可选别名：`search_projects` → 同一 Handler（若需要）

## 冻结契约（建议正式口径）

| 项 | 值 |
|---|---|
| 正式 Tool 名 | `metersphere.project.search` |
| 请求 | `keyword`, `page`(默认 1), `pageSize`(默认 20, max 100), `includeArchived`(默认 false) |
| 响应 | `{ items, page, pageSize, total, hasMore }` |
| 匹配字段 | 项目名称、内部 ID、UI 数字编号（文档写清） |
| 排序 | 名称完全匹配 → 前缀匹配 → 包含匹配；其次更新时间 DESC；再 id |
| `list` | keyword 空的分页枚举，或复用 search |

废弃/替换：仅 `limit` 无分页元数据的旧响应（迁移说明写入技能包）。

## 实现要点

1. 权限候选：用户 RBAC 可达项目 ∩ Token 项目白名单。
2. SQL：`IN` 候选集 + 模糊条件 + `COUNT` + `LIMIT/OFFSET`。
3. LIKE 前转义 `%` `_` `\`，禁止拼接原始 keyword。
4. `includeArchived` 与表字段（enable/deleted/归档）对齐产品定义。
5. `resolveProjectId` 走精确匹配查询，避免依赖大 limit 内存过滤。

## 验收标准

- 返回含 `total`/`hasMore`，翻页稳定。
- 默认 pageSize=20，最大 100。
- 大数据量下不再先拉全量候选再 Java Stream limit（主路径）。
- Tool 名与技能包一致；若提供别名则共用 Handler。

## 非目标

- 完整自动化边界用例见 task008。
- Registry 全面重构见 task007。
