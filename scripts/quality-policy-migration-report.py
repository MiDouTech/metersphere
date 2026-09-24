"""Read-only legacy quality policy inventory. Never selects or publishes a policy.
Input: {"items": [...all archive API pages...], "projects": ["project-id", ...]}.
Usage: python scripts/quality-policy-migration-report.py --input archive.json --output report.md
"""
import argparse
import hashlib
import json
from pathlib import Path


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def normalize_rules(document):
    rules = {}
    for rule in document["rules"]:
        key = rule["ruleId"]
        if key in rules:
            raise ValueError("duplicate rule ID")
        parameters = dict(rule["parameters"])
        for field in ("requiredRoles", "allowedMimeTypes", "allowedOperators"):
            if isinstance(parameters.get(field), list):
                parameters[field] = sorted(parameters[field])
        rules[key] = parameters
    return rules


def cell(value):
    text = value if isinstance(value, str) else canonical(value)
    return text.replace("&", "&amp;").replace("<", "&lt;").replace("|", "&#124;").replace("\n", "<br>").replace("\r", "")


def render(payload):
    items = payload["items"]
    if not isinstance(items, list):
        raise ValueError("items must contain all archive pages")
    if "total" in payload and payload["total"] != len(items):
        raise ValueError("archive is paginated: collect all pages before comparison")
    groups, inventory, metadata, invalid = {}, [], [], []
    for item in items:
        try:
            document = json.loads(item["rulesJson"])
            rules = normalize_rules(document)
            digest = hashlib.sha256(canonical(rules).encode()).hexdigest()
            groups.setdefault(digest, rules)
            meta = {key: value for key, value in document.items() if key != "rules"}
            metadata.append([item["id"], canonical(meta)])
        except (KeyError, ValueError, TypeError):
            digest = "INVALID: requires manual repair"
            invalid.append(item.get("id", "unknown"))
        inventory.append([item.get("projectId", ""), item.get("id", ""), item.get("versionNo", ""),
                          item.get("status", ""), item.get("contentHash", ""), digest])
    lines = ["# Legacy quality policy migration preflight", "",
             "Read-only report. No candidate is selected or published. Draft and published sources remain distinct.", "",
             f"Sources: {len(items)}; distinct normalized rule sets: {len(groups)}; invalid sources: {len(invalid)}.", ""]
    def table(headers, rows):
        lines.append("| " + " | ".join(headers) + " |")
        lines.append("| " + " | ".join("---" for _ in headers) + " |")
        lines.extend("| " + " | ".join(cell(v) for v in row) + " |" for row in rows)
        lines.append("")
    lines.append("## Source inventory (original hashes retained)"); lines.append("")
    table(["Project", "Source ID", "Version", "Status", "Original document hash", "Normalized rules hash"], inventory)
    lines.append("## Rule and parameter differences"); lines.append("")
    keys = sorted({(rule, parameter) for rules in groups.values() for rule, values in rules.items() for parameter in values})
    digests = sorted(groups)
    table(["Rule / parameter"] + digests,
          [[rule + "/" + parameter] + [groups[d].get(rule, {}).get(parameter, "MISSING") for d in digests]
           for rule, parameter in keys])
    lines.append("## Metadata (compared separately from rules)"); lines.append("")
    table(["Source ID", "Metadata"], metadata)
    if "projects" in payload:
        covered = {item["projectId"] for item in items}
        lines += ["## Projects without any legacy policy", "",
                  ", ".join(cell(p) for p in sorted(set(payload["projects"]) - covered)) or "None", ""]
    else:
        lines += ["Project inventory was not supplied; projects without policies are NOT assessed.", ""]
    lines += ["Invalid sources: " + (", ".join(cell(x) for x in invalid) or "None"), "",
              "An administrator must inspect differences, import/create a global draft, validate it, and explicitly publish it.", ""]
    return "\n".join(lines)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    payload = json.loads(Path(args.input).read_text(encoding="utf-8-sig"))
    result = render(payload)
    Path(args.output).write_text(result, encoding="utf8")
