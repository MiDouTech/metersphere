# task016 - P2 执行证据附件

> **阶段**：P2  
> **预估工期**：2 天  
> **前置依赖**：[task007](task007-P0-计划内结果回写服务.md)  
> **阻塞任务**：无  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §9.1

---

## 1. 任务目标

支持 Agent 在执行失败或需要追溯时上传截图/附件，关联到测试计划执行历史，便于人工复核。

---

## 2. 设计方案

### 2.1 扩展 submit 请求

```json
{
  "caseId": "fc-001",
  "testPlanCaseId": "relate-001",
  "lastExecResult": "ERROR",
  "steps": [...],
  "attachments": [
    {
      "name": "step1-failure.png",
      "base64": "...",
      "stepNum": 1
    }
  ]
}
```

或先上传后关联：

```
POST /api/agent/v1/functional/attachment/upload
→ 返回 attachmentId
→ submit 时传 attachmentIds: ["att-001"]
```

### 2.2 存储

复用 MeterSphere 现有文件存储（MinIO / 本地），关联到：

- `test_plan_case_execute_history` 附件字段（若已有）  
- 或新增 `agent_exec_attachment` 关联表  

```sql
CREATE TABLE agent_exec_attachment (
    id              VARCHAR(50) PRIMARY KEY,
    exec_history_id VARCHAR(50) COMMENT '计划内执行历史 ID',
    exec_log_id     VARCHAR(50) COMMENT '计划外审计日志 ID',
    file_id         VARCHAR(50) NOT NULL COMMENT '平台文件 ID',
    step_num        INT,
    create_time     BIGINT
);
```

### 2.3 大小限制

- 单文件最大 5MB（可配置）  
- 单次 submit 最多 10 个附件  

---

## 3. 任务清单

### 3.1 后端

| 类 | 职责 |
|----|------|
| `AgentAttachmentService` | 上传、关联、查询 |
| `AgentFunctionalCaseSubmitService` | submit 后关联附件到执行历史 |

### 3.2 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/agent/v1/functional/attachment/upload` | 上传附件 |
| GET | `/api/agent/v1/functional/attachment/{id}` | 获取附件信息/下载 URL |

### 3.3 MCP

`upload_execution_attachment` Tool，供 Playwright 截图后上传。

---

## 4. 与 Playwright 集成建议

Agent 工作流：

1. 步骤执行失败 → Playwright `page.screenshot()`  
2. 调用 upload API  
3. submit 时附带 attachmentIds  
4. 平台执行历史可查看截图  

---

## 5. 验收标准

- [x] 可上传 PNG 截图并关联到计划内执行历史（upload + `planCommentFileIds`）  
- [x] 提供附件下载 API（`GET .../attachment/download/{projectId}/{fileId}`）  
- [x] 超大文件返回 413（5MB 限制）  
- [x] 计划外回写可关联到 agent_exec_log（`exec_log_id`）  
- [x] MCP `upload_execution_attachment` Tool 已实现  

---

## 6. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-08 |
| 完成日期 | 2026-07-08 |
