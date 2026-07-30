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
        return {"_raw": raw[:1500]}
    return json.loads(raw[i:])


def main():
    for i in range(60):
        st = call("branch", "status", "metersphere-v3-x")
        d = st.get("data") or {}
        sv = {k: v.get("status") for k, v in (d.get("services") or {}).items()}
        q = d.get("buildQueue")
        print(
            f"[{i}] status={d.get('status')} sv={sv} queue={q} "
            f"ready={d.get('lastReadyAt')} deploy={d.get('lastDeployStartedAt')}",
            flush=True,
        )
        if (
            d.get("status") == "running"
            and sv.get("app-metersphere") == "running"
            and not q
        ):
            break
        time.sleep(20)

    r = call(
        "branch",
        "exec",
        "metersphere-v3-x",
        "--profile",
        "app-metersphere",
        "--timeout",
        "60",
        "--",
        "bash -lc "
        "'echo JAR_DATE; ls -la /repo/backend/app/target/app-*.jar; "
        "echo IN_JAR; jar tf /repo/backend/app/target/app-3.x.jar | grep DisplayController || echo NO; "
        "echo API; curl -sS -w \"\\nHTTP=%{http_code}\\n\" http://127.0.0.1:8081/display/info | head -c 300'",
    )
    print("EXEC:", json.dumps(r.get("data"), ensure_ascii=False)[:2500], flush=True)


if __name__ == "__main__":
    main()
