# task015 - P1 STEP 与 TEXT 双向同步

> **阶段**：P1（详情体验二期）  
> **预估工期**：1–1.5 人日  
> **前置依赖**：拆分算法与 [task014](task014-P1-导入智能拆分与模板批注.md) 共用（可先抽公共 util 再并行）  
> **阻塞任务**：无  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **关联方案**：[体验优化方案 v1.3.1](../../summary/MeterSphere-功能用例详情-体验优化方案-2026-07-22.md) §1.5、§4.5  
> **任务状态**：进行中（前端切换/保存同步已接，待联调）

---

## 1. 任务目标

详情内 STEP 与 TEXT **数据并存、双向同步**：TEXT 为 STEP 全文展示；改 STEP → 同步 TEXT；改 TEXT → 按同一拆分规则同步 STEP。`caseEditType` 仅表示当前 UI，切换不丢另一侧数据。

---

## 2. 现状分析

| 项 | 说明 |
|----|------|
| UI | `StepDescription` 下拉切换 STEP/TEXT，`v-if` 互斥展示 |
| 存储 | blob 可同时有 steps 与 text 字段，但编辑路径常只维护当前模式 |
| 风险 | 切换类型后另一侧过期或被清空 |

---

## 3. 任务清单

### 3.1 同步函数

- [ ] `stepsToText(steps) → { textDescription, expectedResult }`（全文 + 换行）  
- [ ] `textToSteps(textDescription, expectedResult) → steps`（复用 task014 拆分规则，前后端规则一致：前端可调共享逻辑或在保存前同步）  
- [ ] 明确触发点：步骤变更、TEXT 失焦/保存、切换 `caseEditType` 前、`persistCase` 前  

### 3.2 详情集成

- [ ] `tabDetail` / `AddStep`：编辑 STEP 后刷新 TEXT 字段  
- [ ] 编辑 TEXT 后重拆 STEP  
- [ ] 切换 UI 类型不丢数据、不误清空  
- [ ] 与自动保存、内容编辑统一态兼容  

### 3.3 回归

- [ ] 导出再导入、计划内嵌详情若共用组件一并验证  

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| `tabContent/tabDetail.vue` | 同步触发 |
| `tabContent/addStep.vue` | 步骤变更回调 |
| 新建 `utils/caseStepSync.ts`（或前后端各一） | 同步/拆分 |
| 后端若保存时校验 | 可选双写兜底 |

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| STEP 增删改一行后切 TEXT | TEXT 反映最新全文 |
| TEXT 改编号段落后切 STEP | 步骤表按规则重拆 |
| 反复切换 STEP↔TEXT | 数据不丢、不空 |
| 保存后再打开 | 两侧一致 |

---

## 6. 验收标准

- [ ] 双向同步符合方案口径  
- [ ] 切换 UI 仅改展示，不丢数据  
- [ ] 与 task014 拆分规则一致  

---

## 7. 人工审核点

- [ ] 同步时机是否导致输入过程中光标跳动/频繁重拆（建议防抖或保存时同步）  
