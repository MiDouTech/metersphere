# task001 - P0 操作列新增「详情」按钮

> **阶段**：P0  
> **预估工期**：2–3 人日  
> **前置依赖**：无  
> **阻塞任务**：无（可与 task002 并行）  
> **关联方案**：[体验优化产品方案](../../summary/MeterSphere-体验优化-产品方案-2026-07-17.md) §3.1  
> **任务状态**：待开始

---

## 1. 任务目标

在存在「操作列 + 详情入口」的业务列表中，于「编辑」左侧增加「详情」，点击行为与点击 ID/名称进入详情一致；权限对齐查看（通常模块 `READ`），不要求 `UPDATE`。

---

## 2. 覆盖清单（本期必做）

| 模块 | 建议改动文件 | 详情行为对齐 |
|------|--------------|--------------|
| 功能用例 | `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue` | `showCaseDetail` |
| 用例评审 | `frontend/src/views/case-management/caseReview/**/reviewTable.vue`（按实际路径） | 点 ID → 评审详情 |
| 测试计划 | `frontend/src/views/test-plan/testPlan/components/planTable.vue` | `openDetail` |
| 缺陷 | `frontend/src/views/bug-management/index.vue`（及操作列所在子组件） | 点 ID → 缺陷抽屉 |
| 接口定义 | 对应 `apiTable.vue` | 点 ID/名称进详情 |
| 接口用例 | 对应 `caseTable.vue` | 同上 |
| 场景 | 对应 `scenarioTable.vue` | 同上 |
| 计划报告 / 接口报告列表 | `reportList.vue` 等（仅当操作列有「编辑」时） | 同现有打开详情 |
| 项目管理 | `project-management/**` 带操作列+详情的表格 | 同现有详情 |
| 组织 / 系统设置 | `setting/organization/**`、`setting/system/**` 带操作列+详情的表格 | 同现有详情抽屉/页 |

**排除**：无详情入口的表格；仅「更多」且无「编辑」的操作列；无资源详情语义的操作（如仅启用/禁用）。

> **✅ 产品确认**：覆盖范围**包含**系统设置 / 组织 / 项目管理。

检索辅助：

```text
rg -n "common.edit|#operation|slotName: 'operation'" frontend/src/views --glob "*Table*.vue"
```

---

## 3. 任务清单

### 3.1 i18n

- 复用已有 `common.detail`（zh=`详情`，en 建议规范为 `Detail`）。
- 各模块若已有 `*.detail` 可继续用，避免重复 key。

### 3.2 交互规则（统一）

1. 顺序：**详情 | 编辑 | …**（其后保持原按钮与「更多」）。  
2. 有 `READ`、无 `UPDATE`：显示「详情」，隐藏或禁用「编辑」（按该模块现有编辑权限指令）。  
3. 无 `READ`：按现有逻辑隐藏整列或不可达详情。  
4. 操作列 `width` 上调约 40–60px，避免挤出。  

### 3.3 实现要点

- 详情按钮调用与 ID/名称列相同的方法（禁止新开一套路由逻辑）。  
- 使用 `MsButton` + 现有 `v-permission`；文案 `t('common.detail')`。  
- 计划组 / 特殊行：遵循该表现有「能否进详情」规则，不扩大权限。  

---

## 4. 测试用例

| 场景 | 预期 |
|------|------|
| 有 READ+UPDATE | 「详情」在「编辑」左侧；点详情与点 ID 同目标 |
| 仅 READ | 可见「详情」；「编辑」不可见或不可点（与模块一致） |
| 无 READ | 不可通过操作列进详情 |
| 窄屏 / 多按钮行 | 操作列不严重换行或可接受横向滚动 |

---

## 5. 验收标准

- [ ] 覆盖清单模块均已加「详情」且位于「编辑」左侧  
- [ ] 点击「详情」与点击 ID 进入同一页面/抽屉、同一权限校验  
- [ ] 无编辑权限用户在具备 READ 时可点「详情」  
- [ ] 中英繁文案可用（至少中英）  

---

## 6. 交付物

- 前端改动 PR（按模块可拆多个 commit）  
- 本文件验收勾选更新  

---

*实现注意：最小化 diff；不顺手重构表格公共组件，除非抽出共用「详情」按钮能减少重复且评审同意。*
