# task002 代码详细说明 — 组织创建与切换 API

> **任务文档**：[../../task/task002-P0-组织创建与切换API.md](../../task/task002-P0-组织创建与切换API.md)  
> **开发摘要**：[../2026-07-03-task002-组织创建与切换API-开发摘要.md](../2026-07-03-task002-组织创建与切换API-开发摘要.md)  
> **完成日期**：2026-07-03

---

## 1. 任务要解决什么问题

前端 `postAddOrgUrl`、`SwitchOrgUrl`、`OrgOptionsUrl` 已定义，后端 `OrganizationService` 仅有 update/delete/成员管理，**缺少创建与切换**。  
社区版解除单组织限制后，必须提供与默认组织同等初始化粒度的 `add` API。

---

## 2. 涉及文件一览

| 文件 | 类型 | 职责 |
|------|------|------|
| `OrganizationInitService.java` | 新增 | 新建组织基础数据初始化 |
| `OrganizationService.java` | 修改 | `add`、`switchOrganization`、`generateNextOrganizationNum` |
| `SystemOrganizationController.java` | 修改 | REST 三接口 |
| `SystemOrganizationLogService.java` | 修改 | 创建组织操作日志 |
| `ExtOrganizationMapper.java` / `.xml` | 修改 | `getMaxNum` |
| `SystemOrganizationControllerTests.java` | 测试 | Order 25–29 集成用例 |

路径前缀：`backend/services/system-setting/src/main/java/io/metersphere/system/`

---

## 3. `OrganizationInitService`

### 3.1 `initOrganization(organizationId, operatorId)`

编排入口，依次调用：

| 方法 | 说明 |
|------|------|
| `initOrgTemplates` | 功能/API/Bug/UI/测试计划默认模板（`BaseTemplateService`） |
| `initOrgStatusFlow` | Bug 状态流（`BaseStatusFlowSettingService`） |
| `initOrgDefaultRoles` | 占位；实际 admin 绑定在 `OrganizationService.add` |

### 3.2 与迁移脚本关系

默认组织 DML（`V3.0.0_11_1__data.sql`）中的模板/状态流逻辑，通过 `BaseTemplateService` / `BaseStatusFlowSettingService` 在运行时复刻到新组织。

---

## 4. `OrganizationService.add()`

```text
checkOrganizationExist / checkUserExist
  → 生成 id、num、时间戳
  → organizationMapper.insertSelective
  → organizationInitService.initOrganization
  → userIds.forEach → createAdmin (org_admin)
  → 返回 OrganizationDTO
```

### 4.1 `generateNextOrganizationNum()`

- `ExtOrganizationMapper.getMaxNum()` 取当前最大 num  
- 若 null 或小于默认基数 → `DEFAULT_ORGANIZATION_NUM + 1`  
- 否则 `maxNum + 1`

---

## 5. `OrganizationService.switchOrganization()`

| 步骤 | 说明 |
|------|------|
| 用户校验 | `request.userId` 必须等于当前登录用户 |
| 组织存在 | `organizationMapper.selectByPrimaryKey` |
| 权限 | 非超管时 `extOrganizationMapper.getRelatedOrganizationIds` 须包含目标 org |
| 切换 | `userLoginService.switchUserResource(organizationId, userDTO)` |
| 返回 | 刷新后的 `UserDTO`（含 `lastOrganizationId` 等） |

前端 `switchUserOrg` → `POST /system/organization/switch`，body：`{ organizationId, userId }`。

---

## 6. `SystemOrganizationController` 新增接口

| 方法 | 路径 | 权限/注解 |
|------|------|-----------|
| POST | `/system/organization/add` | `@RequiresPermissions` + `@Log` |
| POST | `/system/organization/switch` | 登录用户 |
| GET | `/system/organization/switch-option` | 当前用户可切换组织列表 |

---

## 7. 测试

`SystemOrganizationControllerTests` 新增用例（Order 25–29）覆盖 add / switch / switch-option。  
执行依赖 Testcontainers（**ENV-001** 本地 Docker API 阻塞时无法作为 CI 依据）。

---

## 8. 关联修复

项目编辑所属组织不生效见 [../buglist/2026-07-04-BUG-PRJ-001-项目所属组织更新不生效.md](../buglist/2026-07-04-BUG-PRJ-001-项目所属组织更新不生效.md)。

---

## 9. 相关文档

| 文档 | 路径 |
|------|------|
| task003 前端联调 | [task003-前端License解除与组织入口-代码详细说明.md](./task003-前端License解除与组织入口-代码详细说明.md) |
| task001 License | [task001-社区版Xpack与License-代码详细说明.md](./task001-社区版Xpack与License-代码详细说明.md) |
