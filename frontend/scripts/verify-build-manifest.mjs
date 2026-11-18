import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';

const directory = path.resolve('dist');
const manifest = JSON.parse(await readFile(path.join(directory, 'build-manifest.json'), 'utf8'));
assert.equal(manifest.schemaVersion, 1);
assert.match(manifest.revision, /^[a-f0-9]{40}$/);
if (process.env.GITHUB_SHA) assert.equal(manifest.revision, process.env.GITHUB_SHA);
const index = await readFile(path.join(directory, 'index.html'));
assert.equal(manifest.indexSha256, createHash('sha256').update(index).digest('hex'));
assert.ok(Array.isArray(manifest.assets) && manifest.assets.length > 0);
for (const asset of manifest.assets) {
  assert.equal(typeof asset, 'string');
  const file = path.resolve(directory, asset.replace(/^\//, ''));
  assert.ok(file.startsWith(`${directory}${path.sep}`));
  assert.ok((await stat(file)).isFile());
}
console.log(`Verified frontend manifest and ${manifest.assets.length} entry assets`);
