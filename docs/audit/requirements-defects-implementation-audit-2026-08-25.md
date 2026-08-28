# MeterSphere 需求与缺陷实现巡检（2026-08-25）

## 状态

**部分完成。** 仓库中存在已明确未实现、只完成代码但未验收、生产空实现、测试失败和文档状态冲突；不满足项目 `AGENTS.md` 的完成定义。

本次只做审计，不修改业务代码。工作区在巡检前已有大量未提交的 Agent 双通道代码与文档改动；这些改动纳入静态检查和编译，但不视为已交付证据。

## 范围与方法

- 文档范围：`docs/task` 377 份、`docs/summary` 43 份、两处 buglist 16 份，以及相关开发摘要、方案和执行结果文档。
- 代码范围：`frontend`、`backend`、`agent-bridge`、`ai-browser-runner`、`metersphere-mcp`、`wecom-bot-bridge`、迁移与部署文件。
- 判定链路：入口/路由/权限 → 前端请求 → 后端路由/校验/业务 → 数据/迁移 → 错误处理 → 测试/构建/容器/E2E。
- 文档中的“计划”“代码完成”“已修复”不直接等于 verified；缺真实运行证据时最多为 implemented-unverified。

## 确定未真正完成或修复的项目

| 编号 | 需求/缺陷域 | 前端入口 | 后端/数据 | 当前证据 | 判定 |
| --- | --- | --- | --- | --- | --- |
| AUD-001 | 2026-08-24 Agent 双通道统一执行 | 用例、计划、队列/工作台入口已有在改代码 | 新迁移、统一执行服务、MCP/Runner 接口处于未提交开发态 | 最新需求文档 IMP-01～IMP-10 全部仍为“待实现”；无两条真实 E2E | **部分完成** |
| AUD-002 | 旧 Bridge/USER_AGENT/远程 CLI 冗余下线 | 旧入口仍需保留 | Bridge、WSS、Redis 路由、旧字段/Node MCP 尚未满足观测期下线门槛 | 下线文档 DEL-02～DEL-05 明确阻塞 | **阻塞** |
| AUD-003 | Agent 自动化执行 | 页面、批量入口、确认和工作台有主体 | 状态机/回写有主体；真实 Runner、凭据、白名单、SSE、HAR/视频缺失 | task010、task012 未开始；task013 无真实验收；其余多为部分完成 | **部分完成** |
| AUD-004 | AI 用例生成 | 生成页、模型/资源选择、草稿操作有主体 | Schema、解析、保存链路有主体 | 真实 DB/AI/权限/页面 E2E、OCR 样例、外部 Provider 联调缺失 | **部分完成** |
| AUD-005 | AI 自有 Agent 双通道旧方案 | 个人 Agent、生成用例资源选择有骨架 | WSS/Connector 有主体 | WorkBuddy 正式 SDK、可靠断线恢复、真实账号、安全矩阵、浏览器 E2E 未完成 | **部分完成** |
| AUD-006 | AI WebUI 自动化执行 | 第一阶段页面主体存在 | 状态机/Runner 模型主体存在 | 文档明确真实数据库、平台、MinIO 和灰度验收未完成 | **部分完成** |
| AUD-007 | 测试资产与 Agent 委派 | 任务中心/详情存在主体 | 资产版本、租约、证据主干存在 | task002～task019 均由实施记录标记部分完成；独立证据/复核中心、触发器、迁移和安全/性能/E2E 未闭环 | **部分完成** |
| AUD-008 | MCP 检查整改与治理 | Token/审计切片存在 | Scope、Registry、检索主体存在 | DB 集成测试、真实客户端矩阵、治理中心（启停/告警/限流/留存）和全链路验收未完成 | **部分完成** |
| AUD-009 | Agent 对话写闭环 | 无独立 UI 的 API/MCP 消费链 | 创建项目、导入、计划/评审/缺陷写接口存在 | task011～014 未开始；Agent health 不可达，六项 E2E 未勾选 | **阻塞** |
| AUD-010 | 测试计划缺陷资源能力 | 测试计划缺陷列表存在 | `TestPlanBugService` 多个抽象能力固定返回 `Map.of()`、`List.of()`、`0`，排序方法为空；测试集关联为空实现 | 生产代码直接确认 | **未实现** |
| AUD-011 | 自动保存与撤销 | CASE/BUG/PLAN_DOCUMENT 已接部分入口 | 锁/快照 API 有主体 | 53 个验收复选项仍未勾选；功能用例、缺陷、计划文档仅“代码完成待联调”；导入快照钩子未补 | **implemented-unverified / partial** |
| AUD-012 | 默认项目与跨项目导入 | 默认项目导入入口存在部分实现 | 同步引擎/API 有部分代码 | 文档仍有 100 个未勾选项；用例/计划导入及全链路验收受 task011 阻塞，重试/进度缺口存在 | **部分完成** |
| AUD-013 | 用例体验优化 | 方案覆盖列表、详情、导入、导航 | 多模块代码有历史改动 | 179 个验收项未勾选，未形成可追溯完成证据 | **部分完成** |
| AUD-014 | 通用前端工程优化 | 全站 | 主要为前端工程性任务 | task001～task010 总览均为未开始，64 个验收项未勾选 | **未实现** |
| AUD-015 | 权限控制三层模型旧方案 | 路由/按钮权限静态覆盖已通过 | 当前权限常量与资源覆盖存在 | 旧任务总览 task001～task012 全为未开始；后续方案有重叠实现，文档未做替代/废弃映射，无法证明三层配置模型整体交付 | **文档冲突 / 部分完成** |
| AUD-016 | 测试管理模块重构 | 目标为用例全局化、双 Tab、评审/报告迁移 | 目标含新模型和迁移 | 任务文档无实施状态、无执行结果、无验收证据 | **未实现或证据缺失** |
| AUD-017 | 缺陷流程与用例资产统一方案 | 角色页、流程、资产中心等多入口 | 流程版本、迁移、资产 CRUD/血缘等 | 总览仍为“已确认，可排期”；后续 fix 文档只覆盖部分缺陷，原 16 项需求未逐项回填 | **部分完成** |
| AUD-018 | 企微 Bot 通知 | 管理前端已形成闭环代码 | Bridge、Outbox、规则、定时器有代码 | 真实认证/个人消息/群发现/群发送、故障演练、灰度均未通过；里程碑仍大量未勾选 | **implemented-unverified** |
| AUD-019 | 米多 SSO | callback/status/登录桥存在 | SSO Client 和后端入口存在 | 后端测试实际失败：含 `#` 的回调 URL 被 URI builder 拒绝 | **未修复（回归）** |
| AUD-020 | 历史 bug 文档“已修复”声明 | 各历史页面/API | 对应代码多数可定位 | 多数文档只有人工“验证步骤”而无本次可复现实跑记录；PLAN-003 有修复声明但本次未跑真实 SQL/页面 | **implemented-unverified** |

## 需求域汇总

以下覆盖 `docs/task` 下全部有 Markdown 的任务目录；相近或前后继方案按同一交付域合并，避免把重复方案误算成多个独立完成项。

| 分类 | 任务目录 | 当前结论 |
| --- | --- | --- |
| 未实现/待排期 | `metersphere_optimize`、`permission_control_refactor_20260808`、`ai_case_agent_impl_20260806`、`test_management_restructure_20260812` | 未实现或缺任何完成证据 |
| 最新开发中/阻塞 | `agent_dual_channel_target_20260824`、`agent_automation_execution_20260805`、`ai_agent_dual_channel_impl_20260808`、`ai_webui_execution_20260806`、`test_asset_agent_delegation_20260812` | 部分完成；缺真实 E2E/部署证据 |
| MCP/Agent 部分闭环 | `metersphere_agent`、`agent_conversation_loop`、`agent_personal_token_mcp_20260730`、`agent_mcp_case_bug_extension_20260731`、`agent_mcp_impl_check_fix_20260803`、`agent_connection_redundancy_reduction_20260818` | 代码主体不等于整体完成；治理、客户端兼容和运行时验收未闭环 |
| AI 用例相关 | `ai_case_generation_20260805`、`ai_case_agent_20260806` | 部分完成；真实模型/DB/页面/异常 E2E 缺失 |
| 用例/体验/编辑 | `case_feature_optimize`、`experience_optimize`、`auto_save_undo`、`module_state_case_detail_aggregation_20260730`、`module_state_case_detail_followups` | 多为代码主体或待排期，缺完整验收 |
| 项目/组织/导入 | `community_rebuild`、`default_project_cross_import`、`wecom_sync_fields`、`destination` | 历史代码较完整，但仍有大量未勾选验收；不能整体 verified |
| 权限/资产/流程 | `current_gap_fix_20260814`、`deviation_gap_closure_20260813`、`deviation_gap_supplement_20260814`、`permission_flow_case_asset_unification_20260814`、`permission_flow_case_asset_fix_20260817` | 部分专项静态门禁通过；原始大方案未逐条闭环，部署复验仍缺 |
| 外部集成 | `miduo_sso`、`wecom_ai_bot_notification_20260814` | SSO 有实际测试失败；企微缺真实账号/群/灰度 |
| 其他 | `bugs`、`module_state_case_detail_aggregation_20260730` | 文档有问题或计划，但无足够当前运行证据 |

空目录 `fixtures`、`rollback` 不包含需求文档，不作为需求项。

## 缺陷文档复核

- 早期九份 BUG 文档多数提供修复代码说明和人工验证步骤，但缺本次环境的接口/页面复验，因此统一降级为 **implemented-unverified**。
- `BUG-PLAN-003` 文档标记已修复；静态上对应筛选代码存在，但未执行真实数据库查询和浏览器验证，不能判 verified。
- `BUG-AGENT-MCP-P0`、`BUG-AGENT-AUTOMATION`、`BUG-AI-CASE-GENERATION` 指出的核心缺口，在后续代码中有一部分已补，但真实 Runner、客户端/Token、治理、外部 AI、数据库与 E2E 证据仍未关闭。
- `permission-control-unfinished-buglist-20260812.md` 与后续权限/流程改造文档存在状态冲突：静态权限覆盖门禁已通过，但旧缺陷清单仍写“未修复”。必须建立逐 BUG 的替代提交和验收映射后才能关闭。
- 2026-08-17 缺陷流程/资产修复文档自述“代码级闭环，待部署验收”；本次没有数据库/容器环境，因此维持 implemented-unverified。

## 前后端对应关系摘要

| 用户能力 | 前端 | 后端 | 数据/外部依赖 | 结论 |
| --- | --- | --- | --- | --- |
| Agent 双通道执行 | 用例/计划/队列/自动化工作台 | AgentTask/Runner/MCP/统一执行服务 | V3.7.2_79（未提交）、租约/步骤/证据 | 开发中，未完成 |
| AI 用例生成 | `caseGenerate` 及草稿表单 | AI 草稿、解析、保存服务 | AI Provider、OCR、对象存储 | 部分完成 |
| 测试计划缺陷 | 测试计划缺陷列表 | `TestPlanBugService` | 缺陷关系表 | 后端存在空实现 |
| 权限与信息架构 | 路由、Tab、按钮 gate | permission resources/API | 权限初始化数据 | 静态覆盖通过，动态角色/E2E 未验收 |
| 企微通知 | Bot/群/规则/日志页 | Bridge、Outbox、规则/Timer | 真实企微账号和群 | 代码存在，外部闭环未验证 |
| 米多 SSO | callback/登录入口 | SSO client/status/callback | 真实 SSO 环境 | 测试回归失败 |

## 实际验证命令与结果

| 命令 | 结果 | 范围 |
| --- | --- | --- |
| `powershell -File scripts/check-flyway-versions.ps1` | 通过；扫描 107 个迁移，版本无重复 | 仅版本唯一性，不含真实迁移 |
| `pnpm run test:api-contracts` | 通过 | 前端声明的 API 契约静态检查 |
| `pnpm run test:route-tabs` | 通过 | 路由、入口、权限交集和按钮 gate 静态检查 |
| `pnpm run test:permission-resources` | 通过；83 个精确绑定、47 个兼容绑定、0 未绑定 | 权限资源静态覆盖 |
| `pnpm run type:check` | 通过 | 前端 TypeScript/Vue 类型 |
| `mvnw.cmd -f backend/pom.xml -pl services/agent-integration -am package -DskipTests -DskipAntRunForJenkins` | 通过；18 个 reactor 模块编译/打包成功 | 后端编译，明确跳过测试 |
| `mvnw.cmd -f backend/pom.xml -pl services/agent-integration -am test -DskipAntRunForJenkins` | **失败**；system-setting 阶段 504 tests / 374 errors；大部分因 Docker 不可用，另有 `MiduoSsoClientTest` URI 回归 | 后端真实测试，后续模块被跳过 |

## 未执行验证

- 未执行前端 lint：项目脚本带 `--fix`，会修改用户现有工作区，不适合作为只读巡检。
- 未执行前端生产 build：类型检查已跑，但完整 Vite 构建仍需单独执行 `cd frontend && pnpm run build`。
- 未执行空库/存量库 Flyway：当前没有隔离 MySQL；应在隔离环境运行项目 compose 后验证升级与重入。
- 未执行 Docker 镜像构建、容器启动、健康检查：本机 Docker daemon 不可用。
- 未执行真实浏览器 E2E：缺可用后端、数据库和测试账号。
- 未执行真实企微、米多 SSO、外部 AI Provider、真实 MCP 客户端兼容：缺测试租户/凭据/在线服务。

## 已知风险与后续优先级

1. P0：修复 `MiduoSsoClientTest` 暴露的 fragment 回调 URL 构造问题，并单独重跑该测试。
2. P0：完成最新双通道 IMP-01～IMP-10；在此之前禁止下线旧通道，也禁止把当前未提交代码视为已交付。
3. P0：清理 `TestPlanBugService` 固定空结果/空方法；若业务明确不支持，应拆除抽象调用入口并返回明确不支持错误，不能伪装成功空结果。
4. P0：恢复 Docker/Testcontainers 后跑后端全测试、迁移、容器健康和两条核心 E2E。
5. P1：为权限、历史 bug、资产流程建立“旧文档 → 后续替代任务 → 提交 → 自动化/环境验收”的关闭映射，消除“未修复”和“已完成”并存。
6. P1：企微、AI Provider、MCP 客户端、SSO 必须用真实集成环境验收；Mock/编译通过不能替代。

## 修改文件

- 新增本审计报告；未修改任何生产代码、测试或原有需求/缺陷文档。

## 功能状态

**部分完成。** 当前仓库可以通过部分静态门禁、前端类型检查和跳过测试的后端打包，但存在明确生产空实现、后端测试失败、多组未实现/部分实现需求，以及数据库、容器和真实 E2E 证据缺失。
