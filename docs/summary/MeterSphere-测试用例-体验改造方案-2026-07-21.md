# MeterSphere 测试用例模块体验改造方案

> **文档类型**：产品/技术改造方案  
> **模块**：功能用例（列表、详情、附件、全局顶栏）  
> **编写日期**：2026-07-21  
> **任务拆解**：[docs/task/case_feature_optimize/task000-实施总览与依赖关系.md](../task/case_feature_optimize/task000-实施总览与依赖关系.md)  
> **标注**：【AI生成】已按代码现状编写；**一期 9 项多数已落地**（执行人/导航/粘贴/自动下一条/评论内嵌等），详见仓库实现与 task000。  
> **2026-07-24**：导入入口与勾选体验见增量方案并已实施。

---

## 1. 背景与目标

基于测试用例模块当前使用反馈与代码现状，需完成 9 项改造，提升执行效率与交互体验：

| # | 需求摘要 | 优先级 |
|---|----------|--------|
| 1 | 附件可粘贴上传 | P2 |
| 2 | 项目切换下拉支持滚动 | P2 |
| 3 | 列表批量修改执行人 | P1 |
| 4 | 列表增加执行人列 | P1 |
| 5 | 详情上一条/下一条状态修复 | P0 |
| 6 | 详情导航按钮热区修复 | P0 |
| 7 | 基本信息增加执行人 | P1 |
| 8 | 更新用例总结果后自动下一条 | P1 |
| 9 | 评论内嵌至附件下方 | P2 |

**预估总工期**：7–10 人日。

---

## 2. 现状问题摘要

### 2.1 附件上传

`ms-add-attachment` 已支持点击与拖拽，未处理 `paste` 事件。

### 2.2 项目下拉

`navbar/index.vue` 中 `a-option` 外包 `a-tooltip`，多项目时下拉无法正确滚动。

### 2.3 执行人

`functional_case` 表无 `execute_user` 字段；列表无执行人列；无批量改执行人；基本信息无展示。

### 2.4 详情导航

- **状态 bug**：`MsPrevNextButton` 内部维护 `activeDetailIndex`，底部按钮的 `canGoPrev/canGoNext` 仍用打开时的静态 `detailIndex`  
- **热区 bug**：底部悬浮评论叠层 + 按钮容器过宽，导致误触

### 2.5 执行效率

用例级结果按钮保存后停留当前用例，未自动下一条。

### 2.6 评论布局

`caseDetailDrawer` 使用 `inputComment` + `is-use-bottom` 绝对定位，遮挡内容且与内嵌式体验不符。

---

## 3. 技术方案

### 3.1 执行人数据模型

```sql
ALTER TABLE functional_case
  ADD COLUMN execute_user VARCHAR(50) DEFAULT NULL COMMENT '执行人用户ID',
  ADD INDEX idx_execute_user (execute_user);
```

**写入规则**：

- 用例级 `lastExecuteResult` 变更 → `execute_user = 当前用户`
- 批量修改执行人 → 手动指定 `userId`
- 展示：`executeUserName`（join user）

**接口**：

- 列表/详情返回执行人
- `POST /functional/case/batch/update/executor`

### 3.2 导航状态统一

`MsPrevNextButton` 导航后 `emit('change', { id, index })`，`caseDetailDrawer` 维护 `currentDetailIndex`，顶部与底部按钮共用同一 computed。

### 3.3 自动下一条

`tabDetail.handleSetCaseResult` → `persistCase` 成功 → 若 `canGoNext` 则 `emit('nextCase')`。

### 3.4 评论内嵌

详情 Tab 在附件列表下渲染评论输入（`is-use-bottom=false`）；移除 drawer 底部悬浮评论；评论 Tab 保留完整能力。

---

## 4. 实施顺序

```
task005 → task006 → task003 → task004 → task007 → task008 → task001 → task002 → task009
```

| 里程碑 | 包含任务 | 验收要点 |
|--------|----------|----------|
| M1 执行人闭环 | task003、004、007 | 列表列、批量改、基本信息一致 |
| M2 执行效率 | task005、006、008 | 导航正确、自动下一条 |
| M3 体验优化 | task001、002、009 | 粘贴、下拉滚动、评论内嵌 |

---

## 5. 风险与待确认

| 项 | 说明 |
|----|------|
| 执行人语义 | 自动写入 vs 批量指定并存；结果变更会覆盖手动指定 |
| 历史数据 | `execute_user` 为空展示 `-` |
| 评论内嵌范围 | 仅详情 Tab；评论 Tab 不变 |
| 跨模块影响 | `ms-add-attachment`、navbar 为公共组件，需回归 |

---

## 6. 任务文档索引

详见 [case_feature_optimize/task000](../task/case_feature_optimize/task000-实施总览与依赖关系.md)。
