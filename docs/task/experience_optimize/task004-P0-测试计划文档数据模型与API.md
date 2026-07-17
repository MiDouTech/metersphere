# task004 - P0 测试计划文档数据模型与 API

> **阶段**：P0  
> **预估工期**：1–2 人日  
> **前置依赖**：无  
> **阻塞任务**：task005  
> **关联方案**：[体验优化产品方案](../../summary/MeterSphere-体验优化-产品方案-2026-07-17.md) §3.2.4  
> **任务状态**：已完成

---

## 1. 任务目标

为测试计划详情「测试计划」文档提供持久化与读写/导出 API；旧 minder 数据**直接删除**（产品确认无有效业务数据），不做迁移。

---

## 2. 数据模型（建议）

**表名**：`test_plan_document`  
**迁移路径**：`backend/framework/domain/src/main/resources/migration/{version}/`（版本号按仓库现状递增）

```sql
CREATE TABLE test_plan_document (
  id            VARCHAR(50)  NOT NULL COMMENT '主键',
  test_plan_id  VARCHAR(50)  NOT NULL COMMENT '测试计划ID',
  project_id    VARCHAR(50)  NOT NULL COMMENT '项目ID',
  content       LONGTEXT     NOT NULL COMMENT '文档内容(HTML/Markdown)',
  content_type  VARCHAR(20)  NOT NULL DEFAULT 'RICH_TEXT' COMMENT 'RICH_TEXT/MARKDOWN',
  create_time   BIGINT       NOT NULL,
  update_time   BIGINT       NOT NULL,
  create_user   VARCHAR(50)  NOT NULL,
  update_user   VARCHAR(50)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_test_plan (test_plan_id),
  KEY idx_project (project_id)
) COMMENT='测试计划文档';
```

> 【注意】SQL 须本地验证并人工审查后再上生产。

---

## 3. API 设计

| 方法 | 路径 | 说明 | 权限建议 |
|------|------|------|----------|
| GET | `/test-plan/{id}/document` | 无记录时返回空 content + `exists=false` | `PROJECT_TEST_PLAN:READ` |
| POST | `/test-plan/{id}/document` | 保存/覆盖内容 | `PROJECT_TEST_PLAN:READ+UPDATE` |
| GET | `/test-plan/{id}/document/export` | 导出（Word/HTML/PDF 择一，实现阶段定） | `PROJECT_TEST_PLAN:READ` |

**响应建议**：

```json
{
  "testPlanId": "...",
  "content": "...",
  "contentType": "RICH_TEXT",
  "exists": true,
  "updateTime": 0,
  "updateUser": "..."
}
```

**请求体（保存）**：`{ "content": "...", "contentType": "RICH_TEXT" }`

### 3.1 模板元数据（可选同接口或独立）

为前端首次套模板提供自动填充字段（也可纯前端拼）：

| 字段 | 来源 |
|------|------|
| 所属项目 | 项目名称 |
| 测试计划名称 | plan.name |
| 编制日期 | 服务器当天 |
| 编制人 | 当前用户名 |
| 文档编号 | `TP-{projectId短码}-{planNum}-{yyyyMMdd}`（规则可配置常量） |

可增加：`GET /test-plan/{id}/document/template-meta`，或在 GET document 中附带 `templateMeta`。

### 3.2 兼容与清理

- **✅ 产品确认**：旧 minder 业务数据**直接删除**（无有效数据）；不做 minder → document 迁移、不做只读回看提示。  
- 实施：清理 minder 相关表数据；废弃/下线 `getPlanMinder` / `editPlanMinder`（或保留空实现一段时间后删除）。  
- 首次进入文档接口一律返回空，由前端套用富文本模板。 

---

## 4. 任务清单

1. Flyway DDL + Domain/Mapper/Service/Controller。  
2. 项目隔离：校验 plan 属于当前项目。  
3. 操作日志（可选）：保存文档记 UPDATE 日志。  
4. 单测或接口测试：无记录 GET、首次 POST、再次 POST 覆盖。  

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| 新计划 GET | `exists=false`，content 空 |
| POST 保存 | 200；再次 GET 内容一致 |
| 无 UPDATE 权限 POST | 403/业务码拒绝 |
| 跨项目 planId | 拒绝 |

---

## 6. 验收标准

- [x] 表迁移可重复执行于干净库  
- [x] GET/POST 可用且鉴权正确  
- [x] 与 minder 表/接口无强制耦合  

---

## 7. 交付物

- 迁移脚本 + 后端代码  
- 简短 API 说明（可写在本文件）  
- **人工审核**鉴权与 SQL  

### API 说明（实现）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/test-plan/{id}/document` | `PROJECT_TEST_PLAN:READ` |
| POST | `/test-plan/{id}/document` | `PROJECT_TEST_PLAN:READ+UPDATE` |

- 无记录 GET：`exists=false`，`content=""`，并附带 `templateMeta`
- POST body：`{ "content": "...", "contentType": "RICH_TEXT" }`
- 校验：`@CheckOwner` + 计划 `projectId` 与当前项目一致

---

*实现后更新任务状态。*
