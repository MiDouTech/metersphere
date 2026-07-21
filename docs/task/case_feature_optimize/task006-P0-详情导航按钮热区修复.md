# task006 - P0 详情导航按钮热区修复

> **阶段**：P0  
> **预估工期**：0.5 人日  
> **前置依赖**：[task005](task005-P0-详情上下条导航状态修复.md)（建议同批联调）  
> **阻塞任务**：无  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **任务状态**：待开始

---

## 1. 任务目标

修复用例详情 **上一条 / 下一条** 按钮 **点击热区过大** 问题：鼠标未悬浮在按钮图形上时不应触发点击或误选中。

---

## 2. 可能原因

| 原因 | 位置 |
|------|------|
| 底部评论 `position: absolute` 叠层 | `caseDetailDrawer.vue` + `ms-comment/input.vue` `.commentWrapper` |
| 导航按钮容器过宽（`flex-1` / `w-full`） | `tabDetail.vue` 附件区 `labelRight` 插槽 |
| Tooltip 包裹导致热区扩大 | `ms-prev-next-button/index.vue` |

---

## 3. 任务清单

### 3.1 顶部导航（MsPrevNextButton）

- [ ] 外层容器使用 `inline-flex`，禁止撑满父级  
- [ ] `a-tooltip` 仅包裹 `a-button`，不额外扩大 padding  
- [ ] 按钮 `size="mini"`，无多余 `min-width`  

### 3.2 底部导航（tabDetail）

- [ ] 「上一条/下一条」容器 `inline-flex gap-2`，不占用整行点击区域  
- [ ] 检查 `AddAttachment` 的 `label` 行 `justify-between` 是否导致右侧插槽过宽  

### 3.3 叠层排查

- [ ] DevTools 检查评论悬浮层是否覆盖按钮区域  
- [ ] 与 task009 联调：评论改内嵌后可复测热区  

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| `ms-prev-next-button/index.vue` | 热区样式 |
| `tabContent/tabDetail.vue` | 底部按钮布局 |
| `caseDetailDrawer.vue` | 布局/叠层（必要时） |

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| 鼠标在按钮外 10px | 不触发点击 |
| 鼠标在按钮上 | 正常点击 |
| 按钮 disabled | 不可点击，无幽灵热区 |
| 顶部与底部按钮 | 热区均正常 |

---

## 6. 验收标准

- [ ] 仅按钮可视区域响应点击  
- [ ] 无误触、误选中现象  
- [ ] 与 task005 导航逻辑联调通过
