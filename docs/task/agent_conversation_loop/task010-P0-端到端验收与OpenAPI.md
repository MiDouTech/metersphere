# task010 - P0 端到端验收与 OpenAPI

> **阶段**：P0（里程碑收口）  
> **前置依赖**：task001–009  
> **关联方案**：§12、§14  
> **执行日期**：2026-07-23

---

## 1. 任务目标

按可判定标准完成一期 6 项场景验收，并确认 OpenAPI Agent 分组。

---

## 2. 已完成（本轮）

- [x] 静态核对 task001–009 代码与 Scope/审计/MCP 对齐  
- [x] 单测：`AgentScopeAssertTests`（含 CASE_WRITE 隔离）+ 既有 Token/Controller 测试 **14 passed**  
- [x] MCP `npm run build` 通过  
- [x] OpenAPI：`AgentOpenApiConfig` pathsToMatch `/api/agent/v1/**`（含写 Path）  
- [x] 联调脚本：`scripts/verify-agent-conversation-loop.ps1`  
- [x] Fixture：`msat_demo_agent_all_token_01` / scopes=`AGENT_ALL`  

## 3. 阻塞（运行时）

| 项 | 状态 |
|----|------|
| 本地 `http://127.0.0.1:8081` | **未启动**（连接失败） |
| `https://msp.ebcone.cn` health | 超时/不可用 |
| 6 项 E2E 场景 | **未跑通** |

### 解锁步骤

1. 启动 MeterSphere 后端（并确认 Flyway / agent 表）  
2. 导入 fixture（含 AGENT_ALL Token）或 UI 发放 `AGENT_ALL` Token  
3. 执行：

```powershell
$env:MS_BASE_URL = "http://127.0.0.1:8081"
$env:MS_AGENT_TOKEN = "msat_demo_agent_all_token_01"
.\scripts\verify-agent-conversation-loop.ps1 -ProjectId <项目ID> -SkipProjectCreate
# 或新建项目：
# .\scripts\verify-agent-conversation-loop.ps1 -OrganizationId <组织ID> -CreateProject
```

### 端到端勾选（待跑）

| # | 场景 | 结果 |
|---|------|------|
| 1 | 创建项目并加成员 | [ ] |
| 2 | ≥5 条用例导入 | [ ] |
| 3 | 计划关联 + testPlanCaseId | [ ] |
| 4 | 评审关联 | [ ] |
| 5 | 回写 + 截图 | [ ]（脚本未覆盖 upload/submit，可手工或沿用 verify-agent-api） |
| 6 | 失败提缺陷 | [ ] |

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **进行中**：静态/单测/脚本已就绪；**等待后端环境**完成运行时勾选 |
| 优先级 | 一期最高 |
