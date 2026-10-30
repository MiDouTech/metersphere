import os, pathlib, subprocess, xml.etree.ElementTree as ET, json, time, sys, shutil
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
root=next(p for p in pathlib.Path(__file__).resolve().parents if (p/'pom.xml').exists() and (p/'backend').is_dir())
os.chdir(root)
out=root/'.codex-tmp/mcp-remediation-focused-20261030'
out.mkdir(exist_ok=True)
classes=out/'classes'; classes.mkdir(exist_ok=True)
xml=root/'backend/services/agent-integration/target/surefire-reports/TEST-io.metersphere.agent.service.AgentDirectExecutionTests.xml'
props={p.attrib['name']:p.attrib['value'] for p in ET.parse(xml).findall('./properties/property')}
cp=props['java.class.path'].split(';')
cp.extend(str(p) for p in sorted((root/'backend/services').glob('*/target/test-classes')))
repo=pathlib.Path.home()/'.m2/repository'
cp.extend(str(p) for p in sorted((root/'backend/services').glob('*/target/metersphere-*.jar')) if not p.name.endswith(('-sources.jar','-tests.jar')))
cp.extend(str(p) for p in sorted((repo/'io/metersphere').glob('*/3.x/*.jar')) if not p.name.endswith(('-sources.jar','-javadoc.jar','-tests.jar')))
for component in ['junit-platform-launcher','junit-platform-engine','junit-platform-commons']:
    jars=sorted((repo/'org/junit/platform'/component).glob('*/*.jar'))
    if jars: cp.append(str(jars[-1]))
cp=str(classes)+';'+ ';'.join(dict.fromkeys(cp))
lombok=sorted((repo/'org/projectlombok/lombok').glob('*/*.jar'))[-1]
paths=subprocess.check_output(['git','diff','--name-only'],text=True).splitlines()+subprocess.check_output(['git','ls-files','--others','--exclude-standard'],text=True).splitlines()
sources=[p for p in paths if p.endswith('.java')]
for path in paths:
    if '/src/main/java/' in path and path.endswith('.xml'):
        target=classes/path.split('/src/main/java/',1)[1];target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,target)
main=[p for p in sources if '/src/main/' in p]
tests=[p for p in sources if '/src/test/' in p]
runner=out/'FocusedTestRunner.java'
runner.write_text('''import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;
public class FocusedTestRunner {
 public static void main(String[] args) {
  var request=LauncherDiscoveryRequestBuilder.request();
  for(String name:args)request.selectors(DiscoverySelectors.selectClass(name));
  var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();
  launcher.registerTestExecutionListeners(listener);launcher.execute(request.build());
  var writer=new java.io.PrintWriter(System.out,true);listener.getSummary().printTo(writer);listener.getSummary().printFailuresTo(writer);
  System.exit(listener.getSummary().getTotalFailureCount()==0?0:1);
 }
}''',encoding='utf-8')
def execute(label,cmd):
    started=time.time()
    with (out/(label+'.log')).open('w',encoding='utf-8') as log:
        proc=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT)
        while proc.poll() is None:
            time.sleep(3)
            free=int(subprocess.check_output(['powershell','-NoProfile','-Command','(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory'],text=True).strip())/1024
            if free<300:
                subprocess.run(['taskkill','/PID',str(proc.pid),'/T','/F'],stdout=subprocess.DEVNULL)
                raise RuntimeError('Resource guard stopped '+label)
    print(label,'exit',proc.returncode,'seconds',round(time.time()-started),flush=True)
    raw=(out/(label+'.log')).read_bytes()
    try: text=raw.decode('utf-8')
    except UnicodeDecodeError: text=raw.decode('gb18030',errors='replace')
    print(text[-14000:],flush=True)
    if proc.returncode: raise SystemExit(proc.returncode)
def compile_group(label,files):
    args=['-encoding','UTF-8','--release','21','-parameters','-implicit:none','-cp',cp,'-processorpath',str(lombok),'-processor','lombok.launch.AnnotationProcessorHider$AnnotationProcessor','-d',str(classes)]+files
    argfile=out/(label+'.args')
    argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='utf-8')
    execute(label,['javac','-J-Xmx192m','-J-XX:MaxMetaspaceSize=128m','-J-XX:+UseSerialGC','@'+str(argfile)])
if '--run-only' not in sys.argv and '--tests-only' not in sys.argv:
    compile_group('main-compile',main)
if '--run-only' not in sys.argv:
    compile_group('test-compile',tests+[str(runner)])
names=['io.metersphere.agent.service.'+n for n in ['AgentCaseInputValidationTests','AgentCaseBatchValidationTests','AgentAttachmentDeleteContractTests','AgentSafeErrorMapperTests','AgentDirectExecutionTests','AgentExternalImageServiceTests','AgentBugTransferTests','AgentAttachmentIntentPermissionTests','AgentPlanDetailCountTests','AgentBugCreateContractTests','AgentBugWriteRelationTests']]
names+=['io.metersphere.agent.tool.'+n for n in ['AgentMcpLeaseContractTests','AgentBugAttachmentUploadContractTests','AgentMcpToolContractTests','AgentMcpCreateSchemaTests']]
names+=['io.metersphere.agent.tool.PersonalExecutionProjectReferenceTests']
names+=['io.metersphere.bug.service.BugTransitionFreshResponseTests']
names+=['io.metersphere.bug.service.BugExportCustomFieldMappingTests']
names+=['io.metersphere.functional.service.'+n for n in ['FunctionalCaseExportNullFieldTests','FunctionalCaseExportValidationTests','FunctionalCaseExportRowMapperTests']]
argfile=out/'run.args'
argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in ['--add-opens=java.base/java.util=ALL-UNNAMED','-cp',cp,'FocusedTestRunner']+names),encoding='utf-8')
execute('tests',['java','-Xmx192m','-XX:MaxMetaspaceSize=128m','-XX:+UseSerialGC','@'+str(argfile)])
(out/'scope.json').write_text(json.dumps({'main':main,'tests':tests,'executedClasses':names},indent=2),encoding='utf-8')
