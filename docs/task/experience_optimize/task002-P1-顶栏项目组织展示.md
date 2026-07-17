# task002 - P1 顶栏项目下拉加长与组织展示

> **阶段**：P1  
> **预估工期**：0.5–1 人日  
> **前置依赖**：无  
> **阻塞任务**：无  
> **关联方案**：[体验优化产品方案](../../summary/MeterSphere-体验优化-产品方案-2026-07-17.md) §3.3  
> **任务状态**：待开始

---

## 1. 任务目标

顶栏项目选择展示完整（或约 30 字）项目名；在 `right-side` 内项目选择右侧展示当前组织名称（只读）。采用方案 A：不改 TopMenu 居中栅格。

---

## 2. 改动锚点

| 项 | 路径 |
|----|------|
| 顶栏 | `frontend/src/components/pure/navbar/index.vue` |
| 项目列表 | `appStore.projectList` / `currentProjectId` |
| 组织 | `appStore.currentOrgId`、`appStore.orgList` |
| i18n | `settings.navbar.*` 或新增 `navbar.currentOrg` |

---

## 3. 任务清单

### 3.1 项目下拉

1. 去掉隐藏选中文案的样式（如隐藏 `.arco-select-view-value`）。  
2. 宽度：`min-width: 200px; max-width: 420px`（约 30 汉字 @14px）；超长省略 + tooltip 全文。  
3. 切换项目逻辑保持现网（`changeProject` 等不改行为）。  

### 3.2 组织展示

1. 在项目下拉右侧增加只读文本：`当前组织：{name}`。  
2. `name` = `orgList` 中 `id === currentOrgId` 的名称；找不到则 `-`。  
3. 超长省略 + tooltip；小屏可将组织缩为短文案或仅 tooltip（可选）。  
4. **本期不提供组织切换**（仍走现有组织入口）。  

### 3.3 布局

```
Logo | TopMenu | …… | [项目名称下拉] [当前组织：xxx] | 通知/任务/帮助/…
```

项目选择仍为 `right-side` 最左侧控件，可视主体变为项目名（不再是约 50px 箭头）。

### 3.4 i18n

| key | zh-CN | en-US |
|-----|-------|-------|
| `navbar.currentOrg` | 当前组织 | Current org |

---

## 4. 测试用例

| 场景 | 预期 |
|------|------|
| 短项目名 | 完整显示 |
| ≥30 字项目名 | 主体可见或省略，tooltip 全文 |
| 切换项目 | 与现网一致，组织名随上下文正确 |
| 无组织/仅一组织 | 展示正确名称或 `-`，无报错 |

---

## 5. 验收标准

- [ ] 不依赖 hover 即可识别当前项目（约 30 字目标）  
- [ ] 项目下拉右侧可见当前组织名  
- [ ] 切换项目行为与现网一致  
- [ ] 未改动 Logo / TopMenu 居中主布局  

---

## 6. 交付物

- `navbar/index.vue` + locale 改动  
- 本文件验收勾选更新  
