# -*- coding: utf-8 -*-
"""Parse Flyway DDL into schema JSON + AI-oriented Markdown docs."""
from __future__ import annotations

import json
import os
import re
from collections import OrderedDict, defaultdict
from datetime import datetime

ROOT = r"C:\SoftWare\JetBrains\metersphere\backend\framework\domain\src\main\resources\migration"
OUT_DIR = r"C:\SoftWare\JetBrains\metersphere\docs\summary"
DATE = "2026-07-29"

tables: OrderedDict = OrderedDict()


def normalize_name(n: str) -> str:
    return n.strip().strip("`").lower()


def split_top_level(s: str, sep: str = ","):
    parts, buf, depth, in_s, quote = [], [], 0, False, None
    i = 0
    while i < len(s):
        c = s[i]
        if in_s:
            buf.append(c)
            if c == quote:
                in_s = False
            i += 1
            continue
        if c in ("'", '"'):
            in_s = True
            quote = c
            buf.append(c)
            i += 1
            continue
        if c == "(":
            depth += 1
            buf.append(c)
            i += 1
            continue
        if c == ")":
            depth -= 1
            buf.append(c)
            i += 1
            continue
        if c == sep and depth == 0:
            parts.append("".join(buf).strip())
            buf = []
            i += 1
            continue
        buf.append(c)
        i += 1
    if buf:
        parts.append("".join(buf).strip())
    return parts


col_re = re.compile(
    r"^`?(?P<name>[A-Za-z0-9_]+)`?\s+(?P<type>[A-Za-z]+(?:\s*\(\s*\d+(?:\s*,\s*\d+)?\s*\))?(?:\s+UNSIGNED)?)"
    r"(?P<rest>.*)$",
    re.I | re.S,
)


def parse_column(line: str):
    m = col_re.match(line.strip())
    if not m:
        return None
    name = m.group("name")
    typ = re.sub(r"\s+", "", m.group("type").strip()) if "(" in m.group("type") else re.sub(r"\s+", " ", m.group("type").strip())
    # prettier type: keep VARCHAR(50) style
    typ = re.sub(r"\s+", "", m.group("type")) if re.search(r"\(\s*\d+", m.group("type")) else m.group("type").strip()
    rest = m.group("rest") or ""
    nullable = "NOT NULL" not in rest.upper()
    default = None
    dm = re.search(
        r"DEFAULT\s+((?:NULL)|(?:TRUE)|(?:FALSE)|(?:'(?:\\'|[^'])*')|(?:-?\d+(?:\.\d+)?)|(?:b'[01]+'))",
        rest,
        re.I,
    )
    if dm:
        default = dm.group(1)
    comment = ""
    cm = re.search(r"COMMENT\s+'((?:\\'|[^'])*)'", rest, re.I)
    if cm:
        comment = cm.group(1).replace("\\'", "'")
    is_pk = "PRIMARY KEY" in rest.upper()
    return {
        "name": name,
        "type": typ,
        "nullable": nullable and not is_pk,
        "default": default,
        "comment": comment,
        "primary_key": is_pk,
    }


index_prefixes = (
    "PRIMARY KEY",
    "UNIQUE KEY",
    "UNIQUE INDEX",
    "KEY ",
    "INDEX ",
    "CONSTRAINT ",
    "FULLTEXT ",
    "SPATIAL ",
)


def ensure_table(name: str):
    key = normalize_name(name)
    if key not in tables:
        tables[key] = {
            "name": key,
            "comment": "",
            "columns": OrderedDict(),
            "primary_key": [],
            "indexes": [],
            "sources": [],
        }
    return tables[key]


def strip_sql_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    out = []
    for ln in text.splitlines():
        if ln.strip().startswith("--"):
            continue
        # strip inline -- comments carefully outside quotes
        buf, in_s, quote = [], False, None
        i = 0
        while i < len(ln):
            c = ln[i]
            if in_s:
                buf.append(c)
                if c == quote:
                    in_s = False
                i += 1
                continue
            if c in ("'", '"'):
                in_s = True
                quote = c
                buf.append(c)
                i += 1
                continue
            if c == "-" and i + 1 < len(ln) and ln[i + 1] == "-":
                break
            buf.append(c)
            i += 1
        out.append("".join(buf))
    return "\n".join(out)


def extract_create_tables(text: str):
    """Yield (table_name, body, table_options) using paren depth."""
    upper = text.upper()
    i = 0
    while True:
        pos = upper.find("CREATE TABLE", i)
        if pos < 0:
            break
        # skip CREATE TABLE IF NOT EXISTS name
        m = re.match(
            r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(?P<name>[A-Za-z0-9_]+)`?\s*",
            text[pos:],
            re.I,
        )
        if not m:
            i = pos + 12
            continue
        name = m.group("name")
        j = pos + m.end()
        # skip whitespace to (
        while j < len(text) and text[j].isspace():
            j += 1
        if j >= len(text) or text[j] != "(":
            i = pos + 12
            continue
        # parse body by depth
        depth = 0
        k = j
        in_s, quote = False, None
        while k < len(text):
            c = text[k]
            if in_s:
                if c == quote:
                    in_s = False
                k += 1
                continue
            if c in ("'", '"'):
                in_s = True
                quote = c
                k += 1
                continue
            if c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    body = text[j + 1 : k]
                    k += 1
                    # options until ;
                    opt_start = k
                    while k < len(text) and text[k] != ";":
                        k += 1
                    opts = text[opt_start:k]
                    yield name, body, opts
                    i = k + 1 if k < len(text) else k
                    break
            k += 1
        else:
            i = pos + 12


def extract_alter_blocks(text: str):
    upper = text.upper()
    i = 0
    while True:
        pos = upper.find("ALTER TABLE", i)
        if pos < 0:
            break
        m = re.match(
            r"ALTER\s+TABLE\s+`?(?P<table>[A-Za-z0-9_]+)`?\s*",
            text[pos:],
            re.I,
        )
        if not m:
            i = pos + 11
            continue
        table = m.group("table")
        j = pos + m.end()
        # read until ; at depth 0
        depth, k, in_s, quote = 0, j, False, None
        while k < len(text):
            c = text[k]
            if in_s:
                if c == quote:
                    in_s = False
                k += 1
                continue
            if c in ("'", '"'):
                in_s = True
                quote = c
                k += 1
                continue
            if c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
            elif c == ";" and depth <= 0:
                yield table, text[j:k]
                i = k + 1
                break
            k += 1
        else:
            i = pos + 11


create_index_re = re.compile(
    r"CREATE\s+(?:UNIQUE\s+)?INDEX\s+`?(?P<iname>[A-Za-z0-9_]+)`?\s+ON\s+`?(?P<table>[A-Za-z0-9_]+)`?\s*\((?P<cols>[^)]+)\)\s*;",
    re.I | re.S,
)


def apply_create(name, body, opts, rel):
    t = ensure_table(name)
    if rel not in t["sources"]:
        t["sources"].append(rel)
    cm = re.search(r"COMMENT\s*=?\s*'((?:\\'|[^'])*)'", opts, re.I)
    if cm and not t["comment"]:
        t["comment"] = cm.group(1)
    for part in split_top_level(body):
        up = part.strip().upper()
        if not part.strip():
            continue
        if any(up.startswith(p) for p in index_prefixes):
            if up.startswith("PRIMARY KEY"):
                cols = re.search(r"\(([^)]+)\)", part)
                if cols:
                    t["primary_key"] = [normalize_name(c) for c in cols.group(1).split(",")]
            else:
                t["indexes"].append(re.sub(r"\s+", " ", part.strip())[:240])
            continue
        col = parse_column(part)
        if col:
            cname = normalize_name(col["name"])
            if cname not in t["columns"]:
                t["columns"][cname] = col
            if col["primary_key"] and cname not in t["primary_key"]:
                t["primary_key"].append(cname)


def apply_alter(table, body, rel):
    if "ADD" not in body.upper():
        return
    t = ensure_table(table)
    if rel not in t["sources"]:
        t["sources"].append(rel)
    for ch in split_top_level(body):
        chs = ch.strip()
        chu = chs.upper()
        if chu.startswith("ADD COLUMN"):
            rest = re.sub(r"^ADD\s+COLUMN\s+", "", chs, flags=re.I)
            rest = re.sub(r"\s+(AFTER|FIRST)\s+`?[A-Za-z0-9_]+`?\s*$", "", rest, flags=re.I)
            col = parse_column(rest)
            if col:
                t["columns"][normalize_name(col["name"])] = col
        elif chu.startswith("ADD ") and not any(
            chu.startswith(x)
            for x in (
                "ADD INDEX",
                "ADD KEY",
                "ADD UNIQUE",
                "ADD CONSTRAINT",
                "ADD FULLTEXT",
                "ADD PRIMARY",
                "ADD SPATIAL",
            )
        ):
            rest = re.sub(r"^ADD\s+", "", chs, flags=re.I)
            rest = re.sub(r"\s+(AFTER|FIRST)\s+`?[A-Za-z0-9_]+`?\s*$", "", rest, flags=re.I)
            col = parse_column(rest)
            if col:
                t["columns"][normalize_name(col["name"])] = col


MODULE_RULES = [
    (
        "system",
        [
            "user",
            "organization",
            "auth_source",
            "user_role",
            "user_key",
            "user_extend",
            "user_invite",
            "user_local_config",
            "license",
            "system_parameter",
            "organization_parameter",
            "plugin",
            "schedule",
            "service_integration",
            "custom_field",
            "template",
            "status_",
            "novice_",
            "test_resource_pool",
        ],
    ),
    (
        "project",
        [
            "project",
            "file_",
            "message_task",
            "notification",
            "project_robot",
            "project_application",
            "project_version",
            "fake_error",
            "custom_function",
        ],
    ),
    ("functional_case", ["functional_case", "functional_minder", "case_review"]),
    ("test_plan", ["test_plan", "functional_test_report"]),
    ("api_test", ["api_"]),
    ("bug", ["bug"]),
    (
        "environment",
        [
            "environment",
            "project_parameter",
            "share_info",
            "worker_node",
            "operation_",
        ],
    ),
    ("quartz", ["qrtz_"]),
    ("org_structure", ["department", "org_wecom", "org_sync"]),
    ("agent", ["agent_"]),
    ("default_hub", ["default_hub"]),
    ("edit_lock", ["resource_edit_"]),
    ("ai", ["ai_"]),
]


def classify(tname: str) -> str:
    for mod, prefs in MODULE_RULES:
        for p in prefs:
            if tname == p or tname.startswith(p):
                return mod
    return "other"


MODULE_LABELS = {
    "system": "系统设置 / 用户组织权限",
    "project": "项目管理 / 文件 / 通知",
    "functional_case": "功能用例 / 用例评审",
    "test_plan": "测试计划 / 报告 / 文档",
    "api_test": "接口测试",
    "bug": "缺陷管理",
    "environment": "环境 / 操作日志 / 参数",
    "quartz": "Quartz 调度",
    "org_structure": "组织架构 / 企微同步（米多）",
    "agent": "Agent API 集成（米多）",
    "default_hub": "默认项目枢纽（米多）",
    "edit_lock": "编辑锁与 Undo（米多）",
    "ai": "AI 相关",
    "other": "其它",
}

RELATIONSHIPS = [
    {"from": "user", "to": "organization", "via": "last_organization_id", "note": "用户最近组织"},
    {"from": "user", "to": "department", "via": "department_id", "note": "用户主部门"},
    {"from": "department", "to": "organization", "via": "organization_id", "note": "部门属于组织"},
    {"from": "department", "to": "department", "via": "parent_id", "note": "部门父子树"},
    {"from": "project", "to": "organization", "via": "organization_id", "note": "项目属于组织"},
    {"from": "functional_case", "to": "project", "via": "project_id", "note": "用例属于项目"},
    {"from": "functional_case", "to": "functional_case_module", "via": "module_id", "note": "用例所属模块"},
    {"from": "functional_case_blob", "to": "functional_case", "via": "id (= functional_case.id)", "note": "用例大字段（步骤/前置等）"},
    {"from": "functional_case_module", "to": "project", "via": "project_id", "note": "模块树归属项目"},
    {"from": "functional_case_module", "to": "project", "via": "ref_project_id", "note": "默认项目 FOLDER 映射业务项目"},
    {"from": "test_plan", "to": "project", "via": "project_id", "note": "测试计划归属"},
    {"from": "test_plan", "to": "test_plan", "via": "group_id", "note": "子计划挂到 type=group 的计划组；默认 none"},
    {"from": "test_plan_config", "to": "test_plan", "via": "test_plan_id PK", "note": "计划配置 1:1"},
    {"from": "test_plan_functional_case", "to": "test_plan", "via": "test_plan_id", "note": "计划-用例关联行；其 id 即 Agent 的 testPlanCaseId"},
    {"from": "test_plan_functional_case", "to": "functional_case", "via": "functional_case_id", "note": "关联功能用例"},
    {"from": "test_plan_case_execute_history", "to": "test_plan_functional_case", "via": "test_plan_case_id", "note": "计划内执行历史；test_plan_case_id = test_plan_functional_case.id"},
    {"from": "test_plan_document", "to": "test_plan", "via": "test_plan_id UNIQUE", "note": "计划文档 1:1"},
    {"from": "case_review", "to": "project", "via": "project_id", "note": "评审归属项目"},
    {"from": "case_review_functional_case", "to": "case_review", "via": "review_id", "note": "评审关联用例"},
    {"from": "bug", "to": "project", "via": "project_id", "note": "缺陷归属"},
    {"from": "bug_relation_case", "to": "bug", "via": "bug_id", "note": "缺陷关联用例"},
    {"from": "api_definition", "to": "project", "via": "project_id", "note": "接口定义"},
    {"from": "api_test_case", "to": "api_definition", "via": "api_definition_id", "note": "接口用例"},
    {"from": "api_scenario", "to": "project", "via": "project_id", "note": "接口场景"},
    {"from": "agent_token", "to": "user", "via": "user_id", "note": "Token 对应用户"},
    {"from": "agent_token", "to": "project", "via": "project_id", "note": "Token 默认项目"},
    {"from": "agent_exec_log", "to": "functional_case", "via": "case_id", "note": "执行审计"},
    {"from": "agent_exec_attachment", "to": "agent_exec_log", "via": "exec_log_id", "note": "计划外证据"},
    {"from": "default_hub_case_map", "to": "functional_case", "via": "biz_case_id / hub_case_id", "note": "业务↔枢纽用例"},
    {"from": "default_hub_plan_map", "to": "test_plan", "via": "biz_plan_id / hub_plan_id", "note": "业务↔枢纽计划"},
    {"from": "org_wecom_sync_config", "to": "organization", "via": "organization_id", "note": "企微同步配置"},
    {"from": "org_sync_log", "to": "organization", "via": "organization_id", "note": "同步日志"},
    {"from": "resource_edit_lock", "to": "project", "via": "project_id", "note": "编辑锁"},
    {"from": "resource_edit_snapshot", "to": "resource_edit_pointer", "via": "resource_type + resource_id", "note": "快照与 Undo 指针"},
    {"from": "user_role_relation", "to": "user", "via": "user_id", "note": "用户-角色"},
    {"from": "user_role_relation", "to": "user_role", "via": "role_id", "note": "角色定义"},
    {"from": "user_role_permission", "to": "user_role", "via": "role_id", "note": "角色权限点"},
]

SEMANTICS = {
    "common": {
        "id": "主键，多为 VARCHAR(50) 雪花/UUID 字符串，勿当整数",
        "create_time / update_time": "毫秒时间戳 BIGINT，不是 DATETIME；可用 FROM_UNIXTIME(create_time/1000)",
        "create_user / update_user": "用户 ID（user.id），不是显示名",
        "project_id": "所属项目 ID → project.id",
        "organization_id": "所属组织 ID → organization.id",
        "deleted": "BIT(1)：0 否 / 1 是（回收站）；查业务数据默认 deleted=0",
        "pos": "自定义排序，间隔常为 5000",
        "LONGBLOB 文本字段": "如 functional_case_blob.steps：应用层当 UTF-8 文本/JSON 使用，客户端可用 CONVERT(steps USING utf8mb4)",
    },
    "enums": {
        "functional_case 无 priority 列": "等级在自定义字段：functional_case_custom_field.value，关联 custom_field.name='functional_priority'（或模板内同名内部字段）。取值多为 P0/P1/P2/P3",
        "functional_case_custom_field": "case_id + field_id + value；查优先级必须 JOIN custom_field",
        "functional_case.review_status": "UN_REVIEWED 等（未评审/评审中/通过/不通过/重新提审）",
        "functional_case.case_edit_type": "STEP 步骤模式 / TEXT 文本模式（与 blob 字段启用对应）",
        "functional_case.last_execute_result": "UN_EXECUTED / 通过/失败/阻塞/跳过 等",
        "functional_case_module.module_type": "MODULE | FOLDER（默认项目下业务项目文件夹）",
        "functional_case_module.parent_id": "根节点常用 'NONE'",
        "project.is_default": "BIT(1)，1=系统默认项目（枢纽）",
        "test_plan.type": "group=计划组 / testPlan=普通计划",
        "test_plan.group_id": "挂到计划组；无组时为 none",
        "test_plan.status": "未开始/进行中/已完成/已归档（以实际存值为准）",
        "test_plan_functional_case.id": "即 Agent/API 中的 testPlanCaseId（不是 functional_case.id）",
        "test_plan_functional_case.last_exec_result": "计划维度最后执行结果",
        "agent_token.token_prefix": "如 msat；库内只存 token_hash",
        "agent_exec_log.last_exec_result": "SUCCESS / ERROR / BLOCKED / FAKE_ERROR 等",
        "department.dept_status": "1 启用 / 0 停用",
        "department.sync_status / user.sync_status": "0 未同步 / 1 已同步 / 2 同步失败",
        "org_sync_log.sync_mode": "MANUAL / SCHEDULE / LOGIN",
        "org_sync_log.sync_status": "SUCCESS / PARTIAL / FAILED",
        "default_hub_sync_job.job_type": "EVENT / CRON / MANUAL",
        "default_hub_sync_job.status": "PENDING / RUNNING / SUCCESS / FAILED",
        "resource_edit_lock.resource_type": "FUNCTIONAL_CASE | BUG | TEST_PLAN_DOCUMENT | ...",
        "test_plan_document.content_type": "RICH_TEXT / MARKDOWN",
    },
}

QUERY_RECIPES = [
    {
        "title": "按项目查功能用例（含模块名与优先级，排除回收站）",
        "sql": """SELECT c.id, c.num, c.name, c.review_status, c.last_execute_result, c.module_id,
       m.name AS module_name, cfv.value AS priority, c.execute_user, c.create_user, c.create_time
FROM functional_case c
LEFT JOIN functional_case_module m ON m.id = c.module_id
LEFT JOIN functional_case_custom_field cfv ON cfv.case_id = c.id
LEFT JOIN custom_field cf ON cf.id = cfv.field_id AND cf.name = 'functional_priority'
WHERE c.project_id = '{project_id}'
  AND c.deleted = 0
  AND c.latest = 1
ORDER BY c.pos, c.create_time DESC
LIMIT 50;""",
    },
    {
        "title": "仅按优先级过滤用例",
        "sql": """SELECT c.id, c.name, f.value AS priority
FROM functional_case c
JOIN functional_case_custom_field f ON f.case_id = c.id
JOIN custom_field cf ON cf.id = f.field_id AND cf.name = 'functional_priority'
WHERE c.project_id = '{project_id}' AND c.deleted = 0 AND c.latest = 1
  AND f.value = 'P0';""",
    },
    {
        "title": "查用例步骤正文（blob，转文本）",
        "sql": """SELECT c.id, c.name, c.case_edit_type,
       CONVERT(b.steps USING utf8mb4) AS steps_json,
       CONVERT(b.prerequisite USING utf8mb4) AS prerequisite,
       CONVERT(b.text_description USING utf8mb4) AS text_description,
       CONVERT(b.expected_result USING utf8mb4) AS expected_result,
       CONVERT(b.description USING utf8mb4) AS description
FROM functional_case c
JOIN functional_case_blob b ON b.id = c.id
WHERE c.id = '{case_id}';""",
    },
    {
        "title": "测试计划关联的功能用例（test_plan_case_id）",
        "sql": """SELECT tpf.id AS test_plan_case_id, tpf.test_plan_id, tpf.functional_case_id AS case_id,
       c.name, tpf.last_exec_result, tpf.execute_user, tpf.last_exec_time, tpf.pos
FROM test_plan_functional_case tpf
JOIN functional_case c ON c.id = tpf.functional_case_id
WHERE tpf.test_plan_id = '{test_plan_id}'
ORDER BY tpf.pos;""",
    },
    {
        "title": "计划组与子计划",
        "sql": """SELECT id, num, name, type, status, group_id, project_id
FROM test_plan
WHERE project_id = '{project_id}'
ORDER BY type, name;""",
    },
    {
        "title": "默认项目 / 枢纽项目",
        "sql": """SELECT id, name, organization_id, is_default, create_time
FROM project
WHERE is_default = 1;""",
    },
    {
        "title": "业务用例到枢纽映射",
        "sql": """SELECT * FROM default_hub_case_map
WHERE biz_project_id = '{biz_project_id}'
LIMIT 100;""",
    },
    {
        "title": "组织部门树",
        "sql": """SELECT id, name, parent_id, wecom_dept_id, dept_status, sort_order
FROM department
WHERE organization_id = '{organization_id}'
ORDER BY sort_order, name;""",
    },
    {
        "title": "用户及部门",
        "sql": """SELECT u.id, u.name, u.email, u.wecom_userid, u.department_id, d.name AS dept_name, u.last_organization_id
FROM `user` u
LEFT JOIN department d ON d.id = u.department_id
WHERE u.last_organization_id = '{organization_id}'
LIMIT 100;""",
    },
    {
        "title": "Agent Token 元数据（无明文）",
        "sql": """SELECT id, name, token_prefix, user_id, project_id, scopes, expire_time, enable, create_time
FROM agent_token
WHERE enable = 1;""",
    },
    {
        "title": "资源编辑锁是否占用",
        "sql": """SELECT * FROM resource_edit_lock
WHERE resource_type = 'FUNCTIONAL_CASE' AND resource_id = '{resource_id}'
  AND expire_time > UNIX_TIMESTAMP()*1000;""",
    },
    {
        "title": "计划内最近执行历史",
        "sql": """SELECT id, test_plan_case_id, test_plan_id, case_id, status, content, steps, create_user, create_time
FROM test_plan_case_execute_history
WHERE test_plan_case_id = '{test_plan_case_id}'  -- = test_plan_functional_case.id
  AND deleted = 0
ORDER BY create_time DESC
LIMIT 20;""",
    },
    {
        "title": "Flyway 迁移历史",
        "sql": """SELECT installed_rank, version, description, success, installed_on
FROM metersphere_version
ORDER BY installed_rank DESC
LIMIT 30;""",
    },
]


def md_escape(s: str) -> str:
    return (s or "").replace("|", "\\|").replace("\n", " ")


def render_table_section(name: str, meta: dict) -> str:
    lines = []
    label = meta.get("comment") or ""
    lines.append(f"### `{name}`")
    if label:
        lines.append(f"\n**表含义**：{label}")
    lines.append(f"\n- **模块**：{MODULE_LABELS.get(classify(name), classify(name))}")
    if meta["primary_key"]:
        lines.append(f"- **主键**：{', '.join('`'+p+'`' for p in meta['primary_key'])}")
    if meta["sources"]:
        lines.append(f"- **来源迁移**：`{'`, `'.join(meta['sources'][:8])}`")
    lines.append("\n| 字段 | 类型 | 可空 | 默认 | 含义 |")
    lines.append("|------|------|------|------|------|")
    for c in meta["columns"].values():
        null_s = "YES" if c["nullable"] else "NO"
        default = md_escape(c["default"]) if c["default"] is not None else ""
        lines.append(
            f"| `{c['name']}` | {md_escape(c['type'])} | {null_s} | {default} | {md_escape(c['comment'])} |"
        )
    if meta["indexes"]:
        lines.append("\n**索引（摘要）**：")
        for ix in meta["indexes"][:15]:
            lines.append(f"- `{md_escape(ix)}`")
    lines.append("")
    return "\n".join(lines)


def parse_all():
    tables.clear()
    sql_files = []
    for dp, _, fs in os.walk(ROOT):
        for f in fs:
            if f.endswith(".sql"):
                sql_files.append(os.path.join(dp, f))
    for path in sorted(sql_files):
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        raw = open(path, encoding="utf-8", errors="ignore").read()
        text = strip_sql_comments(raw)
        for name, body, opts in extract_create_tables(text):
            apply_create(name, body, opts, rel)
        for table, body in extract_alter_blocks(text):
            apply_alter(table, body, rel)
        for m in create_index_re.finditer(text):
            t = ensure_table(m.group("table"))
            t["indexes"].append(f"INDEX {m.group('iname')} ({m.group('cols').strip()})")
            if rel not in t["sources"]:
                t["sources"].append(rel)


def main():
    parse_all()
    by_module = defaultdict(list)
    for name in tables:
        by_module[classify(name)].append(name)

    schema = {
        "generated_at": DATE,
        "generated_note": "【AI生成】由 scripts/gen_db_schema_docs.py 从 Flyway DDL 解析；逻辑外键非数据库强制约束；查库前建议 DESCRIBE 校验",
        "database": "metersphere",
        "engine": "MySQL 8",
        "table_count": len(tables),
        "connection_local": {
            "host": "127.0.0.1",
            "port": 3306,
            "database": "metersphere",
            "user": "root",
            "password": "Password123@mysql",
            "jdbc": "jdbc:mysql://127.0.0.1:3306/metersphere?autoReconnect=false&useUnicode=true&characterEncoding=UTF-8&characterSetResults=UTF-8&zeroDateTimeBehavior=convertToNull&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai",
            "docker": "docker exec -it ms-dev-mysql mysql -uroot -pPassword123@mysql metersphere",
            "config_files": [
                "dev/docker-compose.yml",
                "deploy/nacos/dev/metersphere.properties",
                "local-runtime/conf/metersphere.properties",
            ],
        },
        "flyway": {
            "locations": "classpath:migration",
            "history_table": "metersphere_version",
            "path": "backend/framework/domain/src/main/resources/migration/",
        },
        "semantics": SEMANTICS,
        "modules": {k: sorted(v) for k, v in sorted(by_module.items())},
        "relationships": RELATIONSHIPS,
        "query_recipes": QUERY_RECIPES,
        "tables": {},
    }
    for name, meta in sorted(tables.items()):
        schema["tables"][name] = {
            "comment": meta["comment"],
            "module": classify(name),
            "primary_key": meta["primary_key"],
            "sources": meta["sources"],
            "indexes": meta["indexes"][:30],
            "columns": [
                {
                    "name": c["name"],
                    "type": c["type"],
                    "nullable": c["nullable"],
                    "default": c["default"],
                    "comment": c["comment"],
                }
                for c in meta["columns"].values()
            ],
        }

    os.makedirs(OUT_DIR, exist_ok=True)
    json_path = os.path.join(OUT_DIR, f"MeterSphere-数据库结构-schema-{DATE}.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(schema, f, ensure_ascii=False, indent=2)

    md = []
    md.append("# MeterSphere 数据库结构与查询指南")
    md.append("")
    md.append(f"> **生成日期**：{DATE}  ")
    md.append("> **标注**：【AI生成】已基于仓库 Flyway DDL 自动解析；关键语义与逻辑外键经方案文档对齐。  ")
    md.append(
        f"> **机器可读全量**：同目录 [`MeterSphere-数据库结构-schema-{DATE}.json`](./MeterSphere-数据库结构-schema-{DATE}.json)（含全部表字段）。  "
    )
    md.append("> **用途**：供其它 AI / 工程师连接数据库后正确查询；本地口令勿用于生产。")
    md.append("")
    md.append("## 0. 给 AI 的阅读与查询协议")
    md.append("")
    md.append("1. 先读 **§1 连接** 与 **§2 通用语义**，再查表。")
    md.append("2. 全量字段以 JSON 的 `tables.<name>.columns` 为准；本文展开核心与米多定制表。")
    md.append("3. **几乎没有物理外键**；用 `*_id` 做逻辑 JOIN。")
    md.append("4. 主键多为 **VARCHAR(50)**；时间为 **BIGINT 毫秒时间戳**。")
    md.append("5. 查询前建议 `SHOW FULL COLUMNS FROM <table>;` 与文档交叉验证。")
    md.append("6. 迁移状态看 `metersphere_version`。")
    md.append("7. **禁止**把 `agent_token.token_hash` 当明文 Token。")
    md.append("8. 表名 `user` 是保留字，SQL 中写 `` `user` ``。")
    md.append("")
    md.append("## 1. 连接信息（本地开发）")
    md.append("")
    md.append("| 项 | 值 |")
    md.append("|----|-----|")
    md.append("| RDBMS | MySQL 8.0.x |")
    md.append("| Host | `127.0.0.1` |")
    md.append("| Port | `3306` |")
    md.append("| Database | `metersphere` |")
    md.append("| User | `root` |")
    md.append("| Password | `Password123@mysql` |")
    md.append("| 容器名 | `ms-dev-mysql` |")
    md.append("")
    md.append("```bash")
    md.append(r"cd C:\SoftWare\JetBrains\metersphere\dev && docker compose up -d mysql")
    md.append("docker exec -it ms-dev-mysql mysql -uroot -pPassword123@mysql metersphere")
    md.append("```")
    md.append("")
    md.append("```text")
    md.append(schema["connection_local"]["jdbc"])
    md.append("```")
    md.append("")
    md.append("配置：`deploy/nacos/dev/metersphere.properties` → `local-runtime/conf/metersphere.properties`。")
    md.append("")
    md.append("栈：MyBatis + HikariCP；Flyway 历史表 `metersphere_version`；脚本目录 `backend/framework/domain/src/main/resources/migration/`。")
    md.append("")
    md.append(f"**解析表数量**：{len(tables)}")
    md.append("")
    md.append("## 2. 通用语义（查询必读）")
    md.append("")
    md.append("### 2.1 公共字段约定")
    md.append("")
    for k, v in SEMANTICS["common"].items():
        md.append(f"- **{k}**：{v}")
    md.append("")
    md.append("### 2.2 重要枚举 / 状态")
    md.append("")
    md.append("| 字段 | 含义 |")
    md.append("|------|------|")
    for k, v in SEMANTICS["enums"].items():
        md.append(f"| `{k}` | {v} |")
    md.append("")
    md.append("## 3. 逻辑表关系（ER 要点）")
    md.append("")
    md.append("```mermaid")
    md.append("erDiagram")
    md.append("  organization ||--o{ project : has")
    md.append("  organization ||--o{ department : has")
    md.append("  department ||--o{ department : parent")
    md.append("  department ||--o{ user : members")
    md.append("  project ||--o{ functional_case : has")
    md.append("  project ||--o{ functional_case_module : has")
    md.append("  functional_case_module ||--o{ functional_case : contains")
    md.append("  functional_case ||--|| functional_case_blob : blob")
    md.append("  project ||--o{ test_plan : has")
    md.append("  test_plan ||--o{ test_plan_functional_case : associates")
    md.append("  functional_case ||--o{ test_plan_functional_case : associated")
    md.append("  test_plan ||--o| test_plan_document : doc")
    md.append("  test_plan ||--|| test_plan_config : config")
    md.append("  project ||--o{ bug : has")
    md.append("  project ||--o{ case_review : has")
    md.append("  user ||--o{ agent_token : owns")
    md.append("  functional_case ||--o{ agent_exec_log : audited")
    md.append("  functional_case ||--o{ default_hub_case_map : mapped")
    md.append("```")
    md.append("")
    md.append("| 从表 | 到表 | 关联字段 | 说明 |")
    md.append("|------|------|----------|------|")
    for r in RELATIONSHIPS:
        md.append(f"| `{r['from']}` | `{r['to']}` | `{r['via']}` | {r['note']} |")
    md.append("")
    md.append("## 4. 模块 → 表清单")
    md.append("")
    for mod in sorted(by_module.keys(), key=lambda x: (x == "other", x)):
        names = sorted(by_module[mod])
        md.append(f"### {MODULE_LABELS.get(mod, mod)}（{len(names)}）")
        md.append("")
        md.append(", ".join(f"`{n}`" for n in names))
        md.append("")

    md.append("## 5. 常用查询配方")
    md.append("")
    for i, q in enumerate(QUERY_RECIPES, 1):
        md.append(f"### 5.{i} {q['title']}")
        md.append("")
        md.append("```sql")
        md.append(q["sql"].strip())
        md.append("```")
        md.append("")

    md.append("## 6. 核心与定制表字段明细")
    md.append("")
    md.append("全量字段亦写入 JSON。大模块中次要表仅给字段摘要。")
    md.append("")

    detail_order = [
        "org_structure",
        "agent",
        "default_hub",
        "edit_lock",
        "system",
        "project",
        "functional_case",
        "test_plan",
        "bug",
        "api_test",
        "environment",
        "ai",
        "quartz",
        "other",
    ]
    summary_only_modules = {"api_test", "quartz", "other"}
    important_subset = {
        "api_definition",
        "api_definition_blob",
        "api_test_case",
        "api_test_case_blob",
        "api_scenario",
        "api_scenario_step",
        "api_report",
        "environment",
        "environment_blob",
        "operation_log",
        "project_parameter",
        "share_info",
    }

    for mod in detail_order:
        names = sorted(by_module.get(mod, []))
        if not names:
            continue
        md.append(f"## 6.{mod} {MODULE_LABELS.get(mod, mod)}")
        md.append("")
        for n in names:
            meta = tables[n]
            if mod in summary_only_modules and n not in important_subset:
                comment = meta["comment"] or ""
                cols = ", ".join(f"`{c}`" for c in list(meta["columns"].keys())[:16])
                more = "" if len(meta["columns"]) <= 16 else f" … 共 {len(meta['columns'])} 列"
                md.append(f"### `{n}`")
                if comment:
                    md.append(f"\n**表含义**：{comment}")
                md.append(f"\n字段摘要：{cols}{more}。完整定义见 JSON `tables.{n}`。\n")
            else:
                md.append(render_table_section(n, meta))

    md.append("## 7. 运维与校验")
    md.append("")
    md.append("```sql")
    md.append("SHOW TABLES LIKE 'agent_%';")
    md.append("SHOW FULL COLUMNS FROM functional_case;")
    md.append("SHOW FULL COLUMNS FROM `user`;")
    md.append("SELECT version, description, success FROM metersphere_version ORDER BY installed_rank;")
    md.append("```")
    md.append("")
    md.append("| 文档 | 路径 |")
    md.append("|------|------|")
    md.append("| 本地连接对齐 | `docs/summary/Metersphere-本地与线上环境对齐改造方案.md` |")
    md.append("| Flyway 排障 | `docs/summary/MeterSphere-Flyway迁移故障-排障与防再发-2026-07-24.md` |")
    md.append("| 组织架构 | `docs/summary/community-unlock-and-org-structure.md` |")
    md.append("| Agent 数据模型 | `docs/task/metersphere_agent/task002-P0-数据模型与Flyway迁移.md` |")
    md.append("| 自动保存 | `docs/summary/MeterSphere-编辑自动保存与撤销-优化方案-2026-07-24.md` |")
    md.append("| 默认项目 | `docs/summary/MeterSphere-默认项目与跨项目导入-优化方案-2026-07-23.md` |")
    md.append("")
    md.append("## 8. 再生方式")
    md.append("")
    md.append("```bash")
    md.append("python scripts/gen_db_schema_docs.py")
    md.append("```")
    md.append("")
    md.append(f"生成时间：{datetime.now().isoformat(timespec='seconds')}")
    md.append("")

    md_path = os.path.join(OUT_DIR, f"MeterSphere-数据库结构与查询指南-{DATE}.md")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("\n".join(md))

    print("tables:", len(tables))
    print("modules:", {k: len(v) for k, v in sorted(by_module.items())})
    print("json:", json_path)
    print("md:", md_path)
    for t in [
        "user",
        "functional_case",
        "functional_case_blob",
        "agent_token",
        "department",
        "org_sync_log",
        "project",
        "test_plan",
        "test_plan_functional_case",
        "default_hub_case_map",
        "resource_edit_lock",
    ]:
        meta = tables.get(t)
        if not meta:
            print(t, "MISSING")
        else:
            print(t, "cols", len(meta["columns"]), "comment", meta["comment"], "pk", meta["primary_key"])


if __name__ == "__main__":
    main()
