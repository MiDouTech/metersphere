import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export default async function writeBuildManifest(directory, revision) {
  const index = await readFile(path.join(directory, 'index.html'));
  const assets = [...index.toString('utf8').matchAll(/(?:src|href)=["']([^"']+\.(?:js|css))["']/g)].map((m) => m[1]);
  const manifest = {
    schemaVersion: 1,
    revision,
    builtAt: new Date().toISOString(),
    indexSha256: createHash('sha256').update(index).digest('hex'),
    assets,
  };
  await writeFile(path.join(directory, 'build-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
  return manifest;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const revision =
    process.env.GITHUB_SHA || execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim();
  await writeBuildManifest(path.join(root, 'dist'), revision);
}
