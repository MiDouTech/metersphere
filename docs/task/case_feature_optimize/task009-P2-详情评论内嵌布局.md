# task009 - P2 详情评论内嵌布局

> **阶段**：P2  
> **预估工期**：1–1.5 人日  
> **前置依赖**：无（与 task006 联调有益）  
> **阻塞任务**：无  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **任务状态**：待开始

---

## 1. 任务目标

功能用例详情 **评论** 不再以底部悬浮条存在，改为 **内嵌文档流**，置于 **添加附件模块下方**。

「评论」Tab 保留完整评论管理能力。

---

## 2. 现状分析

| 项 | 说明 |
|----|------|
| 悬浮评论 | `caseDetailDrawer.vue` 底部 `inputComment`，`is-use-bottom` |
| 样式 | `ms-comment/input.vue` → `.commentWrapper { position: absolute; bottom: 0 }` |
| 高度计算 | `content-wrapper` 根据 `commentInputIsActive` 动态 `calc(100vh - …)` |

---

## 3. 目标布局（详情 Tab）

```
前置条件
步骤描述 + 用例级结果按钮
备注
添加附件 + 上一条/下一条
附件列表
────────────────
评论列表（最近 N 条，可选）
评论输入框（文档流，非 fixed）
```

---

## 4. 任务清单

### 4.1 移除悬浮评论

- [ ] `caseDetailDrawer.vue`：在 `activeTab === 'detail'` 时不再渲染底部 `inputComment`  
- [ ] 删除或简化 `commentInputIsActive` 相关高度 `calc`  
- [ ] 父容器改为 `flex` + `min-h-0` + `overflow-auto` 统一滚动  

### 4.2 内嵌评论区

- [ ] 在 `tabDetail.vue` 附件列表（`MsFileList`）下方新增评论区块  
- [ ] 输入：`<inputComment :is-use-bottom="false" />`  
- [ ] 列表：复用 `tabComment/tabCommentIndex.vue` 精简版，或展示最近评论 + 「查看全部」链到评论 Tab  
- [ ] 权限：`FUNCTIONAL_CASE:READ+COMMENT`  

### 4.3 评论 Tab

- [ ] `activeTab === 'comments'` 逻辑保持不变  
- [ ] 内嵌区发表评论后，同步刷新评论 Tab 计数（`commentCount`）  

---

## 5. 涉及文件

| 文件 | 改动 |
|------|------|
| `caseDetailDrawer.vue` | 移除悬浮评论、调整布局 |
| `tabContent/tabDetail.vue` | 内嵌评论区 |
| `ms-comment/input.vue` | 确认非 bottom 模式样式 |
| `tabContent/tabComment/tabCommentIndex.vue` | 可选复用 |

---

## 6. 测试用例

| 场景 | 预期 |
|------|------|
| 详情 Tab 滚动 | 评论随内容滚动，不遮挡附件 |
| 发表评论 | 内嵌输入框可用，发表成功 |
| 评论 Tab | 完整列表与管理正常 |
| 切换 Tab 再回详情 | 评论数据一致 |
| 窄屏/全屏 | 无底部遮挡 |

---

## 7. 验收标准

- [ ] 详情 Tab 评论在附件下方内嵌展示  
- [ ] 无底部悬浮评论条  
- [ ] 评论 Tab 功能不受影响  
- [ ] 与 task006 联调无点击热区回归
