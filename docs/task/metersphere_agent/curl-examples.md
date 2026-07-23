# Agent API curl 联调示例

> **关联任务**：[task010-P0-集成测试与MVP验收](task010-P0-集成测试与MVP验收.md)  
> **前置**：MeterSphere 已启动，已创建 Agent Token 与测试数据

---

## 环境变量

```bash
export MS_BASE_URL=http://localhost:8081
export MS_TOKEN=msat_demo_token_for_local_testing_01
export MS_PROJECT=100001100001
export MS_PLAN=plan-001
```

> **自动化验证**：后端启动后执行 `.\scripts\verify-agent-api.ps1`（覆盖 health / 401 / 403 / OpenAPI）。

---

## 1. 健康检查（无需 Token）

```bash
curl "$MS_BASE_URL/api/agent/v1/functional/health"
```

---

## 2. 模块列表（消歧）

```bash
curl "$MS_BASE_URL/api/agent/v1/functional/modules?projectId=$MS_PROJECT" \
  -H "Authorization: Bearer $MS_TOKEN" \
  -H "X-MS-PROJECT: $MS_PROJECT"
```

---

## 3. 检索用例（含 steps + testPlanCaseId）

```bash
curl -X POST "$MS_BASE_URL/api/agent/v1/functional/search" \
  -H "Authorization: Bearer $MS_TOKEN" \
  -H "X-MS-PROJECT: $MS_PROJECT" \
  -H "Content-Type: application/json" \
  -d "{
    \"query\": \"订单\",
    \"testPlanId\": \"$MS_PLAN\",
    \"filters\": { \"priority\": [\"P0\"] },
    \"includeSteps\": true,
    \"current\": 1,
    \"pageSize\": 50
  }"
```

### 3.1 摘要模式（消歧确认）

```bash
curl -X POST "$MS_BASE_URL/api/agent/v1/functional/search" \
  -H "Authorization: Bearer $MS_TOKEN" \
  -H "X-MS-PROJECT: $MS_PROJECT" \
  -H "Content-Type: application/json" \
  -d "{
    \"query\": \"订单\",
    \"includeSteps\": false
  }"
```

---

## 4. 单条用例详情

```bash
curl "$MS_BASE_URL/api/agent/v1/functional/{caseId}?includeSteps=true&testPlanId=$MS_PLAN" \
  -H "Authorization: Bearer $MS_TOKEN" \
  -H "X-MS-PROJECT: $MS_PROJECT"
```

---

## 5. 回写执行结果（计划内）

```bash
curl -X POST "$MS_BASE_URL/api/agent/v1/functional/submit" \
  -H "Authorization: Bearer $MS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"projectId\": \"$MS_PROJECT\",
    \"caseId\": \"fc-001\",
    \"testPlanId\": \"$MS_PLAN\",
    \"testPlanCaseId\": \"relate-001\",
    \"lastExecResult\": \"SUCCESS\",
    \"executedBy\": \"cursor-agent\",
    \"steps\": [{
      \"id\": \"step-uuid-1\",
      \"num\": 1,
      \"actualResult\": \"页面正常加载\",
      \"executeResult\": \"SUCCESS\"
    }],
    \"content\": \"Agent 自动执行完成\"
  }"
```

> **注意**：`testPlanCaseId` 是计划关联 ID（`test_plan_functional_case.id`），**不是** `caseId`。

---

## 6. 创建项目（需 PROJECT_WRITE）

```bash
curl -X POST "$MS_BASE_URL/api/agent/v1/project/create" \
  -H "Authorization: Bearer $MS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"organizationId\": \"$MS_ORG\",
    \"name\": \"Agent-Demo\",
    \"userIds\": [\"admin\"]
  }"
```

---

## 7. 批量导入用例（需 CASE_WRITE）

```bash
curl -X POST "$MS_BASE_URL/api/agent/v1/functional/case/batch-create" \
  -H "Authorization: Bearer $MS_TOKEN" \
  -H "X-MS-PROJECT: $MS_PROJECT" \
  -H "Content-Type: application/json" \
  -d "{
    \"projectId\": \"$MS_PROJECT\",
    \"modulePath\": \"登录/短信登录\",
    \"cases\": [{
      \"name\": \"短信登录成功\",
      \"priority\": \"P0\",
      \"steps\": [{\"num\": 1, \"desc\": \"输入手机号\", \"expected\": \"发送成功\"}]
    }]
  }"
```

---

## 8. 创建测试计划并关联（需 PLAN_WRITE）

```bash
curl -X POST "$MS_BASE_URL/api/agent/v1/test-plan/create" \
  -H "Authorization: Bearer $MS_TOKEN" \
  -H "X-MS-PROJECT: $MS_PROJECT" \
  -H "Content-Type: application/json" \
  -d "{
    \"projectId\": \"$MS_PROJECT\",
    \"name\": \"Agent计划\",
    \"caseIds\": [\"case-id-1\"]
  }"
```

---

## 9. 创建评审（需 REVIEW_WRITE）

```bash
curl -X POST "$MS_BASE_URL/api/agent/v1/case-review/create" \
  -H "Authorization: Bearer $MS_TOKEN" \
  -H "X-MS-PROJECT: $MS_PROJECT" \
  -H "Content-Type: application/json" \
  -d "{
    \"projectId\": \"$MS_PROJECT\",
    \"name\": \"Agent评审\",
    \"caseIds\": [\"case-id-1\"]
  }"
```

---

## 10. 创建缺陷（需 BUG_WRITE）

```bash
curl -X POST "$MS_BASE_URL/api/agent/v1/bug/create" \
  -H "Authorization: Bearer $MS_TOKEN" \
  -H "X-MS-PROJECT: $MS_PROJECT" \
  -H "Content-Type: application/json" \
  -d "{
    \"projectId\": \"$MS_PROJECT\",
    \"title\": \"登录失败\",
    \"description\": \"实际：报错；期望：成功\",
    \"caseId\": \"case-id-1\",
    \"caseType\": \"FUNCTIONAL\"
  }"
```

---

## 11. OpenAPI Spec

```bash
curl "$MS_BASE_URL/v3/api-docs/agent"
```

---

## 12. 常见错误

| HTTP | 原因 | 处理 |
|------|------|------|
| 401 | Token 无效/过期 | 检查 Authorization |
| 403 | Scope 不足 | 对应 WRITE/SUBMIT 或 AGENT_ALL |
| 400 | 缺 X-MS-PROJECT | 添加 Header 或 Token 绑定默认项目 |
| 400 | testPlanCaseId 错误 | 先 search 获取正确 ID |
| 400 | 缺陷必填字段 | 传 customFields |
