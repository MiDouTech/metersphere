#!/usr/bin/env python3
import json
import subprocess
import sys
import time

CLI = r"C:\Users\midoo\.claude\skills\cds\cli\cdscli.py"


def call(*args):
    p = subprocess.run(
        [sys.executable, CLI, *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    raw = (p.stdout or "") + (p.stderr or "")
    i = raw.find("{")
    if i < 0:
        return {"_raw": raw[:1500], "rc": p.returncode}
    return json.loads(raw[i:])


def main():
    for i in range(18):
        st = call("branch", "status", "metersphere-v3-x")
        d = st.get("data") or {}
        sv = {k: v.get("status") for k, v in (d.get("services") or {}).items()}
        print(f"[{i}] branch={d.get('status')} services={sv}", flush=True)
        if sv.get("app-metersphere") == "running":
            break
        time.sleep(10)

    r = call(
        "branch",
        "exec",
        "metersphere-v3-x",
        "--profile",
        "app-metersphere",
        "--",
        "bash -lc "
        "'ls -la /repo/backend/app/target/app-*.jar; "
        "jar tf /repo/backend/app/target/app-3.x.jar | grep DisplayController || echo NO_IN_JAR; "
        "if [ -f /tmp/ms-mvn-rebuild.done ]; then echo DONEFILE=$(cat /tmp/ms-mvn-rebuild.done); "
        "else echo NO_DONE; fi; "
        "pgrep -af java | head -3; "
        "curl -sS -o /tmp/d.json -w CODE=%{http_code} http://127.0.0.1:8081/display/info; echo; "
        "head -c 200 /tmp/d.json; echo'",
    )
    print(json.dumps(r.get("data"), ensure_ascii=False)[:3000], flush=True)


if __name__ == "__main__":
    main()
