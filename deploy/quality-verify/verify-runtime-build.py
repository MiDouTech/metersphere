"""Reject runtime retries unless the cached package matches current source and passed evidence."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import re

MODULES = {'sdk', 'system-setting', 'project-management', 'api-test', 'bug-management',
           'case-management', 'test-plan', 'dashboard', 'agent-integration', 'app'}


def validate_source_inputs(source_sha):
    # backend/frontend trees are checked against the source build record below.
    # Compare tracked outer build inputs; Maven-generated target POMs are not inputs.
    for name in ('pom.xml', '.mvn', 'mvnw', 'mvnw.cmd'):
        old = subprocess.check_output(['git', 'rev-parse', f'{source_sha}:{name}'], text=True).strip()
        current = subprocess.check_output(['git', 'rev-parse', f'HEAD:{name}'], text=True).strip()
        if old != current:
            raise ValueError(f'Build input changed: {name}')


def validate_frontend_artifact(record, current_tree):
    if record.get('frontendTree') != current_tree or not re.fullmatch(r'[0-9a-f]{40}', record.get('sourceCommit', '')):
        raise ValueError('Current production frontend artifact identity mismatch')
    source = subprocess.check_output(['git', 'rev-parse', f"{record['sourceCommit']}:frontend"], text=True).strip()
    if source != current_tree:
        raise ValueError('Frontend artifact source does not match current frontend')


def validate(record, summary, source_sha, source_run, backend, frontend, inputs, jar_sha):
    expected = dict(sourceCommit=source_sha, sourceRun=source_run, backendTree=backend,
                    frontendTree=frontend, buildInputsHash=inputs, jarSha256=jar_sha)
    if any(record.get(key) != value for key, value in expected.items()):
        raise ValueError('Verified package source or checksum mismatch')
    if summary.get('passed') is not True or set(summary.get('modules', {})) != MODULES:
        raise ValueError('Complete ten-module coverage required')
    if summary.get('missingModules') or summary.get('reportIntegrityErrors'):
        raise ValueError('Incomplete or inconsistent test evidence')
    for module, counts in summary['modules'].items():
        if counts.get('tests', 0) <= 0 or any(counts.get(key, -1) != 0 for key in ('failures', 'errors', 'skipped')):
            raise ValueError(f'Unsuccessful module: {module}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('source-sha', 'source-run', 'inputs-hash'):
        parser.add_argument('--' + key, required=True)
    args = parser.parse_args()
    validate_source_inputs(args.source_sha)
    tree = lambda name: subprocess.check_output(['git', 'rev-parse', f'HEAD:{name}'], text=True).strip()
    jar = Path('backend/app/target/app-3.x.jar')
    validate_frontend_artifact(json.loads(Path('.ci-frontend-build.json').read_text()), tree('frontend'))
    source_frontend = subprocess.check_output(['git', 'rev-parse', f'{args.source_sha}:frontend'], text=True).strip()
    validate(json.loads(Path('.ci-backend-build.json').read_text()),
             json.loads(Path('.ci-test-summary.json').read_text()), args.source_sha, args.source_run,
             tree('backend'), source_frontend, args.inputs_hash, hashlib.file_digest(jar.open('rb'), 'sha256').hexdigest())
    print('Verified unchanged backend package and separately built current production frontend; runtime checks must still execute')


if __name__ == '__main__':
    main()
