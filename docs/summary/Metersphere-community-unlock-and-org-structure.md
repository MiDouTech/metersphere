# MeterSphere 社区版解除限制 & 组织架构方案摘要

> 本文档汇总 Cursor 会话中的结论，供 Cursor / JetBrains IDEA AI 通过 `@docs/community-unlock-and-org-structure.md` 引用，保持上下文一致。  
> 仓库路径：`C:\SoftWare\JetBrains\metersphere`  
> 参考项目：`C:\SoftWare\JetBrains\myTapd`（git@github.com:MiDouTech/myTapd.git）

---

## 1. 背景与目标

### 1.1 问题

社区版界面提示：**「社区版仅支持 1 个组织」**，无法创建第二个组织；同时存在用户数、资源池等配额限制。

### 1.2 自研目标

1. 可自由添加、修改、删除、启用、禁用组织、成员，配置相关权限  
2. 可同步企微组织架构  
3. 可导入组织架构（Excel，MeterSphere 扩展，myTapd 无此能力）  
4. 配置不受 License 限制  
5. 前端保留/补齐以上操作入口  

### 1.3 产品版本说明

MeterSphere V3 分社区版与企业版，详见 [产品版本对比](https://metersphere.io/pricing.html)。自研改动面向**内部部署**，需保留 FIT2CLOUD 协议要求的 Logo/版权标识。

---

## 2. 社区版限制机制（现状）

### 2.1 前端拦截

| 机制 | 位置 | 行为 |
|------|------|------|
| `licenseStore.hasLicense()` | `frontend/src/store/modules/setting/license.ts` | `status === 'valid'` 才视为有 License |
| 黄色提示条 | `frontend/src/components/business/ms-trial-alert/index.vue` | 无 License 时显示 |
| 隐藏「创建组织」 | `frontend/src/views/setting/system/organizationAndProject/index.vue` | `v-if="... \|\| licenseStore.hasLicense()"` |
| 隐藏「进入组织」 | `systemOrganization.vue` | 同上 |
| 阻止切换组织 | `frontend/src/views/setting/utils.ts` | `enterOrganization` / `enterProject` |
| 组织切换菜单 | `frontend/src/components/business/ms-menu/index.vue` | 仅 `enterprise + hasLicense` |
| `v-xpack` 指令 | `frontend/src/directive/validateLicense/index.ts` | 无 License 时移除 DOM 节点 |
| 资源池限制 UI | `resourcePool/index.vue`, `detail.vue` | 多资源池、并发数等 |

### 2.2 后端缺口（关键）

| API | 前端已定义 | 开源后端 |
|-----|-----------|----------|
| `POST /system/organization/add` | ✅ `organizationAndProject.ts` | ❌ **不存在** |
| `POST /system/organization/switch` | ✅ `api/requrls/system.ts` | ❌ **不存在** |
| `GET /system/organization/switch-option` | ✅ | ❌ 仅有 `/user/platform/switch-option` |

`OrganizationService` 有 update/delete/成员管理，**无 add/create 方法**。  
`LicenseService`、`UserXpackService` 仅有接口，开源仓库**无生产实现**（测试 Mock 除外）。

### 2.3 其他社区版限制

- 系统用户约 **5 人**（`UserXpackService` → 错误码 101511/101512）
- **1 个**资源池（`v-xpack` + `NodeResourcePoolService.licenseValidate()`）
- 默认安装仅 **1 个组织**（迁移脚本 `V3.0.0_11_1__data.sql`，ID `100001`）

### 2.4 已有且可用的能力

- 组织：列表、编辑、删除、启停、成员管理  
- 项目：在组织下**可创建多个项目**（不受「1 组织」限制）  
- 用户：CRUD、Excel 导入（`/system/user/import`）  
- 权限：系统/组织/项目三级用户组（Shiro + `user_role_relation`）  
- 企微：**登录扫码**（`/we_com/info`）、**消息 Webhook**（`WeComNoticeSender`），**无通讯录同步**  

---

## 3. 解除限制方案（P0）

### 3.1 后端：Xpack 实现

在 `backend/services/system-setting` 新增：

```text
CommunityLicenseServiceImpl implements LicenseService
  → validate() 始终返回 status = "valid"

CommunityUserXpackServiceImpl implements UserXpackService
  → GWHowToAddUser / GWHowToChangeUser / GWHowToDeleteUser 始终 return 0
```

参考：`backend/services/system-setting/src/test/java/.../LicenseServiceMockImpl.java`

涉及校验的文件：

- `SimpleUserService.java`（用户数量）
- `NodeResourcePoolService.java`（并发上限）
- `TestResourcePoolService.java`
- `ApiExecuteService.java`

### 3.2 后端：补齐组织 API

**`POST /system/organization/add`**

- Controller：`SystemOrganizationController`
- Service：`OrganizationService.add()` 新建
- 初始化：参考 `V3.0.0_11_1__data.sql` 默认组织（模板、自定义字段、Bug 状态流）
- 建议抽取：`OrganizationInitService`（`initOrgTemplates` / `initOrgCustomFields` / `initOrgStatusFlow`）
- 项目创建可参考：`CommonProjectService.add()` + `ProjectServiceInvoker.invokeCreateServices()`

**`POST /system/organization/switch`**

- 复用：`UserLoginService.switchUserResource(sourceId, sessionUser)`
- DTO 已有：`OrganizationSwitchRequest.java`

**`GET /system/organization/switch-option`**

- 复用：`OrganizationService.getSwitchOption(userId)`

### 3.3 前端：统一开关

```env
# frontend/.env.development.local
VITE_MS_UNLIMITED=true
```

```typescript
// frontend/src/store/modules/setting/license.ts
hasLicense() {
  if (import.meta.env.VITE_MS_UNLIMITED === 'true') return true;
  return this.licenseInfo?.status === 'valid';
}
```

部署可选：`MS_PACKAGE_TYPE=enterprise`（影响菜单逻辑，需配合 License 实现）。

### 3.4 前端需同步调整的文件

- `organizationAndProject/index.vue` — 创建组织按钮  
- `systemOrganization.vue` — 进入组织  
- `views/setting/utils.ts` — 切换拦截  
- `ms-menu/index.vue` — 个人中心切换组织  
- `ms-trial-alert/index.vue` — 可选隐藏提示  
- `directive/validateLicense/index.ts` — v-xpack  
- `resourcePool/*.vue` — 资源池能力  

---

## 4. myTapd 组织架构设计（参考）

### 4.1 核心理念

**企微为唯一数据源 + 本地只读镜像**：全量同步部门与用户，管理页只读展示 + 手动/定时同步，**无手工 CRUD 部门**（MeterSphere 自研可扩展 Excel 导入）。

### 4.2 myTapd 数据模型

```text
department (树)
  ├── wecom_dept_id (唯一桥接键)
  ├── parent_id, dept_status, sync_status, sync_time
  └── leader_wecom_userid

sys_user
  ├── department_id (单主部门)
  ├── wecom_userid (唯一)
  └── sync_status, sync_time

sys_wework_config  — 企微连接 + Cron
sys_sync_log       — 同步审计
```

### 4.3 myTapd 同步流程（WecomSyncService）

1. **部门**：拉列表 → Pass1 upsert → Pass2 补 parent_id → 缺失项软停用  
2. **用户**：根部门 fetch_child 拉全量 → 匹配 wecom_userid → 新用户赋 SUBMITTER 角色  
3. **安全**：企微返回空列表 **跳过失活**；phone/position 空值不覆盖  

### 4.4 myTapd 关键文件路径

| 用途 | 路径 |
|------|------|
| 表结构 | `ticket-bootstrap/.../db/migration/V1__init_base.sql`, `V10__init_wework_identity_reuse.sql` |
| 同步核心 | `ticket-application/.../WecomSyncService.java` |
| 企微客户端 | `ticket-infrastructure/.../WecomClient.java` |
| 定时任务 | `ticket-bootstrap/.../WecomSyncJob.java` |
| 管理页 | `miduo-frontend/src/views/manage/UserManageView.vue` |
| API 客户端 | `miduo-frontend/src/api/organization.ts` |

### 4.5 myTapd API 一览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/v1/departments/tree` | 部门树 + 人数 |
| GET | `/v1/employees/page` | 员工分页 |
| GET | `/v1/employees/detail/{id}` | 员工详情 |
| POST | `/v1/sync/manual` | 手动全量同步 |
| GET | `/v1/sync/status` | 最近同步状态 |
| GET | `/v1/sync/log/page` | 同步日志 |
| POST | `/wecom/config/save` | 企微配置 + Cron |

---

## 5. 应用到 MeterSphere 的映射

### 5.1 模型差异

```text
myTapd:     department → sys_user（单租户）

MeterSphere: System → Organization → Project
             department 挂在 Organization 下
             user 已有，权限走 user_role_relation
```

### 5.2 建议表结构（Flyway 3.7.0+）

```sql
-- department（myTapd + organization_id）
CREATE TABLE department (
  id                  VARCHAR(50)  NOT NULL PRIMARY KEY,
  organization_id     VARCHAR(50)  NOT NULL,
  name                VARCHAR(255) NOT NULL,
  parent_id           VARCHAR(50),
  wecom_dept_id       BIGINT,
  sort_order          INT DEFAULT 0,
  dept_status         TINYINT DEFAULT 1,
  sync_status         TINYINT DEFAULT 0,
  sync_time           BIGINT,
  leader_wecom_userid VARCHAR(100),
  create_time         BIGINT NOT NULL,
  update_time         BIGINT NOT NULL,
  create_user         VARCHAR(50) NOT NULL,
  update_user         VARCHAR(50) NOT NULL,
  UNIQUE KEY uk_org_wecom_dept (organization_id, wecom_dept_id)
);

-- user 表扩展
ALTER TABLE user ADD COLUMN wecom_userid VARCHAR(100);
ALTER TABLE user ADD COLUMN department_id VARCHAR(50);
ALTER TABLE user ADD COLUMN position VARCHAR(100);
ALTER TABLE user ADD COLUMN sync_status TINYINT DEFAULT 0;
ALTER TABLE user ADD COLUMN sync_time BIGINT;

-- org_wecom_sync_config（参考 sys_wework_config + organization_id）
-- org_sync_log（参考 sys_sync_log + organization_id）
```

### 5.3 建议模块结构

```text
backend/services/system-setting/
  controller/
    DepartmentController.java
    OrgWecomSyncController.java
    OrgStructureImportController.java      # MS 扩展
  service/department/
    DepartmentQueryService.java
    WecomOrgSyncService.java               ← 移植 myTapd WecomSyncService
    WecomContactClient.java                ← 移植 myTapd WecomClient（通讯录）
    OrgStructureImportService.java
  job/WecomOrgSyncJob.java

frontend/src/views/setting/system/orgStructure/
  index.vue                                ← 参考 UserManageView.vue
frontend/src/api/modules/setting/orgStructure.ts
```

### 5.4 MeterSphere API 建议

| 用途 | 建议路径 |
|------|----------|
| 部门树 | `GET /department/tree?organizationId=` |
| 成员分页 | `GET /department/member/page?organizationId=&departmentId=` |
| 手动同步 | `POST /org-wecom/sync/manual?organizationId=` |
| 同步状态/日志 | `GET /org-wecom/sync/status`, `/sync/log/page` |
| Excel 导入 | `POST /org-structure/import` |

同步用户后：调用 `SimpleUserService` 创建/更新用户，并写入 `user_role_relation`（默认 `org_member`）。

### 5.5 企微能力复用说明

- **可复用概念**：myTapd 的 Token 管理、部门/用户 API、两阶段树同步、空列表保护  
- **不可直接复用**：MS 现有 `notice/utils/WeComClient.java`（仅 Webhook 消息）  
- **需扩展**：现有 `weComModal.vue` 登录配置，增加通讯录 Secret、定时 Cron  

### 5.6 前端入口建议

| 菜单 | 路由 | 权限 |
|------|------|------|
| 系统设置 → 组织架构 | `/setting/system/org-structure` | `SYSTEM_ORGANIZATION_PROJECT:READ` |
| 组织设置 → 组织架构 | `/setting/organization/org-structure` | `ORGANIZATION_MEMBER:READ` |

布局：左部门树 + 右成员表 + 「同步企微」+ 同步日志（参考 myTapd `UserManageView.vue`，组件改用 Arco Design）。

---

## 6. Excel 组织架构导入（MeterSphere 扩展）

myTapd **无**文件导入，需在 MS 自研：

**Sheet - 部门**：部门路径 | 上级部门路径 | 组织名称  
**Sheet - 用户**：姓名 | 邮箱 | 手机 | 组织名称 | 部门路径 | 组织用户组  

参考：`SimpleUserService.importByExcel` + `UserImportEventListener.java`

---

## 7. 实施优先级

| 阶段 | 任务 | 预估 |
|------|------|------|
| **P0** | `CommunityLicenseServiceImpl` + `CommunityUserXpackServiceImpl` | 2 天 |
| **P0** | `POST /system/organization/add` + `OrganizationInitService` | 3 天 |
| **P0** | `POST /system/organization/switch` + 前端 `VITE_MS_UNLIMITED` | 2 天 |
| **P1** | Flyway department 表 + user 扩展字段 | 1 天 |
| **P1** | `WecomContactClient` + `WecomOrgSyncService`（移植 myTapd） | 4 天 |
| **P1** | 同步 API + `WecomOrgSyncJob` | 2 天 |
| **P2** | 前端 `orgStructure/index.vue` | 3 天 |
| **P2** | 扩展企微配置（通讯录 Secret + Cron） | 2 天 |
| **P3** | Excel 组织架构导入 | 3 天 |

**依赖关系**：P1 组织架构同步依赖 P0（多组织 + 用户无上限）。

---

## 8. 待确认决策

1. **企微配置粒度**：系统级一条（类似 myTapd） vs 每组织一条？  
2. **企微与组织映射**：一个企微主体 = 一个 MS 组织（**推荐**）？  
3. **同步用户默认角色**：仅 `org_member` 还是可配置用户组？  
4. **管理页可见范围**：仅系统管理员 vs 组织管理员只看本组织？  

当前推荐默认：**系统级企微配置 + 一个企微主体对应一个 MS 组织 + 同步用户默认 org_member**。

---

## 9. 本地开发速查（当前分支）

```powershell
# 环境检查
.\scripts\check-local-env.ps1

# 启动（需 Docker Desktop 运行）
.\start.ps1

# 停止
.\stop.ps1
```

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 | http://localhost:8081 |
| Nacos | http://localhost:8848/nacos |

环境要求：JDK 21、Node 20+、Docker Desktop、Maven。

---

## 10. MeterSphere 关键文件索引

### 后端

- `OrganizationService.java` — 组织 CRUD（缺 add）
- `SystemOrganizationController.java` — 需补 `/add`、`/switch`
- `UserLoginService.java` — `switchUserResource`
- `CommonProjectService.java` — 项目创建与初始化参考
- `CreateTemplateResourceService.java` — 项目模板初始化参考
- `SimpleUserService.java` — 用户导入与 Xpack 调用
- `LicenseService.java` / `UserXpackService.java` — 扩展接口
- `V3.0.0_11_1__data.sql` — 默认组织初始化数据

### 前端

- `store/modules/setting/license.ts` — License 判定
- `organizationAndProject/index.vue` — 组织与项目入口
- `components/business/ms-menu/index.vue` — 组织切换
- `views/setting/utils.ts` — 进入组织/项目
- `views/setting/system/user/index.vue` — 用户导入
- `views/setting/system/config/components/weComModal.vue` — 企微登录配置

---

## 11. 在 IDE AI 中使用本文档

**Cursor**

```
@docs/community-unlock-and-org-structure.md 请从 P0 开始实现 CommunityLicenseServiceImpl
```

**JetBrains IDEA AI**

在 AI 聊天首条消息粘贴：

> 请先阅读项目内 docs/community-unlock-and-org-structure.md，然后从 P0 组织 API 开始实现。

或直接 `@` 该文件（若 IDE 支持项目文件引用）。

---

*文档生成自 Cursor 会话，随实现进度更新本节与第 7 节任务状态。*
