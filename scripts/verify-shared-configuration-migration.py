"""Validate configuration migration on a new database in an isolated loopback MySQL."""
from pathlib import Path
import json
import subprocess
import uuid

root = Path(__file__).resolve().parents[1]
database = "msp_config_ui_test_" + uuid.uuid4().hex[:12]
mysql = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
command = [mysql, "--no-defaults", "--host=127.0.0.1", "--port=13306", "--user=root",
           "--default-character-set=utf8mb4", "--batch", "--skip-column-names"]
def execute(sql, selected=True):
    result = subprocess.run(command + (["--database=" + database] if selected else []),
                            input=sql, encoding="utf-8", capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()
execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4;", False)
ddl = root / "backend/framework/domain/src/main/resources/migration/3.7.2/ddl"
execute((ddl / "V3.7.2_152__shared_configurations.sql").read_text(encoding="utf-8").split("INSERT IGNORE")[0])
execute("INSERT INTO shared_configuration_version VALUES('legacy',1,'SECRET','fixture-cipher','','NOT_APPLICABLE','actor',1);")
execute((ddl / "V3.7.2_157__shared_configuration_secret_type.sql").read_text(encoding="utf-8"))
checks = {
    "legacyContentPreserved": execute("SELECT encrypted_value FROM shared_configuration_version WHERE configuration_id='legacy';") == "fixture-cipher",
    "legacyFormatPreserved": execute("SELECT COUNT(*) FROM shared_configuration_version WHERE secret_type='';") == "1",
    "subtypeColumnPresent": execute("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shared_configuration_version' AND column_name='secret_type';") == "1",
    "auditReasonPresent": execute("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shared_configuration_audit' AND column_name='reason';") == "1"
}
report = {"database": database, "port": 13306, "checks": checks}
(root / "docs/implementation/asset-ui-20261123/mysql-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report))
if not all(checks.values()):
    raise SystemExit(1)
