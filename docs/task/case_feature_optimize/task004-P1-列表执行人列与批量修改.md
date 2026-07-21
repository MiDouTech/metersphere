# task004 - P1 列表执行人列与批量修改

> **阶段**：P1  
> **预估工期**：1.5–2 人日  
> **前置依赖**：[task003](task003-P0-执行人数据模型与API.md)  
> **阻塞任务**：无  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **任务状态**：待开始

---

## 1. 任务目标

1. 功能用例列表增加 **执行人** 列  
2. 批量操作增加 **批量修改执行人**

---

## 2. 现状分析

| 项 | 说明 |
|----|------|
| 列表 | `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue` |
| 已有批量 | 批量编辑、批量修改执行结果、移动、复制 |
| 参考实现 | `frontend/src/views/test-plan/testPlan/components/batchUpdateExecutorModal.vue` |

---

## 3. 任务清单

### 3.1 列表列

- [ ] 在「执行结果」列附近增加「执行人」列（`executeUserName`）  
- [ ] 空值展示 `-`  
- [ ] 列宽约 120px；纳入列设置（`showDrag: true`）  
- [ ] 可选：高级筛选支持按执行人过滤（远程成员，参考「更新人」列）  

### 3.2 批量修改执行人

- [ ] 新建 `batchUpdateExecutorModal.vue`（或复用 test-plan 组件并参数化 API）  
- [ ] `tableBatchActions.baseAction` 增加「批量修改执行人」  
- [ ] 权限：`FUNCTIONAL_CASE:READ+UPDATE`  
- [ ] 成员下拉：项目成员接口（如 `project/member/comment/user-option` 或 test-plan 同类接口）  
- [ ] 提交调用 `batchUpdateExecutor` API  
- [ ] 成功后 `initData()` 刷新列表  

### 3.3 i18n

- [ ] `caseManagement.featureCase.tableColumnExecutor`  
- [ ] `caseManagement.featureCase.batchChangeExecutor`  
- [ ] `caseManagement.featureCase.requestExecutorRequired`  

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| `caseTable.vue` | 列配置、批量操作入口 |
| `batchUpdateExecutorModal.vue` | 新建 |
| `locale/zh-CN.ts`、`locale/en-US.ts` | 文案 |
| `api/modules/case-management/featureCase.ts` | 接口（task003 已加） |

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| 列表展示 | 执行人列正确显示 |
| 批量选 3 条改执行人 | 3 条更新，列表刷新 |
| 全选批量改 | 条件范围内全部更新 |
| 无 UPDATE 权限 | 不显示批量入口 |
| 与详情基本信息 | 执行人一致 |

---

## 6. 验收标准

- [ ] 列表有执行人列，数据与后端一致  
- [ ] 支持批量修改执行人  
- [ ] 与 task007 基本信息展示值一致
