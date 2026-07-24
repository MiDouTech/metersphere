# task007 - P1 Excel 导入执行人列

> **阶段**：S6  
> **预估工期**：1–1.5 天  
> **前置依赖**：功能用例 `execute_user` 字段已存在  
> **关联方案**：§3.8  
> **提测**：T11  
> **执行日期**：2026-07-24

---

## 1. 任务目标

下载模板增加「执行人」列；导入写入 `functional_case.execute_user`；兼容旧模板。

---

## 2. 任务清单

- [x] `FunctionalCaseImportFiled.EXECUTE_USER` + Cn/Tw/Us Excel 字段  
- [x] `getHead`：等级后、备注前插入执行人  
- [x] Import/Check Listener 解析与校验  
- [x] `FunctionalCaseService.resolveImportExecuteUser`：邮箱 → 用户名(id) → 姓名（唯一）  
- [x] insert/update 写入 `execute_user`；旧模板无列不覆盖  
- [x] 模板批注 i18n  
- [ ] T11 联调（待环境）  

---

## 3. 验收标准

- [x] 代码具备模板列与解析失败报错  
- [ ] T11：有效/无效执行人校验报告正确（待验证）  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **代码已完成，待联调勾选 T11** |
| 备注 | 解析优先级已按方案建议落地 |
