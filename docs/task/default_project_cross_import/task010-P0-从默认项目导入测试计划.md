# task010 - P0 从默认项目导入测试计划

> **阶段**：S9  
> **预估工期**：3–4 天  
> **前置依赖**：task008（计划镜像与 document 同步）  
> **阻塞任务**：task011  
> **关联方案**：§3.1  
> **提测**：T1、T1b、T15  

---

## 1. 任务目标

测试计划列表支持从【米多公司默认项目】**单条**导入；仅复制「测试计划」Tab / 规划文档；冲突 SKIP/OVERWRITE。

---

## 2. 任务清单

### 2.1 FE

- [ ] 列表「导入」抽屉：来源固定默认项目；计划单选（可按同步文件夹过滤）  
- [ ] 冲突弹窗 SKIP/OVERWRITE + 批量应用到剩余项  
- [ ] 成功提示：未导入关联用例/组/定时/执行与报告  

### 2.2 BE

- [ ] `POST /test-plan/import/from-default-project`  
- [ ] 落库映射（方案 §3.1.1）：  
  - `test_plan` 新 ID、目标 `project_id`、不挂组、状态初始值  
  - `test_plan_document` 复制 content；**图片文件拷贝到目标项目并重写引用**  
  - 不写用例关联、定时、报告等  
- [ ] OVERWRITE：覆盖同名计划约定字段 + document；不动目标已有关联用例  
- [ ] 审计：源计划 ID → 新计划 ID  
- [ ] 可选 `imported_from_hub_plan_id`；**不**写 hub map  

### 2.3 权限

- [ ] 目标 `PROJECT_TEST_PLAN:READ+ADD`；默认项目 `READ`  

---

## 3. 验收标准

- [ ] T1 / T1b / T15 通过  
- [ ] 导入后计划无关联用例、无计划组、无定时任务  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **代码已完成，待环境验收** |
| 备注 | 与现有 `TestPlanBatchOperationService#copyPlan` 差异表写进 MR 说明，避免误复用带关联拷贝 |
