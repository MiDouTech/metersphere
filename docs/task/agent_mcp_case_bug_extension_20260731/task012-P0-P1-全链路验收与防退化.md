# task012 - P0/P1 全链路验收、契约测试与防退化

## 目标

建立覆盖 MCP 工具、附件闭环、权限安全、前端体验和数据一致性的可重复验收方案，防止后续迭代回退。

## 范围

- 后端单元测试
- 后端集成测试
- MCP 契约测试
- 前端类型检查
- 关键页面手工验收
- 可选 Playwright 自动化

## 单元测试

- 项目标识解析。
- Scope 判定。
- 项目白名单。
- 资源归属校验。
- 乐观锁。
- 幂等请求。
- 附件用途和过期校验。
- Patch 字段合并。

## 集成测试

- PNG 上传后出现在用例详情。
- PNG 上传后出现在缺陷详情。
- 执行日志附件不混入详情附件。
- 用例更新触发业务日志和通知。
- 缺陷更新触发业务日志和通知。
- 评论附件在前端可预览。
- 删除进入回收站且可恢复。
- 无 Scope、无 RBAC、项目越权分别返回明确错误。

## MCP 契约测试

- `tools/list` 返回完整 JSON Schema。
- `readOnlyHint`、`destructiveHint`、`idempotentHint` 正确。
- 参数非法时不进入业务 Service。
- JSON-RPC 错误结构稳定。
- 老工具行为保持兼容。

## 前端验收

### 缺陷管理

- 缺陷详情顶部显示缺陷 ID，不显示缺陷名称。
- 详情 Tab 中展示以下结构：

```text
缺陷名称
缺陷名称内容

缺陷内容
缺陷内容正文
```

- 缺陷详情无“评论”Tab。
- 旧缓存评论 Tab 不导致详情空白。
- 缺陷列表状态为统一圆角标签样式。

### 测试用例列表

- 测试用例列表展示：

```text
测试计划：测试计划名称（ID）  [进度条]
```

- 测试计划 ID 不缺失。
- 测试用例列表个人执行进度与详情页完全一致。

### Agent 集成页面

- Agent 集成页面上方展示“我的 Agent Token”记录表。
- Token 密钥只在创建成功时显示一次，列表中不展示明文密钥。
- 原说明文字位于 Token 记录区下方。

## 建议命令

```powershell
cd frontend
pnpm.cmd type:check
```

```powershell
.\mvnw.cmd -pl backend/services/agent-integration,backend/services/case-management,backend/services/bug-management -am -DskipTests compile
```

```powershell
git diff --check
```

## 交付物

- 验收记录。
- 问题清单。
- MCP 契约测试结果。
- 前端关键页面截图。
- 未覆盖原因说明。
