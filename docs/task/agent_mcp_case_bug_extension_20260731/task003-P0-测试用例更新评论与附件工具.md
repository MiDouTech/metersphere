# task003 - P0 测试用例更新、评论与附件工具

## 目标

补齐测试用例 MCP 基础维护闭环，支持已有用例更新、用例评论查询和新增、用例详情附件查询/关联/删除。

## 范围

- `backend/services/agent-integration/`
- `backend/services/case-management/`
- 功能用例详情附件服务
- 功能用例评论服务
- 功能用例更新服务

## 查询工具

| 工具 | Scope | 说明 |
|---|---|---|
| `metersphere.functional.template.get` | `FUNCTIONAL_READ` | 获取默认模板和字段 |
| `metersphere.functional.history.list` | `FUNCTIONAL_READ` | 查询用例变更历史 |
| `metersphere.functional.comments.list` | `FUNCTIONAL_READ` | 查询用例评论 |
| `metersphere.functional.attachments.list` | `FUNCTIONAL_READ` | 查询用例详情附件 |

## 写入工具

| 工具 | Scope | 说明 |
|---|---|---|
| `metersphere.functional.case.update` | `CASE_UPDATE` | 更新已有用例 |
| `metersphere.functional.comment.create` | `CASE_COMMENT` | 新增评论或回复 |
| `metersphere.functional.attachment.attach` | `CASE_ATTACHMENT` | 关联用例详情附件 |
| `metersphere.functional.attachment.delete` | `CASE_ATTACHMENT` | 删除或解除附件 |

## 用例更新请求

```json
{
  "projectId": "100006",
  "caseId": "1565254823337852928",
  "expectedUpdateTime": 1785480787498,
  "patch": {
    "name": "商品查询存在同级码数据时展示关联卡片",
    "moduleId": "1566792971385683970",
    "priority": "P0",
    "tags": ["回归", "PDA"],
    "prerequisite": "测试商品存在同级码数据",
    "description": "用例备注",
    "steps": [
      {
        "id": "step-id",
        "num": 1,
        "desc": "进入商品查询并输入商品码",
        "expected": "展示关联信息卡片"
      }
    ],
    "customFields": {
      "field-id": "field-value"
    }
  },
  "requestId": "client-request-id",
  "reason": "根据最新需求调整步骤"
}
```

## 实现规则

- 采用 Patch 语义，未提供字段不修改。
- 空数组与 `null` 的含义必须明确区分。
- `expectedUpdateTime` 不一致时返回 `VERSION_CONFLICT`。
- 步骤更新必须保留已有步骤 ID，新增步骤由服务端生成 ID。
- 评论新增支持普通评论和回复。
- 评论附件通过临时附件上传后再关联，不允许 Base64 进入 tools/call。
- 业务操作必须补齐日志、通知和 Agent 审计。
- 更新、评论、附件关联均支持 `requestId` 幂等。

## 验收标准

- Agent 可更新已有用例，未提交字段不被覆盖。
- 并发更新时间不一致时返回 `VERSION_CONFLICT`。
- Agent 可查询并新增用例评论。
- Agent 上传 PNG 后可关联到用例详情附件区域。
- 重复 `requestId` 不产生重复评论或重复附件。
- 用户无 RBAC 或 Token 无 Scope 时返回明确错误。
