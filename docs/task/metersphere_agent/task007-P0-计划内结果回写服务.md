# task007 - P0 计划内结果回写服务

> **阶段**：P0  
> **预估工期**：1 天  
> **前置依赖**：[task004](task004-P0-DTO与Schema适配层.md)  
> **阻塞任务**：task008、task010、task013、task016  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §4.2.4

---

## 1. 任务目标

实现 `AgentFunctionalCaseSubmitService`，将 Agent 友好 submit 请求映射为 `TestPlanCaseRunRequest`，复用 `TestPlanFunctionalCaseService.run()` 完成计划内完整回写闭环。

---

## 2. 核心映射（易错点）

| Agent 字段 | 内部字段 | 备注 |
|-----------|---------|------|
| testPlanCaseId | `TestPlanCaseRunRequest.id` | **不是 caseId** |
| caseId | `TestPlanCaseRunRequest.caseId` | 功能用例 ID |
| steps[] | `stepsExecResult` | JSON 字符串，非数组 |
| projectId | `TestPlanCaseRunRequest.projectId` | 必填 |

---

## 3. 实现流程

```java
@Service
public class AgentFunctionalCaseSubmitService {

    public void submit(AgentCaseSubmitRequest submit) {
        // 1. 校验必填：projectId, caseId, testPlanId, testPlanCaseId, lastExecResult
        // 2. 校验 lastExecResult ∈ ResultStatus
        // 3. 映射 TestPlanCaseRunRequest
        TestPlanCaseRunRequest runRequest = new TestPlanCaseRunRequest();
        runRequest.setProjectId(submit.getProjectId());
        runRequest.setId(submit.getTestPlanCaseId());
        runRequest.setCaseId(submit.getCaseId());
        runRequest.setTestPlanId(submit.getTestPlanId());
        runRequest.setLastExecResult(submit.getLastExecResult());
        runRequest.setStepsExecResult(schemaMapper.toStepsExecResultJson(submit.getSteps()));
        runRequest.setContent(formatContent(submit.getExecutedBy(), submit.getContent()));

        // 4. 调用现有 Service（禁止直接写 DB）
        testPlanFunctionalCaseService.run(runRequest, logInsertModule);
    }

    private String formatContent(String executedBy, String content) {
        if (StringUtils.isNotBlank(executedBy)) {
            return "[" + executedBy + "] " + StringUtils.defaultString(content);
        }
        return content;
    }
}
```

---

## 4. run() 写入目标（已有逻辑，不修改）

| 目标 | 表/字段 |
|------|---------|
| 计划用例状态 | `test_plan_functional_case.last_exec_result` |
| 用例库状态 | `functional_case.last_execute_result` |
| 步骤实际结果 | `functional_case_blob.steps` |
| 执行历史 | `test_plan_case_execute_history` |

---

## 5. 校验规则

| 规则 | 说明 |
|------|------|
| testPlanCaseId 归属 | 校验属于 testPlanId + caseId |
| 步骤 num 对齐 | 可选：警告步骤数不一致 |
| Scope | FUNCTIONAL_SUBMIT 或 FUNCTIONAL_ALL |
| 项目权限 | 复用现有 Shiro 项目权限 |

---

## 6. MVP 范围说明

**本任务仅实现计划内回写**。计划外回写（无 testPlanCaseId）在 [task013](task013-P2-计划外回写与审计日志API.md) 实现。

**MVP 建议**：团队预置「Agent 专用测试计划」，用例预先关联。

---

## 7. 单元 / 集成测试

- [ ] submit SUCCESS 后 `test_plan_functional_case.last_exec_result` 更新  
- [ ] `functional_case.last_execute_result` 同步更新  
- [ ] `test_plan_case_execute_history` 新增记录  
- [ ] steps actualResult 写入 blob  
- [ ] testPlanCaseId 错误时返回 4xx  
- [ ] content 含 executedBy 前缀  

---

## 8. 验收标准

- [x] `AgentFunctionalCaseSubmitService` 映射 `TestPlanCaseRunRequest` 已实现  
- [x] 不修改 `TestPlanFunctionalCaseService.run()` 核心逻辑  
- [x] API / DTO 明确 testPlanCaseId ≠ caseId  
- [ ] submit 后平台 UI「测试计划 → 执行历史」可见（待 task010 联调）  

---

## 9. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-07 |
| 完成日期 | 2026-07-07 |
