# task010 - P0 端到端验收与 OpenAPI

> **阶段**：P0（里程碑收口）  
> **前置依赖**：task001–009  
> **关联方案**：§12、§14  
> **执行日期**：2026-07-23 / 重试 2026-07-23 晚

---

## 1. 任务目标

按可判定标准完成一期 6 项场景验收，并确认 OpenAPI Agent 分组。

---

## 2. 测试环境

| 项 | 值 |
|----|-----|
| Base URL | `https://msp.ebcone.net` |
| OrganizationId | `100001`（探测确认可创建项目） |
| Token | 会话内 env `MS_AGENT_TOKEN`（**勿提交 Git**） |
| 联调脚本 | `scripts/verify-agent-conversation-loop.ps1`（已修 Result.data 解包） |

```powershell
$env:MS_BASE_URL = "https://msp.ebcone.net"
$env:MS_AGENT_TOKEN = "<msat_...>"
.\scripts\verify-agent-conversation-loop.ps1 -OrganizationId 100001 -CreateProject -AdminUserId admin
```

---

## 3. 本轮 E2E 结果（部署修复前）

| # | 场景 | 结果 | 说明 |
|---|------|------|------|
| 1 | 创建项目并加成员 | 部分通过 | `project/create` OK；`members/add` 未传 `userRoleIds` 时 NPE（见缺陷 A） |
| 2 | ≥5 条用例导入 | **失败** | `getDefaultTemplateId` 为空即抛错，未回落内置模板（见缺陷 B） |
| 3 | 计划关联 + testPlanCaseId | 阻塞 | 计划/评审可创建；无用例则 search 无 `testPlanCaseId` |
| 4 | 评审关联 | 部分 | `case-review/create` 业务成功（需解包 `data.id`） |
| 5 | 回写 + 截图 | 未跑通 | 依赖 2/3；脚本已补 submit |
| 6 | 失败提缺陷 | 未跑通 | 同模板回落问题（缺陷 B 同步修 BUG） |

OpenAPI：`/v3/api-docs/agent` HTTP 200 但体积极小（~936B），路径字符串未命中 → 记 WARN/SKIP。

说明：文档示例项目 `100001100001` **在本环境不存在**（GET 报「项目不存在」）；勿再用作默认 ProjectId。

---

## 4. 已发现并本地修复的缺陷（待发布到 msp.ebcone.net）

| ID | 问题 | 修复 |
|----|------|------|
| A | `members/add` 未传 `userRoleIds` → NPE | `AgentProjectService` 默认 `project_member` |
| B | 批量建用例/缺陷：仅认 `getDefaultTemplateId`，新建项目常为空 | 回落 `getDefaultTemplateDTO`（内置模板） |
| C | 联调脚本未解包 `{code,data}` | `Get-MsData`；members 可显式传 `userRoleIds` |

涉及文件：

- `AgentProjectService.java`
- `AgentCaseWriteService.java`
- `AgentBugWriteService.java`
- `scripts/verify-agent-conversation-loop.ps1`

**阻塞**：修复未部署到 `msp.ebcone.net` 前，场景 2–6 无法在测试环境闭环验收。

---

## 5. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **进行中**：联调环境已通；发现并修复 A/B；**等待测试环境发版后重跑脚本勾选 §12** |
| 优先级 | 一期最高 |
