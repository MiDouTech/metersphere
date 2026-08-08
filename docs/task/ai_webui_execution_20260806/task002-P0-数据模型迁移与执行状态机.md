# task002 - P0 - 数据模型迁移与执行状态机

> 状态：主体完成（数据迁移、DTO、Mapper 和条件式任务状态机已实现；真实数据库迁移验收待完成）

## 目标

在现有 `ai_execution_task/case/event` 和回写幂等表基础上，补齐步骤、自愈、Runner 和租约数据模型，并由服务端统一实施状态机。

## 实施范围

- 扩展 `ai_execution_task`：选择模式、提示词、解析 DSL、范围哈希、策略快照、租约、证据和回写汇总状态。
- 扩展 `ai_execution_case`：用例版本/快照、自愈次数、失败分类和单例回写状态。
- 新增 `ai_execution_step`、`ai_execution_healing`、`ai_runner`、`ai_runner_lease`。
- 必要时新增执行资源与现有附件的关联表。
- 实现任务/用例/步骤状态迁移校验，使用条件更新或版本字段防止并发覆盖。
- 建立组织、项目、任务、状态、租约到期时间和事件序号索引。
- 定义清理顺序，防止附件孤儿和审计链断裂。

## 重点文件

- `backend/framework/domain/src/main/resources/migration/<version>/ddl/`
- `backend/framework/domain/src/main/java/io/metersphere/agent/**`（如采用生成 Domain）
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/mapper/**`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentExecutionService.java`

## 状态要求

任务：`CREATED → RESOLVING_SCOPE → WAITING_CONFIRMATION → QUEUED → PREPARING_BROWSER → WAITING_LOGIN → RUNNING → WRITING_BACK → 终态`。

用例：`PENDING → RUNNING → HEALING → SUCCESS/FAILED/BLOCKED/SKIPPED/NEEDS_REVIEW/ERROR`。

步骤必须保留首次失败与最终结果，重试生成 attempt，不覆盖历史。

## 验收标准

- Flyway 在空库和现有升级库均执行成功，重复启动不重复建表。
- 非法状态跳转被拒绝并生成审计事件。
- 并发 Runner/控制请求无法重复领取或覆盖终态。
- 按 taskId 查询用例、步骤、事件、自愈和证据无需全表扫描。
- 迁移可通过停用新功能实现业务回滚，旧功能链路不受影响。

## 测试要求

- 迁移集成测试、状态机参数化测试、并发更新测试。
- 覆盖 Runner 超时、取消与完成竞争、回写失败和部分成功。

## 当前实施记录

- 新增 `V3.7.2_31__ai_webui_execution_runtime.sql`。
- 已扩展任务/用例 DTO 和 MyBatis 映射。
- 已新增步骤与自愈 DTO。
- 已实现集中式 `AgentExecutionStateMachine` 及单元测试。
- 关键任务操作已使用 `status + version` 条件更新，冲突时要求刷新重试。
- `agent-integration` 及全部 18 个依赖模块已通过 Maven `compile`。
- 完整 Reactor 测试被 `system-setting` 既有测试源码缺失类型阻断；该问题与本次执行模块改动无关，真实数据库迁移和状态机集成测试仍需在可运行测试环境完成。
