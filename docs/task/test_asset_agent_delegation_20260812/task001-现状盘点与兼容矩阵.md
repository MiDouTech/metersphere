# task001 现状盘点与兼容矩阵

> 盘点日期：2026-08-12  
> 盘点基线：`c4ce33307d`，同时保留当前工作区内其他任务的未提交修改  
> 结论：不得按全新系统重建，应在现有 AI 用例生成和 AI 执行链路上演进

## 1. 总体判断

方案提出的部分 P0 能力已在现有代码中存在，包括 AI 用例来源文档、结构化草稿、Schema 校验、正式用例保存、AI 执行任务、Runner 租约、心跳、事件、步骤快照、证据、结果回写和执行页面。

现有实现仍缺少统一的任务业务结论、任务上下文包、测试资产版本/关系、通用 Agent 实例授权、任务列表、人工复核中心、触发器、Agent 评价，以及 AI 用例“评审—差异—版本发布”闭环。

因此采用：

- 演进 `ai_execution_*`，不创建第二套 `test_task` 表。
- 演进 `functional_case_ai_generation` 和 `functional_case_ai_draft`，不创建平行的 `ai_case_generation_*` 表。
- 复用 `ai_source_document`，补充版本/关系和上下文引用。
- 复用 `ai_runner`、`ai_runner_lease` 和 `ai_execution_artifact`。
- 统一新增能力通过现有领域服务写入，不直接跨模块写正式用例或计划结果。

## 2. 数据模型兼容矩阵

| 目标能力 | 现有结构 | 结论 | 主要缺口 |
|---|---|---|---|
| AI 用例生成批次 | `functional_case_ai_generation` | 演进 | 缺生成模式、模板版本、来源版本集合、评审/发布统计、成本细分 |
| AI 用例生成项 | `functional_case_ai_draft` | 演进 | 缺建议动作、目标用例版本、评审状态/意见/评审人、差异与质量问题 |
| 业务来源文档 | `ai_source_document` | 演进 | 缺稳定资产身份、业务版本、发布状态和外部来源同步元数据 |
| 测试资产版本 | `functional_case.version_id/latest/ref_id` | 兼容并补强 | 现有版本表达项目版本，不足以表达不可变内容版本与发布状态 |
| 资产关系 | `functional_case_relationship_edge`、需求/缺陷关联 | 适配并统一查询 | 缺统一关系类型和文档章节到用例/任务的关系 |
| 统一任务 | `ai_execution_task` | 原地演进 | 缺任务标题/目标、业务结论、能力要求、审批/超时/重试、上下文引用 |
| 任务用例快照 | `ai_execution_case.case_version/case_snapshot` | 保留 | 需补内容摘要与来源资产版本引用 |
| 步骤结果 | `ai_execution_step` | 保留 | 需规范失败分类与确定性结论映射 |
| 任务事件 | `ai_execution_event` | 保留 | 已有序列和 Runner 幂等；需统一公共事件信封 |
| 执行证据 | `ai_execution_artifact` | 保留 | 需补人工复核状态、访问审计和多证据类型策略 |
| Runner | `ai_runner` | 保留 | 当前偏浏览器能力，需与通用 Agent 能力模型建立映射 |
| 租约 | `ai_runner_lease` | 保留并修正 | 当前过期直接 `EXPIRED`，需按尝试次数重排队或终止 |
| 用户 Agent | `ai_user_agent_connection`、`ai_agent_device` | 保留 | 面向用户连接，缺统一项目授权和调度实例视图 |
| 企业 Agent | `ai_agent_gateway` | 保留 | 与 Runner、用户 Agent 的能力声明未统一 |
| 触发器 | 无统一模型 | 新增 | Cron、Webhook、CI、资产变更、幂等与历史 |
| Agent 评价 | `ai_agent_usage`、治理统计 | 演进 | 缺任务结果、人工驳回、证据完整性和失败归因 |

## 3. API 与组件兼容矩阵

| 能力 | 现有入口 | 结论 |
|---|---|---|
| AI 用例生成 | `/functional/case/ai/draft/*` | 保留路径，新增评审/差异/发布接口 |
| AI 用例对话 | `/ai/case/agent/*` | 保留为交互渠道，正式资产仍经草稿领域服务 |
| AI 执行任务 | `/ai/execution/*` | 作为平台 UI 入口继续演进 |
| Agent 外部 API | `/api/agent/v1/*` | 新增任务领取/上下文/事件/结果接口，保持 Token Scope |
| Runner 内部 API | `/internal/ai-runner/v1/*` | 保留；不向普通 Agent 暴露 |
| MCP | `metersphere.execution.*` 等 | 保持薄封装并增加统一任务工具 |
| Agent Bridge | 出站 WSS、设备配对、Provider 调用 | 增加能力注册、任务控制和隔离，不上传凭据 |
| Browser Runner | 冻结动作与确定性断言 | 继续作为首个统一执行器 |

## 4. 状态语义差异

### 4.1 现有任务状态

`CREATED/RESOLVING_SCOPE/WAITING_CONFIRMATION/QUEUED/PREPARING_BROWSER/WAITING_LOGIN/RUNNING/PAUSED/WRITING_BACK/SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELED/EXPIRED`

问题：

- `SUCCESS/FAILED` 同时承担运行结束和业务结论，无法区分产品失败、Agent 失败与环境失败。
- 缺少明确的 `CLAIMED/PREPARING_CONTEXT/WAITING_REVIEW/SUBMITTING` 语义。
- 租约过期当前把任务直接置为 `EXPIRED`，没有按策略自动回收重排队。

### 4.2 演进决策

- 保留现有状态值以兼容前端和 MCP，新增 `verdict` 独立保存业务结论。
- 新增状态只在确有独立用户行为和并发语义时使用，避免机械改名。
- `SUCCESS/PARTIAL_SUCCESS/FAILED` 在兼容期视为运行终态；业务事实以 `verdict` 为准。
- 租约过期由调度策略决定 `QUEUED` 重试或 `FAILED`，并记录 `AGENT_FAILED`/基础设施原因。

## 5. 页面盘点

| 页面 | 现状 | 改造方向 |
|---|---|---|
| AI 生成用例 | 已有三栏工作台、来源文档、草稿编辑与批量保存 | 增加生成批次、质量问题、评审、差异和发布确认 |
| 自动化执行 | 已有单任务详情、事件、证据、操作 | 增加任务列表和运行状态/业务结论分列 |
| 用例列表 | 已有 AI 执行入口 | 改为统一“创建测试任务”并兼容旧入口 |
| Agent 个人中心 | 已有设备/连接能力 | 增加项目授权、能力与任务状态；平台管理视图单独建设 |
| 触发器 | 无统一页面 | 新增调度与触发器页面 |
| 人工复核 | 分散在登录恢复和任务操作 | 新增统一待办与复核页 |

## 6. 已发现风险

1. 当前工作区存在其他功能的大量未提交修改，实施中必须限制改动范围并逐文件核对。
2. 3.7.2 迁移编号已使用到 43，新增迁移从 44 开始，避免版本冲突。
3. `functional_case.version_id` 是项目版本维度，不能直接宣称已实现不可变内容版本。
4. AI 草稿当前以创建人过滤，团队评审需要新的项目级可见性和评审授权，不能简单取消过滤。
5. Runner 和用户 Agent 是两类执行通道，认证和租约不可强行共用，但结果与任务语义必须统一。
6. 现有 `ai_execution_task` 被治理统计、MCP、前端和 Runner 多方读取，字段演进必须兼容。

## 7. 后续实施顺序

1. 统一枚举、业务结论和错误语义。
2. 原地演进任务模型，补上下文、能力、策略和租约尝试。
3. 补资产版本/关系的最小模型并连接 AI 文档、用例和任务快照。
4. 补平台/Agent API 和 MCP。
5. 建设任务列表、人工复核、触发器和 Agent 管理页面。
6. 演进 AI 用例草稿为评审与差异发布闭环。
7. 完成迁移、兼容、故障和安全验收。

## 8. task001 验收结论

- 已覆盖现有 AI 执行、AI 用例生成、MCP、Bridge、Runner、权限、迁移和页面入口。
- 已明确关键表的保留、演进与新增决策。
- 已明确主要状态差异和迁移风险。
- 未进行代码功能实现；task001 仅代表盘点完成，不代表后续改造完成。

