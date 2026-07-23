# task002 - P0 项目创建与成员 API

> **阶段**：P0  
> **预估工期**：1 天  
> **前置依赖**：task001  
> **阻塞任务**：task007、task010  
> **关联方案**：§6、§7.1、§9

---

## 1. 任务目标

提供对话创建项目、追加成员、查询项目能力；创建/加成员必须校验组织归属。

---

## 2. 任务清单

- [x] `POST /api/agent/v1/project/create`（`PROJECT_WRITE`）+ `assertOrgAccessible`  
- [x] `POST /api/agent/v1/project/members/add` + 组织校验  
- [x] `GET /api/agent/v1/project/{id}`（Scope=`PROJECT_WRITE`）  
- [x] 审计：`PROJECT_CREATE`、`PROJECT_ADD_MEMBERS`  
- [ ] 默认 `userRoleIds` 权限（运行时确认，task010）  

**核对路径**：`AgentProjectController`、`AgentProjectService`。

---

## 3. 验收标准

- [x] 代码路径完整（静态核对 2026-07-23）  
- [ ] 组织内创建 / 非组织拒绝 / 成员可见 / 审计可查（运行时）  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **代码已核对**；运行时待 task010 |
