# BUG-CASE-001：错误路由 caseReview 返回 404

> **日期**：2026-07-04  
> **关联任务**：全系统测试 / 用例管理  
> **影响范围**：`#/case-management/caseReview`  
> **严重程度**：P3

---

## 1. 问题现象

直接访问 `http://localhost:5173/#/case-management/caseReview` 显示 not found。

**正确路由**：`#/case-management/caseManagementReview`

---

## 2. 根因分析

路由模块 `caseManagement.ts` 仅注册 `caseManagementReview`，历史文档/外链使用 `caseReview` 路径。

---

## 3. 修复方案

**文件**：`frontend/src/router/routes/modules/caseManagement.ts`

```typescript
{
  path: 'caseReview',
  redirect: '/case-management/caseManagementReview',
},
```

---

## 4. 验证

访问 `#/case-management/caseReview` 自动进入评审列表页。

---

## 5. 关联

BUG-DOC-001 文档路径不一致项之一。
