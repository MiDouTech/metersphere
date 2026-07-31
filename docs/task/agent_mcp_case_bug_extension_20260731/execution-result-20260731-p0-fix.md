# Agent MCP P0 补齐执行结果（2026-07-31 续）

> 对照：`docs/develop_logs/buglist/2026-07-31-BUG-AGENT-MCP-P0-任务未完整实现巡检.md`
> 状态：P0 核心缺口已按代码落地；P1/P2 完整管理闭环仍未全部实现。

## 已实现（本次补齐）

### task001 临时附件与详情附件闭环
- `POST /api/agent/v1/attachment/upload`（purpose：CASE_DETAIL / CASE_COMMENT / BUG_DETAIL / BUG_COMMENT / EXECUTION）
- 表：`agent_temp_attachment`（含 expiresAt / linked / tokenId / purpose）
- MCP：
  - `metersphere.functional.attachments.list|attachment.attach|attachment.delete`
  - `metersphere.bug.attachments.list|attachment.attach|attachment.delete`
- 用例详情关联写入 `CaseFileSourceType.ATTACHMENT`（保证详情附件区可见）
- 缺陷详情关联复用 `BugAttachmentService.transferTmpFile`
- EXECUTION 同时写入 `agent_exec_attachment`，兼容现有 submit

### task003 用例更新 / 模板 / 历史
- `metersphere.functional.case.update`（Patch + `expectedUpdateTime` → `VERSION_CONFLICT`）
- `metersphere.functional.template.get`
- `metersphere.functional.history.list`
- 既有 comments.list / comment.create 保持

### task004 缺陷增强
- `metersphere.bug.template.get`
- `metersphere.bug.history.list`
- `bug.create` / `bug.update` 支持 `attachmentIds` / `addCaseIds` / `removeRelationIds`
- `bug.update` 支持 `expectedUpdateTime`
- 标准命名：`metersphere.bug.case.relate` / `metersphere.bug.case.unrelate`
- 兼容保留：`metersphere.bug.relate_case`

### task007 幂等 / 错误码 / 白名单
- 表：`agent_idempotency_record`（tokenId + toolName + requestId 落库）
- 相同 requestId 不同参数 → `IDEMPOTENCY_CONFLICT`
- JSON-RPC `-32001` 的 `error.data.code` 返回业务错误码
- 缺陷写操作统一项目解析 + Token 项目白名单

### task008 前端
- 缺陷列表状态改为圆角标签按钮 + dropdown（不再用 a-select）
- 状态样式抽到 `utils/bugStatusStyle.ts`
- 平台默认模板详情补齐缺陷名称展示

## 仍未实现 / 验收风险（明确未完成）

### P1（task005 / task006）
- 用例软删除/恢复/批量更新/移动/复制
- 缺陷删除/恢复/批量更新
- 评论编辑/删除
- 回收站 / 自定义字段独立查询工具

### P2（task011）
- 文件库转存、导入导出、永久删除 Scope、外部同步、操作日志前端

### task012
- 真实环境 PNG 上传→详情可见集成测试尚未在本机联调验证
- 前端截图验收未执行

## 编译与单测
- `mvnw -pl backend/services/agent-integration -am -DskipTests compile`：SUCCESS
- 建议继续跑：`AgentAttachmentPurposeTests` / `AgentMcpToolContractTests` / `AgentScopeAssertTests`

## 人工审核提示
- 附件安全（MIME/魔数/图片解码）目前为扩展名黑名单 + 既有 temp 上传校验，未新增恶意文件扫描
- 评论附件仍主要通过既有 `richTextTmpFileIds`（fileId），未强制走 attachmentIds 参数；上传接口已支持 CASE_COMMENT / BUG_COMMENT purpose
