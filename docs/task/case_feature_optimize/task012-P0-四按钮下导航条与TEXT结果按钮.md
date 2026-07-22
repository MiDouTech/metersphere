# task012 - P0 四按钮下导航条与 TEXT 结果按钮

> **阶段**：P0（详情体验二期）  
> **预估工期**：0.5–1 人日  
> **前置依赖**：建议先完成 [task011](task011-P0-前置备注行式布局与位置.md)  
> **阻塞任务**：[task013](task013-P0-自动下一条开关与附件门禁.md) 依赖本任务导航条结构  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **关联方案**：[体验优化方案 v1.3.1](../../summary/MeterSphere-功能用例详情-体验优化方案-2026-07-22.md) §4.3  
> **任务状态**：进行中（前端已合入核心改动，待联调）

---

## 1. 任务目标

1. STEP / TEXT **均展示**用例级四按钮（通过/失败/阻塞/跳过），统一走 `handleSetCaseResult`。  
2. 在四按钮**下方**新建导航 `div`：右侧上一条/下一条；左侧留给 task013 开关。  
3. 从附件 `labelRight` **移除**上下条；详情**顶部**上下条保留，顶底**双入口并存**。  
4. 底栏整段随 `showCaseNav` 同显隐。

---

## 2. 现状分析

| 项 | 说明 |
|----|------|
| STEP | `AddStep` 含四按钮 |
| TEXT | 无对等四按钮 |
| 底栏导航 | 挂在 `AddAttachment #labelRight` |
| 顶栏 | `MsPrevNextButton` 仍在抽屉头 |

---

## 3. 任务清单

### 3.1 TEXT 四按钮

- [ ] 抽取或复用结果按钮条，TEXT 模式同样展示四按钮  
- [ ] 事件绑定 `handleSetCaseResult`（与 STEP 一致）  

### 3.2 导航条位置

- [ ] 紧挨四按钮下方新建 `div.case-nav-bar`  
- [ ] 右：上一条 / 下一条（`canGoPrev` / `canGoNext`）  
- [ ] 左：预留开关槽位（task013 填入）  
- [ ] 从附件 `labelRight` 删除原上下条  
- [ ] 顶部 PrevNext **保留**；状态与底栏一致（依赖既有 index 同步）  
- [ ] `v-if="showCaseNav"` 控制整条底栏  

### 3.3 热区

- [ ] 外层 `inline-flex gap-2`，仅按钮可点  

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| `tabContent/tabDetail.vue` | 布局、TEXT 按钮、导航条 |
| `tabContent/addStep.vue` 或新建 `caseResultActions.vue` | 四按钮复用 |
| `caseDetailDrawer.vue` | props 透传确认 |
| 测试计划内嵌详情（若共用 tabDetail） | 同步验证 |

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| STEP / TEXT 点四按钮 | 均可保存结果 |
| 底栏位置 | 在四按钮下、附件上 |
| 附件标题旁 | 无上下条 |
| 顶部与底部点下一条 | 均可用且状态一致 |
| `showCaseNav=false` | 底栏整条隐藏 |

---

## 6. 验收标准

- [ ] TEXT 补齐四按钮  
- [ ] 底栏在四按钮下；顶底双入口并存  
- [ ] 附件区不再挂导航  
