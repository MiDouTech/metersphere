"""Build the isolated acceptance image from a packaged JAR and verify its frontend/migrations."""
import argparse
import hashlib
import io
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', default='msp-implementation20261022:verify')
    parser.add_argument('--alpine-mirror', default='https://dl-cdn.alpinelinux.org/alpine')
    args = parser.parse_args()
    if not re.fullmatch(r'msp-implementation\d+:[\w.-]+', args.tag):
        parser.error('Only isolated implementation image tags are accepted')
    if not re.fullmatch(r'https://[A-Za-z0-9.-]+/alpine/?', args.alpine_mirror):
        parser.error('An HTTPS Alpine repository is required')
    root = Path(__file__).resolve().parents[1]
    jars = [p for p in (root / 'backend/app/target').glob('*.jar') if not re.search(r'tests|sources|javadoc', p.name)]
    if len(jars) != 1:
        raise RuntimeError('Expected exactly one packaged application JAR')
    with jars[0].open('rb') as source:
        digest = hashlib.file_digest(source, 'sha256').hexdigest()
    context = root / 'backend/app/target' / ('implementation-image-' + digest[:16])
    with zipfile.ZipFile(jars[0]) as jar:
        for member in jar.namelist():
            if not re.fullmatch(r'BOOT-INF/lib/metersphere-[^/]+\.jar', member):
                continue
            matches = list((root / 'backend').glob('**/target/' + Path(member).name))
            if len(matches) > 1:
                raise RuntimeError('Ambiguous reactor dependency ' + member)
            if matches and matches[0].read_bytes() != jar.read(member):
                raise RuntimeError('Stale packaged reactor dependency ' + member)
        if jar.read('BOOT-INF/classes/static/index.html') != (root / 'frontend/dist/index.html').read_bytes():
            raise RuntimeError('Packaged frontend is stale; rebuild backend after frontend')
        domains = [name for name in jar.namelist() if re.fullmatch(r'BOOT-INF/lib/metersphere-domain-[^/]+\.jar', name)]
        if len(domains) != 1:
            raise RuntimeError('Expected a single domain dependency')
        with zipfile.ZipFile(io.BytesIO(jar.read(domains[0]))) as domain:
            for migration in ('116__recovery_scan_health', '117__legacy_import_tracking', '118__writeback_identifier_collations'):
                if f'migration/3.7.2/ddl/V3.7.2_{migration}.sql' not in domain.namelist():
                    raise RuntimeError('Missing packaged migration ' + migration)
        destination = (context / 'app').resolve()
        for name in jar.namelist():
            if not (destination / name).resolve().is_relative_to(destination):
                raise RuntimeError('Unsafe archive member')
        jar.extractall(destination)
    shutil.copy2(root / 'Dockerfile.backend', context / 'Dockerfile')
    bridge = context / 'wecom-bot-bridge'
    bridge.mkdir(parents=True, exist_ok=True)
    for name in ('package.json', 'package-lock.json'):
        shutil.copy2(root / 'wecom-bot-bridge' / name, bridge / name)
    shutil.copytree(root / 'wecom-bot-bridge/src', bridge / 'src', dirs_exist_ok=True)
    (context / 'deploy').mkdir(exist_ok=True)
    shutil.copy2(root / 'deploy/run-with-wecom-bridge.sh', context / 'deploy/run-with-wecom-bridge.sh')
    subprocess.run(['docker', 'build', '--build-arg', 'DEPENDENCY=app', '--build-arg', 'ALPINE_MIRROR=' + args.alpine_mirror, '--build-arg', 'MS_VERSION=implementation-' + digest[:16], '-t', args.tag, str(context)], check=True)
    print(f'Built {args.tag} from application SHA256 {digest}')


if __name__ == '__main__':
    main()
