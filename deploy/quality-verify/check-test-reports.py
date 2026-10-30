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
    executed = []
    integrity_errors = []
    for path in sorted(root.glob("**/target/surefire-reports/TEST-*.xml")):
        module = path.parent.parent.parent.name
        suite = ET.parse(path).getroot()
        cases = suite.findall("testcase")
        observed = dict(tests=len(cases), failures=sum(c.find("failure") is not None for c in cases),
                        errors=sum(c.find("error") is not None for c in cases),
                        skipped=sum(c.find("skipped") is not None for c in cases))
        if any(int(suite.get(key, "0")) != value for key, value in observed.items()):
            integrity_errors.append(str(path.relative_to(root)))
        totals = modules.setdefault(module, dict(tests=0, failures=0, errors=0, skipped=0))
        for key in totals:
            totals[key] += int(suite.get(key, "0"))
        for case in cases:
            outcome = "passed"
            for state in ("failure", "error", "skipped"):
                if case.find(state) is not None:
                    # Do not publish raw messages: they may contain request/session data.
                    failures.append(dict(module=module, classname=case.get("classname"),
                                         test=case.get("name"), state=state))
                    outcome = state
                    break
            executed.append(dict(module=module, classname=case.get("classname"), test=case.get("name"), state=outcome))
    missing = sorted(m for m in required_modules if modules.get(m, {}).get("tests", 0) == 0)
    passed = bool(modules) and not missing and not integrity_errors and all(
        not any(v[k] for k in ("failures", "errors", "skipped")) for v in modules.values())
    return dict(passed=passed, missingModules=missing, modules=modules, unsuccessfulTests=failures,
                executedTests=executed, reportIntegrityErrors=integrity_errors)


def require_inventory(summary, baseline):
    if not baseline.get("executedTests"):
        raise ValueError("Baseline testcase inventory is required")
    key = lambda item: (item["module"], item["classname"], item["test"])
    current = {key(item) for item in summary["executedTests"]}
    summary["missingTestcases"] = [item for item in baseline["executedTests"] if key(item) not in current]
    summary["passed"] = summary["passed"] and not summary["missingTestcases"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("backend"))
    parser.add_argument("--output", type=Path, default=Path(".ci-test-summary.json"))
    parser.add_argument("--modules", nargs="+", help="Explicit module boundary for a targeted verification")
    parser.add_argument("--inventory", type=Path, help="Require every testcase from an earlier complete execution inventory")
    args = parser.parse_args()
    summary = inspect_reports(args.root, set(args.modules) if args.modules else REQUIRED_MODULES)
    if args.inventory:
        baseline = json.loads(args.inventory.read_text(encoding="utf-8"))
        require_inventory(summary, baseline)
        summary["inventorySource"] = str(args.inventory)
    provenance = Path(".ci-retained-upstream.json")
    if provenance.exists():
        summary["retainedEvidence"] = json.loads(provenance.read_text())
        summary["currentExecutionModules"] = sorted(set(summary["modules"]) - set(summary["retainedEvidence"]["modules"]))
    args.output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k not in {"unsuccessfulTests", "executedTests"}}, indent=2))
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
