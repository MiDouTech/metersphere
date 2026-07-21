# task005 - P0 详情上下条导航状态修复

> **阶段**：P0  
> **预估工期**：1 人日  
> **前置依赖**：无  
> **阻塞任务**：task008  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **任务状态**：待开始

---

## 1. 任务目标

修复用例详情 **上一条 / 下一条** 按钮禁用状态错误：

- 从 **第一条** 进入后点「下一条」，离开首条后「上一条」应可点击  
- 从 **最后一条** 进入后点「上一条」，离开末条后「下一条」应可点击  

---

## 2. 根因分析

存在 **两套导航状态**，未同步：

| 位置 | 状态来源 | 是否更新 |
|------|----------|----------|
| 顶部 `MsPrevNextButton` | 内部 `activeDetailIndex` | ✅ 导航时更新 |
| 底部「上一条/下一条」 | `caseDetailDrawer` 的 `canGoPrev/canGoNext` | ❌ 基于初始 `props.detailIndex`，不更新 |

相关代码：

- `frontend/src/components/business/ms-prev-next-button/index.vue`  
- `frontend/src/views/case-management/caseManagementFeature/components/caseDetailDrawer.vue`（`canGoPrev` / `canGoNext`）  
- `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue`（`activeCaseIndex` 仅在打开详情时赋值）

---

## 3. 改造方案

### 3.1 统一导航状态源

- [ ] `MsPrevNextButton` 在 `openPrevDetail` / `openNextDetail` 成功后 `emit('change', { id, index })`  
- [ ] `caseDetailDrawer` 维护 `currentDetailIndex`（ref），监听 `change` 事件更新  
- [ ] `canGoPrev` / `canGoNext` 基于 `currentDetailIndex` + `pagination` 计算（逻辑与 `MsPrevNextButton` 一致）  
- [ ] `caseTable.vue` 同步更新 `activeCaseIndex`、`activeDetailId`（便于列表高亮，可选）

### 3.2 跨页导航

- [ ] 翻页后 `tableData` 刷新，`currentDetailIndex` 与 `activeDetailId` 以组件内部状态为准  
- [ ] 避免仅依赖列表页传入的静态 `detailIndex`  

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| `ms-prev-next-button/index.vue` | emit 导航变更 |
| `ms-detail-drawer/index.vue` | 透传事件（若需要） |
| `caseDetailDrawer.vue` | 统一 canGoPrev/Next |
| `caseTable.vue` | 同步 index/id |
| `tabContent/tabDetail.vue` | 消费更新后的 props |

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| 从第 1 条进入 → 下一条 | 上一条可点 |
| 从最后 1 条进入 → 上一条 | 下一条可点 |
| 中间条双向导航 | 两按钮状态正确 |
| 当前页最后一条 → 下一条（有下一页） | 翻页并加载下一条 |
| 顶部箭头与底部按钮 | 禁用状态一致 |

---

## 6. 验收标准

- [ ] 首条/末条进入场景下，导航后按钮状态正确  
- [ ] 顶部与底部导航按钮状态一致  
- [ ] 跨页导航正常
