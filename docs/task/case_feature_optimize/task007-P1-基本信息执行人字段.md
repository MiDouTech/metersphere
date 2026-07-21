# task007 - P1 基本信息执行人字段

> **阶段**：P1  
> **预估工期**：0.5 人日  
> **前置依赖**：[task003](task003-P0-执行人数据模型与API.md)、[task004](task004-P1-列表执行人列与批量修改.md)  
> **阻塞任务**：无  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **任务状态**：待开始

---

## 1. 任务目标

用例详情 **基本信息** Tab 增加 **执行人** 字段（只读），取值与列表执行人列一致。

**规则**：展示最后更新用例 **总执行结果** 的用户；批量修改执行人后以库中 `execute_user` 为准。

---

## 2. 现状分析

| 项 | 说明 |
|----|------|
| 组件 | `frontend/src/views/case-management/caseManagementFeature/components/tabContent/basicInfo.vue` |
| 已有字段 | 所属模块、自定义字段、创建人、创建时间、标签 |
| 缺失 | 执行人展示 |

---

## 3. 任务清单

- [ ] 在「创建人」附近增加 `baseItem`：标签「执行人」，值 `detailInfo.executeUserName || '-'`  
- [ ] 确认 `getCaseDetail` 返回 `executeUserName`（task003 后端）  
- [ ] `DetailCase` 类型补充字段  
- [ ] i18n：`caseManagement.featureCase.tableColumnExecutor`（复用列表列文案）  
- [ ] 执行结果变更后 `updateSuccess` 刷新详情，执行人同步更新  

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| `tabContent/basicInfo.vue` | UI 展示 |
| `models/caseManagement/featureCase.ts` | 类型 |
| `locale/zh-CN.ts`、`locale/en-US.ts` | 文案（若未在 task004 添加） |

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| 未执行过 | 执行人显示 `-` 或空 |
| 详情改总执行结果 | 执行人变为当前用户 |
| 列表批量改执行人 | 基本信息与列表一致 |
| 刷新详情 | 执行人正确 |

---

## 6. 验收标准

- [ ] 基本信息 Tab 展示执行人  
- [ ] 与列表执行人列值一致  
- [ ] 只读，不可 inline 编辑
