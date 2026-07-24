# MeterSphere-Flyway迁移故障-排障与防再发-2026-07-24

> **文档类型**：事故/排障归档  
> **适用项目**：MeterSphere（表 `metersphere_version`，脚本目录 `backend/framework/domain/src/main/resources/migration/`）  
> **编写日期**：2026-07-24  
> **标注**：【AI生成】已按仓库历史故障与本次现象整理；具体环境以容器日志 / `metersphere_version` 为准需人工核对  

---

## 1. 本次现象

| 项 | 内容 |
|----|------|
| 前端表现 | 登录页「正在前往米多星球登录…」；连续 Toast「网络错误！」 |
| 网络 | `is-login` / `status` / `base-info` 等接口 **502 Bad Gateway**（nginx 反代后端无有效响应） |
| 本质 | **Java 进程未就绪或启动失败**；Flyway 迁移失败是本仓库最常见根因之一（Spring Boot 在 migrate 失败时无法完成启动 → 网关 502） |

> 说明：灰度 CDS 分支 `metersphere-main` 在归档时仍跑旧 commit，日志中应用已在跑业务 SQL；**本次截图更符合「新迁移合入后某环境重启/重部署」场景**。排障仍按 Flyway 标准路径执行。

---

## 2. 根因分析（本仓库反复踩坑的几类）

Flyway 配置要点（`backend/app/src/main/resources/commons.properties`）：

```properties
spring.flyway.locations=classpath:migration
spring.flyway.table=metersphere_version
spring.flyway.validate-on-migrate=false
```

`ddl/` 与 `dml/` **共用同一套版本号空间**（文件名 `V3.7.2_N__*.sql` → 版本 `3.7.2.N`）。

### 2.1 【高频·已实锤】版本号冲突（ddl / dml 撞车）

- **案例**：曾同时存在  
  - `dml/V3.7.2_4__bug_type_custom_field.sql`  
  - `ddl/V3.7.2_4__functional_case_execute_user.sql`  
- **表现**：启动报 `Found more than one migration with version 3.7.2.4`（或同类），**每次启动必现**，直到改名。  
- **修复提交**：`63fd7dad8d` — 将执行人 DDL 重命名为 `V3.7.2_6__functional_case_execute_user.sql`。

### 2.2 【高频】已执行失败的迁移卡死（success=0）

- 某条 SQL 执行失败后，`metersphere_version` 留下失败记录；下次启动会再次执行同一脚本。  
- 若脚本**非幂等**（如 `DROP INDEX idx` 已成功、后续 `MODIFY` 失败），重试时 `DROP` 再失败 → **无限重启失败 / 持续 502**。  
- 相关脆弱脚本示例：`V3.7.2_8__bug_handle_user_multi.sql`（先 `DROP INDEX` 再改列；无 `IF EXISTS` 保护）。

### 2.3 【中频】修改/重命名「已经成功应用」的旧脚本

- 已成功写入 `metersphere_version` 的脚本，**禁止再改内容、禁止改版本号/文件名**。  
- 本仓库虽 `validate-on-migrate=false`（checksum 校验较松），但缺文件 / 版本空洞 / 半改名仍会导致行为难预期。  
- 正确做法：永远 **新增下一个版本号** 的补偿脚本。

### 2.4 【中频】MySQL 索引键过长（Error 1071）

- utf8mb4 下整列索引 + 过长 VARCHAR 触发 1071。  
- 已处理案例：`72647311a6` / `V3.7.2_8` — `handle_user` 扩列后改用前缀索引 `(191)`。

### 2.6 【本次实锤·2026-07-24】`3.7.2.10` default hub seed 失败卡死

- **证据**：`metersphere_version` 中 `version=3.7.2.10`、`description=default hub seed`、`success=0`；`3.7.2.1`～`3.7.2.9` 均为成功；`3.7.2.11/12` 未执行。  
- **根因**：`V3.7.2_10__default_hub_seed.sql` 中 `UPDATE project ... WHERE NOT EXISTS (SELECT ... FROM project ...)` 触发 MySQL **Error 1093**（不能在 UPDATE 目标表的子查询里直接读同一张表）。  
- **后果**：后端启动在 Flyway 处失败 → 网关 **502**；每次重启重复失败。  
- **修复**：子查询外包一层派生表；失败环境删除 `success=0` 行后重跑，或手工执行修正 SQL 后将该行标 `success=1`。  

---

## 3. 标准解决方法（SOP）

### 3.1 立刻确认是不是 Flyway

1. 看后端容器 / 进程日志关键字：`Flyway`、`Migration`、`metersphere_version`、`SQLException`、`Application run failed`。  
2. 前端 502 + 后端起不来 → 先别改前端，先看迁移。

### 3.2 查版本表

```sql
-- 失败与近期 3.7.2
SELECT installed_rank, version, description, success, checksum, installed_on, installed_by
FROM metersphere_version
WHERE success = 0 OR version LIKE '3.7.2%'
ORDER BY installed_rank;

-- 当前最大版本
SELECT version, description, success, installed_on
FROM metersphere_version
ORDER BY installed_rank DESC
LIMIT 20;
```

### 3.3 按类型处置

| 诊断 | 处置 |
|------|------|
| 版本号冲突（Found more than one…） | **未上线**：改其中一个脚本版本号；**已有环境已跑过错误版本**：见 §3.4，禁止只改文件名糊弄 |
| `success=0` 且脚本可幂等 | 修 SQL 后（仅限**尚未成功**的那条）→ 删除失败行或 `flyway repair` → 重启 |
| `success=0` 且库已部分变更 | 手工把库补到目标态 → 将失败行标成功 / repair → **另写补偿迁移**说明 |
| 1071 / 语法错误 | 新增下一号脚本修复；不要改历史成功脚本 |
| 仅缺新表/新列 | 确认新脚本版本号全局唯一后重启即可 |

### 3.4 危险操作红线

- ❌ 改已成功应用的 `V*.sql` 内容或版本号  
- ❌ 生产直接 `DELETE FROM metersphere_version` 全表  
- ❌ ddl / dml 使用相同 `V3.7.2_N`  
- ✅ 只追加 `V3.7.2_{max+1}`（**跨 ddl+dml 一起取 max**）  
- ✅ 破坏性 DDL 尽量幂等：`IF NOT EXISTS` / 信息_schema 判断（按 MySQL 版本能力）

### 3.5 本地提交前自检

```powershell
powershell -File scripts/check-flyway-versions.ps1
```

有重复版本号则退出码 ≠ 0，应在 commit 前改掉。

---

## 4. 版本号分配约定（强制）

当前 `3.7.2` 已占用（归档日）：

| N | 路径 | 说明 |
|---|------|------|
| 1 | ddl | test_plan_document |
| 2 | ddl | functional_test_report |
| 3 | ddl | functional_case_xmind_file |
| 4 | **dml** | bug_type_custom_field |
| 5 | **dml** | clear_wecom_user_password |
| 6 | ddl | functional_case_execute_user（曾误用 4，已改名） |
| 7 | ddl | bug_handle_close_time |
| 8 | ddl | bug_handle_user_multi |
| 9 | ddl | default_hub |
| 10 | **dml** | default_hub_seed |
| 11 | ddl | default_hub_import_audit |
| 12 | ddl | resource_edit_lock_snapshot |
| 13 | **dml** | backfill_project_module_setting |
| 14 | **dml** | align_hub_role_permissions（枢纽角色对齐 org_admin/project_admin） |

**下一条新迁移必须从 `15` 起**，无论放在 ddl 还是 dml。

---

## 5. 防再发清单（改造 / Code Review）

- [ ] 新增脚本前：扫描 `migration/**/V*.sql`，确认版本号不与 ddl/dml 冲突  
- [ ] 跑 `scripts/check-flyway-versions.ps1`  
- [ ] 不修改已发布脚本；只追加  
- [ ] 宽列 + 索引：优先前缀索引，避免 1071  
- [ ] 多语句迁移：考虑失败重入；避免「半成功」不可重跑  
- [ ] 合入后：看一次目标环境启动日志中 Flyway 段是否 `Successfully applied`  
- [ ] 若出现 502：先查后端 Flyway，再查网关；Flyway 正常仍 502 → 查 `form a cycle`（见循环依赖归档）  

---

## 6. 关联记录

| 类型 | 引用 |
|------|------|
| 版本冲突修复 | commit `63fd7dad8d` |
| 1071 修复 | commit `72647311a6`，脚本 `V3.7.2_8__bug_handle_user_multi.sql` |
| 本次新表 | `V3.7.2_12__resource_edit_lock_snapshot.sql`（auto_save_undo） |
| 自检脚本 | `scripts/check-flyway-versions.ps1` |
| Cursor 规则 | `.cursor/rules/flyway-migration.mdc` |
| 同日后续故障 | Flyway 修复后仍 502 → Spring 循环依赖，见 `MeterSphere-Spring循环依赖-排障与防再发-2026-07-24.md`（`655892adaa`） |

---

## 7. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-24 | 初版：结合 502 现象、历史 3.7.2.4 撞车与 1071 案例归档 |
| 2026-07-24 | 补充：迁移恢复后仍 502 时转查循环依赖归档 |
