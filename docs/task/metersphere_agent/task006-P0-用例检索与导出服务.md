# task006 - P0 用例检索与导出服务

> **阶段**：P0  
> **预估工期**：1.5 天  
> **前置依赖**：[task005](task005-P0-检索条件解析器.md)、[task004](task004-P0-DTO与Schema适配层.md)  
> **阻塞任务**：task008、task010  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §4.2.1、§4.2.2、§4.2.3

---

## 1. 任务目标

实现 `AgentFunctionalCaseSearchService` 与 `AgentFunctionalCaseExportService`，完成 search / get / modules 三条业务链路，支持一次返回完整 steps 与 `testPlanCaseId`。

---

## 2. 服务职责

### 2.1 AgentFunctionalCaseSearchService

| 方法 | 说明 |
|------|------|
| `search(AgentCaseSearchRequest, projectId)` | 主检索入口 |
| `getById(caseId, includeSteps, testPlanId, projectId)` | 单条详情 |
| `listModules(projectId)` | 扁平模块列表（消歧） |

### 2.2 AgentFunctionalCaseExportService

| 方法 | 说明 |
|------|------|
| `enrichWithSteps(List<AgentCaseDTO>)` | 批量调用 `getFunctionalCaseDetail()` |
| `enrichWithTestPlanInfo(List<AgentCaseDTO>, testPlanId)` | 填充 testPlanCaseId、计划内 lastExecuteResult |

---

## 3. search 实现流程

```
1. AgentQueryResolver.resolve(request, projectId)
2. 构建内部查询：
   - 无 testPlanId → FunctionalCasePageRequest + FunctionalCaseService.page()
   - 有 testPlanId → TestPlanFunctionalCasePageRequest + 计划内分页
3. SchemaMapper 转为 AgentCaseDTO 列表
4. includeSteps=true → ExportService.enrichWithSteps()
5. testPlanId 有值 → 强制 enrichWithTestPlanInfo()，缺 testPlanCaseId 时 warning
6. 组装 AgentCaseSearchResponse
```

### 3.1 计划内检索

复用 `POST /test-plan/functional/case/page` 对应 Service：

- `TestPlanCasePageResponse.id` → `testPlanCaseId`  
- 支持按 moduleIds、keyword、lastExecResult 过滤  

### 3.2 用例库检索

复用 `FunctionalCaseService` + `FunctionalCasePageRequest`：

- `moduleIds`、`keyword`（name/num/tags）  
- priority 通过自定义字段子查询（ExtMapper 或 Service 层拼装）  

### 3.3 includeSteps 优化

- `includeSteps=true`：批量拉详情，避免 Agent N+1  
- `includeSteps=false`：仅摘要（name/num/modulePath/priority），用于消歧确认  

### 3.4 分页

- 默认 `pageSize=50`，最大 500  
- 超阈值时 `warnings` 增加 `LARGE_RESULT_TRUNCATED`  

---

## 4. get 实现

```
GET /api/agent/v1/functional/{caseId}?includeSteps=true&testPlanId={planId}
```

1. `FunctionalCaseService.getFunctionalCaseDetail(caseId)`  
2. SchemaMapper 转 AgentCaseDTO  
3. 可选 testPlanId → 查计划关联填充 testPlanCaseId  

---

## 5. modules 实现

```
GET /api/agent/v1/functional/modules?projectId={projectId}
```

1. `FunctionalCaseModuleService.getTree(projectId)`  
2. 树扁平化为 `AgentModuleDTO[]`（id, name, path, parentId）  

---

## 6. 关键复用点

| 能力 | 类 |
|------|-----|
| 用例分页 | `FunctionalCaseService` |
| 计划内分页 | `TestPlanFunctionalCaseService` |
| 用例详情 | `FunctionalCaseService.getFunctionalCaseDetail()` |
| 模块树 | `FunctionalCaseModuleService.getTree()` |
| 关键词 SQL | `ExtFunctionalCaseMapper.xml` |

---

## 7. 单元 / 集成测试

- [ ] search 模块命中返回 matchedModules  
- [ ] search + testPlanId 每条含 testPlanCaseId  
- [ ] includeSteps=true 返回完整 steps  
- [ ] Text 模式返回虚拟步骤 + TEXT_MODE_CONVERTED  
- [ ] priority 过滤生效  
- [ ] 分页与 total 正确  

---

## 8. 验收标准

- [x] search 接口业务逻辑完成（`AgentFunctionalCaseSearchService`）  
- [x] get / modules 接口业务逻辑完成  
- [x] 传 testPlanId 时返回 testPlanCaseId（代码已实现）  
- [x] 不修改现有 UI Service 行为  
- [ ] 计划内/外检索端到端联调（待 task010）  

---

## 9. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-07 |
| 完成日期 | 2026-07-07 |
