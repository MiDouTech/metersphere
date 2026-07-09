# task010 - P0 集成测试与 MVP 验收

> **阶段**：P0  
> **预估工期**：1 天  
> **前置依赖**：[task008](task008-P0-REST-Controller四层接口.md)、[task009](task009-P0-OpenAPI-Agent分组.md)  
> **阻塞任务**：task011  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §11 阶段 1

---

## 1. 任务目标

编写端到端集成测试与 curl 联调文档，完成 MVP 验收清单，确保 Agent API 可独立使用。

---

## 2. 测试数据准备

### 2.1 前置条件

| 项 | 说明 |
|----|------|
| 项目 | 已有测试项目 `proj-001` |
| 模块 | 「订单」「订单/下单流程」 |
| 用例 | 至少 3 条 Step 模式 + 1 条 Text 模式 |
| 测试计划 | 「Agent-功能测试-2026」，用例已关联 |
| Token | `msat_xxx`，scope=FUNCTIONAL_ALL |
| 自定义字段 | `functional_priority` = P0/P1 |

### 2.2 Fixture 脚本

**路径**：`docs/task/fixtures/agent_integration_test_data.sql`

- [x] 脚本文件已创建  
- [ ] 本地环境导入并验证数据可用  

---

## 3. 集成测试用例

### 3.1 认证

- [ ] 无 Token → 401  
- [ ] 无效 Token → 401  
- [ ] READ scope 无法 submit → 403  
- [ ] 有效 Token + X-MS-PROJECT → 200  

### 3.2 search

- [ ] `query=订单` 模块命中，返回 matchedModules  
- [ ] `query=订单` + `filters.priority=P0` 组合过滤  
- [ ] `testPlanId` 传入，每条含 testPlanCaseId  
- [ ] `includeSteps=true` 含完整 steps  
- [ ] Text 模式含虚拟步骤 + TEXT_MODE_CONVERTED  
- [ ] 模块未命中降级 keyword + warning  

### 3.3 get / modules

- [ ] get 单条详情与 search 一致  
- [ ] modules 返回扁平列表含 path  

### 3.4 submit

- [ ] 计划内 submit SUCCESS → 执行历史可见  
- [ ] steps actualResult 写入 blob  
- [ ] testPlanCaseId 错误 → 4xx  
- [ ] lastExecResult 枚举校验  

### 3.5 回归

- [ ] 现有 `POST /functional/case/page` 行为不变  
- [ ] 现有 `POST /test-plan/functional/case/run` 行为不变  

---

## 4. curl 联调文档

**路径**：`docs/task/metersphere_agent/curl-examples.md`（本任务产出）

- [x] 文档已创建，含 search / modules / submit / health 示例  
- [ ] 本地 MeterSphere + Token 手工复现端到端  

```bash
# 1. 检索（含 steps + testPlanCaseId）
curl -X POST http://localhost:8081/api/agent/v1/functional/search \
  -H "Authorization: Bearer msat_xxx" \
  -H "X-MS-PROJECT: proj-001" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "订单",
    "testPlanId": "plan-001",
    "filters": { "priority": ["P0"] },
    "includeSteps": true
  }'

# 2. 模块列表
curl "http://localhost:8081/api/agent/v1/functional/modules?projectId=proj-001" \
  -H "Authorization: Bearer msat_xxx" \
  -H "X-MS-PROJECT: proj-001"

# 3. 回写
curl -X POST http://localhost:8081/api/agent/v1/functional/submit \
  -H "Authorization: Bearer msat_xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "proj-001",
    "caseId": "fc-001",
    "testPlanId": "plan-001",
    "testPlanCaseId": "relate-001",
    "lastExecResult": "SUCCESS",
    "executedBy": "cursor-agent",
    "steps": [{
      "id": "step-uuid-1",
      "num": 1,
      "actualResult": "通过",
      "executeResult": "SUCCESS"
    }],
    "content": "Agent 自动执行完成"
  }'
```

---

## 5. MVP 完成定义

> **说明**：带「代码」表示 task001–009 已交付实现；带「运行时」表示需本任务联调验证。

- [x] Agent Token 鉴权代码已交付（Filter + Shiro，运行时待验）  
- [x] search 在含 testPlanId 时返回 testPlanCaseId（代码已实现，运行时待验）  
- [ ] submit 后平台「测试计划 → 执行历史」可见记录（运行时待验）  
- [x] Text 模式用例可返回可执行 steps（`AgentCaseSchemaMapper` 已实现）  
- [x] OpenAPI `agent` 分组配置已交付（`/v3/api-docs/agent` 运行时待验）  
- [x] 无改动现有 `/functional/case/*` UI API 行为（未修改 UI Service）  

---

## 6. 测试类路径

> **当前状态**：`backend/services/agent-integration/src/test/` 目录尚未创建。

```
backend/services/agent-integration/src/test/java/io/metersphere/agent/
├── controller/AgentFunctionalCaseControllerTests.java   ← 待创建
├── resolver/AgentQueryResolverTests.java                ← 待创建
└── mapper/AgentCaseSchemaMapperTests.java               ← 待创建
```

---

## 7. 验收标准

- [x] P0 代码编译通过（`mvn compile -pl backend/app -am -DskipTests`）  
- [x] curl 联调文档已产出（`curl-examples.md`）  
- [x] 测试 Fixture 脚本已产出（`docs/task/fixtures/agent_integration_test_data.sql`）  
- [ ] 集成测试类创建并通过  
- [ ] curl 文档可手工复现端到端  
- [ ] MVP 完成定义全部勾选（含运行时验证）  

---

## 8. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 进行中 |
| 开始日期 | 2026-07-07 |
| 完成日期 | |
| 备注 | 文档与编译验证已完成；集成测试、Flyway 运行时迁移、端到端联调待完成 |
