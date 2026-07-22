# task010 - P0 附件粘贴热区增强

> **阶段**：P0（详情体验二期）  
> **预估工期**：0.5–1 人日  
> **前置依赖**：无（可与 task011 并行）  
> **阻塞任务**：无  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **关联方案**：[MeterSphere-功能用例详情-体验优化方案-2026-07-22](../../summary/MeterSphere-功能用例详情-体验优化方案-2026-07-22.md) §4.1  
> **与旧任务关系**：增强/收口 [task001](task001-P2-附件粘贴上传.md)（组件内已有 paste，本期补热区与详情外包）  
> **任务状态**：进行中（前端已合入核心改动，待联调）

---

## 1. 任务目标

功能用例详情「添加附件」支持稳定粘贴截图：热区外扩约 **200px**，悬停或 focus 后 Ctrl+V 只接收 **file**；**不改** `ms-add-attachment` 默认行为，其它模块（缺陷/API）不受影响。

---

## 2. 现状分析

| 项 | 说明 |
|----|------|
| 组件 | `ms-add-attachment/index.vue` 已有 `@paste` / `onPasteFiles` |
| 问题 | 热区过小；依赖焦点；详情侧体验仍不稳定 |
| 约束 | 仅功能用例详情外包一层；平板依赖 focus |

---

## 3. 任务清单

### 3.1 详情外包热区

- [ ] 在 `tabDetail.vue` 为「添加附件 + 文件列表」外包 `attachment-paste-region`  
- [ ] 相对附件模块可视区域 **向外延伸约 200px**（padding/绝对定位扩大命中区）  
- [ ] `mouseenter` / `mouseleave` 维护 `isHoverPasteZone`；区域 `tabindex="0"` 支持 focus（平板）  

### 3.2 粘贴门控

- [ ] 容器或 document 级 `paste`：仅当悬停或 focus 在热区内处理  
- [ ] **只收 file**（`clipboardData.files` + `items.kind===file`）；文字不当附件  
- [ ] 有 file 时 `preventDefault` 并走现有上传链路（复用 `buildFileItem` / `handleChange`）  
- [ ] 热区内纯文字粘贴：无响应（已接受）  
- [ ] 无文件名时默认 `screenshot-YYYYMMDD-HHmmss.png`  
- [ ] disabled / 无权限时不处理  

### 3.3 隔离与文案

- [ ] 确认缺陷等其它使用方行为未变  
- [ ] i18n 可补「悬停或点选附件区域后粘贴」  

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| `.../tabContent/tabDetail.vue` | 外包热区 + paste 门控 |
| `ms-add-attachment/index.vue` | 尽量不改默认；仅必要时小改钩子 |
| `locale/zh-CN.ts`、`en-US.ts` | 提示文案 |

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| 截图复制后悬停热区（含外扩 200px）Ctrl+V | 上传成功 |
| 悬停热区 + 焦点在富文本 + 粘贴截图 | 进附件 |
| 鼠标在热区外粘贴文字到富文本 | 正文正常 |
| 热区内粘贴纯文字 | 无响应 |
| 缺陷模块附件粘贴 | 行为与改前一致 |

---

## 6. 验收标准

- [ ] 功能用例详情热区外扩约 200px 可粘贴 file  
- [ ] 只收 file；其它模块未扩大热区  
- [ ] 平板可通过 focus 后粘贴  
