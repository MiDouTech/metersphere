# MeterSphere 全系统测试策略

> **文档类型**：测试策略 / 可复用测试指南  
> **适用项目**：MeterSphere V3 自研（社区版解除限制 + 组织架构扩展）  
> **编写日期**：2026-07-04  
> **版本**：v1.0  
> **关联文档**：[task000-实施总览与依赖关系](./task/task000-实施总览与依赖关系.md)、[community-unlock-and-org-structure.md](../../summary/community-unlock-and-org-structure.md)

---

## 0. 文档用途（供其他对话引用）

在其他 Cursor 对话中，可直接引用本文档：

```
请依据 docs/MeterSphere-全系统-测试策略-2026-07-04.md，
对【模块名】执行【功能/UI/接口】测试，输出缺陷清单与复现步骤。
```

**本文档可指导：**

- 按模块划分的功能、UI、接口测试范围与优先级
- 已有自动化测试的复用方式与缺口补齐方向
- 缺陷分级、记录模板、验收标准
- 里程碑 M0–M3 的回归检查清单

---

## 1. 测试目标

检测 MeterSphere 系统中是否存在以下异常：

| 维度 | 检测内容 |
|------|----------|
| **功能** | 业务流程中断、数据不一致、权限越权、配额误拦截、初始化缺失 |
| **UI** | 页面无法访问、菜单跳转错误、按钮/表单状态异常、空态/错误态展示不当 |
| **接口** | HTTP 状态码异常、响应结构错误、参数校验缺失、权限注解失效、副作用未执行 |

**当前改造重点（P0 已完成部分优先回归）：**

1. 社区版 License / Xpack 解除限制（用户数、资源池、组织数）
2. 组织创建 / 切换 / 初始化（`OrganizationInitService`）
3. 前端组织入口与 License 绕过（`VITE_MS_UNLIMITED`）
4. 后续 P1–P3：组织架构、企微同步、Excel 导入（按 task004–011 扩展）

---

## 2. 测试分层与工具矩阵

```
                    ┌─────────────────┐
                    │  E2E / 验收测试  │  手工 + Playwright（外部 visualTest）
                    └────────┬────────┘
              ┌──────────────┴──────────────┐
              │      UI 视觉 / 交互测试      │  Playwright / 手工走查
              └──────────────┬──────────────┘
        ┌────────────────────┴────────────────────┐
        │         接口集成测试（MockMvc）           │  JUnit5 + BaseTest（已有 ~97 类）
        └────────────────────┬────────────────────┘
  ┌──────────────────────────┴──────────────────────────┐
  │              单元测试（Service 逻辑）                 │  JUnit5（当前仅 2 类，需扩展）
  └─────────────────────────────────────────────────────┘
```

| 层级 | 工具 / 框架 | 位置 | 现状 |
|------|-------------|------|------|
| 单元测试 | JUnit 5 | `backend/services/*/src/test/java/**/service/**` | 仅 `CommunityLicenseServiceImplTest`、`CommunityUserXpackServiceImplTest` |
| 接口集成测试 | Spring Boot Test + MockMvc | `backend/services/*/src/test/java/**/*ControllerTests.java` | **成熟**，各模块均有 `BaseTest` 基类 |
| 前端静态检查 | ESLint + vue-tsc | `frontend/` | 有 `npm run lint`、`npm run type:check`，**无组件/E2E 测试** |
| UI / E2E | Playwright（建议独立 visualTest 项目） | 仓库外或新建 `e2e/` | **缺口**，task003+ 前端需补齐 |
| 手工验收 | 浏览器 + Swagger/OpenAPI | 本地/测试环境 | 里程碑 M0–M3 验收 |

### 2.1 环境前置条件

| 项 | 要求 |
|----|------|
| 后端 | JDK 21，Maven；嵌入式 Docker（MySQL 8、Redis、Kafka、MinIO）用于 `mvn test` |
| 前端 | Node.js；`.env.development` 配置 `VITE_MS_UNLIMITED=true`（社区版解除前端限制） |
| 测试账号 | 系统管理员 `admin`；普通用户；多组织场景需 2+ 组织 |
| 数据 | SQL 夹具：`backend/services/*/src/test/resources/dml/` |

### 2.2 通用执行命令

```bash
# 后端 - 单模块接口测试
mvn test -pl backend/services/system-setting
mvn test -pl backend/services/project-management
mvn test -pl backend/services/case-management
mvn test -pl backend/services/api-test
mvn test -pl backend/services/test-plan
mvn test -pl backend/services/bug-management
mvn test -pl backend/services/dashboard

# 后端 - 指定测试类
mvn test -pl backend/services/system-setting -Dtest=SystemOrganizationControllerTests

# 前端 - 静态检查
cd frontend && npm run type:check && npm run lint
```

---

## 3. 缺陷分级标准

| 级别 | 定义 | 示例 |
|------|------|------|
| **P0-阻断** | 核心流程不可用、数据丢失、安全越权 | 无法登录；创建组织后无模板；非管理员可删组织 |
| **P1-严重** | 主要功能异常但有绕行 | 切换组织后项目列表未刷新；用户创建成功但列表不显示 |
| **P2-一般** | 次要功能、边界场景 | 分页排序错误；操作日志缺失 |
| **P3-轻微** | UI 样式、文案、体验 | 按钮间距；i18n 缺失 |

### 缺陷记录模板

```markdown
### BUG-XXX 【P级】标题
- **模块**：
- **类型**：功能 / UI / 接口
- **复现步骤**：
- **期望结果**：
- **实际结果**：
- **关联 API**：`METHOD /path`
- **关联代码**：`文件路径:行号`
- **测试类/用例**：（如有）
```

---

## 4. 按模块测试策略

以下按 **后端服务模块 = 前端路由模块** 对齐划分。每个模块包含：**功能测试点**、**UI 测试点**、**接口测试点**、**已有测试类**、**优先级**。

---

### 4.1 系统设置（system-setting）

**范围**：用户/角色、组织/项目、License、资源池、插件、系统参数、登录、任务中心、操作日志

| 子模块 | 前端路径 | 后端 API 前缀 |
|--------|----------|---------------|
| 系统用户 | `/setting/system/user` | `/system/user/*` |
| 用户组 | `/setting/system/usergroup` | `/system/user-role/*` |
| 组织与项目 | `/setting/system/organizationAndProject` | `/system/organization/*`、`/system/project/*` |
| License | `/setting/system/authorizedManagement` | `/license/*` |
| 资源池 | `/setting/system/resourcePool` | `/system/test-resource-pool/*` |
| 插件 | `/setting/system/plugin` | `/system/plugin/*` |
| 系统参数 | `/setting/system/parameter` | `/system/parameter/*` |
| 操作日志 | `/setting/system/log` | `/system/operation-log/*` |

#### 功能测试点

| ID | 场景 | 优先级 | 备注 |
|----|------|--------|------|
| SYS-F01 | 创建第 2+ 个组织，初始化模板/自定义字段/状态流/角色 | **P0** | M0 验收；对照 `OrganizationInitService` |
| SYS-F02 | 组织切换后，全局上下文（项目、权限）正确更新 | **P0** | `enterOrganization()` |
| SYS-F03 | 创建用户超过 5 人不被拦截 | **P0** | task001 回归 |
| SYS-F04 | 创建多个资源池不被单池限制拦截 | **P0** | `NodeResourcePoolService` |
| SYS-F05 | 组织成员添加/移除/角色变更 | P1 | |
| SYS-F06 | 系统项目 CRUD + 成员管理 | P1 | |
| SYS-F07 | 组织禁用/启用/删除/恢复生命周期 | P1 | |
| SYS-F08 | 用户导入/批量操作 | P1 | 参考 BUG001 |
| SYS-F09 | 插件安装/卸载 | P2 | |
| SYS-F10 | 任务中心调度任务 | P2 | |

#### UI 测试点

| ID | 场景 | 检查项 |
|----|------|--------|
| SYS-U01 | 组织与项目页 | 「创建组织」按钮可见且可点击（`VITE_MS_UNLIMITED=true`） |
| SYS-U02 | 组织列表 | 分页、搜索、操作列（进入/编辑/禁用） |
| SYS-U03 | 创建组织弹窗 | 名称校验、管理员必选、提交后列表刷新 |
| SYS-U04 | 进入组织 | 点击后跳转组织设置上下文，左侧菜单切换 |
| SYS-U05 | License 页 | 社区版显示 valid，无阻断弹窗 |
| SYS-U06 | 用户列表 | 新建用户后立即出现在列表（BUG001 回归） |
| SYS-U07 | 资源池页 | 可创建多个池，无 License 弹窗 |

#### 接口测试点

| API | 方法 | 关键断言 | 测试类 |
|-----|------|----------|--------|
| `/system/organization/add` | POST | 返回 orgId；副作用：模板/字段/状态流/角色 | `SystemOrganizationControllerTests` |
| `/system/organization/switch` | POST | session `last_organization_id` 更新 | 同上 |
| `/system/organization/switch-option` | GET | 仅返回当前用户有权限的组织 | 同上 |
| `/system/organization/list` | POST | 分页、筛选 | 同上 |
| `/system/project/add` | POST | 项目创建 + 初始化 | `SystemProjectControllerTests` |
| `/license/validate` | GET | `status=valid` | `LicenseControllerTests` |
| `/system/test-resource-pool/add` | POST | 无配额错误 | `TestResourcePoolControllerTests` |
| `/system/user/add` | POST | 第 6+ 用户成功 | `SimpleUserService` 相关测试 |

**已有 ControllerTests（28 个）**：`SystemOrganizationControllerTests`、`SystemProjectControllerTests`、`LicenseControllerTests`、`LoginControllerTests`、`TestResourcePoolControllerTests`、`OrganizationControllerTests`、`OrganizationProjectControllerTests` 等。

**单元测试扩展建议**：

- `CommunityLicenseServiceImpl` / `CommunityUserXpackServiceImpl`（已有）
- `OrganizationInitService`（新建，建议补）
- `OrganizationService.add()`（新建，建议补）

---

### 4.2 组织设置（organization-scoped，system-setting 子集）

**范围**：组织成员、组织项目、组织模板、组织日志、服务集成

| 子模块 | 前端路径 | API 前缀 |
|--------|----------|----------|
| 组织成员 | `/setting/organization/member` | `/organization/member/*` |
| 组织项目 | `/setting/organization/project` | `/organization/project/*` |
| 组织模板 | `/setting/organization/template` | `/organization/template/*` |
| 组织日志 | `/setting/organization/log` | `/organization/operation-log/*` |

#### 功能测试点

| ID | 场景 | 优先级 |
|----|------|--------|
| ORG-F01 | 组织管理员仅能看到本组织数据 | P0 |
| ORG-F02 | 组织项目 CRUD（与系统级项目不冲突） | P1 |
| ORG-F03 | 组织成员邀请（邮件） | P2 |
| ORG-F04 | 组织模板与新建组织初始化数据一致 | P1 |

#### UI 测试点

| ID | 场景 |
|----|------|
| ORG-U01 | 切换组织后，组织设置菜单仅显示当前组织 |
| ORG-U02 | 组织项目页「创建项目」流程完整 |

#### 接口测试点

| 测试类 | 覆盖 |
|--------|------|
| `OrganizationProjectControllerTests` | 组织项目 CRUD |
| `OrganizationControllerTests` | 成员管理 |
| `OrganizationTemplateControllerTests` | 模板 |
| `OrganizationUserRoleControllerTests` | 组织角色 |

---

### 4.3 项目管理（project-management）

**范围**：项目权限、环境、文件、消息、日志、全局参数、模板、自定义字段

| 前端路径 | `/project-management/*` |
| API 前缀 | `/project/*`、`/organization/project/*` |

#### 功能测试点

| ID | 场景 | 优先级 |
|----|------|--------|
| PRJ-F01 | 项目环境 CRUD + 环境组 | P1 |
| PRJ-F02 | 文件管理上传/下载/关联 | P1 |
| PRJ-F03 | 项目模板、自定义字段 | P1 |
| PRJ-F04 | 项目成员与角色 | P1 |
| PRJ-F05 | 全局参数、自定义函数 | P2 |
| PRJ-F06 | 消息通知配置 | P2 |

#### UI 测试点

| ID | 场景 |
|----|------|
| PRJ-U01 | 项目设置各 Tab 可访问 |
| PRJ-U02 | 环境管理列表与编辑抽屉 |
| PRJ-U03 | 文件管理树形目录与预览 |

#### 接口测试点

**已有 ControllerTests（18 个）**：`ProjectControllerTests`、`EnvironmentControllerTests`、`FileManagementControllerTests`、`ProjectTemplateControllerTests`、`GlobalParamsControllerTests` 等。

```bash
mvn test -pl backend/services/project-management
```

---

### 4.4 用例管理（case-management）

**范围**：功能用例、用例评审、脑图、附件、AI 用例

| 前端路径 | `/case-management/*` |

#### 功能测试点

| ID | 场景 | 优先级 |
|----|------|--------|
| CASE-F01 | 用例 CRUD + 模块树 | P1 |
| CASE-F02 | 用例评审流程（发起/通过/驳回） | P1 |
| CASE-F03 | 用例关联需求、缺陷 | P1 |
| CASE-F04 | 回收站恢复/彻底删除 | P2 |
| CASE-F05 | AI 生成用例 | P3 |

#### UI 测试点

| ID | 场景 |
|----|------|
| CASE-U01 | 用例列表筛选、列配置、批量操作 |
| CASE-U02 | 用例详情抽屉/页面字段完整性 |
| CASE-U03 | 脑图编辑器加载与保存 |

#### 接口测试点

**已有 ControllerTests（14 个）**：`FunctionalCaseControllerTests`、`CaseReviewControllerTests`、`FunctionalCaseModuleControllerTests` 等。

```bash
mvn test -pl backend/services/case-management
```

---

### 4.5 接口测试（api-test）

**范围**：接口定义、调试、场景、Mock、报告

| 前端路径 | `/api-test/*` |

#### 功能测试点

| ID | 场景 | 优先级 |
|----|------|--------|
| API-F01 | 接口定义 CRUD + 模块树 | P1 |
| API-F02 | 接口调试执行并返回结果 | P1 |
| API-F03 | 场景编排与执行 | P1 |
| API-F04 | Mock 期望与匹配 | P2 |
| API-F05 | 接口报告生成与分享 | P2 |
| API-F06 | 定时任务执行 | P2 |

#### UI 测试点

| ID | 场景 |
|----|------|
| API-U01 | 接口调试页请求/响应展示 |
| API-U02 | 场景步骤拖拽编排 |
| API-U03 | 报告详情图表与断言 |

#### 接口测试点

**已有 ControllerTests（17 个）**：`ApiDefinitionControllerTests`、`ApiDebugControllerTests`、`ApiScenarioControllerTests`、`ApiDefinitionMockControllerTests` 等。

```bash
mvn test -pl backend/services/api-test
```

---

### 4.6 测试计划（test-plan）

**范围**：计划创建、关联用例/接口/场景、执行、报告

| 前端路径 | `/test-plan/*` |

#### 功能测试点

| ID | 场景 | 优先级 |
|----|------|--------|
| PLAN-F01 | 测试计划 CRUD | P1 |
| PLAN-F02 | 关联功能用例/API 用例/场景 | P1 |
| PLAN-F03 | 计划执行与结果汇总 | P0 |
| PLAN-F04 | 测试报告生成与分享 | P1 |
| PLAN-F05 | 计划组与批量执行 | P2 |

#### UI 测试点

| ID | 场景 |
|----|------|
| PLAN-U01 | 计划列表状态筛选 |
| PLAN-U02 | 执行页实时进度与结果 |
| PLAN-U03 | 报告页通过率图表 |

#### 接口测试点

**已有 ControllerTests（9 个）**：`TestPlanControllerTests`、`TestPlanExecuteTests`、`TestPlanReportControllerTests` 等。

```bash
mvn test -pl backend/services/test-plan
```

---

### 4.7 缺陷管理（bug-management）

**范围**：缺陷 CRUD、评论、附件、历史、关联用例

| 前端路径 | `/bug-management/*` |

#### 功能测试点

| ID | 场景 | 优先级 |
|----|------|--------|
| BUG-F01 | 缺陷 CRUD + 状态流转 | P1 |
| BUG-F02 | 缺陷关联用例 | P1 |
| BUG-F03 | 评论与 @提及 | P2 |
| BUG-F04 | 附件上传 | P2 |
| BUG-F05 | 回收站 | P2 |

#### 接口测试点

**已有 ControllerTests（6 个）**：`BugControllerTests`、`BugCommentControllerTests`、`BugAttachmentControllerTests` 等。

```bash
mvn test -pl backend/services/bug-management
```

---

### 4.8 工作台（dashboard）

**范围**：首页统计、我的创建/关注/待办

| 前端路径 | `/workbench/*` |

#### 功能测试点

| ID | 场景 | 优先级 |
|----|------|--------|
| DASH-F01 | 首页概览数据统计正确 | P1 |
| DASH-F02 | 我的待办列表与跳转 | P1 |
| DASH-F03 | 切换组织/项目后数据刷新 | P1 |

#### 接口测试点

**已有 ControllerTests（3 个）**：`DashboardFrontPageControllerTests`、`MyViewControllerTests`、`ToDoControllerTests`。

```bash
mvn test -pl backend/services/dashboard
```

---

### 4.9 组织架构扩展（待实现，P1–P3）

**范围**：部门树、企微同步、Excel 导入（task004–011）

| 阶段 | 任务 | 测试策略 |
|------|------|----------|
| P1 | task004 数据模型 + task005 查询 API | Flyway 迁移验证；部门树/成员分页/详情 API；脱敏字段 |
| P2 | task006–008 企微客户端 + 同步引擎 | 手动同步；增量/全量；用户自动创建 `org_member`；同步日志 |
| P3 | task009–011 管理前端 + Excel | UI 部门树；企微配置 Cron；Excel 导入校验 |

**前置条件**：M0（task001–003）通过后开始。

---

## 5. 跨模块场景测试（端到端）

以下场景需跨模块验证，**当前无自动化覆盖**，建议手工或 Playwright E2E：

| ID | 场景 | 涉及模块 | 步骤摘要 |
|----|------|----------|----------|
| E2E-01 | 新组织全链路 | system-setting → project → case | 创建组织 → 进入组织 → 建项目 → 建用例 → 模板/字段可用 |
| E2E-02 | 用户配额解除 | system-setting | 连续创建 10 个用户，均可登录 |
| E2E-03 | 组织切换隔离 | system-setting → 各业务模块 | 组织 A 建数据 → 切组织 B → A 数据不可见 |
| E2E-04 | 测试计划执行 | case + api + test-plan | 建计划 → 关联用例/接口 → 执行 → 报告 |
| E2E-05 | 缺陷关联用例 | bug + case | 用例详情关联缺陷 → 缺陷详情可跳转 |
| E2E-06 | 资源池执行 | system-setting + api-test | 多资源池创建 → API 场景指定池执行 |

---

## 6. 里程碑回归清单

### M0 - P0 完成（当前重点）

- [ ] `POST /system/organization/add` 成功且初始化完整
- [ ] `POST /system/organization/switch` 上下文正确
- [ ] `GET /system/organization/switch-option` 权限过滤正确
- [ ] `GET /license/validate` 返回 valid
- [ ] 用户创建无 5 人上限
- [ ] 资源池无单池限制
- [ ] 前端「创建组织」「进入组织」可用
- [ ] `SystemOrganizationControllerTests` 全部通过
- [ ] `CommunityLicenseServiceImplTest` / `CommunityUserXpackServiceImplTest` 通过

### M1 - P1 完成

- [ ] Flyway `department` 表迁移成功
- [ ] 部门树 API 返回正确层级
- [ ] 成员分页与详情 API
- [ ] 手机号等字段脱敏

### M2 - P2 完成

- [ ] 企微手动同步成功
- [ ] 同步后部门树与企微一致
- [ ] 新用户自动创建并绑定 `org_member`
- [ ] 同步日志与定时任务

### M3 - P3 完成

- [ ] 组织架构管理页联调
- [ ] 企微配置页（Secret + Cron）
- [ ] Excel 导入部门与用户

---

## 7. 推荐测试执行顺序

适用于全量巡检或发版前回归：

```
第 1 步：前端静态检查
  └─ npm run type:check && npm run lint

第 2 步：P0 后端接口（改造相关）
  └─ system-setting: SystemOrganizationControllerTests, LicenseControllerTests
  └─ Community*ServiceImplTest

第 3 步：P0 手工 UI
  └─ 组织创建/切换/License/用户配额/资源池

第 4 步：各模块后端接口测试（按优先级）
  └─ system-setting → project-management → case-management
  └─ api-test → test-plan → bug-management → dashboard

第 5 步：跨模块 E2E 场景（§5）

第 6 步：缺陷整理与分级（§3 模板）
```

---

## 8. UI 自动化建设建议（缺口补齐）

当前仓库 **无前端测试脚本**。建议：

| 方案 | 适用 | 说明 |
|------|------|------|
| **A. 独立 Playwright 项目** | UI 视觉测试、发版前巡检 | 参考 visual-ui-testing skill；维护 `auth-state.json` |
| **B. Vitest + @vue/test-utils** | 组件/Store 单测 | 已有 devDeps 未使用；适合 `license.ts`、`utils.ts` |
| **C. 手工测试用例表** | 短期 M0 验收 | 本文档 §4 + §6 清单即可 |

**UI 自动化优先覆盖（P0）：**

1. `/setting/system/organizationAndProject` — 创建组织、进入组织
2. `/setting/system/authorizedManagement` — License 状态
3. `/setting/system/user` — 用户创建与列表刷新
4. 组织切换后菜单与数据隔离

---

## 9. 权限与安全测试要点

| 检查项 | 方法 |
|--------|------|
| 未登录访问 | 接口返回 401；页面跳转登录 |
| 无权限访问 | 接口返回 403；按钮不可见或 disabled |
| 跨组织越权 | 用户 A 不能操作组织 B 的资源 |
| 跨项目越权 | `@CheckOwner` 注解资源归属校验 |
| CSRF | 写操作需 `csrf-token`（`BaseTest` 已封装） |
| 参数注入 | 特殊字符、超长字符串、SQL 片段 |

---

## 10. 附录

### 10.1 关键代码索引

| 用途 | 路径 |
|------|------|
| 后端测试基类 | `backend/services/system-setting/src/test/java/io/metersphere/system/base/BaseTest.java` |
| 组织初始化 | `backend/services/system-setting/.../OrganizationInitService.java` |
| 组织 API | `backend/services/system-setting/.../SystemOrganizationController.java` |
| License 社区实现 | `backend/services/system-setting/.../CommunityLicenseServiceImpl.java` |
| 前端组织 API URL | `frontend/src/api/requrls/setting/organizationAndProject.ts` |
| 前端组织切换 | `frontend/src/views/setting/utils.ts` |
| 前端 License Store | `frontend/src/store/modules/setting/license.ts` |
| 路由定义 | `frontend/src/router/routes/modules/*.ts` |
| SQL 测试夹具 | `backend/services/*/src/test/resources/dml/` |

### 10.2 已知历史缺陷（回归关注）

| 编号 | 描述 | 文档 |
|------|------|------|
| BUG001 | 创建用户成功但列表不显示 | `docs/develop_logs/buglist/2026-06-26-BUG001-创建用户成功但列表不显示.md` |

### 10.3 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-04 | 初版：全模块测试策略，含 M0–M3 清单 |

---

**【AI生成】本文档已按当前代码库与 task 文档对齐，发版前请人工审核确认测试范围与优先级。**
