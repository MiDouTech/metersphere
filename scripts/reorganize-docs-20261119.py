"""One-time documentation migration; validates contents before removing empty doc/."""
from pathlib import Path
import hashlib
import json
import os
import re

ROOT = Path(__file__).resolve().parents[1]
mapping = {}
def plan(src, dst):
    src, dst = ROOT / src, ROOT / dst
    assert src.is_file() and dst.is_relative_to(ROOT / 'docs')
    assert not dst.exists(), f'Collision: {dst}'
    mapping[src] = dst

for p in (ROOT / 'doc').rglob('*'):
    if not p.is_file():
        continue
    if p.name.startswith('report.'):
        category = 'weekly_report'
    elif p.name.startswith('metersphere_prod-'):
        category = 'operations/production-data-20260920'
    elif p.name.startswith('platform-optimization'):
        category = 'implementation/platform-optimization-20261021'
    elif p.name.startswith(('case-export', 'docker-startup')):
        category = 'troubleshooting'
    else:
        category = 'overview'
    plan(p.relative_to(ROOT), Path('docs') / category / p.name)

# Historical plans, requirements and scope baselines; execution reports stay in place.
for p in (ROOT / 'docs').rglob('*'):
    if not p.is_file() or 'product_md' in p.parts or p.suffix not in ('.md', '.docx'):
        continue
    rel = p.relative_to(ROOT / 'docs')
    if rel.parts[0] not in ('summary', 'task', 'develop_logs') and len(rel.parts) != 1:
        continue
    if re.match(r'task\d', p.name):
        continue
    if re.search(r'方案|需求文档|实现需求|下线需求|requirements|product-scope-baseline|milestone|里程碑|transformation-plan|simplification-plan|permission-fix-plan|remediation-plan|refactor-plan|redesign-solution|integration-plan|refactoring-plan|optimization-plan|implementation-plan', p.name, re.I) or p.name == 'msp-module-function-disposition-table.md':
        plan(p.relative_to(ROOT), Path('docs/product_md/history') / rel)
        assets = p.with_suffix('.assets')
        if assets.is_dir():
            for a in assets.rglob('*'):
                if a.is_file() and a not in mapping:
                    plan(a.relative_to(ROOT), Path('docs/product_md/history') / a.relative_to(ROOT / 'docs'))

hashes = {p: hashlib.sha256(p.read_bytes()).hexdigest() for p in mapping}
texts = {}
for p in ROOT.rglob('*.md'):
    if any(x in p.parts for x in ('.git', 'node_modules', 'target', '.venv')):
        continue
    try:
        texts[p] = p.read_bytes().decode('utf-8')
    except UnicodeDecodeError:
        continue

for src, dst in mapping.items():
    dst.parent.mkdir(parents=True, exist_ok=True)
    src.rename(dst)
    assert hashlib.sha256(dst.read_bytes()).hexdigest() == hashes[src]

updated = []
link_re = re.compile(r'(!?\[[^\]\n]*\]\()([^\n)]+)(\))')
for old, content in texts.items():
    new = mapping.get(old, old)
    def link(m):
        raw = m[2]
        if re.match(r'[a-zA-Z]+:|#|/', raw):
            return m[0]
        target, sep, anchor = raw.partition('#')
        angle = target.startswith('<') and target.endswith('>')
        target = target.strip('<>')
        resolved = (old.parent / target).resolve()
        dest = mapping.get(resolved, resolved)
        if resolved not in mapping and old == new:
            return m[0]
        if not dest.exists():
            return m[0]
        relative = os.path.relpath(dest, new.parent).replace('\\', '/')
        if angle:
            relative = '<' + relative + '>'
        return m[1] + relative + (sep + anchor if sep else '') + m[3]
    revised = link_re.sub(link, content)
    for src, dst in mapping.items():
        a, b = src.relative_to(ROOT).as_posix(), dst.relative_to(ROOT).as_posix()
        revised = revised.replace(a, b).replace(a.replace('/', '\\'), b.replace('/', '\\'))
    if revised != content:
        new.write_bytes(revised.encode('utf-8'))
        updated.append(new.relative_to(ROOT).as_posix())

doc = (ROOT / 'doc').resolve()
assert doc == ROOT / 'doc' and doc.is_relative_to(ROOT)
assert not list(doc.rglob('*')), 'doc is not empty; refusing deletion'
doc.rmdir()
report = {
    'date': '2026-11-19',
    'files': [{'from': s.relative_to(ROOT).as_posix(), 'to': d.relative_to(ROOT).as_posix(), 'originalSha256': hashes[s], 'finalSha256': hashlib.sha256(d.read_bytes()).hexdigest()} for s, d in mapping.items()],
    'referenceUpdatedFiles': updated,
    'verification': {'allMovedBytesVerifiedBeforeReferenceUpdates': True, 'docRemoved': not doc.exists()}
}
(ROOT / 'docs/document-migration-20261119.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'moved': len(mapping), 'referencesUpdated': len(updated), 'docRemoved': not doc.exists()}, ensure_ascii=False))
