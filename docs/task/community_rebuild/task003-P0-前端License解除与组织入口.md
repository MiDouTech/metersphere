# task003 - P0 前端 License 解除与组织入口

> **阶段**：P0  
> **预估工期**：2 天  
> **前置依赖**：[task001](task001-P0-社区版Xpack与License实现.md)、[task002](task002-P0-组织创建与切换API.md)  
> **阻塞任务**：task009  
> **关联文档**：[community-unlock-and-org-structure.md](../../summary/community-unlock-and-org-structure.md) §3.3、§3.4

---

## 1. 任务目标

通过环境变量 + `licenseStore` 统一开关，解除前端 License 拦截，恢复**创建组织、进入组织、切换组织、资源池**等入口。

---

## 2. 任务清单

### 2.1 环境变量配置

**新建示例文件**：`frontend/.env.development.local.example`

```env
# 社区版自研：解除 License 前端拦截（需配合后端 CommunityXpack 实现）
VITE_MS_UNLIMITED=true
```

**修改**：开发者复制为 `.env.development.local`（已在 `.gitignore` 中）

### 2.2 licenseStore 改造

**文件**：`frontend/src/store/modules/setting/license.ts`

```typescript
hasLicense() {
  if (import.meta.env.VITE_MS_UNLIMITED === 'true') return true;
  return this.licenseInfo?.status === 'valid';
}
```

可选：新增 `isUnlimited` getter 供其他模块引用。

### 2.3 组织入口放开

| 文件 | 改动 |
|------|------|
| `views/setting/system/organizationAndProject/index.vue` | 创建组织按钮不再依赖 `hasLicense()` |
| `.../components/systemOrganization.vue` | 「进入组织」始终可见（或有权限即可见） |
| `views/setting/utils.ts` | 移除 `enterOrganization` / `enterProject` 中 `!hasLicense()` 拦截 |
| `components/business/ms-menu/index.vue` | 组织切换菜单：社区版 + unlimited 时也可用 |

### 2.4 v-xpack 指令

**文件**：`frontend/src/directive/validateLicense/index.ts`

- `VITE_MS_UNLIMITED=true` 时不移除 DOM  
- 或：`hasLicense()` 已覆盖则无需额外改动（优先统一走 store）

### 2.5 资源池页面

| 文件 | 改动 |
|------|------|
| `views/setting/system/resourcePool/index.vue` | 添加按钮、多资源池入口放开 |
| `views/setting/system/resourcePool/detail.vue` | 并发数等字段 editable |

### 2.6 可选：试用提示条

**文件**：`components/business/ms-trial-alert/index.vue`

- `VITE_MS_UNLIMITED=true` 时隐藏黄色社区版提示  

### 2.7 组织切换 API 联调

确认以下 URL 与 task002 后端一致：

| 用途 | 路径 |
|------|------|
| 创建组织 | `POST /system/organization/add` |
| 切换组织 | `POST /system/organization/switch` |
| 切换选项 | `GET /system/organization/switch-option` |

**检查文件**：

- `frontend/src/api/requrls/setting/organizationAndProject.ts`  
- `frontend/src/api/requrls/system.ts`（如有 switch 定义）

---

## 3. 部署说明（可选）

生产环境可通过构建参数注入：

```bash
VITE_MS_UNLIMITED=true npm run build
```

后端可选：`MS_PACKAGE_TYPE=enterprise`（影响部分菜单逻辑，需与 License 实现配合验证）。

---

## 4. 测试用例

| 场景 | 预期 |
|------|------|
| 无 `.env.development.local` | 行为与原版社区版一致 |
| `VITE_MS_UNLIMITED=true` | 显示创建组织按钮 |
| 创建第 2 个组织 | Modal 提交成功，列表刷新 |
| 进入组织 | 跳转到组织设置，无 toast 拦截 |
| 个人中心切换组织 | 下拉可选，切换后上下文更新 |
| 新建第 2 个资源池 | 按钮可点，表单无 disabled |
| v-xpack 标记的按钮 | 正常显示 |

---

## 5. 验收标准

- [x] `VITE_MS_UNLIMITED=true` 下全流程：创建组织 → 进入组织 → 切换组织  
- [x] 资源池多实例 UI 可用（后端 task001 已解除校验）  
- [x] 未设置 unlimited 时，不影响原有社区版行为（`VITE_MS_UNLIMITED` 仅配置在 `.env.development.local.example`）  
- [x] `.env.development.local.example` 已提交，真实 local 文件未提交  

---

## 6. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-04 |
| 完成日期 | 2026-07-04 |

### 6.1 变更文件清单

| 文件 | 说明 |
|------|------|
| `frontend/.env.development.local.example` | 示例环境变量 `VITE_MS_UNLIMITED=true` |
| `frontend/.env.development` | 移除 unlimited 默认值，保持原版社区行为 |
| `frontend/src/store/modules/setting/license.ts` | `isUnlimited` getter + `hasLicense()` 统一开关 |
| `frontend/src/directive/validateLicense/index.ts` | unlimited 时不移除 v-xpack 节点 |
| `frontend/src/App.vue` | unlimited 时拉取 License 并初始化页面配置 |
| `frontend/src/components/business/ms-menu/index.vue` | 社区版 + unlimited 时显示组织切换菜单 |
| `frontend/src/components/business/ms-top-menu/index.vue` | unlimited 时切换组织后刷新 License |
| `views/setting/system/organizationAndProject/index.vue` | 创建组织按钮不依赖 License（已完成） |
| `views/setting/system/organizationAndProject/components/systemOrganization.vue` | 「进入组织」不依赖 License（已完成） |
| `views/setting/utils.ts` | 移除 enterOrganization/enterProject License 拦截（已完成） |

### 6.2 使用说明

```powershell
# 开发环境启用 unlimited
copy frontend\.env.development.local.example frontend\.env.development.local
# 重启 Vite dev server
```
