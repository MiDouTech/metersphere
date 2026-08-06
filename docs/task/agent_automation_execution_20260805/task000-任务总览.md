# task000 - Agent 自动化执行改造任务总览

> 来源方案：`docs/summary/MeterSphere_Agent自动化执行改造方案.md`  
> 任务目录：`docs/task/agent_automation_execution_20260805`  
> 当前状态：P0 主体已落地；2026-08-06 补齐权限树/确认弹窗/状态机 WAITING_LOGIN·WRITING_BACK·SUCCESS 对账/工作台 resolve·pause；task010–012 未实现；task013 未完成真实验收  
> 拆分日期：2026-08-05  
> 状态更新日期：2026-08-06

## 总体目标

在 MeterSphere 中建设 Agent 自动化执行能力，形成“Agent 解析项目/计划/用例范围 → 后端确定性确认 → 创建执行任务 → 调度受控 Runner 或原生执行引擎 → 采集日志证据 → 按计划内/计划外链路回写结果”的闭环。

本次任务拆分不表示功能已实现。所有任务默认状态为“未开始”，只有完成代码实现、接口联调、页面验证和验收项验证后，才允许更新为“已完成”。部分完成必须明确记录剩余事项。

## 任务清单与真实状态

| 任务 | 名称 | 优先级 | 当前状态 | 主要缺口 |
| --- | --- | --- | --- | --- |
| task001 | AI 自动化执行权限、菜单与路由入口 | P0 | 部分完成 | permission.json + i18n 已补；缺真实环境无权限账号验证 |
| task002 | AI 执行任务数据模型、迁移与状态机 | P0 | 部分完成 | 已补 WAITING_LOGIN/WRITING_BACK/PAUSED/SUCCESS 证据对账；缺迁移实跑与单测 |
| task003 | 项目、测试计划、功能用例范围解析服务 | P0 | 部分完成 | 已补计划状态白名单与高风险确认；计划执行权限过滤仍弱 |
| task004 | 执行任务创建、确认、取消、恢复与重试接口 | P0 | 部分完成 | 已补 pause、确认门槛、无会话进 WAITING_LOGIN；Runner 未接 |
| task005 | Agent MCP Tools 自动化执行扩展 | P0 | 部分完成 | 缺真实 Token 端到端调用 |
| task006 | 执行结果回写、幂等与 PARTIAL_SUCCESS | P0 | 部分完成 | SUCCESS 需回写+证据对账，否则 PARTIAL_SUCCESS；缺集成测试 |
| task007 | 测试用例列表【AI执行】批量入口 | P0 | 部分完成 | 确认弹窗已采集环境/地址/浏览器/登录；超阈值需勾选确认 |
| task008 | 【自动化执行】工作台页面与 AI 连接复用 | P1 | 部分完成 | 对话驱动 resolve/create、范围预览、暂停已接；OAuth/Runner 画面未接 |
| task009 | 结构化事件日志、实时订阅与审计 | P1 | 部分完成 | 关键动作写 agent_exec_log 审计；SSE/WebSocket 未实现 |
| task010 | Browser/Desktop Runner 协议 | P1 | 未开始 | 仅有会话表占位 |
| task011 | 凭据引用、域名白名单、高风险动作 | P2 | 部分完成 | 用例名高风险关键词确认已接；凭据注入/白名单未实现 |
| task012 | 证据附件、截图视频 HAR | P2 | 未开始 | SUCCESS 对账已强制证据事件，但采集链路未实现 |
| task013 | 全链路测试验收与交付自检 | P0/P1/P2 | 未完成 | 缺真实环境与自动化验收证据 |

## 本轮已落地（2026-08-06）

- `bug-management/permission.json` 增加 AI_EXECUTION 权限树与 i18n。
- 用例列表【AI执行】完整确认弹窗；超阈值勿默认 `confirmed=true`。
- 状态机：创建/确认后无 Runner 会话进入 `WAITING_LOGIN`；回写进入 `WRITING_BACK`；无证据不得 `SUCCESS`。
- 工作台：对话调用 `resolve`、范围预览、创建任务、暂停。
- 高风险关键词与计划可执行状态过滤加固。

## 本轮已落地（2026-08-05）

- `ai_execution_writeback_idempotency` 迁移与回写去重（task006）。
- 【自动化执行】工作台：左对话/模型选择，右任务信息、用例进度、事件日志、确认/停止/重试/登录恢复、日志下载（task008）。
- 事件游标轮询刷新（task009 基础能力，非 SSE）。

## 不可宣称完成的内容

- 不能宣称 Browser/Desktop Runner、凭据治理、证据 HAR 已完成。
- 不能宣称已通过真实端到端验收。
- 不能宣称 SSE/WebSocket 实时订阅已完成。
