# task001 - P0 - 基线盘点与执行契约冻结

> 状态：已完成（2026-08-06）
> 契约产物：`execution-contract-v1.md`

## 目标

盘点当前 `agent-integration`、功能用例、测试计划、AI Provider、附件和前端执行工作台的真实能力，冻结第一阶段跨模块契约，避免 Runner、后端和前端各自定义状态及数据格式。

## 实施范围

- 核对现有 `AgentExecutionService`、Controller、DTO、Mapper、迁移和 MCP Tool 的已实现/未实现边界。
- 核对计划内、计划外功能用例回写和附件关联能力。
- 定义任务、用例、步骤、自愈、Runner 租约的状态枚举及合法迁移。
- 定义动作、定位器、断言、事件、错误码、证据用途和结果状态 Schema。
- 明确时间、ID、事件序号、幂等键、版本兼容和字段脱敏规范。
- 输出 ADR，确定首期使用独立 Browser Runner、Playwright、Chromium 和游标事件模型。

## 重点文件

- `backend/services/agent-integration/**`
- `backend/services/case-management/**`
- `backend/services/test-plan/**`
- `backend/services/system-setting/**/ai/**`
- `frontend/src/api/modules/ai-execution.ts`
- `frontend/src/views/bug-management/automationExecution/index.vue`
- `metersphere-mcp/src/tools/**`

## 交付物

- 基线能力矩阵和缺口清单。
- JSON Schema/OpenAPI 契约：Action、Assertion、Event、Artifact、RunnerLease。
- 状态机和错误码文档。
- 兼容策略与 ADR。

## 验收标准

- 所有跨进程对象有唯一字段定义和版本号。
- 任务成功、部分成功、失败、阻塞、需人工确认的语义无歧义。
- Runner 无权直接决定任务最终成功。
- 明确首期不支持项和降级行为。
- 后端、Runner、前端和测试负责人完成契约评审。

## 测试要求

- 为 Schema 增加正例、缺字段、未知枚举和向后兼容测试样例。
- 校验当前代码状态常量与新契约的映射，不允许同义多枚举继续扩散。

## 实施记录

- 已核对现有执行任务、用例检索、事件、附件与结果回写基础。
- 已冻结 V1 任务/用例状态、动作、定位器、断言、事件、错误分类与完成语义。
- 已确定独立 Browser Runner、Playwright + Chromium、服务端最终状态裁决和事件游标兼容策略。
