# task006 - P1 缺陷完整管理闭环

## 目标

在 P0 基础上补齐缺陷完整管理能力，支持删除、恢复、批量更新、关联用例、解除关联、评论编辑/删除、模板、自定义字段、历史记录和回收站查询。

## 范围

- `backend/services/agent-integration/`
- `backend/services/bug-management/`
- 缺陷回收站
- 缺陷批量操作
- 缺陷关联用例
- 缺陷评论管理

## 新增查询工具

| 工具 | Scope | 说明 |
|---|---|---|
| `metersphere.bug.relations.list` | `BUG_READ` | 查询关联用例 |
| `metersphere.bug.trash.list` | `BUG_DELETE` | 查询回收站缺陷 |
| `metersphere.bug.custom_fields.list` | `BUG_READ` | 查询自定义字段 |

## 新增写入工具

| 工具 | Scope | 说明 |
|---|---|---|
| `metersphere.bug.delete` | `BUG_DELETE` | 删除至回收站 |
| `metersphere.bug.restore` | `BUG_DELETE` | 恢复缺陷 |
| `metersphere.bug.batch_update` | `BUG_WRITE` | 批量更新字段 |
| `metersphere.bug.comment.update` | `BUG_COMMENT` | 更新本人评论 |
| `metersphere.bug.comment.delete` | `BUG_COMMENT` | 删除本人评论 |
| `metersphere.bug.case.relate` | `BUG_RELATE` | 关联功能/API/场景用例 |
| `metersphere.bug.case.unrelate` | `BUG_RELATE` | 解除用例关联 |

## 实现规则

- 删除默认进入回收站，不开放永久删除。
- 删除和解除关联必须带 `confirm=true`。
- 关联用例时校验缺陷和用例属于同一项目或产品允许的跨项目规则。
- 解除关联时必须校验 relationId 属于目标缺陷。
- 批量更新必须限制最大数量。
- 评论编辑/删除仅允许本人评论，除非用户 RBAC 具备管理员能力。
- 所有写操作支持 `requestId` 幂等并记录 Agent 审计。

## 验收标准

- 缺陷删除进入回收站，可恢复。
- 无 `BUG_DELETE` Scope 的旧 Token 不能删除或恢复缺陷。
- 缺陷可关联和解除关联用例，越权资源被拒绝。
- 批量更新不会越权跨项目操作。
- 评论编辑和删除遵循本人评论规则。
- 重复 `requestId` 不重复执行批量操作。
