# MeterSphere 权限配置标准操作规范（SOP）

> **文档版本**：v1.0  
> **编写日期**：2026-07-13  
> **适用范围**：MeterSphere V3 自研版系统管理员、组织管理员、项目管理员  
> **文档性质**：权限治理与配置标准操作规范  
> **当前平台版本**：V3.x  
> **【AI生成】已人工审核确认**：待系统管理员 / 测试负责人审核后生效

---

## 目录

1. [权限体系概述](#1-权限体系概述)
2. [核心概念与数据模型](#2-核心概念与数据模型)
3. [内置用户组说明](#3-内置用户组说明)
4. [权限 ID 命名规则](#4-权限-id-命名规则)
5. [系统级权限配置 SOP](#5-系统级权限配置-sop)
6. [组织级权限配置 SOP](#6-组织级权限配置-sop)
7. [项目级权限配置 SOP](#7-项目级权限配置-sop)
8. [标准角色配置模板](#8-标准角色配置模板)
9. [成员授权全流程 SOP](#9-成员授权全流程-sop)
10. [Agent Token 与平台权限的关系](#10-agent-token-与平台权限的关系)
11. [菜单可见性与模块开关](#11-菜单可见性与模块开关)
12. [权限审计与变更管理](#12-权限审计与变更管理)
13. [故障排查](#13-故障排查)
14. [术语表](#14-术语表)
15. [附录](#15-附录)

---

## 1. 权限体系概述

### 1.1 设计原则

MeterSphere 采用 **Shiro + RBAC（基于角色的访问控制）**，权限按 **系统 → 组织 → 项目** 三级隔离：

```
用户（User）
  └── 用户角色关联（user_role_relation）  ← 绑定到 system / orgId / projectId
        └── 用户组 / 角色（user_role）       ← 如 org_admin、project_member
              └── 权限项（user_role_permission） ← 如 FUNCTIONAL_CASE:READ+ADD
```

**生效规则**：

| 规则 | 说明 |
|------|------|
| **上下文隔离** | 组织权限仅在对应 `organizationId` 下生效；项目权限仅在对应 `projectId` 下生效 |
| **前后端双重校验** | 前端控制菜单 / 按钮可见；后端 `@RequiresPermissions` 拦截 API |
| **系统管理员特权** | 拥有内置 `admin` 用户组的用户，**绕过全部权限检查** |
| **最小权限** | 只授予完成工作所需的用户组与权限项 |

### 1.2 管理人员配置职责

| 层级 | 配置入口 | 执行人 |
|------|---------|:--:|
| 系统级 | 系统设置 → 用户组 | 系统管理员 |
| 组织级 | 组织设置 → 用户组 / 成员 | 组织管理员 |
| 项目级 | 项目设置 → 用户组 / 成员 | 项目管理员 |
| 成员绑定 | 各层级「成员」页 | 对应层级管理员 |

> 权限**定义**在用户组；权限**生效**在成员绑定。只建用户组不绑成员，权限不会生效。

### 1.3 与组织架构的关系

| 维度 | 组织架构 | 权限体系 |
|------|---------|---------|
| 数据来源 | 企微同步（只读镜像） | 平台本地配置 |
| 作用 | 回答「是谁、在哪个部门」 | 回答「能在 MS 做什么」 |
| 管理人员 | 系统管理员同步 | 组织 / 项目管理员授权 |

**两者独立**：在组织架构中可见 ≠ 拥有 MeterSphere 操作权限；必须完成 §9 授权流程。

---

## 2. 核心概念与数据模型

### 2.1 关键对象

| 对象 | 数据库表 | 说明 |
|------|---------|------|
| 用户 | `user` | 平台登录账号 |
| 用户组（角色） | `user_role` | 权限集合容器，`type` = SYSTEM / ORGANIZATION / PROJECT |
| 用户角色关联 | `user_role_relation` | 用户 ↔ 用户组 ↔ 作用域（sourceId） |
| 权限项 | `user_role_permission` | 用户组下的原子权限 |

### 2.2 作用域（sourceId）

| 用户组 type | sourceId 含义 | 示例 |
|-------------|--------------|------|
| `SYSTEM` | 固定为 `system` | 系统管理员 |
| `ORGANIZATION` | 组织 ID | `100001` |
| `PROJECT` | 项目 ID | `project-xxx` |

同一用户可同时拥有：

- 1 个系统级用户组（如 `admin`）
- N 个组织级用户组（不同组织各一套）
- M 个项目级用户组（不同项目各一套）

### 2.3 权限校验链路

```
用户请求 API
  → Shiro 认证（Session / API Key / Agent Token）
    → @RequiresPermissions 注解校验
      → PermissionCheckService.userHasSourcePermission()
        → 查 user_role_relation（匹配 sourceId + type）
          → 查 user_role_permission（是否含目标 permissionId）
            → admin 用户组 → 直接通过
```

前端校验链路：

```
路由 / 按钮
  → meta.roles 或 v-permission 指令
    → hasPermission() 查 currentRole 中的 system/org/project Permissions
      → isAdmin → 直接通过
```

---

## 3. 内置用户组说明

平台预置 **6 个内置用户组**（`internal=1`），**不可删除**，**谨慎修改权限**。

| ID | 名称 | type | 典型用途 |
|----|------|------|---------|
| `admin` | 系统管理员 | SYSTEM | 全局运维，拥有全部权限 |
| `member` | 系统成员 | SYSTEM | 基础系统账号，权限极少 |
| `org_admin` | 组织管理员 | ORGANIZATION | 管理本组织成员、项目、模板 |
| `org_member` | 组织成员 | ORGANIZATION | 可被加入项目，无组织设置权限 |
| `project_admin` | 项目管理员 | PROJECT | 项目全部权限 |
| `project_member` | 项目成员 | PROJECT | 项目日常操作（用例/计划/缺陷等） |

### 3.1 内置组修改原则

| 操作 | 建议 |
|------|------|
| 修改 `admin` 权限 | **禁止**，系统管理员应拥有全量权限 |
| 修改 `org_admin` / `project_admin` | **谨慎**，仅在明确需要时增减，变更前备份权限配置 |
| 修改 `org_member` / `project_member` | 可微调，但优先**新建自定义用户组** |
| 删除内置组 | **不可**（平台限制） |

### 3.2 自定义用户组

当内置组不能满足需求时（如「只读 PM」「仅缺陷读写开发」），应**新建用户组**：

| 层级 | 命名建议 | 示例 |
|------|---------|------|
| 系统 | `sys_{用途}` | `sys_audit_readonly` |
| 组织 | `org_{组织缩写}_{用途}` | `org_midoo_auditor` |
| 项目 | `proj_{项目缩写}_{用途}` | `proj_ticket_dev_bugonly` |

---

## 4. 权限 ID 命名规则

### 4.1 格式

```
{资源模块}:{操作}
```

- **资源模块**：大写下划线，如 `FUNCTIONAL_CASE`、`PROJECT_TEST_PLAN`
- **操作**：以 `READ` 为基础，扩展操作以 `+` 连接

### 4.2 常见操作后缀

| 后缀 | 含义 | 示例 |
|------|------|------|
| `READ` | 查看 | `FUNCTIONAL_CASE:READ` |
| `READ+ADD` | 查看 + 创建 | `PROJECT_BUG:READ+ADD` |
| `READ+UPDATE` | 查看 + 编辑 | `FUNCTIONAL_CASE:READ+UPDATE` |
| `READ+DELETE` | 查看 + 删除 | `PROJECT_TEST_PLAN:READ+DELETE` |
| `READ+EXECUTE` | 查看 + 执行 | `PROJECT_TEST_PLAN:READ+EXECUTE` |
| `READ+IMPORT` / `READ+EXPORT` | 导入 / 导出 | `FUNCTIONAL_CASE:READ+IMPORT` |
| `READ+REVIEW` | 评审 | `CASE_REVIEW:READ+REVIEW` |
| `READ+INVITE` | 邀请成员 | `PROJECT_USER:READ+INVITE` |

### 4.3 层级前缀对照

| 前缀 | 层级 | 示例 |
|------|------|------|
| `SYSTEM_*` | 系统 | `SYSTEM_USER:READ`、`SYSTEM_ORGANIZATION_PROJECT:READ+ADD` |
| `ORGANIZATION_*` | 组织 | `ORGANIZATION_MEMBER:READ+ADD`、`ORGANIZATION_PROJECT:READ+ADD` |
| `PROJECT_*` / 业务模块 | 项目 | `FUNCTIONAL_CASE:READ`、`PROJECT_API_DEBUG:READ+EXECUTE` |

> 业务模块权限（用例、计划、缺陷、接口）均为 **PROJECT 级**，即使前缀不含 `PROJECT_`。

### 4.4 业务模块权限速查

| 业务模块 | 核心权限 ID | 说明 |
|---------|------------|------|
| 功能用例 | `FUNCTIONAL_CASE:READ` / `+ADD` / `+UPDATE` / `+DELETE` | 用例 CRUD |
| 用例评审 | `CASE_REVIEW:READ` / `+REVIEW` / `+RELEVANCE` | 评审流程 |
| 测试计划 | `PROJECT_TEST_PLAN:READ` / `+EXECUTE` / `+ASSOCIATION` | 计划执行 |
| 测试报告 | `PROJECT_TEST_PLAN_REPORT:READ` / `+EXPORT` | 报告生成 |
| 缺陷 | `PROJECT_BUG:READ` / `+ADD` / `+UPDATE` | 缺陷管理 |
| 接口调试 | `PROJECT_API_DEBUG:READ` / `+EXECUTE` | 接口调试 |
| 接口定义 | `PROJECT_API_DEFINITION:READ` / `+EXECUTE` | API 管理 |
| 接口场景 | `PROJECT_API_SCENARIO:READ` / `+EXECUTE` | 场景自动化 |
| 项目成员 | `PROJECT_USER:READ` / `+ADD` / `+UPDATE` / `+DELETE` | 成员管理 |
| 项目环境 | `PROJECT_ENVIRONMENT:READ` / `+ADD` / `+UPDATE` | 环境配置 |
| 工作台 | `PROJECT_APPLICATION_WORKSTATION:READ` | 工作台入口 |

完整权限树可在各层级**用户组 → 权限配置**页面查看，对应后端 `permission.json` 定义。

---

## 5. 系统级权限配置 SOP

> **执行角色**：系统管理员  
> **入口**：系统设置 → 用户组（`/setting/system/usergroup`）

### 5.1 系统用户组管理流程

```
Step 1：系统设置 → 用户组 → 创建用户组（或使用内置 admin / member）
Step 2：点击「权限配置」→ 勾选 SYSTEM_* 权限树
Step 3：保存
Step 4：系统设置 → 系统用户 → 选择用户 → 分配该系统用户组
Step 5：用户重新登录 → 验证系统设置菜单可见性
```

### 5.2 系统级权限模块

| 模块 | 关键权限 | 建议授予 |
|------|---------|---------|
| 系统用户 | `SYSTEM_USER:READ+ADD+UPDATE+DELETE` | 仅 HR / IT 运维 |
| 组织与项目 | `SYSTEM_ORGANIZATION_PROJECT:READ+ADD` | 系统管理员 |
| 系统用户组 | `SYSTEM_USER_ROLE:READ+UPDATE` | 系统管理员 |
| 资源池 | `SYSTEM_TEST_RESOURCE_POOL:READ+ADD` | 接口 / 性能测试运维 |
| 系统参数 | `SYSTEM_PARAMETER_SETTING_*:READ+UPDATE` | 系统管理员 |
| 插件 | `SYSTEM_PLUGIN:READ+ADD` | 系统管理员 |
| 操作日志 | `SYSTEM_LOG:READ` | 审计人员（只读） |
| Agent 集成 | 需系统管理员 Session | Token 管理 |

### 5.3 系统管理员授权 SOP

```
Step 1：确认该人员职责确实需要系统级权限
Step 2：系统设置 → 系统用户 → 确认账号存在
Step 3：为用户绑定内置 admin 用户组（sourceId = system）
Step 4：二次确认：告知 admin 可绕过全部权限检查，需签署安全责任
Step 5：记录操作日志，纳入季度审计
```

> **原则**：`admin` 用户组人员控制在 **≤ 3 人**，离职立即移除。

---

## 6. 组织级权限配置 SOP

> **执行角色**：组织管理员（`org_admin`）或系统管理员  
> **入口**：组织设置 → 用户组（`/setting/organization/usergroup`）

### 6.1 组织用户组配置流程

```
Step 1：进入目标组织上下文
Step 2：组织设置 → 用户组 → 创建用户组（或复制内置组思路新建）
Step 3：权限配置 → 勾选 ORGANIZATION_* 权限
Step 4：组织设置 → 成员 → 添加成员 → 分配该用户组
Step 5：成员重新登录 / 切换组织 → 验证组织设置菜单
```

### 6.2 组织级权限模块

| 模块 | 关键权限 | 说明 |
|------|---------|------|
| 组织成员 | `ORGANIZATION_MEMBER:READ+ADD+UPDATE+DELETE` | 成员增删改 |
| 组织项目 | `ORGANIZATION_PROJECT:READ+ADD+UPDATE+DELETE` | 项目 CRUD |
| 组织用户组 | `ORGANIZATION_USER_ROLE:READ+UPDATE` | 管理组织内用户组 |
| 组织模板 | `ORGANIZATION_TEMPLATE:READ+UPDATE` | 用例/缺陷模板 |
| 服务集成 | `SYSTEM_SERVICE_INTEGRATION:READ+ADD` | 第三方集成 |
| 组织日志 | `ORGANIZATION_LOG:READ` | 审计只读 |

### 6.3 组织管理员授权 SOP

```
Step 1：系统 / 组织管理员确认被授权人
Step 2：组织设置 → 成员 → 添加成员（用户须已存在）
Step 3：分配内置 org_admin 用户组
Step 4：验收：被授权人可进入组织设置，可创建项目
```

### 6.4 组织成员（普通）授权 SOP

```
Step 1：组织设置 → 成员 → 添加成员
Step 2：分配 org_member 用户组
Step 3：该成员本身还无法操作业务 → 须由项目管理员将其加入具体项目（§7）
```

---

## 7. 项目级权限配置 SOP

> **执行角色**：项目管理员（`project_admin`）  
> **入口**：项目设置 → 用户组 / 成员

### 7.1 项目用户组配置流程

```
Step 1：进入目标项目
Step 2：项目设置 → 用户组 → 创建用户组
Step 3：权限配置 → 按 §8 模板勾选 PROJECT 级权限
Step 4：项目设置 → 成员 → 添加成员 → 选择用户组
Step 5：成员进入项目 → 验证菜单与操作按钮
```

### 7.2 项目成员绑定 SOP

```
Step 1：确认用户已是本组织成员（否则先走 §6.4）
Step 2：项目设置 → 成员 → 添加成员
Step 3：选择用户 + 用户组（project_admin / project_member / 自定义组）
Step 4：保存
Step 5：通知成员刷新页面或重新进入项目
```

### 7.2 项目级权限模块

| 模块 | 配置页面 | 关键权限 |
|------|---------|---------|
| 项目基本信息 | 项目设置 → 基本信息 | `PROJECT_BASE_INFO:READ+UPDATE` |
| 项目成员 | 项目设置 → 成员 | `PROJECT_USER:READ+ADD+UPDATE+DELETE` |
| 项目用户组 | 项目设置 → 用户组 | `PROJECT_GROUP:READ+UPDATE` |
| 项目环境 | 项目设置 → 环境管理 | `PROJECT_ENVIRONMENT:READ+ADD+UPDATE` |
| 项目模板 | 项目设置 → 项目模板 | `PROJECT_TEMPLATE:READ+UPDATE` |
| 消息通知 | 项目设置 → 消息管理 | `PROJECT_MESSAGE:READ+ADD` |
| 功能用例 | 用例管理 | `FUNCTIONAL_CASE:*` |
| 测试计划 | 测试计划 | `PROJECT_TEST_PLAN:*` |
| 缺陷 | 缺陷管理 | `PROJECT_BUG:*` |
| 接口测试 | 接口测试 | `PROJECT_API_*:*` |

### 7.3 项目管理员授权 SOP

```
Step 1：组织管理员创建项目时指定项目管理员
  或：项目设置 → 成员 → 将目标用户用户组改为 project_admin
Step 2：验收：可管理成员、环境、模板
Step 3：确认项目至少保留 2 名 project_admin（防止单点）
```

---

## 8. 标准角色配置模板

> 以下为**推荐配置**，实施时可复制为自定义用户组，避免直接改动内置组。

### 8.1 测试工程师

**用户组类型**：项目级自定义 `proj_{项目}_tester`（或直接用 `project_member`）

| 模块 | 权限 |
|------|------|
| 功能用例 | `READ` `+ADD` `+UPDATE` `+IMPORT` `+EXPORT` |
| 用例评审 | `READ` `+REVIEW` |
| 测试计划 | `READ` `+EXECUTE` `+ASSOCIATION` |
| 测试报告 | `READ` |
| 缺陷 | `READ` `+ADD` `+UPDATE` `+COMMENT` |
| 工作台 | `PROJECT_APPLICATION_WORKSTATION:READ` |
| 项目环境 | `READ` |

### 8.2 测试负责人

**用户组类型**：项目级 `project_admin` 或自定义 `proj_{项目}_test_lead`

在测试工程师基础上增加：

| 模块 | 额外权限 |
|------|---------|
| 测试计划 | `+ADD` `+UPDATE` `+DELETE` |
| 测试报告 | `+EXPORT` |
| 项目成员 | `PROJECT_USER:READ+ADD` |
| 项目环境 | `+ADD` `+UPDATE` |

### 8.3 开发工程师

**用户组类型**：项目级自定义 `proj_{项目}_developer`

| 模块 | 权限 |
|------|------|
| 功能用例 | `READ` |
| 测试计划 | `READ` |
| 缺陷 | `READ` `+ADD` `+UPDATE` |
| 接口调试 | `READ` `+EXECUTE` |
| 工作台 | `READ` |

### 8.4 产品经理

**用户组类型**：项目级自定义 `proj_{项目}_pm`

| 模块 | 权限 |
|------|------|
| 功能用例 | `READ` |
| 用例评审 | `READ` `+REVIEW` |
| 测试计划 | `READ` |
| 测试报告 | `READ` |
| 缺陷 | `READ` `+COMMENT` |
| 工作台 | `READ` |

### 8.5 只读 / 管理层

**用户组类型**：项目级自定义 `proj_{项目}_readonly`

| 模块 | 权限 |
|------|------|
| 全部业务模块 | 仅 `READ` |
| 测试报告 | `READ` `+EXPORT`（可选） |

### 8.6 接口测试工程师

**用户组类型**：项目级自定义 `proj_{项目}_api_tester`

| 模块 | 权限 |
|------|------|
| 接口调试 | `READ` `+ADD` `+UPDATE` `+DELETE` `+EXECUTE` |
| 接口定义 | `READ` `+ADD` `+UPDATE` `+EXECUTE` `+IMPORT` `+EXPORT` |
| 接口场景 | `READ` `+ADD` `+UPDATE` `+EXECUTE` |
| 缺陷 | `READ` `+ADD` |
| 项目环境 | `READ` |

### 8.7 配置模板使用流程

```
Step 1：项目设置 → 用户组 → 创建用户组（按 §8.x 命名）
Step 2：权限配置 → 按模板勾选
Step 3：项目设置 → 成员 → 批量添加成员并分配对应组
Step 4：spot check 2–3 个账号验证菜单与 API
```

---

## 9. 成员授权全流程 SOP

### 9.1 新成员标准授权（端到端）

```
① 系统管理员：创建系统用户（或企微同步自动创建）
    ↓
② 组织管理员：组织设置 → 成员 → 添加 → 分配 org_member
    ↓
③ 项目管理员：项目设置 → 成员 → 添加 → 分配对应用户组（§8）
    ↓
④ 成员：登录 → 选择组织 → 进入项目 → 验证菜单
    ↓
⑤ 管理员：确认无「403 / 无权限 / 菜单缺失」反馈
```

**SLA**：常规入职 **1 个工作日**内完成；紧急 **2 小时**内完成项目级授权。

### 9.2 权限变更

| 场景 | 操作 | 执行人 |
|------|------|:--:|
| 升级为项目管理员 | 成员页 → 改用户组为 `project_admin` | 项目 / 组织管理员 |
| 降级为只读 | 改用户组为 readonly 自定义组 | 项目管理员 |
| 跨项目调动 | 原项目移除 + 新项目添加 | 双方项目管理员 |
| 升级为组织管理员 | 组织成员 → 改 `org_admin` | 系统 / 组织管理员 |
| 临时授权 | 新建临时用户组，设过期提醒 | 项目管理员 |

### 9.3 离职 / 回收

```
Step 1：项目管理员 → 从所有相关项目移除成员
Step 2：组织管理员 → 从组织移除成员
Step 3：系统管理员 → 禁用系统用户 + 禁用 Agent Token（如有）
Step 4：检查 user_role_relation 无残留（操作日志确认）
```

### 9.4 授权验收 Checklist

- [ ] 用户能登录并看到正确组织 / 项目
- [ ] 业务菜单（用例 / 计划 / 缺陷等）按角色显示
- [ ] 关键操作可执行（如测试工程师可创建用例、执行计划）
- [ ] 不应看到的菜单不可见（如无权限的设置页）
- [ ] 不应执行的操作返回 403（如开发不能删用例）

---

## 10. Agent Token 与平台权限的关系

Agent Token 是**独立于 RBAC 用户组**的 API 鉴权机制，但会** impersonate（模拟）** 关联用户身份。

### 10.1 对比

| 维度 | 平台 RBAC | Agent Token |
|------|----------|-------------|
| 认证方式 | Session 登录 | `Authorization: Bearer msat_xxx` |
| 权限来源 | user_role_permission | Token `scopes` 字段 |
| 配置入口 | 用户组 + 成员 | 系统设置 → Agent 集成 |
| 作用范围 | 全平台 UI + API | 仅 `/api/agent/v1/**` |
| 关联用户 | 当前登录用户 | Token 绑定的 user_id |

### 10.2 Agent Scope

| Scope | 允许操作 |
|-------|---------|
| `FUNCTIONAL_READ` | 检索用例、模块列表 |
| `FUNCTIONAL_SUBMIT` | 回写执行结果 |
| `FUNCTIONAL_ALL` | 上述全部 |

### 10.3 配置注意

| 要点 | 说明 |
|------|------|
| Token 关联用户 | 须是有效系统用户，且对该 project 有业务权限 |
| 与 RBAC 独立 | 有 Token 无 Scope → 403；有 Scope 但关联用户无项目权限 → 业务失败 |
| 最小 Scope | 只读分析用 `FUNCTIONAL_READ`；回写需 `FUNCTIONAL_SUBMIT` |
| 离职处理 | 禁用 Token + 禁用用户，二者同步 |

> Agent Token 配置详见：[SOP-MeterSphere-Agent协作平台操作规范.md](./SOP-MeterSphere-Agent协作平台操作规范.md)

---

## 11. 菜单可见性与模块开关

权限配置后仍可能出现「有权限但看不到菜单」，需检查以下两项：

### 11.1 路由权限（meta.roles）

前端路由声明所需权限，如测试计划路由要求 `PROJECT_TEST_PLAN:READ`。  
用户组中**必须包含对应 READ 权限**，菜单才会显示。

### 11.2 项目模块开关

**路径**：项目设置 → 菜单管理

| 说明 | 影响 |
|------|------|
| 关闭某模块（如接口测试） | 即使有 `PROJECT_API_DEBUG:READ` 也不显示菜单 |
| 适用场景 | 纯功能测试项目关闭接口模块 |

**排查顺序**：

```
看不到菜单？
  → ① 是否有对应 READ 权限？
    → ② 是否已加入项目成员？
      → ③ 项目菜单管理中该模块是否开启？
        → ④ 是否 org / project 上下文正确？
```

---

## 12. 权限审计与变更管理

### 12.1 审计日志

| 层级 | 路径 | 记录内容 |
|------|------|---------|
| 系统 | 系统设置 → 操作日志 | 组织创建、用户变更、Token 操作 |
| 组织 | 组织设置 → 日志 | 成员变更、项目创建 |
| 项目 | 项目设置 → 日志 | 成员变更、权限相关配置 |

### 12.2 定期审计（建议每季度）

- [ ] `admin` 用户组人员清单与在职状态
- [ ] 各项目 `project_admin` 至少 2 人
- [ ] 离职人员无残留 user_role_relation
- [ ] Agent Token 持有人与在职状态一致
- [ ] 自定义用户组是否仍被使用，清理废弃组

### 12.3 变更管理规范

| 规范 | 说明 |
|------|------|
| 变更前评估 | 影响范围（单用户 / 单项目 / 全组织） |
| 禁止直接改内置组 | 优先新建自定义组 |
| 变更后验证 | spot check 至少 1 个受影响账号 |
| 记录变更原因 | 在工单 / 运维记录中留痕 |
| 回滚方案 | 保留变更前用户组权限截图或导出 |

---

## 13. 故障排查

### 13.1 常见现象与处理

| 现象 | 可能原因 | 处理 |
|------|---------|------|
| 登录后无任何菜单 | 未加入任何组织 / 项目 | 按 §9.1 补齐授权 |
| 能看到组织但无项目菜单 | 未加入项目成员 | 项目管理员添加成员 |
| 菜单可见但操作 403 | 有 READ 无 ADD/UPDATE/DELETE | 用户组补权限 |
| 组织设置不可见 | 非 org_admin | 组织管理员赋 org_admin |
| 项目设置不可见 | 非 project_admin | 赋 project_admin 或相应权限 |
| 切换组织后权限不对 | 上下文切换，权限按组织隔离 | 确认在该组织下有对应用户组 |
| 系统管理员仍 403 | 账号未绑 admin 组 | 系统设置检查 user_role_relation |
| Agent API 401 | Token 无效 / 禁用 | 系统管理员重建 Token |
| Agent API 403 | Scope 不足 | 增加 FUNCTIONAL_SUBMIT |
| 接口测试菜单不显示 | 项目菜单管理已关闭模块 | 项目设置 → 菜单管理开启 |
| 企微同步用户无法登录 | 用户未设密码 / 未启用 | 系统管理员重置密码 |

### 13.2 排查工具

| 方式 | 说明 |
|------|------|
| 浏览器 DevTools → Network | 查看 API 返回 403 及错误码 |
| 操作日志 | 确认授权变更是否生效 |
| 用户组权限配置页 | 核对 permissionId 是否勾选 |
| 数据库（运维） | 查 `user_role_relation`、`user_role_permission` |

### 13.3 升级路径

```
用户反馈权限问题
  → 项目管理员自查 §13.1
    → 未解决：组织管理员检查组织成员 / 用户组
      → 未解决：系统管理员检查系统用户 / admin 组
        → 仍未解决：提交 issue（附 userId、orgId、projectId、操作步骤、Network 截图）
```

---

## 14. 术语表

| 术语 | 说明 |
|------|------|
| **RBAC** | 基于角色的访问控制 |
| **用户组** | 即角色（user_role），权限的集合 |
| **内置用户组** | 平台预置的 6 个组，不可删除 |
| **sourceId** | 用户组作用域 ID（system / orgId / projectId） |
| **permissionId** | 原子权限标识，如 `FUNCTIONAL_CASE:READ+ADD` |
| **admin** | 系统超级管理员用户组，绕过全部权限检查 |
| **Scope** | Agent Token 的 API 权限范围，与 RBAC 独立 |
| **模块开关** | 项目级菜单可见性配置，独立于 RBAC |

---

## 15. 附录

### 附录 A：管理人员权限配置 Checklist

**新项目启动**

- [ ] 组织成员已添加
- [ ] 项目成员按 §8 模板配置
- [ ] 至少 2 名 project_admin
- [ ] 项目菜单模块开关已确认
- [ ] spot check 各角色账号

**新成员入职**

- [ ] 系统用户存在
- [ ] org_member 已分配
- [ ] 项目用户组已分配
- [ ] 成员验收通过

**成员离职**

- [ ] 项目成员已移除
- [ ] 组织成员已移除
- [ ] 系统用户已禁用
- [ ] Agent Token 已禁用

### 附录 B：配置入口速查

| 操作 | 路径 |
|------|------|
| 系统用户组 | `/setting/system/usergroup` |
| 系统用户 | `/setting/system/user` |
| 组织用户组 | `/setting/organization/usergroup` |
| 组织成员 | `/setting/organization/member` |
| 项目用户组 | `/project-management/permission/userGroup` |
| 项目成员 | `/project-management/permission/member` |
| 项目菜单 | `/project-management/permission/menuManagement` |
| Agent Token | `/setting/system/agent-integration` |

### 附录 C：内置用户组 ID 速查

| ID | 名称 | type |
|----|------|------|
| `admin` | 系统管理员 | SYSTEM |
| `member` | 系统成员 | SYSTEM |
| `org_admin` | 组织管理员 | ORGANIZATION |
| `org_member` | 组织成员 | ORGANIZATION |
| `project_admin` | 项目管理员 | PROJECT |
| `project_member` | 项目成员 | PROJECT |

### 附录 D：相关文档

| 文档 | 路径 |
|------|------|
| 本 SOP | `docs/SOP/SOP-MeterSphere-权限配置操作规范.md` |
| 组织与项目管理人员 SOP | `docs/SOP/SOP-MeterSphere-组织与项目管理人员操作规范.md` |
| 测试人员 SOP | `docs/SOP/SOP-MeterSphere-测试人员操作规范.md` |
| Agent 协作 SOP | `docs/SOP/SOP-MeterSphere-Agent协作平台操作规范.md` |
| 权限常量（开发参考） | `backend/framework/sdk/.../PermissionConstants.java` |
| 权限树定义（开发参考） | `backend/services/*/src/main/resources/permission.json` |

### 附录 E：版本记录

| 版本 | 日期 | 变更说明 | 作者 |
|------|------|---------|------|
| v1.0 | 2026-07-13 | 初版：三级 RBAC、内置组、标准模板、Agent Token 关系、故障排查 | AI 辅助生成 |

---

*本文档由平台治理团队维护，随 MeterSphere 版本迭代更新。权限变更涉及安全，重大调整需经系统管理员审批。*
