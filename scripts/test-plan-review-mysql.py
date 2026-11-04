"""Run development SQL tests in a new isolated MySQL data directory, then stop that exact server.

Uses an already installed binary. Never reads service configuration or connects to its data directory.
Does not deploy, run formal acceptance, install software, or change system/user environment settings.
"""
import argparse
import os
import pathlib
import socket
import subprocess
import sys
import time
import uuid

ROOT=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--mysqld',type=pathlib.Path,default=pathlib.Path(r'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe'))
parser.add_argument('--native-tests',action='store_true',help='Also run the existing import coordinator development tests with Maven')
parser.add_argument('--native-only',action='store_true',help='Run native development tests without repeating the separately recorded core suite')
parser.add_argument('--native-test-filter',default='NotificationIsolationTests,AgentMcpCreateSchemaTests,ImportFollowupCoordinatorMySqlTests,PlanReviewCopyCoordinatorMySqlTests,PlanReviewNoticeChannelMySqlTests,PlanReviewHubOperationMySqlTests,PlanReviewLegacyImportMySqlTests,PlanReviewEntryBoundaryMySqlTests')
parser.add_argument('--port',type=int,default=13326)
args=parser.parse_args()
if args.native_only:args.native_tests=True
if not __import__('re').fullmatch(r'[A-Za-z0-9_,]+',args.native_test_filter):parser.error('Native filter must contain test class names separated by commas')
binary=args.mysqld.resolve(strict=True)
if args.port<1024 or args.port in (3306,33060):parser.error('Use a separate development test port')
with socket.socket() as preflight:preflight.bind(('127.0.0.1',args.port))
run=(ROOT/'.build'/'mysql-selftest'/uuid.uuid4().hex[:8]).resolve()
if not run.is_relative_to((ROOT/'.build').resolve()):raise RuntimeError('Workspace boundary mismatch')
run.mkdir(parents=True)
data=run/'data'; data.mkdir()
connection=run/'connection.properties'
jar=ROOT/'.build/m2/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar'
if not jar.is_file():raise RuntimeError('Compile the existing project dependency set first; MySQL connector is missing')
flags=getattr(subprocess,'CREATE_NO_WINDOW',0)
server=None
with (run/'server-console.log').open('wb') as log:
    subprocess.run([str(binary),'--no-defaults','--initialize-insecure','--basedir='+str(binary.parent.parent),
                    '--datadir='+str(data),'--console'],stdout=log,stderr=subprocess.STDOUT,check=True,creationflags=flags)
    subprocess.run(['javac','-encoding','UTF-8','-d',str(run),str(ROOT/'scripts/java/PlanReviewMysqlBootstrap.java')],check=True,creationflags=flags)
    bootstrap=['java','-cp',os.pathsep.join((str(run),str(jar))),'PlanReviewMysqlBootstrap',str(args.port),str(data),str(connection)]
    server=subprocess.Popen([str(binary),'--no-defaults','--basedir='+str(binary.parent.parent),'--datadir='+str(data),
                '--port='+str(args.port),'--bind-address=127.0.0.1','--mysqlx=OFF','--skip-log-bin','--local-infile=OFF',
                '--innodb-buffer-pool-size=64M','--max-connections=25','--console'],stdout=log,stderr=subprocess.STDOUT,creationflags=flags)
    try:
        deadline=time.monotonic()+60
        while time.monotonic()<deadline:
            if server.poll() is not None:raise RuntimeError('The isolated server exited during startup; existing services were not used')
            if b'ready for connections' in (run/'server-console.log').read_bytes():break
            time.sleep(.25)
        else:raise RuntimeError('The isolated server did not become ready')
        setup=subprocess.run(bootstrap,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=30,creationflags=flags)
        (run/'bootstrap.log').write_bytes(setup.stdout)
        print(setup.stdout.decode('utf-8',errors='replace').strip(),flush=True)
        setup.check_returncode()
        result=subprocess.CompletedProcess([],0)
        if not args.native_only:
            with (run/'suite.log').open('wb') as suite:
                result=subprocess.run([sys.executable,str(ROOT/'scripts/test-plan-review-rules.py'),'--mysql-config',str(connection)],stdout=suite,stderr=subprocess.STDOUT,cwd=ROOT,creationflags=flags)
            output=(run/'suite.log').read_text(encoding='utf-8',errors='replace')
            print('\n'.join(output.splitlines()[-22:]),flush=True)
            print('Isolated SQL suite log: '+str(run/'suite.log'),flush=True)
        else:print('Core SQL suite not run (explicit native-only mode).',flush=True)
        if args.native_tests and result.returncode==0:
            candidates=list(pathlib.Path(os.environ['USERPROFILE']).glob('.m2/wrapper/dists/apache-maven-*-bin/*/apache-maven-*/bin/mvn.cmd'))
            if not candidates:raise RuntimeError('Existing Maven binary not found')
            command=[str(sorted(candidates)[-1]),'-o','-Dmaven.repo.local='+str(ROOT/'.build/m2'),'-Dfile.encoding=UTF-8',
                     '-DskipAntRunForJenkins=true','-Dtest='+args.native_test_filter,'-Dsurefire.failIfNoSpecifiedTests=false',
                     '-Dquality.mysql=true','-Dplan.review.mysql.config='+str(connection),'-pl','backend/services/agent-integration','-am','test']
            with (run/'native-suite.log').open('wb') as native:
                result=subprocess.run(command,stdout=native,stderr=subprocess.STDOUT,cwd=ROOT,creationflags=flags)
            print('Native coordinator suite exit: '+str(result.returncode),flush=True)
            print('Native coordinator suite log: '+str(run/'native-suite.log'),flush=True)

    finally:
        if server.poll() is None:
            try:subprocess.run([*bootstrap,'--shutdown'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=True,timeout=15,creationflags=flags)
            except (subprocess.SubprocessError,OSError):pass
            try:server.wait(timeout=15)
            except subprocess.TimeoutExpired:
                if os.name=='nt':subprocess.run(['taskkill','/PID',str(server.pid),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=False)
                else:server.terminate()
                server.wait(timeout=10)
        if connection.exists():connection.unlink()
        print('Isolated MySQL process stopped; temporary connection removed.',flush=True)
sys.exit(result.returncode)
