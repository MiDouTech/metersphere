# task005 - P1 组织架构查询 API

> **阶段**：P1  
> **预估工期**：2 天  
> **前置依赖**：[task004](./task004-P1-数据模型与Flyway迁移.md)  
> **阻塞任务**：task007、task009、task011  
> **关联文档**：[组织架构模块设计摘要.md](../summary/组织架构模块设计摘要.md) §7

---

## 1. 任务目标

提供组织架构**只读查询** API：部门树（含成员统计）、成员分页、成员详情（脱敏），供业务选人与管理页使用。

---

## 2. API 清单

### 2.1 通用部门树（业务选人）

| 项 | 内容 |
|----|------|
| 方法 | GET |
| 路径 | `/department/tree` |
| 参数 | `organizationId`（必填） |
| 权限 | 组织成员可读 |

**响应节点字段**：

```json
{
  "id": "dept_xxx",
  "name": "研发部",
  "parentId": "dept_root",
  "sortOrder": 1,
  "children": []
}
```

### 2.2 管理端部门树（含统计）

| 项 | 内容 |
|----|------|
| 方法 | GET |
| 路径 | `/org-structure/departments/tree` |
| 参数 | `organizationId`（必填） |
| 权限 | `SYSTEM_ORGANIZATION_PROJECT:READ` 或 `ORGANIZATION_MEMBER:READ` |

**增强字段**：

| 字段 | 说明 |
|------|------|
| directUserCount | 直属成员数 |
| totalUserCount | 含子部门成员数 |
| deptStatus | 1 启用 / 0 停用 |
| syncStatus | 同步状态 |
| syncTime | 最近同步时间 |

### 2.3 成员分页

| 项 | 内容 |
|----|------|
| 方法 | GET |
| 路径 | `/org-structure/members/page` |
| 参数 | 见下表 |

| 参数 | 类型 | 说明 |
|------|------|------|
| organizationId | string | 必填 |
| departmentId | string | 可选，部门过滤 |
| keyword | string | 姓名/邮箱/手机模糊 |
| enable | boolean | 账号启用状态 |
| syncStatus | int | 同步状态 |
| current | int | 页码 |
| pageSize | int | 页大小 |

**要求**：SQL 层分页 + 组合索引，**禁止** `findAll` 后内存过滤（myTapd 已知局限）。

### 2.4 成员详情

| 项 | 内容 |
|----|------|
| 方法 | GET |
| 路径 | `/org-structure/members/{id}` |
| 权限 | 同分页 |

**脱敏规则**：

| 字段 | 规则 |
|------|------|
| phone | 保留前 3 + 后 4，中间 `****` |
| email | 用户名保留首尾各 1 位，中间 `***` |
| wecomUserid | 中间段掩码 |

---

## 3. 后端实现

### 3.1 模块结构

```text
backend/services/system-setting/src/main/java/io/metersphere/system/
├── controller/
│   ├── DepartmentController.java
│   └── OrgStructureMemberController.java
├── service/department/
│   ├── DepartmentQueryService.java
│   └── OrgStructureMemberService.java
└── dto/department/
    ├── DepartmentTreeNode.java
    ├── OrgStructureMemberPageRequest.java
    └── OrgStructureMemberDetailDTO.java
```

### 3.2 DepartmentQueryService

| 方法 | 说明 |
|------|------|
| `getTree(organizationId)` | 查 flat list → 内存构树（部门量级可接受） |
| `getTreeWithStats(organizationId)` | 构树 + 聚合 direct/totalUserCount |

**totalUserCount 算法**：自底向上累加子部门人数。

### 3.3 OrgStructureMemberService

| 方法 | 说明 |
|------|------|
| `page(request)` | ExtMapper SQL 分页 |
| `detail(userId, organizationId)` | 详情 + 脱敏 |
| `maskSensitive(dto)` | 脱敏工具 |

### 3.4 权限与数据隔离

- 系统管理员：可查任意 `organizationId`  
- 组织管理员/成员：仅可查 `SessionUtils.getCurrentOrganizationId()`  
- 使用 `@CheckOwner` 或 Service 层校验  

---

## 4. 测试数据脚本（可选）

提供 `docs/task/fixtures/org_structure_test_data.sql`：

- 1 个组织下 3 层部门树  
- 10+ 测试用户绑定 department_id  

---

## 5. 测试要求

### 5.1 单元测试

- [x] 空组织返回空树
- [x] 3 层树结构 parent-child 正确
- [x] totalUserCount 聚合正确
- [x] 脱敏规则覆盖 phone/email/wecomUserid

### 5.2 接口测试

- [x] `DepartmentControllerTests` — tree API
- [x] `OrgStructureMemberControllerTests` — page + detail
- [x] 跨组织访问返回 403

---

## 6. 验收标准

- [x] 四个 API 均可调用并返回预期结构
- [x] 成员分页走 SQL（PageHelper + ExtOrgStructureMemberMapper）
- [x] 详情脱敏符合规则
- [x] 权限隔离有效（OrgStructureAccessService + Shiro）
- [x] Swagger/OpenAPI 文档已补充（@Operation）

---

## 7. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-06 |
| 完成日期 | 2026-07-06 |
