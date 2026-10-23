"""Exercise real migration/views and production reconciliation SQL in a disposable test schema.

Reuses an already-running isolated MySQL container; never starts/stops Docker or reads production data.
"""
from pathlib import Path
import argparse
import json
import re
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--container', default='msp-sql-optimization-mysql')
parser.add_argument('--output', default=str(ROOT / 'outputs/plan-sync-mysql-20261023.json'))
args = parser.parse_args()
schema = 'msp_plan_fix_' + uuid.uuid4().hex[:12]
assert re.fullmatch(r'msp_plan_fix_[a-f0-9]{12}', schema)
checks = []


def sql(statement, scoped=True):
    prefix = f'USE `{schema}`;\n' if scoped else ''
    result = subprocess.run(['docker', 'exec', '-i', args.container, 'sh', '-c',
                             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names --default-character-set=utf8mb4'],
                            input=(prefix + statement).encode('utf-8'), capture_output=True, timeout=40)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    return result.stdout.decode('utf-8').strip()


def expect(name, statement, expected):
    actual = sql(statement)
    assert actual == expected, (name, expected, actual)
    checks.append(name)


def definition(file, table):
    source = (ROOT / file).read_text(encoding='utf-8')
    match = re.search(r'CREATE TABLE IF NOT EXISTS\s+' + table + r'\s*\(.*?\n\)\s*[^;]*;', source, re.S | re.I)
    assert match, table
    return match.group()


created = False
try:
    sql(f'CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;', False)
    created = True
    # test_plan is taken verbatim from the repository migration: no invented deleted column.
    sql(definition('backend/framework/domain/src/main/resources/migration/3.0.0/ddl/V3.0.0_3__test_plan.sql', 'test_plan'))
    sql('''
    CREATE TABLE functional_case(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),deleted BIT DEFAULT 0,
      latest BIT DEFAULT 1,last_execute_result VARCHAR(30),last_execute_time BIGINT,last_execute_user VARCHAR(50));
    CREATE TABLE test_plan_functional_case(id VARCHAR(50) PRIMARY KEY,test_plan_id VARCHAR(50),functional_case_id VARCHAR(50),
      create_time BIGINT,create_user VARCHAR(50),execute_user VARCHAR(50),pos BIGINT,test_plan_collection_id VARCHAR(50),
      last_exec_result VARCHAR(30),last_exec_time BIGINT);
    CREATE TABLE bug(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),deleted BIT DEFAULT 0);
    ''')
    sql(definition('backend/framework/domain/src/main/resources/migration/3.0.0/ddl/V3.0.0_7__bug_management.sql', 'bug_relation_case'))
    migrations = ROOT / 'backend/framework/domain/src/main/resources/migration/3.7.2/ddl'
    sql((migrations / 'V3.7.2_120__plan_case_effective_views.sql').read_text(encoding='utf-8'))
    sql((migrations / 'V3.7.2_121__plan_case_sync_jobs.sql').read_text(encoding='utf-8'))
    checks.append('both incremental migrations execute on MySQL')
    sql('''
    INSERT INTO test_plan(id,project_id,group_id,module_id,name,status,type,create_time,create_user)
      VALUES ('active','p','NONE','root','active','NOT_ARCHIVED','TEST_PLAN',1,'u'),
             ('archived','p','NONE','root','archived','ARCHIVED','TEST_PLAN',1,'u'),
             ('group','p','NONE','root','group','NOT_ARCHIVED','GROUP',1,'u');
    INSERT INTO functional_case VALUES ('c','p',0,0,'ERROR',42,'runner');
    INSERT INTO test_plan_functional_case VALUES
      ('r1','active','c',1,'u','assigned',1,'collection','PENDING',NULL),
      ('r2','archived','c',1,'u','assigned',1,'collection','SUCCESS',10);
    INSERT INTO bug VALUES ('b1','p',0),('b2','p',0);
    INSERT INTO bug_relation_case(id,case_id,bug_id,case_type,create_user,create_time,update_time)
      VALUES ('source','c','b1','FUNCTIONAL','u',1,1);
    ''')
    expect('active plan summary follows source immediately and preserves assignment',
           "SELECT CONCAT(last_exec_result,':',last_exec_time,':',execute_user) FROM plan_functional_case_effective WHERE id='r1'", 'ERROR:42:assigned')
    expect('archived summary remains frozen',
           "SELECT CONCAT(last_exec_result,':',last_exec_time) FROM plan_functional_case_effective WHERE id='r2'", 'SUCCESS:10')
    expect('non-latest pinned case remains associated', "SELECT COUNT(*) FROM plan_functional_case_effective WHERE id='r1'", '1')
    expect('new source defect is visible before materialization',
           "SELECT COUNT(*) FROM plan_case_effective_bug_relation WHERE test_plan_case_id='r1'", '1')
    service = (ROOT / 'backend/services/test-plan/src/main/java/io/metersphere/plan/service/TestPlanDefectSyncService.java').read_text(encoding='utf-8')
    statements = re.findall(r'jdbc\.update\("""(.*?)"""', service, re.S)
    assert len(statements) == 2

    def bind(statement, values):
        for value in values:
            statement = statement.replace('?', "'" + str(value).replace("'", "''") + "'", 1)
        assert '?' not in statement
        return statement

    delete = bind(statements[0], ['active', 'p'])
    insert = bind(statements[1], ['FUNCTIONAL', 'u', 2, 2, 'FUNCTIONAL', 'active', 'p'])
    sql(delete + ';' + insert + ';' + insert + ';')
    expect('materialization is idempotent and records origin',
           "SELECT COUNT(*) FROM bug_relation_case WHERE test_plan_case_id='r1' AND sync_origin='SOURCE_SYNC'", '1')
    sql("INSERT INTO bug_relation_case(id,case_id,bug_id,case_type,test_plan_id,test_plan_case_id,create_user,create_time,update_time) VALUES ('manual','c','b1','FUNCTIONAL','active','r1','u',1,1);")
    expect('manual and source origins render one bug', "SELECT COUNT(*) FROM plan_case_effective_bug_relation WHERE test_plan_case_id='r1'", '1')
    sql("DELETE FROM bug_relation_case WHERE id='source';")
    sql(delete + ';')
    expect('source unlink retains independent manual link', "SELECT id FROM bug_relation_case WHERE test_plan_case_id='r1'", 'manual')
    sql("UPDATE functional_case SET last_execute_result='SUCCESS',last_execute_time=99 WHERE id='c';")
    expect('subsequent source changes need no manual synchronization',
           "SELECT CONCAT(last_exec_result,':',last_exec_time) FROM plan_functional_case_effective WHERE id='r1'", 'SUCCESS:99')
    access = (ROOT / 'backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentBusinessAccess.java').read_text(encoding='utf-8')
    query = re.search(r'"(SELECT COUNT\(\*\) FROM test_plan WHERE[^"]+)"', access).group(1)
    expect('MCP plan authorization SQL uses actual plan schema', bind(query, ['active', 'p']), '1')
    expect('MCP does not accept a group as a plan', bind(query, ['group', 'p']), '0')
    sql("INSERT INTO plan_case_sync_job(id,project_id,user_id,request_id,state,create_time,update_time) VALUES ('j','p','u','req','QUEUED',1,1);")
    sql("INSERT INTO plan_case_sync_item(job_id,plan_id) VALUES ('j','active');")
    expect('durable job item starts pending', "SELECT state FROM plan_case_sync_item WHERE job_id='j'", 'PENDING')
    sql("CREATE TABLE permission_resource(code VARCHAR(100) PRIMARY KEY,enabled BIT NOT NULL);")
    sql("INSERT INTO permission_resource VALUES ('FUNCTIONAL_CASE_AI_GENERATE_TAB',b'1'),('FUNCTIONAL_CASE_PAGE',b'1');")
    sql((ROOT / 'backend/framework/domain/src/main/resources/migration/3.7.2/dml/V3.7.2_122__hide_functional_ai_generate_tab_resource.sql').read_text(encoding='utf-8'))
    expect('removed generation tab resource is disabled without disabling case page',
           "SELECT GROUP_CONCAT(CONCAT(code,':',enabled+0) ORDER BY code) FROM permission_resource",
           'FUNCTIONAL_CASE_AI_GENERATE_TAB:0,FUNCTIONAL_CASE_PAGE:1')
finally:
    if created:
        assert re.fullmatch(r'msp_plan_fix_[a-f0-9]{12}', schema)
        sql(f'DROP DATABASE `{schema}`;', False)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({'checksPassed': checks, 'count': len(checks), 'schemaCleaned': created}, ensure_ascii=False, indent=2), encoding='utf-8')

print(json.dumps({'passed': len(checks), 'evidence': str(output)}, ensure_ascii=False))
