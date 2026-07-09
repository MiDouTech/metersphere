# task009 - P0 OpenAPI Agent 分组

> **阶段**：P0  
> **预估工期**：0.5 天  
> **前置依赖**：[task008](task008-P0-REST-Controller四层接口.md)  
> **阻塞任务**：task010、task011  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §6

---

## 1. 任务目标

开启 springdoc OpenAPI，注册 Agent API 独立分组，供 GPT/Claude/Dify 等平台导入 Tool 定义。

---

## 2. 配置变更

### 2.1 commons.properties

```properties
springdoc.api-docs.enabled=true
springdoc.swagger-ui.enabled=true
springdoc.api-docs.groups.enabled=true
```

**说明**：生产环境可通过 profile 关闭 Swagger UI，保留 api-docs。

### 2.2 Agent 分组 Bean

**路径**：`backend/services/agent-integration/.../config/AgentOpenApiConfig.java`（或 app 模块）

```java
@Bean
public GroupedOpenApi agentApi() {
    return GroupedOpenApi.builder()
        .group("agent")
        .pathsToMatch("/api/agent/v1/**")
        .build();
}
```

### 2.3 Controller 注解补全

确保 task008 Controller 方法有：

- `@Tag`  
- `@Operation(summary = "...")`  
- 请求/响应 DTO 有 `@Schema` 描述关键字段（尤其 `testPlanCaseId`）  

---

## 3. 访问地址

| 文档 | URL |
|------|-----|
| Agent API Spec | `GET /v3/api-docs/agent` |
| 全量 Spec | `GET /v3/api-docs` |
| Swagger UI | `/swagger-ui.html` |

---

## 4. OpenAPI 文档要点

在 Schema 描述中强调：

1. `testPlanCaseId` 是计划关联 ID，回写时映射为 `TestPlanCaseRunRequest.id`  
2. `steps[].expected` 对应内部 `result` 字段  
3. `filters.priority` 映射自定义字段 `functional_priority`  
4. 认证：`Authorization: Bearer msat_<token>`  

---

## 5. 验收标准

- [x] `AgentOpenApiConfig` 注册 `agent` 分组（`/api/agent/v1/**`）  
- [ ] `/v3/api-docs/agent` 运行时返回 JSON（需开启 springdoc，待 task010）  
- [ ] Swagger UI 可选择 `agent` 分组（待 task010）  
- [x] 不影响现有 API 文档配置  

---

## 6. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-07 |
| 完成日期 | 2026-07-07 |
| 备注 | 配置类已交付；运行时 OpenAPI 访问归属 task010 |
