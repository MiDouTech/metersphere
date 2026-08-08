# -*- coding: utf-8 -*-
"""Generate functional-case focused DB doc from schema JSON."""
from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path

SCHEMA = Path(r"C:\SoftWare\JetBrains\metersphere\docs\summary\MeterSphere-数据库结构-schema-2026-07-29.json")
OUT = Path(r"C:\SoftWare\JetBrains\metersphere\docs\summary\MeterSphere-测试用例相关表结构-2026-07-30.md")
DATE = "2026-07-30"

# Ordered sections: (section_title, table_names)
SECTIONS = [
    (
        "用例核心",
        [
            "functional_case",
            "functional_case_blob",
            "functional_case_module",
            "functional_case_custom_field",
        ],
    ),
    (
        "用例附属",
        [
            "functional_case_attachment",
            "functional_case_comment",
            "functional_case_follower",
            "functional_case_demand",
            "functional_case_relationship_edge",
            "functional_case_test",
            "functional_case_xmind_file",
            "functional_minder_extra_node",
        ],
    ),
    (
        "用例评审",
        [
            "case_review",
            "case_review_module",
            "case_review_functional_case",
            "case_review_functional_case_user",
            "case_review_functional_case_archive",
            "case_review_user",
            "case_review_follower",
            "case_review_history",
        ],
    ),
    (
        "计划关联与执行",
        [
            "test_plan_functional_case",
            "test_plan_case_execute_history",
            "test_plan_report_function_case",
            "functional_test_report",
        ],
    ),
    (
        "Agent / 枢纽（米多扩展）",
        [
            "agent_exec_log",
            "agent_exec_attachment",
            "default_hub_case_map",
        ],
    ),
    (
        "关联支撑表（查询时常 JOIN）",
        [
            "custom_field",
            "custom_field_option",
            "project",
            "test_plan",
            "test_plan_config",
        ],
    ),
]

RELATIONS = [
    ("functional_case", "project", "project_id", "用例属于项目"),
    ("functional_case", "functional_case_module", "module_id", "用例所属模块"),
    ("functional_case_blob", "functional_case", "id = functional_case.id", "正文/步骤 1:1"),
    ("functional_case_custom_field", "functional_case", "case_id", "自定义字段值"),
    ("functional_case_custom_field", "custom_field", "field_id", "字段定义；优先级 name=functional_priority"),
    ("functional_case_module", "project", "project_id", "模块树归属项目"),
    ("functional_case_module", "functional_case_module", "parent_id", "模块父子；根多为 NONE"),
    ("functional_case_module", "project", "ref_project_id", "默认项目 FOLDER 指向业务项目"),
    ("functional_case_attachment", "functional_case", "case_id", "附件"),
    ("functional_case_comment", "functional_case", "case_id", "评论"),
    ("functional_case_follower", "functional_case", "case_id", "关注人"),
    ("functional_case_demand", "functional_case", "case_id", "关联需求"),
    ("functional_case_relationship_edge", "functional_case", "source_id / target_id", "前后置依赖"),
    ("functional_case_test", "functional_case", "case_id", "关联自动化用例"),
    ("case_review", "project", "project_id", "评审归属项目"),
    ("case_review_functional_case", "case_review", "review_id", "评审关联用例"),
    ("case_review_functional_case", "functional_case", "case_id", "被评审用例"),
    ("test_plan_functional_case", "test_plan", "test_plan_id", "计划关联"),
    ("test_plan_functional_case", "functional_case", "functional_case_id", "关联用例；本表 id=testPlanCaseId"),
    ("test_plan_case_execute_history", "test_plan_functional_case", "test_plan_case_id", "计划内执行历史"),
    ("agent_exec_log", "functional_case", "case_id", "计划外执行审计"),
    ("default_hub_case_map", "functional_case", "biz_case_id / hub_case_id", "业务↔枢纽用例映射"),
]


def esc(s: str) -> str:
    return (s or "").replace("|", "\\|").replace("\n", " ")


def render_table(name: str, meta: dict) -> str:
    lines = [f"### `{name}`", ""]
    if meta.get("comment"):
        lines.append(f"**表含义**：{meta['comment']}")
        lines.append("")
    if meta.get("primary_key"):
        lines.append(f"- **主键**：{', '.join(f'`{p}`' for p in meta['primary_key'])}")
    if meta.get("sources"):
        lines.append(f"- **来源迁移**：`{'`, `'.join(meta['sources'][:6])}`")
    lines.append("")
    lines.append("| 字段 | 类型 | 可空 | 默认 | 含义 |")
    lines.append("|------|------|------|------|------|")
    for c in meta.get("columns", []):
        default = esc(c["default"]) if c.get("default") is not None else ""
        lines.append(
            f"| `{c['name']}` | {esc(c.get('type',''))} | {'YES' if c.get('nullable') else 'NO'} | {default} | {esc(c.get('comment',''))} |"
        )
    if meta.get("indexes"):
        lines.append("")
        lines.append("**索引（摘要）**：")
        for ix in meta["indexes"][:12]:
            lines.append(f"- `{esc(ix)}`")
    lines.append("")
    return "\n".join(lines)


def main():
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    tables = schema["tables"]
    conn = schema.get("connection_local", {})

    all_names = []
    for _, names in SECTIONS:
        all_names.extend(names)
    missing = [n for n in all_names if n not in tables]

    md = []
    md.append("# MeterSphere 测试用例相关表结构")
    md.append("")
    md.append(f"> **生成日期**：{DATE}  ")
    md.append("> **标注**：【AI生成】字段来自 Flyway DDL 解析结果（`MeterSphere-数据库结构-schema-2026-07-29.json`）；逻辑外键非数据库强制约束。  ")
    md.append("> **范围**：功能用例（`functional_case*`）、用例评审、计划关联执行、Agent/枢纽相关表。不含接口用例 `api_*`。  ")
    md.append("> **用途**：供其它 AI / 工程师连库后正确查询功能测试用例数据。")
    md.append("")
    md.append("## 0. AI 查询协议（必读）")
    md.append("")
    md.append("1. 库名 `metersphere`；本地默认 `127.0.0.1:3306` / `root` / `Password123@mysql`。")
    md.append("2. 主键多为 **VARCHAR(50)**；`create_time`/`update_time` 为 **BIGINT 毫秒时间戳**。")
    md.append("3. 列表查询默认加：`deleted = 0` 且 `latest = 1`（多版本时只看最新）。")
    md.append("4. **无 `functional_case.priority` 列**；优先级在 `functional_case_custom_field`，JOIN `custom_field.name = 'functional_priority'`。")
    md.append("5. 步骤正文在 `functional_case_blob`（与用例 **同 id**）；LONGBLOB 用 `CONVERT(col USING utf8mb4)`。")
    md.append("6. Agent/计划回写用 **`test_plan_functional_case.id`（testPlanCaseId）**，不是 `functional_case.id`。")
    md.append("7. 几乎无物理外键；用 `*_id` 逻辑 JOIN。查前可用 `SHOW FULL COLUMNS FROM <table>;` 校验。")
    md.append("")
    md.append("## 1. 连接（本地）")
    md.append("")
    md.append("| 项 | 值 |")
    md.append("|----|-----|")
    md.append(f"| Host | `{conn.get('host', '127.0.0.1')}` |")
    md.append(f"| Port | `{conn.get('port', 3306)}` |")
    md.append(f"| Database | `{conn.get('database', 'metersphere')}` |")
    md.append(f"| User | `{conn.get('user', 'root')}` |")
    md.append(f"| Password | `{conn.get('password', 'Password123@mysql')}` |")
    md.append("")
    md.append("```bash")
    md.append("docker exec -it ms-dev-mysql mysql -uroot -pPassword123@mysql metersphere")
    md.append("```")
    md.append("")
    md.append("## 2. 表关系")
    md.append("")
    md.append("```mermaid")
    md.append("erDiagram")
    md.append("  project ||--o{ functional_case_module : has")
    md.append("  project ||--o{ functional_case : has")
    md.append("  functional_case_module ||--o{ functional_case : contains")
    md.append("  functional_case ||--|| functional_case_blob : blob")
    md.append("  functional_case ||--o{ functional_case_custom_field : fields")
    md.append("  custom_field ||--o{ functional_case_custom_field : defines")
    md.append("  functional_case ||--o{ functional_case_attachment : files")
    md.append("  functional_case ||--o{ functional_case_comment : comments")
    md.append("  case_review ||--o{ case_review_functional_case : reviews")
    md.append("  functional_case ||--o{ case_review_functional_case : reviewed")
    md.append("  test_plan ||--o{ test_plan_functional_case : associates")
    md.append("  functional_case ||--o{ test_plan_functional_case : associated")
    md.append("  test_plan_functional_case ||--o{ test_plan_case_execute_history : history")
    md.append("  functional_case ||--o{ agent_exec_log : agent_audit")
    md.append("  functional_case ||--o{ default_hub_case_map : hub_map")
    md.append("```")
    md.append("")
    md.append("| 从表 | 到表 | 关联 | 说明 |")
    md.append("|------|------|------|------|")
    for a, b, via, note in RELATIONS:
        md.append(f"| `{a}` | `{b}` | `{via}` | {note} |")
    md.append("")
    md.append("## 3. 枚举与语义")
    md.append("")
    md.append("| 字段/概念 | 含义 |")
    md.append("|-----------|------|")
    md.append("| `functional_case.deleted` | 0 正常 / 1 回收站 |")
    md.append("| `functional_case.latest` | 1 最新版本；列表一般只查 latest=1 |")
    md.append("| `functional_case.case_edit_type` | `STEP` 用 blob.steps；`TEXT` 用 text_description + expected_result |")
    md.append("| `functional_case.review_status` | 如 UN_REVIEWED（未评审/评审中/通过/不通过/重新提审） |")
    md.append("| `functional_case.last_execute_result` | UN_EXECUTED / SUCCESS / ERROR / BLOCKED 等（以实际存值为准） |")
    md.append("| 优先级 P0–P3 | **不在主表**；`custom_field.name='functional_priority'` → `functional_case_custom_field.value` |")
    md.append("| `functional_case_module.parent_id` | 根节点常用 `'NONE'` |")
    md.append("| `functional_case_module.module_type` | `MODULE` 普通模块 / `FOLDER` 默认项目下业务项目文件夹 |")
    md.append("| `test_plan_functional_case.id` | = Agent `testPlanCaseId` |")
    md.append("| `test_plan_case_execute_history.status` | 成功/失败/阻塞等 |")
    md.append("| `agent_exec_log.last_exec_result` | SUCCESS / ERROR / BLOCKED / FAKE_ERROR |")
    md.append("| `project.is_default` | 1=默认枢纽项目 |")
    md.append("")
    md.append("## 4. 表清单")
    md.append("")
    for title, names in SECTIONS:
        md.append(f"- **{title}**：{', '.join(f'`{n}`' for n in names)}")
    if missing:
        md.append("")
        md.append(f"> 警告：下列表在 schema JSON 中缺失：{', '.join(missing)}")
    md.append("")
    md.append("## 5. 常用查询")
    md.append("")
    md.append("### 5.1 用例列表（模块 + 优先级）")
    md.append("")
    md.append("```sql")
    md.append("""SELECT c.id, c.num, c.name, c.review_status, c.last_execute_result,
       m.name AS module_name, f.value AS priority, c.execute_user, c.create_user, c.create_time
FROM functional_case c
LEFT JOIN functional_case_module m ON m.id = c.module_id
LEFT JOIN functional_case_custom_field f ON f.case_id = c.id
LEFT JOIN custom_field cf ON cf.id = f.field_id AND cf.name = 'functional_priority'
WHERE c.project_id = '{project_id}'
  AND c.deleted = 0
  AND c.latest = 1
ORDER BY c.pos, c.create_time DESC
LIMIT 50;""".strip())
    md.append("```")
    md.append("")
    md.append("### 5.2 用例步骤正文")
    md.append("")
    md.append("```sql")
    md.append("""SELECT c.id, c.name, c.case_edit_type,
       CONVERT(b.steps USING utf8mb4) AS steps_json,
       CONVERT(b.prerequisite USING utf8mb4) AS prerequisite,
       CONVERT(b.text_description USING utf8mb4) AS text_description,
       CONVERT(b.expected_result USING utf8mb4) AS expected_result,
       CONVERT(b.description USING utf8mb4) AS description
FROM functional_case c
JOIN functional_case_blob b ON b.id = c.id
WHERE c.id = '{case_id}';""".strip())
    md.append("```")
    md.append("")
    md.append("### 5.3 仅 P0 用例")
    md.append("")
    md.append("```sql")
    md.append("""SELECT c.id, c.name, f.value AS priority
FROM functional_case c
JOIN functional_case_custom_field f ON f.case_id = c.id
JOIN custom_field cf ON cf.id = f.field_id AND cf.name = 'functional_priority'
WHERE c.project_id = '{project_id}' AND c.deleted = 0 AND c.latest = 1
  AND f.value = 'P0';""".strip())
    md.append("```")
    md.append("")
    md.append("### 5.4 计划关联用例（含 testPlanCaseId）")
    md.append("")
    md.append("```sql")
    md.append("""SELECT tpf.id AS test_plan_case_id, tpf.test_plan_id, tpf.functional_case_id AS case_id,
       c.name, tpf.last_exec_result, tpf.execute_user, tpf.last_exec_time, tpf.pos
FROM test_plan_functional_case tpf
JOIN functional_case c ON c.id = tpf.functional_case_id
WHERE tpf.test_plan_id = '{test_plan_id}'
ORDER BY tpf.pos;""".strip())
    md.append("```")
    md.append("")
    md.append("### 5.5 计划内执行历史")
    md.append("")
    md.append("```sql")
    md.append("""SELECT id, test_plan_case_id, test_plan_id, case_id, status,
       CONVERT(content USING utf8mb4) AS content,
       CONVERT(steps USING utf8mb4) AS steps_json,
       create_user, create_time
FROM test_plan_case_execute_history
WHERE test_plan_case_id = '{test_plan_case_id}'
  AND deleted = 0
ORDER BY create_time DESC
LIMIT 20;""".strip())
    md.append("```")
    md.append("")
    md.append("### 5.6 模块树")
    md.append("")
    md.append("```sql")
    md.append("""SELECT id, name, parent_id, module_type, ref_project_id, pos
FROM functional_case_module
WHERE project_id = '{project_id}'
ORDER BY pos, name;""".strip())
    md.append("```")
    md.append("")
    md.append("### 5.7 评审中的用例")
    md.append("")
    md.append("```sql")
    md.append("""SELECT r.id AS review_id, r.name AS review_name, rfc.case_id, c.name AS case_name, rfc.status
FROM case_review r
JOIN case_review_functional_case rfc ON rfc.review_id = r.id
JOIN functional_case c ON c.id = rfc.case_id
WHERE r.project_id = '{project_id}'
ORDER BY r.create_time DESC
LIMIT 50;""".strip())
    md.append("```")
    md.append("")
    md.append("### 5.8 枢纽映射")
    md.append("")
    md.append("```sql")
    md.append("""SELECT * FROM default_hub_case_map
WHERE biz_project_id = '{biz_project_id}'
LIMIT 100;""".strip())
    md.append("```")
    md.append("")
    md.append("## 6. 字段明细")
    md.append("")

    for title, names in SECTIONS:
        md.append(f"## 6.{title}")
        md.append("")
        for n in names:
            if n not in tables:
                md.append(f"### `{n}`")
                md.append("")
                md.append("> 未在 schema JSON 中找到该表定义。")
                md.append("")
                continue
            md.append(render_table(n, tables[n]))

    md.append("## 7. 相关文档")
    md.append("")
    md.append("| 文档 | 路径 |")
    md.append("|------|------|")
    md.append("| 全库结构与查询指南 | `docs/summary/MeterSphere-数据库结构与查询指南-2026-07-29.md` |")
    md.append("| 全库 schema JSON | `docs/summary/MeterSphere-数据库结构-schema-2026-07-29.json` |")
    md.append("| Agent 数据模型 | `docs/task/metersphere_agent/task002-P0-数据模型与Flyway迁移.md` |")
    md.append("| 默认项目枢纽 | `docs/summary/MeterSphere-默认项目与跨项目导入-优化方案-2026-07-23.md` |")
    md.append("")
    md.append(f"生成时间：{datetime.now().isoformat(timespec='seconds')}")
    md.append("")

    OUT.write_text("\n".join(md), encoding="utf-8")
    print("wrote", OUT, "bytes", OUT.stat().st_size)
    print("tables_in_doc", len(all_names), "missing", missing)


if __name__ == "__main__":
    main()
