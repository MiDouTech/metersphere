"""Watch Windows memory during isolated acceptance; stop only this task's containers."""
import argparse
import ctypes
from datetime import datetime, timezone
import json
import re
import subprocess
import time


class MemoryStatus(ctypes.Structure):
    _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong)] + [
        (name, ctypes.c_ulonglong) for name in (
            "physical_total", "physical_available", "commit_limit", "commit_available",
            "virtual_total", "virtual_available", "extended_available")]


def sample():
    status = MemoryStatus()
    status.length = ctypes.sizeof(status)
    if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
        raise ctypes.WinError()
    return {
        "time": datetime.now(timezone.utc).isoformat(),
        "freeMiB": round(status.physical_available / 1048576),
        "commitPercent": round(100 * (1 - status.commit_available / status.commit_limit), 1),
    }


def stop_owned(project):
    # No prune, volume deletion, Docker restart, or changes to other projects.
    for services in (("migration", "app"), ("kafka", "minio", "redis", "mysql")):
        try:
            result = subprocess.run(
                ["docker", "stop", "--timeout", "10", *[f"{project}-{s}-1" for s in services]],
                capture_output=True, text=True, timeout=45)
            print(json.dumps({"stoppedServices": services, "exitCode": result.returncode,
                              "output": (result.stdout + result.stderr)[-2000:]}), flush=True)
        except (OSError, subprocess.TimeoutExpired) as error:
            print(json.dumps({"stopError": type(error).__name__, "services": services}), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", default="msp-implementation20261022")
    parser.add_argument("--min-free-mib", type=int, default=1536)
    parser.add_argument("--max-commit-percent", type=float, default=92)
    parser.add_argument("--duration-minutes", type=int, default=60)
    parser.add_argument("--sample-only", action="store_true")
    parser.add_argument("--preflight", action="store_true",
                        help="Require another 2048 MiB startup headroom before starting containers")
    args = parser.parse_args()
    if not re.fullmatch(r"msp-implementation[0-9]+", args.project):
        parser.error("Only an isolated implementation project is allowed")
    if args.min_free_mib < 1536 or not 50 <= args.max_commit_percent <= 92 or not 1 <= args.duration_minutes <= 240:
        parser.error("Keep at least 1536 MiB free, commit threshold <=92%, duration 1..240 minutes")
    deadline = time.monotonic() + args.duration_minutes * 60
    while True:
        current = sample()
        print(json.dumps(current), flush=True)
        if args.preflight:
            allowed = (current["freeMiB"] >= args.min_free_mib + 2048
                       and current["commitPercent"] < args.max_commit_percent)
            print(json.dumps({"startupAllowed": allowed,
                              "requiredFreeMiB": args.min_free_mib + 2048}), flush=True)
            raise SystemExit(0 if allowed else 3)
        if args.sample_only:
            return
        if current["freeMiB"] < args.min_free_mib or current["commitPercent"] >= args.max_commit_percent:
            print(json.dumps({"cutoff": True, "project": args.project}), flush=True)
            stop_owned(args.project)
            raise SystemExit(2)
        if time.monotonic() >= deadline:
            print(json.dumps({"guardExpired": True, "project": args.project}), flush=True)
            stop_owned(args.project)
            return
        time.sleep(5)


if __name__ == "__main__":
    main()
