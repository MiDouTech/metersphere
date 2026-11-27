from pathlib import Path
import re,hashlib,json
root=Path(__file__).resolve().parents[2]
base=root/'backend/services/agent-integration/src/main/java/io/metersphere/agent'
paths=[]
for folder,names in {
'controller':['TestAssetController','BusinessAssetController','SharedConfigurationController','ExternalExecutorController','EvidenceCatalogController'],
'service':['TestAssetCatalogService','TestAssetGovernanceService','TestAssetVersionService','TestAssetPublicationListener','BusinessAssetService','SharedConfigurationService','ExternalExecutorService','EvidenceCatalogService'],
'dto':['BusinessAssetSubmission','SharedConfigurationSave'],
'tool':['AgentMcpToolSchemas','AgentMcpToolRegistry','AgentMcpToolHandler','BuiltinAgentMcpToolConfig','AgentMcpToolExposure'],
'tool/configuration':['SharedConfigurationMcpTools'],
}.items():
 for n in names: paths.append(base/folder/(n+'.java'))
paths += [base/'mapper/TestAssetMapper.xml',base/'mapper/TestAssetMapper.java']
paths += list((root/'frontend/src/views/test-asset').glob('*.vue'))
paths += list((root/'frontend/src/views/test-asset/components').glob('*Versions.vue'))
paths += [root/'frontend/src/router/routes/modules/testAsset.ts',root/'frontend/src/api/modules/business-assets.ts']
paths += list((root/'backend/services/project-management/src/main/java/io/metersphere/project/service').glob('File*Service.java'))
out=['# 技术分析源码定位附录','', '自动静态索引；不是实现或运行验证。基线188d47397c1d15c89ebb8268e38c342a2f77babc。','']
records=[]
for p in paths:
 if not p.exists():raise RuntimeError(p)
 rel=p.relative_to(root).as_posix(); data=p.read_bytes(); lines=data.decode('utf-8-sig').splitlines()
 hits=[]
 for i,line in enumerate(lines,1):
  if re.search(r'^\s*(public|private|protected)\s+.*\(',line) or re.search(r'\b(?:async\s+)?function\s+\w+\s*\(',line) or re.search(r'@(Get|Post|Put|Delete|Request)Mapping',line) or re.search(r'<(?:select|sql|insert|update|delete)\s+id=',line):
   hits.append((i,line.strip()))
 records.append({'path':rel,'sha256':hashlib.sha256(data).hexdigest(),'symbols':len(hits)})
 out+=['## '+rel,'','SHA256: '+records[-1]['sha256'],'','```text']+[f'{i}: {s}' for i,s in hits]+['```','']
dest=root/'docs/implementation'
(dest/'测试资产全局化-源码定位附录-20261127.md').write_text('\n'.join(out),encoding='utf-8')
(dest/'asset-global-analysis-source-index.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'Indexed {len(records)} existing files; {sum(r["symbols"] for r in records)} method/route/query anchors')
