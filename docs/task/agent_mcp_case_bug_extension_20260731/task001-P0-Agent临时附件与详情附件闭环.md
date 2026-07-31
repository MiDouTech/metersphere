# task001 - P0 Agent 临时附件与详情附件闭环

## 目标

建立 Agent Token 可用的通用临时附件上传机制，并支持将临时附件关联到测试用例详情和缺陷详情，使附件进入现有正式附件表与对象存储目录，前端无需特殊适配即可展示。

## 范围

- `backend/services/agent-integration/`
- 用例详情附件服务
- 缺陷详情附件服务
- 临时附件数据模型
- 附件安全校验与过期清理

## 实现要点

1. 新增 Agent 临时附件上传接口：

```http
POST /api/agent/v1/attachment/upload
Authorization: Bearer <Agent Token>
X-MS-PROJECT: <projectId>
Content-Type: multipart/form-data
```

2. 请求参数：

| 参数 | 必填 | 说明 |
|---|---:|---|
| `file` | 是 | 二进制文件 |
| `purpose` | 是 | `CASE_DETAIL`、`CASE_COMMENT`、`BUG_DETAIL`、`BUG_COMMENT`、`EXECUTION` |
| `stepNum` | 否 | 执行步骤编号，仅执行附件使用 |

3. 响应包含：

- `attachmentId`
- `fileId`
- `fileName`
- `contentType`
- `size`
- `purpose`
- `expiresAt`

4. 新增或复用临时附件存储模型，至少记录：

- Token ID
- 用户 ID
- 项目 ID
- 文件 ID
- 文件名
- MIME
- 文件大小
- 用途 `purpose`
- 过期时间
- 是否已关联

5. 附件关联工具：

- `metersphere.functional.attachment.attach`
- `metersphere.bug.attachment.attach`

6. 附件删除工具：

- `metersphere.functional.attachment.delete`
- `metersphere.bug.attachment.delete`

7. 附件删除参数必须包含：

```json
{
  "projectId": "100006",
  "resourceId": "resource-id",
  "attachmentId": "attachment-id",
  "confirm": true,
  "requestId": "client-request-id"
}
```

8. 删除时必须校验附件确实属于目标资源，禁止只凭附件 ID 删除。

## 安全要求

- 单文件最大 5 MB，沿用现有限制。
- 单次业务操作最多关联 10 个附件。
- 校验扩展名、Content-Type 和真实文件头。
- HTML、SVG 等主动内容默认不允许直接预览。
- 文件名净化，防路径穿越。
- 图片解码校验。
- 下载响应设置安全的 `Content-Disposition`。
- 上传后未关联文件由定时任务清理，默认 24 小时失效。
- `purpose` 必须与最终关联工具一致，不允许 `EXECUTION` 附件直接关联为详情附件。

## 验收标准

- 使用 Agent Token 上传 PNG 后，可以通过工具关联到用例详情，并在现有用例详情附件区域预览。
- 使用 Agent Token 上传 PNG 后，可以通过工具关联到缺陷详情，并在现有缺陷详情附件区域预览。
- Agent 执行日志附件不会自动混入用例详情附件。
- `purpose` 不匹配时返回 `ATTACHMENT_PURPOSE_MISMATCH`。
- 临时附件过期后关联返回 `ATTACHMENT_EXPIRED`。
- 删除附件时，如果附件不属于目标资源，返回明确错误。
