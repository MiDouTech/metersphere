# task001 - P2 附件粘贴上传

> **阶段**：P2  
> **预估工期**：0.5–1 人日  
> **前置依赖**：无  
> **阻塞任务**：无  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **任务状态**：待开始

---

## 1. 任务目标

功能用例详情「添加附件」区域支持 **Ctrl+V / 粘贴** 上传文件，行为与点击上传、拖拽上传一致。

---

## 2. 现状分析

| 项 | 说明 |
|----|------|
| 组件 | `frontend/src/components/business/ms-add-attachment/index.vue` |
| 已有能力 | 点击上传、拖拽上传（`attach-drop-zone` + dragenter/drop） |
| 缺失 | 无 `paste` 事件处理 |

---

## 3. 任务清单

### 3.1 粘贴事件

- [ ] 在 `.attach-drop-zone` 上监听 `@paste="onPasteFiles"`  
- [ ] 读取 `event.clipboardData.files` 与 `clipboardData.items`（图片）  
- [ ] 复用 `buildFileItem` + `handleChange`，与拖拽逻辑统一  
- [ ] `props.disabled === true` 时不处理  

### 3.2 校验与提示

- [ ] 单文件大小校验（`appStore.getFileMaxSize`）  
- [ ] 更新 i18n：`caseManagement.featureCase.dragUploadTip` 旁增加「支持粘贴上传」  

### 3.3 可选增强

- [ ] 为附件区设置 `tabindex="0"`，聚焦后可直接粘贴（非本期必做）

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| `frontend/src/components/business/ms-add-attachment/index.vue` | 粘贴逻辑 |
| `frontend/src/views/case-management/caseManagementFeature/locale/zh-CN.ts` | 文案 |
| `frontend/src/views/case-management/caseManagementFeature/locale/en-US.ts` | 文案 |

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| 详情 Tab 附件区粘贴单文件 | 文件加入列表并上传 |
| 粘贴图片 | 正常识别为文件 |
| 粘贴超大文件 | 提示超限，不上传 |
| disabled 状态 | 不响应粘贴 |
| 与拖拽/点击混用 | 行为一致 |

---

## 6. 验收标准

- [ ] 用例详情附件区支持粘贴上传  
- [ ] 大小限制与现有拖拽一致  
- [ ] 不影响其他使用 `ms-add-attachment` 的模块（缺陷、API 等）
