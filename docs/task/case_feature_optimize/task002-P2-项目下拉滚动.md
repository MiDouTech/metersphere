# task002 - P2 项目切换下拉支持滚动

> **阶段**：P2  
> **预估工期**：0.5 人日  
> **前置依赖**：无  
> **阻塞任务**：无  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **任务状态**：待开始

---

## 1. 任务目标

顶栏 **项目切换下拉** 在项目数量较多时支持 **纵向滚动**，可正常选择任意项目。

---

## 2. 现状分析

| 项 | 说明 |
|----|------|
| 组件 | `frontend/src/components/pure/navbar/index.vue` |
| 数据源 | `appStore.projectList` |
| 问题 | `a-option` 外层包裹 `a-tooltip`，易导致下拉面板无法正确滚动 |
| 参考 | 全局样式 `.arco-trigger-menu-vertical { max-height: 500px }` 已存在，但未作用于 Select 下拉 |

---

## 3. 任务清单

### 3.1 下拉结构优化

- [ ] 移除 `a-option` 外层 `a-tooltip`（改用 `title` 属性或 option 文本省略 + tooltip）  
- [ ] 为 `a-select` 增加 `popup-max-height`（建议 360px）  
- [ ] 确认 `.arco-select-dropdown-list` 具备 `overflow-y: auto`  

### 3.2 同步组件（如有）

- [ ] 检查 `frontend/src/components/business/ms-project-select/index.vue` 是否存在同样问题并一并修复  

### 3.3 回归

- [ ] 项目搜索（`allow-search`）正常  
- [ ] 「+ 新建项目」header 按钮正常  
- [ ] 选中态、切换路由正常  

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| `frontend/src/components/pure/navbar/index.vue` | Select 下拉滚动 |
| `frontend/src/components/business/ms-project-select/index.vue` | 同上（若适用） |

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| 项目数 ≤ 5 | 下拉正常展示 |
| 项目数 > 10 | 出现滚动条，可滚到底部选项 |
| 长项目名 | 省略显示，hover 可见全名 |
| 搜索过滤 | 过滤后列表可滚动 |

---

## 6. 验收标准

- [ ] 多项目时下拉可滚动选择  
- [ ] 不影响当前项目切换与页面刷新逻辑
