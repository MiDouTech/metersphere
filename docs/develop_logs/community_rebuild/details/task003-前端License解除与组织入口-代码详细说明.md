# task003 代码详细说明 — 前端 License 解除与组织入口

> **任务文档**：[../../task/task003-P0-前端License解除与组织入口.md](../../../task/task003-P0-前端License解除与组织入口.md)  
> **开发摘要**：[../2026-07-04-task003-前端License解除与组织入口-开发摘要.md](../2026-07-04-task003-前端License解除与组织入口-开发摘要.md)  
> **完成日期**：2026-07-04

---

## 1. 架构：统一 License 开关

```
┌─────────────────────────────────────────────────────────┐
│  VITE_MS_UNLIMITED (build-time env)                      │
└──────────────────────────┬──────────────────────────────┘
                           ▼
              licenseStore.isUnlimited (getter)
                           ▼
              licenseStore.hasLicense() → true
                           ▼
    ┌──────────────────────┼──────────────────────┐
    ▼                      ▼                      ▼
 v-xpack 指令        MsTrialAlert           资源池 disabled
 ms-menu 组织切换     enterOrganization      authTable 权限项
```

未设置 `VITE_MS_UNLIMITED` 时，`hasLicense()` 仍仅依赖 `licenseInfo.status === 'valid'`，与原版社区版一致。

---

## 2. 环境变量

### 2.1 `frontend/.env.development.local.example`

```env
VITE_MS_UNLIMITED=true
```

开发者复制为 `frontend/.env.development.local`（已在根 `.gitignore`）。  
Vite 加载顺序见 `frontend/config/vite.config.dev.ts`：`.env.development.local` 优先于 `.env.development`。

### 2.2 `frontend/.env.development`

已**移除** `VITE_MS_UNLIMITED`，保证无 local 文件时行为不变。

---

## 3. `license.ts`（Pinia store）

**路径**：`frontend/src/store/modules/setting/license.ts`

| 成员 | 类型 | 说明 |
|------|------|------|
| `isUnlimited` | getter | `import.meta.env.VITE_MS_UNLIMITED === 'true'` |
| `hasLicense()` | action | `isUnlimited` 为 true 时直接返回 true；否则检查 `licenseInfo?.status === 'valid'` |
| `getValidateLicense()` | action | 调用 `GET /license/validate`；无 status 时不覆盖 store（兼容 Community 后端） |

**设计要点**：业务代码统一调用 `hasLicense()`，避免散落 `import.meta.env` 判断。

---

## 4. `validateLicense/index.ts`（v-xpack 指令）

**路径**：`frontend/src/directive/validateLicense/index.ts`

| 行为 | 说明 |
|------|------|
| `isUnlimited` | 直接 return，**不** `removeChild` |
| 否则 | `hasLicense()` 为 false 时从 DOM 移除节点 |

与 store 配合：unlimited 下所有 `v-xpack` 元素保留。

---

## 5. 组织入口

### 5.1 `organizationAndProject/index.vue`

- 创建组织按钮：仅 `v-permission="['SYSTEM_ORGANIZATION_PROJECT:READ+ADD']"`，**无** `hasLicense()` / `MsTrialAlert`
- 前期已移除社区版「仅支持 1 个组织」试用条

### 5.2 `systemOrganization.vue`

- 「进入组织」：`v-if="record.switchAndEnter"` + 权限，**无** License 判断

### 5.3 `views/setting/utils.ts`

- `enterOrganization` / `enterProject`：直接 `switchUserOrg` + `userStore.isLogin()`，**无** `!hasLicense()` Toast

### 5.4 `ms-menu/index.vue`（关键）

**问题**：个人中心「切换组织」仅在 `packageType === 'enterprise'` 且 `hasLicense()` 时挂载。

**修复**：

```typescript
const canSwitchOrg = computed(
  () => xPack.value && (appStore.getPackageType === 'enterprise' || licenseStore.isUnlimited)
);
```

- `watch(canSwitchOrg)`：为 true 时 `personalMenus` 加入 `switchOrgMenus` 并 `getOrgList()`
- `watchEffect`：打开切换弹层时，若 `canSwitchOrg` 则拉取 `GET /system/organization/switch-option`

---

## 6. 应用启动与顶栏

### 6.1 `App.vue`

```typescript
if (appStore.getPackageType === 'enterprise' || licenseStore.isUnlimited) {
  await licenseStore.getValidateLicense();
}
if (licenseStore.hasLicense()) {
  appStore.initPageConfig();
}
```

unlimited 社区版也会拉 License 并初始化页面配置（logo 等）。

### 6.2 `ms-top-menu/index.vue`

组织切换后：unlimited 时同样调用 `getValidateLicense()` 刷新 store。

---

## 7. 资源池与试用提示

| 组件 | 机制 |
|------|------|
| `resourcePool/index.vue` | 创建按钮 `v-xpack`；启用开关 `disabled="!licenseStore.hasLicense()"` |
| `resourcePool/detail.vue` | 并发数字段 `disabled: !licenseStore.hasLicense()` |
| `ms-trial-alert/index.vue` | `v-if="!licenseStore.hasLicense()"` |

unlimited 下 `hasLicense()` 为 true → 按钮可见、字段可编辑、提示条隐藏。**无需**逐文件删 disabled。

---

## 8. API 路径（与 task002 对齐）

| 用途 | 前端定义 | 后端 |
|------|----------|------|
| 创建组织 | `postAddOrgUrl` → `/system/organization/add` | `SystemOrganizationController.add` |
| 切换组织 | `SwitchOrgUrl` → `/system/organization/switch` | `switchOrganization` |
| 切换选项 | `OrgOptionsUrl` → `/system/organization/switch-option` | `getSwitchOption` |

定义位置：

- `frontend/src/api/requrls/setting/organizationAndProject.ts`
- `frontend/src/api/requrls/system.ts`

---

## 9. 生产部署

```bash
VITE_MS_UNLIMITED=true npm run build
```

后端需 task001 Community Xpack 已部署，否则仅 UI 放开、配额仍可能被后端拒绝。

---

## 10. 相关文档

| 文档 | 路径 |
|------|------|
| task002 后端 API | [task002-组织创建与切换API-代码详细说明.md](task002-组织创建与切换API-代码详细说明.md) |
| BUG001 用户创建 | [../buglist/2026-06-26-BUG001-创建用户成功但列表不显示.md](../../buglist/2026-06-26-BUG001-创建用户成功但列表不显示.md) |
