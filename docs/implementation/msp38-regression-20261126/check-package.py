from pathlib import Path
from zipfile import ZipFile
import hashlib,json,io
root=Path.cwd();module=root/'backend/services/case-management/target'
items=['io/metersphere/functional/service/FunctionalCaseFileService.class','io/metersphere/functional/excel/util/AssetStepSnapshot.class','io/metersphere/functional/excel/listener/FunctionalCaseImportEventListener.class','io/metersphere/functional/excel/util/CaseAssetExcelStepMetadata.class']
with ZipFile(root/'backend/app/target/app-3.x.jar') as app:
 lib=next(n for n in app.namelist() if n.startswith('BOOT-INF/lib/metersphere-case-management-'))
 with ZipFile(io.BytesIO(app.read(lib))) as packaged:
  results=[dict(path=p,match=packaged.read(p)==(module/'classes'/p).read_bytes()) for p in items]
assert all(x['match'] for x in results),results
print(json.dumps(results));(root/'docs/implementation/msp38-regression-20261126/package-class-check.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
