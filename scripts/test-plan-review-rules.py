"""Run the pure v1.0 rule tests without Spring, a business database or Docker.

This does not replace application/integration/permission/transaction/acceptance testing.
JUnit is cached only in this checkout's ignored .build directory.
"""
import hashlib
import argparse
import concurrent.futures
import os
import pathlib
import subprocess
import urllib.request
import urllib.error

ROOT = pathlib.Path(__file__).resolve().parents[1]
BUILD = ROOT / ".build" / "plan-review-rules"
BUILD.mkdir(parents=True, exist_ok=True)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--jdbc", action="store_true", help="Also run local H2 relational tests; not MySQL acceptance")
parser.add_argument("--mysql-config", type=pathlib.Path, help="Ignored workspace properties for an explicitly isolated local MySQL test instance")
args = parser.parse_args()
if args.mysql_config:
    args.mysql_config=args.mysql_config.resolve()
    if not args.mysql_config.is_relative_to((ROOT/'.build').resolve()):
        parser.error('MySQL test configuration must be inside the ignored workspace .build directory')
    args.jdbc=True
JAR = BUILD / "junit-platform-console-standalone-1.11.4.jar"
URL = "https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/" + JAR.name
def artifact(url, destination):
    if not destination.exists():
        content = urllib.request.urlopen(url, timeout=60).read()
        for algorithm in ("sha512", "sha256", "sha1"):
            try:
                expected = urllib.request.urlopen(url + "." + algorithm, timeout=30).read().decode().strip().split()[0]
                break
            except urllib.error.HTTPError as error:
                if error.code != 404:
                    raise
        else:
            raise RuntimeError("Dependency checksum unavailable: " + destination.name)
        if hashlib.new(algorithm, content).hexdigest() != expected:
            raise RuntimeError("Dependency checksum mismatch: " + destination.name)
        destination.write_bytes(content)
    return destination

artifact(URL, JAR)
dependencies = []
if args.jdbc:
    coordinates = [("org/springframework", "spring-" + name, "6.2.12")
                   for name in ("jdbc", "tx", "beans", "core", "jcl", "context")]
    coordinates.append(("com/h2database", "h2", "2.3.232"))
    if args.mysql_config:
        coordinates.append(("com/mysql", "mysql-connector-j", "9.4.0"))
    coordinates += [("com/fasterxml/jackson/core", "jackson-" + name, "2.19.2")
                    for name in ("annotations", "core", "databind")]
    coordinates.append(("com/fasterxml/jackson/datatype", "jackson-datatype-jsr310", "2.19.2"))
    def dependency(coordinate):
        group, name, version = coordinate
        filename = name + "-" + version + ".jar"
        return artifact("https://repo.maven.apache.org/maven2/" + group + "/" + name + "/"
                        + version + "/" + filename, BUILD / filename)
    with concurrent.futures.ThreadPoolExecutor(max_workers=7) as executor:
        dependencies = list(executor.map(dependency, coordinates))
classes = BUILD / "classes"
classes.mkdir(exist_ok=True)
module = ROOT / "backend/services/case-management/src"
pure = {"PlanReviewPolicy.java", "PlanReviewApproval.java", "PlanReviewSourceMembership.java",
        "PlanReviewSettings.java", "PlanReviewRetryBudget.java", "PlanReviewReconciliation.java", "PlanReviewFailure.java"}
sources = sorted(p for p in (module / "main/java/io/metersphere/functional/planreview").glob("*.java")
                 if args.jdbc or p.name in pure)
sources += sorted(p for p in (module / "test/java/io/metersphere/functional/planreview").glob("*.java")
                  if args.jdbc or p.name == "PlanReviewRulesTests.java")
if args.jdbc:
    sources.append(ROOT / "backend/framework/sdk/src/main/java/io/metersphere/sdk/util/JSON.java")
classpath = os.pathsep.join(map(str, [JAR, *dependencies]))
subprocess.run(["javac", "-encoding", "UTF-8", "--release", "21", "-cp", classpath,
                "-d", str(classes), *map(str, sources)], check=True)
subprocess.run(["java", "-Dplan.review.repo.root=" + str(ROOT),
                *(["-Dplan.review.mysql.config="+str(args.mysql_config)] if args.mysql_config else []), "-jar", str(JAR), "execute",
                "--class-path", os.pathsep.join(map(str, [classes, *dependencies])),
                "--select-class", "io.metersphere.functional.planreview.PlanReviewRulesTests",
                *(["--select-class", "io.metersphere.functional.planreview.PlanReviewPersistenceTests"] if args.jdbc else []),
                *(["--select-class", "io.metersphere.functional.planreview.PlanReviewApplicationTests"] if args.jdbc else []),
                *(["--select-class", "io.metersphere.functional.planreview.PlanReviewLifecycleTests"] if args.jdbc else []),
                *(["--select-class", "io.metersphere.functional.planreview.PlanReviewOperationTests"] if args.jdbc else []),
                *(["--select-class", "io.metersphere.functional.planreview.PlanReviewNoticeTests"] if args.jdbc else []),
                *(["--select-class", "io.metersphere.functional.planreview.PlanReviewLegacyTests"] if args.jdbc else []),
                *(["--select-class", "io.metersphere.functional.planreview.PlanReviewLegacyMigrationTests"] if args.jdbc else []),
                "--fail-if-no-tests", "--disable-ansi-colors", "--details", "summary"], check=True)
