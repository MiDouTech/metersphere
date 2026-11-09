"""Focused offline tests of real legacy POST and secret classes; no external webhook is contacted."""
import argparse
import os
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--baseline',action='store_true',help='Reproduce ClientPost execute failure on the fixed pre-change commit')
args=parser.parse_args()
build=ROOT/'.build/webhook-reuse';build.mkdir(parents=True,exist_ok=True)
cache=Path.home()/'.m2/repository'
def jar(path):
    found=sorted(p for p in (cache/path).glob('*/*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))
    if not found: raise RuntimeError('Required existing dependency missing: '+path)
    return found[-1]
junit=ROOT/'.build/plan-review-rules/junit-platform-console-standalone-1.11.4.jar'
dependencies=[junit,*[jar(p) for p in ['org/apache/httpcomponents/client5/httpclient5','org/apache/httpcomponents/core5/httpcore5','org/apache/httpcomponents/core5/httpcore5-h2','org/apache/commons/commons-lang3','org/slf4j/slf4j-api','com/fasterxml/jackson/core/jackson-core','com/fasterxml/jackson/core/jackson-annotations','com/fasterxml/jackson/core/jackson-databind','com/fasterxml/jackson/datatype/jackson-datatype-jsr310','org/springframework/spring-context','org/springframework/spring-core','org/springframework/spring-beans']]]
dependencies.append(jar('jakarta/annotation/jakarta.annotation-api'))
sdk=ROOT/'backend/framework/sdk/src/main/java/io/metersphere/sdk'
system=ROOT/'backend/services/system-setting/src'
sources=[sdk/'util/JSON.java',sdk/'util/LogUtils.java']
client=system/'main/java/io/metersphere/system/notice/utils/ClientPost.java'
test=system/'test/java/io/metersphere/system/notice/utils/ClientPostTests.java'
classes=build/('baseline-classes' if args.baseline else 'classes');classes.mkdir(exist_ok=True)
if args.baseline:
    old=build/'ClientPost.java';old.write_bytes(subprocess.check_output(['git','show','c066660239b05b76dacccea2f9b1a4c6bc271db8:backend/services/system-setting/src/main/java/io/metersphere/system/notice/utils/ClientPost.java'],cwd=ROOT))
    regression=build/'ClientPostTests.java';content=test.read_text(encoding='utf-8');content=content[:content.index('    @Test void sharedClient')]+ '}\n';regression.write_text(content,encoding='utf-8')
    sources += [old,regression]
else:
    sources += [client,test,sdk/'util/AesGcmCodec.java',sdk/'exception/MSException.java',sdk/'exception/IResultCode.java',sdk/'util/Translator.java']
    sources += [system/p for p in ['main/java/io/metersphere/system/wecombot/WecomSecretService.java','main/java/io/metersphere/system/wecombot/WecomRuntimeSecrets.java','test/java/io/metersphere/system/wecombot/SecretCodecCompatibilityTests.java']]
    sources += [system/'main/java/io/metersphere/system/notice/utils'/name for name in ['WeComClient.java','DingClient.java','LarkClient.java']]
cp=os.pathsep.join(map(str,dependencies))
subprocess.run(['javac','-encoding','UTF-8','--release','21','-cp',cp,'-d',str(classes),*map(str,sources)],check=True)
env=dict(os.environ,MS_WECOM_SECRET_MASTER_KEY='isolated-test-master-key',MSP_REUSE_TEST_REFERENCE='isolated-reference')
result=subprocess.run(['java','-jar',str(junit),'execute','--class-path',os.pathsep.join([str(classes),cp]),'--scan-class-path','--fail-if-no-tests','--disable-ansi-colors','--details','tree'],env=env)
raise SystemExit(result.returncode)
