"""Retain passing upstream evidence only when its complete source dependency trees match."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

MODULES = {"sdk": "backend/framework/sdk", "system-setting": "backend/services/system-setting",
           "project-management": "backend/services/project-management"}
DEPENDENCIES = ["pom.xml", "backend/pom.xml", ".mvn", "mvnw", "mvnw.cmd",
                "backend/framework/domain", "backend/framework/sdk", "backend/framework/provider",
                "backend/framework/ai-engine", "backend/services/system-setting",
                "backend/services/project-management", "deploy/quality-verify/Dockerfile.minio-test-source"]


def matching_trees(source_sha):
    hashes = {}
    for path in DEPENDENCIES:
        before = subprocess.check_output(["git", "rev-parse", f"{source_sha}:{path}"], text=True).strip()
        current = subprocess.check_output(["git", "rev-parse", f"HEAD:{path}"], text=True).strip()
        if before != current:
            raise ValueError(f"Upstream dependency changed; full verification required: {path}")
        hashes[path] = current
    return hashes


def validate_module(module, reports, expected):
    observed = dict(tests=0, failures=0, errors=0, skipped=0)
    if not reports:
        raise ValueError(f"No source reports: {module}")
    for report in reports:
        suite = ET.parse(report).getroot()
        if int(suite.get("tests", "0")) != len(suite.findall("testcase")):
            raise ValueError(f"Incomplete source report: {module}")
        for key in observed:
            observed[key] += int(suite.get(key, "0"))
        if any(case.find(state) is not None for case in suite.findall("testcase")
               for state in ("failure", "error", "skipped")):
            raise ValueError(f"Source module did not pass: {module}")
    if observed != expected or observed["tests"] == 0 or any(observed[k] for k in ("failures", "errors", "skipped")):
        raise ValueError(f"Source summary/report mismatch or failure: {module}")
    return observed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--source-run", required=True)
    parser.add_argument("--reports", type=Path, required=True)
    parser.add_argument("--summary", type=Path, required=True)
    args = parser.parse_args()
    hashes = matching_trees(args.source_sha)
    source = json.loads(args.summary.read_text())
    provenance = dict(sourceRun=args.source_run, sourceCommit=args.source_sha, dependencyTrees=hashes, modules={})
    for module, target in MODULES.items():
        reports = sorted((args.reports / target / "target/surefire-reports").glob("TEST-*.xml"))
        totals = validate_module(module, reports, source["modules"][module])
        destination = Path(target) / "target/surefire-reports"
        if list(destination.glob("TEST-*.xml")):
            raise ValueError(f"Current execution already produced upstream reports: {module}")
        destination.mkdir(parents=True, exist_ok=True)
        for report in reports:
            shutil.copy2(report, destination / report.name)
        provenance["modules"][module] = totals
    Path(".ci-retained-upstream.json").write_text(json.dumps(provenance, indent=2) + "\n")
    print("Retained verified source-identical modules:", ", ".join(MODULES))


if __name__ == "__main__":
    main()
