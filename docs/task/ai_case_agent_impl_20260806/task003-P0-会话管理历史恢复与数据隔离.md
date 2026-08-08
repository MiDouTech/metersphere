# task003 - P0 - 会话管理、历史恢复与数据隔离

## 状态

进行中。

已新增服务端生成 ID 的会话创建、项目/用户隔离分页、消息游标查询、重命名、归档、删除和运行中会话保护接口。待前端移除 localStorage 消息及完成数据库/越权测试后标记完成。

## 目标

提供用例 Agent 专用的会话创建、分页、详情、消息历史、重命名、归档和删除能力，以后端数据替代 localStorage 中的完整聊天记录。

## 依赖

- task001 会话和消息数据模型。

## 实现范围

### 1. 会话接口

```http
POST /functional/case/ai/agent/conversation/create
POST /functional/case/ai/agent/conversation/page
GET  /functional/case/ai/agent/conversation/{id}
GET  /functional/case/ai/agent/conversation/{id}/messages
POST /functional/case/ai/agent/conversation/rename
POST /functional/case/ai/agent/conversation/archive
POST /functional/case/ai/agent/conversation/delete
```

### 2. 会话创建

- 服务端生成 conversationId，不接受客户端指定主键。
- 创建时绑定 projectId、organizationId、userId、modelSourceId。
- 标题可以先取首条消息摘要，异步生成标题失败不能影响会话。
- 创建空会话时不得调用模型消耗 Token。

### 3. 历史查询

- 按最后消息时间倒序分页。
- 消息分页或游标加载，不能一次返回无限历史。
- 消息只返回当前用户有权读取的脱敏内容。
- 返回会话当前是否有运行中执行，便于页面恢复。

### 4. 删除与保留

- 首期采用软删除或归档，明确消息、草稿、文件的关联处理。
- 删除会话不得误删已保存的正式用例。
- 物理清理由独立保留策略执行。

### 5. localStorage 迁移

- 新页面只保存当前 conversationId、布局和非敏感偏好。
- 不上传旧 localStorage 中的聊天内容。
- 旧 key 应清理或停止读取，避免切换用户泄漏。

## 权限要求

- 所有接口要求 `FUNCTIONAL_CASE_AI:READ`。
- 创建和修改会话要求 `FUNCTIONAL_CASE_AI:GENERATE`。
- 所有查询必须绑定 projectId + currentUser。
- 首期项目管理员也不能默认查看成员消息正文。

## 验收标准

- 会话可创建、分页、恢复、重命名和删除。
- 页面刷新、重新登录和跨设备后能恢复后端会话。
- 切换用户后看不到上一用户的会话。
- 切换项目后不会混入其他项目会话。
- 删除会话不会删除正式用例。
- localStorage 不再包含完整用户消息和 AI 回复。

## 测试要求

- Controller、Service 和数据库集成测试。
- 会话分页、消息游标和排序测试。
- 跨项目、跨用户、无权限账号测试。
- 删除会话关联数据策略测试。
- 浏览器刷新和用户切换测试。
