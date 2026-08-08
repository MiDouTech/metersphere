# task000 - AI 用户自有 Agent 双通道改造任务总览

## 状态

实现中，尚未达到本文“完成定义”和生产上线门槛；详见 `执行状态与偏差记录.md`。

## 需求基线

- `docs/task/ai_agent_dual_channel_20260808/AI用户自有Agent双通道改造方案.md`
- `docs/task/ai_case_agent_20260806/AI用例生成Agent改造方案.md`

## 目标

在不破坏现有模型 API 通道的前提下，增加用户自有 WorkBuddy、Codex、Cursor Agent 通道。用户在【生成用例】中统一选择模型或本人已授权 Agent，通过连续聊天生成、修改和校验草稿，最后仍由用户确认保存正式用例。

## 交付原则

- 原 `ai_model_source`、`/models`、Provider、Token 统计和模型回退链路必须向后兼容。
- 模型 API 与用户 Agent 使用独立适配接口，不把 CLI/SDK Agent 塞入 `AiProviderAdapter`。
- 个人会员凭据默认留在用户设备，通过本地 Agent Bridge 调用官方 SDK/CLI。
- 禁止抓取 Cookie、复制网页登录 Token、模拟第三方私有协议。
- 平台始终负责权限、文档范围、工具白名单、草稿校验和正式保存确认。
- 新通道必须由 Feature Flag 控制；关闭后系统行为与改造前一致。
- “代码存在”不等于“真实 Agent 已接入”，必须有真实账号端到端证据。

## 任务清单

| 任务 | 优先级 | 内容 | 依赖 |
| --- | --- | --- | --- |
| task001 | P0 | 双通道资源模型、路由与向后兼容 | 无 |
| task002 | P0 | 用户 Agent 连接、设备、会话绑定与权限数据模型 | task001 |
| task003 | P0 | Agent Bridge 配对、设备身份与凭据安全 | task002 |
| task004 | P0 | WSS 长连接协议、流式事件与多节点路由 | task002、task003 |
| task005 | P0 | UserAgentConnector、编排接入与工具安全 | task001、task002、task004 |
| task006 | P1 | WorkBuddy Agent SDK 正式适配 | task003–task005 |
| task007 | P1 | OpenAI Codex CLI 正式适配 | task003–task005 |
| task008 | P1 | Cursor Agent CLI 正式适配 | task003–task005 |
| task009 | P1 | 个人中心“我的 AI Agent”与 Bridge 引导 | task002、task003、task006–task008 |
| task010 | P1 | 生成用例统一 AI 资源选择与双通道交互 | task001、task005、task009 |
| task011 | P1 | 项目治理、配额、审计与可观测性 | task001–task010 |
| task012 | P0/P1 | 自动化、真实端到端验收、灰度与上线门槛 | task001–task011 |

## 关键路径

```text
task001 统一资源与路由
        ↓
task002 连接/设备/会话数据模型
        ↓
task003 配对与设备身份
        ↓
task004 WSS 长连接与事件协议
        ↓
task005 Connector 与用例 Agent 编排
   ┌────┼────────┐
task006 task007 task008
WorkBuddy Codex  Cursor
   └────┼────────┘
        ↓
task009 个人 Agent 管理
        ↓
task010 生成用例统一资源选择
        ↓
task011 治理审计
        ↓
task012 全链路验收
```

## 里程碑

### M1 - 双通道骨架可回归

完成 task001–task005，并使用 Mock Bridge 打通 Agent 流式事件和草稿工具。原模型 API 全量回归通过。

### M2 - WorkBuddy 灰度

完成 task006、task009、task010 的 WorkBuddy 范围，使用真实购买账号打通授权、对话、草稿、确认保存。

### M3 - Codex/Cursor 实验能力

完成 task007、task008，分别使用独立 Feature Flag，仅向指定用户开放。

### M4 - 生产开放

完成 task011、task012，满足安全、审计、真实 Provider、灰度和回滚门槛。

## 完成定义

必须同时满足：

1. Feature Flag 关闭时原模型 API 行为、接口和数据均不变化。
2. 用户只能连接和使用本人 Agent，不能访问其他用户设备、连接或会话。
3. Bridge 凭据、第三方登录 Token、Cookie 不上传平台或写入日志。
4. WorkBuddy 至少完成一次真实账号授权与完整用例生成闭环。
5. Codex/Cursor 未完成真实联调时保持关闭或明确实验标识。
6. Agent 输出经过与模型 API 相同的 Schema、草稿和正式保存确认链路。
7. WSS 断线、节点重启、Bridge 重启、授权过期和取消均有自动化验证。
8. 平台 API 成本与用户会员 Agent 用量分开统计。

## 明确不计为完成

- 仅在 Provider 名称集合中加入 `workbuddy/cursor/codex`。
- 仅保存一个远程 Gateway URL。
- 仅实现 OAuth 表和回调接口但未与真实供应商联调。
- 使用 Mock CLI 代替真实账号验收。
- 只验证编译或单元测试，没有浏览器端闭环。
