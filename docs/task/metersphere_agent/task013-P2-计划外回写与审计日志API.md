# task013 - P2 计划外回写与审计日志 API

> **阶段**：P2  
> **预估工期**：1.5 天  
> **前置依赖**：[task007](task007-P0-计划内结果回写服务.md)、[task002](task002-P0-数据模型与Flyway迁移.md)  
> **阻塞任务**：无  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §4.2.4 计划外回写

---

## 1. 任务目标

扩展 submit 支持计划外回写（无 testPlanCaseId），写入 `agent_exec_log` 审计；提供执行日志查询 API。

---

## 2. 计划外回写逻辑

```
无 testPlanCaseId 时：
  → 更新 functional_case.last_execute_result
  → 写入 agent_exec_log（steps_snapshot、executed_by、content）
  → 不写入 test_plan_case_execute_history
```

**限制说明**：计划外回写在平台测试计划报告中不可见，仅审计日志可查。

---

## 3. 任务清单

### 3.1 AgentExecLogService

**路径**：`.../service/AgentExecLogService.java`

| 方法 | 说明 |
|------|------|
| `log(AgentCaseSubmitRequest, stepsSnapshot)` | 写入 agent_exec_log |
| `page(caseId, current, pageSize)` | 分页查询 |

### 3.2 扩展 AgentFunctionalCaseSubmitService

```java
if (StringUtils.isNotBlank(submit.getTestPlanCaseId())) {
    submitInPlan(submit);  // 现有 task007 逻辑
} else {
    submitOutOfPlan(submit);  // 新增
}
```

**submitOutOfPlan**：

1. 更新 `functional_case.last_execute_result`（通过 Service，不直写 DB）  
2. 可选：更新 blob steps（若业务允许计划外写步骤）  
3. 调用 `AgentExecLogService.log()`  

### 3.3 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agent/v1/functional/exec-log/page` | 审计日志分页 |
| GET | `/api/agent/v1/functional/exec-log/{id}` | 单条日志详情 |

**参数**：caseId（可选）、executedBy（可选）、current、pageSize

---

## 4. 请求体调整

submit 校验变更：

- `testPlanId` + `testPlanCaseId`：计划内（必填对）  
- 两者皆空：计划外（仅更新用例库 + 审计）  
- 仅填其一：400 参数错误  

---

## 5. 验收标准

- [x] 计划外 submit 更新 `functional_case.last_execute_result`（`AgentFunctionalCaseSubmitService.submitOutOfPlan`）  
- [x] `agent_exec_log` 有记录，含 steps_snapshot（`AgentExecLogService.log`）  
- [x] exec-log 查询 API 可分页（`GET /api/agent/v1/functional/exec-log/page`）  
- [x] 计划内回写行为不受影响（`testPlanCaseId` 存在时走原逻辑）  

---

## 6. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-08 |
| 完成日期 | 2026-07-08 |
