# task008 - P0 REST Controller 四层接口

> **阶段**：P0  
> **预估工期**：0.5 天  
> **前置依赖**：[task003](task003-P0-AgentToken认证与Shiro集成.md)、[task006](task006-P0-用例检索与导出服务.md)、[task007](task007-P0-计划内结果回写服务.md)  
> **阻塞任务**：task009、task010  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §4.2

---

## 1. 任务目标

实现 `AgentFunctionalCaseController`，暴露 4 个 REST 接口 + health 检查，统一前缀 `/api/agent/v1`。

---

## 2. API 清单

| 方法 | 路径 | Scope | 说明 |
|------|------|-------|------|
| GET | `/api/agent/v1/functional/health` | anon | 健康检查 |
| POST | `/api/agent/v1/functional/search` | READ | 用例检索 |
| GET | `/api/agent/v1/functional/{caseId}` | READ | 单条详情 |
| GET | `/api/agent/v1/functional/modules` | READ | 模块树扁平列表 |
| POST | `/api/agent/v1/functional/submit` | SUBMIT | 结果回写 |

**公共 Header**：

- `Authorization: Bearer msat_<token>`  
- `X-MS-PROJECT: {projectId}`（search/get/modules 必填；submit 可从 body.projectId 取）  

---

## 3. Controller 实现

**路径**：`backend/services/agent-integration/.../controller/AgentFunctionalCaseController.java`

```java
@RestController
@RequestMapping("/api/agent/v1/functional")
@Tag(name = "Agent Functional Case")
public class AgentFunctionalCaseController {

    @PostMapping("/search")
    @Operation(summary = "检索功能用例")
    public AgentCaseSearchResponse search(@RequestBody AgentCaseSearchRequest request) { ... }

    @GetMapping("/{caseId}")
    @Operation(summary = "获取用例详情")
    public AgentCaseDTO get(
        @PathVariable String caseId,
        @RequestParam(defaultValue = "true") boolean includeSteps,
        @RequestParam(required = false) String testPlanId) { ... }

    @GetMapping("/modules")
    @Operation(summary = "模块列表（消歧）")
    public List<AgentModuleDTO> modules(@RequestParam String projectId) { ... }

    @PostMapping("/submit")
    @Operation(summary = "回写执行结果")
    public void submit(@RequestBody @Valid AgentCaseSubmitRequest request) { ... }
}
```

---

## 4. 请求校验

| 接口 | 校验 |
|------|------|
| search | query 与 filters 至少一项非空；pageSize ≤ 500 |
| get | caseId 非空 |
| modules | projectId 非空 |
| submit | @Valid：projectId、caseId、testPlanId、testPlanCaseId、lastExecResult 必填 |

---

## 5. 响应格式

沿用 MeterSphere 统一包装 `ResultHolder` 或直接返回 DTO（与现有 API 风格保持一致）。

**search 响应关键字段**：

```json
{
  "matchedBy": ["module", "filter"],
  "matchedModules": ["订单"],
  "total": 8,
  "warnings": [],
  "cases": [...]
}
```

---

## 6. 集成测试

**路径**：`AgentFunctionalCaseControllerTests.java`

- [ ] health 无需 Token  
- [ ] search 无 Token 返回 401  
- [ ] search 有效 Token 返回 200  
- [ ] submit READ scope 返回 403  
- [ ] submit SUBMIT scope 成功  

---

## 7. 验收标准

- [x] `AgentFunctionalCaseController` 已实现 4 个业务接口 + health  
- [x] Swagger `@Tag` / `@Operation` 注解完整  
- [x] submit 使用 `@Valid` 校验  
- [ ] 运行时 HTTP 可访问（待 task010 联调）  

---

## 8. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-07 |
| 完成日期 | 2026-07-07 |
