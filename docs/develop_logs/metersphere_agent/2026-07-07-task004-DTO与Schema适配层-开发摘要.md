# 开发摘要日志 — task004 DTO 与 Schema 适配层

> **日期**：2026-07-07  
> **任务**：[task004-P0-DTO与Schema适配层.md](../../task/metersphere_agent/task004-P0-DTO与Schema适配层.md)  
> **前置**：task001 ✅  
> **状态**：✅ 已完成

---

## 1. 本次目标

定义 Agent 对外统一 Schema，实现 `AgentCaseSchemaMapper`，屏蔽 MeterSphere 内部字段差异。

---

## 2. DTO 清单

| 类 | 用途 |
|----|------|
| `AgentCaseSearchRequest` / `AgentSearchFilters` | search 请求 |
| `AgentCaseSearchResponse` | search 响应（matchedBy、warnings） |
| `AgentCaseDTO` / `AgentCaseStepDTO` | 用例与步骤 |
| `AgentCaseSubmitRequest` | 回写请求 |
| `AgentModuleDTO` | 模块扁平列表 |

---

## 3. Schema 适配要点

| Agent 字段 | 内部来源 |
|-----------|---------|
| `steps[].expected` | `FunctionalCaseStepDTO.result` |
| `priority` | 自定义字段 `functional_priority` |
| `testPlanCaseId` | `TestPlanCasePageResponse.id` |
| Text 模式 | `textDescription` + `expectedResult` → 虚拟单步 |

---

## 4. warnings 枚举

`AgentWarningCode`：`MODULE_NOT_MATCHED_KEYWORD_FALLBACK`、`TEXT_MODE_CONVERTED`、`TEST_PLAN_CASE_ID_MISSING`、`LARGE_RESULT_TRUNCATED`

---

## 5. 变更文件

| 路径 | 说明 |
|------|------|
| `agent/dto/*.java` | 6 个 DTO |
| `agent/mapper/AgentCaseSchemaMapper.java` | Schema 映射 |
| `agent/constants/AgentWarningCode.java` | 警告码 |
