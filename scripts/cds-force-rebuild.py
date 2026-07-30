#!/usr/bin/env python3
"""Force Maven rebuild inside CDS app container, then restart Java."""
import base64
import json
import subprocess
import sys
import time

CLI = r"C:\Users\midoo\.claude\skills\cds\cli\cdscli.py"
BRANCH = "metersphere-v3-x"
PROFILE = "app-metersphere"

REMOTE_SCRIPT = r"""#!/bin/bash
set -eu
mkdir -p /repo/tmp
cd /repo
rm -f /tmp/ms-mvn-rebuild.done
mvn -pl backend/app -am package -DskipTests -DskipAntRunForJenkins=true -Djansi.tmpdir=/repo/tmp \
  > /tmp/ms-mvn-rebuild.log 2>&1
echo $? > /tmp/ms-mvn-rebuild.done
"""


def exec_remote(command: str, timeout: int = 60) -> dict:
    p = subprocess.run(
        [
            sys.executable,
            CLI,
            "branch",
            "exec",
            BRANCH,
            "--profile",
            PROFILE,
            "--timeout",
            str(timeout),
            "--",
            command,
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    raw = (p.stdout or "") + (p.stderr or "")
    i = raw.find("{")
    if i < 0:
        raise RuntimeError(f"no json from cdscli (rc={p.returncode}): {raw[:800]}")
    return json.loads(raw[i:])


def main() -> int:
    b64 = base64.b64encode(REMOTE_SCRIPT.encode()).decode()
    print("[1] install + start rebuild script...", flush=True)
    r = exec_remote(
        f"bash -lc 'echo {b64} | base64 -d > /tmp/ms-rebuild.sh; chmod +x /tmp/ms-rebuild.sh; "
        f"rm -f /tmp/ms-mvn-rebuild.done; "
        f"setsid /tmp/ms-rebuild.sh </dev/null >/dev/null 2>&1 & "
        f"sleep 2; pgrep -af mvn || true; wc -c /tmp/ms-mvn-rebuild.log || true; "
        f"tail -5 /tmp/ms-mvn-rebuild.log || true'",
        timeout=30,
    )
    print((r.get("data") or {}).get("stdout"), flush=True)

    print("[2] poll rebuild (up to 60min)...", flush=True)
    deadline = time.time() + 3600
    while time.time() < deadline:
        poll = exec_remote(
            "bash -lc 'if [ -f /tmp/ms-mvn-rebuild.done ]; then echo DONE=$(cat /tmp/ms-mvn-rebuild.done); "
            "else echo RUNNING; pgrep -af mvn || echo no-mvn; fi; "
            "tail -2 /tmp/ms-mvn-rebuild.log 2>/dev/null || true'",
            timeout=30,
        )
        out = ((poll.get("data") or {}).get("stdout") or "").strip()
        print(out[-600:], flush=True)
        if "DONE=" in out:
            for line in out.splitlines():
                if line.startswith("DONE="):
                    code = int(line.split("=", 1)[1].strip())
                    if code != 0:
                        # dump log tail
                        log = exec_remote(
                            "bash -lc 'tail -80 /tmp/ms-mvn-rebuild.log'",
                            timeout=30,
                        )
                        print((log.get("data") or {}).get("stdout"), flush=True)
                        return code
                    break
            break
        time.sleep(25)
    else:
        print("timeout waiting for mvn", flush=True)
        return 2

    print("[3] verify DisplayController in jar...", flush=True)
    verify = exec_remote(
        "bash -lc 'ls -la /repo/backend/app/target/app-*.jar; "
        "jar tf /repo/backend/app/target/app-3.x.jar 2>/dev/null | grep -i DisplayController | head -5; "
        "find /repo/backend/services/system-setting/target -name DisplayController.class 2>/dev/null | head -3'",
        timeout=60,
    )
    print((verify.get("data") or {}).get("stdout"), flush=True)

    print("[4] kill java (pid 1) to force container restart...", flush=True)
    try:
        exec_remote("bash -lc 'kill 1'", timeout=15)
    except Exception as e:
        print(f"kill expected to drop connection: {e}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
