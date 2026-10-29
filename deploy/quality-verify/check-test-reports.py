#!/usr/bin/env python3
"""Fail closed after collecting every module's Surefire results."""
import argparse
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


REQUIRED_MODULES = {
    "sdk", "system-setting", "project-management", "api-test", "bug-management",
    "case-management", "test-plan", "dashboard", "agent-integration", "app",
}


def inspect_reports(root, required_modules=REQUIRED_MODULES):
    modules = {}
    failures = []
    for path in sorted(root.glob("**/target/surefire-reports/TEST-*.xml")):
        module = path.parent.parent.parent.name
        suite = ET.parse(path).getroot()
        totals = modules.setdefault(module, dict(tests=0, failures=0, errors=0, skipped=0))
        for key in totals:
            totals[key] += int(suite.get(key, "0"))
        for case in suite.findall("testcase"):
            for state in ("failure", "error", "skipped"):
                if case.find(state) is not None:
                    # Do not publish raw messages: they may contain request/session data.
                    failures.append(dict(module=module, classname=case.get("classname"),
                                         test=case.get("name"), state=state))
                    break
    missing = sorted(m for m in required_modules if modules.get(m, {}).get("tests", 0) == 0)
    passed = bool(modules) and not missing and all(
        not any(v[k] for k in ("failures", "errors", "skipped")) for v in modules.values())
    return dict(passed=passed, missingModules=missing, modules=modules, unsuccessfulTests=failures)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("backend"))
    parser.add_argument("--output", type=Path, default=Path(".ci-test-summary.json"))
    parser.add_argument("--modules", nargs="+", help="Explicit module boundary for a targeted verification")
    args = parser.parse_args()
    summary = inspect_reports(args.root, set(args.modules) if args.modules else REQUIRED_MODULES)
    args.output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "unsuccessfulTests"}, indent=2))
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
