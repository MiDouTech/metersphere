# task005 - P1 测试用例完整管理闭环

## 目标

在 P0 基础上补齐测试用例完整管理能力，支持软删除、恢复、批量更新、移动、复制、评论编辑/删除、模板、自定义字段、历史记录和回收站查询。

## 范围

- `backend/services/agent-integration/`
- `backend/services/case-management/`
- 用例回收站
- 用例批量操作
- 用例评论管理

## 新增查询工具

| 工具 | Scope | 说明 |
|---|---|---|
| `metersphere.functional.trash.list` | `CASE_DELETE` | 查询回收站用例 |
| `metersphere.functional.custom_fields.list` | `FUNCTIONAL_READ` | 查询自定义字段 |

## 新增写入工具

| 工具 | Scope | 说明 |
|---|---|---|
| `metersphere.functional.case.delete` | `CASE_DELETE` | 软删除到回收站 |
| `metersphere.functional.case.restore` | `CASE_DELETE` | 从回收站恢复 |
| `metersphere.functional.case.batch_update` | `CASE_UPDATE` | 批量更新字段 |
| `metersphere.functional.case.batch_move` | `CASE_UPDATE` | 批量移动 |
| `metersphere.functional.case.batch_copy` | `CASE_WRITE` | 批量复制 |
| `metersphere.functional.comment.update` | `CASE_COMMENT` | 更新本人评论 |
| `metersphere.functional.comment.delete` | `CASE_COMMENT` | 删除本人评论 |

## 删除策略

- 第一阶段只开放软删除。
- `confirm=true` 必填。
- 删除工具声明 `destructiveHint: true`。
- 批量删除限制最多 100 条。
- 永久删除放入 P2 独立 Scope，默认关闭。

## 实现规则

- 所有写操作支持 `requestId`。
- 更新类操作支持 `expectedUpdateTime` 或等价乐观锁机制。
- 批量操作必须限制最大数量。
- 恢复前校验资源仍属于 Token 允许项目范围。
- 评论编辑/删除仅允许本人评论，除非用户 RBAC 具备管理员能力。
- 所有操作写业务日志和 Agent 审计。

## 验收标准

- 用例删除进入回收站，可恢复。
- 无 `CASE_DELETE` Scope 的旧 Token 不能删除或恢复用例。
- 批量更新、移动、复制不会越权跨项目操作。
- 评论编辑和删除遵循本人评论规则。
- 重复 `requestId` 不重复执行批量操作。
