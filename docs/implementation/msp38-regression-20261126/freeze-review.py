from pathlib import Path
import subprocess,hashlib,json,zipfile,difflib
root=Path.cwd();dest=root/'docs/implementation/msp38-regression-20261126'
paths=subprocess.check_output(['git','diff','--name-only'],text=True).splitlines()
paths.append('backend/services/case-management/src/main/java/io/metersphere/functional/excel/util/AssetStepSnapshot.java')
paths=sorted(set(paths))
records=[dict(path=p,sha256=hashlib.sha256((root/p).read_bytes()).hexdigest()) for p in paths]
(dest/'review-source-sha256.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
patch=subprocess.check_output(['git','diff','--binary'])
p=paths[0]
new='backend/services/case-management/src/main/java/io/metersphere/functional/excel/util/AssetStepSnapshot.java'
addition='diff --git a/'+new+' b/'+new+'\nnew file mode 100644\n'+''.join(difflib.unified_diff([], (root/new).read_text(encoding='utf-8').splitlines(keepends=True),fromfile='/dev/null',tofile='b/'+new))
(dest/'changes.patch').write_bytes(patch+addition.encode())
items=[root/p for p in paths]+[p for p in dest.iterdir() if p.is_file() and p.name not in ('bundle-manifest.json',) and p.suffix!='.zip']
manifest=dict(base=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),patchSha256=hashlib.sha256((dest/'changes.patch').read_bytes()).hexdigest(),files=[dict(path=p.relative_to(root).as_posix(),sha256=hashlib.sha256(p.read_bytes()).hexdigest(),bytes=p.stat().st_size) for p in sorted(set(items))])
(dest/'bundle-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
bundle=dest/'MSP38-regression-review-v1.0-20261126.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(set(items+[dest/'bundle-manifest.json'])):z.write(p,p.relative_to(root).as_posix())
print(json.dumps(dict(files=len(paths),patchSha256=manifest['patchSha256'],zipSha256=hashlib.sha256(bundle.read_bytes()).hexdigest(),bundle=str(bundle))))
