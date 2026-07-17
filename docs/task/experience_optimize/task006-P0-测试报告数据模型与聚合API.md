# task006 - P0 测试报告数据模型与聚合 API

> **阶段**：P0  
> **预估工期**：3–5 人日  
> **前置依赖**：无（统计可复用用例执行/缺陷查询；与 task003 无强依赖）  
> **阻塞任务**：task007  
> **关联方案**：[体验优化产品方案](../../summary/MeterSphere-体验优化-产品方案-2026-07-17.md) §3.4  
> **任务状态**：已完成（MVP）

---

## 1. 任务目标

提供用例模块「测试报告」的存储与一键生成所需聚合 API：执行统计、缺陷双图数据、失败/阻塞用例列表；报告正文可存可改。

---

## 2. 数据模型（建议）

**表**：`functional_test_report`（名称可按仓库规范微调）

| 字段 | 说明 |
|------|------|
| id | 主键 |
| project_id | 项目 |
| name | 报告名称 |
| plan_id | 可选关联测试计划 |
| content | 可编辑正文（HTML/JSON 分节，二选一；建议 **JSON 分节 + 快照统计**） |
| stats_snapshot | 执行统计/图表原始 JSON（只读刷新用） |
| create_user / update_user / time | 审计字段 |

> SQL 须人工审查；Flyway 版本递增。

**权限默认**：复用功能用例模块权限（见 task000）；若后续拆独立权限点，另开任务。

---

## 3. API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/functional/test-report/page` | 报告分页 |
| GET | `/functional/test-report/get/{id}` | 详情 |
| POST | `/functional/test-report/generate` | 一键生成（聚合 + 落库草稿） |
| POST | `/functional/test-report/update` | 保存编辑（文字章节） |
| POST | `/functional/test-report/refresh-stats/{id}` | 按原范围重算统计快照 |
| GET | `/functional/test-report/delete/{id}` | 删除 |

### 3.1 生成请求（建议）

```json
{
  "projectId": "...",
  "name": "可选，默认带日期",
  "planId": "可选，空表示项目范围按约定汇总",
  "startTime": null,
  "endTime": null
}
```

范围默认：见 task000「当前项目 + 可选测试计划」。

### 3.2 聚合规则（对齐方案）

| 块 | 规则 |
|----|------|
| 3.1 执行统计 | 功能用例最新执行（或计划内）：总数/通过/失败/阻塞/执行率/通过率 |
| 通过率 | `(通过 / (总数 - 阻塞 - 失败)) * 100%`；分母 0 → `-`；响应中带 `passRateFormulaNote` |
| 图1 | 缺陷：处理人（`handleUser`）× 处理状态 计数 → 柱状数据 |
| 图2 | 缺陷：类型字段计数 → 饼图数据（字段解析见 task000；找不到则空数组 + message） |
| 遗留风险 | `lastExecResult ∈ {BLOCKED, ERROR/FAILED}` 用例：编号+名称+结果 |
| 不生成 | 「测试依据」；「六、附件」 |

### 3.3 生成响应

返回报告 id + 分节内容（版本概览默认值、测试内容占位、统计快照、遗留列表、结论草稿等），供前端直接进编辑页。

---

## 4. 任务清单

1. DDL + Domain/Mapper/Service/Controller。  
2. 复用现有用例执行统计、缺陷查询（优先扩展而非复制 SQL）。  
3. 项目隔离与鉴权。  
4. 接口测试：生成 → 查询 → 更新文字 → 刷新统计。  

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| 无用例项目生成 | 统计为 0；报告仍可创建 |
| 含失败/阻塞 | 遗留风险列表非空 |
| 刷新统计 | stats 更新；用户改过的文字章节不丢（按 content 结构设计保证） |
| 跨项目访问 | 拒绝 |

---

## 6. 验收标准

- [x] CRUD/生成/刷新 API 可用  
- [ ] 双图维度与遗留规则符合方案（遗留风险已实现；缺陷双图 MVP 占位空数组）  
- [x] 无「测试依据」「附件」字段输出  
- [ ] **人工审核** SQL 与权限  

---

## 7. 交付物

- 迁移 + 后端 API  
- 字段说明（缺陷类型实际取用的 key 写进本文件「实现备注」）  

---

## 8. 实现备注（2026-07-17）

### 迁移

- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_2__functional_test_report.sql`

### API（权限复用功能用例）

| 方法 | 路径 | 权限 |
|------|------|------|
| POST | `/functional/test-report/page` | `FUNCTIONAL_CASE:READ` |
| GET | `/functional/test-report/get/{id}` | `FUNCTIONAL_CASE:READ` |
| POST | `/functional/test-report/generate` | `FUNCTIONAL_CASE:READ+ADD` |
| POST | `/functional/test-report/update` | `FUNCTIONAL_CASE:READ+UPDATE` |
| POST | `/functional/test-report/refresh-stats/{id}` | `FUNCTIONAL_CASE:READ+UPDATE` |
| GET | `/functional/test-report/delete/{id}` | `FUNCTIONAL_CASE:READ+DELETE` |

- page / generate：`@CheckOwner` 校验 `projectId`
- get / update / refresh / delete：Service 内校验报告 `projectId` 与当前项目一致

### content JSON 骨架（无「测试依据」「附件」）

```json
{
  "versionOverview": {},
  "testScope": { "content": "" },
  "conclusion": { "result": "", "suggestion": "" },
  "riskNote": ""
}
```

### 统计实现口径（MVP）

| 块 | 实现 | 说明 |
|----|------|------|
| exec | **真聚合** | 无 `planId`：`functional_case`（`deleted=0 AND latest=1`）按 `last_execute_result` 分组；有 `planId`：`test_plan_functional_case` 按 `last_exec_result` |
| pass/fail/block | **真聚合** | SUCCESS→pass；ERROR/FAKE_ERROR→fail；BLOCKED→block |
| execRate | **真聚合** | `(pass+fail+block)/total` |
| passRate | **真聚合** | `pass/(total-block-fail)`，分母≤0 为 `-`；附 `passRateFormulaNote` |
| riskCases | **真聚合** | ERROR/BLOCKED/FAKE_ERROR，最多 200 条 |
| bugHandlerStatus | **占位** | 空数组 `[]` |
| bugType | **占位** | 空数组 `[]` + `bugTypeMessage`；缺陷类型字段 key 二期再定 |

`startTime` / `endTime` 入参已预留，MVP 未参与过滤。

**【注意】** SQL 与权限须人工审查后再上生产。
