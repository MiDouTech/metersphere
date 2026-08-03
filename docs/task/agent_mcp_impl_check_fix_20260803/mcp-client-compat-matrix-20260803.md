# MCP 客户端兼容矩阵（task009）

> 日期：2026-08-03  
> 状态：**未完成** — 本环境无法代替四类真实 Agent 客户端完成完整联调

## 已做探测（非客户端验收）

| 目标 | 方法 | 结果 | 说明 |
|---|---|---|---|
| `https://msp.ebcone.net/api/mcp` GET | Windows `curl.exe` | HTTP **401** | 线上尚未体现本分支 GET 405 修复（需部署后复测） |
| `https://msp.ebcone.cn/api/mcp` GET | Windows `curl.exe` | **TLS RST / curl 35** | 与 WorkBuddy 结论一致：schannel 与 `.cn` vhost TLS 不兼容 |
| Codex / ChatGPT / Cursor / WorkBuddy | — | **未执行** | 本 Agent 会话无真实 GUI/账号配置与用户 Token |

## 矩阵（待人工补齐）

| 客户端 | 版本 | MCP URL | 配置格式 | 连接结果 | tools/list | 只读调用 | 401/403/429 | 备注 |
|---|---|---|---|---|---|---|---|---|
| Codex | 待填 | 建议 `.net` | 待填 | 未测 | 未测 | 未测 | 未测 | 需用户 Token |
| ChatGPT | 待填 | 建议 `.net` | 待填 | 未测 | 未测 | 未测 | 未测 | 需用户 Token |
| Cursor | 待填 | 建议 `.net` | 待填 | 未测 | 未测 | 未测 | 未测 | 部署后验 GET 405 |
| WorkBuddy | 待填 | **仅 `.net`** | 待填 | 未测 | 未测 | 未测 | 未测 | `.cn` TLS 未修前不可用 |

## 阻塞项

1. 缺少可写入客户端的有效 Agent Token（技能包故意不含密钥）。
2. 代码改动未部署到 `msp.ebcone.net/cn`，线上行为不能代表本分支。
3. `msp.ebcone.cn` TLS/schannel 需运维修复，与应用代码正交。

**结论：task009 不能标记为完成。**
