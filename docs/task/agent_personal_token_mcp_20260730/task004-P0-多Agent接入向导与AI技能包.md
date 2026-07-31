# task004 - P0 多 Agent 接入向导与 AI 技能包

## 目标

重构 Agent 接入向导和 AI 技能包，让用户可以把技能包交给 AI，由 AI 根据内置平台地址、用户提供的 Token 和目标客户端生成远程 MCP 配置。

## 范围

- Agent 集成页面接入向导
- 技能包 manifest
- `SKILL.md`
- 多客户端配置模板
- 连接验证说明

## 平台地址

技能包和页面说明必须内置：

| 环境 | 地址 |
|---|---|
| 测试环境 | `https://msp.ebcone.net` |
| 正式环境 | `https://msp.ebcone.cn` |

## 实现要点

1. 技能包不得包含真实 Token、真实用户 ID、真实项目 ID。
2. 技能包提供占位变量：
   - `${MS_BASE_URL}`
   - `${MS_AGENT_TOKEN}`
   - `${MS_PROJECT_ID}`
3. 提供多客户端模板：
   - Codex TOML
   - ChatGPT 远程 MCP JSON/说明
   - Cursor `mcp.json`
   - WorkBuddy/通用 MCP JSON
4. 技能包明确指导 AI：
   - 先确认环境地址
   - 再向用户索取 Token
   - 先调用项目查询类只读 Tool
   - 多项目命中时必须让用户确认
   - 删除、覆盖、批量写入前必须二次确认
5. 页面提供“下载技能包”和“创建 Token”入口。
6. 删除本地 Node.js stdio MCP 包引导。

## 验收标准

- 下载 zip 中包含完整技能说明和多客户端模板。
- zip 中不存在真实 Token、真实用户 ID、真实项目 ID。
- 测试/正式环境地址在技能包内可直接看到。
- 用户创建 Token 后可复制对应客户端远程 MCP 配置。
- 连接验证只执行只读 Tool，不产生业务写入。
