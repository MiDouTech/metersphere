# task009 - P2 四类真实客户端兼容矩阵

> 问题：MCP-011  
> 依赖：task003、task006；环境可用（优先 `msp.ebcone.net`）  
> 状态：待开始

## 目标

形成 Codex、ChatGPT、Cursor、WorkBuddy 的版本化兼容证据，而非仅 Controller 单测。

## 范围

- 文档产出：建议本目录或 `docs/summary` 下《MCP 客户端兼容矩阵》
- 联调配置：远程 Streamable HTTP + Bearer/`X-API-Key`
- 只读调用：`tools/list` + 至少一个读 Tool（如 project.search / functional.search）

## 矩阵字段（最低）

| 客户端 | 版本 | MCP URL | 配置格式 | 连接结果 | tools/list | 只读调用 | 401/403/429 | 备注 |
|---|---|---|---|---|---|---|---|---|
| Codex | | | | | | | | |
| ChatGPT | | | | | | | | |
| Cursor | | | | | | | | |
| WorkBuddy | | | | | | | | |

## 环境注意

- `msp.ebcone.cn` 若仍存在 Windows schannel TLS RST，WorkBuddy/Cursor 联调改用 `msp.ebcone.net`，并在矩阵备注标明。
- 不把 TLS 运维问题记为 MCP 应用缺陷。

## 验收标准

- 四类客户端至少各有一次真实连接与只读调用记录。
- 401、403、429、超时、写操作审批（若适用）有抽样说明。

## 非目标

- 不实现管理员告警中心（task011）。
