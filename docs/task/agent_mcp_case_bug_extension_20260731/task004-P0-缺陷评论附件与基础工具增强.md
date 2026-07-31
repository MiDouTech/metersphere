# task004 - P0 缺陷评论、附件与基础工具增强

## 目标

补齐缺陷 MCP 基础维护闭环，支持缺陷评论查询和新增、缺陷详情附件查询/关联/删除，并增强现有缺陷创建和更新工具对附件及关联用例的支持。

## 范围

- `backend/services/agent-integration/`
- `backend/services/bug-management/`
- 缺陷详情附件服务
- 缺陷评论服务
- 缺陷创建和更新工具

## 查询工具

| 工具 | Scope | 说明 |
|---|---|---|
| `metersphere.bug.template.get` | `BUG_READ` | 查询缺陷模板和字段定义 |
| `metersphere.bug.comments.list` | `BUG_READ` | 查询评论 |
| `metersphere.bug.attachments.list` | `BUG_READ` | 查询缺陷详情附件 |
| `metersphere.bug.history.list` | `BUG_READ` | 查询变更历史 |

## 写入工具

| 工具 | Scope | 说明 |
|---|---|---|
| `metersphere.bug.comment.create` | `BUG_COMMENT` | 新增评论或回复 |
| `metersphere.bug.attachment.attach` | `BUG_ATTACHMENT` | 关联缺陷详情附件 |
| `metersphere.bug.attachment.delete` | `BUG_ATTACHMENT` | 删除详情附件 |

## 现有工具增强

`metersphere.bug.create` 和 `metersphere.bug.update` 增加：

```json
{
  "attachmentIds": ["temporary-attachment-id"],
  "addCaseIds": ["case-id"],
  "removeRelationIds": ["relation-id"]
}
```

建议仍保留独立附件和关联工具，使单次操作更容易审计、重试和授权。

## 实现规则

- 缺陷状态、处理人等模板字段继续通过 `customFields` 修改。
- 模板查询工具必须返回字段 ID、字段名称、字段类型、必填属性、可选值及当前值。
- 禁止 Agent 根据显示名称猜测字段 ID 或状态 ID。
- 评论新增支持普通评论和回复。
- 附件通过临时附件上传后再关联，不允许 Base64 进入 tools/call。
- 业务操作必须补齐日志、通知和 Agent 审计。
- 评论、附件关联均支持 `requestId` 幂等。

## 验收标准

- Agent 可查询并新增缺陷评论。
- Agent 上传 PNG 后可关联到缺陷详情附件区域。
- `bug.create` / `bug.update` 可携带临时附件 ID，并最终出现在缺陷详情。
- `bug.create` / `bug.update` 可携带新增或移除关联用例参数，权限不足时拒绝。
- 重复 `requestId` 不产生重复评论或重复附件。
- 用户无 RBAC 或 Token 无 Scope 时返回明确错误。
