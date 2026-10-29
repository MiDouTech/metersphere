"""Extract failed testcases for faster private CI diagnosis; do not publish these as source."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET


def collect(root, destination):
    count = 0
    for path in sorted(root.glob("**/target/surefire-reports/TEST-*.xml")):
        suite = ET.parse(path).getroot()
        cases = [case for case in suite.findall("testcase")
                 if any(case.find(state) is not None for state in ("failure", "error", "skipped"))]
        if not cases:
            continue
        # Drop environment/system properties and passing HTTP/session logs.
        result = ET.Element("testsuite", dict(suite.attrib))
        result.set("diagnosticOnly", "true")
        result.set("originalTests", suite.get("tests", "0"))
        result.set("tests", str(len(cases)))
        for state, attr in (("failure", "failures"), ("error", "errors"), ("skipped", "skipped")):
            result.set(attr, str(sum(case.find(state) is not None for case in cases)))
        result.extend(cases)
        target = destination / path.relative_to(root)
        target.parent.mkdir(parents=True, exist_ok=True)
        ET.ElementTree(result).write(target, encoding="utf-8", xml_declaration=True)
        count += len(cases)
    return count


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("backend"))
    parser.add_argument("--output", type=Path, default=Path(".ci-failed-reports"))
    args = parser.parse_args()
    print(f"Collected {collect(args.root, args.output)} unsuccessful testcase details")
