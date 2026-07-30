# MeterSphere-Flyway迁移故障-排障与防再发

> **文档类型**：事故/排障归档 + 日常操作规范
> **适用项目**：MeterSphere（表 `metersphere_version`，脚本目录 `backend/framework/domain/src/main/resources/migration/`）
> **编写日期**：2026-07-24（2026-07-30 刷新版本占用与 SOP）
> **标注**：【AI生成】已按仓库历史故障与当前迁移目录整理；具体环境以容器日志 / `metersphere_version` 为准，需人工核对

---

## 0. 一句话结论

前端大面积 **502** / 登录「网络错误」且后端起不来时：**先查 Flyway**，不要先改前端。
修迁移只做三件事：**修 SQL（或补补偿脚本）→ 处理 `success=0` → 重启验证**；日常靠 **版本号全局唯一 + 只追加不改史 + 提交前自检** 防再发。

---

## 1. 典型现象

| 项 | 内容 |
|----|------|
| 前端表现 | 登录页卡住 / Toast「网络错误！」 |
| 网络 | `is-login` / `status` / `base-info` 等 **502 Bad Gateway** |
| 本质 | **Java 进程未就绪或启动失败**；Flyway migrate 失败时 Spring Boot 无法完成启动 → 网关 502 |
| 日志关键字 | `Flyway`、`Migration`、`metersphere_version`、`SQLException`、`Application run failed`、`Found more than one migration with version` |

> 若 Flyway 已 `Successfully applied` / up to date，仍 502 → 转查 Spring 循环依赖：`docs/summary/MeterSphere-Spring循环依赖-排障与防再发-2026-07-24.md`。

---

## 2. 配置要点（本仓库）

`backend/app/src/main/resources/commons.properties`：

```properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.locations=classpath:migration
spring.flyway.table=metersphere_version
spring.flyway.validate-on-migrate=false
```

| 要点 | 含义 |
|------|------|
| 历史表 | `metersphere_version`（非默认 `flyway_schema_history`） |
| 脚本根目录 | `classpath:migration` → `backend/framework/domain/src/main/resources/migration/` |
| `ddl/` 与 `dml/` | **共用同一套版本号空间**（`V3.7.2_N__*.sql` → 版本 `3.7.2.N`） |
| `validate-on-migrate=false` | checksum 校验较松，**仍禁止改已成功脚本**；缺文件/半改名仍会出问题 |

命名约定：

```text
migration/{x.y.z}/ddl/V{x.y.z}_{N}__描述.sql
migration/{x.y.z}/dml/V{x.y.z}_{N}__描述.sql
```

`N` 在同 major（如整个 `3.7.2`）内跨 ddl+dml **全局递增、不得重复**。

---

## 3. 根因分类（本仓库反复踩坑）

### 3.1 【高频·已实锤】版本号冲突（ddl / dml 撞车）

- **案例**：曾同时存在
  - `dml/V3.7.2_4__bug_type_custom_field.sql`
  - `ddl/V3.7.2_4__functional_case_execute_user.sql`
- **表现**：`Found more than one migration with version 3.7.2.4`，每次启动必现。
- **修复**：commit `63fd7dad8d` — 执行人 DDL 改名为 `V3.7.2_6__...`。

### 3.2 【高频】已执行失败的迁移卡死（`success=0`）

- SQL 失败后 `metersphere_version` 留下失败行；下次启动会再跑同一脚本。
- 脚本**非幂等**（如 `DROP INDEX` 已成功、后续 `MODIFY` 失败）→ 重试再挂 → **无限重启 / 持续 502**。
- 脆弱示例：`V3.7.2_8__bug_handle_user_multi.sql`（先 `DROP INDEX` 再改列）。

### 3.3 【中频】修改/重命名「已经成功应用」的旧脚本

- 已写入 `metersphere_version` 且 `success=1` 的脚本：**禁止改内容、禁止改版本号/文件名**。
- 正确做法：永远 **新增下一个 N** 的补偿脚本。

### 3.4 【中频】MySQL 索引键过长（Error 1071）

- utf8mb4 下整列索引 + 过长 VARCHAR → 1071。
- 已处理：`72647311a6` / `V3.7.2_8` — 用前缀索引 `(191)`。

### 3.5 【实锤·2026-07-24】`3.7.2.10` default hub seed 失败卡死

- **证据**：`version=3.7.2.10`、`description=default hub seed`、`success=0`。
- **根因**：`UPDATE project ... WHERE NOT EXISTS (SELECT ... FROM project ...)` → MySQL **Error 1093**（不能在 UPDATE 目标表的子查询里直接读同一张表）。
- **修复**：子查询外包一层派生表；失败环境删 `success=0` 行后重跑，或手工执行修正 SQL 后将该行标 `success=1`。

### 3.6 【实锤·2026-07-30】`agent_token_personal_mcp` 迁移导致 502

本次故障分两段：

1. **版本号冲突**：`V3.7.2_18__agent_token_personal_mcp.sql` 与 `V3.7.2_18__functional_case_last_execute_user.sql` 同版本，启动报 `Found more than one migration with version 3.7.2.18`。
   - 修复：改名为 `V3.7.2_19__agent_token_personal_mcp.sql`，并跑 `scripts/check-flyway-versions.ps1`。
2. **失败记录卡死**：重部署后日志显示 `Current version of schema metersphere: 3.7.2.19`，随后报 `Schema metersphere contains a failed migration to version 3.7.2.19 !`。这表示 Flyway 已经看到 `success=0`，不会继续打印第一次 SQL 失败原因。
   - 修复：把 `V3.7.2_19__agent_token_personal_mcp.sql` 改成基于 `information_schema + PREPARE/EXECUTE` 的幂等 DDL，不再使用 `CREATE PROCEDURE` / `DELIMITER`，降低生产库例程权限或解析差异风险。
   - 恢复：确认该版本尚未 `success=1` 后，删除 `version='3.7.2.19' AND success=0` 的失败行，再部署包含修复提交的镜像并重启。

结论：新增迁移脚本默认不要引入 `CREATE PROCEDURE`、`CREATE FUNCTION`、`DELIMITER`。确需使用时，必须提前确认目标库用户具备例程权限，并在排障文档里说明回滚/清理方式。

---

## 4. 标准修复 SOP

### 4.1 立刻确认是不是 Flyway

1. 看后端容器 / 进程日志上述关键字。
2. 前端 502 + 后端起不来 → **先别改前端**，先看迁移。

### 4.2 查版本表

```sql
-- 失败记录 + 近期 3.7.2
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

### 4.3 按类型处置

| 诊断 | 处置 |
|------|------|
| 版本号冲突（Found more than one…） | **未上线**：改其中一个脚本版本号；**已有环境已跑过错误版本**：见 §4.4，禁止只改文件名糊弄 |
| `success=0` 且脚本可幂等 | 修 SQL（仅限**尚未成功**的那条）→ 删除失败行或 `flyway repair` → 重启 |
| `success=0` 且库已部分变更 | 手工把库补到目标态 → 将失败行标成功 / repair → **另写补偿迁移**说明差异 |
| 1071 / 语法错误 / 1093 | 修当前失败脚本（若从未成功）或 **新增下一号** 补偿；不要改历史成功脚本 |
| 例程权限或 `DELIMITER` 解析问题 | 避免迁移中 `CREATE PROCEDURE/FUNCTION`；改用 `information_schema + PREPARE/EXECUTE` 幂等 DDL |
| 仅缺新表/新列 | 确认新脚本版本号全局唯一后重启即可 |

#### 失败行处理示例（需 DBA/人工确认，禁止直接用于生产）

```sql
-- 仅删除「明确失败、可重跑」的那一行（把版本换成实际失败号）
DELETE FROM metersphere_version
WHERE version = '3.7.2.10' AND success = 0;

-- 或：库已手工修好、确认与脚本目标态一致后，标记成功（更谨慎，优先用 repair）
-- UPDATE metersphere_version SET success = 1 WHERE version = '3.7.2.10' AND success = 0;
```

重启应用，确认日志出现迁移成功 / up to date，再验证登录与关键接口。

### 4.4 危险操作红线

- ❌ 改已成功应用的 `V*.sql` 内容或版本号
- ❌ 生产直接 `DELETE FROM metersphere_version` **全表**
- ❌ ddl / dml 使用相同 `V3.7.2_N`
- ❌ 把「半成功」的非幂等脚本改完后不处理 `success=0` 就反复重启
- ❌ 默认在迁移脚本里使用 `CREATE PROCEDURE` / `CREATE FUNCTION` / `DELIMITER`（生产库权限和 Flyway 解析差异风险高）
- ✅ 只追加 `V3.7.2_{max+1}`（**跨 ddl+dml 一起取 max**）
- ✅ 破坏性 DDL 尽量幂等：`IF NOT EXISTS` / 信息_schema 判断
- ✅ UPDATE 同表时用派生表规避 Error 1093
- ✅ 条件 DDL 优先用 `information_schema + PREPARE/EXECUTE`，避免依赖例程权限

---

## 5. 版本号分配约定（强制）

当前 `3.7.2` 已占用（**2026-07-30 扫描仓库**；自检脚本建议下一号为 **20**）：

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
| 14 | **dml** | align_hub_role_permissions |
| 15 | **dml** | admin_login_entry_switch |
| 16 | ddl | org_sync_email_conflict |
| 17 | ddl | agent_token_project_ids |
| 18 | ddl | functional_case_last_execute_user |
| 19 | ddl | agent_token_personal_mcp |

**下一条新迁移必须从 `20` 起**，无论放在 ddl 还是 dml。取号前以自检脚本为准：

```powershell
powershell -File scripts/check-flyway-versions.ps1
```

---

## 6. 防再发清单（改造 / Code Review / 合入）

### 6.1 写脚本前

- [ ] 扫描 `migration/**/V*.sql`，确认版本号不与 ddl/dml 冲突
- [ ] 跑 `scripts/check-flyway-versions.ps1`（重复则 exit ≠ 0）
- [ ] 不修改已发布脚本；只追加
- [ ] 宽列 + 索引：优先前缀索引，避免 1071
- [ ] 多语句迁移：考虑失败重入；避免「半成功」不可重跑
- [ ] 对同表 `UPDATE ... WHERE NOT EXISTS (SELECT ... 同表)` 使用派生表，避免 1093
- [ ] 条件 DDL 不依赖 `CREATE PROCEDURE/FUNCTION`；优先 `information_schema + PREPARE/EXECUTE`

### 6.2 合入后

- [ ] 看目标环境启动日志 Flyway 段是否 `Successfully applied`
- [ ] 抽查 `metersphere_version` 无 `success=0`
- [ ] 若出现 502：先查后端 Flyway，再查网关；Flyway 正常仍 502 → 查 `form a cycle`

### 6.3 工程护栏（已落地）

| 项 | 路径 |
|----|------|
| 版本唯一性自检 | `scripts/check-flyway-versions.ps1` |
| Cursor 规则（改 SQL 时提醒） | `.cursor/rules/flyway-migration.mdc` |
| 502 与循环依赖分流 | `.cursor/rules/spring-circular-dependency.mdc` |

建议：本地 / CI 在包含 `migration/**/*.sql` 变更的 PR 上强制跑自检脚本。

---

## 7. 关联记录

| 类型 | 引用 |
|------|------|
| 版本冲突修复 | commit `63fd7dad8d` |
| 1071 修复 | commit `72647311a6`，脚本 `V3.7.2_8__bug_handle_user_multi.sql` |
| 2026-07-30 Agent 迁移版本冲突 | commit `4ecc60b317`，脚本改为 `V3.7.2_19__agent_token_personal_mcp.sql` |
| 2026-07-30 Agent 迁移例程风险修复 | commit `7d3c8eb686`，移除 `CREATE PROCEDURE/DELIMITER` |
| 循环依赖（迁移恢复后仍 502） | `MeterSphere-Spring循环依赖-排障与防再发-2026-07-24.md`（`655892adaa`） |
| 自检脚本 | `scripts/check-flyway-versions.ps1` |
| Cursor 规则 | `.cursor/rules/flyway-migration.mdc` |
| DB 总览 | `docs/summary/MeterSphere-数据库结构与查询指南-2026-07-29.md` |

---

## 8. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-07-24 | 初版：结合 502 现象、历史 3.7.2.4 撞车与 1071 案例归档 |
| 2026-07-24 | 补充：迁移恢复后仍 502 时转查循环依赖归档；实锤 3.7.2.10 Error 1093 |
| 2026-07-30 | 刷新 3.7.2 占用至 N=19；下一号 20；强化 SOP / 防再发清单与一句话结论 |
| 2026-07-30 | 补充 Agent Token / MCP 迁移事故：`V3.7.2_18` 撞车、`V3.7.2_19 success=0`、迁移禁用例程的防再发要求 |
