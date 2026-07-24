# task001 附件 - 默认项目权限点白名单（已审签）

> **状态**：【AI生成】**已人工审签确认**（2026-07-24）  
> **对照源**：`PermissionConstants`  
> **用途**：task002 种子数据唯一依据  
> **审签结论**：系统设置-系统模块权限为**初始不授予**；后续可由管理员按需自行调整用户组权限。其余条款无异议，确认签署。

---

## 1. 角色设计

| 角色 | scope | source_id | 说明 |
|------|-------|-----------|------|
| `default_hub_project_member` | PROJECT | 默认项目 ID | 项目侧业务权限全集（按模块启用由前端/鉴权自然裁剪） |
| `default_hub_org_setting` | ORGANIZATION | 组织 ID（米多公司） | **仅**组织设置相关权限；加入默认项目时补授 |

- 加入默认项目：绑项目角色 +（若尚无等价组织角色）补授组织角色。  
- 离开默认项目：仅回收本机制授予的组织角色关系（建议关系表打 `grant_source=DEFAULT_HUB` 或单独角色 ID 可识别）。  
- 已是组织管理员：幂等，不重复授权。  

---

## 2. 排除清单（SYSTEM_* — 初始种子不授予）

以下为**落种子时的初始配置**：默认项目成员用户组**初始不包含**系统设置 → 系统模块权限。

| 常量前缀 / 示例 |
|-----------------|
| `SYSTEM_USER_ROLE:*` |
| `SYSTEM_ORGANIZATION_PROJECT:*`（系统级组织项目管理） |
| `SYSTEM_PLUGIN:*` |
| `SYSTEM_SERVICE_INTEGRATION:*` |
| `SYSTEM_USER:*` |
| `SYSTEM_TEST_RESOURCE_POOL:*` |
| `SYSTEM_PARAMETER_SETTING_*` |
| `SYSTEM_AUTH:*` |
| `SYSTEM_LOG:READ` |
| `SYSTEM_PERSONAL_API_KEY:*` |

> **审签补充（已确认）**：以上为**初始权限**边界，非永久硬编码禁止。后续管理员可在系统/组织用户组管理中，按业务需要自行增删该角色或相关用户的系统模块权限；代码侧加入/离开默认项目的自动授予逻辑仍**不以 SYSTEM_* 为默认补授项**。

实施时：凡 `PermissionConstants` 中以 `SYSTEM_` 开头的权限点，**种子与自动授予**默认排除。

---

## 3. 组织设置权限（ORGANIZATION_* — 授予组织角色）

| 常量 | 说明 |
|------|------|
| `ORGANIZATION_USER_ROLE:READ` / `READ+ADD` / `READ+UPDATE` / `READ+DELETE` | 组织用户组 |
| `ORGANIZATION_MEMBER:READ` / `READ+ADD` / `READ+INVITE` / `READ+UPDATE` / `READ+DELETE` | 组织成员 |
| `ORGANIZATION_PROJECT:READ` / `READ+ADD` / `READ+UPDATE` / `READ+DELETE` / `READ+RECOVER` | 组织项目 |
| `ORGANIZATION_PROJECT:READ+ADD_MEMBER` / `UPDATE_MEMBER` / `DELETE_MEMBER` | 组织侧项目成员 |
| `ORGANIZATION_TEMPLATE:READ` / `ADD` / `UPDATE` / `DELETE` / `ENABLE` | 组织模板 |
| `ORGANIZATION_LOG:READ` | 组织日志 |

---

## 4. 项目业务权限（PROJECT / 功能模块 — 授予项目角色）

### 4.1 通用项目设置

`PROJECT_USER:*`、`PROJECT_GROUP:*`、`PROJECT_LOG:READ`、`PROJECT_MESSAGE:*`、`PROJECT_BASE_INFO:*`、`PROJECT_ENVIRONMENT:*`、`PROJECT_VERSION:*`、`PROJECT_FILE_MANAGEMENT:*`、`PROJECT_CUSTOM_FUNCTION:*`、`PROJECT_TEMPLATE:*`、以及 `PROJECT_APPLICATION_*` 读写。

### 4.2 功能用例 / 评审

`FUNCTIONAL_CASE:READ` 及 ADD/UPDATE/DELETE/COMMENT/EXPORT/IMPORT/MINDER；  
`CASE_REVIEW:READ` 及 ADD/UPDATE/DELETE/REVIEW/RELEVANCE。

### 4.3 缺陷

`PROJECT_BUG:READ` 及 ADD/UPDATE/DELETE/EXPORT/COMMENT。

### 4.4 测试计划 / 报告

`PROJECT_TEST_PLAN:READ` 及 ADD/UPDATE/DELETE/EXECUTE/…（以 `PermissionConstants` 中 TEST_PLAN_* 全集为准）；  
`PROJECT_TEST_PLAN_REPORT:*`。

### 4.5 接口 / 场景 / 报告（若项目启用模块）

`PROJECT_API_DEBUG:*`、`PROJECT_API_DEFINITION*`、`PROJECT_API_SCENARIO:*`、`PROJECT_API_REPORT:*` 等 API 相关全集。

---

## 5. 签字栏

| 角色 | 姓名 / 渠道 | 日期 | 结论 |
|------|-------------|------|------|
| 产品 / 业务确认 | 对话审签 | 2026-07-24 | **同意按本表落种子**；SYSTEM_* 为初始不授予，后续管理员可自行修改 |
| 技术负责人 | （同审签） | 2026-07-24 | **同意** |
| 安全负责人 | （同审签） | 2026-07-24 | **同意**（其他条款无异议） |

**已审签，可启动 task002。**

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| 草案 | 2026-07-24 | 初稿待签 |
| 已审签 | 2026-07-24 | 确认 SYSTEM_* 为初始权限；管理员可后续调整；其余无异议 |
