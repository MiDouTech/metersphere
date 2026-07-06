# BUG-DASH-001：工作台首页 orgId 为空时不加载卡片布局

> **日期**：2026-07-04  
> **关联任务**：全系统测试 / 工作台模块  
> **影响范围**：`#/workstation/home`  
> **严重程度**：P1

---

## 1. 问题现象

清除 `localStorage` 中 `currentOrgId` 后访问工作台，URL 变为 `?orgId=`，页面显示「工作台暂无内容，请先加入项目」，不请求 `/dashboard/layout/get/{organizationId}`。

---

## 2. 根因分析

1. **`isLogin()`**：URL 无 `orgId` 时不设置 `appStore.currentOrgId`，即使用户有 `lastOrganizationId`。  
2. **`homePage/index.vue`**：`initDefaultList()` 仅在 `appStore.currentOrgId`  truthy 时加载布局。

---

## 3. 修复方案

| 文件 | 改动 |
|------|------|
| `frontend/src/store/modules/user/index.ts` | 非 forceSet 时：`orgId \|\| res.lastOrganizationId`；`pId \|\| res.lastProjectId` |
| `frontend/src/views/workbench/homePage/index.vue` | `initDefaultList` 回退 `userStore.lastOrganizationId` 并 `setCurrentOrgId` |

---

## 4. 验证步骤

1. 清除 `currentOrgId`（或 localStorage 对应项）  
2. 已登录访问 `#/workstation/home`  
3. 应加载 dashboard 布局并展示统计卡片

---

## 6. 参考

- 关联测试点：DASH-F01、DASH-F03
