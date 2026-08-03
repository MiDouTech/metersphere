# task000 - MCP 接口实现检查问题整改任务总览

> 来源文档：`docs/summary/MeterSphere-MCP接口实现检查-问题汇总-2026-08-03.md`
> 任务目录：`docs/task/agent_mcp_impl_check_fix_20260803`
> 状态：**执行中 / 部分完成**（详见 `execution-result-20260803.md`）
> 【AI生成】已对照代码核实问题点位；2026-08-03 已执行实现，未关闭项不得视为完成

## 总体目标

关闭 MCP 接口实现检查中的 P0 安全缺陷与关键 P1 契约差距，使远程 `/api/mcp` 在 Token scope、会话隔离、项目检索和协议行为上达到可生产验收的最小闭环；其余 P2 按工程治理节奏推进。

## 问题映射

| 问题编号 | 等级 | 对应任务 |
|---|---|---|
| MCP-001 空 scope 放行 | P0 | task001 |
| MCP-002 scope 字符串包含匹配 | P0 | task001 |
| MCP-003 Agent Filter 注销 Web 登录 | P0 | task002 |
| MCP-001/002/003 安全回归 | P0 | task003 |
| MCP-005 独立 PROJECT_READ | P1 | task004 |
| MCP-004/006/007 项目检索分页与 SQL | P1 | task005 |
| MCP-008 JSON-RPC/Streamable HTTP | P1 | task006 |
| MCP-009 Tool 注册双轨 | P2 | task007 |
| MCP-010 项目检索自动化测试 | P2 | task008 |
| MCP-011 真实客户端联调证据 | P2 | task009 |
| MCP-012 旧 MCP 包与旧入口 | P2 | task010 |
| MCP-013 管理员治理与审计 | P2 | task011 |
| 总体完成标准 | P0/P1 | task012 |

## 任务清单

| 任务 | 名称 | 优先级 | 依赖 |
|---|---|---|---|
| task001 | Scope 默认拒绝与集合精确匹配 | P0 | 无 |
| task002 | AgentTokenFilter 条件注销 | P0 | 无（可与 task001 并行） |
| task003 | 安全回归单测与契约测试 | P0 | task001、task002 |
| task004 | 独立 PROJECT_READ 与兼容迁移 | P1 | task001 |
| task005 | 项目检索分页、契约冻结与 SQL 下沉 | P1 | task004 |
| task006 | MCP Notification 与协议行为修正 | P1 | 可与 task004/005 并行 |
| task007 | 统一 McpToolRegistry | P2 | task006 建议先完成正式 Tool 名冻结 |
| task008 | 项目检索专用自动化测试 | P2 | task005 |
| task009 | 四类真实客户端兼容矩阵 | P2 | task003、task006；联调依赖环境 TLS |
| task010 | 清理旧 MCP 静态包与旧入口 | P2 | 可与 task007 并行 |
| task011 | 管理员治理策略与审计中心 | P2 | 不阻塞最小只读试用 |
| task012 | 全链路验收与完成标准闭环 | P0/P1 | task001-task006；证据依赖 task008-task010 |

## 批次建议

### 第一批：上线阻断（必须）

1. task001 Scope 安全
2. task002 Filter 注销
3. task003 安全回归
4. 部署后回归：空 scope / 子串误授权 / 混合登录会话

### 第二批：项目检索契约

1. task004 `PROJECT_READ`
2. task005 分页 + SQL + 冻结契约
3. task008 检索测试（可随后）

### 第三批：协议与工程治理

1. task006 协议行为
2. task007 Registry
3. task009 客户端矩阵
4. task010 旧资源清理
5. task011 治理能力
6. task012 总体验收关门

## P0 交付目标

- MCP Tool 调用必须存在有效 Token；scopes 为空默认拒绝。
- scope 解析为集合后精确匹配；`XAGENT_ALL`、`BUG_WRITE_EXT` 等不得误授权。
- Agent Filter 只清理由本次请求建立的临时登录，不注销既有 Web/JWT 身份。
- 对应单元测试与写 Tool 缺 scope 契约测试通过。

## P1 交付目标

- 项目检索/列表/详情使用独立 `PROJECT_READ`（迁移期可临时兼容 `FUNCTIONAL_READ`）。
- `metersphere.project.search` 支持 `page/pageSize/total/hasMore`，过滤排序分页下沉数据库。
- Notification（无 id）不返回带 result 的 JSON-RPC 响应体。
- Tool 名称冻结为 `metersphere.project.search`，技能包与 `tools/list` 一致。

## P2 交付目标

- 内置与扩展 Tool 统一 Registry。
- 项目检索边界与注入防护自动化覆盖。
- Codex / ChatGPT / Cursor / WorkBuddy 联调证据归档。
- 删除或废弃旧静态 MCP zip 与误导入口。
- 管理员治理与审计能力按企业验收补齐。

## 总体验收（摘自检查文档第八节）

1. P0 全部关闭并完成安全回归。
2. 项目模糊检索满足冻结后的 Tool 契约。
3. 用户 RBAC、Token scope、Token 项目范围、Tool 策略交集有测试覆盖。
4. `agent-integration` 模块编译与相关测试通过。
5. 四类客户端至少各完成一次真实连接和只读调用（联调优先 `msp.ebcone.net`，若 `.cn` TLS/schannel 未修）。
6. 任一写 Tool 完成权限、审计、幂等与异常回滚验收。
7. 技能包 Tool 清单与服务端 `tools/list` 一致。
8. 不再向新用户交付或引导旧 MCP 集成程序包。

## 风险控制

- 不允许空 scope 或子串匹配绕过授权。
- 不允许 Agent Token 请求注销同会话 Web 登录。
- 不允许项目检索枚举无权限或白名单外项目。
- SQL 模糊匹配必须参数化并转义 `%` `_` `\`。
- 不在本任务内“修” `msp.ebcone.cn` Nginx TLS；客户端联调与运维 TLS 整改正交。
- 未经用户明确要求，不自动 git commit / push。

## 关键代码落点（基线）

| 模块 | 路径 |
|---|---|
| Scope 断言 | `backend/services/agent-integration/.../security/AgentScopeAssert.java` |
| Scope 常量 | `.../constants/AgentTokenScope.java` |
| Agent Filter | `.../security/AgentTokenFilter.java` |
| MCP 服务 | `.../service/AgentMcpStreamableService.java` |
| 项目检索 | `.../service/AgentProjectService.java` |
| 检索请求 DTO | `.../dto/AgentProjectSearchRequest.java` |
| 现有单测 | `.../test/.../AgentScopeAssertTests.java`、`AgentTokenFilterTests.java` |
