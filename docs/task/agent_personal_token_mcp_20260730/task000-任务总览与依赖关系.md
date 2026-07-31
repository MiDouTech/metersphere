# task000 - Agent 集成个人 Token 与多 Agent MCP 接入任务总览

> 来源方案：`docs/summary/MeterSphere-Agent集成-个人Token与多Agent-MCP接入改造方案-2026-07-30.md`
> 任务目录：`docs/task/agent_personal_token_mcp_20260730`
> 状态：待排期 / 可按 task 独立流转

## 总体目标

将 Agent 集成从管理员分配 Token、本地 MCP 包模式，升级为用户自助创建个人 Token、远程 Streamable HTTP MCP、多 Agent 客户端接入和不含密钥的 AI 技能包模式。

## 任务清单

| 任务 | 名称 | 优先级 | 依赖 |
|---|---|---|---|
| task001 | 个人 Token 自助管理与 UI 改造 | P0 | 无 |
| task002 | Token v2 安全模型、迁移与审计 | P0 | task001 |
| task003 | 原生远程 MCP 服务与 Tool Registry | P0 | task002 |
| task004 | 多 Agent 接入向导与 AI 技能包 | P0 | task003 可并行部分前端 |
| task005 | 管理治理、限流、幂等与安全验收 | P0 | task001-task004 |

## 总体验收

- 普通用户可以创建、查看、禁用、启用、轮换、删除自己的 Token。
- Token 创建和管理不依赖 `SYSTEM_USER:*` 权限。
- 用户不能创建、查看、修改、删除其他用户的 Token。
- 完整 Token 只在创建或轮换成功时显示一次。
- MCP 服务使用远程 Streamable HTTP，不再要求用户运行本地 Node.js stdio 适配器。
- Codex、ChatGPT、Cursor、WorkBuddy 至少完成连接和只读 Tool 验收。
- AI 技能包不包含真实 Token、真实用户 ID、真实项目 ID。
- 测试环境地址 `https://msp.ebcone.net` 和正式环境地址 `https://msp.ebcone.cn` 在技能包说明与模板中明确提供。

## 风险控制

- 个人 Token 只能代表创建者本人，不能提升权限。
- MCP 写操作必须同时经过 RBAC、项目范围、Token scope 和 Tool 安全策略校验。
- 日志、审计、技能包、页面列表不得泄露完整 Token。
- Flyway 版本号必须与当前分支迁移文件无冲突。
