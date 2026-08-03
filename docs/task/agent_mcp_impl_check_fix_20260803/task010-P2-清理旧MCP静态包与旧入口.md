# task010 - P2 清理旧 MCP 静态包与旧入口

> 问题：MCP-012  
> 依赖：可与 task007 并行  
> 状态：待开始

## 目标

避免运维与后续开发误用旧静态 MCP 包或旧命名入口；新用户只引导个人 AI 技能包下载。

## 范围

- 检索并确认 `metersphere-mcp-*.zip` 等静态资源引用
- 历史下载 API（如 `/api/agent/mcp/download`）
- 前端文案、技能包文件名、API 说明统一为「AI 技能包」
- 新入口：`/api/personal/agent-package/skill/download`（及现有 personal 路径）

## 改造要点

1. 无运行时引用则删除旧静态 zip。
2. 旧 API：标注 deprecated 并转发到个人技能包服务，或文档明确废弃后下线。
3. 页面与技能包不再出现「本地 MCP 集成程序包」新用户引导。

## 验收标准

- 仓库无误导性旧包交付路径（或仅保留明确 deprecated 转发）。
- 新用户入口只暴露个人技能包下载。

## 非目标

- 不改变 Token 创建与 MCP 配置复制主流程。
