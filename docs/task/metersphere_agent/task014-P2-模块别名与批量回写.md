# task014 - P2 模块别名与批量回写

> **阶段**：P2  
> **预估工期**：1.5 天  
> **前置依赖**：[task005](task005-P0-检索条件解析器.md)、[task007](task007-P0-计划内结果回写服务.md)  
> **阻塞任务**：无  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §4.3.4、§11 阶段 3

---

## 1. 任务目标

1. 支持模块别名配置（如「CW」→「财务」），提升 NL 检索命中率  
2. 支持批量 submit，减少 Agent 多次 HTTP 往返  

---

## 2. 模块别名

### 2.1 数据表（可选方案）

```sql
CREATE TABLE agent_module_alias (
    id           VARCHAR(50) PRIMARY KEY,
    project_id   VARCHAR(50) NOT NULL,
    alias        VARCHAR(50) NOT NULL COMMENT '别名，如 CW',
    module_id    VARCHAR(50) NOT NULL COMMENT '目标模块 ID',
    create_time  BIGINT,
    UNIQUE KEY uk_project_alias (project_id, alias)
);
```

### 2.2 解析器集成

在 `AgentQueryResolver` 步骤 2 之前：

```
query → 查 agent_module_alias → 命中则直接 moduleIds
```

### 2.3 管理 API（P2 可先 SQL 配置）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agent/v1/config/module-alias` | 列表 |
| POST | `/api/agent/v1/config/module-alias` | 新增 |

---

## 3. 批量回写

### 3.1 新增 API

```
POST /api/agent/v1/functional/submit/batch
```

**请求体**：

```json
{
  "projectId": "proj-001",
  "testPlanId": "plan-001",
  "executedBy": "cursor-agent",
  "results": [
    {
      "caseId": "fc-001",
      "testPlanCaseId": "relate-001",
      "lastExecResult": "SUCCESS",
      "steps": [...],
      "content": "..."
    }
  ]
}
```

**响应**：

```json
{
  "total": 5,
  "success": 4,
  "failed": 1,
  "errors": [
    { "caseId": "fc-003", "message": "testPlanCaseId not found" }
  ]
}
```

### 3.2 实现要点

- 逐条调用 `AgentFunctionalCaseSubmitService.submit()`  
- 单条失败不中断整批（可配置 `failFast`）  
- 事务：每条独立事务，避免一条失败回滚全部  

### 3.3 MCP 扩展

`submit_functional_results_batch` Tool 对应批量 API。

---

## 4. 验收标准

- [x] 别名「CW」可匹配目标模块（`agent_module_alias` + `AgentQueryResolver`）  
- [x] 批量 submit 接口返回 total/success/failed/errors（`POST /api/agent/v1/functional/submit/batch`）  
- [x] 批量接口 Scope 校验 FUNCTIONAL_SUBMIT  
- [x] 单条 submit 接口行为不变  
- [x] MCP `submit_functional_results_batch` Tool 已实现  

---

## 5. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-08 |
| 完成日期 | 2026-07-08 |
