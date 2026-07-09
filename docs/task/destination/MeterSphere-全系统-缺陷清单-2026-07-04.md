# MeterSphere 全系统缺陷清单

> **文档类型**：缺陷汇总 / 测试输出  
> **关联策略**：[MeterSphere-全系统-测试策略-2026-07-04.md](MeterSphere-全系统-测试策略-2026-07-04.md)  
> **汇总日期**：2026-07-04  
> **版本**：v1.1  
> **测试环境**：前端 `http://localhost:5173`（Vite dev，`VITE_MS_UNLIMITED=true`），后端 `http://localhost:8081`，账号 `admin/metersphere`

---

## 0. 文档说明

本文档整合 **2026-07-04** 依据全系统测试策略执行的各模块测试记录中的缺陷内容，并纳入策略文档 §10.2 所列历史缺陷及已修复项（供回归关注）。

**缺陷分级**（同测试策略 §3）：

| 级别 | 定义 |
|------|------|
| **P0-阻断** | 核心流程不可用、数据丢失、安全越权 |
| **P1-严重** | 主要功能异常但有绕行 |
| **P2-一般** | 次要功能、边界场景、测试/文档/环境问题 |
| **P3-轻微** | UI 样式、文案、体验、文档笔误 |

**状态说明**：`待修复` / `已修复（回归）` / `环境阻塞` / `待确认`

---

## 1. 测试执行概况

| 模块 | 测试记录来源 | 接口集成测试 | 在线 API / UI | 缺陷数（开→闭） |
|------|--------------|--------------|---------------|-----------------|
| 系统设置 §4.1 | Cursor Agent，2026-07-04 16:01 | ❌ Docker 阻塞 | ✅ P0 核心通过 | 4 → 2 |
| 组织设置 §4.2 | Cursor Agent，2026-07-04 16:02 | ❌ Docker 阻塞 | ✅ 16/16 API，UI 通过 | 1 → 0 |
| 项目管理 §4.3 | Cursor Agent，2026-07-04 16:03 | ❌ 276 ERROR | ✅ 20/23 API，UI 通过 | 1 → 0 |
| 用例管理 §4.4 | Cursor Agent，2026-07-04 16:02 | ❌ Docker 阻塞 | ✅ 大部分 UI 通过 | 1 → 0 |
| 接口测试 §4.5 | Cursor Agent，2026-07-04 16:03 | ❌ 251/267 ERROR | — | 2 → 1 |
| 测试计划 §4.6 | Cursor Agent，2026-07-04 16:03 | ❌ 123 ERROR | ⚠️ 部分通过 | 2 → 0 |
| 缺陷管理 §4.7 | — | ⏭ 未执行 | ⏭ 未执行 | 0 |
| 工作台 §4.8 | Cursor Agent，2026-07-04 16:03 | ❌ 16 ERROR | ⚠️ 接口通过，UI 异常 | 2 → 1 |

> 各模块测试于 2026-07-04 下午在 Cursor Agent 对话中执行，依据 `MeterSphere-全系统-测试策略-2026-07-04.md` 分模块走查。  
> **2026-07-04 晚**：7 项代码缺陷已修复，详见 §5.2；发版前需对 P1/P2 已修复项做 UI/API 回归。

**共性环境阻塞**：后端 MockMvc 集成测试依赖 Testcontainers（MySQL / Redis / Kafka / MinIO），当前 Maven/JUnit 进程内无法连接 Docker API，导致 **全模块 ControllerTests 未能作为发版依据**。详见 **ENV-001**。

---

## 2. 缺陷统计

| 级别 | 待修复 | 环境阻塞 | 待确认 | 已修复（回归） |
|------|--------|----------|--------|----------------|
| P0 | 0 | 1 | 0 | 1 |
| P1 | 0 | 0 | 0 | 3 |
| P2 | 1 | 0 | 0 | 3 |
| P3 | 1 | 0 | 1 | 1 |
| **合计** | **2** | **1** | **1** | **14（含 BUG001 + 6 历史 UI/SQL + 7 本轮）** |

---

## 3. 待修复缺陷（按优先级）

### ENV-001 【P0-阻断】【环境阻塞】Testcontainers 无法初始化集成测试环境

- **模块**：全模块后端集成测试
- **类型**：环境 / 测试基础设施
- **状态**：环境阻塞
- **复现步骤**：
  1. 确保 Docker Desktop 已启动（命令行 `docker ps` 可正常）
  2. 执行任一模块的 `mvn test`，例如：
     ```bash
     .\mvnw.cmd test -pl backend/services/system-setting
     .\mvnw.cmd test -pl backend/services/api-test -DskipAntRunForJenkins=true
     ```
- **期望结果**：Testcontainers 拉起 MySQL/Redis/Kafka/MinIO，Spring 上下文启动，ControllerTests 执行
- **实际结果**：`Could not find a valid Docker environment` / `Docker must be present in order for testcontainers to work properly!`；Maven 进程内连接 Docker API 返回 HTTP 400
- **影响范围**：§4.1–§4.8 全部 MockMvc 集成测试；M0 清单中 `SystemOrganizationControllerTests` 等项无法自动化验收
- **建议修复**：
  1. Docker Desktop 启用 TCP 暴露（`tcp://localhost:2375`），设置 `DOCKER_HOST` 后重跑
  2. 在 IntelliJ / 原生 PowerShell（非受限子进程）中执行
  3. 确认 `.testcontainers.properties` 与 `dockerDesktopLinuxEngine` 管道配置
- **测试类/用例**：各模块 `*ControllerTests`（约 97 类）

---

### BUG-SYS-003 【P2】`mvn test` 依赖 `frontend/dist` 未在策略中强调

- **模块**：构建 / 测试
- **类型**：环境
- **状态**：待修复（文档/流程）
- **复现步骤**：未构建前端直接跑 `mvn test -pl backend/services/system-setting`
- **期望结果**：后端测试可独立执行或文档明确前置条件
- **实际结果**：`frontend/dist does not exist`（antrun 复制静态资源失败）
- **建议**：测试前先 `cd frontend && npm run build`，或使用 `-DskipAntRunForJenkins=true`；更新测试策略 §2.2

---

### BUG-DOC-001 【P3】测试策略文档路径与代码不一致（多处）

- **模块**：文档 / 多模块 UI
- **类型**：文档
- **状态**：待修复
- **不一致项**：

| 文档写法 | 实际路径 / API |
|----------|----------------|
| `/setting/system/organizationAndProject` | `/setting/system/organization-and-project` |
| `/system/user/list` | `/system/user/page` |
| `/system/test-resource-pool/*` | `/test/resource/pool/*` |
| `/workbench/*`（§4.8） | `/workstation/*` |
| `#/case-management/caseReview`（若外部引用） | `#/case-management/caseManagementReview`（代码已加重定向，见 BUG-CASE-001） |

- **关联代码**：
  - `frontend/src/router/routes/modules/setting.ts:87`
  - `frontend/src/router/routes/modules/workbench.ts`

---

### BUG-SYS-004 【P3】系统设置 UI 走查未覆盖「进入组织」完整流程

- **模块**：系统设置
- **类型**：测试覆盖缺口（非确认缺陷）
- **状态**：待确认
- **说明**：M0 清单「前端进入组织」仅验证列表页按钮可见，未点击「进入」验证菜单切换（SYS-U04）
- **建议**：补测 `enterOrganization()` 后左侧菜单与组织上下文切换

---

## 4. 待确认 / 观察项

| 编号 | 模块 | 说明 | 优先级 |
|------|------|------|--------|
| OBS-CASE-001 | 用例管理 | 自动化填表创建用例时 Toast 显示「更新成功」，列表未确认新增（可能 Vue 双向绑定未生效）；**建议手工复测** CASE-F01 | P2 |
| OBS-PLAN-001 | 测试计划 | PLAN-F02 关联用例流程自动化未成功，需手工验证脑图关联 | P1 |
| OBS-DASH-001 | 工作台 | DASH-F02/F03 UI 因会话不稳定未完整跑通；接口层 12/12 通过 | P1 |

---

## 5. 已修复缺陷（回归关注）

### 5.1 历史已修复

#### BUG001 【P0→已修复】创建用户成功但列表不显示

- **状态**：已修复（2026-06-26），**M0 / SYS-U06 / SYS-F08 回归必测**
- **现象**：前端提示创建成功，用户列表无新数据；早期阶段伴随 NPE / License null
- **根因**：Community 版 `UserXpackService` Bean 未注册；桩实现 `return 0` 但未落库
- **修复**：`CommunityXpackConfiguration` + `CommunityUserXpackServiceImpl` 完整持久化
- **详细记录**：[docs/develop_logs/buglist/2026-06-26-BUG001-创建用户成功但列表不显示.md](../../develop_logs/buglist/2026-06-26-BUG001-创建用户成功但列表不显示.md)
- **2026-07-04 回归结果**：系统设置模块测试 SYS-U06 ✅，用户列表 14 条，未见异常

#### 历史 UI / SQL 修复项（2026-06-22 前）

以下来自 [docs/summary/前端与SQL问题调整.md](../../summary/前端与SQL问题调整.md)，**已修复**，发版前建议 spot check：

| 序号 | 现象 | 关联测试点 | 状态 |
|------|------|------------|------|
| H-01 | 登录页 banner / favicon 裂图 | — | 已修复 |
| H-02 | 工作台/测试计划 SQL 报错（`ExtTestPlanBugMapper` GROUP BY） | DASH-F01、PLAN | 已修复 |
| H-03 | 组织成员页 SQL 报错（`ExtOrganizationMapper`） | ORG-F01 | 已修复 |
| H-04 | 用例模块树横向滚动条 | CASE-U01 | 已修复 |
| H-05 | 侧边栏「系统设置」与子菜单重叠 | SYS-U* | 已修复 |
| H-06 | 登录后首次加载慢（ThirdDemandDrawer 同步加载） | — | 已优化 |

---

### 5.2 2026-07-04 代码修复（本轮，回归必测）

| 编号 | 级别 | 修复摘要 | 主要变更 |
|------|------|----------|----------|
| BUG-PLAN-001 | P1 | 执行历史 Tab 为空 | `ExtTestPlanMapper.listHis`：`exec_task.deleted=0`；UNION 已完成 `test_plan_report` | [develop_logs/buglist/2026-07-04-BUG-PLAN-001-执行历史Tab为空.md](./develop_logs/buglist/2026-07-04-BUG-PLAN-001-执行历史Tab为空.md) |
| BUG-PLAN-002 | P1 | 生成报告后列表为空 | `TestPlanReportService`：默认 `passThreshold=100`；`postHandleReport` 空值保护，避免 `result_status='-'` | [develop_logs/buglist/2026-07-04-BUG-PLAN-002-报告列表为空.md](./develop_logs/buglist/2026-07-04-BUG-PLAN-002-报告列表为空.md) |
| BUG-DASH-001 | P1 | 工作台 orgId 为空不加载 | `user/index.ts` 回退 `lastOrganizationId`；`homePage/index.vue` 初始化布局 | [develop_logs/buglist/2026-07-04-BUG-DASH-001-工作台orgId为空.md](./develop_logs/buglist/2026-07-04-BUG-DASH-001-工作台orgId为空.md) |
| BUG-SYS-001 | P2 | 单元测试 NPE | `CommunityUserXpackServiceImplTest` Mock `IDGenerator` | [develop_logs/buglist/2026-07-04-BUG-SYS-001-单元测试IDGenerator-NPE.md](./develop_logs/buglist/2026-07-04-BUG-SYS-001-单元测试IDGenerator-NPE.md) |
| BUG-SYS-002 | P2 | 未认证 API 返回 500 | 新增 `MsAuthenticationFilter`，JSON 请求返回 HTTP 401 | [develop_logs/buglist/2026-07-04-BUG-SYS-002-未认证API返回500.md](./develop_logs/buglist/2026-07-04-BUG-SYS-002-未认证API返回500.md) |
| BUG-API-001 | P2 | JDK 21 XStream 失败 | 根 `pom.xml` Surefire `--add-opens java.base/java.util=ALL-UNNAMED` | [develop_logs/buglist/2026-07-04-BUG-API-001-JDK21-XStream解析失败.md](./develop_logs/buglist/2026-07-04-BUG-API-001-JDK21-XStream解析失败.md) |
| BUG-CASE-001 | P3 | `caseReview` 路由 404 | `caseManagement.ts` 重定向至 `caseManagementReview` | [develop_logs/buglist/2026-07-04-BUG-CASE-001-caseReview路由404.md](./develop_logs/buglist/2026-07-04-BUG-CASE-001-caseReview路由404.md) |

**单元测试验证（2026-07-04）**：

- `CommunityUserXpackServiceImplTest`：4/4 通过
- `MsHTTPElementTest`：通过（`-DskipAntRunForJenkins=true`）

**UI/API 回归建议**：

1. 测试计划 `v1.0版本发布计划` → 执行历史 / 生成报告 → 报告 Tab
2. 清除 `currentOrgId` → `#/workstation/home` 应展示卡片
3. 无 Token 调用 `POST /organization/member/list` → HTTP 401
4. 访问 `#/case-management/caseReview` → 自动跳转评审页

---

## 6. 按模块索引

| 模块 | 待修复 / 阻塞 | 已修复（本轮） |
|------|---------------|----------------|
| 环境（全模块） | ENV-001 | — |
| 系统设置 | BUG-SYS-003、BUG-SYS-004（待确认）、BUG-DOC-001（部分） | BUG-SYS-001、BUG-SYS-002 |
| 组织设置 | — | BUG-SYS-002 |
| 项目管理 | — | BUG-SYS-002 |
| 用例管理 | OBS-CASE-001 | BUG-CASE-001 |
| 接口测试 | ENV-001 | BUG-API-001 |
| 测试计划 | OBS-PLAN-001 | BUG-PLAN-001、BUG-PLAN-002 |
| 工作台 | OBS-DASH-001、BUG-DOC-001（部分） | BUG-DASH-001 |
| 缺陷管理 | —（未测） | — |

---

## 7. 修复优先级建议

```
第 1 步（阻断测试能力）：修复 ENV-001 — 恢复 Docker/Testcontainers，补跑全模块 ControllerTests

第 2 步（回归本轮已修复项）：BUG-PLAN-001/002、BUG-DASH-001、BUG-SYS-002、BUG-CASE-001

第 3 步（文档/流程）：BUG-SYS-003、BUG-DOC-001

第 4 步（待确认）：BUG-SYS-004、OBS-* 观察项手工复测

回归必测：BUG001（用户创建列表）、M0 组织/License/配额项、§5.2 七项
```

---

## 8. 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-04 | 整合 7 个模块测试记录 + BUG001 + 历史修复项 |
| v1.1 | 2026-07-04 | 7 项代码缺陷标记已修复（§5.2）；统计与模块索引同步更新 |

---

**【AI生成】本文档由 2026-07-04 各模块测试报告自动汇总，v1.1 同步本轮代码修复状态；发版前请人工审核确认缺陷状态与优先级。**
