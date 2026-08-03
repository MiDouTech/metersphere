# task007 - P2 统一 McpToolRegistry

> 问题：MCP-009  
> 依赖：建议 task005/task006 完成正式 Tool 名冻结后进行  
> 状态：待开始

## 目标

消除内置 Tool `switch` 与扩展 `AgentMcpToolHandler` 双轨，统一到 Registry，保证 `tools/list`、技能包清单与执行逻辑同源。

## 范围

- `AgentMcpToolHandler` 契约
- 新增/完善 `McpToolRegistry`（或等价注册中心）
- `AgentMcpStreamableService`：移除内置 switch 主路径
- 技能包生成：从 Registry 导出 Tool 清单
- 写操作幂等白名单：从 Registry 元数据生成

## 改造要点

1. 每个 Tool：`name`、`description`、`inputSchema`、`requiredScope`、`execute`、可选 `idempotent`/`riskLevel`。
2. 内置 Tool 全部改为 Handler Bean 注册。
3. `tools/list` 只读 Registry。
4. 技能包 `SKILL.md` / Tool 文档由同一源生成或校验脚本对比防漂移。

## 验收标准

- 新增 Tool 只需新增 Handler，无需改 switch。
- 技能包 Tool 列表与 `tools/list` 一致（自动化比对或生成）。
- 旧 Tool 行为回归通过。

## 非目标

- 不在本任务扩展全部 P2 业务写能力。
- 不替代管理员 Tool 启停策略 UI（见 task011）。
