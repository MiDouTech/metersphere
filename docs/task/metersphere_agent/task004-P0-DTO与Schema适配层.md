# task004 - P0 DTO 与 Schema 适配层

> **阶段**：P0  
> **预估工期**：1 天  
> **前置依赖**：[task001](task001-P0-agent-integration模块脚手架.md)  
> **阻塞任务**：task005、task006、task007  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §4.4、附录 B

---

## 1. 任务目标

定义 Agent 对外统一 Schema（DTO），实现 `AgentCaseSchemaMapper`，屏蔽 MeterSphere 内部字段差异（Step/Text 模式、priority 自定义字段、步骤 result→expected 映射）。

---

## 2. DTO 清单

### 2.1 请求 DTO

| 类 | 字段 |
|----|------|
| `AgentCaseSearchRequest` | query, includeSteps, testPlanId, filters, current, pageSize |
| `AgentSearchFilters` | priority, lastExecuteResult, tags, moduleIds |
| `AgentCaseSubmitRequest` | projectId, caseId, testPlanId, testPlanCaseId, lastExecResult, executedBy, steps, content |

### 2.2 响应 DTO

| 类 | 字段 |
|----|------|
| `AgentCaseSearchResponse` | matchedBy, matchedModules, matchedModuleIds, total, warnings, cases |
| `AgentCaseDTO` | caseId, num, name, modulePath, caseEditType, tags, priority, prerequisite, testPlanId, testPlanCaseId, lastExecuteResult, steps |
| `AgentCaseStepDTO` | id, num, desc, expected, actualResult, executeResult |
| `AgentModuleDTO` | id, name, path, parentId |

### 2.3 warnings 枚举

| code | 含义 |
|------|------|
| `MODULE_NOT_MATCHED_KEYWORD_FALLBACK` | 模块未命中，降级 keyword |
| `TEXT_MODE_CONVERTED` | Text 模式已转虚拟步骤 |
| `TEST_PLAN_CASE_ID_MISSING` | 未传 testPlanId，无回写 ID |
| `LARGE_RESULT_TRUNCATED` | 命中数超阈值 |

---

## 3. AgentCaseSchemaMapper

**路径**：`backend/services/agent-integration/.../mapper/AgentCaseSchemaMapper.java`

### 3.1 用例字段映射

| AgentCaseDTO | 内部来源 |
|-------------|---------|
| caseId | `FunctionalCase.id` |
| expected（步骤） | `FunctionalCaseStepDTO.result` |
| priority | 自定义字段 `functional_priority` |
| modulePath | 模块树 path 拼接 |
| testPlanCaseId | `TestPlanCasePageResponse.id` |
| lastExecuteResult | 计划内：`test_plan_functional_case.last_exec_result`；否则用例库 |

### 3.2 Text 模式适配

当 `caseEditType = Text` 且 `steps` 为空时：

```json
{
  "caseEditType": "TEXT",
  "steps": [{
    "num": 1,
    "desc": "{textDescription}",
    "expected": "{expectedResult}"
  }]
}
```

响应 `warnings` 增加 `TEXT_MODE_CONVERTED`。

### 3.3 回写映射

```java
// Agent steps[] → FunctionalCaseStepDTO[] → JSON string
List<FunctionalCaseStepDTO> steps = toFunctionalCaseSteps(submit.getSteps());
String stepsExecResult = JSON.toJSONString(steps);
```

- 保留 Agent 传入的 `step.id`  
- `executeResult` 枚举对齐 `ResultStatus`  

### 3.4 priority 读取

通过 `FunctionalCaseCustomFieldService` 或现有自定义字段查询，读取 `functional_priority` 字段值。

---

## 4. 参考内部类

| 类 | 路径 |
|----|------|
| FunctionalCaseDetailDTO | `case-management/.../dto/FunctionalCaseDetailDTO.java` |
| FunctionalCaseStepDTO | `case-management/.../dto/FunctionalCaseStepDTO.java` |
| TestPlanCasePageResponse | `test-plan/.../dto/response/TestPlanCasePageResponse.java` |
| TestPlanCaseRunRequest | `test-plan/.../dto/request/TestPlanCaseRunRequest.java` |

---

## 5. 单元测试

- [ ] Step 模式 steps 正确映射 expected  
- [ ] Text 模式生成虚拟步骤 + warning  
- [ ] priority 从自定义字段正确读取  
- [ ] submit steps 转为 `stepsExecResult` JSON 格式与 `run()` 兼容  

---

## 6. 验收标准

- [x] 所有 DTO 类创建完成，字段与方案一致  
- [x] `AgentCaseSchemaMapper` 已实现（Step/Text/priority 适配）  
- [x] `testPlanCaseId` 与 `caseId` 在 DTO / OpenAPI 注释中明确区分  
- [ ] SchemaMapper 单元测试通过（待 task010）  

---

## 7. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-07 |
| 完成日期 | 2026-07-07 |
