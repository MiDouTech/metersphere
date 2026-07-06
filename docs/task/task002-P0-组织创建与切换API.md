# task002 - P0 组织创建与切换 API

> **阶段**：P0  
> **预估工期**：3 天  
> **前置依赖**：[task001](./task001-P0-社区版Xpack与License实现.md)  
> **阻塞任务**：task003、task004、task007、task011  
> **关联文档**：[community-unlock-and-org-structure.md](../summary/community-unlock-and-org-structure.md) §3.2

---

## 1. 任务目标

补齐前端已定义但后端缺失的组织 API，支持**多组织创建、切换、切换选项查询**，并在创建组织时完成与默认组织相同的基础数据初始化。

---

## 2. 现状缺口

| API | 前端 | 后端 |
|-----|------|------|
| `POST /system/organization/add` | ✅ `postAddOrgUrl` | ✅ 已实现 |
| `POST /system/organization/switch` | ✅ `api/requrls/system.ts` | ✅ 已实现 |
| `GET /system/organization/switch-option` | ✅ | ✅ 已实现 |

`OrganizationService` 有 update/delete/成员管理，**无 add/create 方法**。

---

## 3. 任务清单

### 3.1 OrganizationInitService（新建）

**路径**：`backend/services/system-setting/src/main/java/io/metersphere/system/service/OrganizationInitService.java`

**职责**：抽取默认组织初始化逻辑，供新建组织复用。

| 方法 | 说明 | 参考 |
|------|------|------|
| `initOrgTemplates(organizationId, operatorId)` | 组织级用例/API/Bug 模板 | `V3.0.0_11_1__data.sql` + `OrganizationTemplateService` |
| `initOrgCustomFields(organizationId, operatorId)` | 组织自定义字段 | 迁移脚本 DML |
| `initOrgStatusFlow(organizationId, operatorId)` | Bug 状态流 | `OrganizationStatusFlowSettingService` |
| `initOrgDefaultRoles(organizationId, operatorId)` | 确保 org_admin/org_member 可用 | 已有全局角色，绑定 scope |

### 3.2 OrganizationService.add()（新建）

**输入 DTO**：复用或扩展 `OrganizationEditRequest` / 新建 `OrganizationAddRequest`

**流程**：

1. 生成组织 ID、num（参考现有 num 生成规则）  
2. 插入 `organization` 表  
3. 调用 `OrganizationInitService` 完成初始化  
4. 绑定组织管理员（`userIds`）→ `user_role_relation`（`org_admin`）  
5. 写操作日志  

**参考**：`CommonProjectService.add()` + `ProjectServiceInvoker.invokeCreateServices()` 的初始化模式

### 3.3 SystemOrganizationController 补齐接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/system/organization/add` | 创建组织 |
| POST | `/system/organization/switch` | 切换当前组织 |
| GET | `/system/organization/switch-option` | 当前用户可切换的组织列表 |

**switch 实现**：

```java
// 复用已有能力
userLoginService.switchUserResource(request.getOrganizationId(), SessionUtils.getUser());
```

**switch-option 实现**：

```java
// 复用已有能力
return organizationService.getSwitchOption(SessionUtils.getUserId());
```

### 3.4 DTO 与校验

- 确认 `OrganizationSwitchRequest.java` 字段与前端一致  
- 组织名称非空、管理员至少 1 人、名称唯一性校验  

### 3.5 单元 / 接口测试

**路径**：`SystemOrganizationControllerTests.java`

- [x] 创建组织成功，返回组织 ID  
- [x] 创建后模板/状态流/自定义字段已初始化  
- [x] 管理员已绑定 org_admin  
- [x] switch 后 session 中 last_organization_id 更新  
- [x] switch-option 返回用户有权限的组织列表  

---

## 4. 初始化数据对照

参考 `backend/framework/domain/src/main/resources/migration/3.0.0/dml/V3.0.0_11_1__data.sql`：

- 默认组织 ID：`100001`  
- 新建组织需复制同等粒度的：模板、字段、状态流、权限（组织级）  

**注意**：不要复制项目数据；项目由用户在组织下自行创建。

---

## 5. 验收标准

- [ ] `POST /system/organization/add` 可创建第 2 个组织  
- [ ] 新组织下可正常创建项目、用例模板、Bug 模板  
- [ ] 新组织管理员可进入组织设置  
- [ ] `POST /system/organization/switch` 切换后前端上下文正确  
- [ ] `GET /system/organization/switch-option` 返回预期列表  
- [ ] 操作有审计日志  

---

## 6. 关联前端（task003 联调）

| 前端文件 | 期望行为 |
|----------|----------|
| `addOrganizationModal.vue` | 调用 `postAddOrgUrl` 成功 |
| `api/requrls/setting/organizationAndProject.ts` | 无需改 URL |
| `views/setting/utils.ts` | switch 后跳转正确 |

---

## 7. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-03 |
| 完成日期 | 2026-07-03 |
