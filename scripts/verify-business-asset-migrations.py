"""Verify migrations on a NEW database on an isolated loopback MySQL instance.

Never targets port 3306 or an existing database. Keeps the test database for inspection.
"""
from pathlib import Path
import argparse
import subprocess
import uuid
import json

parser = argparse.ArgumentParser()
parser.add_argument('--mysql', default=r'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe')
parser.add_argument('--port', type=int, default=13306)
args = parser.parse_args()
if args.port == 3306:
    raise SystemExit('Refusing standard MySQL port; use an isolated test instance')
root = Path(__file__).resolve().parents[1]
database = 'msp_assets_test_' + uuid.uuid4().hex[:12]
command = [args.mysql, '--no-defaults', '--host=127.0.0.1', '--protocol=TCP', '--port='+str(args.port), '--user=root', '--default-character-set=utf8mb4', '--batch', '--skip-column-names']
def execute(sql, selected=True):
    result = subprocess.run(command + (['--database='+database] if selected else []), input=sql, encoding='utf-8', capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()
execute('CREATE DATABASE '+database+' CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;', False)
execute('''
CREATE TABLE project(id VARCHAR(64) PRIMARY KEY,organization_id VARCHAR(64),deleted BOOLEAN,enable BOOLEAN);
CREATE TABLE agent_case_evidence(id VARCHAR(64) PRIMARY KEY,token_id VARCHAR(64) NOT NULL);
CREATE TABLE agent_case_submission(id VARCHAR(64) PRIMARY KEY,token_id VARCHAR(64) NOT NULL);
CREATE TABLE ai_execution_step_result(id VARCHAR(64) PRIMARY KEY,lease_id VARCHAR(50));
CREATE TABLE user_role_permission(id VARCHAR(64) PRIMARY KEY,role_id VARCHAR(64),permission_id VARCHAR(128),UNIQUE(role_id,permission_id));
INSERT INTO project VALUES('project-a','org',FALSE,TRUE),('project-b','org',FALSE,TRUE);
INSERT INTO user_role_permission VALUES('r1','role-a','FUNCTIONAL_CASE_AI:READ'),('r2','role-a','FUNCTIONAL_CASE_AI:SAVE'),('r3','role-a','PROJECT_ENVIRONMENT:READ'),('r4','role-a','PROJECT_ENVIRONMENT:READ+UPDATE'),('r5','role-b','FUNCTIONAL_CASE_AI:READ');
''')
ddl = root/'backend/framework/domain/src/main/resources/migration/3.7.2/ddl'
resource = (ddl/'V3.7.2_38__ui_permission_resource.sql').read_text(encoding='utf-8').split('CREATE TABLE IF NOT EXISTS user_role_ui_permission')[0]
execute(resource)
execute("INSERT INTO permission_resource(id,code,name,type,scope_type) VALUES('TEST_ASSET_DOCUMENTS_PAGE','TEST_ASSET_DOCUMENTS_PAGE','业务文档','PAGE','PROJECT');")
for name in ['V3.7.2_148__governed_execution_protocol.sql','V3.7.2_149__ai_execution_knowledge_usage.sql']:
    execute((ddl/name).read_text(encoding='utf-8'))
execute('''
INSERT INTO msp_knowledge VALUES('legacy','org','creator','PUBLISHED',2,2,0,
 JSON_OBJECT('title','订单业务','body','原始业务理解','sources',JSON_ARRAY(JSON_OBJECT('projectId','project-a'))),1);
INSERT INTO msp_knowledge_version VALUES('legacy',1,'PUBLISHED',JSON_OBJECT('title','订单业务','body','第一版'),REPEAT('a',64),'creator',NULL,1),
 ('legacy',2,'PUBLISHED',JSON_OBJECT('title','订单业务','body','第二版'),REPEAT('b',64),'creator',NULL,2);
INSERT INTO msp_knowledge VALUES('unbound','org','creator','DRAFT',1,NULL,0,JSON_OBJECT('title','未归属业务','body','不得猜测项目'),1);
INSERT INTO msp_protocol_evidence_hold VALUES('PERSONAL','old-personal','old-run','FACT',1),('TASK','old-task','old-run','FACT',1);
''')
names = ['V3.7.2_150__business_understanding_assets.sql','V3.7.2_151__external_executor_sessions.sql','V3.7.2_152__shared_configurations.sql','V3.7.2_153__retire_governed_execution.sql','V3.7.2_154__external_executor_evidence.sql','V3.7.2_155__business_asset_navigation.sql','V3.7.2_156__historical_evidence_retention.sql']
for name in names:
    try:
        execute((ddl/name).read_text(encoding='utf-8'))
    except RuntimeError as error:
        raise RuntimeError(name + ': ' + str(error)) from None
checks = {
 'allHistoricalKnowledgeRetained': execute('SELECT COUNT(*) FROM retired_msp_knowledge;') == '2',
 'mappedKnowledgeVersionsPreserved': execute('SELECT COUNT(*) FROM business_asset_version;') == '2',
 'noGuessedProjectForUnboundKnowledge': execute('SELECT COUNT(*) FROM business_asset;') == '1',
 'legacyReaderRestrictionPreserved': execute("SELECT COUNT(*) FROM business_asset_reader WHERE user_id='creator';") == '1',
 'permissionGrantsHaveUniqueIds': execute("SELECT COUNT(*) FROM user_role_permission WHERE permission_id='BUSINESS_ASSET:READ';") == '2',
 'noActiveProtocolTables': execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'msp_protocol_%';") == '0',
 'newNavigationPresent': execute("SELECT COUNT(*) FROM permission_resource WHERE code='TEST_ASSET_SHARED_CONFIGURATION_PAGE';") == '1',
 'historicalPersonalAndTaskEvidenceRetained': execute("SELECT COUNT(*) FROM historical_evidence_retention WHERE evidence_id IN ('old-personal','old-task');") == '2'
}
report = {'database': database, 'port': args.port, 'migrations': names, 'checks': checks}
output = root/'docs/implementation/business-assets-20261119/mysql-migration-verification.json'
output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(json.dumps(report, ensure_ascii=False))
if not all(checks.values()):
    raise SystemExit(1)
