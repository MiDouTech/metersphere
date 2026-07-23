# task012 - P2 模板必填字段查询 API

> **阶段**：P2  
> **预估工期**：1–1.5 天  
> **前置依赖**：task010  
> **关联方案**：§7.5、§15#2

---

## 1. 任务目标

提供缺陷（及可选功能用例）模板必填自定义字段查询，避免 Agent 仅靠报错试错。

---

## 2. 任务清单

- [ ] 设计只读 API，例如：  
  - `GET /api/agent/v1/bug/template/fields?projectId=&templateId=`  
  - （可选）功能用例模板字段同类接口  
- [ ] Scope：建议 `BUG_WRITE` 或新增 `BUG_READ` / 复用 `FUNCTIONAL_READ`（实现前产品确认）  
- [ ] 返回：`fieldId`、`name`、`required`、`type`、选项枚举（如有）  
- [ ] MCP Tool：`get_bug_template_fields`（可选）  
- [ ] 更新 onboarding：创建缺陷前先拉字段再填 `customFields`  

---

## 3. 验收标准

- [ ] 对有必填自定义字段的项目，Agent 可一次填齐并成功 `create_bug`  
- [ ] 无必填字段时返回空列表且创建仍成功  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 未开始 |
| 备注 | 需产品确认 Scope 命名 |
