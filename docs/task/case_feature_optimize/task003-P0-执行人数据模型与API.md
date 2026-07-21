# task003 - P0 执行人数据模型与 API

> **阶段**：P0  
> **预估工期**：1.5–2 人日  
> **前置依赖**：无  
> **阻塞任务**：task004、task007  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **任务状态**：待开始

---

## 1. 任务目标

为功能用例增加 **执行人** 持久化能力：数据库字段、详情/列表查询、执行结果变更时自动写入执行人。

---

## 2. 字段语义

| 字段 | 说明 |
|------|------|
| `execute_user` | 执行人用户 ID（`user.id`） |
| 自动更新 | 用例级 `lastExecuteResult` 变更时 → `execute_user = 当前操作用户` |
| 手动更新 | 批量修改执行人接口（task004）可覆盖 |

展示字段：`executeUserName`（join `user` 表）。

---

## 3. 数据库变更

```sql
ALTER TABLE functional_case
  ADD COLUMN execute_user VARCHAR(50) DEFAULT NULL COMMENT '执行人用户ID',
  ADD INDEX idx_execute_user (execute_user);
```

- [ ] 编写 Flyway 迁移脚本（遵循项目现有 migration 目录规范）  
- [ ] 更新 `FunctionalCase.java`、`FunctionalCaseMapper.xml`  

---

## 4. 后端任务清单

### 4.1 实体与 DTO

- [ ] `FunctionalCase` 增加 `executeUser`  
- [ ] `FunctionalCaseDetailDTO`、列表响应 DTO 增加 `executeUser` / `executeUserName`  
- [ ] `ExtFunctionalCaseMapper.xml` 列表、详情查询 join `user`  

### 4.2 写入逻辑

- [ ] `FunctionalCaseService.updateCase()`：检测 `lastExecuteResult` 变化时设置 `executeUser = userId`  
- [ ] `handleLastExecuteResult()`（批量改执行结果）同步写 `executeUser`  
- [ ] 单条编辑接口 `FunctionalCaseEditRequest` 若携带 `lastExecuteResult` 变更，同样处理  

### 4.3 批量改执行人 API（供 task004 调用）

- [ ] `POST /functional/case/batch/update/executor`  
- [ ] 请求体参考测试计划：`projectId`、`selectIds`、`selectAll`、`excludeIds`、`condition`、`userId`  
- [ ] 权限：`FUNCTIONAL_CASE:READ+UPDATE`  
- [ ] 操作日志（参考 `FunctionalCaseLogService`）  

---

## 5. 前端任务清单（本任务仅模型与 API 层）

- [ ] `frontend/src/models/caseManagement/featureCase.ts` 增加 `executeUser`、`executeUserName`  
- [ ] `frontend/src/api/modules/case-management/featureCase.ts` 增加 `batchUpdateExecutor`  
- [ ] `frontend/src/api/requrls/case-management/featureCase.ts` 增加 URL 常量  

---

## 6. 涉及文件

| 层级 | 路径 |
|------|------|
| 数据库 | `backend/.../db/migration/`（Flyway） |
| Domain | `backend/framework/domain/.../FunctionalCase.java` |
| Service | `backend/services/case-management/.../FunctionalCaseService.java` |
| Controller | `backend/services/case-management/.../FunctionalCaseController.java` |
| Mapper | `ExtFunctionalCaseMapper.xml` |
| 前端模型 | `frontend/src/models/case-management/featureCase.ts` |

---

## 7. 测试用例

| 场景 | 预期 |
|------|------|
| 新建用例 | `execute_user` 为空 |
| 详情改总执行结果 | `execute_user` = 当前用户 |
| 批量改执行结果 | 选中用例 `execute_user` 批量更新 |
| 批量改执行人 | `execute_user` = 指定用户 |
| 详情/列表查询 | 返回 `executeUserName` |

---

## 8. 验收标准

- [ ] 迁移脚本可执行，回滚方案明确  
- [ ] 执行结果变更自动写执行人  
- [ ] 批量改执行人 API 可用  
- [ ] 列表与详情接口返回执行人姓名
